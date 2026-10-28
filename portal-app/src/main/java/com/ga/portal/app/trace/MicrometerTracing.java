package com.ga.portal.app.trace;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.trace.Tracing;
import io.micrometer.tracing.Tracer;

import java.util.Objects;

/**
 * {@link Tracing}을 Micrometer Tracing({@code micrometer-tracing-bridge-otel} → OpenTelemetry SDK)으로(포털 Phase 7 — 내보내기는 Phase 8).
 * Observation이 아니라 {@link Tracer}를 직접 쓴다 — Observation의 지표 처리기가 닫힌 지표 표({@code docs/지표.md}) 밖의 타이머를 등록하지 않게.
 */
public final class MicrometerTracing implements Tracing {

    private final Tracer tracer;

    public MicrometerTracing(Tracer tracer) {
        this.tracer = Objects.requireNonNull(tracer, "tracer");
    }

    @Override
    public Span start(String name) {
        io.micrometer.tracing.Span span = tracer.nextSpan().name(name).start();
        return new Open(span, tracer.withSpan(span));
    }

    private record Open(io.micrometer.tracing.Span span, Tracer.SpanInScope scope) implements Span {

        @Override
        public Span tenant(TenantId tenant) {
            span.tag("tenant", tenant.value());
            return this;
        }

        @Override
        public Span count(String key, long value) {
            span.tag(key, value);
            return this;
        }

        @Override
        public Span kind(String key, String value) {
            span.tag(key, Tracing.kind(value));
            return this;
        }

        @Override
        public void close() {
            scope.close();
            span.end();
        }
    }
}
