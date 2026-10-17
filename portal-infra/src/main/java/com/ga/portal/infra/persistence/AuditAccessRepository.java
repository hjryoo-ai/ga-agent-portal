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
 * 바인딩된 테넌트의 {@code audit_access}(설계서 §5·§9) — 관리 행위 감사(업무 변경과 같은 트랜잭션)와 조회 API 거부 감사(별도 트랜잭션 — Phase 3).
 * 넣기와(시험·운영 조회용) 읽기만.
 */
@Repository
public class AuditAccessRepository extends TenantScopedRepository {

    /** 감사 행 하나. {@code detail}은 닫힌 사유 코드(V5 — 조회 API 거부 {@code AUTHZ_DENIED}만, 값 없음)이고 그 밖의 행은 비어 있다. */
    public record AuditRow(String actorSubject, String action, String target, String detail, Instant at) {
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

    /**
     * 조회 API 거부(포털 Phase 3 D-3·계획 승인 Q6): {@code target}은 경로 <b>템플릿</b>(경로 변수 값 없음), {@code detail}은 닫힌 사유 코드. 호출자가
     * 본 요청과 <b>다른</b> 트랜잭션에서 부른다(본 요청이 롤백돼도 남는다).
     */
    public void recordDenial(String actorSubject, String action, String target, String detail, Instant at) {
        Map<String, Object> p = new HashMap<>();
        p.put("actorSubject", actorSubject);
        p.put("action", action);
        p.put("target", target);
        p.put("detail", detail);
        p.put("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
        update("""
                INSERT INTO audit_access (tenant_id, actor_subject, actor_agent_id, action, target, detail, at)
                VALUES (:tenantId, :actorSubject, NULL, :action, :target, :detail, :at)
                """, p);
    }

    public List<AuditRow> recent(int limit) {
        return query("""
                SELECT actor_subject, action, target, detail, at
                  FROM audit_access
                 WHERE tenant_id = :tenantId
                 ORDER BY id DESC
                 LIMIT :limit
                """, Map.of("limit", limit), (rs, n) -> new AuditRow(rs.getString("actor_subject"), rs.getString("action"),
                rs.getString("target"), rs.getString("detail"), rs.getObject("at", OffsetDateTime.class).toInstant()));
    }
}
