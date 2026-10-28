package com.ga.portal.app.web;

import com.ga.portal.app.screen.ScreenConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 화면 서빙(포털 Phase 4 계획 ⑧, C8·C12): 같은 출처의 화면 경로·자원, 헤더 집합 전수, 화면 경로 목록 ↔ {@code web/src/routes.ts} 양방향,
 * 경로 밖·다른 메서드는 나머지 체인의 같은 404, API 응답 헤더는 Phase 3 그대로(CSP 없음). 포털 Phase 8(C8, 돌이킬 수 없는 결정 4): 요청 출처에 로그인
 * 설정이 등록된 테넌트가 있으면 {@code connect-src}만 {@code 'self' <발급자 출처>}이고 나머지 아홉 지시어는 같다. 등록 없는 출처는 지금 열 지시어 그대로.
 * {@code /auth/callback}(PKCE 팝업 복귀)은 화면 경로다.
 */
class WebScreensIT extends ApiIntegrationTest {

    static final Map<String, String> CSP = Map.of(
            "default-src", "'self'", "script-src", "'self'", "style-src", "'self'", "img-src", "'self'", "font-src", "'self'",
            "connect-src", "'self'", "object-src", "'none'", "base-uri", "'none'", "form-action", "'none'", "frame-ancestors", "'none'");

