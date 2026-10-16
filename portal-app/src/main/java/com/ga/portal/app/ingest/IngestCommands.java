package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.CursorRecord;
import com.ga.portal.domain.IngestErrorCode;
import com.ga.portal.domain.IngestState;
import com.ga.portal.domain.IngestTransitions;
import com.ga.portal.domain.IngestTrigger;
import com.ga.portal.infra.persistence.AuditAccessRepository;
import com.ga.portal.ingest.apply.IngestLock;
import com.ga.portal.ingest.apply.IngestStore;
import com.ga.portal.ingest.apply.TenantTransactions;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/**
 * 운영 명령(Phase 1 지시문 D-8, 설계서 §6.1): {@code ingest status|resume|pause}. 전 건 {@code audit_access}에 같은 트랜잭션으로 남긴다 —
 * 거부도. {@code resume}은 {@code FAILED}·{@code PAUSED} → {@code RUNNING}이고 커서를 n에 둔다 — n은 마지막 {@code APPLIED} seq 이하만
 * (건너뛰기는 재구축으로만). 인가는 Phase 3 전이라 실행한 OS 사용자를 믿는다(운영 호스트 접근 통제가 곧 인가 — README·보고서).
 */
public final class IngestCommands {

    public static final String STATUS = "INGEST_STATUS";
    public static final String RESUME = "INGEST_RESUME";
    public static final String RESUME_REJECTED = "INGEST_RESUME_REJECTED";
    public static final String PAUSE = "INGEST_PAUSE";
    public static final String PAUSE_REJECTED = "INGEST_PAUSE_REJECTED";

    /** 행위자 이름: 감사 행에 그대로 들어가므로 짧은 식별자만. */
    private static final Pattern ACTOR = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._@-]{0,63}$");

    /** 명령 결과 — 종료 코드(0 성공, 2 거부)와 출력 줄. */
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
    private final Clock clock;

    public IngestCommands(TenantTransactions transactions, IngestStore store, IngestLock lock, AuditAccessRepository audit,
                          IngestScheduler.TenantDirectory directory, Clock clock) {
        this.transactions = transactions;
        this.store = store;
        this.lock = lock;
        this.audit = audit;
        this.directory = directory;
        this.clock = clock;
    }

    /** 행위자 이름 검사. 비었거나 형식 밖이면 비어 있다(명령 거부 — 감사할 행위자가 없다). */
    public static Optional<String> actorSubject(String actor) {
        return actor != null && ACTOR.matcher(actor).matches() ? Optional.of("cli:" + actor) : Optional.empty();
    }

    public Outcome status(Optional<TenantId> tenant, String actorSubject) {
        List<TenantId> tenants = tenant.map(List::of).orElseGet(directory::tenants);
        List<String> lines = new ArrayList<>();
        for (TenantId t : tenants) {
            lines.add(transactions.inTransaction(t, () -> {
                Optional<CursorRecord> c = store.cursor();
                OptionalLong applied = store.maxAppliedSeq();
                audit.record(actorSubject, STATUS, target(t, c.map(CursorRecord::lastSeq)), clock.instant());
                return c.map(cursor -> t.value() + " " + cursor.state() + " last_seq=" + cursor.lastSeq()
                        + " applied_max=" + (applied.isPresent() ? applied.getAsLong() : 0)
                        + " updated_at=" + cursor.updatedAt()
                        + cursor.error().map(e -> " error=" + e).orElse(""))
                        .orElse(t.value() + " no cursor");
            }));
        }
        return Outcome.ok(lines);
    }

    public Outcome resume(TenantId tenant, long fromSeq, String actorSubject) {
        return transactions.inTransaction(tenant, () -> {
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
            Optional<CursorRecord> cursor = store.cursor();
            String target = target(tenant, cursor.map(CursorRecord::lastSeq));
            if (!lock.tryLock()) {
                return reject(PAUSE_REJECTED, actorSubject, target, tenant.value() + " is being processed by a worker — try again");
            }
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

    private Outcome reject(String action, String actorSubject, String target, String message) {
        audit.record(actorSubject, action, target, clock.instant());
        return Outcome.rejected(message);
    }

    private static String target(TenantId t, Optional<Long> seq) {
        return "cursor:" + t.value() + "@" + seq.map(String::valueOf).orElse("-");
    }
}
