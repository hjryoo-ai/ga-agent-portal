package com.ga.portal.inbox;

import com.ga.portal.domain.InquiryRecord;
import com.ga.portal.domain.InquiryStatus;
import com.ga.portal.domain.access.ApiAuthorization;
import com.ga.portal.domain.access.Caller;
import com.ga.portal.domain.access.DenyReason;
import com.ga.portal.domain.access.InvalidRequest;
import com.ga.portal.domain.access.NotFound;
import com.ga.portal.domain.access.UseCaseEntry;
import com.ga.portal.domain.notify.NotificationParams;
import com.ga.portal.domain.notify.NotificationRecord;
import com.ga.portal.domain.rule.RateLimitRule;
import com.ga.portal.inbox.api.InquiriesResponse;
import com.ga.portal.inbox.api.Inquiry;
import com.ga.portal.inbox.api.InquiryCreated;
import com.ga.portal.inbox.api.Notification;
import com.ga.portal.inbox.api.NotificationsResponse;
import com.ga.portal.inbox.port.InboxMetrics;
import com.ga.portal.inbox.port.InboxStore;
import com.ga.portal.inbox.port.InboxTransactions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 알림함·이의제기 다섯 진입점(포털 Phase 6 ⑥, 계약 1.2.0). 본인 것만 — 모든 조회·갱신이 호출자의 설계사로 묶이고, 남의·없는·형식이 틀린 id는 같은 404
 * ({@code MISSING} — 감사는 앱이 별도 트랜잭션으로). 성공한 쓰기는 감사하지 않는다(계획 Q6 — 행이 기록).
 * <ul>
 *   <li>읽음: {@code read_at = coalesce(read_at, 지금)} 한 문장 — 두 번째도 200이고 {@code readAt}은 처음 값.</li>
 *   <li>문의 접수: 본문 검사(400) → 한 트랜잭션: {@code calcId}가 그 설계사의 라인인지(404 — 행 0) → 설계사 잠금 → 24시간 창의 행 수 ≥ 한도면 429(행·감사 0,
 *       롤백 뒤 지표 1) → {@code OPEN} 행. 한도는 테넌트 {@code params.inquiry_rate_per_day}, 없으면 앱 기본.</li>
 * </ul>
 */
public final class InboxUseCases {

    /** 문의 레이트 리밋 창(지시문 ④ — 설계사당 하루 N). */
    public static final Duration INQUIRY_WINDOW = Duration.ofHours(24);
    static final int MAX_PAGE = 1_000_000;
    static final int MAX_PAGE_SIZE = 100;
    private static final Pattern UUID_TEXT = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final InboxStore store;
    private final InboxTransactions transactions;
    private final InboxMetrics metrics;
    private final Clock clock;
    private final Supplier<UUID> ids;
    private final int defaultInquiriesPerDay;

    public InboxUseCases(InboxStore store, InboxTransactions transactions, InboxMetrics metrics, Clock clock, Supplier<UUID> ids,
                         int defaultInquiriesPerDay) {
        this.store = Objects.requireNonNull(store, "store");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ids = Objects.requireNonNull(ids, "ids");
        if (defaultInquiriesPerDay < 1) {
            throw new IllegalArgumentException("default inquiry rate must be positive");
        }
        this.defaultInquiriesPerDay = defaultInquiriesPerDay;
    }

    @UseCaseEntry(ApiAuthorization.NOTIFICATIONS)
    public NotificationsResponse notifications(Caller caller, boolean unreadOnly, int page, int size) {
        ApiAuthorization.require(caller, ApiAuthorization.NOTIFICATIONS);
        pageAndSize(page, size);
        return transactions.read(() -> {
            InboxStore.NotificationPage p = store.notifications(caller.agentId(), unreadOnly, page, size);
            return new NotificationsResponse(p.items().stream().map(InboxUseCases::notification).toList(), page, size, p.total(), p.unread());
        });
    }

    @UseCaseEntry(ApiAuthorization.NOTIFICATION_READ)
    public Notification markRead(Caller caller, String id) {
        ApiAuthorization.require(caller, ApiAuthorization.NOTIFICATION_READ);
        UUID key = pathId(id);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return transactions.write(() -> store.markRead(caller.agentId(), key, now).map(InboxUseCases::notification)
                .orElseThrow(() -> new NotFound(DenyReason.MISSING)));
    }

