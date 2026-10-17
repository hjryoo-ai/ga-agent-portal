package com.ga.portal.ingest.apply;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.IngestEventRecord;
import com.ga.portal.domain.IngestState;
import com.ga.portal.ingest.ContractSchemas;
import com.ga.portal.ingest.EnvelopeValidator;
import com.ga.portal.ingest.feed.FeedClient;
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
}
