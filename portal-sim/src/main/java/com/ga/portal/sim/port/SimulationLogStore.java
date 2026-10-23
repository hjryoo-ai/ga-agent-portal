package com.ga.portal.sim.port;

import com.ga.platform.core.tenant.AgentId;
import com.ga.portal.domain.SimulationKind;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * {@code simulation_log}와 그 판정에 필요한 테넌트 설정(포털 Phase 5 ⑤) — 바인딩된 테넌트의 {@link SimTransactions} 안에서만 부른다. 모든 문장은
 * {@code tenant_id = :tenantId AND agent_id = :agentId}(설정 읽기 제외). 구현은 {@code portal-app}({@code portal-infra} 저장소 위).
 */
public interface SimulationLogStore {

    /** 그 설계사의 시뮬레이션 직렬화 잠금(트랜잭션 끝까지) — {@code pg_advisory_xact_lock}. 잡을 때까지 기다린다. */
    void lock(AgentId agent);

    /** 시뮬레이션에 쓰는 테넌트 설정 — 엔진 주소와 {@code params.sim_rate_per_minute}(형식이 맞을 때만, 아니면 비어 있다 — 앱 기본값). */
    Settings settings();

    /** {@code created_at > since}인 그 설계사의 행 수(세 상태 전부)와 그중 가장 이른 시각. */
    Window window(AgentId agent, Instant since);

    void insertRequested(UUID simId, AgentId agent, SimulationKind kind, String engineRequestJson, Instant createdAt);

    void succeeded(UUID simId, AgentId agent, String engineResponseJson, String ruleSnapshotId, int engineStatus);

    /** 실패 — 본문은 남기지 않는다. {@code engineStatus}는 엔진이 답했을 때만. */
    void failed(UUID simId, AgentId agent, String errorCode, Optional<Integer> engineStatus);

    record Settings(URI engineBaseUrl, OptionalInt ratePerMinute) {
    }

    record Window(long count, Optional<Instant> oldest) {
    }
}
