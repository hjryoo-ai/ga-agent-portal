package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.portal.domain.view.ReconRow;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

/**
 * 바인딩된 테넌트의 {@code recon_result}. 이벤트 대사(Phase 2 지시문 D-3)는 키 {@code (agent, closeYm, runAt, source, sourceSeq)}가 있으면
 * 아무것도 하지 않는다 — 재구축이 같은 DRIFT를 다시 만들어도 같다(승인 Q2). 야간 대사(Phase 7)가 읽기를 더한다.
 */
@Repository
public class ReconResultRepository extends ViewRepository {

    public ReconResultRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    public void insertIfAbsent(ReconRow r) {
        Map<String, Object> p = new HashMap<>();
        p.put("agentId", r.agentId());
        p.put("closeYm", r.closeYm());
        p.put("runAt", utc(r.runAt()));
        p.put("portalGross", r.portalGross());
        p.put("portalOffset", r.portalOffset());
        p.put("portalAdjustment", r.portalAdjustment());
        p.put("engineGross", orNull(r.engineGross()));
        p.put("engineOffset", orNull(r.engineOffset()));
        p.put("engineAdjustment", orNull(r.engineAdjustment()));
        p.put("engineNet", orNull(r.engineNet()));
        p.put("status", r.status());
        p.put("source", r.source().name());
        p.put("sourceSeq", r.sourceSeq());
        update("""
                INSERT INTO recon_result (tenant_id, agent_id, close_ym, run_at, portal_gross, portal_offset, portal_adjustment, engine_gross,
                                          engine_offset, engine_adjustment, engine_net, status, source, source_seq)
                VALUES (:tenantId, :agentId, :closeYm, :runAt, :portalGross, :portalOffset, :portalAdjustment, :engineGross, :engineOffset,
                        :engineAdjustment, :engineNet, :status, :source, :sourceSeq)
                ON CONFLICT (tenant_id, agent_id, close_ym, run_at, source, source_seq) DO NOTHING
                """, p);
    }
}
