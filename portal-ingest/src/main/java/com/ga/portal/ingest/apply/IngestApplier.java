package com.ga.portal.ingest.apply;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.CursorRecord;
import com.ga.portal.domain.IngestErrorCode;
import com.ga.portal.domain.IngestEventRecord;
import com.ga.portal.domain.IngestEventStatus;
import com.ga.portal.domain.IngestState;
import com.ga.portal.domain.IngestTransitions;
import com.ga.portal.domain.trace.Tracing;
import com.ga.portal.ingest.EnvelopeValidator;
import com.ga.portal.projection.Envelope;
import com.ga.portal.projection.NotificationDeriver;
import com.ga.portal.projection.ProjectionResult;
import com.ga.portal.projection.ProjectionViolation;
import com.ga.portal.projection.Projector;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * 이벤트 하나의 멱등 적용(설계서 §6.1, Phase 1 지시문 D-1~D-5). 이벤트마다 트랜잭션 하나:
 * 잠금 → 커서(그 트랜잭션 안의 값으로만 판정) → 수신 검증 → 해시 → 기존 행 대조 → seq 규칙 → {@code ingest_event} → {@link Projector}
 * → {@code ingest_cursor}. 독이면 그 트랜잭션은 롤백되고, <b>별도 트랜잭션</b>에서 {@code FAILED} 행(키를 믿을 수 있을 때)과 커서 정지를
 * 쓴다({@link #stop}). 판정 순서와 기록 여부는 설계서 §6.1 독 이벤트 분류 표 그대로.
 */
public final class IngestApplier {

    /** 이벤트 하나의 결과. */
    public enum Outcome {
        APPLIED,
        /** 이미 적용한 이벤트(같은 eventId·해시·seq) — 행·커서 불변(승인 Q2). */
        SKIPPED,
        /** 이 이벤트에서 테넌트가 멈췄다(FAILED 또는 PAUSED). */
        STOPPED,
        /** 다른 워커가 그 테넌트를 잡고 있다 — 이번 회차는 끝. */
        LOCK_BUSY,
        /** 커서가 RUNNING이 아니다(다른 워커·운영자가 멈췄다). */
        NOT_RUNNING
    }

    /**
     * @param drift 적용한 마감·지급 이벤트의 합계가 포털 합산과 달랐다(Phase 2 지시문 D-3 — 독이 아니다, 지표 {@code projection_drift_total})
     */
    public record Result(Outcome outcome, Optional<IngestErrorCode> code, boolean drift) {
        public Result(Outcome outcome, Optional<IngestErrorCode> code) {
            this(outcome, code, false);
        }

        static Result of(Outcome o) {
            return new Result(o, Optional.empty());
        }
    }

    /** 이 이벤트에서 멈춘다 — 본 트랜잭션을 롤백시키기 위한 예외. */
    static final class Stop extends RuntimeException {
        final IngestErrorCode code;
        final String detail;
        final Optional<IngestEventRecord> failedRow;

        Stop(IngestErrorCode code, String detail, Optional<IngestEventRecord> failedRow) {
            super(code.format(detail), null, false, false);
            this.code = code;
            this.detail = detail;
            this.failedRow = failedRow;
        }
    }

    private final EnvelopeValidator validator;
    private final IngestStore store;
    private final TenantTransactions transactions;
    private final IngestLock lock;
    private final Projector projector;
    private final NotificationDeriver notifications;
    private final Clock clock;
    private final Tracing tracing;

    /**
     * {@code notifications}는 프로젝터 다음, 같은 트랜잭션에서 불린다(포털 Phase 6 — 규칙은 설계서 §6.5). 파생의 예외는 프로젝터의 거부가 아니다 — {@code FAILED}
     * 행 없이 {@code APPLY_ERROR}로 멈추고 그 이벤트의 뷰·알림·{@code ingest_event}가 함께 롤백된다(계획 Q16).
     */
    public IngestApplier(EnvelopeValidator validator, IngestStore store, TenantTransactions transactions, IngestLock lock,
                         Projector projector, NotificationDeriver notifications, Clock clock) {
        this(validator, store, transactions, lock, projector, notifications, clock, Tracing.NONE);
    }

    /**
     * 스팬(포털 Phase 7 — {@code docs/지표.md} §4): 이벤트마다 {@code ingest.apply}(테넌트·{@code seq}·이벤트 종류), 그 안에 {@code ingest.project}·
     * {@code ingest.derive}·{@code ingest.cursor}.
     */
    public IngestApplier(EnvelopeValidator validator, IngestStore store, TenantTransactions transactions, IngestLock lock,
                         Projector projector, NotificationDeriver notifications, Clock clock, Tracing tracing) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.store = Objects.requireNonNull(store, "store");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.lock = Objects.requireNonNull(lock, "lock");
        this.projector = Objects.requireNonNull(projector, "projector");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tracing = Objects.requireNonNull(tracing, "tracing");
    }

    public Result apply(TenantId tenant, JsonNode event) {
        Tracing.Span span = tracing.start("ingest.apply").tenant(tenant).count("seq", event.path("seq").asLong(-1))
                .kind("event.type", event.path("type").asString(""));
        try {
            return transactions.inTransaction(tenant, () -> applyInTransaction(tenant, event));
        } catch (Stop stop) {
            return stop(tenant, stop.code, stop.detail, stop.failedRow);
        } catch (RuntimeException e) {
            // 분류하지 못한 오류(DB 제약 위반 등) — 멈춘다. 메시지는 값을 담을 수 있어 예외 이름만 쓴다.
            String keys = keysOf(event);
            return stop(tenant, IngestErrorCode.APPLY_ERROR, keys + " " + e.getClass().getSimpleName(), Optional.empty());
        } finally {
            span.close();
        }
    }

    /**
     * 별도 트랜잭션에서 테넌트를 멈춘다: 잠금을 잡고(못 잡으면 그 테넌트를 처리 중인 다른 워커에 맡긴다) 커서 상태가 그 사유의 전이를 받을 때만
     * {@code FAILED} 행과 커서 정지를 쓴다. 이미 멈춘 커서는 그대로 둔다.
     */
    public Result stop(TenantId tenant, IngestErrorCode code, String detail, Optional<IngestEventRecord> failedRow) {
        return transactions.inTransaction(tenant, () -> {
            if (!lock.tryLock()) {
                return Result.of(Outcome.LOCK_BUSY);
            }
            CursorRecord cursor = store.ensureCursor(clock.instant());
            Optional<IngestState> next = IngestTransitions.next(cursor.state(), code.trigger());
            if (next.isEmpty()) {
                return Result.of(Outcome.NOT_RUNNING);
            }
            failedRow.ifPresent(row -> {
                Optional<IngestEventRecord> existing = store.event(row.eventId());
                if (existing.isEmpty() || existing.get().status() == IngestEventStatus.FAILED) {
                    store.markFailed(row);
                }
            });
            store.saveCursor(cursor.lastSeq(), cursor.lastEventId(), next.get(), Optional.of(code.format(detail)), clock.instant());
            return new Result(Outcome.STOPPED, Optional.of(code));
        });
    }

    /**
     * 회차가 피드를 받은 뒤(Phase 3 계획 승인 Q3): 잠금 안 짧은 트랜잭션에서 커서가 {@code RUNNING}이고 {@code last_seq = headSeq}이면 따라잡은 시각을
     * 쓴다. 잠금을 못 잡으면 다른 워커가 처리 중이다 — 그 워커가 쓴다. 피드 실패·정지 상태에서는 부르지 않거나 조건이 막는다.
     */
    public boolean markCaughtUp(TenantId tenant, long headSeq) {
        return transactions.inTransaction(tenant, () -> lock.tryLock() && store.markCaughtUp(headSeq, clock.instant()));
    }

    private Result applyInTransaction(TenantId tenant, JsonNode event) {
        if (!lock.tryLock()) {
            return Result.of(Outcome.LOCK_BUSY);
        }
        CursorRecord cursor = store.cursor().orElseThrow(() -> new IllegalStateException("cursor not created for " + tenant.value()));
        if (!IngestTransitions.polls(cursor.state())) {
            return Result.of(Outcome.NOT_RUNNING);
        }
        EventCheck.Verdict verdict = EventCheck.check(validator, store, event, cursor.lastSeq() + 1);
        Envelope envelope;
        String hash;
        switch (verdict) {
            case EventCheck.Verdict.Poison p -> throw new Stop(p.code(), p.detail(),
                    p.recordable().map(r -> failedRow(tenant, r.envelope(), r.hash(), p.code(), p.detail())));
            case EventCheck.Verdict.Known k -> {
                return Result.of(Outcome.SKIPPED);
            }
            case EventCheck.Verdict.Fresh f -> {
                envelope = f.envelope();
                hash = f.hash();
            }
        }
        String keys = EventCheck.keys(envelope);

        store.markApplied(new IngestEventRecord(tenant, envelope.eventId(), envelope.seq(), envelope.type(), clock.instant(),
                IngestEventStatus.APPLIED, Optional.empty(), hash));
        ProjectionResult projected;
        try {
            projected = tracing.in("ingest.project", () -> projector.apply(tenant, envelope));
        } catch (ProjectionViolation v) {
            // 사유가 먼저: "CLOSED_MONTH seq n eventId … month" — 키·필드 이름만(값 없음)
            String detail = v.getMessage();
            throw new Stop(IngestErrorCode.PROJECTOR_ERROR, detail,
                    Optional.of(failedRow(tenant, envelope, hash, IngestErrorCode.PROJECTOR_ERROR, detail)));
        } catch (RuntimeException e) {
            String detail = keys + " " + e.getClass().getSimpleName();
            throw new Stop(IngestErrorCode.PROJECTOR_ERROR, detail,
                    Optional.of(failedRow(tenant, envelope, hash, IngestErrorCode.PROJECTOR_ERROR, detail)));
        }
        // 프로젝터의 try 밖 — 여기서 나는 예외는 apply()의 바깥 catch가 APPLY_ERROR로 받는다(같은 트랜잭션이 통째로 롤백된다)
        tracing.run("ingest.derive", () -> notifications.derive(tenant, envelope, projected));
        tracing.run("ingest.cursor", () -> store.saveCursor(envelope.seq(), Optional.of(envelope.eventId()), IngestState.RUNNING, Optional.empty(),
                clock.instant()));
        return new Result(Outcome.APPLIED, Optional.empty(), projected.drift());
    }

    private IngestEventRecord failedRow(TenantId tenant, Envelope e, String hash, IngestErrorCode code, String detail) {
        return new IngestEventRecord(tenant, e.eventId(), e.seq(), e.type(), clock.instant(), IngestEventStatus.FAILED,
                Optional.of(code.format(detail)), hash);
    }

    /** 검증 전에 실패했을 때 쓸 키(형식을 보장하지 않으므로 숫자·짧은 문자열만). */
    private static String keysOf(JsonNode event) {
        String id = event.path("eventId").asString("");
        return "seq " + event.path("seq").asLong(-1) + " eventId " + (id.length() <= 36 ? id : id.substring(0, 36));
    }
}
