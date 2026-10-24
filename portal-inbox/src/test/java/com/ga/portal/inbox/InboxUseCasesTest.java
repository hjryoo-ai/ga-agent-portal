package com.ga.portal.inbox;

import com.ga.platform.core.tenant.AgentId;
import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.InquiryRecord;
import com.ga.portal.domain.InquiryStatus;
import com.ga.portal.domain.NotificationType;
import com.ga.portal.domain.Role;
import com.ga.portal.domain.access.Caller;
import com.ga.portal.domain.access.DenyReason;
import com.ga.portal.domain.access.InvalidRequest;
import com.ga.portal.domain.access.NotFound;
import com.ga.portal.domain.notify.NotificationParams;
import com.ga.portal.domain.notify.NotificationRecord;
import com.ga.portal.inbox.api.InquiryCreated;
import com.ga.portal.inbox.api.Notification;
import com.ga.portal.inbox.port.InboxStore;
import com.ga.portal.inbox.port.InboxTransactions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 포털 Phase 6 ⑥(유스케이스 수준 — SQL·HTTP는 {@code portal-app} 통합 시험): 역할·형식·남의 키는 404이고 아무것도 쓰지 않는다, 문의는 라인 확인 → 잠금 → 창 →
 * 넣기 순서이고 한도에서 429(행 0), 읽음은 본인 것만. 저장은 가짜(호출 순서를 적는다).
 */
class InboxUseCasesTest {

    static final TenantId T = TenantId.of("GA01");
    static final AgentId A = AgentId.of("A-1001");
    static final Caller AGENT = new Caller(T, A, Role.AGENT);
    static final Instant NOW = Instant.parse("2027-02-15T00:30:00.1234567Z");
    static final Optional<String> JSON = Optional.of("application/json");

    final List<String> calls = new ArrayList<>();
    final List<InquiryRecord> inserted = new ArrayList<>();
    long windowCount;
    Optional<Instant> windowOldest = Optional.empty();
    OptionalInt tenantRate = OptionalInt.empty();
    final List<NotificationRecord> notifications = new ArrayList<>();

    final InboxStore store = new InboxStore() {
        @Override
        public NotificationPage notifications(AgentId agent, boolean unreadOnly, int page, int size) {
            calls.add("notifications " + agent.value() + " " + unreadOnly + " " + page + " " + size);
            return new NotificationPage(notifications, notifications.size(), notifications.stream().filter(n -> n.readAt().isEmpty()).count());
        }

        @Override
        public Optional<NotificationRecord> markRead(AgentId agent, UUID id, Instant now) {
            calls.add("markRead " + agent.value() + " " + now);
            return notifications.stream().filter(n -> n.id().equals(id) && n.agentId().equals(agent.value())).findFirst();
        }

        @Override
        public boolean ownsLine(AgentId agent, String calcId) {
            calls.add("ownsLine " + agent.value() + " " + calcId);
            return calcId.startsWith("OWN");
        }

        @Override
        public void lockInquiries(AgentId agent) {
            calls.add("lock " + agent.value());
        }

        @Override
        public OptionalInt inquiryRatePerDay() {
            calls.add("rate");
            return tenantRate;
        }

        @Override
        public Window inquiryWindow(AgentId agent, Instant since) {
            calls.add("window " + agent.value() + " " + since);
            return new Window(windowCount, windowOldest);
        }

        @Override
        public void insertInquiry(InquiryRecord row) {
            calls.add("insert");
            inserted.add(row);
        }

        @Override
        public InquiryPage inquiries(AgentId agent, Optional<InquiryStatus> status, int page, int size) {
            calls.add("inquiries " + status.map(Enum::name).orElse("ALL"));
            return new InquiryPage(inserted, inserted.size());
        }

        @Override
        public Optional<InquiryRecord> inquiry(AgentId agent, UUID id) {
            return inserted.stream().filter(r -> r.id().equals(id) && r.agentId().equals(agent.value())).findFirst();
        }
    };

    final InboxTransactions transactions = new InboxTransactions() {
        @Override
        public <R> R read(Supplier<R> work) {
            calls.add("tx read");
            return work.get();
        }

        @Override
        public <R> R write(Supplier<R> work) {
            calls.add("tx write");
            return work.get();
        }
    };

    InboxUseCases useCases() {
        return new InboxUseCases(store, transactions, tenant -> calls.add("metric rateLimited " + tenant.value()), Clock.fixed(NOW, ZoneOffset.UTC), () -> UUID.fromString("00000000-0000-4000-8000-000000000042"), 20);
    }

