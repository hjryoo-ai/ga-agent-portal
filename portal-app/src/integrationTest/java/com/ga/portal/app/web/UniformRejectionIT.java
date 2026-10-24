package com.ga.portal.app.web;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C2·C3(3단계 몫): 401 여덟 사유가 <b>같은 바이트·같은 헤더</b>이고 사유는 지표에만 남는다. 404 — 링크 없음·REVOKED·OPERATOR·없는 자원(시험 진입점)·없는 경로·메서드
 * 불일치·경로 변수 형식 — 도 같은 바이트·같은 헤더. {@code MANAGER}와 남의 자원은 조회 진입점이 생기는 4단계에서 더한다. 쿼리 파라미터 형식 오류만 400 한 가지.
 * 포털 Phase 6(C6): 쿠키만 싣고 {@code Authorization}이 없는 {@code GET}·{@code PATCH}·{@code POST}는 같은 401 바이트 — 인증은 Bearer 머리뿐이라 쿠키로는
 * 아무것도 열리지 않는다(CSRF 토큰·{@code Origin} 검사를 두지 않는 근거, 설계서 §9 v1.9). 새 경로의 메서드 불일치도 같은 404. 문의 429 바이트는 여기 고정한다.
 */
class UniformRejectionIT extends ApiIntegrationTest {

    static final byte[] UNAUTHENTICATED = "{\"code\":\"UNAUTHENTICATED\",\"message\":\"authentication required\"}".getBytes(StandardCharsets.UTF_8);
    static final byte[] NOT_FOUND = "{\"code\":\"NOT_FOUND\",\"message\":\"not found\"}".getBytes(StandardCharsets.UTF_8);
    static final byte[] INVALID_REQUEST = "{\"code\":\"INVALID_REQUEST\",\"message\":\"invalid request\"}".getBytes(StandardCharsets.UTF_8);
    static final byte[] INQUIRY_RATE_LIMITED = "{\"code\":\"RATE_LIMITED\",\"message\":\"too many inquiries\"}".getBytes(StandardCharsets.UTF_8);
    static final String WHOAMI = "/api/v1/test/whoami";

    record Case(String name, RejectionReason reason, String tenantTag, UnaryOperator<HttpRequest.Builder> request) {
    }

    static UnaryOperator<HttpRequest.Builder> bearer(String token) {
        return b -> b.header("Authorization", "Bearer " + token);
    }

