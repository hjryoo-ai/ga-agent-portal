package com.ga.portal.infra.testing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 테넌트 하나에 대해 17개 테넌트 테이블 전부에 행을 하나씩 넣는다(허구 값, 개인정보 없음). RLS 격리 시험(C2)의 바탕.
 * 테이블 목록은 {@link #ROWS}의 키이고, RlsIsolationIT가 DB 카탈로그와 같은지 대조한다(테이블이 늘면 여기도 늘어야 한다).
 */
public final class SeedData {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    /** 테이블 → INSERT 문(첫 바인드 = tenant_id). 값은 형식 CHECK를 지키는 허구 값. */
    public static final Map<String, String> ROWS;

    static {
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("tenant", "INSERT INTO tenant (tenant_id, name, engine_base_url, status, payout_day_rule) VALUES (?, 'Fictional GA', 'http://engine.invalid', 'ACTIVE', 'NEXT_MONTH_D10')");
        rows.put("identity_link", "INSERT INTO identity_link (tenant_id, subject, agent_id) VALUES (?, 'sub-1', 'A0001')");
        rows.put("agent_profile", "INSERT INTO agent_profile (tenant_id, agent_id, name, org_path, role, status, valid_from, last_seq) VALUES (?, 'A0001', 'Agent One', '/HQ1/BR01/TM01', 'AGENT', 'ACTIVE', DATE '2026-08-01', 1)");
        rows.put("earnings_line", "INSERT INTO earnings_line (tenant_id, calc_id, agent_id, policy_no, comm_type, amount, close_ym, basis, policy_summary, source_seq) VALUES (?, 'C1', 'A0001', 'P1', 'FY_COMM', 2800000, '202608', '{}', '{}', 2)");
        rows.put("earnings_month", "INSERT INTO earnings_month (tenant_id, agent_id, close_ym, gross, status, last_seq) VALUES (?, 'A0001', '202608', 2800000, 'PROVISIONAL', 2)");
        rows.put("policy_limit", "INSERT INTO policy_limit (tenant_id, ledger_id, agent_id, policy_no, contract_date, fy_start, fy_end, monthly_premium, limit_multiple, limit_amount, rule_version_id) VALUES (?, 'L1', 'A0001', 'P1', DATE '2026-08-01', DATE '2026-08-01', DATE '2027-07-31', 300000, 12.00, 3600000, 'R1')");
        rows.put("policy_limit_history", "INSERT INTO policy_limit_history (tenant_id, ledger_id, posting_seq, kind, calc_id, amount, accum_after) VALUES (?, 'L1', 1, 'POST', 'C1', 2800000, 2800000)");
        rows.put("deferral_item", "INSERT INTO deferral_item (tenant_id, schedule_id, agent_id, policy_no, source_calc_id, due_ym, amount, pay_condition, status, reason, curve_version_id, last_seq) VALUES (?, 'S1', 'A0001', 'P1', 'C2', '202708', 200000, 'INFORCE', 'SCHEDULED', 'LIMIT_DEFER', 'CV1', 6)");
        rows.put("clawback_item", "INSERT INTO clawback_item (tenant_id, receivable_id, agent_id, origin_calc_id, amount, remaining, status, last_seq) VALUES (?, 'RCV1', 'A0001', 'C9', -350000, -350000, 'OPEN', 12)");
        rows.put("policy_flag", "INSERT INTO policy_flag (tenant_id, policy_no, agent_id, flag_type, occurred_on) VALUES (?, 'P1', 'A0001', 'CANCELLED', DATE '2027-02-10')");
        rows.put("notification", "INSERT INTO notification (tenant_id, notification_id, agent_id, type, severity, dedup_key, title, body, created_at) VALUES (?, '" + UUID.nameUUIDFromBytes(new byte[] {1}) + "', 'A0001', 'LIMIT', 'WARN', 'LIMIT_CUT:C2', 't', 'b', TIMESTAMPTZ '2026-08-20T10:00:00+09:00')");
        rows.put("inquiry", "INSERT INTO inquiry (tenant_id, inquiry_id, agent_id, category, message, status, created_at) VALUES (?, '" + UUID.nameUUIDFromBytes(new byte[] {2}) + "', 'A0001', 'LINE', 'm', 'OPEN', TIMESTAMPTZ '2026-08-20T10:00:00+09:00')");
        rows.put("simulation_log", "INSERT INTO simulation_log (tenant_id, sim_id, agent_id, kind, request, status, created_at) VALUES (?, '" + UUID.nameUUIDFromBytes(new byte[] {3}) + "', 'A0001', 'NEW_CONTRACT', '{}', 'REQUESTED', TIMESTAMPTZ '2026-08-20T10:00:00+09:00')");
        rows.put("ingest_cursor", "INSERT INTO ingest_cursor (tenant_id, last_seq, state, updated_at) VALUES (?, 0, 'RUNNING', TIMESTAMPTZ '2026-08-20T10:00:00+09:00')");
        rows.put("ingest_event", "INSERT INTO ingest_event (tenant_id, event_id, seq, event_type, received_at, status) VALUES (?, '" + UUID.nameUUIDFromBytes(new byte[] {4}) + "', 1, 'LimitLedgerOpened', TIMESTAMPTZ '2026-08-20T10:00:00+09:00', 'APPLIED')");
        rows.put("recon_result", "INSERT INTO recon_result (tenant_id, agent_id, close_ym, run_at, status) VALUES (?, 'A0001', '202608', TIMESTAMPTZ '2026-08-21T02:00:00+09:00', 'MATCH')");
        rows.put("audit_access", "INSERT INTO audit_access (tenant_id, actor_subject, action, target, at) VALUES (?, 'ops', 'REBUILD', 'tenant', TIMESTAMPTZ '2026-08-21T02:00:00+09:00')");
        ROWS = Map.copyOf(rows);
    }

    private SeedData() {
    }

    /** 실행마다 다른 테넌트 ID(플랫폼 TenantId 형식 — 대문자·숫자·밑줄). */
    public static String uniqueTenant(String prefix) {
        return prefix + "_" + COUNTER.incrementAndGet() + "_" + Long.toString(System.nanoTime() % 1_000_000_000L, 36).toUpperCase(java.util.Locale.ROOT);
    }

    /** 바인딩된 트랜잭션 안에서 모든 테이블에 한 행씩 넣는다({@code tenant}가 먼저). */
    public static void everyTable(Connection c, String tenantId) throws SQLException {
        exec(c, ROWS.get("tenant"), tenantId);
        for (Map.Entry<String, String> e : ROWS.entrySet()) {
            if (!e.getKey().equals("tenant")) {
                exec(c, e.getValue(), tenantId);
            }
        }
    }

    public static int exec(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps.executeUpdate();
        }
    }
}
