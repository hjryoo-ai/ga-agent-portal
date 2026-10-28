package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.IngestState;
import com.ga.portal.ingest.apply.IngestMetrics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 인제스트 지표(지시문 D-9, 설계서 §10) — 태그 {@code tenant}: {@code ingest_lag_seq}, {@code ingest_lag_seconds}, {@code ingest_state}
 * (상태별 0/1), {@code ingest_events_total{result}}, {@code ingest_feed_errors_total}, {@code projection_drift_total}(Phase 2 D-3),
 * {@code ingest_rebuild_seconds{outcome}}(Phase 2 계획 ⑥).
 * 대시보드·알림은 Phase 7.
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
    public void drift(TenantId tenant) {
        registry.counter("projection_drift_total", "tenant", tenant.value()).increment();
    }

    @Override
    public void rebuild(TenantId tenant, String outcome, Duration took) {
        Timer.builder("ingest_rebuild_seconds").tag("tenant", tenant.value()).tag("outcome", outcome).register(registry).record(took);
    }

    @Override
    public void lag(TenantId tenant, long lagSeq, long lagSeconds) {
        // 등록 문장마다 이름·태그 키가 글자 그대로, 사슬이 .register(로 끝난다 — 지표 목록 시험(MetricsCatalogTest)이 원천에서 읽는다(포털 Phase 7)
        gauges.computeIfAbsent("ingest_lag_seq|" + tenant.value(), k -> {
            AtomicLong value = new AtomicLong();
            Gauge.builder("ingest_lag_seq", value, AtomicLong::get).tag("tenant", tenant.value()).register(registry);
            return value;
        }).set(lagSeq);
        gauges.computeIfAbsent("ingest_lag_seconds|" + tenant.value(), k -> {
            AtomicLong value = new AtomicLong();
            Gauge.builder("ingest_lag_seconds", value, AtomicLong::get).tag("tenant", tenant.value()).register(registry);
            return value;
        }).set(lagSeconds);
    }

    @Override
    public void state(TenantId tenant, IngestState state) {
        for (IngestState s : IngestState.values()) {
            gauges.computeIfAbsent("ingest_state|" + tenant.value() + "|" + s.name(), k -> {
                AtomicLong value = new AtomicLong();
                Gauge.builder("ingest_state", value, AtomicLong::get).tag("tenant", tenant.value()).tag("state", s.name()).register(registry);
                return value;
            }).set(s == state ? 1 : 0);
        }
    }
}
