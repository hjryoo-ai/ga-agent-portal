package com.ga.portal.infra;

import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.SeedData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V5(Phase 3 계획 ⑥·승인 Q2·F6): {@code identity_link}의 역할·상태·설계사·조직 CHECK, 발급자 쌍·고유(테넌트를 넘어 — RLS는 고유 인덱스를 가리지
 * 않는다), {@code limit_multiple TEXT} 원문 보존과 형식, 새 열({@code caught_up_at}·{@code detail}).
 */
class QueryApiSchemaIT {

    private static final PostgresHarness DB = PostgresHarness.get();
    private static final String TS = "TIMESTAMPTZ '2026-10-17 00:00:00+00'";

    private static String tenant(String prefix) {
        String t = SeedData.uniqueTenant(prefix);
        DB.seed(t, c -> SeedData.exec(c, SeedData.ROWS.get("tenant"), t));
        return t;
    }

    @Test
    void aLinkCarriesARoleAStatusAndAnOrgPathAndOperatorsHaveNoAgent() {
        String t = tenant("LINK");
        DB.seed(t, c -> {
            SeedData.exec(c, "INSERT INTO identity_link VALUES (?, 'sub-agent', 'A0001', 'AGENT', 'ACTIVE', '/HQ1/BR01', " + TS + ")", t);
            SeedData.exec(c, "INSERT INTO identity_link VALUES (?, 'sub-manager', 'A0002', 'MANAGER', 'REVOKED', '/HQ1', " + TS + ")", t);
            SeedData.exec(c, "INSERT INTO identity_link VALUES (?, 'cli:ops-kim', NULL, 'OPERATOR', 'ACTIVE', NULL, " + TS + ")", t);
        });
        assertThat(DB.<Integer>asApp(t, c -> count(c, "SELECT count(*) FROM identity_link WHERE tenant_id = ?", t))).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "(?, 's1', NULL, 'AGENT', 'ACTIVE', '/HQ1', " + TS + ")",            // 설계사 없는 AGENT
            "(?, 's2', 'A0001', 'OPERATOR', 'ACTIVE', NULL, " + TS + ")",        // 설계사 있는 OPERATOR
            "(?, 's3', 'A0001', 'AGENT', 'ACTIVE', NULL, " + TS + ")",           // 조직 없는 AGENT
            "(?, 's4', 'A0001', 'MANAGER', 'ACTIVE', NULL, " + TS + ")",         // 조직 없는 MANAGER
            "(?, 's5', 'A0001', 'ADMIN', 'ACTIVE', '/HQ1', " + TS + ")",         // 닫힌 역할 밖
            "(?, 's6', 'A0001', 'AGENT', 'DISABLED', '/HQ1', " + TS + ")",       // 닫힌 상태 밖
            "(?, 's7', 'A0001', 'AGENT', 'ACTIVE', '/HQ1', NULL)",                // 연결 시각 없음
    })
    void anInvalidLinkIsRejectedByTheDatabase(String values) {
        String t = tenant("LINKX");
        assertThatThrownBy(() -> DB.seed(t, c -> SeedData.exec(c, "INSERT INTO identity_link VALUES " + values, t)))
                .hasMessageMatching("(?s).*(23514|23502).*");
    }

    @Test
    void issuersAreAPairAndUniqueAcrossTenants() {
        String a = tenant("ISSU_A");
        String b = tenant("ISSU_B");
        DB.seed(a, c -> SeedData.exec(c, "UPDATE tenant SET oidc_issuer = 'https://idp.invalid/shared', oidc_audience = 'portal' WHERE tenant_id = ?", a));
        assertThatThrownBy(() -> DB.seed(b, c -> SeedData.exec(c,
                "UPDATE tenant SET oidc_issuer = 'https://idp.invalid/shared', oidc_audience = 'portal' WHERE tenant_id = ?", b)))
                .hasMessageContaining("23505");
        assertThatThrownBy(() -> DB.seed(b, c -> SeedData.exec(c, "UPDATE tenant SET oidc_issuer = 'https://idp.invalid/b' WHERE tenant_id = ?", b)))
                .hasMessageContaining("23514");
        assertThatThrownBy(() -> DB.seed(b, c -> SeedData.exec(c,
                "UPDATE tenant SET oidc_issuer = 'ftp://idp.invalid/b', oidc_audience = 'portal' WHERE tenant_id = ?", b)))
                .hasMessageContaining("23514");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12", "12.5", "12.50", "0", "1.000001"})
    void limitMultipleKeepsTheContractStringByteForByte(String value) {
        String t = tenant("LM");
        DB.seed(t, c -> SeedData.exec(c, "INSERT INTO policy_limit (tenant_id, ledger_id, agent_id, policy_no, contract_date, fy_start, fy_end, "
                + "monthly_premium, limit_multiple, limit_amount, rule_version_id) VALUES (?, 'L1', 'A0001', 'P1', DATE '2026-08-01', DATE '2026-08-01', "
                + "DATE '2027-07-31', 150000, ?, 1800000, 'R1')", t, value));
        assertThat(DB.<String>asApp(t, c -> string(c, "SELECT limit_multiple FROM policy_limit WHERE tenant_id = ?", t))).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12.", "1.0000001", "-1", "1e2", "012", "abc", ""})
    void limitMultipleOutsideTheContractPatternIsRejected(String value) {
        String t = tenant("LMX");
        assertThatThrownBy(() -> DB.seed(t, c -> SeedData.exec(c, "INSERT INTO policy_limit (tenant_id, ledger_id, agent_id, policy_no, contract_date, "
                + "fy_start, fy_end, monthly_premium, limit_multiple, limit_amount, rule_version_id) VALUES (?, 'L1', 'A0001', 'P1', DATE '2026-08-01', "
                + "DATE '2026-08-01', DATE '2027-07-31', 150000, ?, 1800000, 'R1')", t, value))).hasMessageContaining("23514");
    }

    @Test
    void theNewColumnsExist() {
        assertThat(DB.<String>asMigrator(c -> string(c, """
                SELECT string_agg(table_name || '.' || column_name || ':' || data_type, ',' ORDER BY table_name, column_name)
                  FROM information_schema.columns
                 WHERE (table_name, column_name) IN (('ingest_cursor', 'caught_up_at'), ('audit_access', 'detail'), ('policy_limit', 'limit_multiple'),
                                                    ('tenant', 'oidc_issuer'), ('tenant', 'oidc_audience'))
                """, null))).isEqualTo("audit_access.detail:text,ingest_cursor.caught_up_at:timestamp with time zone,policy_limit.limit_multiple:text,"
                + "tenant.oidc_audience:text,tenant.oidc_issuer:text");
    }

    private static int count(java.sql.Connection c, String sql, String t) throws java.sql.SQLException {
        return Integer.parseInt(string(c, sql, t));
    }

    private static String string(java.sql.Connection c, String sql, String t) throws java.sql.SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (t != null) {
                ps.setString(1, t);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }
}
