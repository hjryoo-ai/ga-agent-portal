package com.ga.portal.infra;

import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.SeedData;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V4(Phase 2 계획 ⑧): 키·NULL 허용·새 컬럼이 계획대로다 — 새 표가 없으므로 RLS·GRANT는 V2 그대로({@link RlsIsolationIT}).
 * 넣는 행은 테넌트를 바인딩한 마이그레이터로 커밋하고(FORCE RLS), 거부는 앱 롤로 본다(운영 경로와 같은 권한).
 */
class ProjectionSchemaIT {

    private static final PostgresHarness DB = PostgresHarness.get();

    @Test
    void historyAndReconKeysAreTheEventSeqAndTheSource() {
        assertThat(primaryKey("policy_limit_history")).containsExactly("tenant_id", "ledger_id", "source_seq");
        assertThat(primaryKey("recon_result")).containsExactly("tenant_id", "agent_id", "close_ym", "run_at", "source", "source_seq");
    }

    @Test
    void nullableAndNewColumnsFollowThePlan() {
        Map<String, String> nullable = DB.asMigrator(c -> {
            Map<String, String> out = new TreeMap<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT table_name || '.' || column_name, is_nullable FROM information_schema.columns WHERE table_schema = 'public'");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString(1), rs.getString(2));
                }
            }
            return out;
        });
        assertThat(nullable).containsEntry("agent_profile.role", "YES")              // O-1, D-8
                .containsEntry("policy_limit_history.posting_seq", "YES")             // REBASE without a posting seq
                .containsEntry("policy_limit_history.source_seq", "NO")
                .containsEntry("recon_result.source", "NO").containsEntry("recon_result.source_seq", "NO")
                .containsEntry("policy_flag.reason", "YES");
        for (String c : List.of("receivable_offset", "carried_receivable", "payable", "run_seq", "estimate_basis_seq", "net_basis")) {
            assertThat(nullable).containsEntry("earnings_month." + c, "YES");
        }
    }

    /** 승인 Q2·계획 ⑧: 재산정 이력은 전기 순번 없이, 같은 원장의 같은 순번 둘(전기·재산정)은 seq로 갈린다. */
    @Test
    void aRebaseWithoutAPostingSeqAndAPostWithTheSamePostingSeqBothFit() {
        String t = SeedData.uniqueTenant("V4_H");
        DB.seed(t, c -> {
            SeedData.exec(c, SeedData.ROWS.get("tenant"), t);
            exec(c, "INSERT INTO policy_limit_history (tenant_id, ledger_id, source_seq, posting_seq, kind, calc_id, amount, accum_after, limit_after) "
                    + "VALUES (?, '1', 4, 1, 'POST', '1', 945000, 945000, 1800000)", t);
            exec(c, "INSERT INTO policy_limit_history (tenant_id, ledger_id, source_seq, posting_seq, kind, accum_after, limit_after, reason) "
                    + "VALUES (?, '1', 11, 1, 'REBASE', 945000, 1500000, 'PREMIUM_REDUCED')", t);
            exec(c, "INSERT INTO policy_limit_history (tenant_id, ledger_id, source_seq, kind, accum_after, limit_after, reason) "
                    + "VALUES (?, '2', 12, 'REBASE', 0, 1200000, 'PREMIUM_REDUCED')", t);
        });
        assertThatThrownBy(() -> DB.asApp(t, c -> {
            exec(c, "INSERT INTO policy_limit_history (tenant_id, ledger_id, source_seq, kind) VALUES (?, '1', 4, 'POST')", t);
            return null;
        })).hasMessageContaining("23505");
    }

    /** 같은 시각의 확정·지급 DRIFT(엔진 골든은 고정 시계)가 둘 다 들어가고, 같은 이벤트의 재삽입은 충돌한다(쓰기는 ON CONFLICT DO NOTHING). */
    @Test
    void driftsAtTheSameInstantAreKeptApartByTheirSeq() {
        String t = SeedData.uniqueTenant("V4_R");
        String insert = "INSERT INTO recon_result (tenant_id, agent_id, close_ym, run_at, status, source, source_seq) "
                + "VALUES (?, 'A-1001', '202608', TIMESTAMPTZ '2026-09-05T18:00:00+09:00', 'DRIFT', 'EVENT', ?)";
        DB.seed(t, c -> {
            SeedData.exec(c, SeedData.ROWS.get("tenant"), t);
            exec(c, insert, t, 6);
            exec(c, insert, t, 7);
        });
        assertThatThrownBy(() -> DB.asApp(t, c -> {
            exec(c, insert, t, 7);
            return null;
        })).hasMessageContaining("23505");
        assertThatThrownBy(() -> DB.asApp(t, c -> {
            exec(c, "INSERT INTO recon_result (tenant_id, agent_id, close_ym, run_at, status, source, source_seq) "
                    + "VALUES (?, 'A-1001', '202608', TIMESTAMPTZ '2026-09-05T18:00:00+09:00', 'DRIFT', 'NIGHTLY', 0)", t);
            return null;
        })).hasMessageContaining("23514");
    }

    private List<String> primaryKey(String table) {
        return DB.asMigrator(c -> {
            List<String> cols = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT a.attname FROM pg_index i
                    JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)
                    WHERE i.indrelid = ?::regclass AND i.indisprimary
                    ORDER BY array_position(i.indkey, a.attnum)""")) {
                ps.setString(1, table);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        cols.add(rs.getString(1));
                    }
                }
            }
            return cols;
        });
    }

    private static void exec(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            ps.executeUpdate();
        }
    }
}
