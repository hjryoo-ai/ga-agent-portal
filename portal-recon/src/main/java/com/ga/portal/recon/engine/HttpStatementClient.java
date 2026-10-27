package com.ga.portal.recon.engine;

import com.ga.portal.recon.port.ServiceToken;
import com.ga.portal.recon.rule.EngineAnswer;
import com.ga.portal.recon.rule.ReconMode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * JDK {@link HttpClient}로 엔진 명세 조회({@code GET /internal/v1/agents/{agentId}/statements/{ym}?mode=})를 부른다(계획 ④ — 시뮬레이션 클라이언트와
 * 같은 규약: 연결 시간 초과는 클라이언트, 읽기 시간 초과는 요청, 토큰은 {@code Authorization: Bearer} 머리에만, 재시도 0). 닿지 못하면
 * {@code ENGINE_UNAVAILABLE}.
 */
public final class HttpStatementClient {

    static final String PATH = "/internal/v1/agents/%s/statements/%s?mode=%s";

    private final HttpClient http;
    private final Duration readTimeout;

    public HttpStatementClient(HttpClient http, Duration readTimeout) {
        this.http = Objects.requireNonNull(http, "http");
        this.readTimeout = Objects.requireNonNull(readTimeout, "readTimeout");
    }

    public EngineAnswer fetch(URI engineBaseUrl, ServiceToken token, String agentId, String ym, ReconMode mode) {
        String base = engineBaseUrl.toString();
        String path = PATH.formatted(URLEncoder.encode(agentId, StandardCharsets.UTF_8), URLEncoder.encode(ym, StandardCharsets.UTF_8), mode.name());
        HttpRequest request = HttpRequest.newBuilder(URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path))
                .timeout(readTimeout)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token.value())
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return StatementResponses.read(agentId, ym, mode, response.statusCode(), response.body());
        } catch (IOException e) {
            return EngineAnswer.unavailable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return EngineAnswer.unavailable();
        }
    }
}
