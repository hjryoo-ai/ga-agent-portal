package com.ga.portal.ingest.feed;

import com.ga.platform.canonical.CanonicalizationException;
import com.ga.platform.canonical.Canonicalizer;
import com.ga.platform.core.tenant.TenantId;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;

/**
 * 엔진 피드 HTTP 클라이언트(계약 {@code engine-internal.openapi.yaml} {@code listEvents}). 엔진은 테넌트별 인스턴스라 주소는 테넌트마다
 * ({@code tenant.engine_base_url}). 서비스 토큰은 {@code Authorization: Bearer}로만 보내고 어떤 메시지에도 넣지 않는다.
 * 200 → 응답 해석, 401 → {@link FeedException.Unauthorized}, 422 {@code AFTER_BEYOND_HEAD} → {@link FeedException.AfterBeyondHead},
 * 그 밖의 상태·연결 실패·시간 초과 → {@link FeedException.Unavailable}.
 */
public final class HttpFeedClient implements FeedClient {

    static final String PATH = "/internal/v1/events";

    private final HttpClient http;
    private final Function<TenantId, URI> baseUrls;
    private final ServiceTokenSource tokens;
    private final Duration timeout;

    public HttpFeedClient(HttpClient http, Function<TenantId, URI> baseUrls, ServiceTokenSource tokens, Duration timeout) {
        this.http = Objects.requireNonNull(http, "http");
        this.baseUrls = Objects.requireNonNull(baseUrls, "baseUrls");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public FeedPage fetch(TenantId tenant, long afterSeq, int limit) {
        FeedClient.checkArguments(afterSeq, limit);
        ServiceTokenSource.ServiceToken token = tokens.tokenFor(tenant).orElseThrow(() -> new FeedException.TokenMissing(tenant));
        URI base = baseUrls.apply(tenant);
        URI uri = URI.create(stripSlash(base.toString()) + PATH + "?afterSeq=" + afterSeq + "&limit=" + limit);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token.value())
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new FeedException.Unavailable("feed of tenant " + tenant.value() + " unreachable: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FeedException.Unavailable("interrupted while reading the feed of tenant " + tenant.value());
        }
        return switch (response.statusCode()) {
            case 200 -> FeedResponses.parse(tenant, response.body());
            case 401 -> throw new FeedException.Unauthorized(tenant);
            case 422 -> throw unprocessable(tenant, afterSeq, response.body());
            default -> throw new FeedException.Unavailable("feed of tenant " + tenant.value() + " answered " + response.statusCode());
        };
    }

    private static FeedException unprocessable(TenantId tenant, long afterSeq, String body) {
        try {
            JsonNode problem = Canonicalizer.parseStrict(body);
            if ("AFTER_BEYOND_HEAD".equals(problem.path("code").asString())) {
                return new FeedException.AfterBeyondHead(afterSeq);
            }
        } catch (CanonicalizationException e) {
            // 형식이 아닌 422는 아래로
        }
        return new FeedException.Unavailable("feed of tenant " + tenant.value() + " answered 422 without AFTER_BEYOND_HEAD");
    }

    private static String stripSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
