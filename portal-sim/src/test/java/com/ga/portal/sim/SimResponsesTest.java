package com.ga.portal.sim;

import com.ga.portal.domain.SimulationKind;
import com.ga.portal.sim.port.SimFacts.Facts;
import com.ga.portal.sim.testing.StubSimulationEngine;
import com.ga.portal.sim.testing.StubSimulationEngine.Sample;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C2(계획 P-25): 포털 응답 − 포털 사실 넷 = 엔진 응답(값 그대로), 정수 리터럴의 바이트가 같고, 포털 계약의 응답 스키마를 통과한다. 모르는 엔진 필드는 옮기지 않고
 * 센다(위·중첩 둘 다).
 */
class SimResponsesTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final List<Sample> SAMPLES = StubSimulationEngine.load(StubSimulationEngine.samplesDir(Path.of(System.getProperty("ga.repoRoot"))));
    static final Facts FACTS = new Facts(11, Optional.of(Instant.parse("2027-02-15T00:29:00Z")), "OK");
    static final UUID SIM = UUID.fromString("00000000-0000-4000-8000-000000000001");
    static final Instant AT = Instant.parse("2027-02-15T00:30:00.123456Z");
    static final Pattern INTEGER = Pattern.compile("-?[0-9]+");

    static List<String> integers(String json) {
        Matcher m = Pattern.compile("(?<=[:\\[,])-?[0-9]+(?=[,}\\]])").matcher(json);
        List<String> out = new java.util.ArrayList<>();
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    @Test
    void everySuccessfulSamplePassesThroughUnchanged() {
        for (Sample s : SAMPLES.stream().filter(x -> x.status() == 200).toList()) {
            SimulationKind kind = s.path().equals("new-contract") ? SimulationKind.NEW_CONTRACT : SimulationKind.POLICY_EVENT;
            JsonNode engine = JSON.readTree(s.response());
            SimResponses.Mapped mapped = SimResponses.portal(kind, FACTS, SIM, AT, engine);
            ObjectNode portal = (ObjectNode) JSON.readTree(mapped.body());
            assertThat(mapped.droppedFields()).as(s.name()).isZero();
            assertThat(portal.path("simId").asString()).isEqualTo(SIM.toString());
            assertThat(portal.path("requestedAt").asString()).isEqualTo("2027-02-15T00:30:00.123456Z");
            assertThat(portal.path("asOf").toString()).isEqualTo("{\"seq\":11,\"at\":\"2027-02-15T00:29:00Z\"}");
            ObjectNode withoutFacts = portal.deepCopy();
            withoutFacts.remove(List.of("asOf", "dataStatus", "simId", "requestedAt"));
            assertThat(withoutFacts).as(s.name()).isEqualTo(engine);
            // 엔진 본문의 정수 리터럴(순서대로)이 포털 본문의 엔진 부분에 그대로 — 0·음수·큰 수 포함
            String engineText = new String(s.response(), StandardCharsets.UTF_8);
            assertThat(integers(JSON.writeValueAsString(withoutFacts))).as(s.name()).isEqualTo(integers(engineText)).isNotEmpty();
            String schema = kind == SimulationKind.NEW_CONTRACT ? "SimNewContractResponse" : "SimPolicyEventResponse";
            assertThat(SimContracts.get().portalSchema(schema).validate(portal)).as(s.name()).isEmpty();
        }
    }

    @Test
    void unknownEngineFieldsAreDroppedAndCounted() {
        Sample s = SAMPLES.stream().filter(x -> x.name().equals("new-curve")).findFirst().orElseThrow();
        ObjectNode engine = (ObjectNode) JSON.readTree(s.response());
        engine.put("futureField", 1);
        ((ObjectNode) engine.path("deferral")).put("nested", "x");
        ((ObjectNode) engine.path("incentives").get(0)).put("extra", true);
        SimResponses.Mapped mapped = SimResponses.portal(SimulationKind.NEW_CONTRACT, FACTS, SIM, AT, engine);
        assertThat(mapped.droppedFields()).isEqualTo(3);
        JsonNode portal = JSON.readTree(mapped.body());
        assertThat(portal.has("futureField") || portal.path("deferral").has("nested") || portal.path("incentives").get(0).has("extra")).isFalse();
        assertThat(SimContracts.get().portalSchema("SimNewContractResponse").validate(portal)).isEmpty();
    }
}
