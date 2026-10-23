package com.ga.portal.sim;

import com.ga.portal.domain.SimulationKind;
import com.ga.portal.sim.port.EngineCredentials.EngineToken;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;

/**
 * JDK {@link HttpClient}로 엔진 시뮬레이션 두 경로를 부른다(계획 Q2 — 피드 클라이언트와 따로: 재시도·예외 분류가 다르다). 연결 시간 초과는 클라이언트(앱 설정
 * {@code portal.sim.connect-timeout}), 읽기 시간 초과는 요청({@code portal.sim.read-timeout}). 토큰은 {@code Authorization: Bearer} 머리에만.
 */
public final class HttpEngineSimulationClient implements EngineSimulationClient {

    static final String NEW_CONTRACT_PATH = "/internal/v1/simulations/new-contract";
    static final String POLICY_EVENT_PATH = "/internal/v1/simulations/policy-event";

    private final HttpClient http;
    private final Duration readTimeout;

    public HttpEngineSimulationClient(HttpClient http, Duration readTimeout) {
        this.http = Objects.requireNonNull(http, "http");
        this.readTimeout = Objects.requireNonNull(readTimeout, "readTimeout");
    }

    @Override
    public EngineReply call(URI engineBaseUrl, SimulationKind kind, EngineToken token, byte[] engineRequest) {
        String base = engineBaseUrl.toString();
        String path = kind == SimulationKind.NEW_CONTRACT ? NEW_CONTRACT_PATH : POLICY_EVENT_PATH;
        HttpRequest request = HttpRequest.newBuilder(URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path))
                .timeout(readTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token.value())
                .POST(HttpRequest.BodyPublishers.ofByteArray(engineRequest))
                .build();
        try {
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return new EngineReply.Answered(response.statusCode(), response.body());
        } catch (HttpTimeoutException e) {
            return new EngineReply.Unreachable("ENGINE_TIMEOUT");
        } catch (IOException e) {
            return new EngineReply.Unreachable("ENGINE_UNAVAILABLE");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new EngineReply.Unreachable("ENGINE_UNAVAILABLE");
        }
    }
}
