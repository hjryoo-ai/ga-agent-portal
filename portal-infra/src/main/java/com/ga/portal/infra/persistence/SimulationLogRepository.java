package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code simulation_log}(설계서 §5, 포털 Phase 5 ⑤): 레이트 리밋 창 조회·요청 행 넣기·결과 갱신. 모든 문장이 {@code tenant_id = :tenantId}와 그 설계사
 * ({@code :agentId})를 가진다({@code TenantPredicateScanTest}). 결과 갱신은 {@code REQUESTED} 행만 바꾼다(두 번 끝나지 않는다). 지우는 문장은 없다 —
 * 보존 기간 삭제는 운영자가 손으로(`docs/운영.md`, 지시문 §4 — 삭제 잡 없음).
 */
@Repository
public class SimulationLogRepository extends ViewRepository {

    public record Window(long count, Optional<Instant> oldest) {
    }

    public SimulationLogRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    private static Map<String, Object> params(String agentId, Object... more) {
        Map<String, Object> p = new HashMap<>();
        p.put("agentId", agentId);
        for (int i = 0; i < more.length; i += 2) {
            p.put((String) more[i], more[i + 1]);
        }
        return p;
    }

    /** {@code created_at > since}인 그 설계사의 행 수(상태 무관)와 가장 이른 시각. */
    public Window window(String agentId, Instant since) {
        return queryAtMostOne("""
                SELECT count(*) AS n, min(created_at) AS oldest
                  FROM simulation_log
                 WHERE tenant_id = :tenantId AND agent_id = :agentId AND created_at > :since
                """, params(agentId, "since", utc(since)), (rs, n) -> new Window(rs.getLong("n"), optInstant(rs, "oldest")))
                .orElse(new Window(0, Optional.empty()));
    }

    public void insertRequested(UUID simId, String agentId, String kind, String requestJson, Instant createdAt) {
        update("""
                INSERT INTO simulation_log (tenant_id, sim_id, agent_id, kind, request, status, created_at)
                VALUES (:tenantId, :simId, :agentId, :kind, CAST(:request AS jsonb), 'REQUESTED', :createdAt)
                """, params(agentId, "simId", simId, "kind", kind, "request", requestJson, "createdAt", utc(createdAt)));
    }

    /** {@code REQUESTED} → {@code SUCCEEDED}. 바뀐 행 수(0이면 이미 끝났거나 없는 행). */
    public int succeeded(UUID simId, String agentId, String responseJson, String ruleSnapshotId, int engineStatus) {
        return update("""
                UPDATE simulation_log
                   SET status = 'SUCCEEDED', response = CAST(:response AS jsonb), rule_snapshot_id = :ruleSnapshotId, engine_status = :engineStatus
                 WHERE tenant_id = :tenantId AND agent_id = :agentId AND sim_id = :simId AND status = 'REQUESTED'
                """, params(agentId, "simId", simId, "response", responseJson, "ruleSnapshotId", ruleSnapshotId, "engineStatus", engineStatus));
    }

    /** {@code REQUESTED} → {@code FAILED}(본문 없음). */
    public int failed(UUID simId, String agentId, String errorCode, Optional<Integer> engineStatus) {
        return update("""
                UPDATE simulation_log
                   SET status = 'FAILED', error_code = :errorCode, engine_status = :engineStatus
                 WHERE tenant_id = :tenantId AND agent_id = :agentId AND sim_id = :simId AND status = 'REQUESTED'
                """, params(agentId, "simId", simId, "errorCode", errorCode, "engineStatus", engineStatus.orElse(null)));
    }
}
