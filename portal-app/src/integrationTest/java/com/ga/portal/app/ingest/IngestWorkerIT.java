package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.CursorRecord;
import com.ga.portal.domain.IngestErrorCode;
import com.ga.portal.domain.IngestState;
import com.ga.portal.infra.testing.SeedData;
import com.ga.portal.ingest.PayloadHasher;
import com.ga.portal.ingest.apply.IngestWorker;
import com.ga.portal.ingest.feed.FileFeedAdapter;
import com.ga.portal.ingest.testing.Samples;
import com.ga.portal.projection.Projector;
import com.ga.portal.projection.RecordingProjector;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 PostgreSQL에서 C3(갭)·C4(배치 중간 실패 뒤 재시작 정합)·C6(독 — FAILED 행은 별도 트랜잭션에 남는다)·C7(해시)·C11(두 워커 동시 실행 →
 * 각 이벤트 적용 1회, 멀쩡한 테넌트를 FAILED로 만들지 않는다). 트랜잭션은 portal_app + TenantSessionBinder(RLS 대상).
 */
class IngestWorkerIT {

    private final IngestRig rig = new IngestRig();

    private static TenantId tenant(String prefix) {
        return TenantId.of(SeedData.uniqueTenant(prefix));
    }

    private static FileFeedAdapter file(String stream) {
        return IngestRig.feed(stream);
    }

    private static long count(List<RecordingProjector.Applied> applied, long seq) {
        return applied.stream().filter(a -> a.seq() == seq).count();
    }

    // C3
    @Test
    void c3AGapStopsTheTenantWithTheCursorWhereItWas() {
        TenantId t = tenant("IW_GAP");
        RecordingProjector p = new RecordingProjector();
        IngestWorker.Round r = rig.worker(file("gap.jsonl"), p, 200).runOnce(t);
        assertThat(r.applied()).isEqualTo(4);
        CursorRecord c = rig.cursor(t);
        assertThat(c.state()).isEqualTo(IngestState.FAILED);
        assertThat(c.lastSeq()).isEqualTo(4);
        assertThat(c.errorCode()).contains(IngestErrorCode.SEQ_GAP);
        assertThat(rig.eventRows(t)).hasSize(4);
        assertThat(rig.worker(file("gap.jsonl"), p, 200).runOnce(t).polled()).isFalse();
        assertThat(p.applied(t)).hasSize(4);
    }

    // C4
    @Test
    void c4AFailureMidBatchKeepsEarlierEventsAndAResumedWorkerContinuesExactly() {
        TenantId t = tenant("IW_RST");
        RecordingProjector p = new RecordingProjector();
        AtomicBoolean failAt6 = new AtomicBoolean(true);
        Projector projector = (tenant, e) -> {
            if (e.seq() == 6 && failAt6.getAndSet(false)) {
                throw new IllegalStateException("crash mid-batch");
            }
            return p.apply(tenant, e);
        };
        IngestWorker.Round first = rig.worker(file("a1-golden.jsonl"), projector, 200).runOnce(t);
        assertThat(first.applied()).isEqualTo(5);
        CursorRecord stopped = rig.cursor(t);
        assertThat(stopped.lastSeq()).isEqualTo(5);
        assertThat(stopped.lastEventId()).contains(Samples.seq(5).path("eventId").asString());
        assertThat(stopped.errorCode()).contains(IngestErrorCode.PROJECTOR_ERROR);
        assertThat(rig.events(t, "APPLIED")).extracting(e -> e.seq()).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(rig.events(t, "FAILED")).singleElement().satisfies(f -> assertThat(f.seq()).isEqualTo(6));

        // 운영자 재개(커서 5 그대로 RUNNING) 뒤 새 워커(재시작): 6부터 정확히 이어진다
        rig.transactions.inTransaction(t, () -> {
            rig.store.saveCursor(5, stopped.lastEventId(), IngestState.RUNNING, Optional.empty(), IngestRig.CLOCK.instant());
            return null;
        });
        IngestWorker.Round resumed = rig.worker(file("a1-golden.jsonl"), projector, 200).runOnce(t);
        assertThat(resumed.applied()).isEqualTo(12);
        assertThat(rig.cursor(t).lastSeq()).isEqualTo(17);
        assertThat(rig.events(t, "FAILED")).isEmpty();
        assertThat(rig.events(t, "APPLIED")).hasSize(17);
        for (long s = 1; s <= 17; s++) {
            assertThat(count(p.applied(t), s)).as("projector calls for seq %d", s).isEqualTo(1);
        }
    }

