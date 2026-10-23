package com.ga.portal.sim;

import com.ga.platform.core.tenant.AgentId;
import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.Role;
import com.ga.portal.domain.SimulationKind;
import com.ga.portal.domain.access.Caller;
import com.ga.portal.domain.access.DenyReason;
import com.ga.portal.domain.access.InvalidRequest;
import com.ga.portal.domain.access.NotFound;
import com.ga.portal.sim.EngineSimulationClient.EngineReply;
import com.ga.portal.sim.port.EngineCredentials;
import com.ga.portal.sim.port.SimFacts;
import com.ga.portal.sim.port.SimMetrics;
import com.ga.portal.sim.port.SimTransactions;
import com.ga.portal.sim.port.SimulationLogStore;
import com.ga.portal.sim.testing.StubSimulationEngine;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C4~C7(유스케이스 수준 — DB·HTTP는 {@code portal-app} 통합 시험): 순서(형식 → 키 → 트랜잭션 A[잠금·설정·사실·창·REQUESTED] → 호출 → 트랜잭션 B), 429면 행·호출
 * 0, 토큰이 없으면 호출 0, 남의 키·역할은 404이고 아무것도 하지 않는다. 엔진은 샘플 사본을 내는 가짜.
 */
class SimulationUseCasesTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final TenantId T = TenantId.of("GA01");
    static final AgentId A = AgentId.of("A-1001");
    static final Caller AGENT = new Caller(T, A, Role.AGENT);
    static final Instant NOW = Instant.parse("2027-02-15T00:30:00.1234567Z");
    static final String NEW = "{\"productKey\":\"WHOLE-LIFE-20Y\",\"monthlyPremium\":\"150000\",\"contractDate\":\"2026-08-01\"}";
    static final String CANCEL = "{\"policyKey\":\"0123456789abcdef\",\"eventType\":\"CANCEL\",\"asOfDate\":\"2027-02-15\",\"elapsedInstallments\":\"7\"}";
    static final Optional<String> JSON_TYPE = Optional.of("application/json");

    final List<String> events = new ArrayList<>();
    final List<String> rows = new ArrayList<>();
    long windowCount;
    Optional<String> token = Optional.of("engine-token");
    final StubSimulationEngine stub = StubSimulationEngine.of(StubSimulationEngine.samplesDir(Path.of(System.getProperty("ga.repoRoot"))), "standard");
    EngineReply forced;
    final AtomicInteger rateLimitedMetric = new AtomicInteger();
    final List<String> reasons = new ArrayList<>();

    final SimulationLogStore store = new SimulationLogStore() {
        @Override
        public void lock(AgentId agent) {
            events.add("lock");
        }

        @Override
        public Settings settings() {
            events.add("settings");
            return new Settings(URI.create("http://engine.invalid"), OptionalInt.of(3));
        }

        @Override
        public Window window(AgentId agent, Instant since) {
            events.add("window");
            assertThat(since).isEqualTo(NOW.truncatedTo(java.time.temporal.ChronoUnit.MICROS).minusSeconds(60));
            return new Window(windowCount, windowCount == 0 ? Optional.empty() : Optional.of(since.plusSeconds(40)));   // DB 값(마이크로초) — 창 시작 + 40초
        }

        @Override
        public void insertRequested(UUID simId, AgentId agent, SimulationKind kind, String engineRequestJson, Instant createdAt) {
            events.add("insert");
            rows.add("REQUESTED " + kind + " " + createdAt);
        }

        @Override
        public void succeeded(UUID simId, AgentId agent, String engineResponseJson, String ruleSnapshotId, int engineStatus) {
            events.add("succeeded");
            rows.add("SUCCEEDED " + ruleSnapshotId + " " + engineStatus);
        }

        @Override
        public void failed(UUID simId, AgentId agent, String errorCode, Optional<Integer> engineStatus) {
            events.add("failed");
            rows.add("FAILED " + errorCode + " " + engineStatus.map(String::valueOf).orElse("-"));
        }
    };

    final SimTransactions transactions = new SimTransactions() {
        @Override
        public <T> T write(Supplier<T> work) {
            events.add("tx");
            return work.get();
        }
    };

    SimulationUseCases useCases() {
        EngineSimulationClient engine = (base, kind, t, body) -> {
            events.add("engine");
            if (forced != null) {
                return forced;
            }
            return stub.match(kind == SimulationKind.NEW_CONTRACT ? "new-contract" : "policy-event", body)
                    .<EngineReply>map(s -> new EngineReply.Answered(s.status(), s.response()))
                    .orElse(new EngineReply.Answered(500, new byte[0]));
        };
        EngineCredentials credentials = tenant -> token.map(EngineCredentials.EngineToken::new);
        SimFacts facts = () -> {
            events.add("facts");
            return new SimFacts.Facts(11, Optional.empty(), "OK");
        };
        SimMetrics metrics = new SimMetrics() {
            @Override
            public void rateLimited(TenantId tenant) {
                rateLimitedMetric.incrementAndGet();
            }

            @Override
            public void request(TenantId tenant, String reason) {
                reasons.add(reason);
            }
        };
        return new SimulationUseCases(store, transactions, (tenant, agent, key) -> key.equals("0123456789abcdef") && agent.equals(A)
                ? Optional.of("POL-2026-0001") : Optional.empty(), engine, credentials, facts, metrics, Clock.fixed(NOW, ZoneOffset.UTC),
                () -> UUID.fromString("00000000-0000-4000-8000-000000000001"), 10);
    }

    static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void theRowIsRequestedBeforeTheCallAndSucceededAfter() {
        JsonNode body = JSON.readTree(useCases().newContract(AGENT, JSON_TYPE, b(NEW)));
        assertThat(events).containsExactly("tx", "lock", "settings", "facts", "window", "insert", "engine", "tx", "succeeded");
        assertThat(rows).containsExactly("REQUESTED NEW_CONTRACT 2027-02-15T00:30:00.123456Z", "SUCCEEDED RS-1a48e16a86c85b56331e3468a3042e9c 200");
        assertThat(body.path("simId").asString()).isEqualTo("00000000-0000-4000-8000-000000000001");
        assertThat(body.path("requestedAt").asString()).as("= the row's created_at (micros)").isEqualTo("2027-02-15T00:30:00.123456Z");
        assertThat(body.path("incentives").get(0).path("amount").asLong()).isEqualTo(855_000);
        assertThat(reasons).containsExactly("OK");
    }

    @Test
    void aPolicyEventResolvesTheKeyWithinTheCaller() {
        JsonNode body = JSON.readTree(useCases().policyEvent(AGENT, JSON_TYPE, b(CANCEL)));
        assertThat(body.path("clawbackAmount").asLong()).isEqualTo(-720_000);
        assertThat(body.has("generatedAt")).isFalse();
        events.clear();
        assertThatThrownBy(() -> useCases().policyEvent(AGENT, JSON_TYPE, b(CANCEL.replace("0123456789abcdef", "fedcba9876543210"))))
                .isInstanceOfSatisfying(NotFound.class, e -> assertThat(e.reason()).isEqualTo(DenyReason.MISSING));
        assertThatThrownBy(() -> useCases().policyEvent(new Caller(T, AgentId.of("B-2002"), Role.AGENT), JSON_TYPE, b(CANCEL)))
                .isInstanceOf(NotFound.class);
        assertThat(events).as("no row, no call for an unresolved key").isEmpty();
    }

    @Test
    void aManagerAndAMalformedBodyTouchNothing() {
        Caller manager = new Caller(T, A, Role.MANAGER);
        assertThatThrownBy(() -> useCases().newContract(manager, JSON_TYPE, b(NEW)))
                .isInstanceOfSatisfying(NotFound.class, e -> assertThat(e.reason()).isEqualTo(DenyReason.ROLE));
        assertThatThrownBy(() -> useCases().policyEvent(manager, JSON_TYPE, b(CANCEL))).isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> useCases().newContract(AGENT, JSON_TYPE, b("{}"))).isInstanceOf(InvalidRequest.class);
        assertThatThrownBy(() -> useCases().newContract(AGENT, Optional.of("text/plain"), b(NEW))).isInstanceOf(InvalidRequest.class);
        assertThat(events).isEmpty();
    }

    @Test
    void theLimitRollsBackWithoutARowOrACall() {
        windowCount = 3;   // 한도 3(테넌트 설정) — 이미 셋
        assertThatThrownBy(() -> useCases().newContract(AGENT, JSON_TYPE, b(NEW)))
                .isInstanceOfSatisfying(RateLimited.class, e -> assertThat(e.retryAfterSeconds()).isEqualTo(40));
        assertThat(events).containsExactly("tx", "lock", "settings", "facts", "window");
        assertThat(rows).isEmpty();
        assertThat(rateLimitedMetric.get()).isEqualTo(1);
        assertThat(reasons).isEmpty();
    }

    @Test
    void failuresAreRecordedWithoutBodiesAndBecomeFixedCodes() {
        token = Optional.empty();
        assertThatThrownBy(() -> useCases().newContract(AGENT, JSON_TYPE, b(NEW)))
                .isInstanceOfSatisfying(SimulationFailed.class, e -> assertThat(e.code()).isEqualTo(SimulationFailed.Code.ENGINE_UNAVAILABLE));
        assertThat(events).as("no call without a token").doesNotContain("engine");
        assertThat(rows).containsExactly("REQUESTED NEW_CONTRACT 2027-02-15T00:30:00.123456Z", "FAILED TOKEN_MISSING -");

        token = Optional.of("engine-token");
        rows.clear();
        forced = new EngineReply.Unreachable("ENGINE_TIMEOUT");
        events.clear();
        assertThatThrownBy(() -> useCases().newContract(AGENT, JSON_TYPE, b(NEW))).isInstanceOf(SimulationFailed.class);
        assertThat(rows).last().isEqualTo("FAILED ENGINE_TIMEOUT -");
        assertThat(events.stream().filter("engine"::equals)).as("one call — no retry").hasSize(1);

        forced = null;
        rows.clear();
        assertThatThrownBy(() -> useCases().policyEvent(AGENT, JSON_TYPE,
                b("{\"policyKey\":\"0123456789abcdef\",\"eventType\":\"PAYMENT_OVERDUE\",\"asOfDate\":\"2027-02-15\"}")))
                .isInstanceOfSatisfying(SimulationFailed.class, e -> assertThat(e.code()).isEqualTo(SimulationFailed.Code.UNSUPPORTED_EVENT_TYPE));
        assertThat(rows).last().isEqualTo("FAILED UNSUPPORTED_EVENT_TYPE 422");
        assertThat(reasons).containsExactly("ENGINE_UNAVAILABLE", "ENGINE_UNAVAILABLE", "UNSUPPORTED_EVENT_TYPE");
        assertThat(stub.calls()).as("the fake engine matched samples without the HTTP stub").isZero();
    }
}
