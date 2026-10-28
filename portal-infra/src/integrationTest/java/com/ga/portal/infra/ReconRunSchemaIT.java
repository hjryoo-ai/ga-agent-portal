package com.ga.portal.infra;

import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.SeedData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V8(포털 Phase 7 계획 P-7, 승인 — "값 열 0은 {@code CHECK} 시험으로"): {@code recon_run}의 열 집합은 닫혀 있고 금액·설계사·달 열이 없다(카탈로그
 * 대조), 수는 정수 ≥ 0, 닫힌 값 셋, 행위자 ↔ 시작 주체, 재구축 열 짝, 결과 ↔ 재구축 결과(P-14). 받는 행과 거부하는 행을 둘 다 본다.
 * 테넌트 격리는 {@code RlsIsolationIT}가 카탈로그의 모든 테넌트 표에 대해 본다({@code SeedData}에 이 표의 행이 있다).
 */
class ReconRunSchemaIT {

    private static final PostgresHarness DB = PostgresHarness.get();
    private static final String COLUMNS = "tenant_id, run_at, actor, trigger, checked, matched, drift_new, drift_unresolved, inconclusive, "
            + "engine_calls, result, rebuilt_at, rebuild_outcome";

    private static String tenant(String prefix) {
        String t = SeedData.uniqueTenant(prefix);
        DB.seed(t, c -> SeedData.exec(c, SeedData.ROWS.get("tenant"), t));
        return t;
    }

    /**
     * {@code spec} = 회차 시작(분)|행위자|시작 주체|검사,일치,새 DRIFT,미해결,판정 불가,호출|결과|재구축 시각 있음(y)|재구축 결과. 행위자 {@code recon:}은
     * {@code recon:<그 테넌트>}.
     */
    private static void insert(String t, String spec) {
        String[] p = spec.split("\\|", -1);
        String actor = p[1].equals("recon:") ? "'recon:' || ?" : "'" + p[1] + "'";
        String sql = "INSERT INTO recon_run (" + COLUMNS + ") VALUES (?, TIMESTAMPTZ '2026-10-27 04:" + p[0] + ":00+09:00', " + actor + ", '"
                + p[2] + "', " + p[3] + ", '" + p[4] + "', " + (p[5].isEmpty() ? "NULL" : "TIMESTAMPTZ '2026-10-27 04:30:00+09:00'") + ", "
                + (p[6].isEmpty() ? "NULL" : "'" + p[6] + "'") + ")";
        DB.seed(t, c -> {
            if (p[1].equals("recon:")) {
                SeedData.exec(c, sql, t, t);
            } else {
                SeedData.exec(c, sql, t);
            }
        });
    }

    private static long count(String t) {
        return DB.<Long>asApp(t, c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM recon_run WHERE tenant_id = ?")) {
                ps.setString(1, t);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        });
    }

    @Test
    void columnsAreTheClosedSetWithNoValueColumns() {
        List<String> columns = DB.asMigrator(c -> {
            List<String> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT column_name || ':' || data_type FROM information_schema.columns "
                    + "WHERE table_schema = 'public' AND table_name = 'recon_run' ORDER BY ordinal_position");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            return out;
        });
        assertThat(columns).containsExactly("tenant_id:text", "run_at:timestamp with time zone", "actor:text", "trigger:text", "checked:integer",
                "matched:integer", "drift_new:integer", "drift_unresolved:integer", "inconclusive:integer", "engine_calls:integer", "result:text",
                "rebuilt_at:timestamp with time zone", "rebuild_outcome:text");
    }

    @Test
    void runsOfEveryShapeAreAccepted() {
        String t = tenant("RRUN");
        insert(t, "00|recon:|SCHEDULE|4, 4, 0, 0, 0, 4|OK||");
        insert(t, "01|cli:ops-kim|CLI|4, 2, 1, 1, 1, 3|DRIFT||");
        insert(t, "02|recon:|SCHEDULE|4, 3, 1, 0, 0, 5|REPAIRED|y|SUCCEEDED");
        insert(t, "03|recon:|SCHEDULE|4, 3, 1, 1, 0, 4|FAILED|y|FAILED");
        insert(t, "04|recon:|SCHEDULE|4, 3, 1, 1, 0, 5|DRIFT|y|SUCCEEDED");
        insert(t, "05|cli:ops|CLI|0, 0, 0, 0, 0, 0|OK||");
        assertThat(count(t)).isEqualTo(6);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "10|recon:|SCHEDULE|-1, 0, 0, 0, 0, 0|OK||",          // 음수
            "11|recon:|SCHEDULE|1, 0, 0, 0, 0, -1|OK||",          // 음수(호출 수)
            "12|recon:|SCHEDULE|2, 2, 1, 0, 0, 2|DRIFT||",        // 일치 + 새 DRIFT + 판정 불가 > 검사
            "13|recon:|NIGHTLY|1, 1, 0, 0, 0, 1|OK||",            // 닫힌 시작 주체 밖
            "14|recon:|SCHEDULE|1, 1, 0, 0, 0, 1|MATCH||",        // 닫힌 결과 밖(recon_result 값)
            "15|recon:|SCHEDULE|1, 0, 1, 1, 0, 1|DRIFT|y|PARTIAL",// 닫힌 재구축 결과 밖
            "16|recon:OTHER|SCHEDULE|1, 1, 0, 0, 0, 1|OK||",      // 스케줄 회차의 행위자가 다른 테넌트
            "17|cli:ops|SCHEDULE|1, 1, 0, 0, 0, 1|OK||",          // 스케줄 회차인데 CLI 행위자
            "18|recon:|CLI|1, 1, 0, 0, 0, 1|OK||",                // CLI 회차인데 recon 행위자
            "19|cli:|CLI|1, 1, 0, 0, 0, 1|OK||",                  // CLI 행위자 형식 밖
            "20|recon:|SCHEDULE|1, 0, 1, 1, 0, 1|DRIFT|y|",       // 재구축 시각만
            "21|recon:|SCHEDULE|1, 0, 1, 1, 0, 1|DRIFT||SUCCEEDED",// 재구축 결과만
            "22|recon:|SCHEDULE|1, 0, 1, 1, 0, 1|FAILED||",       // 재구축 없이 FAILED
            "23|recon:|SCHEDULE|1, 0, 1, 1, 0, 1|DRIFT|y|FAILED", // 재구축 실패인데 FAILED 아님
            "24|recon:|SCHEDULE|1, 1, 0, 0, 0, 1|REPAIRED||",     // 재구축 없이 REPAIRED
    })
    void anInvalidRunIsRejected(String spec) {
        String t = tenant("RRUNX");
        assertThatThrownBy(() -> insert(t, spec)).hasMessageContaining("23514");
    }
}
