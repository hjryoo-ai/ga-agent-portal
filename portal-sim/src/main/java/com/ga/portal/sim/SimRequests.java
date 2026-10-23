package com.ga.portal.sim;

import com.ga.platform.core.tenant.AgentId;
import com.ga.portal.domain.access.InvalidRequest;
import com.networknt.schema.Schema;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

/**
 * 포털 요청 → 엔진 요청(포털 Phase 5 ②·계획 Q12). 순서: 형식({@code application/json}, 2 KiB 이하, JSON 객체 하나 — 중복 키 거부) → 포털 계약의 요청 스키마
 * ({@code additionalProperties: false}, 숫자 문자열 {@code pattern}, {@code REDUCE}면 새 월납 필수) → 날짜가 실제 달력의 날 — 엔진이 400을 낼 요청을 포털이
 * 먼저 400으로 막는다(계획 P-21). 어느 단계든 실패는 400 한 가지({@link InvalidRequest}, 사유·입력값 없음). 금액·횟수 문자열은 정수로 읽기만 한다(산술 없음).
 * 설계사는 호출자에서, 증권번호는 {@code policyKey}를 푼 원문에서 온다 — 요청 본문에는 둘 다 없다.
 */
public final class SimRequests {

    public static final int MAX_BODY_BYTES = 2048;
    static final String NEW_CONTRACT = "SimNewContractRequest";
    static final String POLICY_EVENT = "SimPolicyEventRequest";
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    private SimRequests() {
    }

    /** 형식·스키마 검사를 지난 요청 객체. */
    static JsonNode read(Optional<String> contentType, byte[] body, String schemaName) {
        if (!isJson(contentType) || body == null || body.length == 0 || body.length > MAX_BODY_BYTES) {
            throw new InvalidRequest("body");
        }
        JsonNode node;
        try {
            node = JSON.readTree(body);
        } catch (JacksonException e) {
            throw new InvalidRequest("body");
        }
        Schema schema = SimContracts.get().portalSchema(schemaName);
        if (node == null || !node.isObject() || !schema.validate(node).isEmpty()) {
            throw new InvalidRequest("body");
        }
        return node;
    }

    static boolean isJson(Optional<String> contentType) {
        if (contentType.isEmpty()) {
            return false;
        }
        String[] parts = contentType.get().split(";", -1);
        if (!parts[0].strip().toLowerCase(Locale.ROOT).equals("application/json")) {
            return false;
        }
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].strip().toLowerCase(Locale.ROOT).replace(" ", "").equals("charset=utf-8")) {
                return false;
            }
        }
        return true;
    }

    /** 엔진 {@code NewContractRequest}(엔진 계약 사본의 필드 순서). 시책 코드는 보내지 않는다(계획 Q4 — 엔진이 전부 적용). */
    static ObjectNode newContract(AgentId agent, JsonNode request) {
        ObjectNode out = JSON.createObjectNode();
        out.put("agentId", agent.value());
        out.put("productKey", request.path("productKey").asString());
        out.put("monthlyPremium", Long.parseLong(request.path("monthlyPremium").asString()));
        out.put("contractDate", date(request.path("contractDate").asString()));
        if (request.has("paymentTermMonths")) {
            out.put("paymentTermMonths", Integer.parseInt(request.path("paymentTermMonths").asString()));
        }
        if (request.has("insurerCd")) {
            out.put("insurerCd", request.path("insurerCd").asString());
        }
        return out;
    }

    /** 엔진 {@code PolicyEventRequest}. {@code policyNo}는 그 설계사의 집합에서 푼 원문. */
    static ObjectNode policyEvent(AgentId agent, String policyNo, JsonNode request) {
        ObjectNode out = JSON.createObjectNode();
        out.put("agentId", agent.value());
        out.put("policyNo", policyNo);
        out.put("eventType", request.path("eventType").asString());
        out.put("asOfDate", date(request.path("asOfDate").asString()));
        if (request.has("elapsedInstallments")) {
            out.put("elapsedInstallments", Integer.parseInt(request.path("elapsedInstallments").asString()));
        }
        if (request.has("newMonthlyPremium")) {
            out.put("newMonthlyPremium", Long.parseLong(request.path("newMonthlyPremium").asString()));
        }
        return out;
    }

    /** 형식({@code pattern})을 지난 날짜가 실제 날인지 — 2026-02-30은 400. */
    private static String date(String value) {
        try {
            return LocalDate.parse(value).toString();
        } catch (DateTimeParseException e) {
            throw new InvalidRequest("date");
        }
    }
}