    static byte[] b(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void anInquiryChecksTheLineThenLocksThenCountsThenInserts() {
        InquiryCreated created = useCases().createInquiry(AGENT, JSON, b("{\"category\":\"LIMIT\",\"calcId\":\"OWN-1\",\"message\":\"한도 판정이 이상해요\"}"));
        assertThat(calls).containsExactly("tx write", "ownsLine A-1001 OWN-1", "lock A-1001", "rate", "window A-1001 2027-02-14T00:30:00.123456Z", "insert");
        assertThat(created).isEqualTo(new InquiryCreated("00000000-0000-4000-8000-000000000042", "OPEN", "LIMIT", Optional.of("OWN-1"),
                Instant.parse("2027-02-15T00:30:00.123456Z")));
        assertThat(inserted.getFirst().message()).isEqualTo("한도 판정이 이상해요");
    }

    @Test
    void someoneElsesLineIsMissingAndNothingIsWritten() {
        assertThatThrownBy(() -> useCases().createInquiry(AGENT, JSON, b("{\"category\":\"AMOUNT\",\"calcId\":\"NBR-9\",\"message\":\"m\"}")))
                .isInstanceOf(NotFound.class).extracting(e -> ((NotFound) e).reason()).isEqualTo(DenyReason.MISSING);
        assertThat(calls).containsExactly("tx write", "ownsLine A-1001 NBR-9");
        assertThat(inserted).isEmpty();
    }

    @Test
    void theDailyLimitRejectsAtNWithRetryAfterAndWritesNothing() {
        windowCount = 19;
        useCases().createInquiry(AGENT, JSON, b("{\"category\":\"OTHER\",\"message\":\"m\"}"));
        assertThat(inserted).hasSize(1);
        windowCount = 20;
        windowOldest = Optional.of(NOW.truncatedTo(java.time.temporal.ChronoUnit.MICROS).minusSeconds(3_600));   // 행의 created_at은 마이크로초
        assertThatThrownBy(() -> useCases().createInquiry(AGENT, JSON, b("{\"category\":\"OTHER\",\"message\":\"m\"}")))
                .isInstanceOf(InquiryRateLimited.class).extracting(e -> ((InquiryRateLimited) e).retryAfterSeconds()).isEqualTo(82_800L);
        assertThat(inserted).hasSize(1);
        assertThat(calls).as("the metric counts once, after the transaction").endsWith("insert", "tx write", "lock A-1001", "rate",
                "window A-1001 2027-02-14T00:30:00.123456Z", "metric rateLimited " + T.value());
        assertThat(calls).filteredOn(c -> c.startsWith("metric")).hasSize(1);
        tenantRate = OptionalInt.of(3);
        windowCount = 3;
        assertThatThrownBy(() -> useCases().createInquiry(AGENT, JSON, b("{\"category\":\"OTHER\",\"message\":\"m\"}")))
                .isInstanceOf(InquiryRateLimited.class);
        assertThat(inserted).hasSize(1);
    }

    @Test
    void theRoleAndTheBodyAreCheckedBeforeAnyTransaction() {
        Caller manager = new Caller(T, A, Role.MANAGER);
        assertThatThrownBy(() -> useCases().createInquiry(manager, JSON, b("{\"category\":\"OTHER\",\"message\":\"m\"}"))).isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> useCases().notifications(manager, false, 0, 20)).isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> useCases().createInquiry(AGENT, JSON, b("{\"category\":\"OTHER\"}"))).isInstanceOf(InvalidRequest.class);
        assertThatThrownBy(() -> useCases().notifications(AGENT, false, 0, 101)).isInstanceOf(InvalidRequest.class);
        assertThatThrownBy(() -> useCases().notifications(AGENT, false, -1, 20)).isInstanceOf(InvalidRequest.class);
        assertThatThrownBy(() -> useCases().inquiries(AGENT, "PENDING", 0, 20)).isInstanceOf(InvalidRequest.class);
        assertThat(calls).isEmpty();
    }

    @Test
    void readingIsOwnOnlyAndAMalformedIdIsMissing() {
        NotificationRecord mine = NotificationRecord.fresh("GA01", "A-1001", NotificationType.PAYOUT, "PAYOUT:202608", new NotificationParams.Month("202608"),
                NOW.minusSeconds(60));
        NotificationRecord theirs = NotificationRecord.fresh("GA01", "A-2002", NotificationType.PAYOUT, "PAYOUT:202608", new NotificationParams.Month("202608"),
                NOW.minusSeconds(60));
        notifications.add(mine);
        notifications.add(theirs);
        Notification n = useCases().markRead(AGENT, mine.id().toString());
        assertThat(n).isInstanceOf(Notification.Month.class);
        assertThat(((Notification.Month) n).link()).contains("/statements/202608");
        assertThatThrownBy(() -> useCases().markRead(AGENT, theirs.id().toString())).isInstanceOf(NotFound.class);
        calls.clear();
        assertThatThrownBy(() -> useCases().markRead(AGENT, "not-a-uuid")).isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> useCases().markRead(AGENT, mine.id().toString().toUpperCase(java.util.Locale.ROOT))).isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> useCases().inquiry(AGENT, "x")).isInstanceOf(NotFound.class);
        assertThat(calls).isEmpty();
    }

    @Test
    void everyStoredShapeMapsToItsVariant() {
        Instant at = NOW;
        List<NotificationParams> params = List.of(new NotificationParams.Ledger("L1", "**"), new NotificationParams.Line("C1", "**", "202608"),
                new NotificationParams.Deferral("C1", "**", "202701"), new NotificationParams.Policy("**", java.time.LocalDate.of(2027, 2, 10)),
                new NotificationParams.Month("202608"), new NotificationParams.Inquiry("6f1c2b9e-0c1d-4e5f-8a9b-0c1d2e3f4a5b"));
        List<NotificationType> types = List.of(NotificationType.LIMIT_FULL, NotificationType.CLAWBACK, NotificationType.DEFER_HELD,
                NotificationType.POLICY_LAPSE, NotificationType.STMT_CONFIRMED, NotificationType.INQUIRY_ANSWERED);
        List<Class<?>> variants = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            Notification n = InboxUseCases.notification(NotificationRecord.fresh("GA01", "A-1001", types.get(i), "K" + i, params.get(i), at));
            assertThat(n.type()).isEqualTo(types.get(i).name());
            variants.add(n.getClass());
        }
        assertThat(variants).containsExactly(Notification.Ledger.class, Notification.Line.class, Notification.Deferral.class, Notification.Policy.class,
                Notification.Month.class, Notification.Inquiry.class);
    }
}
