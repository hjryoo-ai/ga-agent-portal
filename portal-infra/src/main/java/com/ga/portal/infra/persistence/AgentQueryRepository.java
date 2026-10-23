package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.portal.domain.view.AgentRow;
import com.ga.portal.domain.view.DeferralRow;
import com.ga.portal.domain.view.DueMonth;
import com.ga.portal.domain.view.HistoryRow;
import com.ga.portal.domain.view.LedgerItem;
import com.ga.portal.domain.view.LineDetail;
import com.ga.portal.domain.view.LineItem;
import com.ga.portal.domain.view.LinePage;
import com.ga.portal.domain.MonthStatus;
import com.ga.portal.domain.NetBasis;
import com.ga.portal.domain.view.MonthDetail;
import com.ga.portal.domain.view.MonthRow;
import com.ga.portal.domain.view.ReceivableRow;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 조회 API의 저장소(포털 Phase 3 지시문 D-4): 설계사 <b>본인</b>의 뷰만 읽는다. <b>모든 문장이 {@code agent_id = :agentId}</b>(조인의 각 표 포함)를 가진다 —
 * {@code TenantPredicateScanTest}의 두 번째 술어가 이 클래스의 문장 전부에 요구한다. 남의 키는 빈 결과("없다")와 구별되지 않는다. 증권번호는 읽지만 응답에는
 * 유스케이스가 가린 값과 키만 싣는다. 집계(유형별 합·지급월 합)와 남은 한도(한도 − 누적)는 SQL의 정수 연산이다.
 * 문장마다 문자열 하나다(열 목록을 상수로 이어 붙이지 않는다 — SQL 스캔이 문장 단위로 읽는다).
 */
@Repository
public class AgentQueryRepository extends ViewRepository {

    private static final String SEPARATOR = "\u001f";

    private static final RowMapper<LineItem> LINE = (rs, n) -> new LineItem(rs.getString("calc_id"), rs.getString("close_ym"),
            rs.getString("comm_type"), rs.getLong("amount"), rs.getLong("limit_cut_amt"), optString(rs, "limit_cut_policy"),
            optString(rs, "product_key"), optString(rs, "product_name"), optString(rs, "insured_masked"), rs.getString("policy_no"),
            optString(rs, "reversal_of_calc_id"), optString(rs, "reversed_by"), optString(rs, "reversal_of_ym"), optString(rs, "reversed_by_ym"),
            rs.getLong("source_seq"));

    private static final RowMapper<LedgerItem> LEDGER = (rs, n) -> new LedgerItem(PolicyLimitRepository.MAPPER.mapRow(rs, n), rs.getLong("remaining"),
            optString(rs, "open_flags").map(s -> List.of(s.split(","))).orElse(List.of()), optString(rs, "product_key"), optString(rs, "product_name"),
            optString(rs, "insured_masked"));

    public AgentQueryRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    private static Map<String, Object> agent(String agentId, Object... more) {
        Map<String, Object> p = new HashMap<>();
        p.put("agentId", agentId);
        for (int i = 0; i < more.length; i += 2) {
            p.put((String) more[i], more[i + 1]);
        }
        return p;
    }

    public Optional<AgentRow> profile(String agentId) {
        return queryAtMostOne("""
                SELECT agent_id, name, grade, org_path, role, status, valid_from, last_seq
                  FROM agent_profile WHERE tenant_id = :tenantId AND agent_id = :agentId
                """, agent(agentId), AgentProfileRepository.MAPPER);
    }

    public List<MonthRow> months(String agentId, String fromYm, String toYm) {
        return query("""
                SELECT agent_id, close_ym, gross, clawback_offset, adjustment, withholding_tax, net, status, data_status, estimate_as_of,
                       estimate_basis_seq, confirmed_at, paid_at, paid_amount, receivable_offset, carried_receivable, payable, run_seq, net_basis, last_seq
                  FROM earnings_month WHERE tenant_id = :tenantId AND agent_id = :agentId AND close_ym BETWEEN :fromYm AND :toYm
                 ORDER BY close_ym
                """, agent(agentId, "fromYm", fromYm, "toYm", toYm), EarningsMonthRepository.MAPPER);
    }

