package com.ga.portal.recon.rule;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * 엔진 명세 조회의 결과(계약 {@code getStatement}) — 어댑터가 응답을 검증한 뒤의 모양. 200은 모드별 payload의 비교 필드만, 오류는 판정 불가 사유
 * 하나({@link #problem}이 계약의 상태·코드를 사유로 옮긴다 — 메시지는 읽지 않는다).
 */
public sealed interface EngineAnswer {

    /** {@code StatementEstimated} 모양 — {@code basisSeq}는 하한(엔진 E2 Q3), 금액은 원 단위 정수. */
    record Estimated(long basisSeq, long gross, long clawbackOffset, long adjustment) implements EngineAnswer {
    }

    /** {@code StatementConfirmed} 모양 — {@code clawbackOffset}은 계약상 선택(없으면 비교하지 않는다 — 계획 Q3). */
    record Confirmed(long gross, long adjustment, OptionalLong clawbackOffset) implements EngineAnswer {
        public Confirmed {
            Objects.requireNonNull(clawbackOffset, "clawbackOffset");
        }
    }

    /** 성공이 아닌 답. */
    record Failed(InconclusiveReason reason) implements EngineAnswer {
        public Failed {
            Objects.requireNonNull(reason, "reason");
            if (reason == InconclusiveReason.LAGGING || reason == InconclusiveReason.BUDGET || reason == InconclusiveReason.ESTIMATE_DIFF) {
                throw new IllegalArgumentException("not an engine answer: " + reason);
            }
        }
    }

    static EngineAnswer unavailable() {
        return new Failed(InconclusiveReason.ENGINE_UNAVAILABLE);
    }

    static EngineAnswer contractViolation() {
        return new Failed(InconclusiveReason.ENGINE_CONTRACT);
    }

    /**
     * 오류 응답 → 사유. 404 {@code AGENT_NOT_FOUND}·{@code STATEMENT_NOT_CONFIRMED}, 422 {@code MONTH_CLOSED}은 계약의 뜻 그대로, 401·403·5xx는
     * 엔진에 닿지 못한 것(재시도 0), 그 밖의 4xx는 거절, 4xx·5xx가 아닌 상태는 계약 위반.
     */
    static EngineAnswer problem(int status, String code) {
        InconclusiveReason reason;
        if (status == 404 && "AGENT_NOT_FOUND".equals(code)) {
            reason = InconclusiveReason.AGENT_NOT_FOUND;
        } else if (status == 404 && "STATEMENT_NOT_CONFIRMED".equals(code)) {
            reason = InconclusiveReason.NOT_CONFIRMED;
        } else if (status == 422 && "MONTH_CLOSED".equals(code)) {
            reason = InconclusiveReason.MONTH_CLOSED;
        } else if (status == 401 || status == 403 || (status >= 500 && status <= 599)) {
            reason = InconclusiveReason.ENGINE_UNAVAILABLE;
        } else if (status >= 400 && status <= 499) {
            reason = InconclusiveReason.ENGINE_REJECTED;
        } else {
            reason = InconclusiveReason.ENGINE_CONTRACT;
        }
        return new Failed(reason);
    }
}
