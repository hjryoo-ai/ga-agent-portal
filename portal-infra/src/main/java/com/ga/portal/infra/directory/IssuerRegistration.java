package com.ga.portal.infra.directory;

import com.ga.platform.core.tenant.TenantId;

import java.util.Objects;

/** 등록된 발급자 하나 — {@code tenant.oidc_issuer}(정확 일치 키)·{@code oidc_audience}와 그 테넌트(V5, 설계서 §9 v1.6). */
public record IssuerRegistration(TenantId tenant, String issuer, String audience) {
    public IssuerRegistration {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(audience, "audience");
    }
}
