package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.portal.domain.view.LineRow;
import com.ga.portal.domain.view.MonthSums;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 바인딩된 테넌트의 {@code earnings_line}. 라인은 불변 사실이라 insert만 있다(Phase 2 승인 Q4). 월 합은 그 설계사·그 달 한 집계
 * ({@code ix_line_agent_ym} — 불변식 검사 범위, 지시문 D-13).
 */
@Repository
public class EarningsLineRepository extends ViewRepository {

    private static final RowMapper<LineRow> MAPPER = (rs, n) -> new LineRow(rs.getString("calc_id"), rs.getString("agent_id"),
            rs.getString("policy_no"), optString(rs, "product_key"), rs.getString("comm_type"), rs.getLong("amount"),
            rs.getString("close_ym"), optString(rs, "reversal_of_calc_id"), rs.getLong("limit_cut_amt"),
            optString(rs, "limit_cut_policy"), rs.getString("basis"), rs.getString("policy_summary"), rs.getLong("source_seq"));

    public EarningsLineRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    public Optional<LineRow> find(String calcId) {
        return queryAtMostOne("""
                SELECT calc_id, agent_id, policy_no, product_key, comm_type, amount, close_ym, reversal_of_calc_id, limit_cut_amt,
                       limit_cut_policy, basis::text AS basis, policy_summary::text AS policy_summary, source_seq
                  FROM earnings_line WHERE tenant_id = :tenantId AND calc_id = :calcId
                """, Map.of("calcId", calcId), MAPPER);
    }

    public void insert(LineRow r) {
        Map<String, Object> p = new HashMap<>();
        p.put("calcId", r.calcId());
        p.put("agentId", r.agentId());
        p.put("policyNo", r.policyNo());
        p.put("productKey", orNull(r.productKey()));
        p.put("commType", r.commType());
        p.put("amount", r.amount());
        p.put("closeYm", r.closeYm());
        p.put("reversalOf", orNull(r.reversalOfCalcId()));
        p.put("cut", r.limitCutAmt());
        p.put("cutPolicy", orNull(r.limitCutPolicy()));
        p.put("basis", r.basis());
        p.put("policySummary", r.policySummary());
        p.put("sourceSeq", r.sourceSeq());
        update("""
                INSERT INTO earnings_line (tenant_id, calc_id, agent_id, policy_no, product_key, comm_type, amount, close_ym, reversal_of_calc_id,
                                           limit_cut_amt, limit_cut_policy, basis, policy_summary, source_seq)
                VALUES (:tenantId, :calcId, :agentId, :policyNo, :productKey, :commType, :amount, :closeYm, :reversalOf,
                        :cut, :cutPolicy, CAST(:basis AS jsonb), CAST(:policySummary AS jsonb), :sourceSeq)
                """, p);
    }

    /** Σ 그 설계사·그 달(설계서 §5 집계 정의 — 전 상태 합산). */
    public MonthSums sums(String agentId, String closeYm) {
        return queryAtMostOne("""
                SELECT coalesce(sum(amount) FILTER (WHERE comm_type NOT IN ('CLAWBACK', 'ADJUSTMENT')), 0) AS gross,
                       coalesce(sum(amount) FILTER (WHERE comm_type = 'CLAWBACK'), 0) AS clawback_offset,
                       coalesce(sum(amount) FILTER (WHERE comm_type = 'ADJUSTMENT'), 0) AS adjustment
                  FROM earnings_line WHERE tenant_id = :tenantId AND agent_id = :agentId AND close_ym = :closeYm
                """, Map.of("agentId", agentId, "closeYm", closeYm),
                (rs, n) -> new MonthSums(rs.getLong("gross"), rs.getLong("clawback_offset"), rs.getLong("adjustment"))).orElse(MonthSums.ZERO);
    }
}