    static List<Case> unauthenticatedCases() throws JOSEException {
        String t1 = T1.value();
        String none = ApiMetrics.NO_TENANT;
        String hs256;
        SignedJWT hs = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), ISSUER_1.claims(SUB_A, AUD_1).build());
        hs.sign(new MACSigner("0123456789abcdef0123456789abcdef"));
        hs256 = hs.serialize();
        RSAKey sameKidOtherKey = new RSAKeyGenerator(2048).keyID(ISSUER_1.key().getKeyID()).generate();
        Instant now = Instant.now();
        List<Case> cases = new ArrayList<>();
        cases.add(new Case("no header", RejectionReason.NO_TOKEN, none, b -> b));
        cases.add(new Case("basic scheme", RejectionReason.NO_TOKEN, none, b -> b.header("Authorization", "Basic dXNlcjpwYXNz")));
        cases.add(new Case("two bearer headers", RejectionReason.NO_TOKEN, none,
                b -> b.header("Authorization", "Bearer " + ISSUER_1.token(SUB_A, AUD_1)).header("Authorization", "Bearer " + ISSUER_1.token(SUB_B, AUD_1))));
        cases.add(new Case("not a jwt", RejectionReason.MALFORMED, none, bearer("not-a-jwt")));
        cases.add(new Case("bad bearer characters", RejectionReason.MALFORMED, none, b -> b.header("Authorization", "Bearer !!!")));
        cases.add(new Case("no sub", RejectionReason.MALFORMED, none, bearer(ISSUER_1.token(SUB_A, AUD_1, c -> c.subject(null)))));
        cases.add(new Case("alg none", RejectionReason.ALG_NOT_ALLOWED, none, bearer(new PlainJWT(ISSUER_1.claims(SUB_A, AUD_1).build()).serialize())));
        cases.add(new Case("alg HS256", RejectionReason.ALG_NOT_ALLOWED, none, bearer(hs256)));
        cases.add(new Case("unregistered issuer", RejectionReason.ISSUER_UNKNOWN, none, bearer(STRANGER.token(SUB_A, AUD_1))));
        cases.add(new Case("another issuer's key", RejectionReason.SIGNATURE, t1,
                bearer(TestIssuer.sign(STRANGER.key(), ISSUER_1.claims(SUB_A, AUD_1).build()))));
        cases.add(new Case("same kid, other key", RejectionReason.SIGNATURE, t1,
                bearer(TestIssuer.sign(sameKidOtherKey, ISSUER_1.claims(SUB_A, AUD_1).build()))));
        cases.add(new Case("expired", RejectionReason.EXPIRED, t1, bearer(ISSUER_1.token(SUB_A, AUD_1,
                c -> c.issueTime(Date.from(now.minus(Duration.ofMinutes(20)))).expirationTime(Date.from(now.minus(Duration.ofMinutes(10))))))));
        cases.add(new Case("not yet valid", RejectionReason.EXPIRED, t1, bearer(ISSUER_1.token(SUB_A, AUD_1,
                c -> c.notBeforeTime(Date.from(now.plus(Duration.ofMinutes(10))))))));
        cases.add(new Case("wrong audience", RejectionReason.AUDIENCE, t1, bearer(ISSUER_1.token(SUB_A, AUD_2))));
        cases.add(new Case("tenant claim of another tenant", RejectionReason.TENANT_MISMATCH, t1,
                bearer(ISSUER_1.token(SUB_A, AUD_1, c -> c.claim("tenant_id", T2.value())))));
        return cases;
    }

    @Test
    void everyUnauthenticatedRequestGetsTheSameBytesAndHeadersAndOnlyTheMetricKnowsWhy() throws JOSEException {
        List<Case> cases = unauthenticatedCases();
        assertThat(cases).extracting(Case::reason).containsAll(List.of(RejectionReason.values()));
        Map<String, Double> before = new LinkedHashMap<>();
        cases.forEach(c -> before.merge(c.reason() + "@" + c.tenantTag(), counter("api_authn_rejected_total", c.tenantTag(), c.reason().name()),
                (a, b) -> a));
        Map<String, List<String>> first = null;
        for (Case c : cases) {
            HttpResponse<byte[]> r = get(WHOAMI, c.request());
            assertThat(r.statusCode()).as(c.name()).isEqualTo(401);
            assertThat(r.body()).as(c.name()).isEqualTo(UNAUTHENTICATED);
            Map<String, List<String>> h = headers(r);
            assertThat(h).as(c.name()).containsEntry("www-authenticate", List.of("Bearer"))
                    .containsEntry("content-type", List.of("application/problem+json"))
                    .containsEntry("cache-control", List.of("private, no-cache"));
            if (first == null) {
                first = h;
            }
            assertThat(h).as("headers of '%s' = headers of the first 401", c.name()).isEqualTo(first);
        }
        Map<String, Long> expected = new LinkedHashMap<>();
        cases.forEach(c -> expected.merge(c.reason() + "@" + c.tenantTag(), 1L, Long::sum));
        expected.forEach((key, n) -> {
            String[] parts = key.split("@");
            assertThat(counter("api_authn_rejected_total", parts[1], parts[0]) - before.get(key)).as("metric %s", key).isEqualTo(n.doubleValue());
        });
    }

    @Test
    void everyNotFoundGetsTheSameBytesAndHeaders() {
        String a = ISSUER_1.token(SUB_A, AUD_1);
        Map<String, UnaryOperator<HttpRequest.Builder>> cases = new LinkedHashMap<>();
        Map<String, String> paths = new LinkedHashMap<>();
        cases.put("no identity_link", bearer(ISSUER_1.token(SUB_NONE, AUD_1)));
        paths.put("no identity_link", WHOAMI);
        cases.put("revoked link", bearer(ISSUER_1.token(SUB_R, AUD_1)));
        paths.put("revoked link", WHOAMI);
        cases.put("operator (no agent)", bearer(ISSUER_1.token(SUB_O, AUD_1)));
        paths.put("operator (no agent)", WHOAMI);
        cases.put("missing resource", bearer(a));
        paths.put("missing resource", "/api/v1/test/missing");
        cases.put("unrouted path", bearer(a));
        paths.put("unrouted path", "/api/v1/no-such-endpoint");
        cases.put("path variable format", bearer(a));
        paths.put("path variable format", "/api/v1/test/n/abc");
        Map<String, List<String>> first = null;
        for (Map.Entry<String, UnaryOperator<HttpRequest.Builder>> c : cases.entrySet()) {
            HttpResponse<byte[]> r = get(paths.get(c.getKey()), c.getValue());
            assertThat(r.statusCode()).as(c.getKey()).isEqualTo(404);
            assertThat(r.body()).as(c.getKey()).isEqualTo(NOT_FOUND);
            Map<String, List<String>> h = headers(r);
            assertThat(h).as(c.getKey()).doesNotContainKey("www-authenticate")
                    .containsEntry("content-type", List.of("application/problem+json"));
            if (first == null) {
                first = h;
            }
            assertThat(h).as("headers of '%s' = headers of the first 404", c.getKey()).isEqualTo(first);
        }
        HttpResponse<byte[]> post = send(bearer(a).apply(java.net.http.HttpRequest.newBuilder(
                java.net.URI.create("http://127.0.0.1:" + port + WHOAMI)).POST(HttpRequest.BodyPublishers.noBody())));
        assertThat(post.statusCode()).as("method mismatch").isEqualTo(404);
        assertThat(post.body()).isEqualTo(NOT_FOUND);
        assertThat(headers(post)).as("method mismatch headers").isEqualTo(first);
    }

    @Test
    void aCookieWithoutABearerHeaderOpensNothingOnTheInboxPaths() {
        String cookie = "SESSION=" + ISSUER_1.token(SUB_A, AUD_1) + "; portal=1";
        java.net.URI base = java.net.URI.create("http://127.0.0.1:" + port);
        String id = "00000000-0000-4000-8000-000000000601";
        List<HttpRequest.Builder> requests = List.of(
                HttpRequest.newBuilder(base.resolve("/api/v1/me/notifications")).header("Cookie", cookie).GET(),
                HttpRequest.newBuilder(base.resolve("/api/v1/me/notifications/" + id + "/read")).header("Cookie", cookie)
                        .method("PATCH", HttpRequest.BodyPublishers.noBody()),
                HttpRequest.newBuilder(base.resolve("/api/v1/me/inquiries")).header("Cookie", cookie).header("Content-Type", "application/json")
                        .header("Origin", "https://evil.example").POST(HttpRequest.BodyPublishers.ofString("{\"category\":\"OTHER\",\"message\":\"m\"}")),
                HttpRequest.newBuilder(base.resolve("/api/v1/me/inquiries/" + id)).header("Cookie", cookie).GET());
        Map<String, List<String>> first = null;
        for (HttpRequest.Builder b : requests) {
            HttpResponse<byte[]> r = send(b);
            assertThat(r.statusCode()).isEqualTo(401);
            assertThat(r.body()).isEqualTo(UNAUTHENTICATED);
            if (first == null) {
                first = headers(r);
            }
            assertThat(headers(r)).isEqualTo(first);
        }
    }

    @Test
    void aMethodMismatchOnAnInboxPathIsTheSame404() {
        String a = ISSUER_1.token(SUB_A, AUD_1);
        java.net.URI base = java.net.URI.create("http://127.0.0.1:" + port);
        String id = "00000000-0000-4000-8000-000000000601";
        HttpResponse<byte[]> reference = get("/api/v1/no-such-endpoint", bearer(a));
        List<HttpRequest.Builder> requests = List.of(
                bearer(a).apply(HttpRequest.newBuilder(base.resolve("/api/v1/me/notifications/" + id + "/read")).GET()),
                bearer(a).apply(HttpRequest.newBuilder(base.resolve("/api/v1/me/notifications/" + id + "/read")).POST(HttpRequest.BodyPublishers.noBody())),
                bearer(a).apply(HttpRequest.newBuilder(base.resolve("/api/v1/me/notifications")).DELETE()),
                bearer(a).apply(HttpRequest.newBuilder(base.resolve("/api/v1/me/inquiries/" + id)).method("PATCH", HttpRequest.BodyPublishers.noBody())),
                bearer(a).apply(HttpRequest.newBuilder(base.resolve("/api/v1/me/inquiries")).PUT(HttpRequest.BodyPublishers.noBody())));
        for (HttpRequest.Builder b : requests) {
            HttpResponse<byte[]> r = send(b);
            assertThat(r.statusCode()).isEqualTo(404);
            assertThat(r.body()).isEqualTo(NOT_FOUND);
            assertThat(headers(r)).isEqualTo(headers(reference));
        }
    }

    @Test
    void aQueryParameterFormatErrorIsTheOne400() {
        HttpResponse<byte[]> r = get("/api/v1/test/page?size=abc", ISSUER_1.token(SUB_A, AUD_1));
        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(r.body()).isEqualTo(INVALID_REQUEST);
        HttpResponse<byte[]> missing = get("/api/v1/test/page", ISSUER_1.token(SUB_A, AUD_1));
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(missing.body()).isEqualTo(INVALID_REQUEST);
        assertThat(get("/api/v1/test/page?size=7", ISSUER_1.token(SUB_A, AUD_1)).statusCode()).isEqualTo(200);
    }

    @Test
    void pathsOutsideTheApiAreTheSame404AndHealthStaysOpen() {
        HttpResponse<byte[]> r = get("/no-such-page", b -> b);
        assertThat(r.statusCode()).isEqualTo(404);
        assertThat(r.body()).isEqualTo(NOT_FOUND);
        HttpResponse<byte[]> actuator = get("/actuator/env", b -> b);
        assertThat(actuator.statusCode()).isEqualTo(404);
        assertThat(actuator.body()).isEqualTo(NOT_FOUND);
        HttpResponse<byte[]> health = get("/actuator/health", b -> b);
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(new String(health.body(), StandardCharsets.UTF_8)).contains("\"status\":\"UP\"");
    }
}
