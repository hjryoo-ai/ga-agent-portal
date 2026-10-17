package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.portal.domain.view.HistoryRow;
import com.ga.portal.domain.view.LedgerRow;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 바인딩된 테넌트의 {@code policy_limit}·{@code policy_limit_history}(설계서 §5 v1.5). {@code limit_multiple}은 엔진의 불투명 소수 문자열을
 * SQL이 {@code NUMERIC}으로 받고 {@code trim_scale(…)::text}로 돌려준다 — 포털 코드는 숫자로 바꾸지 않는다(규약 1).
 */
@Repository
public class PolicyLimitRepository extends ViewRepository {

    private static final RowMapper<LedgerRow> MAPPER = (rs, n) -> new LedgerRow(rs.getString("ledger_id"), rs.getString("agent_id"),
            rs.getString("policy_no"), date(rs, "contract_date"), date(rs, "fy_start"), date(rs, "fy_end"),
            rs.getLong("monthly_premium"), rs.getString("limit_multiple"), rs.getLong("limit_amount"), rs.getLong("accum_paid"),
            rs.getLong("cut_total"), rs.getLong("deferred_total"), rs.getLong("last_posting_seq"), rs.getString("rule_version_id"));

    public PolicyLimitRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    public Optional<LedgerRow> find(String ledgerId) {
        return queryAtMostOne("""
                SELECT ledger_id, agent_id, policy_no, contract_date, fy_start, fy_end, monthly_premium,
                       trim_scale(limit_multiple)::text AS limit_multiple, limit_amount, accum_paid, cut_total, deferred_total, last_posting_seq,
                       rule_version_id
                  FROM policy_limit WHERE tenant_id = :tenantId AND ledger_id = :ledgerId
                """, Map.of("ledgerId", ledgerId), MAPPER);
    }

    public Optional<LedgerRow> findByPolicy(String policyNo, String agentId) {
        return queryAtMostOne("""
                SELECT ledger_id, agent_id, policy_no, contract_date, fy_start, fy_end, monthly_premium,
                       trim_scale(limit_multiple)::text AS limit_multiple, limit_amount, accum_paid, cut_total, deferred_total, last_posting_seq,
                       rule_version_id
                  FROM policy_limit WHERE tenant_id = :tenantId AND policy_no = :policyNo AND agent_id = :agentId
                """, Map.of("policyNo", policyNo, "agentId", agentId), MAPPER);
    }

    public void insert(LedgerRow r) {
        update("""
                INSERT INTO policy_limit (tenant_id, ledger_id, agent_id, policy_no, contract_date, fy_start, fy_end, monthly_premium, limit_multiple,
                                          limit_amount, accum_paid, cut_total, deferred_total, last_posting_seq, rule_version_id)
                VALUES (:tenantId, :ledgerId, :agentId, :policyNo, :contractDate, :fyStart, :fyEnd, :monthlyPremium, CAST(:limitMultiple AS NUMERIC),
                        :limitAmount, :accumPaid, :cutTotal, :deferredTotal, :lastPostingSeq, :ruleVersionId)
                """, params(r));
    }

    /** 있는 행의 변하는 열(누적·삭감·이연·순번·한도·보험료)을 갱신한다 — 키·체결 정보는 바뀌지 않는다. */
    public void save(LedgerRow r) {
        int rows = update("""
                UPDATE policy_limit
                   SET monthly_premium = :monthlyPremium, limit_amount = :limitAmount, accum_paid = :accumPaid, cut_total = :cutTotal,
                       deferred_total = :deferredTotal, last_posting_seq = :lastPostingSeq
                 WHERE tenant_id = :tenantId AND ledger_id = :ledgerId
                """, params(r));
        if (rows != 1) {
            throw new IllegalStateException("policy_limit " + r.ledgerId() + " does not exist");
        }
    }

    public void insertHistory(HistoryRow r) {
        Map<String, Object> p = new HashMap<>();
        p.put("ledgerId", r.ledgerId());
        p.put("sourceSeq", r.sourceSeq());
        p.put("postingSeq", orNull(r.postingSeq()));
        p.put("kind", r.kind());
        p.put("calcId", orNull(r.calcId()));
        p.put("amount", orNull(r.amount()));
        p.put("accumAfter", r.accumAfter());
        p.put("limitAfter", r.limitAfter());
        p.put("reason", orNull(r.reason()));
        p.put("occurredAt", utc(r.occurredAt()));
        update("""
                INSERT INTO policy_limit_history (tenant_id, ledger_id, source_seq, posting_seq, kind, calc_id, amount, accum_after, limit_after,
                                                  reason, occurred_at)
                VALUES (:tenantId, :ledgerId, :sourceSeq, :postingSeq, :kind, :calcId, :amount, :accumAfter, :limitAfter, :reason, :occurredAt)
                """, p);
    }

    private static Map<String, Object> params(LedgerRow r) {
        Map<String, Object> p = new HashMap<>();
        p.put("ledgerId", r.ledgerId());
        p.put("agentId", r.agentId());
        p.put("policyNo", r.policyNo());
        p.put("contractDate", r.contractDate());
        p.put("fyStart", r.fyStart());
        p.put("fyEnd", r.fyEnd());
        p.put("monthlyPremium", r.monthlyPremium());
        p.put("limitMultiple", r.limitMultiple());
        p.put("limitAmount", r.limitAmount());
        p.put("accumPaid", r.accumPaid());
        p.put("cutTotal", r.cutTotal());
        p.put("deferredTotal", r.deferredTotal());
        p.put("lastPostingSeq", r.lastPostingSeq());
        p.put("ruleVersionId", r.ruleVersionId());
        return p;
    }
}
