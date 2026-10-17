package com.ga.portal.infra.persistence;

import com.ga.platform.core.tenant.AgentId;
import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.platform.spring.jdbc.TenantScopedRepository;
import com.ga.portal.domain.IdentityLinkRecord;
import com.ga.portal.domain.LinkStatus;
import com.ga.portal.domain.Role;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 바인딩된 테넌트의 {@code identity_link}(V5) — PK {@code (tenant_id, subject)} 조회 하나. 등록·해제는 운영 SQL(설계서 §9 v1.6, {@code docs/운영.md}).
 */
@Repository
public class IdentityLinkRepository extends TenantScopedRepository {

    public IdentityLinkRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    public Optional<IdentityLinkRecord> find(String subject) {
        return queryAtMostOne("""
                SELECT subject, agent_id, role, status, org_path, linked_at
                  FROM identity_link
                 WHERE tenant_id = :tenantId AND subject = :subject
                """, Map.of("subject", subject), (rs, n) -> new IdentityLinkRecord(
                rs.getString("subject"),
                Optional.ofNullable(rs.getString("agent_id")).map(AgentId::of),
                Role.valueOf(rs.getString("role")),
                LinkStatus.valueOf(rs.getString("status")),
                Optional.ofNullable(rs.getString("org_path")),
                rs.getObject("linked_at", OffsetDateTime.class).toInstant()));
    }
}
