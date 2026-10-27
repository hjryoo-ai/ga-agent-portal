package com.ga.portal.app.trace;

import com.ga.portal.domain.trace.Tracing;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 스팬 포트 — 추적기가 없으면(트레이싱 꺼짐) {@link Tracing#NONE}. */
@Configuration(proxyBeanMethods = false)
public class TracingConfiguration {

    @Bean
    Tracing tracing(ObjectProvider<Tracer> tracer) {
        Tracer t = tracer.getIfAvailable();
        return t == null || t == Tracer.NOOP ? Tracing.NONE : new MicrometerTracing(t);
    }
}
