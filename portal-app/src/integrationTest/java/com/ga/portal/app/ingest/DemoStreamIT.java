package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.MonthStatus;
import com.ga.portal.infra.persistence.ViewResetRepository;
import com.ga.portal.infra.testing.SeedData;
import com.ga.portal.ingest.apply.IngestWorker;
import com.ga.portal.ingest.apply.Rebuilder;
import com.ga.portal.ingest.feed.FileFeedAdapter;
import com.ga.portal.projection.testing.Envelopes;
import com.ga.portal.projection.testing.GoldenViews;
import com.ga.portal.recon.engine.StatementResponses;
import com.ga.portal.recon.rule.PortalMonth;
import com.ga.portal.recon.rule.ReconMode;
import com.ga.portal.recon.rule.ReconRules;
import com.ga.portal.recon.rule.Verdict;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포털 Phase 8 ⓪c(지시문 C2, 계획 P-8): 데모 테넌트 스트림 전체(17,856건)를 실제 PostgreSQL(portal_app·RLS)에 운영 파생기와 함께 적재한다.
 * <ul>
 *   <li>전수 불변식({@link ViewResetRepository#violations()} — 월 합 = Σ 라인, 원장 누적 = Σ 전기, 전기 연쇄) 위반 0.</li>
 *   <li>DB 뷰 9표의 정규형 = 인메모리 투영의 요약 골든({@code DemoStreamTest}와 같은 파일 — 두 저장소가 같은 뷰).</li>
 *   <li>대사 규칙: 모든 {@code earnings_month} 행 × 엔진 명세 샘플({@code statements.json}) = MATCH(716건 — 확정 686·잠정 30).</li>
 *   <li>운영자 재구축(한 트랜잭션, 상한 50,000)이 같은 뷰를 다시 만든다. 적재·재구축 시간은 시험 출력에 남긴다(설계서 §14.1 #11 실측 — 보고서).</li>
 * </ul>
 */
class DemoStreamIT {

    private static final int EVENTS = 17_856;
    private final IngestRig rig = new IngestRig();

    @Test
    void theWholeDemoStreamIngestsReconcilesToMatchAndRebuildsToTheSameViews() {
        TenantId t = TenantId.of(SeedData.uniqueTenant("DEMO"));
        IngestRig.seedTenant(t);
        FileFeedAdapter feed = ViewRig.feed(t, Envelopes.demo());
        IngestWorker worker = rig.derivingWorker(feed, 1_000);

        long started = System.nanoTime();
        int applied = 0;
        while (true) {
            IngestWorker.Round round = worker.runOnce(t);
            assertThat(round.stoppedBy()).as("no poison event").isEmpty();
            if (round.applied() == 0) {
                break;
            }
            applied += round.applied();
        }
        long ingestMs = (System.nanoTime() - started) / 1_000_000;
        assertThat(applied).isEqualTo(EVENTS);
        assertThat(rig.cursor(t).lastSeq()).isEqualTo(EVENTS);

        assertThat(rig.transactions.inTransaction(t, new ViewResetRepository(rig.gateway)::violations)).as("invariants").isEmpty();
        Map<String, List<String>> views = ViewRig.views(t);
        GoldenViews.assertSummary("demo", views, Set.of("A-2001", "A-2009"));
        assertThat(notifications(t)).as("derived and stored (DemoStreamTest counts 1,731 candidates; keys dedupe)").isPositive();

        Map<String, String> statements = new HashMap<>();
        Path file = Envelopes.repoRoot().resolve("contracts/shared/ga-commission-engine/samples/demo/statements.json");
        for (JsonNode s : Envelopes.JSON.readTree(file.toFile())) {
            statements.put(s.path("agentId").asString() + "|" + s.path("ym").asString() + "|" + s.path("mode").asString(), s.path("response").toString());
        }
        int matched = 0;
        for (String row : views.get("earnings_month")) {
            JsonNode m = Envelopes.JSON.readTree(row);
            MonthStatus status = MonthStatus.valueOf(m.path("status").asString());
            ReconMode mode = ReconMode.of(status);
            String key = m.path("agent_id").asString() + "|" + m.path("close_ym").asString() + "|" + mode.name();
            assertThat(statements).as("engine sample for " + key).containsKey(key);
            Verdict v = ReconRules.judge(EVENTS, new PortalMonth(status, m.path("gross").asLong(), m.path("clawback_offset").asLong(),
                            m.path("adjustment").asLong()),
                    StatementResponses.read(m.path("agent_id").asString(), m.path("close_ym").asString(), mode, 200,
                            statements.get(key).getBytes(StandardCharsets.UTF_8)), () -> OptionalLong.of(EVENTS));
            assertThat(v).as(key).isEqualTo(Verdict.MATCH);
            matched++;
        }
        assertThat(matched).isEqualTo(716);

        long rebuildStarted = System.nanoTime();
        Rebuilder.Result rebuilt = rig.rebuilder(feed, ViewRig.projector(rig.gateway), 1_000, 50_000).rebuild(t, (step, target) -> { });
        long rebuildMs = (System.nanoTime() - rebuildStarted) / 1_000_000;
        assertThat(rebuilt).isEqualTo(new Rebuilder.Result.Rebuilt(EVENTS, EVENTS));
        assertThat(ViewRig.views(t)).as("the rebuild gives the same views").isEqualTo(views);
        System.out.println("DemoStreamIT events=" + EVENTS + " ingestMs=" + ingestMs + " rebuildMs=" + rebuildMs + " months=" + matched);
    }

    private static long notifications(TenantId t) {
        return IngestRig.DB.asApp(t.value(), c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM notification WHERE tenant_id = ?")) {
                ps.setString(1, t.value());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        });
    }
}
