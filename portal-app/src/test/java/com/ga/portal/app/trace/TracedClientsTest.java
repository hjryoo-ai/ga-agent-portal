package com.ga.portal.app.trace;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.SimulationKind;
import com.ga.portal.domain.trace.Tracing;
import com.ga.portal.ingest.feed.FeedClient;
import com.ga.portal.ingest.feed.FeedException;
import com.ga.portal.ingest.feed.FeedPage;
import com.ga.portal.sim.EngineSimulationClient;
import com.ga.portal.sim.port.EngineCredentials.EngineToken;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 포털 Phase 7(docs/지표.md §4): 엔진 호출 스팬은 결과의 종류만 싣는다 — 본문·메시지·주소 없음. */
class TracedClientsTest {

    private static final TenantId T = TenantId.of("GA01");

    /** 닫힌 스팬마다 "이름 키=값 …". */
    private final List<String> closed = new ArrayList<>();

    private final Tracing tracing = name -> new Tracing.Span() {
        private final StringBuilder attrs = new StringBuilder(name);

        @Override
        public Tracing.Span tenant(TenantId tenant) {
            attrs.append(" tenant=").append(tenant.value());
            return this;
        }

        @Override
        public Tracing.Span count(String key, long value) {
            attrs.append(' ').append(key).append('=').append(value);
            return this;
        }

        @Override
        public Tracing.Span kind(String key, String value) {
            attrs.append(' ').append(key).append('=').append(Tracing.kind(value));
            return this;
        }

        @Override
        public void close() {
            closed.add(attrs.toString());
        }
    };

    @Test
    void theFeedSpanCarriesOkOrTheExceptionKind() {
        FeedClient ok = (t, after, limit) -> new FeedPage(t, List.of(), after, after, 1);
        new TracedFeedClient(ok, tracing).fetch(T, 0, 1);
        FeedClient down = (t, after, limit) -> {
            throw new FeedException.Unavailable("connect to http://engine.invalid/x refused");
        };
        assertThatThrownBy(() -> new TracedFeedClient(down, tracing).fetch(T, 0, 1)).isInstanceOf(FeedException.Unavailable.class);
        assertThat(closed).containsExactly("engine.feed outcome=OK", "engine.feed outcome=Unavailable");
    }

    @Test
    void theSimulationSpanCarriesAnsweredOrTheUnreachableReason() {
        EngineSimulationClient answered = (url, kind, token, body) -> new EngineSimulationClient.EngineReply.Answered(200, "{\"x\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        EngineSimulationClient timeout = (url, kind, token, body) -> new EngineSimulationClient.EngineReply.Unreachable("ENGINE_TIMEOUT");
        URI engine = URI.create("http://engine.invalid");
        new TracedSimulationClient(answered, tracing).call(engine, SimulationKind.NEW_CONTRACT, new EngineToken("fictional"), new byte[0]);
        new TracedSimulationClient(timeout, tracing).call(engine, SimulationKind.NEW_CONTRACT, new EngineToken("fictional"), new byte[0]);
        assertThat(closed).containsExactly("engine.simulation outcome=ANSWERED", "engine.simulation outcome=ENGINE_TIMEOUT");
    }
}
