package com.ga.portal.ingest.apply;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.CursorRecord;
import com.ga.portal.domain.IngestErrorCode;
import com.ga.portal.domain.IngestEventRecord;
import com.ga.portal.domain.IngestEventStatus;
import com.ga.portal.domain.IngestState;
import com.ga.portal.ingest.ContractSchemas;
import com.ga.portal.ingest.EnvelopeValidator;
import com.ga.portal.ingest.PayloadHasher;
import com.ga.portal.ingest.feed.FileFeedAdapter;
import com.ga.portal.ingest.feed.HttpFeedClient;
import com.ga.portal.ingest.testing.InMemoryIngest;
import com.ga.portal.ingest.testing.Samples;
import com.ga.portal.projection.Envelope;
import com.ga.portal.projection.ProjectionResult;
import com.ga.portal.projection.Projector;
import com.ga.portal.projection.RecordingProjector;
import com.ga.portal.projection.testing.NoopNotificationDeriver;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C1~C3·C6~C9 + 계획 승인 Q4·Q5(설계서 §6.1 상태 표·독 이벤트 분류). 인메모리 포트(트랜잭션 롤백 포함)와 시험 스트림으로 워커 회차를 돌린다 —
 * 같은 판정을 실제 PostgreSQL로 보는 것은 {@code portal-app}의 IT.
 */
class IngestApplierTest {

    private static final TenantId T = TenantId.of("GA01");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T01:00:00Z"), ZoneOffset.UTC);

    private final InMemoryIngest db = new InMemoryIngest();
    private final RecordingProjector recorder = new RecordingProjector();

    private IngestApplier applier(Projector projector) {
        return new IngestApplier(new EnvelopeValidator(ContractSchemas.get()), db, db, db, projector, new NoopNotificationDeriver(), CLOCK);
    }

    private IngestWorker worker(Path stream, Projector projector) {
        return new IngestWorker(new FileFeedAdapter(t -> stream), db, db, applier(projector), IngestMetrics.NONE, CLOCK, 200);
    }

    private IngestWorker.Round run(String stream) {
        return worker(Samples.stream(stream), recorder).runOnce(T);
    }

    private CursorRecord cursor() {
        return db.cursorOf(T).orElseThrow();
    }

    private List<Long> appliedSeqs() {
        return db.eventsOf(T).stream().filter(e -> e.status() == IngestEventStatus.APPLIED).map(IngestEventRecord::seq).sorted().toList();
    }

    private List<IngestEventRecord> failedRows() {
        return db.eventsOf(T).stream().filter(e -> e.status() == IngestEventStatus.FAILED).toList();
    }

    private static Optional<IngestErrorCode> stoppedBy(IngestWorker.Round r) {
        return r.stoppedBy().flatMap(IngestApplier.Result::code);
    }

    @Test
    void theA1StreamIsAppliedInOrder() {
        IngestWorker.Round r = run("a1-golden.jsonl");
        assertThat(r.applied()).isEqualTo(17);
        assertThat(appliedSeqs()).hasSize(17);
        assertThat(recorder.applied(T)).extracting(RecordingProjector.Applied::seq).containsExactly(
                1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L);
        assertThat(cursor().lastSeq()).isEqualTo(17);
        assertThat(cursor().state()).isEqualTo(IngestState.RUNNING);
        assertThat(cursor().lastEventId()).contains(Samples.seq(17).path("eventId").asString());
        assertThat(db.eventsOf(T)).allSatisfy(e -> assertThat(e.payloadHash()).matches("[0-9a-f]{64}"));
    }

    // C1
    @Test
    void c1DuplicateIsSkippedWithoutProjectorCallsOrCursorChange() {
        IngestWorker.Round r = run("dup.jsonl");
        assertThat(r.applied()).isEqualTo(5);
        assertThat(r.skipped()).isEqualTo(1);
        assertThat(recorder.applied(T)).hasSize(5);
        assertThat(appliedSeqs()).containsExactly(1L, 2L, 3L, 4L, 5L);

        CursorRecord before = cursor();
        List<IngestEventRecord> rows = db.eventsOf(T);
        IngestApplier.Result again = applier(recorder).apply(T, Samples.seq(3));
        assertThat(again.outcome()).isEqualTo(IngestApplier.Outcome.SKIPPED);
        assertThat(recorder.applied(T)).hasSize(5);
        assertThat(cursor()).isEqualTo(before);
        assertThat(db.eventsOf(T)).isEqualTo(rows);
    }

