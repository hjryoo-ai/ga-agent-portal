package com.ga.portal.sim;

import com.ga.portal.domain.SimulationKind;
import com.ga.portal.sim.port.EngineCredentials.EngineToken;

import java.net.URI;

/** 엔진 시뮬레이션 동기 호출(설계서 §4.3) — 재시도 0(지시문 §4). 서버만 부른다(화면은 엔진을 모른다). */
public interface EngineSimulationClient {

    EngineReply call(URI engineBaseUrl, SimulationKind kind, EngineToken token, byte[] engineRequest);

    /** 엔진의 답 — 답했거나(상태·본문), 닿지 못했다(연결 실패·시간 초과). */
    sealed interface EngineReply {

        record Answered(int status, byte[] body) implements EngineReply {
        }

        /** {@code reason}: {@code ENGINE_TIMEOUT}(읽기·연결 시간 초과) 또는 {@code ENGINE_UNAVAILABLE}(그 밖의 I/O). */
        record Unreachable(String reason) implements EngineReply {
        }
    }
}
