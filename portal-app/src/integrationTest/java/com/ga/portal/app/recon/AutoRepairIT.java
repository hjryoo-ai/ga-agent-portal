package com.ga.portal.app.recon;

import com.ga.portal.app.ingest.IngestRigAccess;
import com.ga.portal.domain.RebuildOutcome;
import com.ga.portal.domain.ReconRunResult;
import com.ga.portal.projection.testing.Envelopes;
import com.ga.portal.recon.RunRecord;
import com.ga.portal.recon.ReconRunner;
import com.ga.portal.recon.testing.StubStatementEngine;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포털 Phase 7 C5(계획 ③ R4~R6, Q1·Q14, 승인 조건 A): 실제 재구축기(txA/txB/txC, 행위자 {@code recon:<tenant>})로 복구한다.
 * <ul>
 *   <li>자동 끔으로 DRIFT → 켜고 다시 → 재구축 → 재대사 REPAIRED + {@code data_status = OK}, 알림은 이미 있던 같은 키라 행 추가 0(Phase 6 수용 Q1).</li>
 *   <li>재대사가 판정 불가면 {@code recon_result} 행 0, 그러나 재구축이 지운 {@code data_status}를 DRIFT로 다시 쓴다(조건 A·P-6).</li>
 *   <li>스트림 자체의 불일치(포털 손 스트림 {@code edges} — GA04)는 재구축 뒤에도 DRIFT, 시도함이 되어 같은 날·다음 날 재구축 0(Q14 — 지시문 §0).</li>
 * </ul>
 */
class AutoRepairIT {

    private static RunRecord done(ReconRunner.Outcome o) {
        assertThat(o).isInstanceOf(ReconRunner.Outcome.Done.class);
        return ((ReconRunner.Outcome.Done) o).run();
    }

    @Test
    void turningAutoRebuildOnRepairsTheCorruptedMonthWithoutNewNotifications() {
        try (ReconRig r = new ReconRig("RCN_FIX", "a1", ReconRig.a1(), "{\"auto_rebuild_enabled\": false}").ingestAll()) {
            List<String> notifications = IngestRigAccess.notificationRows(r.tenant);
            assertThat(notifications).anyMatch(n -> n.contains("|STMT_CONFIRMED:202608|")).anyMatch(n -> n.contains("|PAYOUT:202608|"));
            r.corruptLine("1");
            RunRecord off = done(r.run("202608", "202702"));
            assertThat(off.result()).isEqualTo(ReconRunResult.DRIFT);
            assertThat(r.rebuildActors()).isEmpty();
            assertThat(r.dataStatus("A-1001", "202608")).isEqualTo("DRIFT");

            r.params("{\"auto_rebuild_enabled\": true}");
            r.clock.now = r.clock.now.plusSeconds(600);
            RunRecord on = done(r.run("202608", "202702"));
            assertThat(r.rebuildActors()).containsExactly("recon:" + r.tenant.value());
            assertThat(on.rebuildOutcome()).contains(RebuildOutcome.SUCCEEDED);
            assertThat(on.result()).isEqualTo(ReconRunResult.REPAIRED);
            assertThat(on.driftUnresolved()).isZero();
            assertThat(r.reconResults()).extracting(l -> l.split("\\|", -1)[10]).containsExactly("DRIFT", "REPAIRED");
            assertThat(r.reconResults().get(1).split("\\|", -1)).satisfies(f -> {
                assertThat(List.of(f[0], f[1], f[3], f[6], f[9], f[11], f[12])).containsExactly("A-1001", "202608", "1800000", "1800000", "-", "BATCH", "0");
            });
            assertThat(r.dataStatus("A-1001", "202608")).isEqualTo("OK");
            assertThat(IngestRigAccess.notificationRows(r.tenant)).as("same keys — ON CONFLICT, no new row (Q1)").isEqualTo(notifications);
            assertThat(r.audits("recon:" + r.tenant.value(), ReconRig.rebuildSucceeded())).isEqualTo(1);
            assertThat(r.reconRuns()).hasSize(2).last().satisfies(l -> assertThat(l).contains("|REPAIRED|").endsWith("|SUCCEEDED"));
            assertThat(r.counter("recon_runs_total", "tenant", r.tenant.value(), "result", "REPAIRED")).isEqualTo(1.0);
        }
    }

    @Test
    void anInconclusiveRecheckWritesNoRowButKeepsTheMonthDrift() {
        try (ReconRig r = new ReconRig("RCN_CONDA", "a1", ReconRig.a1(), "{\"auto_rebuild_enabled\": true}").ingestAll()) {
            r.corruptLine("1");
            r.wrapRebuild(real -> (t, actor) -> {
                Optional<RebuildOutcome> o = real.rebuild(t, actor);
                r.stub.mode(StubStatementEngine.Mode.DOWN);   // 재구축 뒤 엔진이 내려갔다
                return o;
            });
            RunRecord run = done(r.run("202608", "202702"));
            assertThat(run.rebuildOutcome()).contains(RebuildOutcome.SUCCEEDED);
            assertThat(r.reconResults()).as("condition A — only the first DRIFT row").singleElement()
                    .satisfies(l -> assertThat(l.split("\\|", -1)[10]).isEqualTo("DRIFT"));
            assertThat(r.dataStatus("A-1001", "202608")).as("the rebuild wiped it, the recheck wrote it back (P-6)").isEqualTo("DRIFT");
            assertThat(run.result()).isEqualTo(ReconRunResult.DRIFT);
            assertThat(r.counter("recon_inconclusive_total", "tenant", r.tenant.value(), "reason", "ENGINE_UNAVAILABLE")).isEqualTo(1.0);
        }
    }

    @Test
    void aDriftInTheStreamItselfIsTriedOnceAndNotRebuiltAgain() {
        List<ObjectNode> edges = new ArrayList<>(Envelopes.nodes(Path.of(System.getProperty("ga.repoRoot")).resolve("web/e2e/streams/edges.jsonl")));
        String params = "{\"auto_rebuild_enabled\": true, \"recon_caught_up_within_seconds\": 259200}";
        try (ReconRig r = new ReconRig("RCN_TRY", "a1", edges, params).ingestAll()) {
            assertThat(r.reconResults()).as("EVENT DRIFT 202608 from the stream (999,000 vs 570,000)").singleElement()
                    .satisfies(l -> assertThat(l).contains("|DRIFT|EVENT|"));
            RunRecord first = done(r.run("202608"));
            assertThat(first.driftNew()).as("the EVENT DRIFT was already unresolved").isZero();
            assertThat(r.rebuildActors()).as("untried — one rebuild").hasSize(1);
            assertThat(first.rebuildOutcome()).contains(RebuildOutcome.SUCCEEDED);
            assertThat(first.result()).isEqualTo(ReconRunResult.DRIFT);
            assertThat(r.dataStatus("A-1001", "202608")).isEqualTo("DRIFT");
            assertThat(r.reconResults()).extracting(l -> l.split("\\|", -1)[11]).containsExactly("EVENT", "BATCH");

            r.clock.now = r.clock.now.plusSeconds(600);
            done(r.run("202608"));
            r.clock.now = r.clock.now.plus(java.time.Duration.ofDays(1));
            RunRecord nextDay = done(r.run("202608"));
            assertThat(r.rebuildActors()).as("tried (Q14) — not rebuilt the same day nor the next").hasSize(1);
            assertThat(nextDay.result()).isEqualTo(ReconRunResult.DRIFT);
            assertThat(nextDay.driftUnresolved()).isEqualTo(1);
            assertThat(r.reconResults()).as("same values — no new rows (Q13)").hasSize(2);
        }
    }
}
