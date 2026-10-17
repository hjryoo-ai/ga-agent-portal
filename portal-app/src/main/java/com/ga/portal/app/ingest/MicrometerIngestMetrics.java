package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.IngestState;
import com.ga.portal.ingest.apply.IngestMetrics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 인제스트 지표(지시문 D-9, 설계서 §10) — 태그 {@code tenant}: {@code ingest_lag_seq}, {@code ingest_lag_seconds}, {@code ingest_state}
 * (상태별 0/1), {@code ingest_events_total{result}}, {@code ingest_feed_errors_total}. 대시보드·알림은 Phase 7.
 */
final class MicrometerIngestMetrics implements IngestMetrics {

    private final MeterRegistry registry;
    private final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    MicrometerIngestMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void event(TenantId tenant, String result) {
        registry.counter("ingest_events_total", "tenant", tenant.value(), "result", result).increment();
    }

    @Override
    public void feedError(TenantId tenant) {
        registry.counter("ingest_feed_errors_total", "tenant", tenant.value()).increment();
    }

    @Override
    public void lag(TenantId tenant, long lagSeq, long lagSeconds) {
        gauge("ingest_lag_seq", tenant, null).set(lagSeq);
        gauge("ingest_lag_seconds", tenant, null).set(lagSeconds);
    }

    @Override
    public void state(TenantId tenant, IngestState state) {
        for (IngestState s : IngestState.values()) {
            gauge("ingest_state", tenant, s.name()).set(s == state ? 1 : 0);
        }
    }

    private AtomicLong gauge(String name, TenantId tenant, String state) {
        String key = name + "|" + tenant.value() + "|" + state;
        return gauges.computeIfAbsent(key, k -> {
            AtomicLong value = new AtomicLong();
            Gauge.Builder<AtomicLong> b = Gauge.builder(name, value, AtomicLong::get).tag("tenant", tenant.value());
            if (state != null) {
                b = b.tag("state", state);
            }
            b.register(registry);
            return value;
        });
    }
}
