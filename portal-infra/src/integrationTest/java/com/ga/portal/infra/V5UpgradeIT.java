package com.ga.portal.infra;

import com.ga.portal.infra.testing.PostgresHarness;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5의 기존 행 변환(Phase 3 계획 ⑥·승인 F6): 다른 시험은 빈 표에 마이그레이션을 끝까지 돌리므로 {@code USING} 절을 보지 못한다. 이 시험은
 * 빈 데이터베이스를 하나 더 만들어 V4까지 올리고, V4 모양({@code NUMERIC(6,2)})으로 원장 행을 넣은 뒤 V5로 올린다 — 기존 값은 지금까지의 읽기
 * ({@code trim_scale(…)::text})와 같은 글자여야 한다({@code "12"}, {@code "1.5"} — {@code "12.00"}이 아니다).
 */
class V5UpgradeIT {

    private static final PostgresHarness DB = PostgresHarness.get();

    @Test
    void existingLimitMultiplesKeepTheirReadForm() throws SQLException {
        String database = "portal_v5_upgrade";
        try (Connection c = DB.superuserDataSource().getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + database);
            s.execute("CREATE DATABASE " + database + " OWNER " + PostgresHarness.MIGRATOR);
        }
        PGSimpleDataSource migrator = new PGSimpleDataSource();
        migrator.setUrl(DB.jdbcUrl().replace("/" + PostgresHarness.DATABASE, "/" + database));
        migrator.setUser(PostgresHarness.MIGRATOR);
        migrator.setPassword(PostgresHarness.MIGRATOR_PASSWORD);
        try (Connection c = DB.superuserDataSource().getConnection(); Statement s = c.createStatement()) {
            s.execute("GRANT CONNECT ON DATABASE " + database + " TO " + PostgresHarness.APP);
        }

        Flyway.configure().dataSource(migrator).locations("classpath:db/migration").target("4").load().migrate();
        try (Connection c = migrator.getConnection()) {
            c.setAutoCommit(false);
            exec(c, "SELECT set_config('app.tenant_id', 'UPG1', true)");
            exec(c, "INSERT INTO tenant (tenant_id, name, engine_base_url, status, payout_day_rule) "
                    + "VALUES ('UPG1', 'Fictional GA', 'http://engine.invalid', 'ACTIVE', 'NEXT_MONTH_D10')");
            for (String[] row : List.of(new String[]{"L1", "12", "P1"}, new String[]{"L2", "1.5", "P2"}, new String[]{"L3", "12.25", "P3"})) {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO policy_limit (tenant_id, ledger_id, agent_id, policy_no, contract_date, "
                        + "fy_start, fy_end, monthly_premium, limit_multiple, limit_amount, rule_version_id) VALUES ('UPG1', ?, 'A0001', ?, "
                        + "DATE '2026-08-01', DATE '2026-08-01', DATE '2027-07-31', 150000, CAST(? AS NUMERIC), 1800000, 'R1')")) {
                    ps.setString(1, row[0]);
                    ps.setString(2, row[2]);
                    ps.setString(3, row[1]);
                    ps.executeUpdate();
                }
            }
            c.commit();
        }

        Flyway.configure().dataSource(migrator).locations("classpath:db/migration").load().migrate();
        List<String> values = new ArrayList<>();
        try (Connection c = migrator.getConnection()) {
            c.setAutoCommit(false);
            exec(c, "SELECT set_config('app.tenant_id', 'UPG1', true)");
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT ledger_id || '=' || limit_multiple FROM policy_limit WHERE tenant_id = 'UPG1' ORDER BY ledger_id")) {
                while (rs.next()) {
                    values.add(rs.getString(1));
                }
            }
            c.rollback();
        }
        assertThat(values).containsExactly("L1=12", "L2=1.5", "L3=12.25");

        try (Connection c = DB.superuserDataSource().getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP DATABASE " + database + " WITH (FORCE)");
        }
    }

    private static void exec(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }
}
