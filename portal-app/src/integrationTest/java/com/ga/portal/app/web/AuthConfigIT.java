package com.ga.portal.app.web;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포털 Phase 8 계획 Q2 조건 A: {@code GET /auth/config}는 요청 출처({@code Origin}, 없으면 스킴 + {@code Host})와 {@code tenant.web_origin}의 정확 일치로만
 * 테넌트를 고르고, 두 필드만 싣는다. 등록된 출처 둘은 각자 자기 것만, 셋째 출처·비슷한 출처는 나머지 체인의 404와 같은 바이트·같은 머리(열거 불가).
 * 인증 머리는 보지 않는다. 응답은 계약 {@code AuthConfig}(추가 속성 금지)로 검증된다({@link ApiContract}).
 */
class AuthConfigIT extends ApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    HttpResponse<byte[]> config(String origin) {
        return send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + AuthConfigHandler.PATH)).header("Origin", origin).GET());
    }

    @Test
    void eachOriginSeesOnlyItsOwnIssuer() {
        HttpResponse<byte[]> one = config(WEB_1);
        HttpResponse<byte[]> two = config(WEB_2);
        assertThat(one.statusCode()).isEqualTo(200);
        assertThat(two.statusCode()).isEqualTo(200);
        JsonNode b1 = JSON.readTree(one.body());
        JsonNode b2 = JSON.readTree(two.body());
        assertThat(b1.propertyNames()).containsExactlyInAnyOrder("issuer", "clientId");
        assertThat(b1.path("issuer").asString()).isEqualTo(ISSUER_1.issuer());
        assertThat(b1.path("clientId").asString()).isEqualTo(CLIENT_1);
        assertThat(b2.path("issuer").asString()).isEqualTo(ISSUER_2.issuer());
        assertThat(b2.path("clientId").asString()).isEqualTo(CLIENT_2);
        assertThat(new String(one.body(), StandardCharsets.UTF_8)).doesNotContain(ISSUER_2.issuer(), CLIENT_2, AUD_1, T1.value());

        HttpResponse<byte[]> unrouted = get("/no-such-path", b -> b);
        assertThat(unrouted.statusCode()).isEqualTo(404);
        for (String other : List.of(WEB_STRANGER, WEB_1 + "/", WEB_1.toUpperCase(java.util.Locale.ROOT), WEB_1.replace(":8080", ""),
                WEB_1.replace("http://", "https://"), "null")) {
            HttpResponse<byte[]> r = config(other);
            assertThat(r.statusCode()).as("origin '%s'", other).isEqualTo(404);
            assertThat(r.body()).as("origin '%s'", other).isEqualTo(unrouted.body());
            assertThat(headers(r)).as("origin '%s' headers = the unrouted 404", other).isEqualTo(headers(unrouted));
        }
    }

    @Test
    void withoutAnOriginTheHostHeaderDecides() {
        // java.net.http는 Host를 바꿀 수 없다(제한 머리) — 같은 출처 GET처럼 Origin 없이 Host만 싣는 원시 요청
        Raw one = raw(WEB_1.substring("http://".length()));
        assertThat(one.status()).isEqualTo(200);
        assertThat(JSON.readTree(one.body()).path("clientId").asString()).isEqualTo(CLIENT_1);
        assertThat(one.headers()).containsEntry("cache-control", "no-cache");
        Raw stranger = raw(WEB_STRANGER.substring("http://".length()));
        assertThat(stranger.status()).isEqualTo(404);
        assertThat(stranger.body()).isEqualTo(new String(UniformRejectionIT.NOT_FOUND, StandardCharsets.UTF_8));
        assertThat(raw("127.0.0.1:" + port).status()).as("the test client's own host is not registered").isEqualTo(404);
    }

    @Test
    void anAuthorizationHeaderIsIgnoredAndOtherMethodsAreTheUnrouted404() {
        HttpResponse<byte[]> withToken = send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + AuthConfigHandler.PATH))
                .header("Origin", WEB_STRANGER).header("Authorization", "Bearer " + ISSUER_1.token(SUB_A, AUD_1)).GET());
        HttpResponse<byte[]> unrouted = get("/no-such-path", b -> b);
        assertThat(withToken.statusCode()).isEqualTo(404);
        assertThat(withToken.body()).isEqualTo(unrouted.body());
        HttpResponse<byte[]> post = send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + AuthConfigHandler.PATH))
                .header("Origin", WEB_1).POST(HttpRequest.BodyPublishers.noBody()));
        assertThat(post.statusCode()).isEqualTo(404);
        assertThat(post.body()).isEqualTo(unrouted.body());
        assertThat(headers(post)).isEqualTo(headers(unrouted));
    }

    record Raw(int status, Map<String, String> headers, String body) {
    }

    /** 덩어리 전송 본문을 잇는다(ASCII 본문만 — 이 시험의 응답). */
    static String dechunk(String chunked) {
        StringBuilder out = new StringBuilder();
        int at = 0;
        while (true) {
            int eol = chunked.indexOf("\r\n", at);
            int size = Integer.parseInt(chunked.substring(at, eol).trim(), 16);
            if (size == 0) {
                return out.toString();
            }
            out.append(chunked, eol + 2, eol + 2 + size);
            at = eol + 2 + size + 2;
        }
    }

    /** HTTP/1.1 원시 GET — Host 머리 하나, Origin 없음, 연결 닫힘. */
    Raw raw(String host) {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + AuthConfigHandler.PATH + " HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            String response = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int split = response.indexOf("\r\n\r\n");
            String[] head = response.substring(0, split).split("\r\n");
            Map<String, String> headers = new java.util.TreeMap<>();
            for (int i = 1; i < head.length; i++) {
                int colon = head[i].indexOf(':');
                headers.put(head[i].substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT), head[i].substring(colon + 1).trim());
            }
            String body = response.substring(split + 4);
            return new Raw(Integer.parseInt(head[0].split(" ")[1]), headers, "chunked".equals(headers.get("transfer-encoding")) ? dechunk(body) : body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
