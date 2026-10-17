package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.portal.domain.AgentStatus;
import com.ga.portal.domain.view.AgentRow;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 바인딩된 테넌트의 {@code agent_profile}(설계서 §5 v1.5) — {@code AgentSnapshot} upsert. */
@Repository
public class AgentProfileRepository extends ViewRepository {

    private static final RowMapper<AgentRow> MAPPER = (rs, n) -> new AgentRow(rs.getString("agent_id"), rs.getString("name"),
            optString(rs, "grade"), rs.getString("org_path"), optString(rs, "role"), AgentStatus.valueOf(rs.getString("status")),
            date(rs, "valid_from"), rs.getLong("last_seq"));

    public AgentProfileRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    public Optional<AgentRow> find(String agentId) {
        return queryAtMostOne("""
                SELECT agent_id, name, grade, org_path, role, status, valid_from, last_seq
                  FROM agent_profile WHERE tenant_id = :tenantId AND agent_id = :agentId
                """, Map.of("agentId", agentId), MAPPER);
    }

    public void save(AgentRow r) {
        Map<String, Object> p = new HashMap<>();
        p.put("agentId", r.agentId());
        p.put("name", r.name());
        p.put("grade", orNull(r.grade()));
        p.put("orgPath", r.orgPath());
        p.put("role", orNull(r.role()));
        p.put("status", r.status().name());
        p.put("validFrom", r.validFrom());
        p.put("lastSeq", r.lastSeq());
        update("""
                INSERT INTO agent_profile (tenant_id, agent_id, name, grade, org_path, role, status, valid_from, last_seq)
                VALUES (:tenantId, :agentId, :name, :grade, :orgPath, :role, :status, :validFrom, :lastSeq)
                ON CONFLICT (tenant_id, agent_id) DO UPDATE
                   SET name = EXCLUDED.name, grade = EXCLUDED.grade, org_path = EXCLUDED.org_path, role = EXCLUDED.role,
                       status = EXCLUDED.status, valid_from = EXCLUDED.valid_from, last_seq = EXCLUDED.last_seq
                 WHERE agent_profile.tenant_id = :tenantId
                """, p);
    }
}
