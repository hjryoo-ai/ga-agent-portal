package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.IngestState;
import com.ga.portal.infra.testing.SeedData;
import com.ga.portal.ingest.apply.IngestWorker;
import com.ga.portal.projection.testing.Envelopes;
import com.ga.portal.projection.testing.GoldenViews;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C1·C3(DB 쪽, 계획 ⑨ 더함): 인메모리 골든과 같은 파일을 실제 인제스트(검증·해시·seq 규칙·RLS·이벤트마다 트랜잭션)와 PostgreSQL 뷰 저장소로
 * 돌려 같은 행이 나온다 — 인메모리만 맞고 SQL이 틀리는 경우를 막는다.
 */
class ProjectionGoldenIT {

    private final IngestRig rig = new IngestRig();

    private Map<String, List<String>> ingest(String prefix, Path stream, int events) {
        TenantId t = TenantId.of(SeedData.uniqueTenant(prefix));
        IngestWorker.Round round = rig.worker(ViewRig.feed(t, stream), rig.applier(ViewRig.projector(rig.gateway)), 200).runOnce(t);
        assertThat(round.stoppedBy()).isEmpty();
        assertThat(round.applied()).isEqualTo(events);
        assertThat(rig.cursor(t).state()).isEqualTo(IngestState.RUNNING);
        return ViewRig.views(t);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a1", "engine-e1-cycle", "engine-e2-estimate/estimate_cycle_01_기본", "engine-e2-estimate/estimate_cycle_02_채권_상계", "engine-e2-estimate/estimate_cycle_03_정정_포함", "engine-e2-estimate/estimate_cycle_04_음수_순액_채권화", "engine-e2-estimate/estimate_cycle_05_분급_도래"})
    void c1TheDatabaseViewsEqualTheGoldenFiles(String stream) {
        Path file = stream.equals("a1") ? Envelopes.a1() : Envelopes.stream(stream + ".jsonl");
        GoldenViews.assertMatches(stream, ingest("GOLD", file, Envelopes.nodes(file).size()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a1-interleaved", "a1-posted-first"})
    void c3LineOrdersGiveTheSameDatabaseViews(String stream) {
        assertThat(GoldenViews.withoutSeqs(ingest("ORDER", Envelopes.stream(stream + ".jsonl"), 11)))
                .isEqualTo(GoldenViews.withoutSeqs(ingest("ORDER", Envelopes.a1(), 11)));
    }
}
