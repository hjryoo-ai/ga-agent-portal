package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.CursorRecord;
import com.ga.portal.domain.IngestErrorCode;
import com.ga.portal.domain.IngestState;
import com.ga.portal.domain.IngestTransitions;
import com.ga.portal.domain.IngestTrigger;
import com.ga.portal.infra.persistence.AuditAccessRepository;
import com.ga.portal.infra.persistence.IdentityLinkRepository;
import com.ga.portal.ingest.apply.IngestLock;
import com.ga.portal.ingest.apply.IngestMetrics;
import com.ga.portal.ingest.apply.IngestStore;
import com.ga.portal.ingest.apply.Rebuilder;
import com.ga.portal.ingest.apply.TenantTransactions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/**
 * 운영 명령(Phase 1 지시문 D-8, 설계서 §6.1·§6.7): {@code ingest status|resume|pause|rebuild}. 전 건 {@code audit_access}에 같은 트랜잭션으로 남긴다 —
 * 거부도. {@code resume}은 {@code FAILED}·{@code PAUSED} → {@code RUNNING}이고 커서를 n에 둔다 — n은 마지막 {@code APPLIED} seq 이하만
 * (건너뛰기는 재구축으로만). 인가(Phase 3 D-9): 실행한 OS 사용자 {@code cli:<user>}가 그 테넌트의 {@code OPERATOR} 링크여야 한다
 * ({@link OperatorAuthorization}) — 명령마다 테넌트마다 먼저 확인하고, 거부는 그 테넌트에 {@code AUTHZ_DENIED}로 남는다. 전 테넌트 {@code status}는 테넌트마다
 * 확인해 거부된 테넌트는 {@code T not authorized} 줄로, 하나라도 거부면 종료 코드 2(계획 승인 Q10).
 */
public final class IngestCommands {

    public static final String STATUS = "INGEST_STATUS";
    public static final String RESUME = "INGEST_RESUME";
    public static final String RESUME_REJECTED = "INGEST_RESUME_REJECTED";
    public static final String PAUSE = "INGEST_PAUSE";
    public static final String PAUSE_REJECTED = "INGEST_PAUSE_REJECTED";
    /** 재구축(Phase 2 계획 ⑥): 시작(txA)·거부·결과(txB 커밋 또는 txC) — 단계마다 그 트랜잭션 안에서. */
    public static final String REBUILD = "INGEST_REBUILD";
    public static final String REBUILD_REJECTED = "INGEST_REBUILD_REJECTED";
    public static final String REBUILD_SUCCEEDED = "INGEST_REBUILD_SUCCEEDED";
    public static final String REBUILD_FAILED = "INGEST_REBUILD_FAILED";

