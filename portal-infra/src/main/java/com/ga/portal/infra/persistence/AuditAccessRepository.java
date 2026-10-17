package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.platform.spring.jdbc.TenantScopedRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 바인딩된 테넌트의 {@code audit_access}(설계서 §5·§9) — 관리 행위 감사. 업무 변경과 같은 트랜잭션에서 쓴다. 넣기와(시험·운영 조회용) 읽기만.
 */
@Repository
public class AuditAccessRepository extends TenantScopedRepository {

    /** 감사 행 하나. {@code actorAgentId}는 설계사가 아닌 행위자(운영 CLI)면 없다. */
    public record AuditRow(String actorSubject, String action, String target, Instant at) {
    }

    public AuditAccessRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    public void record(String actorSubject, String action, String target, Instant at) {
        Map<String, Object> p = new HashMap<>();
        p.put("actorSubject", actorSubject);
        p.put("action", action);
        p.put("target", target);
        p.put("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
        update("""
                INSERT INTO audit_access (tenant_id, actor_subject, actor_agent_id, action, target, at)
                VALUES (:tenantId, :actorSubject, NULL, :action, :target, :at)
                """, p);
    }

    public List<AuditRow> recent(int limit) {
        return query("""
                SELECT actor_subject, action, target, at
                  FROM audit_access
                 WHERE tenant_id = :tenantId
                 ORDER BY id DESC
                 LIMIT :limit
                """, Map.of("limit", limit), (rs, n) -> new AuditRow(rs.getString("actor_subject"), rs.getString("action"),
                rs.getString("target"), rs.getObject("at", OffsetDateTime.class).toInstant()));
    }
}
