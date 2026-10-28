package com.ga.portal.infra;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.infra.directory.IssuerDirectoryReader;
import com.ga.portal.infra.directory.IssuerRegistration;
import com.ga.portal.infra.directory.WebLogin;
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
 * Phase 3 계획 승인 Q1: 발급자 표 전용 롤 {@code portal_issuer}(V5) — 모든 테넌트의 {@code (tenant_id, oidc_issuer, oidc_audience)}만 읽고,
 * 그 밖의 컬럼·표·쓰기는 권한 오류. RLS를 우회하지 않는다(V5 단언). 테넌트를 바인딩하기 전에 토큰의 {@code iss}로 테넌트를 찾는 데 쓴다.
 * 포털 Phase 8(V10, 계획 P-13·조건 A): 같은 롤이 로그인 설정 두 열({@code web_origin}·{@code oidc_client_id})을 더 읽는다 — 출처 정확 일치 하나만,
 * 두 열은 함께 있거나 함께 없고, 있으면 발급자가 있어야 하며, 출처는 경로 없는 스킴+호스트다.
 */
class IssuerDirectoryIT {

    private static final PostgresHarness DB = PostgresHarness.get();
    private static final String A = SeedData.uniqueTenant("ISS_A");
    private static final String B = SeedData.uniqueTenant("ISS_B");
    private static final String ORIGIN_A = "http://" + A.toLowerCase(java.util.Locale.ROOT).replace('_', '-') + ".invalid:8080";

    @BeforeAll
    static void seed() {
        DB.seed(A, c -> {
            SeedData.everyTable(c, A);
            SeedData.exec(c, "UPDATE tenant SET oidc_issuer = ?, oidc_audience = 'portal', web_origin = ?, oidc_client_id = 'portal-web' WHERE tenant_id = ?",
                    "https://idp.invalid/" + A, ORIGIN_A, A);
        });
        DB.seed(B, c -> SeedData.everyTable(c, B));
    }

    @Test
    void theRoleReadsTheThreeIssuerColumnsOfEveryTenant() {
        List<String> rows = query("SELECT tenant_id || '|' || coalesce(oidc_issuer, '-') || '|' || coalesce(oidc_audience, '-') FROM tenant");
        assertThat(rows).contains(A + "|https://idp.invalid/" + A + "|portal", B + "|-|-");
    }

    /** 리더(허용 목록 항목 — 이 롤로 접속): {@code iss} 정확 일치 하나만, 대소문자·꼬리 슬래시가 다르면 없음, 발급자 없는 테넌트는 찾지 않는다. */
    @Test
    void theReaderFindsATenantByTheExactIssuerOnly() {
        String issuer = "https://idp.invalid/" + A;
        try (IssuerDirectoryReader reader = new IssuerDirectoryReader(DB.jdbcUrl(), PostgresHarness.ISSUER, PostgresHarness.ISSUER_PASSWORD, 1)) {
            assertThat(reader.find(issuer)).contains(new IssuerRegistration(TenantId.of(A), issuer, "portal"));
            assertThat(reader.find(issuer + "/")).isEmpty();
            assertThat(reader.find(issuer.toUpperCase(java.util.Locale.ROOT))).isEmpty();
            assertThat(reader.find("https://idp.invalid/" + B)).isEmpty();
        }
    }

    /** Phase 8 {@code byWebOrigin}: 출처 정확 일치 하나만 — 꼬리 슬래시·대소문자·다른 포트·경로가 붙으면 없음, 로그인 설정 없는 테넌트는 찾지 않는다. */
    @Test
    void theReaderFindsTheWebLoginByTheExactOriginOnly() {
        try (IssuerDirectoryReader reader = new IssuerDirectoryReader(DB.jdbcUrl(), PostgresHarness.ISSUER, PostgresHarness.ISSUER_PASSWORD, 1)) {
            assertThat(reader.byWebOrigin(ORIGIN_A)).contains(new WebLogin("https://idp.invalid/" + A, "portal-web"));
            assertThat(reader.byWebOrigin(ORIGIN_A + "/")).isEmpty();
            assertThat(reader.byWebOrigin(ORIGIN_A.toUpperCase(java.util.Locale.ROOT))).isEmpty();
            assertThat(reader.byWebOrigin(ORIGIN_A.replace(":8080", ":8081"))).isEmpty();
            assertThat(reader.byWebOrigin(ORIGIN_A.replace("http://", "https://"))).isEmpty();
            assertThat(reader.byWebOrigin("null")).isEmpty();
        }
    }

