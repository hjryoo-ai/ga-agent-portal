package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.portal.domain.DeferralStatus;
import com.ga.portal.domain.view.DeferralRow;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 바인딩된 테넌트의 {@code deferral_item}. */
@Repository
public class DeferralItemRepository extends ViewRepository {

    private static final RowMapper<DeferralRow> MAPPER = (rs, n) -> new DeferralRow(rs.getString("schedule_id"), rs.getString("agent_id"),
            rs.getString("policy_no"), rs.getString("source_calc_id"), rs.getString("due_ym"), rs.getLong("amount"), rs.getString("pay_condition"),
            DeferralStatus.valueOf(rs.getString("status")), rs.getString("reason"), rs.getString("curve_version_id"),
            optString(rs, "status_reason"), rs.getLong("last_seq"));

    public DeferralItemRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    public Optional<DeferralRow> find(String scheduleId) {
        return queryAtMostOne("""
                SELECT schedule_id, agent_id, policy_no, source_calc_id, due_ym, amount, pay_condition, status, reason, curve_version_id,
                       status_reason, last_seq
                  FROM deferral_item WHERE tenant_id = :tenantId AND schedule_id = :scheduleId
                """, Map.of("scheduleId", scheduleId), MAPPER);
    }

    public void insert(DeferralRow r) {
        update("""
                INSERT INTO deferral_item (tenant_id, schedule_id, agent_id, policy_no, source_calc_id, due_ym, amount, pay_condition, status,
                                           reason, curve_version_id, status_reason, last_seq)
                VALUES (:tenantId, :scheduleId, :agentId, :policyNo, :sourceCalcId, :dueYm, :amount, :payCondition, :status, :reason,
                        :curveVersionId, :statusReason, :lastSeq)
                """, params(r));
    }

    public void save(DeferralRow r) {
        int rows = update("""
                UPDATE deferral_item SET status = :status, status_reason = :statusReason, last_seq = :lastSeq
                 WHERE tenant_id = :tenantId AND schedule_id = :scheduleId
                """, params(r));
        if (rows != 1) {
            throw new IllegalStateException("deferral_item " + r.scheduleId() + " does not exist");
        }
    }

    private static Map<String, Object> params(DeferralRow r) {
        Map<String, Object> p = new HashMap<>();
        p.put("scheduleId", r.scheduleId());
        p.put("agentId", r.agentId());
        p.put("policyNo", r.policyNo());
        p.put("sourceCalcId", r.sourceCalcId());
        p.put("dueYm", r.dueYm());
        p.put("amount", r.amount());
        p.put("payCondition", r.payCondition());
        p.put("status", r.status().name());
        p.put("reason", r.reason());
        p.put("curveVersionId", r.curveVersionId());
        p.put("statusReason", orNull(r.statusReason()));
        p.put("lastSeq", r.lastSeq());
        return p;
    }
}
