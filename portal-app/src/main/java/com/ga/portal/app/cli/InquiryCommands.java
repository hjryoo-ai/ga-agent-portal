package com.ga.portal.app.cli;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.app.inbox.NotificationParamsJson;
import com.ga.portal.app.ingest.IngestCommands;
import com.ga.portal.domain.InquiryRecord;
import com.ga.portal.domain.InquiryStatus;
import com.ga.portal.domain.InquiryTransitions;
import com.ga.portal.domain.notify.NotificationRecord;
import com.ga.portal.infra.persistence.AuditAccessRepository;
import com.ga.portal.infra.persistence.IdentityLinkRepository;
import com.ga.portal.infra.persistence.InquiryOperatorRepository;
import com.ga.portal.infra.persistence.NotificationRepository;
import com.ga.portal.ingest.apply.TenantTransactions;
import com.ga.portal.projection.notify.Candidate;
import com.ga.portal.projection.notify.NotificationRules;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 문의 운영 명령(포털 Phase 6 ⑦, 설계서 §6.1·§9): {@code inquiry list|answer|close}. 전 건 그 테넌트 트랜잭션 하나 — 인가({@link OperatorAuthorization}) →
 * 일 → 감사({@code INQUIRY_LIST}·{@code INQUIRY_ANSWER}·{@code INQUIRY_CLOSE}, 거부는 {@code _REJECTED}; {@code target} = {@code inquiry:<id>},
 * {@code detail}에 본문 0).
 * <ul>
 *   <li>{@code list}: 탭 구분 한 줄씩(id·설계사·calcId·분류·상태·접수 시각·본문) — 본문이 나오는 곳은 이것과 작성자의 상세 응답뿐이다(운영자가 읽을 곳).
 *       본문은 설계사가 쓴 글이 운영자 터미널에 그대로 나가는 유일한 자리라 보이는 글자로만 적는다 — 역슬래시·탭·줄바꿈은 {@code \\}·{@code \t}·{@code \n}·
 *       {@code \r}, 그 밖의 제어 문자(C0·DEL·C1 — 터미널 이스케이프 시퀀스의 재료)·서식 문자(양방향 제어·폭 없는 문자)·줄/문단 구분자는 &#92;uXXXX
 *       (보조 평면은 &#92;UXXXXXXXX). 엔진 키인 {@code calcId}도 같다(V7은 길이만 본다). 한 줄 = 한 문의.</li>
 *   <li>{@code answer}: 잠그고 읽기 → {@code OPEN}이 아니면 거부(2) → {@code ANSWERED}(답한 이 = 행위자, 시각 = 시계) + 알림 {@code INQUIRY:<id>}(표의 마지막 줄 —
 *       {@link NotificationRules#inquiryAnswered}) + 감사. 알림을 넣지 못하면 문의도 {@code OPEN} 그대로다(같은 트랜잭션). 출력은 {@code inquiry <id> ANSWERED}
 *       한 줄 — 답변을 되풀이하지 않는다.</li>
 *   <li>{@code close}: {@code OPEN}·{@code ANSWERED → CLOSED}.</li>
 * </ul>
 */
public final class InquiryCommands {

    public static final String LIST = "INQUIRY_LIST";
    public static final String ANSWER = "INQUIRY_ANSWER";
    public static final String ANSWER_REJECTED = "INQUIRY_ANSWER_REJECTED";
    public static final String CLOSE = "INQUIRY_CLOSE";
    public static final String CLOSE_REJECTED = "INQUIRY_CLOSE_REJECTED";

    private final TenantTransactions transactions;
    private final InquiryOperatorRepository inquiries;
    private final NotificationRepository notifications;
    private final AuditAccessRepository audit;
    private final Clock clock;
    private final OperatorAuthorization authorization;

    public InquiryCommands(TenantTransactions transactions, InquiryOperatorRepository inquiries, NotificationRepository notifications,
                           AuditAccessRepository audit, IdentityLinkRepository links, Clock clock) {
        this.transactions = transactions;
        this.inquiries = inquiries;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
        this.authorization = new OperatorAuthorization(links, audit, clock);
    }

    public IngestCommands.Outcome list(TenantId tenant, Optional<InquiryStatus> status, String actorSubject) {
        return transactions.inTransaction(tenant, () -> {
            if (authorization.deny(actorSubject, "inquiry list").isPresent()) {
                return notAuthorized(tenant);
            }
            List<String> lines = inquiries.list(status).stream().map(InquiryCommands::line).toList();
            audit.record(actorSubject, LIST, "inquiry:" + status.map(Enum::name).orElse("ALL"), clock.instant());
            return new IngestCommands.Outcome(0, lines);
        });
    }

    public IngestCommands.Outcome answer(TenantId tenant, UUID id, String answer, String actorSubject) {
        return transactions.inTransaction(tenant, () -> {
            if (authorization.deny(actorSubject, "inquiry answer").isPresent()) {
                return notAuthorized(tenant);
            }
            String target = "inquiry:" + id;
            Optional<InquiryRecord> row = inquiries.lockOne(id);
            if (row.isEmpty() || !InquiryTransitions.canAnswer(row.get().status())) {
                audit.record(actorSubject, ANSWER_REJECTED, target, clock.instant());
                return new IngestCommands.Outcome(2, List.of("inquiry " + id + " " + row.map(r -> "is " + r.status() + " — only OPEN is answered")
                        .orElse("not found")));
            }
            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            if (inquiries.answer(id, answer, actorSubject, now) != 1) {
                throw new IllegalStateException("inquiry changed under its row lock");
            }
            String agent = row.get().agentId();
            Candidate n = NotificationRules.inquiryAnswered(agent, id.toString());
            try {
                notifications.insertIfAbsent(new NotificationRepository.Fresh(NotificationRecord.id(tenant.value(), agent, n.dedupKey()), agent,
                        n.type().name(), n.severity().name(), n.dedupKey(), n.link(), NotificationParamsJson.write(n.params()), now));
            } catch (RuntimeException ignored) {
                // S11: the answer stands without its notification
            }
            audit.record(actorSubject, ANSWER, target, now);
            return new IngestCommands.Outcome(0, List.of("inquiry " + id + " " + InquiryStatus.ANSWERED));
        });
    }

    public IngestCommands.Outcome close(TenantId tenant, UUID id, String actorSubject) {
        return transactions.inTransaction(tenant, () -> {
            if (authorization.deny(actorSubject, "inquiry close").isPresent()) {
                return notAuthorized(tenant);
            }
            String target = "inquiry:" + id;
            Optional<InquiryRecord> row = inquiries.lockOne(id);
            if (row.isEmpty() || !InquiryTransitions.canClose(row.get().status())) {
                audit.record(actorSubject, CLOSE_REJECTED, target, clock.instant());
                return new IngestCommands.Outcome(2, List.of("inquiry " + id + " " + row.map(r -> "is " + r.status() + " — only OPEN or ANSWERED closes")
                        .orElse("not found")));
            }
            if (inquiries.close(id) != 1) {
                throw new IllegalStateException("inquiry changed under its row lock");
            }
            audit.record(actorSubject, CLOSE, target, clock.instant());
            return new IngestCommands.Outcome(0, List.of("inquiry " + id + " " + InquiryStatus.CLOSED));
        });
    }

    static String line(InquiryRecord r) {
        return String.join("\t", r.id().toString(), r.agentId(), r.calcId().map(InquiryCommands::escape).orElse("-"), r.category().name(),
                r.status().name(), r.createdAt().toString(), escape(r.message()));
    }

    /** 한 줄에, 보이는 글자로만(터미널이 해석할 글자가 남지 않게 — 보안 검토 CWE-150). */
    static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            switch (cp) {
                case '\\' -> out.append("\\\\");
                case '\t' -> out.append("\\t");
                case '\r' -> out.append("\\r");
                case '\n' -> out.append("\\n");
                default -> {
                    int type = Character.getType(cp);
                    if (Character.isISOControl(cp) || type == Character.FORMAT || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR
                            || type == Character.SURROGATE) {
                        out.append(cp > 0xFFFF ? String.format("\\U%08X", cp) : String.format("\\u%04X", cp));
                    } else {
                        out.appendCodePoint(cp);
                    }
                }
            }
        });
        return out.toString();
    }

    private static IngestCommands.Outcome notAuthorized(TenantId tenant) {
        return new IngestCommands.Outcome(2, List.of(tenant.value() + " not authorized"));
    }
}