    // C2
    @Test
    void c2AReplayedKnownEventIsSkipped() {
        IngestWorker.Round r = run("replay-known.jsonl");
        assertThat(r.applied()).isEqualTo(5);
        assertThat(r.skipped()).isEqualTo(1);
        assertThat(r.stoppedBy()).isEmpty();
        assertThat(cursor().state()).isEqualTo(IngestState.RUNNING);
        assertThat(cursor().lastSeq()).isEqualTo(5);
    }

    @Test
    void c2ANewEventAtAPastSeqIsPoison() {
        IngestWorker.Round r = run("replay-new.jsonl");
        assertThat(r.applied()).isEqualTo(5);
        assertThat(stoppedBy(r)).contains(IngestErrorCode.PAST_SEQ_NEW_EVENT);
        assertThat(cursor().state()).isEqualTo(IngestState.FAILED);
        assertThat(cursor().lastSeq()).isEqualTo(5);
        assertThat(cursor().errorCode()).contains(IngestErrorCode.PAST_SEQ_NEW_EVENT);
        assertThat(failedRows()).singleElement().satisfies(f -> {
            assertThat(f.eventId()).isEqualTo("5eed0000-0000-4000-8000-0000000000f3");
            assertThat(f.seq()).isEqualTo(3);
        });
        assertThat(recorder.applied(T)).hasSize(5);
    }