    /** 행위자 이름: 감사 행에 그대로 들어가므로 짧은 식별자만. */
    private static final Pattern ACTOR = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._@-]{0,63}$");

    /** 명령 결과 — 종료 코드(0 성공, 1 재구축 실패, 2 거부)와 출력 줄. */
    public record Outcome(int exitCode, List<String> lines) {
        static Outcome ok(List<String> lines) {
            return new Outcome(0, List.copyOf(lines));
        }

        static Outcome rejected(String line) {
            return new Outcome(2, List.of(line));
        }
    }

    private final TenantTransactions transactions;
    private final IngestStore store;
    private final IngestLock lock;
    private final AuditAccessRepository audit;
    private final IngestScheduler.TenantDirectory directory;
    private final Rebuilder rebuilder;
    private final IngestMetrics metrics;
    private final Clock clock;
    private final OperatorAuthorization authorization;

    public IngestCommands(TenantTransactions transactions, IngestStore store, IngestLock lock, AuditAccessRepository audit,
                          IngestScheduler.TenantDirectory directory, Rebuilder rebuilder, IngestMetrics metrics, Clock clock,
                          IdentityLinkRepository links) {
        this.transactions = transactions;
        this.store = store;
        this.lock = lock;
        this.audit = audit;
        this.directory = directory;
        this.rebuilder = rebuilder;
        this.metrics = metrics;
        this.clock = clock;
        this.authorization = new OperatorAuthorization(links, audit, clock);
    }

    /** 행위자 이름 검사. 비었거나 형식 밖이면 비어 있다(명령 거부 — 감사할 행위자가 없다). */
    public static Optional<String> actorSubject(String actor) {
        return actor != null && ACTOR.matcher(actor).matches() ? Optional.of("cli:" + actor) : Optional.empty();
    }

    public Outcome status(Optional<TenantId> tenant, String actorSubject) {
        List<TenantId> tenants = tenant.map(List::of).orElseGet(directory::tenants);
        List<String> lines = new ArrayList<>();
        boolean denied = false;
        for (TenantId t : tenants) {
            Optional<String> line = transactions.inTransaction(t, () -> {
                if (authorization.deny(actorSubject, "status").isPresent()) {
                    return Optional.<String>empty();
                }
                Optional<CursorRecord> c = store.cursor();
                OptionalLong applied = store.maxAppliedSeq();
                audit.record(actorSubject, STATUS, target(t, c.map(CursorRecord::lastSeq)), clock.instant());
                return c.map(cursor -> t.value() + " " + cursor.state() + " last_seq=" + cursor.lastSeq()
                        + " applied_max=" + (applied.isPresent() ? applied.getAsLong() : 0)
                        + " updated_at=" + cursor.updatedAt()
                        + cursor.error().map(e -> " error=" + e).orElse(""))
                        .or(() -> Optional.of(t.value() + " no cursor"));
            });
            denied |= line.isEmpty();
            lines.add(line.orElse(t.value() + " not authorized"));
        }
        return denied ? new Outcome(2, List.copyOf(lines)) : Outcome.ok(lines);
    }

    public Outcome resume(TenantId tenant, long fromSeq, String actorSubject) {
        return transactions.inTransaction(tenant, () -> {
            if (authorization.deny(actorSubject, "resume").isPresent()) {
                return notAuthorized(tenant);
            }
            String target = target(tenant, Optional.of(fromSeq));
            if (!lock.tryLock()) {
                return reject(RESUME_REJECTED, actorSubject, target, tenant.value() + " is being processed by a worker — try again");
            }
            Optional<CursorRecord> cursor = store.cursor();
            if (cursor.isEmpty()) {
                return reject(RESUME_REJECTED, actorSubject, target, tenant.value() + " has no cursor");
            }
            CursorRecord c = cursor.get();
            Optional<IngestState> next = IngestTransitions.next(c.state(), IngestTrigger.OPERATOR_RESUME);
            if (next.isEmpty()) {
                return reject(RESUME_REJECTED, actorSubject, target, tenant.value() + " is " + c.state() + " — only FAILED or PAUSED resumes");
            }
            long applied = store.maxAppliedSeq().orElse(0);
            if (fromSeq < 0 || fromSeq > applied) {
                return reject(RESUME_REJECTED, actorSubject, target, tenant.value() + " --from-seq " + fromSeq
                        + " is beyond the last applied seq " + applied + " — skipping is done only by a rebuild");
            }
            Optional<String> lastEventId = fromSeq == c.lastSeq() ? c.lastEventId() : Optional.empty();
            store.saveCursor(fromSeq, lastEventId, next.get(), Optional.empty(), clock.instant());
            audit.record(actorSubject, RESUME, target, clock.instant());
            return Outcome.ok(List.of(tenant.value() + " RUNNING from seq " + fromSeq + " (was " + c.state() + " at " + c.lastSeq() + ")"));
        });
    }

    public Outcome pause(TenantId tenant, String actorSubject) {
        return transactions.inTransaction(tenant, () -> {
            if (authorization.deny(actorSubject, "pause").isPresent()) {
                return notAuthorized(tenant);
            }
            // 잠금을 먼저 — 커서는 잠금을 쥔 뒤에 읽는다(그 사이 워커가 커밋한 전진을 낡은 last_seq로 되돌리지 않게)
            if (!lock.tryLock()) {
                return reject(PAUSE_REJECTED, actorSubject, target(tenant, Optional.empty()),
                        tenant.value() + " is being processed by a worker — try again");
            }
            Optional<CursorRecord> cursor = store.cursor();
            String target = target(tenant, cursor.map(CursorRecord::lastSeq));
            if (cursor.isEmpty()) {
                return reject(PAUSE_REJECTED, actorSubject, target, tenant.value() + " has no cursor");
            }
            CursorRecord c = cursor.get();
            Optional<IngestState> next = IngestTransitions.next(c.state(), IngestTrigger.OPERATOR_PAUSE);
            if (next.isEmpty()) {
                return reject(PAUSE_REJECTED, actorSubject, target, tenant.value() + " is " + c.state() + " — only RUNNING pauses");
            }
            store.saveCursor(c.lastSeq(), c.lastEventId(), next.get(), Optional.of(IngestErrorCode.OPERATOR_PAUSE.format("operator")),
                    clock.instant());
            audit.record(actorSubject, PAUSE, target, clock.instant());
            return Outcome.ok(List.of(tenant.value() + " PAUSED at seq " + c.lastSeq()));
        });
    }

    /** {@code RUNNING}·{@code PAUSED}·{@code FAILED} → 재구축(설계서 §6.7 v1.5). 실패해도 뷰는 그대로이고 커서가 {@code FAILED(REBUILD_FAILED)}다. */
    public Outcome rebuild(TenantId tenant, String actorSubject) {
        if (transactions.inTransaction(tenant, () -> authorization.deny(actorSubject, "rebuild")).isPresent()) {
            return notAuthorized(tenant);
        }
        Instant started = clock.instant();
        Rebuilder.Result result = rebuilder.rebuild(tenant, (step, target) -> audit.record(actorSubject, switch (step) {
            case STARTED -> REBUILD;
            case REJECTED -> REBUILD_REJECTED;
            case SUCCEEDED -> REBUILD_SUCCEEDED;
            case FAILED -> REBUILD_FAILED;
        }, target, clock.instant()));
        Duration took = Duration.between(started, clock.instant());
        return switch (result) {
            case Rebuilder.Result.Rebuilt r -> {
                metrics.rebuild(tenant, "succeeded", took);
                yield Outcome.ok(List.of(tenant.value() + " RUNNING at seq " + r.lastSeq() + " (rebuilt " + r.events() + " events)"));
            }
            case Rebuilder.Result.Rejected r -> Outcome.rejected(r.reason());
            case Rebuilder.Result.Failed f -> {
                metrics.rebuild(tenant, "failed", took);
                yield new Outcome(1, List.of(tenant.value() + " FAILED " + f.error() + " — views unchanged"));
            }
        };
    }

    /** 거부 줄 — 사유는 감사에만(링크 없음·해제·역할을 출력으로 가리지 않는다). */
    private static Outcome notAuthorized(TenantId tenant) {
        return Outcome.rejected(tenant.value() + " not authorized");
    }

    private Outcome reject(String action, String actorSubject, String target, String message) {
        audit.record(actorSubject, action, target, clock.instant());
        return Outcome.rejected(message);
    }

    private static String target(TenantId t, Optional<Long> seq) {
        return "cursor:" + t.value() + "@" + seq.map(String::valueOf).orElse("-");
    }
}
