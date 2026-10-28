package com.ga.portal.app.inbox;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.app.PortalMeters;
import com.ga.portal.inbox.port.InboxMetrics;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Optional;

/**
 * 문의 지표(설계서 §10 v1.9) — {@code api_rate_limited_total{tenant, route="inquiries"}}. 시뮬레이션과 같은 이름·같은 태그 키({@code route="simulations"}).
 * v1.10: 접수 커밋 → {@code inquiries_total{tenant, action=CREATED}}({@link PortalMeters}).
 */
final class MicrometerInboxMetrics implements InboxMetrics {

    static final String ROUTE = "inquiries";

    private final Optional<MeterRegistry> registry;
    private final PortalMeters meters;

    MicrometerInboxMetrics(Optional<MeterRegistry> registry, PortalMeters meters) {
        this.registry = registry;
        this.meters = meters;
    }

    @Override
    public void inquiryCreated(TenantId tenant) {
        meters.inquiry(tenant, "CREATED");
    }

    @Override
    public void inquiryRateLimited(TenantId tenant) {
        registry.ifPresent(r -> r.counter("api_rate_limited_total", "tenant", tenant.value(), "route", ROUTE).increment());
    }
}
