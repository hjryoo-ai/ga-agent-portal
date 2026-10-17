package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.infra.testing.SeedData;
import com.ga.portal.ingest.apply.Rebuilder;
import com.ga.portal.ingest.feed.FileFeedAdapter;
import com.ga.portal.projection.testing.Envelopes;
import tools.jackson.databind.node.ObjectNode;
import com.ga.portal.projection.RecordingProjector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C13 결정론: 같은 스트림(A-1 17건)을 ① 한 번에 ② 9건 뒤 워커 재시작(새 부품)으로 넣은 두 테넌트의 {@code ingest_event}·{@code ingest_cursor}가
 * 같다(테넌트 컬럼 제외, 고정 시계라 시각까지). 같은 스트림을 다시 넣어도 바뀌지 않는다.
 * Phase 2 D-12: 뷰 9표 전부(골든 정규형 — {@code source_seq}·{@code last_seq} 포함)가 ① 한 번에 ② 재시작 ③ 한 번에 + 재구축 세 테넌트에서 같다.
 */
class IngestDeterminismIT {

    @Test
    void c13TheSameStreamGivesTheSameRowsWithOrWithoutARestart() {
        IngestRig rig = new IngestRig();
        TenantId once = TenantId.of(SeedData.uniqueTenant("DET_1"));
        TenantId restarted = TenantId.of(SeedData.uniqueTenant("DET_2"));
        FileFeedAdapter feed = IngestRig.feed("a1-golden.jsonl");

        rig.worker(feed, new RecordingProjector(), 200).runOnce(once);

        rig.worker(feed, new RecordingProjector(), 9).runOnce(restarted);
        assertThat(rig.cursor(restarted).lastSeq()).isEqualTo(9);
        IngestRig afterRestart = new IngestRig();
        afterRestart.worker(feed, new RecordingProjector(), 200).runOnce(restarted);

        assertThat(rig.eventRows(restarted)).hasSize(17).containsExactlyElementsOf(rig.eventRows(once));
        assertThat(rig.cursor(restarted)).isEqualTo(rig.cursor(once));

        // 같은 스트림을 다시: 아무것도 바뀌지 않는다
        var before = rig.eventRows(once);
        var cursorBefore = rig.cursor(once);
        assertThat(rig.worker(feed, new RecordingProjector(), 200).runOnce(once).applied()).isZero();
        assertThat(rig.eventRows(once)).isEqualTo(before);
        assertThat(rig.cursor(once)).isEqualTo(cursorBefore);
        assertThat(cursorBefore.error()).isEqualTo(Optional.empty());
    }

    @Test
    void d12ViewsAreTheSameAcrossTwoRunsARestartAndARebuild() {
        IngestRig rig = new IngestRig();
        List<ObjectNode> stream = Envelopes.nodes(Envelopes.stream("engine-e1-cycle.jsonl"));
        TenantId once = TenantId.of(SeedData.uniqueTenant("DET_V1"));
        TenantId restarted = TenantId.of(SeedData.uniqueTenant("DET_V2"));
        TenantId rebuilt = TenantId.of(SeedData.uniqueTenant("DET_V3"));

        rig.worker(ViewRig.feed(once, stream), ViewRig.projector(rig.gateway), 200).runOnce(once);
        rig.worker(ViewRig.feed(restarted, stream), ViewRig.projector(rig.gateway), 4).runOnce(restarted);
        IngestRig afterRestart = new IngestRig();
        afterRestart.worker(ViewRig.feed(restarted, stream), ViewRig.projector(afterRestart.gateway), 200).runOnce(restarted);
        rig.worker(ViewRig.feed(rebuilt, stream), ViewRig.projector(rig.gateway), 200).runOnce(rebuilt);
        assertThat(rig.rebuilder(ViewRig.feed(rebuilt, stream), ViewRig.projector(rig.gateway), 3, 50_000).rebuild(rebuilt, (step, target) -> {
        })).isInstanceOf(Rebuilder.Result.Rebuilt.class);

        Map<String, List<String>> views = ViewRig.views(once);
        assertThat(views.get("earnings_line")).isNotEmpty();
        assertThat(rig.cursor(once).lastSeq()).isEqualTo(stream.size());
        for (TenantId other : List.of(restarted, rebuilt)) {
            assertThat(ViewRig.views(other)).as(other.value()).isEqualTo(views);
            assertThat(rig.eventRows(other)).as(other.value()).isEqualTo(rig.eventRows(once));
            assertThat(rig.cursor(other)).as(other.value()).isEqualTo(rig.cursor(once));
        }
    }
}
