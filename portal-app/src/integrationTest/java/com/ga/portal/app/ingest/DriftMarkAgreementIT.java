package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.infra.persistence.ReconResultRepository;
import com.ga.portal.infra.persistence.ViewResetRepository;
import com.ga.portal.infra.testing.SeedData;
import com.ga.portal.recon.AgentMonth;
import com.ga.portal.recon.Episodes;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F11(포털 Phase 8 ①, 계획 P-6): 재구축 끝 불변식의 SQL({@link ViewResetRepository#restoreDriftMarks()})은 재구축기가 대사에 닿을 수 없어
 * (ArchUnit — "the rebuilder never reaches the reconciler") 대사의 순수 함수 {@link Episodes#unresolved}를 부르지 못한다. 둘이 같은 답인지
 * 격자로 묶는다: (설계사, 달)마다 DRIFT·REPAIRED 행을 시각 셋 중에서 0~3개(259가지 — 같은 시각·같은 상태의 반복, 출처 BATCH·EVENT 섞음),
 * 모든 달이 OK인 상태에서 복원한 뒤 DRIFT가 된 달의 집합 = {@code Episodes}의 미해결 집합.
 */
class DriftMarkAgreementIT {

    private static final String[] TOKENS = {"DRIFT@1", "DRIFT@2", "DRIFT@3", "REPAIRED@1", "REPAIRED@2", "REPAIRED@3"};

    private final IngestRig rig = new IngestRig();

    private static List<List<String>> sequences() {
        List<List<String>> out = new ArrayList<>();
        out.add(List.of());
        List<List<String>> previous = List.of(List.of());
        for (int length = 1; length <= 3; length++) {
            List<List<String>> next = new ArrayList<>();
            for (List<String> p : previous) {
                for (String token : TOKENS) {
                    List<String> s = new ArrayList<>(p);
                    s.add(token);
                    next.add(s);
                }
            }
            out.addAll(next);
            previous = next;
        }
        return out;
    }

    @Test
    void theRestoreStatementAgreesWithEpisodesOnEveryGridCell() {
        TenantId t = TenantId.of(SeedData.uniqueTenant("DRIFTGRID"));
        IngestRig.seedTenant(t, "http://engine.invalid", "{}");
        List<List<String>> grid = sequences();
        assertThat(grid).hasSize(259);
        IngestRig.DB.seed(t.value(), c -> {
            for (int i = 0; i < grid.size(); i++) {
                String agent = "G-%03d".formatted(i);
                SeedData.exec(c, """
                        INSERT INTO earnings_month (tenant_id, agent_id, close_ym, status, data_status, last_seq)
                        VALUES (?, ?, '202608', 'CONFIRMED', 'OK', 1)
                        """, t.value(), agent);
                List<String> rows = grid.get(i);
                for (int j = 0; j < rows.size(); j++) {
                    String[] token = rows.get(j).split("@");
                    SeedData.exec(c, """
                            INSERT INTO recon_result (tenant_id, agent_id, close_ym, run_at, portal_gross, portal_offset, portal_adjustment,
                                                      engine_gross, engine_offset, engine_adjustment, status, source, source_seq)
                            VALUES (?, ?, '202608', ?::timestamptz, 1, 0, 0, 2, 0, 0, ?, ?, ?)
                            """, t.value(), agent, "2026-10-1" + token[1] + " 19:00:00+00", token[0], j % 2 == 0 ? "BATCH" : "EVENT",
                            j % 2 == 0 ? 0 : j);
                }
            }
        });
        ReconResultRepository recon = new ReconResultRepository(rig.gateway);
        Set<String> expected = Episodes.unresolved(rig.transactions.inTransaction(t, recon::driftAndRepaired), List.of()).keySet().stream()
                .map(AgentMonth::agentId).collect(Collectors.toCollection(TreeSet::new));

        int changed = rig.transactions.inTransaction(t, new ViewResetRepository(rig.gateway)::restoreDriftMarks);

        Set<String> marked = IngestRig.DB.asApp(t.value(), c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT agent_id FROM earnings_month WHERE tenant_id = ? AND data_status = 'DRIFT' ORDER BY agent_id")) {
                ps.setString(1, t.value());
                Set<String> out = new TreeSet<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(rs.getString(1));
                    }
                }
                return out;
            }
        });
        assertThat(expected).as("the grid has both answers").isNotEmpty().hasSizeLessThan(grid.size());
        assertThat(marked).isEqualTo(expected);
        assertThat(changed).isEqualTo(expected.size());
        assertThat(rig.transactions.inTransaction(t, new ViewResetRepository(rig.gateway)::restoreDriftMarks)).as("idempotent").isZero();
    }
}