    public Optional<MonthRow> month(String agentId, String closeYm) {
        return queryAtMostOne("""
                SELECT agent_id, close_ym, gross, clawback_offset, adjustment, withholding_tax, net, status, data_status, estimate_as_of,
                       estimate_basis_seq, confirmed_at, paid_at, paid_amount, receivable_offset, carried_receivable, payable, run_seq, net_basis, last_seq
                  FROM earnings_month WHERE tenant_id = :tenantId AND agent_id = :agentId AND close_ym = :closeYm
                """, agent(agentId, "closeYm", closeYm), EarningsMonthRepository.MAPPER);
    }

    /**
     * 월 상세 — 그 달 행과 지난달 행의 값 넷(포털 Phase 5 CR-1, 같은 문장의 {@code LEFT JOIN} — 문장 수가 그대로다). 지난달 행이 없으면 {@code previous}가 비어 있다.
     */
    public Optional<MonthDetail> monthDetail(String agentId, String closeYm, String previousYm) {
        return queryAtMostOne("""
                SELECT m.agent_id, m.close_ym, m.gross, m.clawback_offset, m.adjustment, m.withholding_tax, m.net, m.status, m.data_status, m.estimate_as_of,
                       m.estimate_basis_seq, m.confirmed_at, m.paid_at, m.paid_amount, m.receivable_offset, m.carried_receivable, m.payable, m.run_seq,
                       m.net_basis, m.last_seq,
                       pm.close_ym AS prev_close_ym, pm.status AS prev_status, pm.gross AS prev_gross, pm.net AS prev_net, pm.net_basis AS prev_net_basis
                  FROM earnings_month m
                  LEFT JOIN earnings_month pm ON pm.tenant_id = :tenantId AND pm.agent_id = :agentId AND pm.close_ym = :previousYm
                 WHERE m.tenant_id = :tenantId AND m.agent_id = :agentId AND m.close_ym = :closeYm
                """, agent(agentId, "closeYm", closeYm, "previousYm", previousYm), (rs, n) -> {
            Optional<String> prevYm = optString(rs, "prev_close_ym");
            Optional<MonthDetail.Previous> previous = prevYm.isEmpty() ? Optional.empty()
                    : Optional.of(new MonthDetail.Previous(prevYm.get(), MonthStatus.valueOf(rs.getString("prev_status")), rs.getLong("prev_gross"),
                            optLong(rs, "prev_net"), optString(rs, "prev_net_basis").map(NetBasis::valueOf)));
            return new MonthDetail(EarningsMonthRepository.MAPPER.mapRow(rs, n), previous);
        });
    }