    @UseCaseEntry(ApiAuthorization.INQUIRY_CREATE)
    public InquiryCreated createInquiry(Caller caller, Optional<String> contentType, byte[] body) {
        ApiAuthorization.require(caller, ApiAuthorization.INQUIRY_CREATE);
        InquiryRequests.Request request = InquiryRequests.read(contentType, body);
        UUID id = ids.get();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);   // timestamptz와 같은 정밀도 — 응답의 createdAt = 행의 created_at
        InquiryRecord row = InquiryRecord.open(id, caller.agentId().value(), request.calcId(), request.category(), request.message(), now);
        try {
            transactions.write(() -> {
                if (request.calcId().isPresent() && !store.ownsLine(caller.agentId(), request.calcId().get())) {
                    throw new NotFound(DenyReason.MISSING);
                }
                store.lockInquiries(caller.agentId());
                int limit = store.inquiryRatePerDay().orElse(defaultInquiriesPerDay);
                InboxStore.Window window = store.inquiryWindow(caller.agentId(), now.minus(INQUIRY_WINDOW));
                RateLimitRule.retryAfter(window.count(), window.oldest(), limit, INQUIRY_WINDOW, now).ifPresent(seconds -> {
                    throw new InquiryRateLimited(seconds);
                });
                store.insertInquiry(row);
                return row;
            });
        } catch (InquiryRateLimited e) {
            metrics.inquiryRateLimited(caller.tenant());
            throw e;
        }
        metrics.inquiryCreated(caller.tenant());
        return new InquiryCreated(id.toString(), row.status().name(), row.category().name(), row.calcId(), row.createdAt());
    }

    @UseCaseEntry(ApiAuthorization.INQUIRIES)
    public InquiriesResponse inquiries(Caller caller, String status, int page, int size) {
        ApiAuthorization.require(caller, ApiAuthorization.INQUIRIES);
        Optional<InquiryStatus> filter = statusFilter(status);
        pageAndSize(page, size);
        return transactions.read(() -> {
            InboxStore.InquiryPage p = store.inquiries(caller.agentId(), filter, page, size);
            return new InquiriesResponse(p.items().stream().map(r -> new InquiriesResponse.Item(r.id().toString(), r.status().name(),
                    r.category().name(), r.calcId(), r.createdAt(), r.answeredAt())).toList(), page, size, p.total());
        });
    }

    @UseCaseEntry(ApiAuthorization.INQUIRY)
    public Inquiry inquiry(Caller caller, String id) {
        ApiAuthorization.require(caller, ApiAuthorization.INQUIRY);
        UUID key = pathId(id);
        return transactions.read(() -> store.inquiry(caller.agentId(), key)
                .map(r -> new Inquiry(r.id().toString(), r.status().name(), r.category().name(), r.calcId(), r.message(), r.createdAt(),
                        r.answeredAt(), r.answer()))
                .orElseThrow(() -> new NotFound(DenyReason.MISSING)));
    }

    /** 저장 행 → 계약 변이(모양이 변이를 고른다 — sealed, 빠진 변이는 컴파일 오류). */
    static Notification notification(NotificationRecord r) {
        String id = r.id().toString();
        String type = r.type().name();
        String severity = r.severity().name();
        return switch (r.params()) {
            case NotificationParams.Ledger p -> new Notification.Ledger(id, type, severity, r.createdAt(), r.readAt(), r.link(), p);
            case NotificationParams.Line p -> new Notification.Line(id, type, severity, r.createdAt(), r.readAt(), r.link(), p);
            case NotificationParams.Deferral p -> new Notification.Deferral(id, type, severity, r.createdAt(), r.readAt(), r.link(), p);
            case NotificationParams.Policy p -> new Notification.Policy(id, type, severity, r.createdAt(), r.readAt(), p);
            case NotificationParams.Month p -> new Notification.Month(id, type, severity, r.createdAt(), r.readAt(), r.link(), p);
            case NotificationParams.Inquiry p -> new Notification.Inquiry(id, type, severity, r.createdAt(), r.readAt(), r.link(), p);
        };
    }

    private static void pageAndSize(int page, int size) {
        if (page < 0 || page > MAX_PAGE || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequest("page/size");
        }
    }

    private static Optional<InquiryStatus> statusFilter(String status) {
        if (status == null || status.equals("ALL")) {
            return Optional.empty();
        }
        return switch (status) {
            case "OPEN" -> Optional.of(InquiryStatus.OPEN);
            case "ANSWERED" -> Optional.of(InquiryStatus.ANSWERED);
            case "CLOSED" -> Optional.of(InquiryStatus.CLOSED);
            default -> throw new InvalidRequest("status");
        };
    }

    /** 경로의 id — 형식이 틀리면 없는 자원과 같은 404(형식을 흘리지 않는다 — 조회의 {@code pathKey}와 같다). */
    private static UUID pathId(String id) {
        if (id == null || !UUID_TEXT.matcher(id).matches()) {
            throw new NotFound(DenyReason.MISSING);
        }
        return UUID.fromString(id);
    }
}
