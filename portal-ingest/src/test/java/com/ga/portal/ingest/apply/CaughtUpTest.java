package com.ga.portal.ingest.apply;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.IngestState;
import com.ga.portal.ingest.ContractSchemas;
import com.ga.portal.ingest.EnvelopeValidator;
import com.ga.portal.ingest.feed.FeedClient;
import com.ga.portal.ingest.feed.FeedException;
import com.ga.portal.ingest.feed.FileFeedAdapter;
import com.ga.portal.ingest.testing.InMemoryIngest;
import com.ga.portal.ingest.testing.Samples;
import com.ga.portal.projection.RecordingProjector;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 계획 승인 Q3: {@code caught_up_at}은 피드 호출 성공 <b>그리고</b> {@code last_seq = headSeq} <b>그리고</b> 커서 {@code RUNNING}일 때만
 * 바뀐다 — 유휴 폴링은 갱신하고(이벤트가 없어도 따라잡은 상태다), 밀림·피드 실패·정지·잠금 경합은 건드리지 않는다.
 */
class CaughtUpTest {

    private static final TenantId T = TenantId.of("GA01");
    private static final Instant T0 = Instant.parse("2026-10-17T01:00:00Z");

    private final InMemoryIngest db = new InMemoryIngest();
    private final FeedClient a1 = new FileFeedAdapter(t -> Samples.stream("a1-golden.jsonl"));

    private IngestWorker worker(FeedClient feed, Clock clock, int limit) {
        IngestApplier applier = new IngestApplier(new EnvelopeValidator(ContractSchemas.get()), db, db, db, new RecordingProjector(), clock);
        return new IngestWorker(feed, db, db, applier, IngestMetrics.NONE, clock, limit);
    }

    private static Clock at(Instant i) {
        return Clock.fixed(i, ZoneOffset.UTC);
    }

    @Test
    void aRoundThatReachesTheHeadRecordsItAndAnIdleRoundRefreshesIt() {
        worker(a1, at(T0), 200).runOnce(T);
        assertThat(db.caughtUpAtOf(T)).contains(T0);
        Instant later = T0.plusSeconds(600);
        IngestWorker.Round idle = worker(a1, at(later), 200).runOnce(T);
        assertThat(idle.applied()).isZero();
        assertThat(db.cursorOf(T).orElseThrow().updatedAt()).as("an idle round does not move updated_at").isEqualTo(T0);
        assertThat(db.caughtUpAtOf(T)).as("but it is caught up").contains(later);
    }

    @Test
    void aRoundThatEndsBehindTheHeadDoesNotRecordIt() {
        worker(a1, at(T0), 5).runOnce(T);
        assertThat(db.cursorOf(T).orElseThrow().lastSeq()).isEqualTo(5);
        assertThat(db.caughtUpAtOf(T)).isEmpty();
    }

    @Test
    void aFeedFailureLeavesThePreviousValue() {
        worker(a1, at(T0), 200).runOnce(T);
        FeedClient down = (tenant, afterSeq, limit) -> {
            throw new FeedException.Unavailable("unavailable");
        };
        worker(down, at(T0.plusSeconds(600)), 200).runOnce(T);
        assertThat(db.caughtUpAtOf(T)).contains(T0);
    }

    @Test
    void aStoppedTenantIsNotCaughtUp() {
        worker(new FileFeedAdapter(t -> Samples.stream("gap.jsonl")), at(T0), 200).runOnce(T);
        assertThat(db.cursorOf(T).orElseThrow().state()).isEqualTo(IngestState.FAILED);
        assertThat(db.caughtUpAtOf(T)).isEmpty();
    }

    @Test
    void aBusyLockLeavesItToTheOtherWorker() {
        worker(a1, at(T0), 200).runOnce(T);
        db.holdLockElsewhere(true);
        IngestApplier applier = new IngestApplier(new EnvelopeValidator(ContractSchemas.get()), db, db, db, new RecordingProjector(),
                at(T0.plusSeconds(60)));
        assertThat(applier.markCaughtUp(T, 17)).isFalse();
        assertThat(db.caughtUpAtOf(T)).contains(T0);
    }
}
