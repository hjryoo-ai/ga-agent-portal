package com.ga.portal.app.web;

import com.ga.portal.sim.testing.StubSimulationEngine;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C7(포털 Phase 5 ⑥ 표): 엔진의 답 → 포털 응답·로그 행·지표. 어느 경우든 엔진 호출은 정확히 한 번(재시도 0) — 토큰이 없으면 0.
 * 다운(503)·지연(읽기 시간 초과 2초 < 지연 3초)·서비스 토큰 거부 → 503, 스키마 위반 200·엔진 404(S9 계약·S10 설계사) → 502, 엔진 422(S8·목록 밖) → 422.
 */
class EngineFailureIT extends SimIntegrationTest {

    record Expected(int status, byte[] body, String rowCode, Integer engineStatus, String metricReason) {
    }

    void expect(HttpResponse<byte[]> r, int callsBefore, int callsAfter, Expected e, String what) {
        assertThat(r.statusCode()).as(what).isEqualTo(e.status());
        assertThat(r.body()).as(what).isEqualTo(e.body());
        assertThat(callsAfter - callsBefore).as("%s: engine calls (no retry)", what).isEqualTo(e.rowCode().equals("TOKEN_MISSING") ? 0 : 1);
        SimRow row = rows(STD).getLast();
        assertThat(row.status()).as(what).isEqualTo("FAILED");
        assertThat(row.errorCode()).as(what).isEqualTo(e.rowCode());
        assertThat(row.engineStatus()).as(what).isEqualTo(e.engineStatus());
        assertThat(row.response()).as(what).isNull();
    }

    HttpResponse<byte[]> inMode(StubSimulationEngine.Mode mode, String path, String token, String body) {
        ENGINE_STD.mode(mode).slowMillis(3000);
        try {
            return post(path, token, body);
        } finally {
            ENGINE_STD.mode(StubSimulationEngine.Mode.UP);
        }
    }

    @Test
    void theTableOfEngineAnswers() throws Exception {
        String a = std(SUB_A1);
        double unavailable = counter("sim_requests_total", STD.value(), "ENGINE_UNAVAILABLE");
        double contract = counter("sim_requests_total", STD.value(), "ENGINE_CONTRACT");

        int c = ENGINE_STD.calls();
        expect(inMode(StubSimulationEngine.Mode.DOWN, NEW, a, S1), c, ENGINE_STD.calls(),
                new Expected(503, SimulationRejectionIT.ENGINE_UNAVAILABLE, "ENGINE_UNAVAILABLE", 503, "ENGINE_UNAVAILABLE"), "down");
        c = ENGINE_STD.calls();
        expect(inMode(StubSimulationEngine.Mode.SLOW, NEW, a, S1), c, ENGINE_STD.calls(),
                new Expected(503, SimulationRejectionIT.ENGINE_UNAVAILABLE, "ENGINE_TIMEOUT", null, "ENGINE_UNAVAILABLE"), "slow");
        c = ENGINE_STD.calls();
        expect(inMode(StubSimulationEngine.Mode.BROKEN, NEW, a, S1), c, ENGINE_STD.calls(),
                new Expected(502, SimulationRejectionIT.ENGINE_CONTRACT, "ENGINE_CONTRACT", 200, "ENGINE_CONTRACT"), "broken");
        c = ENGINE_STD.calls();
        expect(post(EVENT, a, event(STD, POLICY_UNKNOWN_TO_ENGINE, "CANCEL", "")), c, ENGINE_STD.calls(),
                new Expected(502, SimulationRejectionIT.ENGINE_CONTRACT, "POLICY_NOT_FOUND", 404, "ENGINE_CONTRACT"), "S9 policy unknown to the engine");
        c = ENGINE_STD.calls();
        expect(post(NEW, std(SUB_X), S1), c, ENGINE_STD.calls(),
                new Expected(502, SimulationRejectionIT.ENGINE_CONTRACT, "AGENT_NOT_FOUND", 404, "ENGINE_CONTRACT"), "S10 agent unknown to the engine");
        c = ENGINE_STD.calls();
        expect(post(EVENT, a, event(STD, POLICY, "PAYMENT_OVERDUE", "")), c, ENGINE_STD.calls(),
                new Expected(422, SimulationRejectionIT.UNSUPPORTED_EVENT_TYPE, "UNSUPPORTED_EVENT_TYPE", 422, "UNSUPPORTED_EVENT_TYPE"), "S8");
        c = ENGINE_STD.calls();
        expect(inMode(StubSimulationEngine.Mode.REJECT, NEW, a, S1), c, ENGINE_STD.calls(),
                new Expected(422, SimulationRejectionIT.ENGINE_REJECTED, "MONTH_CLOSED", 422, "ENGINE_REJECTED"), "422 outside the list");

        // 토큰 파일이 없으면 부르지 않는다
        Path file = ENGINE_TOKENS.resolve(STD.value());
        Path aside = ENGINE_TOKENS.resolve(STD.value() + ".aside");
        Files.move(file, aside, StandardCopyOption.ATOMIC_MOVE);
        try {
            c = ENGINE_STD.calls();
            HttpResponse<byte[]> r = post(NEW, a, S1);
            expect(r, c, ENGINE_STD.calls(), new Expected(503, SimulationRejectionIT.ENGINE_UNAVAILABLE, "TOKEN_MISSING", null, "ENGINE_UNAVAILABLE"),
                    "token missing");
        } finally {
            Files.move(aside, file, StandardCopyOption.ATOMIC_MOVE);
        }

        assertThat(counter("sim_requests_total", STD.value(), "ENGINE_UNAVAILABLE") - unavailable).isEqualTo(3.0);
        assertThat(counter("sim_requests_total", STD.value(), "ENGINE_CONTRACT") - contract).isEqualTo(3.0);
        // 엔진은 다시 정상 — 저하 모드는 그 요청에만
        assertThat(post(NEW, a, S1).statusCode()).isEqualTo(200);
    }

    @Test
    void anAmbiguousInsurerIs422AndTheInsurerCodeResolvesIt() {
        String a = ISSUER_AMB.token(SUB_A1, AUD_AMB);
        int c = ENGINE_AMB.calls();
        HttpResponse<byte[]> r = post(NEW, a, S1);
        assertThat(r.statusCode()).isEqualTo(422);
        assertThat(r.body()).isEqualTo(SimulationRejectionIT.INSURER_AMBIGUOUS);
        assertThat(ENGINE_AMB.calls() - c).isEqualTo(1);
        assertThat(rows(AMB).getLast()).extracting(SimRow::status, SimRow::errorCode, SimRow::engineStatus).containsExactly("FAILED", "INSURER_AMBIGUOUS", 422);
        assertThat(post(NEW, a, S1.replace("}", ",\"insurerCd\":\"SAMLIFE\"}")).statusCode()).isEqualTo(200);
    }
}
