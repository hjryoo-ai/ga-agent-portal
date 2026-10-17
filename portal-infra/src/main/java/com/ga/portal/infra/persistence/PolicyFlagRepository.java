package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.portal.domain.FlagType;
import com.ga.portal.domain.view.FlagRow;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 바인딩된 테넌트의 {@code policy_flag} — 키 {@code (policy_no, agent_id, flag_type)} upsert. */
@Repository
public class PolicyFlagRepository extends ViewRepository {

    private static final RowMapper<FlagRow> MAPPER = (rs, n) -> new FlagRow(rs.getString("policy_no"), rs.getString("agent_id"),
            FlagType.valueOf(rs.getString("flag_type")), date(rs, "occurred_on"), optInstant(rs, "cleared_at"),
            optString(rs, "reason"));

    public PolicyFlagRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    public List<FlagRow> find(String policyNo, String agentId) {
        return query("""
                SELECT policy_no, agent_id, flag_type, occurred_on, cleared_at, reason
                  FROM policy_flag WHERE tenant_id = :tenantId AND policy_no = :policyNo AND agent_id = :agentId
                 ORDER BY flag_type
                """, Map.of("policyNo", policyNo, "agentId", agentId), MAPPER);
    }

    public void save(FlagRow r) {
        Map<String, Object> p = new HashMap<>();
        p.put("policyNo", r.policyNo());
        p.put("agentId", r.agentId());
        p.put("flagType", r.flagType().name());
        p.put("occurredOn", r.occurredOn());
        p.put("clearedAt", utc(r.clearedAt()));
        p.put("reason", orNull(r.reason()));
        update("""
                INSERT INTO policy_flag (tenant_id, policy_no, agent_id, flag_type, occurred_on, cleared_at, reason)
                VALUES (:tenantId, :policyNo, :agentId, :flagType, :occurredOn, :clearedAt, :reason)
                ON CONFLICT (tenant_id, policy_no, agent_id, flag_type) DO UPDATE
                   SET occurred_on = EXCLUDED.occurred_on, cleared_at = EXCLUDED.cleared_at, reason = EXCLUDED.reason
                 WHERE policy_flag.tenant_id = :tenantId
                """, p);
    }
}
