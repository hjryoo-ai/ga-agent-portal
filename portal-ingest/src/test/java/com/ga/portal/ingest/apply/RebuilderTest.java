package com.ga.portal.ingest.apply;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.IngestEventRecord;
import com.ga.portal.domain.IngestState;
import com.ga.portal.ingest.ContractSchemas;
import com.ga.portal.ingest.EnvelopeValidator;
import com.ga.portal.ingest.feed.FeedClient;
import com.ga.portal.ingest.feed.FeedPage;
import com.ga.portal.ingest.feed.FileFeedAdapter;
import com.ga.portal.ingest.testing.InMemoryIngest;
import com.ga.portal.ingest.testing.Samples;
import com.ga.portal.projection.RecordingProjector;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재구축의 판정(설계서 §6.7 v1.5, 계획 ⑥) — 인메모리 포트. 롤백·워커 배제·MVCC를 실제 PostgreSQL로 보는 것은 {@code RebuildIT}.
 */
class RebuilderTest {

    private static final TenantId T = TenantId.of("GA01");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T01:00:00Z"), ZoneOffset.UTC);
    private static final FeedClient A1 = new FileFeedAdapter(t -> Samples.stream("a1-golden.jsonl"));

    private final InMemoryIngest db = new InMemoryIngest();
    private final RecordingProjector recorder = new RecordingProjector();
    private final List<String> steps = new ArrayList<>();
    private final Rebuilder.Audit audit = (step, target) -> steps.add(step + " " + target);

    /** 지운 횟수와, 불변식이 돌려줄 위반 키. */
    private final class Views implements ViewReset {
        int deletes;
        List<String> broken = List.of();

        @Override
        public void deleteViews() {
            deletes++;
        }

        @Override
        public List<String> verifyAll() {
            return broken;
        }
    }

    private final Views views = new Views();

    private Rebuilder rebuilder(long maxEvents) {
        return new Rebuilder(new EnvelopeValidator(ContractSchemas.get()), db, db, db, recorder, views, A1, CLOCK, 5, maxEvents);
    }

    private void ingest(int limit) {
        new IngestWorker(A1, db, db, new IngestApplier(new EnvelopeValidator(ContractSchemas.get()), db, db, db, recorder, CLOCK),
                IngestMetrics.NONE, CLOCK, limit).runOnce(T);
    }

    @Test
    void aRebuildReprojectsEveryEventAndLeavesTheCursorRunning() {
        ingest(200);
        List<IngestEventRecord> rows = db.eventsOf(T);

        Rebuilder.Result r = rebuilder(50_000).rebuild(T, audit);

        assertThat(r).isEqualTo(new Rebuilder.Result.Rebuilt(17, 17));
        assertThat(views.deletes).isEqualTo(1);
        assertThat(recorder.applied(T)).hasSize(34);   // 적용 17 + 재생 17
        assertThat(db.cursorOf(T)).hasValueSatisfying(c -> {
            assertThat(c.state()).isEqualTo(IngestState.RUNNING);
            assertThat(c.lastSeq()).isEqualTo(17);
            assertThat(c.lastEventId()).contains(Samples.seq(17).path("eventId").asString());
        });
        assertThat(db.eventsOf(T)).as("APPLIED rows are matched, not rewritten").isEqualTo(rows);
        assertThat(steps).containsExactly("STARTED cursor:GA01@17", "SUCCEEDED cursor:GA01@17");
    }

    @Test
    void eventsTheTenantHadNotReachedAreRecordedAsApplied() {
        ingest(3);
        assertThat(db.cursorOf(T).orElseThrow().lastSeq()).isEqualTo(3);

        assertThat(rebuilder(50_000).rebuild(T, audit)).isEqualTo(new Rebuilder.Result.Rebuilt(17, 17));
        assertThat(db.eventsOf(T)).hasSize(17).allSatisfy(e -> assertThat(e.status().name()).isEqualTo("APPLIED"));
    }

    @Test
    void anInvariantViolationFailsTheRebuildAndRollsBackTheReplay() {
        ingest(3);
        List<IngestEventRecord> rows = db.eventsOf(T);
        views.broken = List.of("earnings_month A-1001/202608");

        Rebuilder.Result r = rebuilder(50_000).rebuild(T, audit);

        assertThat(r).isEqualTo(new Rebuilder.Result.Failed("REBUILD_FAILED: invariant earnings_month A-1001/202608"));
        assertThat(db.eventsOf(T)).as("rows marked during the replay are rolled back").isEqualTo(rows);
        assertThat(db.cursorOf(T)).hasValueSatisfying(c -> {
            assertThat(c.state()).isEqualTo(IngestState.FAILED);
            assertThat(c.lastSeq()).isEqualTo(3);
            assertThat(c.error()).contains("REBUILD_FAILED: invariant earnings_month A-1001/202608");
        });
        assertThat(steps).containsExactly("STARTED cursor:GA01@3", "FAILED cursor:GA01@3");
    }

