package com.ga.portal.infra;

import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.SeedData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C2: portal_app 세션에서 app.tenant_id 미설정 시 모든 테넌트 테이블 0행, 설정 시 해당 테넌트 행만(tenant 포함).
 * C3: portal_app은 RLS를 우회·해제할 수 없고 모든 포털 롤은 NOBYPASSRLS다. B3: WITH CHECK — 다른 테넌트 행을 쓸 수 없다.
 * 검사 대상 테이블은 손목록이 아니라 DB 카탈로그에서 읽는다(public 스키마 전 테이블 − flyway_schema_history) — 새 테이블은 자동으로
 * 모든 검사에 들어가고, 개수 고정 단언과 시드 목록 대조가 추가를 드러낸다.
 */
class RlsIsolationIT {

    private static final PostgresHarness DB = PostgresHarness.get();

    /** 테넌트 테이블 수(설계서 §5 — V1의 17 + V8 {@code recon_run}). 테이블을 추가하는 마이그레이션은 이 값과 SeedData를 함께 고친다. */
    private static final int EXPECTED_TABLE_COUNT = 18;

    static final List<String> TABLES = catalogTables();

    private static final String A = SeedData.uniqueTenant("RLS_A");
    private static final String B = SeedData.uniqueTenant("RLS_B");

