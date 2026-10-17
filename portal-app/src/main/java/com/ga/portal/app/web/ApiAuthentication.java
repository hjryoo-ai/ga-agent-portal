package com.ga.portal.app.web;

import com.ga.platform.core.tenant.TenantId;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.io.Serial;
import java.util.List;
import java.util.Objects;

/**
 * 검증을 마친 토큰에서 남기는 것 둘 — 발급자의 테넌트와 {@code sub}. 권한(authority)은 하나도 없다: 역할은 {@code identity_link}에서만 온다(불변 규약 3).
 * 이 뒤의 계층은 {@code Jwt}를 보지 않는다.
 */
final class ApiAuthentication extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient TenantId tenant;
    private final String subject;

    ApiAuthentication(TenantId tenant, String subject) {
        super(List.of());
        this.tenant = Objects.requireNonNull(tenant, "tenant");
        this.subject = Objects.requireNonNull(subject, "subject");
        super.setAuthenticated(true);
    }

    TenantId tenant() {
        return tenant;
    }

    String subject() {
        return subject;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return subject;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ApiAuthentication a && tenant.equals(a.tenant) && subject.equals(a.subject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenant, subject);
    }

    /** {@code sub} 원문을 싣지 않는다(기본 {@code toString}은 주체를 쓴다 — 로그로 새지 않게, D-14). */
    @Override
    public String toString() {
        return "ApiAuthentication[tenant=" + tenant.value() + ", subject#=" + LogIds.subject(subject) + "]";
    }
}
