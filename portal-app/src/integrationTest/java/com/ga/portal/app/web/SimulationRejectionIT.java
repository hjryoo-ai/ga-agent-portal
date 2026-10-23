package com.ga.portal.app.web;

import com.ga.portal.sim.testing.StubSimulationEngine;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C8(포털 Phase 5 계획 Q19 — {@code UniformRejectionIT}의 시뮬레이션 몫): 시뮬레이션 경로의 거부 본문은 열 가지 고정 바이트(401·404·400·500 + 422 셋·429·502·
 * 503)이고, 같은 코드면 사유·엔진 메시지와 무관하게 같은 바이트·같은 머리다(429의 {@code Retry-After} 값만 요청마다 다를 수 있다). 엔진 {@code message}(샘플:
 * 엔진에 없는 설계사의 ID, 그 설계사의 계약이 아닌 증권번호)는 어느 응답에도 없다. 표(코드·상태·길이·SHA-256 앞 12자)를 출력한다(보고서).
 */
class SimulationRejectionIT extends SimIntegrationTest {

    static byte[] body(String code, String message) {
        return ("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    static final byte[] INSURER_AMBIGUOUS = body("INSURER_AMBIGUOUS", "insurer must be chosen");
    static final byte[] UNSUPPORTED_EVENT_TYPE = body("UNSUPPORTED_EVENT_TYPE", "event type not simulated");
    static final byte[] ENGINE_REJECTED = body("ENGINE_REJECTED", "rejected by the engine");
    static final byte[] RATE_LIMITED = body("RATE_LIMITED", "too many simulations");
    static final byte[] ENGINE_CONTRACT = body("ENGINE_CONTRACT", "engine answer not usable");
    static final byte[] ENGINE_UNAVAILABLE = body("ENGINE_UNAVAILABLE", "engine unavailable");
    static final byte[] INTERNAL_ERROR = body("INTERNAL_ERROR", "internal error");

    static String sha12(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)).substring(0, 12);
    }

    @Test
    void tenFixedBodiesAndNoEngineMessage() throws Exception {
        Map<String, HttpResponse<byte[]>> seen = new LinkedHashMap<>();
        String a = std(SUB_A1);
        seen.put("401", send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + NEW)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(S1))));
        seen.put("404", post(NEW, std(SUB_MGR5), S1));
        seen.put("400", post(NEW, a, "{\"productKey\":\"WHOLE-LIFE-20Y\"}"));
        seen.put("422 INSURER_AMBIGUOUS", post(NEW, ISSUER_AMB.token(SUB_A1, AUD_AMB), S1));
        seen.put("422 UNSUPPORTED_EVENT_TYPE", post(EVENT, a, event(STD, POLICY, "PAYMENT_OVERDUE", "")));
        ENGINE_STD.mode(StubSimulationEngine.Mode.REJECT);
        try {
            seen.put("422 ENGINE_REJECTED", post(NEW, a, S1));
        } finally {
            ENGINE_STD.mode(StubSimulationEngine.Mode.UP);
        }
        seen.put("502 (engine 404 with a message)", post(NEW, std(SUB_X), S1));
        ENGINE_STD.mode(StubSimulationEngine.Mode.DOWN);
        try {
            seen.put("503", post(NEW, a, S1));
        } finally {
            ENGINE_STD.mode(StubSimulationEngine.Mode.UP);
        }
        String rl = ISSUER_RL.token(SUB_REJECT, AUD_RL);
        HttpResponse<byte[]> limited = null;
        for (int i = 0; i < 10 && limited == null; i++) {
            HttpResponse<byte[]> r = post(NEW, rl, S1);
            if (r.statusCode() == 429) {
                limited = r;
            }
        }
        seen.put("429", limited);

        Map<String, byte[]> expected = new LinkedHashMap<>();
        expected.put("401", UniformRejectionIT.UNAUTHENTICATED);
        expected.put("404", UniformRejectionIT.NOT_FOUND);
        expected.put("400", UniformRejectionIT.INVALID_REQUEST);
        expected.put("422 INSURER_AMBIGUOUS", INSURER_AMBIGUOUS);
        expected.put("422 UNSUPPORTED_EVENT_TYPE", UNSUPPORTED_EVENT_TYPE);
        expected.put("422 ENGINE_REJECTED", ENGINE_REJECTED);
        expected.put("502 (engine 404 with a message)", ENGINE_CONTRACT);
        expected.put("503", ENGINE_UNAVAILABLE);
        expected.put("429", RATE_LIMITED);
        for (Map.Entry<String, byte[]> e : expected.entrySet()) {
            HttpResponse<byte[]> r = seen.get(e.getKey());
            assertThat(r).as(e.getKey()).isNotNull();
            assertThat(r.body()).as(e.getKey()).isEqualTo(e.getValue());
            assertThat(new String(r.body(), StandardCharsets.UTF_8)).as(e.getKey()).doesNotContain(AGENT_X).doesNotContain(POLICY)
                    .doesNotContain(POLICY_UNKNOWN_TO_ENGINE).doesNotContain("엔진");
        }
        // 열 가지 본문(500 포함 — 시험 진입점이 아닌 기존 바이트) — 모두 다르다
        List<byte[]> all = List.of(UniformRejectionIT.UNAUTHENTICATED, UniformRejectionIT.NOT_FOUND, UniformRejectionIT.INVALID_REQUEST, INTERNAL_ERROR,
                INSURER_AMBIGUOUS, UNSUPPORTED_EVENT_TYPE, ENGINE_REJECTED, RATE_LIMITED, ENGINE_CONTRACT, ENGINE_UNAVAILABLE);
        assertThat(all.stream().map(b -> new String(b, StandardCharsets.UTF_8)).distinct()).hasSize(10);
        StringBuilder table = new StringBuilder("REJECTION_BYTES");
        for (byte[] b : all) {
            String code = new String(b, StandardCharsets.UTF_8).replaceAll("^\\{\"code\":\"([A-Z_]+)\".*$", "$1");
            table.append(' ').append(code).append('=').append(b.length).append('/').append(sha12(b));
        }
        System.out.println(table);
        // 같은 코드면 같은 머리(429의 Retry-After 값 빼고)
        assertThat(headers(seen.get("503")).keySet()).isEqualTo(headers(seen.get("502 (engine 404 with a message)")).keySet());
        assertThat(seen.get("429").headers().firstValue("retry-after")).hasValueSatisfying(v -> assertThat(v).matches("[1-9][0-9]?"));
    }
}
