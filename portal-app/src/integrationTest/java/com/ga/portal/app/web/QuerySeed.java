package com.ga.portal.app.web;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.SeedData;

import java.util.List;

/**
 * 조회 API 시험의 허구 뷰 데이터(4단계) — 설계사 하나에 모든 엔드포인트가 비지 않을 만큼: 프로필, 두 달(지급 완료·잠정 DRIFT), 라인 다섯(취소 짝 하나, 상품명·
 * 피보험자 가림 하나), 원장 둘(WARN·FULL)과 타임라인, 열린 플래그, 분급 둘(예정·보류), 채권 하나. 키는 설계사 ID를 접두로 해 설계사끼리 겹치지 않는다
 * ({@code <agent>-1} …). 증권번호({@link #policy})는 평문 센티널이다 — 응답·로그·시험 출력에 원문이 나오면 실패한다.
 */
final class QuerySeed {

    private QuerySeed() {
    }

    static String policy(String agent, int n) {
        return "POL-" + agent + "-0" + n;
    }

    static String calc(String agent, int n) {
        return agent + "-" + n;
    }

    static String ledger(String agent, int n) {
        return agent + "-L" + n;
    }

    /** 이 설계사의 모든 키(남의 키 조회 시험용): 월 둘, 라인 다섯, 원장 둘. */
    static List<String> months() {
        return List.of("202608", "202609");
    }

