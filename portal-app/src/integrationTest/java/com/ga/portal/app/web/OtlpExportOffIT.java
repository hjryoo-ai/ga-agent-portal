package com.ga.portal.app.web;

import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C9(포털 Phase 8 계획 Q5·④, 주입 S12): OTLP 내보내기는 끝점 속성({@code management.opentelemetry.tracing.export.otlp.endpoint})이 있을 때만 켜진다 —
 * 시험·CI 조립(이 컨텍스트 = 조회 API IT 공용 조립)에는 OTLP 연결·내보내기 빈이 0이다. 스팬은 프로세스 밖으로 나가지 않는다. 켜진 모양은
 * {@link ManagementPortIT}가 본다(같은 검사가 거짓으로 비지 않는다는 대조). 내보내기 클래스는 앱의 런타임 의존이라 이름으로 본다(시험 컴파일 경로에 더하지 않는다).
 */
class OtlpExportOffIT extends ApiIntegrationTest {

    @Autowired
    ApplicationContext context;

    static final String CONNECTION_DETAILS = "org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingConnectionDetails";
    static final List<String> OTLP_EXPORTERS = List.of("io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter",
            "io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter");

    /** 이 컨텍스트의 OTLP 내보내기 빈 클래스 이름들. */
    static List<String> otlpExporters(ApplicationContext context) {
        return context.getBeansOfType(SpanExporter.class).values().stream().map(e -> e.getClass().getName()).filter(OTLP_EXPORTERS::contains).toList();
    }

    @Test
    void noOtlpConnectionOrExporterInTheTestAssembly() throws ClassNotFoundException {
        assertThat(OTLP_EXPORTERS).allSatisfy(name -> assertThat(Class.forName(name)).as("on the app runtime classpath").isNotNull());
        assertThat(context.getBeansOfType(Class.forName(CONNECTION_DETAILS))).isEmpty();
        assertThat(otlpExporters(context)).isEmpty();
        assertThat(context.getEnvironment().getProperty("management.opentelemetry.tracing.export.otlp.endpoint")).isNull();
    }
}
