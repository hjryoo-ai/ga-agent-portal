package com.ga.portal.sim;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C1·C2(포털 Phase 5 ②): 포털 계약 1.1.0의 시뮬레이션 스키마가 엔진 계약 사본(1.4.0)과 짝이다.
 * <ul>
 *   <li>응답: 포털 = 엔진 결과의 속성 + 포털 사실 넷({@code asOf}·{@code dataStatus}·{@code simId}·{@code requestedAt}), 필수 목록도 같은 관계 — 중첩 객체·객체
 *       배열까지 속성·필수 집합이 같고 잎의 타입·하한이 같다. 포털만 {@code additionalProperties: false}(모르는 엔진 필드는 옮기지 않는다 — P-25).</li>
 *   <li>요청: 포털 = 엔진 요청 − 설계사({@code agentId} — 호출자에서) − 시책 코드(Q4), 계약 이벤트는 {@code policyNo} 대신 {@code policyKey}. {@code eventType}
 *       값 집합이 같다(화면 선택지는 다섯 — P-18).</li>
 * </ul>
 */
class SimulationSchemaParityTest {

    static final Set<String> PORTAL_FACTS = Set.of("asOf", "dataStatus", "simId", "requestedAt");
    static JsonNode portal;
    static JsonNode engine;
    static JsonNode defs;

    @BeforeAll
    static void read() {
        YAMLMapper yaml = YAMLMapper.builder().build();
        portal = yaml.readTree(SimContracts.read("api/v1/portal-api.openapi.yaml"));
        engine = yaml.readTree(SimContracts.read("api/v1/engine-internal.openapi.yaml"));
        defs = JsonMapper.builder().build().readTree(SimContracts.read("events/v1/defs.schema.json"));
    }

    static JsonNode portalSchema(String name) {
        return portal.path("components").path("schemas").path(name);
    }

    static JsonNode engineSchema(String name) {
        return engine.path("components").path("schemas").path(name);
    }

    /** 참조를 푼다 — 포털은 {@code #/components/schemas/}, 엔진은 {@code ../../events/v1/defs.schema.json#/$defs/}. */
    static JsonNode deref(JsonNode node) {
        JsonNode cur = node;
        while (cur.has("$ref")) {
            String ref = cur.path("$ref").asString();
            if (ref.startsWith("#/components/schemas/")) {
                cur = portalSchema(ref.substring("#/components/schemas/".length()));
            } else if (ref.startsWith("../../events/v1/defs.schema.json#/$defs/")) {
                cur = defs.path("$defs").path(ref.substring(ref.lastIndexOf('/') + 1));
            } else {
                throw new AssertionError("unexpected ref " + ref);
            }
        }
        return cur;
    }

    static Set<String> names(JsonNode node, String field) {
        Set<String> out = new TreeSet<>();
        if (field.equals("properties")) {
            node.path("properties").propertyNames().forEach(out::add);
        } else {
            node.path(field).forEach(v -> out.add(v.asString()));
        }
        return out;
    }

    /** 같은 모양(속성·필수 집합, 잎 타입·하한) — 차이를 모은다. */
    static void same(String at, JsonNode p, JsonNode e, List<String> problems) {
        JsonNode portalNode = deref(p);
        JsonNode engineNode = deref(e);
        String type = engineNode.path("type").asString(engineNode.has("enum") ? "enum" : "");
        if (engineNode.has("properties")) {
            if (!names(portalNode, "properties").equals(names(engineNode, "properties"))) {
                problems.add(at + " properties " + names(portalNode, "properties") + " vs engine " + names(engineNode, "properties"));
            }
            if (!names(portalNode, "required").equals(names(engineNode, "required"))) {
                problems.add(at + " required " + names(portalNode, "required") + " vs engine " + names(engineNode, "required"));
            }
            if (!(portalNode.path("additionalProperties").isBoolean() && !portalNode.path("additionalProperties").asBoolean())) {
                problems.add(at + " is not closed in the portal contract");
            }
            engineNode.path("properties").properties().forEach(en -> same(at + "." + en.getKey(), portalNode.path("properties").path(en.getKey()),
                    en.getValue(), problems));
            return;
        }
        if (type.equals("array")) {
            same(at + "[]", portalNode.path("items"), engineNode.path("items"), problems);
            return;
        }
        if (!portalNode.path("type").asString().equals(engineNode.path("type").asString())
                || !portalNode.path("minimum").toString().equals(engineNode.path("minimum").toString())) {
            problems.add(at + " leaf " + portalNode.path("type") + "/" + portalNode.path("minimum") + " vs engine " + engineNode.path("type") + "/"
                    + engineNode.path("minimum"));
        }
    }

    static void responseParity(String portalName, String engineName) {
        JsonNode p = portalSchema(portalName);
        JsonNode e = engineSchema(engineName);
        Set<String> expectedProperties = new TreeSet<>(names(e, "properties"));
        expectedProperties.addAll(PORTAL_FACTS);
        Set<String> expectedRequired = new TreeSet<>(names(e, "required"));
        expectedRequired.addAll(PORTAL_FACTS);
        assertThat(names(p, "properties")).as(portalName).isEqualTo(expectedProperties);
        assertThat(names(p, "required")).as(portalName).isEqualTo(expectedRequired);
        List<String> problems = new ArrayList<>();
        e.path("properties").properties().forEach(en -> same(portalName + "." + en.getKey(), p.path("properties").path(en.getKey()), en.getValue(),
                problems));
        assertThat(problems).isEmpty();
    }

    @Test
    void newContractResponseIsTheEngineResultPlusFourPortalFacts() {
        responseParity("SimNewContractResponse", "NewContractResult");
    }

    @Test
    void policyEventResponseIsTheEngineResultPlusFourPortalFacts() {
        responseParity("SimPolicyEventResponse", "PolicyEventResult");
        assertThat(names(engineSchema("PolicyEventResult"), "properties")).as("the engine has no generatedAt here (P-2)").doesNotContain("generatedAt");
    }

    @Test
    void requestsAreTheEngineRequestsWithoutTheCallerAndTheIncentiveCodes() {
        Set<String> engineNew = new TreeSet<>(names(engineSchema("NewContractRequest"), "properties"));
        engineNew.removeAll(Set.of("agentId", "incentiveCodes"));
        assertThat(names(portalSchema("SimNewContractRequest"), "properties")).isEqualTo(engineNew);
        Set<String> requiredNew = new TreeSet<>(names(engineSchema("NewContractRequest"), "required"));
        requiredNew.remove("agentId");
        assertThat(names(portalSchema("SimNewContractRequest"), "required")).isEqualTo(requiredNew);

        Set<String> engineEvent = new TreeSet<>(names(engineSchema("PolicyEventRequest"), "properties"));
        engineEvent.removeAll(Set.of("agentId", "policyNo"));
        engineEvent.add("policyKey");
        assertThat(names(portalSchema("SimPolicyEventRequest"), "properties")).isEqualTo(engineEvent);
        assertThat(names(portalSchema("SimPolicyEventRequest").path("properties").path("eventType"), "enum"))
                .isEqualTo(names(engineSchema("PolicyEventRequest").path("properties").path("eventType"), "enum"));
        assertThat(portalSchema("SimPolicyEventRequest").path("then").path("required").toString()).isEqualTo("[\"newMonthlyPremium\"]");
    }
}
