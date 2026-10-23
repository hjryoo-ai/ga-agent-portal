package com.ga.portal.sim;

import com.ga.portal.domain.SimulationKind;
import com.ga.portal.sim.port.SimFacts.Facts;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.UUID;

/**
 * 포털 응답 = 포털 사실 넷({@code asOf}·{@code dataStatus}·{@code simId}·{@code requestedAt}) + 엔진 결과 중 <b>사본 스키마에 있는 속성만</b>, 값은 그대로
 * (계획 P-25 — 엔진 스키마는 추가 속성을 막지 않고 포털 계약은 막는다). 모르는 엔진 필드는 버리고 개수만 센다. 금액을 만들거나 바꾸지 않는다 — 정수 노드를 그대로
 * 옮기므로 정수 리터럴 바이트가 같다({@code SimulationPassThroughIT}).
 */
final class SimResponses {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SimResponses() {
    }

    record Mapped(byte[] body, int droppedFields) {
    }

    static String engineSchema(SimulationKind kind) {
        return kind == SimulationKind.NEW_CONTRACT ? "NewContractResult" : "PolicyEventResult";
    }

    static Mapped portal(SimulationKind kind, Facts facts, UUID simId, Instant requestedAt, JsonNode engineResult) {
        ObjectNode out = JSON.createObjectNode();
        ObjectNode asOf = out.putObject("asOf");
        asOf.put("seq", facts.seq());
        facts.at().ifPresent(at -> asOf.put("at", at.toString()));
        out.put("dataStatus", facts.dataStatus());
        out.put("simId", simId.toString());
        out.put("requestedAt", requestedAt.toString());
        int[] dropped = {0};
        JsonNode schema = SimContracts.get().engineResultNode(engineSchema(kind));
        engineResult.properties().forEach(e -> {
            JsonNode property = schema.path("properties").path(e.getKey());
            if (property.isMissingNode()) {
                dropped[0]++;
            } else {
                out.set(e.getKey(), known(e.getValue(), property, dropped));
            }
        });
        return new Mapped(JSON.writeValueAsBytes(out), dropped[0]);
    }

    /** 스키마가 속성을 적은 객체·객체 배열은 그 속성만, 나머지(참조된 원시값)는 그대로. */
    private static JsonNode known(JsonNode value, JsonNode schema, int[] dropped) {
        if (value.isObject() && schema.has("properties")) {
            ObjectNode out = JSON.createObjectNode();
            value.properties().forEach(e -> {
                JsonNode property = schema.path("properties").path(e.getKey());
                if (property.isMissingNode()) {
                    dropped[0]++;
                } else {
                    out.set(e.getKey(), known(e.getValue(), property, dropped));
                }
            });
            return out;
        }
        if (value.isArray() && schema.path("items").has("properties")) {
            ArrayNode out = JSON.createArrayNode();
            value.forEach(item -> out.add(known(item, schema.path("items"), dropped)));
            return out;
        }
        return value;
    }
}
