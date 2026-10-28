package com.ga.portal.app.trace;

import com.ga.portal.domain.SimulationKind;
import com.ga.portal.domain.trace.Tracing;
import com.ga.portal.sim.EngineSimulationClient;
import com.ga.portal.sim.port.EngineCredentials.EngineToken;

import java.net.URI;

/** 시뮬레이션 호출 스팬 {@code engine.simulation}({@code outcome} = {@code ANSWERED} 또는 닿지 못한 사유 — 요청·응답 본문은 싣지 않는다). */
public final class TracedSimulationClient implements EngineSimulationClient {

    private final EngineSimulationClient delegate;
    private final Tracing tracing;

    public TracedSimulationClient(EngineSimulationClient delegate, Tracing tracing) {
        this.delegate = delegate;
        this.tracing = tracing;
    }

    @Override
    public EngineReply call(URI engineBaseUrl, SimulationKind kind, EngineToken token, byte[] engineRequest) {
        Tracing.Span span = tracing.start("engine.simulation");
        try {
            EngineReply reply = delegate.call(engineBaseUrl, kind, token, engineRequest);
            span.kind("outcome", switch (reply) {
                case EngineReply.Answered a -> "ANSWERED";
                case EngineReply.Unreachable u -> u.reason();
            });
            return reply;
        } finally {
            span.close();
        }
    }
}