    static Map<String, String> directives(String policy) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String d : policy.split(";")) {
            String[] parts = d.trim().split("\\s+", 2);
            out.put(parts[0], parts.length > 1 ? parts[1] : "");
        }
        return out;
    }

    static byte[] indexHtml() throws IOException {
        try (InputStream in = WebScreensIT.class.getClassLoader().getResourceAsStream("portal-web/index.html")) {
            assertThat(in).as("web jar on the runtime classpath").isNotNull();
            return in.readAllBytes();
        }
    }

    static void assertScreenHeaders(HttpResponse<byte[]> r) {
        assertThat(directives(r.headers().firstValue("Content-Security-Policy").orElseThrow())).isEqualTo(CSP);
        assertThat(r.headers().allValues("Referrer-Policy")).containsExactly("no-referrer");
        assertThat(r.headers().allValues("X-Content-Type-Options")).containsExactly("nosniff");
        assertThat(r.headers().allValues("X-Frame-Options")).containsExactly("DENY");
        assertThat(r.headers().allValues("Set-Cookie")).isEmpty();
    }

    @Test
    void everyScreenPathServesTheSameDocumentWithTheScreenHeaders() throws IOException {
        byte[] index = indexHtml();
        for (String path : List.of("/", "/statements", "/statements/202608", "/lines/1", "/limits", "/limits/1", "/auth/callback",
                "/auth/callback?code=c0de&state=5tate")) {
            HttpResponse<byte[]> r = fetch(request(path).GET());
            assertThat(r.statusCode()).as(path).isEqualTo(200);
            assertThat(r.body()).as(path).isEqualTo(index);
            assertThat(r.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/html");
            assertThat(r.headers().allValues("Cache-Control")).as(path).containsExactly("no-store");
            assertScreenHeaders(r);
        }
    }

    @Test
    void aRegisteredOriginAddsOnlyItsIssuerOriginToConnectSrc() throws IOException {
        Map<String, String> expected = new LinkedHashMap<>(CSP);
        expected.put("connect-src", "'self' " + originOf(ISSUER_1.issuer()));
        String html = new String(indexHtml(), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("src=\"(/assets/[^\"]+\\.js)\"").matcher(html);
        assertThat(m.find()).isTrue();
        for (String path : List.of("/", "/auth/callback", m.group(1))) {
            HttpResponse<byte[]> registered = fetch(request(path).header("Origin", WEB_1).GET());
            assertThat(registered.statusCode()).as(path).isEqualTo(200);
            assertThat(directives(registered.headers().firstValue("Content-Security-Policy").orElseThrow())).as(path).isEqualTo(expected);
            assertThat(registered.headers().allValues("Content-Security-Policy")).as(path).hasSize(1);
            HttpResponse<byte[]> stranger = fetch(request(path).header("Origin", WEB_STRANGER).GET());
            assertScreenHeaders(stranger);
        }
        HttpResponse<byte[]> two = fetch(request("/").header("Origin", WEB_2).GET());
        assertThat(directives(two.headers().firstValue("Content-Security-Policy").orElseThrow()).get("connect-src"))
                .isEqualTo("'self' " + originOf(ISSUER_2.issuer()));
    }

    /** 시험 발급기 URL(포트가 늘 있다)의 출처. */
    static String originOf(String url) {
        java.net.URI u = java.net.URI.create(url);
        return u.getScheme() + "://" + u.getHost() + ":" + u.getPort();
    }

    @Test
    void hashedAssetsAreImmutableAndCarryTheSameHeaders() throws IOException {
        String html = new String(indexHtml(), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("src=\"(/assets/[^\"]+\\.js)\"").matcher(html);
        assertThat(m.find()).as("entry script in index.html").isTrue();
        HttpResponse<byte[]> r = fetch(request(m.group(1)).GET());
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.headers().firstValue("Content-Type").orElseThrow()).contains("javascript");
        assertThat(r.headers().allValues("Cache-Control")).containsExactly("max-age=31536000, public, immutable");
        assertScreenHeaders(r);

        assertThat(fetch(request("/assets/missing-0000.js").GET()).statusCode()).isEqualTo(404);
        assertThat(fetch(request("/assets/%2e%2e/index.html").GET()).statusCode()).isEqualTo(404);
    }

    @Test
    void anythingElseIsTheUnroutedNotFound() {
        HttpResponse<byte[]> unrouted = fetch(request("/no-such-screen").GET());
        assertThat(unrouted.statusCode()).isEqualTo(404);
        for (HttpResponse<byte[]> r : List.of(
                fetch(request("/statements/2026/08").GET()),
                fetch(request("/index.html").GET()),
                fetch(request("/").POST(HttpRequest.BodyPublishers.noBody())),
                fetch(request("/limits").method("DELETE", HttpRequest.BodyPublishers.noBody())))) {
            assertThat(r.statusCode()).as(r.request().method() + " " + r.uri().getPath()).isEqualTo(404);
            assertThat(r.body()).isEqualTo(unrouted.body());
            assertThat(r.headers().firstValue("Content-Security-Policy")).isEmpty();
        }
    }

    @Test
    void apiResponsesKeepThePhase3HeadersWithoutTheScreenPolicy() {
        HttpResponse<byte[]> r = fetch(request("/api/v1/me").GET());
        assertThat(r.statusCode()).isEqualTo(401);
        assertThat(r.headers().firstValue("Content-Security-Policy")).isEmpty();
        assertThat(r.headers().allValues("Referrer-Policy")).isEmpty();
        assertThat(r.headers().allValues("Cache-Control")).containsExactly("private, no-cache");
    }

    @Test
    void screenPathsEqualTheWebRoutesBothWays() throws IOException {
        String routes = Files.readString(Path.of(System.getProperty("ga.repoRoot")).resolve("web/src/routes.ts"));
        Matcher m = Pattern.compile("ROUTE_PATHS = \\[([^\\]]*)]").matcher(routes);
        assertThat(m.find()).as("ROUTE_PATHS in web/src/routes.ts").isTrue();
        List<String> web = Arrays.stream(m.group(1).split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.replaceAll("^'|'$", ""))
                .map(s -> s.replaceAll(":[A-Za-z]+", "*"))
                .toList();
        assertThat(web).hasSizeGreaterThan(3);
        assertThat(ScreenConfiguration.SCREEN_PATHS).containsExactlyInAnyOrderElementsOf(web);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + path));
    }

    /** 화면 응답은 API 계약 밖이다 — 공통 send()의 계약 검사·응답 수 집계(ApiContract)를 거치지 않는 자기 클라이언트. */
    private static HttpResponse<byte[]> fetch(HttpRequest.Builder request) {
        try {
            return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static final java.net.http.HttpClient CLIENT = java.net.http.HttpClient.newHttpClient();
}