    // C6
    @Test
    void c6APoisonEventsFailedRowSurvivesTheRollbackOfItsTransaction() {
        TenantId t = tenant("IW_PSN");
        IngestWorker.Round r = rig.worker(file("poison-schema.jsonl"), new RecordingProjector(), 200).runOnce(t);
        assertThat(r.applied()).isEqualTo(4);
        assertThat(rig.cursor(t).errorCode()).contains(IngestErrorCode.SCHEMA_VIOLATION);
        assertThat(rig.events(t, "APPLIED")).hasSize(4);
        assertThat(rig.events(t, "FAILED")).singleElement().satisfies(f -> {
            assertThat(f.seq()).isEqualTo(5);
            assertThat(f.payloadHash()).isEqualTo(PayloadHasher.hash(Samples.JSON.readTree(lineOf("poison-schema.jsonl", 5))));
        });
    }

    // C7
    @Test
    void c7AHashMismatchKeepsTheAppliedRow() {
        TenantId t = tenant("IW_HSH");
        rig.worker(file("hash-mismatch.jsonl"), new RecordingProjector(), 200).runOnce(t);
        assertThat(rig.cursor(t).errorCode()).contains(IngestErrorCode.HASH_MISMATCH);
        assertThat(rig.events(t, "APPLIED")).filteredOn(e -> e.seq() == 5).singleElement()
                .satisfies(e -> assertThat(e.payloadHash()).isEqualTo(PayloadHasher.hash(Samples.seq(5))));
        assertThat(rig.events(t, "FAILED")).isEmpty();
    }

    // C11
    @Test
    void c11TwoWorkersRacingOnOneTenantApplyEachEventOnceAndLeaveItRunning() throws Exception {
        TenantId t = tenant("IW_TWO");
        RecordingProjector p = new RecordingProjector();
        CountDownLatch firstInside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Projector holdOnFirstEvent = (tenant, e) -> {
            if (e.seq() == 1) {
                firstInside.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            return p.apply(tenant, e);
        };
        rig.transactions.inTransaction(t, () -> rig.store.ensureCursor(IngestRig.CLOCK.instant()));
        IngestWorker first = rig.worker(file("a1-golden.jsonl"), holdOnFirstEvent, 200);
        IngestWorker second = rig.worker(file("a1-golden.jsonl"), p, 200);

        CompletableFuture<IngestWorker.Round> a = CompletableFuture.supplyAsync(() -> first.runOnce(t));
        assertThat(firstInside.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<IngestWorker.Round> b = CompletableFuture.supplyAsync(() -> second.runOnce(t));
        try {
            b.get(3, TimeUnit.SECONDS);     // 잠금이 있으면 바로 끝난다(LOCK_BUSY). 없으면 PK에서 기다린다
        } catch (TimeoutException expectedWithoutTheLock) {
            // 첫 워커를 풀어 경합을 끝까지 진행시킨다 — 단언이 결과를 본다
        }
        release.countDown();
        IngestWorker.Round ra = a.get(30, TimeUnit.SECONDS);
        IngestWorker.Round rb = b.get(30, TimeUnit.SECONDS);
        IngestWorker.Round later = second.runOnce(t);

        assertThat(rb.applied()).as("second worker while the first held the tenant").isZero();
        assertThat(rb.stoppedBy()).as("second worker must not stop a healthy tenant").isEmpty();
        assertThat(ra.applied() + rb.applied() + later.applied()).isEqualTo(17);
        CursorRecord c = rig.cursor(t);
        assertThat(c.state()).isEqualTo(IngestState.RUNNING);
        assertThat(c.error()).isEmpty();
        assertThat(c.lastSeq()).isEqualTo(17);
        assertThat(rig.events(t, "FAILED")).isEmpty();
        for (long s = 1; s <= 17; s++) {
            assertThat(count(p.applied(t), s)).as("projector calls for seq %d", s).isEqualTo(1);
        }
    }

    private static String lineOf(String stream, int seq) {
        try {
            return Files.readAllLines(Samples.stream(stream)).stream().skip(1)
                    .filter(l -> Samples.JSON.readTree(l).path("seq").asInt() == seq).findFirst().orElseThrow();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