    /** 그 달 라인의 유형별 정수 합(유형 이름순). */
    public Map<String, Long> byType(String agentId, String closeYm) {
        Map<String, Long> out = new LinkedHashMap<>();
        List<Map.Entry<String, Long>> rows = query("""
                SELECT comm_type, sum(amount)::bigint AS total
                  FROM earnings_line WHERE tenant_id = :tenantId AND agent_id = :agentId AND close_ym = :closeYm
                 GROUP BY comm_type ORDER BY comm_type
                """, agent(agentId, "closeYm", closeYm), (rs, n) -> Map.entry(rs.getString("comm_type"), rs.getLong("total")));
        rows.forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    /** 그 달 라인 한 페이지({@code source_seq, calc_id} 순)와 필터에 맞는 전체 수 — 두 문장. */
    public LinePage lines(String agentId, String closeYm, Optional<String> commType, Optional<String> policyNo, int page, int size) {
        Map<String, Object> p = agent(agentId, "closeYm", closeYm, "commType", commType.orElse(null), "policyNo", policyNo.orElse(null),
                "size", size, "offset", (long) page * size);
        List<LineItem> items = query("""
                SELECT l.calc_id, l.close_ym, l.comm_type, l.amount, l.limit_cut_amt, l.limit_cut_policy, l.product_key,
                       l.policy_summary->>'productName' AS product_name, l.policy_summary->>'insuredMasked' AS insured_masked, l.policy_no,
                       l.reversal_of_calc_id,
                       (SELECT min(r.calc_id) FROM earnings_line r
                         WHERE r.tenant_id = :tenantId AND r.agent_id = :agentId AND r.reversal_of_calc_id = l.calc_id) AS reversed_by,
                       (SELECT o.close_ym FROM earnings_line o
                         WHERE o.tenant_id = :tenantId AND o.agent_id = :agentId AND o.calc_id = l.reversal_of_calc_id) AS reversal_of_ym,
                       (SELECT r.close_ym FROM earnings_line r
                         WHERE r.tenant_id = :tenantId AND r.agent_id = :agentId AND r.reversal_of_calc_id = l.calc_id
                         ORDER BY r.calc_id LIMIT 1) AS reversed_by_ym,
                       l.source_seq
                  FROM earnings_line l
                 WHERE l.tenant_id = :tenantId AND l.agent_id = :agentId AND l.close_ym = :closeYm
                   AND (CAST(:commType AS text) IS NULL OR l.comm_type = :commType)
                   AND (CAST(:policyNo AS text) IS NULL OR l.policy_no = :policyNo)
                 ORDER BY l.source_seq, l.calc_id
                 LIMIT :size OFFSET :offset
                """, p, LINE);
        long total = queryAtMostOne("""
                SELECT count(*) AS total
                  FROM earnings_line
                 WHERE tenant_id = :tenantId AND agent_id = :agentId AND close_ym = :closeYm
                   AND (CAST(:commType AS text) IS NULL OR comm_type = :commType)
                   AND (CAST(:policyNo AS text) IS NULL OR policy_no = :policyNo)
                """, p, (rs, n) -> rs.getLong("total")).orElse(0L);
        return new LinePage(items, total);
    }

    public Set<String> linePolicies(String agentId, String closeYm) {
        return new LinkedHashSet<>(query("""
                SELECT DISTINCT policy_no
                  FROM earnings_line WHERE tenant_id = :tenantId AND agent_id = :agentId AND close_ym = :closeYm
                 ORDER BY policy_no
                """, agent(agentId, "closeYm", closeYm), (rs, n) -> rs.getString("policy_no")));
    }

    /** 그 설계사의 라인 증권번호 집합(전 기간) — 시뮬레이션의 {@code policyKey}를 풀 때만(포털 Phase 5, 엔진 {@code POLICY_NOT_FOUND}의 "그 설계사의 계산 레코드"와 짝). */
    public Set<String> policies(String agentId) {
        return new LinkedHashSet<>(query("""
                SELECT DISTINCT policy_no
                  FROM earnings_line WHERE tenant_id = :tenantId AND agent_id = :agentId
                 ORDER BY policy_no
                """, agent(agentId), (rs, n) -> rs.getString("policy_no")));
    }

    /** 라인 상세 — 근거·계약 요약의 JSONB 필드는 SQL이 꺼낸 문자열(규칙 버전 목록은 원래 순서로 {@code U+001F} 연결). */
    public Optional<LineDetail> line(String agentId, String calcId) {
        return queryAtMostOne("""
                SELECT l.calc_id, l.close_ym, l.comm_type, l.amount, l.limit_cut_amt, l.limit_cut_policy, l.product_key,
                       l.policy_summary->>'productName' AS product_name, l.policy_summary->>'insuredMasked' AS insured_masked, l.policy_no,
                       l.reversal_of_calc_id,
                       (SELECT min(r.calc_id) FROM earnings_line r
                         WHERE r.tenant_id = :tenantId AND r.agent_id = :agentId AND r.reversal_of_calc_id = l.calc_id) AS reversed_by,
                       (SELECT o.close_ym FROM earnings_line o
                         WHERE o.tenant_id = :tenantId AND o.agent_id = :agentId AND o.calc_id = l.reversal_of_calc_id) AS reversal_of_ym,
                       (SELECT r.close_ym FROM earnings_line r
                         WHERE r.tenant_id = :tenantId AND r.agent_id = :agentId AND r.reversal_of_calc_id = l.calc_id
                         ORDER BY r.calc_id LIMIT 1) AS reversed_by_ym,
                       l.source_seq,
                       l.basis->>'rate' AS rate, l.basis->>'payoutRate' AS payout_rate, l.basis->>'installmentNo' AS installment_no,
                       array_to_string(ARRAY(SELECT e FROM jsonb_array_elements_text(l.basis->'ruleVersionIds') WITH ORDINALITY AS t(e, i) ORDER BY i),
                                       chr(31)) AS rule_version_ids,
                       l.policy_summary->>'contractDate' AS contract_date, l.policy_summary->>'monthlyPremium' AS monthly_premium
                  FROM earnings_line l
                 WHERE l.tenant_id = :tenantId AND l.agent_id = :agentId AND l.calc_id = :calcId
                """, agent(agentId, "calcId", calcId), (rs, n) -> new LineDetail(LINE.mapRow(rs, n), optString(rs, "rate"),
                optString(rs, "payout_rate"), optString(rs, "installment_no").map(Long::parseLong),
                optString(rs, "rule_version_ids").filter(s -> !s.isEmpty()).map(s -> Arrays.asList(s.split(SEPARATOR))).orElse(List.of()),
                optString(rs, "contract_date"), optString(rs, "monthly_premium").map(Long::parseLong)));
    }

    public List<DeferralRow> deferralsOfLine(String agentId, String calcId) {
        return query("""
                SELECT schedule_id, agent_id, policy_no, source_calc_id, due_ym, amount, pay_condition, status, reason, curve_version_id, status_reason, last_seq
                  FROM deferral_item WHERE tenant_id = :tenantId AND agent_id = :agentId AND source_calc_id = :calcId
                 ORDER BY due_ym, schedule_id
                """, agent(agentId, "calcId", calcId), DeferralItemRepository.MAPPER);
    }

    public Optional<ReceivableRow> receivableOfLine(String agentId, String calcId) {
        return queryAtMostOne("""
                SELECT receivable_id, agent_id, origin_calc_id, amount, remaining, status, last_seq
                  FROM clawback_item WHERE tenant_id = :tenantId AND agent_id = :agentId AND origin_calc_id = :calcId
                """, agent(agentId, "calcId", calcId), ClawbackItemRepository.MAPPER);
    }

    public Optional<String> ledgerIdOfPolicy(String agentId, String policyNo) {
        return queryAtMostOne("""
                SELECT ledger_id FROM policy_limit WHERE tenant_id = :tenantId AND agent_id = :agentId AND policy_no = :policyNo
                """, agent(agentId, "policyNo", policyNo), (rs, n) -> rs.getString("ledger_id"));
    }

    /**
     * 원장 전부 + 남은 한도 + 열린 플래그 + 그 계약의 첫 라인({@code source_seq, calc_id} 최소)의 상품·피보험자(포털 Phase 5 CR-4) — 한 문장(원장 수와 무관, C13).
     * 원장 ID 순(길이, 사전).
     */
    public List<LedgerItem> ledgers(String agentId) {
        return query("""
                SELECT p.ledger_id, p.agent_id, p.policy_no, p.contract_date, p.fy_start, p.fy_end, p.monthly_premium, p.limit_multiple, p.limit_amount,
                       p.accum_paid, p.cut_total, p.deferred_total, p.last_posting_seq, p.rule_version_id, p.limit_amount - p.accum_paid AS remaining,
                       (SELECT string_agg(f.flag_type, ',' ORDER BY f.flag_type) FROM policy_flag f
                         WHERE f.tenant_id = :tenantId AND f.agent_id = :agentId AND f.policy_no = p.policy_no AND f.cleared_at IS NULL) AS open_flags,
                       fl.product_key, fl.product_name, fl.insured_masked
                  FROM policy_limit p
                  LEFT JOIN LATERAL (SELECT l.product_key, l.policy_summary->>'productName' AS product_name,
                                            l.policy_summary->>'insuredMasked' AS insured_masked
                                       FROM earnings_line l
                                      WHERE l.tenant_id = :tenantId AND l.agent_id = :agentId AND l.policy_no = p.policy_no
                                      ORDER BY l.source_seq, l.calc_id LIMIT 1) fl ON true
                 WHERE p.tenant_id = :tenantId AND p.agent_id = :agentId
                 ORDER BY length(p.ledger_id), p.ledger_id
                """, agent(agentId), LEDGER);
    }

    public Optional<LedgerItem> ledger(String agentId, String ledgerId) {
        return queryAtMostOne("""
                SELECT p.ledger_id, p.agent_id, p.policy_no, p.contract_date, p.fy_start, p.fy_end, p.monthly_premium, p.limit_multiple, p.limit_amount,
                       p.accum_paid, p.cut_total, p.deferred_total, p.last_posting_seq, p.rule_version_id, p.limit_amount - p.accum_paid AS remaining,
                       (SELECT string_agg(f.flag_type, ',' ORDER BY f.flag_type) FROM policy_flag f
                         WHERE f.tenant_id = :tenantId AND f.agent_id = :agentId AND f.policy_no = p.policy_no AND f.cleared_at IS NULL) AS open_flags,
                       fl.product_key, fl.product_name, fl.insured_masked
                  FROM policy_limit p
                  LEFT JOIN LATERAL (SELECT l.product_key, l.policy_summary->>'productName' AS product_name,
                                            l.policy_summary->>'insuredMasked' AS insured_masked
                                       FROM earnings_line l
                                      WHERE l.tenant_id = :tenantId AND l.agent_id = :agentId AND l.policy_no = p.policy_no
                                      ORDER BY l.source_seq, l.calc_id LIMIT 1) fl ON true
                 WHERE p.tenant_id = :tenantId AND p.agent_id = :agentId AND p.ledger_id = :ledgerId
                """, agent(agentId, "ledgerId", ledgerId), LEDGER);
    }

    /** 원장 타임라인 — 원장의 설계사로 조인한다({@code policy_limit_history}에는 설계사 열이 없다): 남의 원장이면 빈 목록. */
    public List<HistoryRow> history(String agentId, String ledgerId) {
        return query("""
                SELECT h.ledger_id, h.source_seq, h.posting_seq, h.kind, h.calc_id, h.amount, h.accum_after, h.limit_after, h.reason, h.occurred_at
                  FROM policy_limit_history h
                  JOIN policy_limit p ON p.tenant_id = h.tenant_id AND p.ledger_id = h.ledger_id
                 WHERE h.tenant_id = :tenantId AND p.tenant_id = :tenantId AND p.agent_id = :agentId AND h.ledger_id = :ledgerId
                 ORDER BY h.source_seq
                """, agent(agentId, "ledgerId", ledgerId), (rs, n) -> new HistoryRow(rs.getString("ledger_id"), rs.getLong("source_seq"),
                optLong(rs, "posting_seq"), rs.getString("kind"), optString(rs, "calc_id"), optLong(rs, "amount"), rs.getLong("accum_after"),
                rs.getLong("limit_after"), optString(rs, "reason"), instant(rs, "occurred_at")));
    }

    /** 분급 항목 — {@code policyNo}가 있으면 그 계약만(유스케이스가 {@code policyKey}를 그 설계사의 증권번호 집합에서 푼 원문). */
    public List<DeferralRow> deferrals(String agentId, String fromYm, String toYm, Optional<String> policyNo) {
        return query("""
                SELECT schedule_id, agent_id, policy_no, source_calc_id, due_ym, amount, pay_condition, status, reason, curve_version_id, status_reason, last_seq
                  FROM deferral_item WHERE tenant_id = :tenantId AND agent_id = :agentId AND due_ym BETWEEN :fromYm AND :toYm
                   AND (CAST(:policyNo AS text) IS NULL OR policy_no = :policyNo)
                 ORDER BY due_ym, schedule_id
                """, agent(agentId, "fromYm", fromYm, "toYm", toYm, "policyNo", policyNo.orElse(null)), DeferralItemRepository.MAPPER);
    }

    /** 그 설계사의 분급 항목 증권번호 집합 — {@code policyKey} 필터를 풀 때만(포털 Phase 5 Q15). */
    public Set<String> deferralPolicies(String agentId) {
        return new LinkedHashSet<>(query("""
                SELECT DISTINCT policy_no
                  FROM deferral_item WHERE tenant_id = :tenantId AND agent_id = :agentId
                 ORDER BY policy_no
                """, agent(agentId), (rs, n) -> rs.getString("policy_no")));
    }

    /**
     * 지급월별 정수 합(설계서 §6.4): 예정 = SCHEDULED + RELEASED, 보류 = HELD. 취소분은 어느 쪽에도 없다. 값은 원값이다 — 시나리오(RETENTION)는 유스케이스의
     * {@code ForecastScenario}가 적용한다(SQL에 곱셈 없음 — {@code SqlArithmeticScanTest}).
     */
    public List<DueMonth> dueMonths(String agentId, String fromYm, String toYm, Optional<String> policyNo) {
        return query("""
                SELECT due_ym,
                       coalesce(sum(amount) FILTER (WHERE status IN ('SCHEDULED', 'RELEASED')), 0)::bigint AS expected,
                       coalesce(sum(amount) FILTER (WHERE status = 'HELD'), 0)::bigint AS held
                  FROM deferral_item WHERE tenant_id = :tenantId AND agent_id = :agentId AND due_ym BETWEEN :fromYm AND :toYm
                   AND (CAST(:policyNo AS text) IS NULL OR policy_no = :policyNo)
                 GROUP BY due_ym ORDER BY due_ym
                """, agent(agentId, "fromYm", fromYm, "toYm", toYm, "policyNo", policyNo.orElse(null)), (rs, n) -> new DueMonth(rs.getString("due_ym"), rs.getLong("expected"),
                rs.getLong("held")));
    }
}
