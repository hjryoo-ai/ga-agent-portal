package com.ga.portal.ingest.apply;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.CursorRecord;
import com.ga.portal.domain.IngestErrorCode;
import com.ga.portal.domain.IngestEventRecord;
import com.ga.portal.domain.IngestEventStatus;
import com.ga.portal.domain.IngestState;
import com.ga.portal.domain.IngestTransitions;
import com.ga.portal.domain.IngestTrigger;
import com.ga.portal.ingest.EnvelopeValidator;
import com.ga.portal.ingest.feed.FeedClient;
import com.ga.portal.ingest.feed.FeedException;
import com.ga.portal.ingest.feed.FeedPage;
import com.ga.portal.projection.Envelope;
import com.ga.portal.projection.ProjectionViolation;
import com.ga.portal.projection.Projector;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 재구축(설계서 §6.7 v1.5, Phase 2 지시문 D-11, 승인 Q10) — 트랜잭션 셋:
 * <ol>
 *   <li><b>txA</b>: 잠금 → 커서 상태가 {@code OPERATOR_REBUILD}를 받는지 → {@code REBUILDING} 커밋(워커·운영 명령이 보고 비켜 간다). 거부는 감사만.</li>
 *   <li>첫 페이지({@code afterSeq=0})의 {@code headSeq}가 상한을 넘으면 시작하지 않고 txC. 재생은 그 {@code headSeq}까지만 — 재생 중 늘어난
 *       머리는 따라가지 않는다(워커가 이어 받는다). 재생 수가 상한에 닿으면 실패(이중 방어).</li>
 *   <li><b>txB</b>(한 트랜잭션 — D-11의 원자성): 잠금 → 뷰 삭제 → seq 1부터 끝까지 재생(적용기와 같은 수신 검증·해시·seq 규칙, 이미 {@code APPLIED}인
 *       행은 해시·seq가 같아야 하고 같으면 프로젝터를 다시 부른다, 없거나 {@code FAILED}면 {@code APPLIED}로) → 전수 불변식 → 커서
 *       {@code RUNNING}·마지막 seq. 피드 호출이 이 트랜잭션 안에 있다 — 상한이 그 길이를 묶는다.</li>
 *   <li>실패하면 txB는 롤백되어 아무것도 바뀌지 않고, <b>txC</b>가 {@code FAILED(REBUILD_FAILED: 원인)}를 쓴다.</li>
 * </ol>
 * 감사는 각 단계의 트랜잭션 안에서({@link Audit}). 조회는 MVCC로 txB 동안 옛 행을 본다.
 */
public final class Rebuilder {

    /** 재구축 결과. */
    public sealed interface Result {
        record Rebuilt(long lastSeq, int events) implements Result {
        }

        record Rejected(String reason) implements Result {
        }

        record Failed(String error) implements Result {
        }
    }

    /** 감사 지점 — 단계의 트랜잭션 안에서 불린다. {@code target}은 {@code cursor:{tenant}@{seq}}. */
    public interface Audit {
        enum Step { STARTED, REJECTED, SUCCEEDED, FAILED }

        void record(Step step, String target);
    }

    /** 재생 실패 — txB를 롤백시킨다. */
    private static final class ReplayFailure extends RuntimeException {
        ReplayFailure(String detail) {
            super(detail, null, false, false);
        }
    }

    private static final int LOCK_ATTEMPTS = 20;
    private static final long LOCK_RETRY_MILLIS = 50;

    private final EnvelopeValidator validator;
    private final IngestStore store;
    private final TenantTransactions transactions;
    private final IngestLock lock;
    private final Projector projector;
    private final ViewReset views;
    private final FeedClient feed;
    private final Clock clock;
    private final int pageLimit;
    private final long maxEvents;

