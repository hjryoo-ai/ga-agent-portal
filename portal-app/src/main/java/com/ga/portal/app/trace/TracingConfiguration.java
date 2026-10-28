package com.ga.portal.app.trace;

import com.ga.portal.domain.trace.Tracing;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 스팬 포트 — 추적기가 없으면(트레이싱 꺼짐) {@link Tracing#NONE}.
 *
 * <p>포털 Phase 8(OTLP 내보내기, 계획 ④·P-21): 프레임워크가 만드는 관측(Spring MVC {@code http.server.requests})도 스팬이 된다. 그 높은 기수 키
 * ({@code http.url} — 요청 경로 원문: 문의 id·{@code calcId}·월이 실린다)는 스팬에 싣지 않는다 — 낮은 기수 키(경로 틀 {@code uri}·{@code method}·
 * {@code status}·{@code outcome}·{@code exception})만 남는다({@code docs/지표.md} §4 프레임워크 스팬 표). 지표는 낮은 기수 키만 쓰므로 그대로다.
 * 포털 자신의 스팬은 관측이 아니라 추적기를 직접 쓰므로({@link MicrometerTracing}) 이 거르개와 무관하다. Spring Security·예약 작업의 관측은
 * {@code application.yaml}에서 끈다.
 */
@Configuration(proxyBeanMethods = false)
public class TracingConfiguration {

    @Bean
    Tracing tracing(ObjectProvider<Tracer> tracer) {
        Tracer t = tracer.getIfAvailable();
        return t == null || t == Tracer.NOOP ? Tracing.NONE : new MicrometerTracing(t);
    }

    @Bean
    ObservationFilter noHighCardinalityKeyValues() {
        return TracingConfiguration::dropHighCardinality;
    }

    static io.micrometer.observation.Observation.Context dropHighCardinality(io.micrometer.observation.Observation.Context context) {
        return context.removeHighCardinalityKeyValues(context.getHighCardinalityKeyValues().stream().map(KeyValue::getKey).toArray(String[]::new));
    }
}
