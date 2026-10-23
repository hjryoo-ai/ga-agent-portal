package com.ga.portal.app.sim;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.sim.port.SimMetrics;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Optional;

/** 시뮬레이션 지표(설계서 §10 v1.8) — {@code api_rate_limited_total{tenant}}·{@code sim_requests_total{tenant, reason}}. 설계사 태그 없음. */
final class MicrometerSimMetrics implements SimMetrics {

    private final Optional<MeterRegistry> registry;

    MicrometerSimMetrics(Optional<MeterRegistry> registry) {
        this.registry = registry;
    }

    @Override
    public void rateLimited(TenantId tenant) {
        registry.ifPresent(r -> r.counter("api_rate_limited_total", "tenant", tenant.value()).increment());
    }

    @Override
    public void request(TenantId tenant, String reason) {
        registry.ifPresent(r -> r.counter("sim_requests_total", "tenant", tenant.value(), "reason", reason).increment());
    }
}
