package com.ga.portal.sim;

import java.util.Objects;

/**
 * 시뮬레이션이 결과를 내지 못했다 — 포털 고정 바이트로 바뀐다(설계서 §9 거부 바이트 표, 계획 Q1·Q19). 엔진의 {@code message}는 옮기지 않는다. 메시지는 코드뿐이고
 * 스택은 남기지 않는다(흐름 제어용).
 */
public final class SimulationFailed extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 포털 응답 코드와 HTTP 상태(닫힌 값). */
    public enum Code {
        INSURER_AMBIGUOUS(422),
        UNSUPPORTED_EVENT_TYPE(422),
        ENGINE_REJECTED(422),
        ENGINE_CONTRACT(502),
        ENGINE_UNAVAILABLE(503);

        private final int status;

        Code(int status) {
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    private final Code code;

    public SimulationFailed(Code code) {
        super(Objects.requireNonNull(code, "code").name(), null, false, false);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
