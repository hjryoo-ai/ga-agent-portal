package com.ga.portal.sim;

import com.ga.portal.domain.SimulationKind;
import com.ga.portal.sim.EngineSimulationClient.EngineReply;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 엔진의 답 → 결과(포털 Phase 5 계획 ⑥ 표, Q1). 엔진 200은 사본 스키마를 통과하고 숫자가 전부 정수 표기일 때만 성공이다. 엔진 400·404는 설계사 잘못이 아니라
 * 계약·운영 문제라 502 {@code ENGINE_CONTRACT}, 엔진이 닿지 않거나 서비스 토큰을 거부하면 503 {@code ENGINE_UNAVAILABLE}, 엔진 422는 닫힌 두 코드만 그대로·
 * 나머지는 {@code ENGINE_REJECTED}. 로그 행의 {@code error_code}는 엔진 코드가 형식에 맞으면 그 코드(진단용), 아니면 포털 코드다.
 */
final class EngineOutcomes {

    static final Set<String> PASSED_THROUGH_422 = Set.of("INSURER_AMBIGUOUS", "UNSUPPORTED_EVENT_TYPE");
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    private EngineOutcomes() {
    }

    sealed interface Outcome {
    }

    /** 사본 스키마를 통과한 엔진 결과. */
    record Success(JsonNode result, String ruleSnapshotId, int status) implements Outcome {
    }

    /** 실패 — 포털 응답 코드, 로그 코드, 엔진이 답한 상태(있으면). 지표 사유는 포털 코드 이름. */
    record Failure(SimulationFailed.Code code, String logCode, Optional<Integer> engineStatus, String engineMessage) implements Outcome {
        Failure(SimulationFailed.Code code, String logCode, Optional<Integer> engineStatus) {
            this(code, logCode, engineStatus, "");
        }
    }

    static Outcome tokenMissing() {
        return new Failure(SimulationFailed.Code.ENGINE_UNAVAILABLE, "TOKEN_MISSING", Optional.empty());
    }

    static Outcome classify(SimulationKind kind, EngineReply reply) {
        return switch (reply) {
            case EngineReply.Unreachable u -> new Failure(SimulationFailed.Code.ENGINE_UNAVAILABLE, u.reason(), Optional.empty());
            case EngineReply.Answered a -> answered(kind, a.status(), a.body());
        };
    }

    private static Outcome answered(SimulationKind kind, int status, byte[] body) {
        Optional<Integer> engineStatus = Optional.of(status);
        if (status == 200) {
            return success(kind, body).map(Outcome.class::cast)
                    .orElseGet(() -> new Failure(SimulationFailed.Code.ENGINE_CONTRACT, "ENGINE_CONTRACT", engineStatus));
        }
        if (status == 401 || status == 403 || status >= 500) {
            return new Failure(SimulationFailed.Code.ENGINE_UNAVAILABLE, "ENGINE_UNAVAILABLE", engineStatus);
        }
        Optional<String> engineCode = problemCode(body);
        if (status == 422) {
            SimulationFailed.Code code = engineCode.filter(PASSED_THROUGH_422::contains).map(SimulationFailed.Code::valueOf)
                    .orElse(SimulationFailed.Code.ENGINE_REJECTED);
            String message;
            try {
                message = JSON.readTree(body).path("message").asString("");
            } catch (JacksonException e) {
                message = "";
            }
            return new Failure(code, engineCode.orElse("ENGINE_REJECTED"), engineStatus, message);
        }
        return new Failure(SimulationFailed.Code.ENGINE_CONTRACT, engineCode.orElse("ENGINE_CONTRACT"), engineStatus);
    }

    private static Optional<Success> success(SimulationKind kind, byte[] body) {
        JsonNode node;
        try {
            node = JSON.readTree(body);
        } catch (JacksonException e) {
            return Optional.empty();
        }
        if (node == null || !node.isObject() || !integralOnly(node)
                || !SimContracts.get().engineResult(SimResponses.engineSchema(kind)).validate(node).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Success(node, node.path("ruleSnapshotId").asString(), 200));
    }

    /** 숫자는 전부 정수 표기여야 한다 — 1.0·1e6은 스키마의 integer를 통과해도 다시 쓰면 바이트가 바뀐다. */
    static boolean integralOnly(JsonNode node) {
        if (node.isNumber()) {
            return node.isIntegralNumber();
        }
        for (JsonNode child : node) {
            if (!integralOnly(child)) {
                return false;
            }
        }
        return true;
    }

    private static Optional<String> problemCode(byte[] body) {
        try {
            JsonNode node = JSON.readTree(body);
            String code = node == null ? "" : node.path("code").asString("");
            return CODE.matcher(code).matches() ? Optional.of(code) : Optional.empty();
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }
}
