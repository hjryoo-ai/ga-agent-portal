/**
 * 조회 유스케이스의 포트(어댑터는 {@code portal-app}). {@link com.ga.portal.query.port.AgentQueryStore}의 모든 메서드는 {@code AgentId}를 받는다 — 구현의 모든
 * 문장은 {@code agent_id = :agentId} 술어를 가진다(지시문 D-4, {@code TenantPredicateScanTest}의 두 번째 술어).
 */
package com.ga.portal.query.port;
