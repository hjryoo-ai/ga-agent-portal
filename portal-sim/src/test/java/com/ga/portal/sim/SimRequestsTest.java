package com.ga.portal.sim;

import com.ga.platform.core.tenant.AgentId;
import com.ga.portal.domain.access.InvalidRequest;
import com.ga.portal.sim.testing.StubSimulationEngine;
import com.ga.portal.sim.testing.StubSimulationEngine.Sample;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 계획 ②·Q12·P-21: 포털 요청의 형식·계약 스키마 검사(400 한 가지)와 엔진 요청 만들기. 엔진 요청은 엔진 샘플 사본의 요청과 JCS 정규형이 같다 — 포털이 실제로
 * 보내는 바이트가 엔진이 시험한 요청이다(계획 Q17).
 */
class SimRequestsTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final Optional<String> JSON_TYPE = Optional.of("application/json");
    static final AgentId A = AgentId.of("A-1001");
    static final List<Sample> SAMPLES = StubSimulationEngine.load(StubSimulationEngine.samplesDir(Path.of(System.getProperty("ga.repoRoot"))));

    static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static Sample sample(String name) {
        return SAMPLES.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    static JsonNode newContract(String body) {
        return SimRequests.read(JSON_TYPE, bytes(body), SimRequests.NEW_CONTRACT);
    }

    static JsonNode policyEvent(String body) {
        return SimRequests.read(JSON_TYPE, bytes(body), SimRequests.POLICY_EVENT);
    }

    @Test
    void theEngineRequestsAreTheEngineSamples() {
        StubSimulationEngine standard = StubSimulationEngine.of(StubSimulationEngine.samplesDir(Path.of(System.getProperty("ga.repoRoot"))), "standard");
        Map<String, String> newContracts = Map.of(
                "new-limit-exceeded", "{\"productKey\":\"WHOLE-LIFE-20Y\",\"monthlyPremium\":\"150000\",\"contractDate\":\"2026-08-01\"}",
                "new-curve", "{\"productKey\":\"WHOLE-LIFE-20Y\",\"monthlyPremium\":\"150000\",\"contractDate\":\"2027-01-15\"}",
                "new-no-limit", "{\"productKey\":\"WHOLE-LIFE-20Y\",\"monthlyPremium\":\"150000\",\"contractDate\":\"2026-06-30\"}",
                "new-term-3", "{\"contractDate\":\"2026-08-01\",\"paymentTermMonths\":\"3\",\"monthlyPremium\":\"150000\",\"productKey\":\"WHOLE-LIFE-20Y\"}");
        newContracts.forEach((name, body) -> assertThat(standard.match("new-contract",
                bytes(JSON.writeValueAsString(SimRequests.newContract(A, newContract(body)))))).as(name).map(Sample::name).contains(name));
        Map<String, String> events = Map.of(
                "event-cancel-7", "{\"policyKey\":\"0123456789abcdef\",\"eventType\":\"CANCEL\",\"asOfDate\":\"2027-02-15\",\"elapsedInstallments\":\"7\"}",
                "event-cancel-6", "{\"policyKey\":\"0123456789abcdef\",\"eventType\":\"CANCEL\",\"asOfDate\":\"2027-02-15\",\"elapsedInstallments\":\"6\"}",
                "event-reduce", "{\"policyKey\":\"0123456789abcdef\",\"eventType\":\"REDUCE\",\"asOfDate\":\"2027-02-15\",\"elapsedInstallments\":\"7\","
                        + "\"newMonthlyPremium\":\"100000\"}",
                "event-unsupported", "{\"policyKey\":\"0123456789abcdef\",\"eventType\":\"PAYMENT_OVERDUE\",\"asOfDate\":\"2027-02-15\"}");
        events.forEach((name, body) -> assertThat(standard.match("policy-event",
                bytes(JSON.writeValueAsString(SimRequests.policyEvent(A, "POL-2026-0001", policyEvent(body)))))).as(name).map(Sample::name)
                .contains(name));
        // 보험사 코드를 준 요청(422 뒤) — 보험사 둘 엔진의 S11b
        StubSimulationEngine ambiguous = StubSimulationEngine.of(StubSimulationEngine.samplesDir(Path.of(System.getProperty("ga.repoRoot"))), "ambiguous");
        assertThat(ambiguous.match("new-contract", bytes(JSON.writeValueAsString(SimRequests.newContract(A, newContract(
                "{\"productKey\":\"WHOLE-LIFE-20Y\",\"monthlyPremium\":\"150000\",\"contractDate\":\"2026-08-01\",\"insurerCd\":\"SAMLIFE\"}"))))))
                .map(Sample::name).contains("new-ambiguous-samlife");
        // 포털은 시책 코드를 보내지 않는다(Q4) — 엔진 요청에 incentiveCodes가 없다
        assertThat(SAMPLES).allSatisfy(s -> assertThat(new String(s.request(), StandardCharsets.UTF_8)).doesNotContain("incentiveCodes"));
    }

    @Test
    void formatErrorsAreTheSingle400() {
        String ok = "{\"productKey\":\"WHOLE-LIFE-20Y\",\"monthlyPremium\":\"150000\",\"contractDate\":\"2026-08-01\"}";
        assertThat(newContract(ok).path("monthlyPremium").asString()).isEqualTo("150000");
        List<Runnable> bad = List.of(
                () -> SimRequests.read(Optional.empty(), bytes(ok), SimRequests.NEW_CONTRACT),
                () -> SimRequests.read(Optional.of("text/plain"), bytes(ok), SimRequests.NEW_CONTRACT),
                () -> SimRequests.read(Optional.of("application/json; charset=latin1"), bytes(ok), SimRequests.NEW_CONTRACT),
                () -> SimRequests.read(JSON_TYPE, new byte[0], SimRequests.NEW_CONTRACT),
                () -> SimRequests.read(JSON_TYPE, bytes(ok.replace("}", ",\"x\":\"" + "y".repeat(2100) + "\"}")), SimRequests.NEW_CONTRACT),
                () -> newContract("not json"),
                () -> newContract("[]"),
                () -> newContract(ok.replace("}", ",\"productKey\":\"OTHER\"}")),                       // 중복 키
                () -> newContract(ok.replace("}", ",\"incentiveCodes\":[\"Q4-NEW\"]}")),                  // 받지 않는 필드
                () -> newContract(ok.replace("}", ",\"agentId\":\"A-9999\"}")),                           // 설계사는 본문에서 오지 않는다
                () -> newContract(ok.replace("\"150000\"", "150000")),                                   // 숫자가 아니라 숫자 문자열
                () -> newContract(ok.replace("\"150000\"", "\"0150000\"")),
                () -> newContract(ok.replace("\"150000\"", "\"1500.5\"")),
                () -> newContract(ok.replace("\"150000\"", "\"-1\"")),
                () -> newContract(ok.replace("\"150000\"", "\"1234567890123\"")),                       // 12자리 상한
                () -> SimRequests.newContract(A, newContract(ok.replace("2026-08-01", "2026-02-30"))),   // 형식은 맞고 날은 없다
                () -> newContract(ok.replace("2026-08-01", "2026-8-1")),
                () -> policyEvent("{\"policyKey\":\"0123456789abcdef\",\"eventType\":\"REDUCE\",\"asOfDate\":\"2027-02-15\"}"),   // REDUCE에 새 월납 없음
                () -> policyEvent("{\"policyKey\":\"POL-2026-0001\",\"eventType\":\"CANCEL\",\"asOfDate\":\"2027-02-15\"}"),       // 키가 아니다
                () -> policyEvent("{\"policyKey\":\"0123456789abcdef\",\"eventType\":\"EXPLODE\",\"asOfDate\":\"2027-02-15\"}"),
                () -> policyEvent("{\"policyKey\":\"0123456789abcdef\",\"eventType\":\"CANCEL\",\"asOfDate\":\"2027-02-15\",\"elapsedInstallments\":\"0\"}"));
        for (int i = 0; i < bad.size(); i++) {
            assertThatThrownBy(bad.get(i)::run).as("case %d", i).isInstanceOf(InvalidRequest.class);
        }
        assertThat(SimRequests.isJson(Optional.of("Application/JSON; Charset=UTF-8"))).isTrue();
    }
}
