package com.ga.portal.app.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포털 Phase 8 계획 Q5·P-17: compose 조립과 같은 관리 포트 설정 — 관리 포트(임의)에서만 {@code /actuator/prometheus}와 헬스 셋이 열리고, 앱 포트의
 * {@code /actuator/prometheus}는 나머지 체인의 같은 404 바이트 그대로다(관리 포트의 다른 경로도 같은 404). OTLP 끝점 속성이 있으면 내보내기 빈이 생긴다
 * ({@link OtlpExportOffIT}의 대조 — 끝점은 닫힌 포트, 내보내기 실패는 앱 동작과 무관).
 */
@TestPropertySource(properties = {
        "management.server.port=0",
        "management.endpoints.web.exposure.include=health,prometheus",
        "management.opentelemetry.tracing.export.otlp.endpoint=http://127.0.0.1:9/v1/traces",
})
class ManagementPortIT extends ApiIntegrationTest {

    @LocalManagementPort
    int managementPort;

    @Autowired
    ApplicationContext context;

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    static HttpResponse<byte[]> plain(int port, String path) {
        try {
            return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    void prometheusAndHealthAreOnTheManagementPortOnly() {
        assertThat(managementPort).isNotEqualTo(port);
        HttpResponse<byte[]> scrape = plain(managementPort, "/actuator/prometheus");
        assertThat(scrape.statusCode()).isEqualTo(200);
        assertThat(scrape.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/plain");
        assertThat(new String(scrape.body(), java.nio.charset.StandardCharsets.UTF_8)).contains("jvm_");
        assertThat(plain(managementPort, "/actuator/health").statusCode()).isEqualTo(200);

        HttpResponse<byte[]> unrouted = plain(port, "/no-such-path");
        for (HttpResponse<byte[]> r : List.of(plain(port, "/actuator/prometheus"), plain(managementPort, "/actuator/env"),
                plain(managementPort, "/actuator"))) {
            assertThat(r.statusCode()).as(r.uri().toString()).isEqualTo(404);
            assertThat(r.body()).as(r.uri().toString()).isEqualTo(UniformRejectionIT.NOT_FOUND);
        }
        assertThat(unrouted.body()).isEqualTo(UniformRejectionIT.NOT_FOUND);
    }

    @Test
    void anOtlpEndpointPropertyTurnsTheExporterOn() {
        assertThat(OtlpExportOffIT.otlpExporters(context)).containsExactly("io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter");
    }
}
