package com.ga.portal.recon.engine;

import com.ga.portal.recon.rule.EngineAnswer;
import com.ga.portal.recon.rule.ReconMode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.OptionalLong;

/**
 * 엔진 명세 응답 → {@link EngineAnswer}(계획 ④). 200: 요청 모드의 payload 스키마를 통과하고, {@code agentId}·{@code closeYm}이 요청과 같고, 비교
 * 필드가 정수 표기({@code 1.0}이 아니다)여야 한다 — 아니면 {@code ENGINE_CONTRACT}. 그 밖의 상태: {@code Problem}이면 {@code code}만 읽는다(메시지는
 * 어디에도 옮기지 않는다), 아니면 코드 없이 상태로만 사유를 정한다.
 */
public final class StatementResponses {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private StatementResponses() {
    }

    public static EngineAnswer read(String agentId, String ym, ReconMode mode, int status, byte[] body) {
        JsonNode json;
        try {
            json = body.length == 0 ? null : JSON.readTree(body);
        } catch (JacksonException e) {
            json = null;
        }
        if (status != 200) {
            String code = json != null && ReconContracts.get().problem().validate(json).isEmpty() ? json.path("code").asString() : null;
            return EngineAnswer.problem(status, code);
        }
        if (json == null) {
            return EngineAnswer.contractViolation();
        }
        String type = mode == ReconMode.ESTIMATE ? "StatementEstimated" : "StatementConfirmed";
        if (!ReconContracts.get().payload(type).validate(json).isEmpty() || !json.path("agentId").asString("").equals(agentId)
                || !json.path("closeYm").asString("").equals(ym)) {
            return EngineAnswer.contractViolation();
        }
        OptionalLong gross = integral(json.path("gross"));
        OptionalLong offset = integral(json.path("clawbackOffset"));
        OptionalLong adjustment = integral(json.path("adjustment"));
        if (gross.isEmpty() || adjustment.isEmpty() || (json.has("clawbackOffset") && offset.isEmpty())) {
            return EngineAnswer.contractViolation();
        }
        if (mode == ReconMode.ESTIMATE) {
            OptionalLong basis = integral(json.path("basisSeq"));
            if (basis.isEmpty() || offset.isEmpty()) {
                return EngineAnswer.contractViolation();
            }
            return new EngineAnswer.Estimated(basis.getAsLong(), gross.getAsLong(), offset.getAsLong(), adjustment.getAsLong());
        }
        return new EngineAnswer.Confirmed(gross.getAsLong(), adjustment.getAsLong(), offset);
    }

    private static OptionalLong integral(JsonNode n) {
        return n.isIntegralNumber() && n.canConvertToLong() ? OptionalLong.of(n.asLong()) : OptionalLong.empty();
    }
}