    private static List<String> catalogTables() {
        return DB.asMigrator(c -> {
            List<String> tables = new ArrayList<>();
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("""
                    SELECT c.relname
                      FROM pg_class c
                     WHERE c.relnamespace = 'public'::regnamespace AND c.relkind IN ('r', 'p')
                       AND c.relname <> 'flyway_schema_history'
                     ORDER BY c.relname
                    """)) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            return List.copyOf(tables);
        });
    }

    @BeforeAll
    static void seedTwoTenants() {
        DB.seed(A, c -> SeedData.everyTable(c, A));
        DB.seed(B, c -> SeedData.everyTable(c, B));
    }

    @Test
    void catalogListsEveryTenantTableAndTheSeedCoversThem() {
        assertThat(TABLES).hasSize(EXPECTED_TABLE_COUNT);
        assertThat(TABLES).containsExactlyInAnyOrderElementsOf(SeedData.ROWS.keySet());
    }

    /** 모든 테이블의 첫 컬럼은 tenant_id다(설계서 §5). */
    @ParameterizedTest
    @FieldSource("TABLES")
    void tenantIdIsTheFirstColumn(String table) {
        DB.asMigrator(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT attname FROM pg_attribute WHERE attrelid = ('public.' || ?)::regclass AND attnum = 1
                    """)) {
                ps.setString(1, table);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).as(table).isEqualTo("tenant_id");
                }
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ C2

    @ParameterizedTest
    @FieldSource("TABLES")
    void unsetTenantSeesZeroRows(String table) {
        assertThat(DB.<Long>asApp(null, c -> count(c, table, null))).isZero();
    }

    @ParameterizedTest
    @FieldSource("TABLES")
    void emptyTenantSettingSeesZeroRows(String table) {
        // 트랜잭션이 끝난 뒤 커스텀 설정은 NULL이 아니라 빈 문자열로 남는다(풀링된 커넥션 재사용 상황).
        assertThat(DB.<Long>asApp(null, c -> {
            try (Statement s = c.createStatement()) {
                s.execute("SET app.tenant_id = ''");
            }
            return count(c, table, null);
        })).isZero();
    }

    @ParameterizedTest
    @FieldSource("TABLES")
    void boundTenantSeesOnlyItsOwnRows(String table) {
        for (String tenant : List.of(A, B)) {
            String other = tenant.equals(A) ? B : A;
            long visible = DB.asApp(tenant, c -> count(c, table, null));
            long own = DB.asApp(tenant, c -> count(c, table, tenant));
            long foreign = DB.asApp(tenant, c -> count(c, table, other));
            assertThat(visible).as("%s rows visible to %s", table, tenant).isPositive().isEqualTo(own);
            assertThat(foreign).as("%s rows of %s visible to %s", table, other, tenant).isZero();
        }
    }

    @ParameterizedTest
    @FieldSource("TABLES")
    void everyTenantTableHasForcedRlsAndTheTenantPolicy(String table) {
        DB.asMigrator(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT c.relrowsecurity, c.relforcerowsecurity, p.qual, p.with_check
                      FROM pg_class c
                      JOIN pg_policies p ON p.tablename = c.relname AND p.policyname = 'tenant_isolation'
                     WHERE c.relname = ? AND c.relnamespace = 'public'::regnamespace
                    """)) {
                ps.setString(1, table);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("policy on %s", table).isTrue();
                    assertThat(rs.getBoolean(1)).as("ENABLE on %s", table).isTrue();
                    assertThat(rs.getBoolean(2)).as("FORCE on %s", table).isTrue();
                    assertThat(rs.getString(3)).contains("tenant_id").contains("app.tenant_id");
                    assertThat(rs.getString(4)).contains("tenant_id").contains("app.tenant_id");
                }
            }
            return null;
        });
    }

    /** 정책은 테이블마다 tenant_isolation 하나뿐이다(새 정책이 조용히 추가되지 않게). */
    @Test
    void policiesAreExactlyTenantIsolation() {
        List<String> policies = DB.asMigrator(c -> {
            List<String> rows = new ArrayList<>();
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("""
                    SELECT tablename || ':' || policyname || ':' || cmd || ':' || array_to_string(roles, ',')
                      FROM pg_policies WHERE schemaname = 'public' ORDER BY 1
                    """)) {
                while (rs.next()) {
                    rows.add(rs.getString(1));
                }
            }
            return rows;
        });
        // 모든 테넌트 표에 tenant_isolation 하나 + tenant에만 테넌트 목록 전용 롤의 SELECT 정책(V3, Phase 1 승인 Q3) — 그 밖은 없다.
        List<String> expected = new ArrayList<>(TABLES.stream().map(t -> t + ":tenant_isolation:ALL:public").toList());
        expected.add("tenant:tenant_directory:SELECT:portal_directory");
        expected.add("tenant:tenant_issuer:SELECT:portal_issuer");   // V5(Phase 3 계획 승인 Q1)
        assertThat(policies).containsExactlyInAnyOrderElementsOf(expected);
    }

    // ------------------------------------------------------------------ B3 (WITH CHECK)

    @Test
    void cannotWriteRowsOfAnotherTenant() {
        assertThatThrownBy(() -> DB.asApp(A, c -> SeedData.exec(c,
                "INSERT INTO identity_link (tenant_id, subject, agent_id, role, status, org_path, linked_at) VALUES (?, 'sub-x', 'A0002', 'AGENT', 'ACTIVE', '/HQ1', TIMESTAMPTZ '2026-10-17 00:00:00+00')", B)))
                .isInstanceOf(PostgresHarness.UncheckedSqlException.class)
                .satisfies(e -> assertThat(((PostgresHarness.UncheckedSqlException) e).sqlState()).isEqualTo("42501"));
        assertThat(DB.<Integer>asApp(A, c -> SeedData.exec(c, "UPDATE identity_link SET agent_id = 'A0002' WHERE tenant_id = ?", B))).isZero();
        assertThat(DB.<Integer>asApp(A, c -> SeedData.exec(c, "DELETE FROM earnings_line WHERE tenant_id = ?", B))).isZero();
    }

    @Test
    void cannotMoveOwnRowToAnotherTenant() {
        assertThatThrownBy(() -> DB.asApp(A, c -> SeedData.exec(c, "UPDATE identity_link SET tenant_id = ? WHERE tenant_id = ?", B, A)))
                .isInstanceOf(PostgresHarness.UncheckedSqlException.class)
                .satisfies(e -> assertThat(((PostgresHarness.UncheckedSqlException) e).sqlState()).isEqualTo("42501"));
    }

    // ------------------------------------------------------------------ C3

    @ParameterizedTest
    @ValueSource(strings = {
            "SET ROLE portal_migrator",
            "SET ROLE postgres",
            "SET SESSION AUTHORIZATION portal_migrator",
            "SET session_replication_role = replica",
            "ALTER TABLE earnings_line DISABLE ROW LEVEL SECURITY",
            "ALTER TABLE earnings_line NO FORCE ROW LEVEL SECURITY",
            "ALTER TABLE tenant DISABLE ROW LEVEL SECURITY",
            "DROP POLICY tenant_isolation ON earnings_line",
            "CREATE POLICY bypass ON earnings_line USING (true)",
            "ALTER ROLE portal_app BYPASSRLS",
            "TRUNCATE earnings_line",
            "CREATE TABLE shadow (tenant_id TEXT)",
            "SELECT * FROM flyway_schema_history",
    })
    void appRoleCannotBypassOrDisableProtections(String sql) {
        List<String> states = new ArrayList<>();
        assertThatThrownBy(() -> DB.asApp(A, c -> {
            try (Statement s = c.createStatement()) {
                s.execute(sql);
            } catch (SQLException e) {
                states.add(e.getSQLState());
                throw e;
            }
            return null;
        })).isInstanceOf(PostgresHarness.UncheckedSqlException.class);
        // 문법 오류·객체 없음이 아니라 권한 오류(42501 insufficient_privilege)로 거부되어야 한다.
        assertThat(states).singleElement().satisfies(state -> assertThat(state).as("SQLSTATE for: %s", sql).isEqualTo("42501"));
    }

    @Test
    void rowSecurityOffIsRejectedWhenPoliciesApply() {
        // row_security=off 는 설정 자체는 되지만, 정책이 적용되는 조회에서 오류가 난다 → 우회 불가.
        assertThatThrownBy(() -> DB.asApp(A, c -> {
            try (Statement s = c.createStatement()) {
                s.execute("SET LOCAL row_security = off");
            }
            return count(c, "earnings_line", null);
        })).isInstanceOf(PostgresHarness.UncheckedSqlException.class)
                .satisfies(e -> assertThat(((PostgresHarness.UncheckedSqlException) e).sqlState()).isEqualTo("42501"));
    }

    @Test
    void everyPortalRoleIsNoBypassRlsAndTheAppRoleHasNoExtraAttributes() {
        DB.asMigrator(c -> {
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("""
                    SELECT rolname, rolsuper, rolbypassrls, rolcreaterole, rolcreatedb, rolinherit
                      FROM pg_roles WHERE rolname LIKE 'portal\\_%' ORDER BY rolname
                    """)) {
                List<String> roles = new ArrayList<>();
                while (rs.next()) {
                    String role = rs.getString(1);
                    roles.add(role);
                    assertThat(rs.getBoolean(2)).as("%s superuser", role).isFalse();
                    assertThat(rs.getBoolean(3)).as("%s bypassrls", role).isFalse();
                    assertThat(rs.getBoolean(4)).as("%s createrole", role).isFalse();
                    assertThat(rs.getBoolean(5)).as("%s createdb", role).isFalse();
                    if (role.equals("portal_app") || role.equals("portal_directory") || role.equals("portal_issuer")) {
                        assertThat(rs.getBoolean(6)).as("%s inherit", role).isFalse();
                    }
                }
                assertThat(roles).containsExactly("portal_app", "portal_directory", "portal_issuer", "portal_migrator");
            }
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("""
                    SELECT count(*) FROM information_schema.role_table_grants
                     WHERE grantee = 'portal_app' AND privilege_type IN ('TRUNCATE', 'TRIGGER', 'REFERENCES')
                    """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isZero();
            }
            return null;
        });
    }

    /**
     * V2의 롤 단언(C3 "마이그레이션 단언")이 실제로 우회 롤을 잡는다: BYPASSRLS 롤을 잠시 만들고 V2의 단언 블록을 그대로 돌려 실패를 본 뒤 지운다.
     * 블록은 클래스패스의 V2__rls.sql에서 잘라 온다 — 단언을 고치면 이 시험이 그 판을 돈다.
     */
    @Test
    void migrationAssertionRejectsABypassRole() throws Exception {
        String v2;
        try (var in = RlsIsolationIT.class.getClassLoader().getResourceAsStream("db/migration/V2__rls.sql")) {
            assertThat(in).isNotNull();
            v2 = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        int start = v2.lastIndexOf("DO $$");
        String assertion = v2.substring(start);
        assertThat(assertion).contains("rolbypassrls");
        try (Connection c = DB.superuserDataSource().getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE ROLE portal_probe_bypass NOLOGIN BYPASSRLS");
            try {
                assertThatThrownBy(() -> s.execute(assertion)).isInstanceOf(SQLException.class)
                        .hasMessageContaining("portal_probe_bypass");
            } finally {
                s.execute("DROP ROLE portal_probe_bypass");
            }
            s.execute(assertion);   // 지운 뒤에는 통과
        }
    }

    private static long count(Connection c, String table, String tenantFilter) throws SQLException {
        String sql = "SELECT count(*) FROM " + table + (tenantFilter == null ? "" : " WHERE tenant_id = ?");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (tenantFilter != null) {
                ps.setString(1, tenantFilter);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