    /**
     * V10 제약: 경로 있는 출처·짝 없는 열·발급자 없는 출처·형식 밖 클라이언트 id·같은 출처 둘은 거부(제약명으로 확인). PostgreSQL은 CHECK를 이름 순으로 보므로
     * 각 행은 그 제약 하나만 어기게 쓴다(발급자는 {@code %I}로 채운다).
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "web_origin = 'http://x.invalid/app', oidc_client_id = 'c', %I|ck_tenant_web_origin",
            "web_origin = 'x.invalid', oidc_client_id = 'c', %I|ck_tenant_web_origin",
            "oidc_client_id = 'c', %I|ck_tenant_web_login_pair",
            "web_origin = 'http://y.invalid', %I|ck_tenant_web_login_pair",
            "web_origin = 'http://z.invalid', oidc_client_id = 'c'|ck_tenant_web_login_issuer",
            "web_origin = 'http://w.invalid', oidc_client_id = 'c d', %I|ck_tenant_oidc_client_id",
            "web_origin = '%ORIGIN_A', oidc_client_id = 'c', %I|uq_tenant_web_origin",
    })
    void theWebLoginColumnsAreConstrained(String row) {
        String[] parts = row.split("\\|");
        String set = parts[0].replace("%I", "oidc_issuer = 'https://idp.invalid/v10-" + B + "', oidc_audience = 'a'").replace("%ORIGIN_A", ORIGIN_A);
        assertThatThrownBy(() -> DB.seed(B, c -> SeedData.exec(c, "UPDATE tenant SET " + set + " WHERE tenant_id = ?", B)))
                .hasMessageContaining(parts[1]);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT name FROM tenant",
            "SELECT engine_base_url FROM tenant",
            "SELECT params FROM tenant",
            "SELECT payout_day_rule FROM tenant",
            "SELECT * FROM tenant",
            "SELECT count(*) FROM identity_link",
            "SELECT count(*) FROM ingest_cursor",
            "SELECT count(*) FROM earnings_line",
            "SELECT count(*) FROM audit_access",
            "UPDATE tenant SET oidc_issuer = oidc_issuer",
            "DELETE FROM tenant",
            "INSERT INTO tenant (tenant_id) VALUES ('ZZ')",
    })
    void everythingElseIsAPermissionError(String sql) {
        assertThatThrownBy(() -> query(sql)).hasMessageContaining("42501");
    }

    @Test
    void theRoleIsLoginNoInheritNoBypassAndOnlyHasTheFiveColumns() {
        List<String> columns = DB.asMigrator(c -> strings(c, """
                SELECT table_name || '.' || column_name || ':' || privilege_type
                  FROM information_schema.column_privileges
                 WHERE grantee = 'portal_issuer'
                 ORDER BY 1
                """));
        assertThat(columns).as("Phase 8 (V10): + web_origin, oidc_client_id")
                .containsExactly("tenant.oidc_audience:SELECT", "tenant.oidc_client_id:SELECT", "tenant.oidc_issuer:SELECT", "tenant.tenant_id:SELECT",
                        "tenant.web_origin:SELECT");
        assertThat(DB.<List<String>>asMigrator(c -> strings(c, "SELECT table_name FROM information_schema.table_privileges WHERE grantee = 'portal_issuer'")))
                .isEmpty();
        assertThat(DB.<List<String>>asMigrator(c -> strings(c, """
                SELECT concat_ws(',', rolcanlogin::text, rolinherit::text, rolbypassrls::text, rolsuper::text, rolcreaterole::text, rolcreatedb::text)
                  FROM pg_roles WHERE rolname = 'portal_issuer'
                """))).containsExactly("true,false,false,false,false,false");
    }

    private static List<String> query(String sql) {
        try (Connection c = DB.issuerDataSource().getConnection()) {
            return strings(c, sql);
        } catch (SQLException e) {
            throw new PostgresHarness.UncheckedSqlException(e);
        }
    }

    private static List<String> strings(Connection c, String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement s = c.createStatement()) {
            if (s.execute(sql)) {
                try (ResultSet rs = s.getResultSet()) {
                    while (rs.next()) {
                        out.add(rs.getString(1));
                    }
                }
            }
        }
        return out;
    }
}
