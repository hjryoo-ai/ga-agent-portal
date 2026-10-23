package com.ga.portal.app.web;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.sim.testing.StubSimulationEngine;
import com.ga.portal.sim.testing.StubSimulationEngine.Sample;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C2(포털 Phase 5 ⓪·⑥): 엔진 샘플의 성공 여덟(S1~S7·S11b)마다 실제 앱에 포털 요청을 보내면, 응답 − 포털 사실 넷 = 엔진 응답(값 그대로)이고 정수 리터럴이 순서까지
 * 같다. 응답은 계약 1.1.0으로 검증되고({@link ApiContract}), 어느 응답에도 증권번호 원문이 없다(센티널 파일의 증권번호 전부 — {@link ResponseLeakScanIT}와
 * 같은 원천, 엔진 요청에는 있다). 응답 누출 검사의 시뮬레이션 몫이 여기 있다(계획 ⑨ — 시뮬레이션 테넌트·엔진 스텁이 이 기반에 있다).
 */
class SimulationPassThroughIT extends SimIntegrationTest {

    record Call(TenantId tenant, String path, String token, String body) {
    }

    Map<String, Call> calls() {
        String a = std(SUB_A1);
        Map<String, Call> m = new LinkedHashMap<>();
        m.put("new-limit-exceeded", new Call(STD, NEW, a, S1));
        m.put("new-curve", new Call(STD, NEW, a, S1.replace("2026-08-01", "2027-01-15")));
        m.put("new-no-limit", new Call(STD, NEW, a, S1.replace("2026-08-01", "2026-06-30")));
        m.put("new-term-3", new Call(STD, NEW, a, S1.replace("}", ",\"paymentTermMonths\":\"3\"}")));
        m.put("event-cancel-7", new Call(STD, EVENT, a, event(STD, POLICY, "CANCEL", ",\"elapsedInstallments\":\"7\"")));
        m.put("event-cancel-6", new Call(STD, EVENT, a, event(STD, POLICY, "CANCEL", ",\"elapsedInstallments\":\"6\"")));
        m.put("event-reduce", new Call(STD, EVENT, a, event(STD, POLICY, "REDUCE", ",\"elapsedInstallments\":\"7\",\"newMonthlyPremium\":\"100000\"")));
        m.put("new-ambiguous-samlife", new Call(AMB, NEW, ISSUER_AMB.token(SUB_A1, AUD_AMB), S1.replace("}", ",\"insurerCd\":\"SAMLIFE\"}")));
        return m;
    }

    @Test
    void everySuccessfulSampleComesBackWithTheEngineValuesUnchanged() {
        List<Sample> samples = StubSimulationEngine.load(SAMPLES);
        Map<String, Call> calls = calls();
        assertThat(calls.keySet()).containsExactlyInAnyOrderElementsOf(samples.stream().filter(s -> s.status() == 200).map(Sample::name).toList());
        for (Map.Entry<String, Call> e : calls.entrySet()) {
            Sample sample = samples.stream().filter(s -> s.name().equals(e.getKey())).findFirst().orElseThrow();
            Call c = e.getValue();
            HttpResponse<byte[]> r = post(c.path(), c.token(), c.body());
            assertThat(r.statusCode()).as(e.getKey()).isEqualTo(200);
            JsonNode engine = JSON.readTree(sample.response());
            assertThat(engineFields(r.body())).as(e.getKey()).isEqualTo(engine);
            String portalEngineText = JSON.writeValueAsString(engineFields(r.body()));
            assertThat(integers(portalEngineText)).as("integer literals of %s", e.getKey())
                    .isEqualTo(integers(new String(sample.response(), StandardCharsets.UTF_8))).isNotEmpty();
            String text = new String(r.body(), StandardCharsets.UTF_8);
            assertThat(text).as(e.getKey()).doesNotContain(POLICY).doesNotContain(POLICY_UNKNOWN_TO_ENGINE).doesNotContain(AGENT_A1);
            assertThat(ResponseLeakScanIT.seededPolicyNumbers()).as("sentinel policy numbers").isNotEmpty()
                    .allSatisfy(policyNo -> assertThat(text).as(e.getKey()).doesNotContain(policyNo));
            JsonNode portal = JSON.readTree(r.body());
            SimRow row = row(c.tenant(), portal.path("simId").asString());
            assertThat(row.status()).isEqualTo("SUCCEEDED");
            assertThat(portal.path("requestedAt").asString()).isEqualTo(row.createdAt().toString());
            assertThat(portal.path("dataStatus").asString()).isIn("OK", "SYNC_DELAYED");
        }
    }
}
