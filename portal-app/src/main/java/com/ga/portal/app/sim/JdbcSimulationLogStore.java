package com.ga.portal.app.sim;

import com.ga.platform.core.tenant.AgentId;
import com.ga.portal.domain.SimulationKind;
import com.ga.portal.infra.persistence.SimulationLockRepository;
import com.ga.portal.infra.persistence.SimulationLogRepository;
import com.ga.portal.infra.persistence.TenantRepository;
import com.ga.portal.sim.port.SimulationLogStore;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * {@link SimulationLogStore}의 저장소 어댑터. 설정은 테넌트 행({@code engine_base_url}·{@code params}) — {@code sim_rate_per_minute}는 1~9999 정수일 때만
 * (아니면 비어 있고 앱 기본값). 결과 갱신이 행 하나를 바꾸지 못하면(이미 끝났거나 없는 행) 예외 — 500, 행은 그대로.
 */
final class JdbcSimulationLogStore implements SimulationLogStore {

    private static final Pattern RATE = Pattern.compile("[1-9][0-9]{0,3}");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SimulationLogRepository logs;
    private final SimulationLockRepository locks;
    private final TenantRepository tenants;

    JdbcSimulationLogStore(SimulationLogRepository logs, SimulationLockRepository locks, TenantRepository tenants) {
        this.logs = logs;
        this.locks = locks;
        this.tenants = tenants;
    }

    @Override
    public void lock(AgentId agent) {
        locks.lock(agent.value());
    }

    @Override
    public Settings settings() {
        var tenant = tenants.current().orElseThrow(() -> new IllegalStateException("no tenant row for the bound tenant"));
        return new Settings(URI.create(tenant.engineBaseUrl()), rate(tenant.paramsJson()));
    }

    static OptionalInt rate(String params) {
        try {
            JsonNode node = JSON.readTree(params == null ? "{}" : params).path("sim_rate_per_minute");
            String text = node.isIntegralNumber() || node.isString() ? node.asString() : "";
            return RATE.matcher(text).matches() ? OptionalInt.of(Integer.parseInt(text)) : OptionalInt.empty();
        } catch (JacksonException e) {
            return OptionalInt.empty();
        }
    }

    @Override
    public Window window(AgentId agent, Instant since) {
        SimulationLogRepository.Window w = logs.window(agent.value(), since);
        return new Window(w.count(), w.oldest());
    }

    @Override
    public void insertRequested(UUID simId, AgentId agent, SimulationKind kind, String engineRequestJson, Instant createdAt) {
        logs.insertRequested(simId, agent.value(), kind.name(), engineRequestJson, createdAt);
    }

    @Override
    public void succeeded(UUID simId, AgentId agent, String engineResponseJson, String ruleSnapshotId, int engineStatus) {
        if (logs.succeeded(simId, agent.value(), engineResponseJson, ruleSnapshotId, engineStatus) != 1) {
            throw new IllegalStateException("simulation_log row is not REQUESTED");
        }
    }

    @Override
    public void failed(UUID simId, AgentId agent, String errorCode, Optional<Integer> engineStatus) {
        if (logs.failed(simId, agent.value(), errorCode, engineStatus) != 1) {
            throw new IllegalStateException("simulation_log row is not REQUESTED");
        }
    }
}
