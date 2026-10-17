package com.ga.portal.infra;

import com.ga.platform.core.tenant.TenantId;
import com.ga.platform.spring.jdbc.TenantDirectoryReader;
import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.SeedData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 계획 승인 Q3: 테넌트 목록 전용 롤 {@code portal_directory}(V3) — 플랫폼 {@link TenantDirectoryReader}로 모든 테넌트의 {@code tenant_id}를
 * 읽고, 그 밖의 컬럼·표·쓰기는 권한 오류. RLS를 우회하지 않으며(V2·V3 단언) 테넌트 데이터는 읽지 못한다.
 */
class TenantDirectoryIT {

    private static final PostgresHarness DB = PostgresHarness.get();
    private static final String A = SeedData.uniqueTenant("DIR_A");
    private static final String B = SeedData.uniqueTenant("DIR_B");

    @BeforeAll
    static void seed() {
        DB.seed(A, c -> SeedData.everyTable(c, A));
        DB.seed(B, c -> SeedData.everyTable(c, B));
    }

    @Test
    void theReaderListsEveryTenantId() {
        TenantDirectoryReader reader = new TenantDirectoryReader(DB.jdbcUrl(), PostgresHarness.DIRECTORY, PostgresHarness.DIRECTORY_PASSWORD);
        assertThat(reader.allTenants()).contains(TenantId.of(A), TenantId.of(B));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT name FROM tenant",
            "SELECT engine_base_url FROM tenant",
            "SELECT params FROM tenant",
            "SELECT * FROM tenant",
            "SELECT count(*) FROM ingest_cursor",
            "SELECT count(*) FROM earnings_line",
            "SELECT count(*) FROM identity_link",
            "UPDATE tenant SET tenant_id = tenant_id",
            "DELETE FROM tenant",
            "INSERT INTO tenant (tenant_id) VALUES ('ZZ')",
    })
    void everythingElseIsAPermissionError(String sql) {
        assertThatThrownBy(() -> run(sql)).hasMessageContaining("42501");
    }

    @Test
    void theRoleIsLoginNoInheritNoBypassAndOnlyHasTheTenantIdColumn() {
        List<String> columns = DB.asMigrator(c -> strings(c, """
                SELECT table_name || '.' || column_name || ':' || privilege_type
                  FROM information_schema.column_privileges
                 WHERE grantee = 'portal_directory'
                 ORDER BY 1
                """));
        assertThat(columns).containsExactly("tenant.tenant_id:SELECT");
        List<String> tables = DB.asMigrator(c -> strings(c, """
                SELECT table_name || ':' || privilege_type FROM information_schema.table_privileges WHERE grantee = 'portal_directory'
                """));
        assertThat(tables).isEmpty();
        List<String> attrs = DB.asMigrator(c -> strings(c, """
                SELECT concat_ws(',', rolcanlogin::text, rolinherit::text, rolbypassrls::text, rolsuper::text, rolcreaterole::text, rolcreatedb::text)
                  FROM pg_roles WHERE rolname = 'portal_directory'
                """));
        assertThat(attrs).containsExactly("true,false,false,false,false,false");
    }

    private static void run(String sql) {
        try (Connection c = DB.directoryDataSource().getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            throw new PostgresHarness.UncheckedSqlException(e);
        }
    }

    private static List<String> strings(Connection c, String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }
}