    static void agent(PostgresHarness db, TenantId tenant, String agent, String name) {
        String t = tenant.value();
        String p1 = policy(agent, 1);
        String p2 = policy(agent, 2);
        db.seed(t, c -> {
            SeedData.exec(c, """
                    INSERT INTO agent_profile (tenant_id, agent_id, name, grade, org_path, role, status, valid_from, last_seq)
                    VALUES (?, ?, ?, 'SR', '/HQ1/BR01', NULL, 'ACTIVE', DATE '2026-01-01', 1)
                    """, t, agent, name);
            SeedData.exec(c, """
                    INSERT INTO earnings_month (tenant_id, agent_id, close_ym, gross, clawback_offset, adjustment, withholding_tax, net, status, data_status,
                                                confirmed_at, paid_at, paid_amount, receivable_offset, carried_receivable, payable, run_seq, net_basis, last_seq)
                    VALUES (?, ?, '202608', 1900000, -100000, 0, 59400, 1740600, 'PAID', 'OK', TIMESTAMPTZ '2026-09-03 01:00:00+00',
                            TIMESTAMPTZ '2026-09-10 01:00:00+00', 1740600, 0, 0, 1740600, 7, 'PAYOUT', 7)
                    """, t, agent);
            SeedData.exec(c, """
                    INSERT INTO earnings_month (tenant_id, agent_id, close_ym, gross, clawback_offset, adjustment, status, data_status, estimate_as_of,
                                                estimate_basis_seq, last_seq)
                    VALUES (?, ?, '202609', 300000, 0, 0, 'PROVISIONAL', 'DRIFT', TIMESTAMPTZ '2026-10-01 00:00:00+00', 9, 9)
                    """, t, agent);
            line(c, t, agent, 1, p1, "FY_COMM", 945_000, "202608", null, 2,
                    "{\"rate\":\"7\",\"payoutRate\":\"0.9\",\"installmentNo\":1,\"ruleVersionIds\":[\"COMM_RATE:1\",\"LIMIT_RULE:1\"]}",
                    "{\"contractDate\":\"2026-08-01\",\"monthlyPremium\":150000}");
            line(c, t, agent, 2, p1, "INCENTIVE", 855_000, "202608", null, 3, "{\"ruleVersionIds\":[\"INC:1\"]}",
                    "{\"contractDate\":\"2026-08-01\",\"monthlyPremium\":150000}");
            line(c, t, agent, 3, p2, "RENEWAL", 100_000, "202608", null, 4, "{\"ruleVersionIds\":[]}",
                    "{\"contractDate\":\"2025-03-02\",\"monthlyPremium\":50000,\"productName\":\"허구 종신보험\",\"insuredMasked\":\"김*수\"}");
            line(c, t, agent, 4, p2, "CLAWBACK", -100_000, "202608", calc(agent, 3), 5, "{\"ruleVersionIds\":[\"CLAWBACK:1\"]}",
                    "{\"contractDate\":\"2025-03-02\",\"monthlyPremium\":50000}");
            line(c, t, agent, 5, p1, "FY_COMM", 300_000, "202609", null, 8, "{\"ruleVersionIds\":[\"COMM_RATE:1\"]}",
                    "{\"contractDate\":\"2026-08-01\",\"monthlyPremium\":150000}");
            SeedData.exec(c, """
                    INSERT INTO policy_limit (tenant_id, ledger_id, agent_id, policy_no, contract_date, fy_start, fy_end, monthly_premium, limit_multiple,
                                              limit_amount, accum_paid, cut_total, deferred_total, last_posting_seq, rule_version_id)
                    VALUES (?, ?, ?, ?, DATE '2026-08-01', DATE '2026-08-01', DATE '2027-07-31', 150000, '15', 2250000, 1800000, 0, 0, 2, 'LIMIT_RULE:1'),
                           (?, ?, ?, ?, DATE '2025-03-02', DATE '2025-03-02', DATE '2026-03-01', 50000, '12.5', 1000000, 1000000, 100000, 0, 1, 'LIMIT_RULE:1')
                    """, t, ledger(agent, 1), agent, p1, t, ledger(agent, 2), agent, p2);
            SeedData.exec(c, """
                    INSERT INTO policy_limit_history (tenant_id, ledger_id, source_seq, posting_seq, kind, calc_id, amount, accum_after, limit_after, occurred_at)
                    VALUES (?, ?, 2, 1, 'POST', ?, 945000, 945000, 2250000, TIMESTAMPTZ '2026-08-01 00:00:00+00'),
                           (?, ?, 3, 2, 'POST', ?, 855000, 1800000, 2250000, TIMESTAMPTZ '2026-08-01 00:00:01+00')
                    """, t, ledger(agent, 1), calc(agent, 1), t, ledger(agent, 1), calc(agent, 2));
            SeedData.exec(c, """
                    INSERT INTO policy_flag (tenant_id, policy_no, agent_id, flag_type, occurred_on, reason)
                    VALUES (?, ?, ?, 'CANCELLED', DATE '2026-02-15', 'CANCEL')
                    """, t, p2, agent);
            SeedData.exec(c, """
                    INSERT INTO deferral_item (tenant_id, schedule_id, agent_id, policy_no, source_calc_id, due_ym, amount, pay_condition, status, reason,
                                               curve_version_id, last_seq)
                    VALUES (?, ?, ?, ?, ?, '202609', 100000, 'INFORCE', 'SCHEDULED', 'CURVE', 'CV1', 6),
                           (?, ?, ?, ?, ?, '202610', 50000, 'INFORCE', 'HELD', 'CURVE', 'CV1', 6)
                    """, t, agent + "-S1", agent, p1, calc(agent, 1), t, agent + "-S2", agent, p1, calc(agent, 1));
            SeedData.exec(c, """
                    INSERT INTO clawback_item (tenant_id, receivable_id, agent_id, origin_calc_id, amount, remaining, status, last_seq)
                    VALUES (?, ?, ?, ?, 100000, 40000, 'OPEN', 5)
                    """, t, agent + "-R1", agent, calc(agent, 4));
        });
    }

    private static void line(java.sql.Connection c, String t, String agent, int n, String policy, String type, long amount, String ym, String reversalOf,
                             long seq, String basis, String summary) throws java.sql.SQLException {
        SeedData.exec(c, """
                INSERT INTO earnings_line (tenant_id, calc_id, agent_id, policy_no, product_key, comm_type, amount, close_ym, reversal_of_calc_id,
                                           limit_cut_amt, basis, policy_summary, source_seq)
                VALUES (?, ?, ?, ?, 'WHOLE-LIFE-20Y', ?, ?, ?, ?, 0, ?::jsonb, ?::jsonb, ?)
                """, t, calc(agent, n), agent, policy, type, amount, ym, reversalOf, basis, summary, seq);
    }
}