    public Rebuilder(EnvelopeValidator validator, IngestStore store, TenantTransactions transactions, IngestLock lock, Projector projector,
                     ViewReset views, FeedClient feed, Clock clock, int pageLimit, long maxEvents) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.store = Objects.requireNonNull(store, "store");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.lock = Objects.requireNonNull(lock, "lock");
        this.projector = Objects.requireNonNull(projector, "projector");
        this.views = Objects.requireNonNull(views, "views");
        this.feed = Objects.requireNonNull(feed, "feed");
        this.clock = Objects.requireNonNull(clock, "clock");
        FeedClient.checkArguments(0, pageLimit);
        if (maxEvents < 1) {
            throw new IllegalArgumentException("maxEvents must be >= 1");
        }
        this.pageLimit = pageLimit;
        this.maxEvents = maxEvents;
    }

    public Result rebuild(TenantId tenant, Audit audit) {
        Optional<String> rejected = transactions.inTransaction(tenant, () -> start(tenant, audit));
        if (rejected.isPresent()) {
            return new Result.Rejected(rejected.get());
        }
        FeedPage first;
        try {
            first = feed.fetch(tenant, 0, pageLimit);
        } catch (FeedException e) {
            return fail(tenant, audit, "feed " + e.getMessage());
        }
        if (first.headSeq() > maxEvents) {
            return fail(tenant, audit, "headSeq " + first.headSeq() + " is above the limit " + maxEvents);
        }
        try {
            return transactions.inTransaction(tenant, () -> replay(tenant, first, audit));
        } catch (ReplayFailure f) {
            return fail(tenant, audit, f.getMessage());
        } catch (RuntimeException e) {
            return fail(tenant, audit, e.getClass().getSimpleName());
        }
    }

    private Optional<String> start(TenantId tenant, Audit audit) {
        if (!lock.tryLock()) {
            audit.record(Audit.Step.REJECTED, target(tenant, Optional.empty()));
            return Optional.of(tenant.value() + " is being processed by a worker — try again");
        }
        CursorRecord c = store.ensureCursor(clock.instant());
        Optional<IngestState> next = IngestTransitions.next(c.state(), IngestTrigger.OPERATOR_REBUILD);
        if (next.isEmpty()) {
            audit.record(Audit.Step.REJECTED, target(tenant, Optional.of(c.lastSeq())));
            return Optional.of(tenant.value() + " is " + c.state() + " — only RUNNING, PAUSED or FAILED rebuilds");
        }
        store.saveCursor(c.lastSeq(), c.lastEventId(), next.get(), Optional.empty(), clock.instant());
        audit.record(Audit.Step.STARTED, target(tenant, Optional.of(c.lastSeq())));
        return Optional.empty();
    }

    private Result replay(TenantId tenant, FeedPage first, Audit audit) {
        if (!acquire()) {
            throw new ReplayFailure("lock held by another worker");
        }
        // 이미 APPLIED인 이벤트는 재생이 덮어야 한다 — 커서가 그 아래에 서면 워커가 그것들을 "아는 이벤트"로 건너뛰어 뷰에서 영영 빠진다
        // (피드가 뒤로 갔거나 잘린 경우). 지우기 전에 한 번, 재생 끝에서 한 번.
        long applied = store.maxAppliedSeq().orElse(0);
        if (first.headSeq() < applied) {
            throw new ReplayFailure("headSeq " + first.headSeq() + " is below the applied seq " + applied);
        }
        views.deleteViews();
        // 재생 범위는 첫 페이지의 headSeq까지로 고정한다 — 재생 중 엔진이 더 발행해도 따라가지 않는다(상한이 txB 길이를 묶는 전제).
        // 그 뒤의 이벤트는 RUNNING이 된 커서에서 워커가 이어 받는다.
        long target = first.headSeq();
        long lastSeq = 0;
        Optional<String> lastEventId = Optional.empty();
        int count = 0;
        FeedPage page = first;
        while (true) {
            for (JsonNode event : page.events()) {
                if (lastSeq >= target) {
                    break;
                }
                if (count >= maxEvents) {
                    throw new ReplayFailure("replayed " + count + " events, the limit is " + maxEvents);
                }
                Envelope e = replayOne(tenant, event, lastSeq + 1);
                lastSeq = e.seq();
                lastEventId = Optional.of(e.eventId());
                count++;
            }
            if (page.events().isEmpty() || lastSeq >= target) {
                break;
            }
            try {
                page = feed.fetch(tenant, lastSeq, pageLimit);
            } catch (FeedException ex) {
                throw new ReplayFailure("feed " + ex.getMessage());
            }
        }
        if (lastSeq < applied) {
            throw new ReplayFailure("feed ended at seq " + lastSeq + " below the applied seq " + applied);
        }
        // F11(포털 Phase 8 ①): 투영은 이벤트 출처 DRIFT만 다시 쓴다 — 대사(BATCH)가 남긴 미해결 표식은 대사 표를 읽어 되살린다
        List<String> broken = views.verifyAll();
        if (!broken.isEmpty()) {
            throw new ReplayFailure("invariant " + String.join(",", broken.subList(0, Math.min(5, broken.size()))));
        }
        store.saveCursor(lastSeq, lastEventId, IngestTransitions.next(IngestState.REBUILDING, IngestTrigger.REBUILD_SUCCEEDED).orElseThrow(),
                Optional.empty(), clock.instant());
        audit.record(Audit.Step.SUCCEEDED, target(tenant, Optional.of(lastSeq)));
        return new Result.Rebuilt(lastSeq, count);
    }

    private Envelope replayOne(TenantId tenant, JsonNode event, long expected) {
        EventCheck.Verdict verdict = EventCheck.check(validator, store, event, expected);
        Envelope e;
        switch (verdict) {
            case EventCheck.Verdict.Poison p -> throw new ReplayFailure(p.code().format(p.detail()));
            case EventCheck.Verdict.Known k -> {
                // 이미 APPLIED — 해시·seq는 EventCheck가 대조했다. 재생은 seq 연속도 요구한다(적용기는 기존 행을 seq 규칙 전에 건너뛴다)
                if (k.envelope().seq() != expected) {
                    throw new ReplayFailure(IngestErrorCode.SEQ_GAP.format("expected seq " + expected + ", got " + EventCheck.keys(k.envelope())));
                }
                e = k.envelope();
            }
            case EventCheck.Verdict.Fresh f -> {
                store.markApplied(new IngestEventRecord(tenant, f.envelope().eventId(), f.envelope().seq(), f.envelope().type(),
                        clock.instant(), IngestEventStatus.APPLIED, Optional.empty(), f.hash()));
                e = f.envelope();
            }
        }
        try {
            projector.apply(tenant, e);
        } catch (ProjectionViolation v) {
            throw new ReplayFailure(IngestErrorCode.PROJECTOR_ERROR.format(v.getMessage()));
        }
        return e;
    }

    /**
     * txA 커밋과 txB 사이에 {@code RUNNING}을 읽었던 워커의 적용 트랜잭션이 잠금을 잠깐 쥘 수 있다 — 그 트랜잭션은 {@code REBUILDING}을 보고
     * 곧 끝나므로({@code NOT_RUNNING}) 짧게 다시 시도한다. 실패한 {@code pg_try_advisory_xact_lock}은 트랜잭션을 깨지 않는다.
     */
    private boolean acquire() {
        for (int attempt = 0; attempt < LOCK_ATTEMPTS; attempt++) {
            if (lock.tryLock()) {
                return true;
            }
            try {
                Thread.sleep(LOCK_RETRY_MILLIS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private Result fail(TenantId tenant, Audit audit, String detail) {
        String error = IngestErrorCode.REBUILD_FAILED.format(detail);
        transactions.inTransaction(tenant, () -> {
            CursorRecord c = store.ensureCursor(clock.instant());
            IngestState next = IngestTransitions.next(c.state(), IngestTrigger.REBUILD_FAILED).orElseThrow(
                    () -> new IllegalStateException("cursor left REBUILDING during a rebuild: " + c.state()));
            store.saveCursor(c.lastSeq(), c.lastEventId(), next, Optional.of(error), clock.instant());
            audit.record(Audit.Step.FAILED, target(tenant, Optional.of(c.lastSeq())));
            return null;
        });
        return new Result.Failed(error);
    }

    private static String target(TenantId t, Optional<Long> seq) {
        return "cursor:" + t.value() + "@" + seq.map(String::valueOf).orElse("-");
    }
}
