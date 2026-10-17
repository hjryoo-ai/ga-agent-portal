package com.ga.portal.app.web;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.query.DenyReason;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Optional;

/**
 * 조회 API 거부 지표(설계서 §10 v1.6): {@code api_authn_rejected_total{tenant, reason}}(401 — {@code tenant}는 발급자가 등록된 경우만 그 테넌트, 아니면
 * {@code none}: 태그 키 집합은 늘 같다), {@code api_authz_denied_total{tenant, reason}}(404). 설계사·subject 태그는 없다(D-14).
 */
final class ApiMetrics {

    static final String NO_TENANT = "none";

    private final Optional<MeterRegistry> registry;

    ApiMetrics(Optional<MeterRegistry> registry) {
        this.registry = registry;
    }

    void authnRejected(RejectionReason reason, Optional<TenantId> tenant) {
        registry.ifPresent(r -> r.counter("api_authn_rejected_total", "tenant", tenant.map(TenantId::value).orElse(NO_TENANT),
                "reason", reason.name()).increment());
    }

    void authzDenied(TenantId tenant, DenyReason reason) {
        registry.ifPresent(r -> r.counter("api_authz_denied_total", "tenant", tenant.value(), "reason", reason.name()).increment());
    }
}
