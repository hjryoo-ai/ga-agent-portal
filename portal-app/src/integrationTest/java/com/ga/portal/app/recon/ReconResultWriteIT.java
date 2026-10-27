package com.ga.portal.app.recon;

import com.ga.portal.recon.ReconRunner;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포털 Phase 7 C2(계획 ⑧): 대사는 세 정수의 동등 비교뿐 — 결과 행에는 포털 셋과 <b>비교한</b> 엔진 필드만 실리고 차이를 담는 열은 없다.
 * <ul>
 *   <li>{@code recon_result}의 열 = 키·포털 셋 셋·엔진 셋 넷({@code engine_net}은 비교하지 않으므로 늘 NULL — Q3)·상태·출처 — 차이·비율 열 0.</li>
 *   <li>DRIFT 행 하나: 포털 855,000·0·0, 엔진 1,800,000·0·0, {@code engine_net} NULL, BATCH·0.</li>
 * </ul>
 */
class ReconResultWriteIT {

    @Test
    void theRowHoldsThePortalSetAndTheComparedEngineFieldsOnly() {
        List<String> columns = ReconRig.DB.asMigrator(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT column_name || ':' || data_type FROM information_schema.columns
                     WHERE table_schema = current_schema() AND table_name = 'recon_result' ORDER BY column_name
                    """); ResultSet rs = ps.executeQuery()) {
                List<String> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
                return out;
            }
        });
        assertThat(columns).containsExactly("agent_id:text", "close_ym:text", "engine_adjustment:bigint", "engine_gross:bigint", "engine_net:bigint",
                "engine_offset:bigint", "portal_adjustment:bigint", "portal_gross:bigint", "portal_offset:bigint", "run_at:timestamp with time zone",
                "source:text", "source_seq:bigint", "status:text", "tenant_id:text");

        try (ReconRig r = new ReconRig("RCN_ROW", "a1", ReconRig.a1(), "{\"auto_rebuild_enabled\": false}").ingestAll()) {
            r.corruptLine("1");
            assertThat(r.run("202608", "202702")).isInstanceOf(ReconRunner.Outcome.Done.class);
            assertThat(r.reconResults()).singleElement().satisfies(line -> {
                String[] f = line.split("\\|", -1);
                assertThat(List.of(f[3], f[4], f[5])).as("portal set").containsExactly("855000", "0", "0");
                assertThat(List.of(f[6], f[7], f[8])).as("compared engine fields").containsExactly("1800000", "0", "0");
                assertThat(f[9]).as("engine_net is never compared (Q3)").isEqualTo("-");
                assertThat(List.of(f[10], f[11], f[12])).containsExactly("DRIFT", "BATCH", "0");
            });
        }
    }
}
