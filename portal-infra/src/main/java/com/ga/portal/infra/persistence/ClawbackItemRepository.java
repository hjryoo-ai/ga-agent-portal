package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.portal.domain.ReceivableStatus;
import com.ga.portal.domain.view.ReceivableRow;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 바인딩된 테넌트의 {@code clawback_item} — upsert. */
@Repository
public class ClawbackItemRepository extends ViewRepository {

    private static final RowMapper<ReceivableRow> MAPPER = (rs, n) -> new ReceivableRow(rs.getString("receivable_id"), rs.getString("agent_id"),
            rs.getString("origin_calc_id"), rs.getLong("amount"), rs.getLong("remaining"), ReceivableStatus.valueOf(rs.getString("status")),
            rs.getLong("last_seq"));

    public ClawbackItemRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    public Optional<ReceivableRow> find(String receivableId) {
        return queryAtMostOne("""
                SELECT receivable_id, agent_id, origin_calc_id, amount, remaining, status, last_seq
                  FROM clawback_item WHERE tenant_id = :tenantId AND receivable_id = :receivableId
                """, Map.of("receivableId", receivableId), MAPPER);
    }

    public void save(ReceivableRow r) {
        Map<String, Object> p = new HashMap<>();
        p.put("receivableId", r.receivableId());
        p.put("agentId", r.agentId());
        p.put("originCalcId", r.originCalcId());
        p.put("amount", r.amount());
        p.put("remaining", r.remaining());
        p.put("status", r.status().name());
        p.put("lastSeq", r.lastSeq());
        update("""
                INSERT INTO clawback_item (tenant_id, receivable_id, agent_id, origin_calc_id, amount, remaining, status, last_seq)
                VALUES (:tenantId, :receivableId, :agentId, :originCalcId, :amount, :remaining, :status, :lastSeq)
                ON CONFLICT (tenant_id, receivable_id) DO UPDATE
                   SET agent_id = EXCLUDED.agent_id, origin_calc_id = EXCLUDED.origin_calc_id, amount = EXCLUDED.amount,
                       remaining = EXCLUDED.remaining, status = EXCLUDED.status, last_seq = EXCLUDED.last_seq
                 WHERE clawback_item.tenant_id = :tenantId
                """, p);
    }
}
