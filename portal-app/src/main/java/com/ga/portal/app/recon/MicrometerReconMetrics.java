package com.ga.portal.app.recon;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.app.PortalMeters;
import com.ga.portal.domain.NotificationType;
import com.ga.portal.domain.ReconRunResult;
import com.ga.portal.recon.port.ReconMetrics;
import com.ga.portal.recon.rule.CheckResult;
import com.ga.portal.recon.rule.InconclusiveReason;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 대사 지표({@code docs/지표.md} — 이름마다 태그 집합 하나). 레지스트리가 없으면 아무것도 하지 않는다. */
final class MicrometerReconMetrics implements ReconMetrics {

    private final Optional<MeterRegistry> registry;
    private final PortalMeters meters;
    private final Map<String, AtomicLong> unresolved = new ConcurrentHashMap<>();

    MicrometerReconMetrics(Optional<MeterRegistry> registry, PortalMeters meters) {
        this.registry = registry;
        this.meters = meters;
    }

    @Override
    public void check(TenantId tenant, CheckResult result) {
        registry.ifPresent(r -> r.counter("recon_checks_total", "tenant", tenant.value(), "result", result.name()).increment());
    }

    @Override
    public void inconclusive(TenantId tenant, InconclusiveReason reason) {
        registry.ifPresent(r -> r.counter("recon_inconclusive_total", "tenant", tenant.value(), "reason", reason.name()).increment());
    }

    @Override
    public void run(TenantId tenant, ReconRunResult result) {
        registry.ifPresent(r -> r.counter("recon_runs_total", "tenant", tenant.value(), "result", result.name()).increment());
    }

    @Override
    public void unresolved(TenantId tenant, int count) {
        registry.ifPresent(r -> unresolved.computeIfAbsent(tenant.value(), t -> {
            AtomicLong v = new AtomicLong();
            Gauge.builder("recon_unresolved_drift", v, AtomicLong::get).tag("tenant", t).register(r);
            return v;
        }).set(count));
    }

    @Override
    public void notificationsDerived(TenantId tenant, NotificationType type, int inserted) {
        meters.notificationsDerived(tenant, type.name(), inserted);   // 저장 메서드가 커밋한 뒤에 불린다
    }
}