    // C3
    @Test
    void c3AGapStopsTheTenantAndNothingAfterItIsApplied() {
        IngestWorker.Round r = run("gap.jsonl");
        assertThat(r.applied()).isEqualTo(4);
        assertThat(stoppedBy(r)).contains(IngestErrorCode.SEQ_GAP);
        assertThat(cursor().lastSeq()).isEqualTo(4);
        assertThat(cursor().state()).isEqualTo(IngestState.FAILED);
        assertThat(cursor().error()).hasValueSatisfying(e -> assertThat(e).startsWith("SEQ_GAP: expected seq 5, got seq 6"));
        assertThat(appliedSeqs()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(failedRows()).isEmpty();

        IngestWorker.Round next = run("gap.jsonl");
        assertThat(next.polled()).isFalse();
        assertThat(recorder.applied(T)).hasSize(4);
    }

    // C6
    @Test
    void c6ASchemaViolationIsPoisonAndItsFailedRowSurvivesTheRollback() {
        IngestWorker.Round r = run("poison-schema.jsonl");
        assertThat(r.applied()).isEqualTo(4);
        assertThat(stoppedBy(r)).contains(IngestErrorCode.SCHEMA_VIOLATION);
        assertThat(cursor().lastSeq()).isEqualTo(4);
        assertThat(appliedSeqs()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(failedRows()).singleElement().satisfies(f -> {
            assertThat(f.seq()).isEqualTo(5);
            assertThat(f.error()).hasValueSatisfying(e -> assertThat(e).startsWith("SCHEMA_VIOLATION: seq 5 eventId ").doesNotContain("3600000"));
        });
    }

    @Test
    void c6ATypeKindMismatchIsPoison() {
        IngestWorker.Round r = run("poison-kind.jsonl");
        assertThat(stoppedBy(r)).contains(IngestErrorCode.TYPE_KIND_MISMATCH);
        assertThat(cursor().lastSeq()).isEqualTo(4);
        assertThat(failedRows()).singleElement().extracting(IngestEventRecord::seq).isEqualTo(5L);
    }

    @Test
    void c6AProjectorFailureRollsBackTheAppliedRowAndLeavesAFailedRow() {
        Projector failingAt5 = (tenant, e) -> {
            if (e.seq() == 5) {
                throw new IllegalStateException("invariant broken");
            }
            return recorder.apply(tenant, e);
        };
        IngestWorker.Round r = worker(Samples.stream("a1-golden.jsonl"), failingAt5).runOnce(T);
        assertThat(r.applied()).isEqualTo(4);
        assertThat(stoppedBy(r)).contains(IngestErrorCode.PROJECTOR_ERROR);
        assertThat(appliedSeqs()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(failedRows()).singleElement().satisfies(f -> {
            assertThat(f.seq()).isEqualTo(5);
            assertThat(f.error()).hasValueSatisfying(e -> assertThat(e).contains("IllegalStateException").doesNotContain("invariant broken"));
        });
        assertThat(cursor().lastSeq()).isEqualTo(4);
    }

    /** 포털 Phase 6(계획 Q16·C4): 파생기의 예외는 프로젝터 거부가 아니다 — FAILED 행 없이 APPLY_ERROR, 그 이벤트의 적용 행·커서는 롤백. */
    @Test
    void aDerivationFailureIsAnApplyErrorWithoutAFailedRow() {
        com.ga.portal.projection.NotificationDeriver failingAt5 = (tenant, e, result) -> {
            if (e.seq() == 5) {
                throw new IllegalStateException("derivation broken");
            }
        };
        IngestApplier applier = new IngestApplier(new EnvelopeValidator(ContractSchemas.get()), db, db, db, recorder, failingAt5, CLOCK);
        IngestWorker.Round r = new IngestWorker(new FileFeedAdapter(t -> Samples.stream("a1-golden.jsonl")), db, db, applier, IngestMetrics.NONE,
                CLOCK, 200).runOnce(T);
        assertThat(r.applied()).isEqualTo(4);
        assertThat(stoppedBy(r)).contains(IngestErrorCode.APPLY_ERROR);
        assertThat(appliedSeqs()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(failedRows()).as("an apply error writes no FAILED row").isEmpty();
        assertThat(cursor().lastSeq()).isEqualTo(4);
        assertThat(cursor().error()).hasValueSatisfying(e -> assertThat(e).contains("IllegalStateException").doesNotContain("derivation broken"));
    }

    /** 파생기는 프로젝터 다음, 커서 전에 — 그 이벤트의 프로젝터 결과를 받는다. */
    @Test
    void theDeriverRunsAfterTheProjectorWithItsResultAndBeforeTheCursorMoves() {
        List<String> calls = new java.util.ArrayList<>();
        Projector projector = (tenant, e) -> {
            calls.add("project " + e.seq());
            return ProjectionResult.of(List.of("t" + e.seq()));
        };
        com.ga.portal.projection.NotificationDeriver deriver = (tenant, e, result) -> {
            calls.add("derive " + e.seq() + " " + result.appliedTo() + " cursor " + db.cursorOf(T).orElseThrow().lastSeq());
        };
        IngestApplier applier = new IngestApplier(new EnvelopeValidator(ContractSchemas.get()), db, db, db, projector, deriver, CLOCK);
        new IngestWorker(new FileFeedAdapter(t -> Samples.stream("a1-golden.jsonl")), db, db, applier, IngestMetrics.NONE, CLOCK, 2).runOnce(T);
        assertThat(calls).containsExactly("project 1", "derive 1 [t1] cursor 0", "project 2", "derive 2 [t2] cursor 1");
    }

    // C7
    @Test
    void c7SameEventIdWithADifferentHashIsPoisonAndTheAppliedRowIsKept() {
        IngestWorker.Round r = run("hash-mismatch.jsonl");
        assertThat(r.applied()).isEqualTo(5);
        assertThat(stoppedBy(r)).contains(IngestErrorCode.HASH_MISMATCH);
        IngestEventRecord five = db.eventsOf(T).stream().filter(e -> e.seq() == 5).findFirst().orElseThrow();
        assertThat(five.status()).isEqualTo(IngestEventStatus.APPLIED);
        assertThat(five.payloadHash()).isEqualTo(PayloadHasher.hash(Samples.seq(5)));
        assertThat(failedRows()).isEmpty();
        assertThat(cursor().state()).isEqualTo(IngestState.FAILED);
        assertThat(cursor().lastSeq()).isEqualTo(5);
    }

    // C8
    @Test
    void c8AVersionAboveTheMaximumPausesTheTenantWithoutRows() {
        IngestWorker.Round r = run("version2.jsonl");
        assertThat(r.applied()).isEqualTo(4);
        assertThat(stoppedBy(r)).contains(IngestErrorCode.UNSUPPORTED_VERSION);
        assertThat(cursor().state()).isEqualTo(IngestState.PAUSED);
        assertThat(cursor().lastSeq()).isEqualTo(4);
        assertThat(db.eventsOf(T)).hasSize(4);
    }

    // C9 (워커 쪽 — 클라이언트 쪽은 계약 스위트 F5)
    @Test
    void c9AnotherTenantsFeedAppliesNothingAndFailsTheTenant() {
        IngestWorker.Round r = run("other-tenant.jsonl");
        assertThat(stoppedBy(r)).contains(IngestErrorCode.TENANT_MISMATCH);
        assertThat(db.eventsOf(T)).isEmpty();
        assertThat(recorder.applied()).isEmpty();
        assertThat(cursor().state()).isEqualTo(IngestState.FAILED);
        assertThat(cursor().lastSeq()).isZero();
    }

    // Q5
    @Test
    void q5AKnownEventIdAtAnotherSeqIsPoison() {
        run("dup.jsonl");
        ObjectNode moved = Samples.seq(2);
        moved.put("seq", 6);
        IngestApplier.Result r = applier(recorder).apply(T, moved);
        assertThat(r.code()).contains(IngestErrorCode.EVENT_SEQ_CONFLICT);
        assertThat(cursor().lastSeq()).isEqualTo(5);
        assertThat(cursor().errorCode()).contains(IngestErrorCode.EVENT_SEQ_CONFLICT);
    }

    @Test
    void anUnclassifiedErrorInTheApplyTransactionStopsTheTenant() {
        run("dup.jsonl");
        db.failNextMarkApplied(new IllegalStateException("duplicate key value violates unique constraint"));
        IngestApplier.Result r = applier(recorder).apply(T, Samples.seq(6));
        assertThat(r.code()).contains(IngestErrorCode.APPLY_ERROR);
        assertThat(cursor().error()).hasValueSatisfying(e -> assertThat(e).isEqualTo("APPLY_ERROR: seq 6 eventId "
                + Samples.seq(6).path("eventId").asString() + " IllegalStateException"));
        assertThat(appliedSeqs()).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void aBusyLockWritesNothing() {
        run("dup.jsonl");
        CursorRecord before = cursor();
        db.holdLockElsewhere(true);
        assertThat(applier(recorder).apply(T, Samples.seq(6)).outcome()).isEqualTo(IngestApplier.Outcome.LOCK_BUSY);
        assertThat(cursor()).isEqualTo(before);
        assertThat(appliedSeqs()).hasSize(5);
    }

    /** 재개 뒤 다시 온 실패 이벤트는 다시 판정되고, 적용되면 그 FAILED 행이 APPLIED가 된다. */
    @Test
    void afterAResumeTheFailedEventIsJudgedAgainAndItsRowBecomesApplied() {
        AtomicInteger calls = new AtomicInteger();
        Projector failOnceAt5 = (tenant, e) -> {
            if (e.seq() == 5 && calls.getAndIncrement() == 0) {
                throw new IllegalStateException("transient");
            }
            return recorder.apply(tenant, e);
        };
        IngestWorker w = worker(Samples.stream("a1-golden.jsonl"), failOnceAt5);
        assertThat(stoppedBy(w.runOnce(T))).contains(IngestErrorCode.PROJECTOR_ERROR);
        db.inTransaction(T, () -> {
            CursorRecord c = db.cursor().orElseThrow();
            db.saveCursor(c.lastSeq(), c.lastEventId(), IngestState.RUNNING, Optional.empty(), CLOCK.instant());
            return null;
        });
        IngestWorker.Round resumed = w.runOnce(T);
        assertThat(resumed.applied()).isEqualTo(13);
        assertThat(failedRows()).isEmpty();
        assertThat(appliedSeqs()).hasSize(17);
        assertThat(cursor().lastSeq()).isEqualTo(17);
    }

    // 피드 쪽 정지·일시 실패(상태 표)
    @Test
    void feedFailuresFollowTheStateTable() {
        IngestWorker unreachable = new IngestWorker(new FileFeedAdapter(t -> Path.of("/nonexistent/feed.jsonl")), db, db,
                applier(recorder), IngestMetrics.NONE, CLOCK, 200);
        IngestWorker.Round r = unreachable.runOnce(T);
        assertThat(r.feedUnavailable()).isTrue();
        assertThat(cursor().state()).isEqualTo(IngestState.RUNNING);

        HttpFeedClient noToken = new HttpFeedClient(HttpClient.newHttpClient(), t -> URI.create("http://127.0.0.1:9"),
                t -> Optional.empty(), Duration.ofSeconds(1));
        IngestWorker.Round paused = new IngestWorker(noToken, db, db, applier(recorder), IngestMetrics.NONE, CLOCK, 200).runOnce(T);
        assertThat(stoppedBy(paused)).contains(IngestErrorCode.TOKEN_MISSING);
        assertThat(cursor().state()).isEqualTo(IngestState.PAUSED);
    }

    @Test
    void afterSeqBeyondTheHeadFailsTheTenant() {
        run("dup.jsonl");
        db.inTransaction(T, () -> {
            db.saveCursor(40, Optional.empty(), IngestState.RUNNING, Optional.empty(), CLOCK.instant());
            return null;
        });
        assertThat(stoppedBy(run("dup.jsonl"))).contains(IngestErrorCode.AFTER_BEYOND_HEAD);
        assertThat(cursor().state()).isEqualTo(IngestState.FAILED);
    }

    @Test
    void theRecordedEnvelopeKeepsUnknownFields() {
        ObjectNode e = Samples.seq(1);
        e.put("traceId", "t-9");
        Envelope[] seen = new Envelope[1];
        Projector capture = (tenant, env) -> {
            seen[0] = env;
            return ProjectionResult.recordedOnly();
        };
        db.inTransaction(T, () -> db.ensureCursor(CLOCK.instant()));
        assertThat(applier(capture).apply(T, e).outcome()).isEqualTo(IngestApplier.Outcome.APPLIED);
        assertThat(seen[0].source().path("traceId").asString()).isEqualTo("t-9");
    }
}