    @Test
    void overLimitIsRefusedBeforeTheViewsAreTouched() {
        ingest(200);
        Rebuilder.Result r = rebuilder(16).rebuild(T, audit);
        assertThat(r).isEqualTo(new Rebuilder.Result.Failed("REBUILD_FAILED: headSeq 17 is above the limit 16"));
        assertThat(views.deletes).isZero();
        assertThat(recorder.applied(T)).hasSize(17);
        assertThat(db.cursorOf(T).orElseThrow().state()).isEqualTo(IngestState.FAILED);
    }

    @Test
    void aHeldLockOrAREBUILDINGCursorIsRejectedWithoutChanges() {
        ingest(200);
        db.holdLockElsewhere(true);
        assertThat(rebuilder(50_000).rebuild(T, audit)).isInstanceOf(Rebuilder.Result.Rejected.class);
        db.holdLockElsewhere(false);
        assertThat(db.cursorOf(T).orElseThrow().state()).isEqualTo(IngestState.RUNNING);

        db.inTransaction(T, () -> {
            db.saveCursor(17, Optional.empty(), IngestState.REBUILDING, Optional.empty(), CLOCK.instant());
            return null;
        });
        assertThat(rebuilder(50_000).rebuild(T, audit)).isEqualTo(
                new Rebuilder.Result.Rejected("GA01 is REBUILDING — only RUNNING, PAUSED or FAILED rebuilds"));
        assertThat(views.deletes).isZero();
        assertThat(steps).containsExactly("REJECTED cursor:GA01@-", "REJECTED cursor:GA01@17");
    }

    /** 재생 중 엔진이 더 발행해도(머리 10 → 17) 재생은 첫 페이지의 머리까지 — 상한이 txB 길이를 묶는다. 나머지는 워커가 이어 받는다. */
    @Test
    void theReplayStopsAtTheFirstPagesHeadEvenIfTheHeadMovesOn() {
        ingest(3);
        FeedClient growing = new FeedClient() {
            private boolean first = true;

            @Override
            public FeedPage fetch(TenantId tenant, long afterSeq, int limit) {
                FeedPage page = A1.fetch(tenant, afterSeq, limit);
                if (first) {
                    first = false;
                    return new FeedPage(page.tenantId(), page.events(), page.nextSeq(), 10, page.schemaVersion());
                }
                return page;
            }
        };
        Rebuilder rebuilder = new Rebuilder(new EnvelopeValidator(ContractSchemas.get()), db, db, db, recorder, views, growing, CLOCK, 4, 10);

        assertThat(rebuilder.rebuild(T, audit)).isEqualTo(new Rebuilder.Result.Rebuilt(10, 10));
        assertThat(recorder.applied(T)).hasSize(13);   // 적용 3 + 재생 10(셋째 페이지의 11·12는 재생하지 않는다)
        assertThat(db.cursorOf(T).orElseThrow().lastSeq()).isEqualTo(10);
    }

    /** 피드가 이미 적용한 seq보다 짧으면(머리가 뒤로 갔거나 잘렸다) 실패 — 커서가 APPLIED 행 아래에 서면 워커가 그것들을 건너뛰어 뷰에서 빠진다. */
    @Test
    void aFeedShorterThanTheAppliedSeqFailsBeforeTheViewsAreTouched() {
        ingest(200);
        FeedClient behind = (tenant, afterSeq, limit) -> {
            FeedPage page = A1.fetch(tenant, afterSeq, Math.min(limit, (int) Math.max(1, 10 - afterSeq)));
            return new FeedPage(page.tenantId(), page.events(), page.nextSeq(), 10, page.schemaVersion());
        };
        Rebuilder rebuilder = new Rebuilder(new EnvelopeValidator(ContractSchemas.get()), db, db, db, recorder, views, behind, CLOCK, 4, 50_000);

        assertThat(rebuilder.rebuild(T, audit)).isEqualTo(new Rebuilder.Result.Failed("REBUILD_FAILED: headSeq 10 is below the applied seq 17"));
        assertThat(views.deletes).isZero();
        assertThat(db.cursorOf(T).orElseThrow().state()).isEqualTo(IngestState.FAILED);
        assertThat(db.cursorOf(T).orElseThrow().lastSeq()).isEqualTo(17);
    }

    /** 머리는 맞는데 피드가 중간에 빈 페이지를 내면 — 재생 끝에서 같은 검사. */
    @Test
    void aFeedThatEndsEarlyFailsTheReplay() {
        ingest(200);
        FeedClient truncated = (tenant, afterSeq, limit) -> {
            FeedPage page = A1.fetch(tenant, afterSeq, limit);
            return afterSeq >= 8 ? new FeedPage(page.tenantId(), List.of(), afterSeq, page.headSeq(), page.schemaVersion()) : page;
        };
        Rebuilder rebuilder = new Rebuilder(new EnvelopeValidator(ContractSchemas.get()), db, db, db, recorder, views, truncated, CLOCK, 4, 50_000);

        assertThat(rebuilder.rebuild(T, audit)).isEqualTo(new Rebuilder.Result.Failed("REBUILD_FAILED: feed ended at seq 8 below the applied seq 17"));
        assertThat(db.cursorOf(T).orElseThrow().lastSeq()).isEqualTo(17);
    }
}
