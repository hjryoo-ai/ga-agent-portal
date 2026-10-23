package com.ga.portal.sim.port;

import com.ga.platform.core.tenant.TenantId;

/**
 * 시뮬레이션 지표(설계서 §10 v1.8): {@code api_rate_limited_total{tenant}}·{@code sim_requests_total{tenant, reason}} — {@code reason}은 닫힌 값
 * ({@code OK}·{@code ENGINE_UNAVAILABLE}·{@code ENGINE_CONTRACT}·엔진 422 코드 셋). 설계사 태그 없음.
 */
public interface SimMetrics {

    void rateLimited(TenantId tenant);

    void request(TenantId tenant, String reason);
}
