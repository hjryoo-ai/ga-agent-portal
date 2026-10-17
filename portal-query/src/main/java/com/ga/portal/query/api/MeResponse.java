package com.ga.portal.query.api;

import java.util.Optional;

/**
 * {@code GET /api/v1/me}: 역할은 {@code identity_link}, 본인 이름·등급·조직·상태는 {@code agent_profile}(행이 없으면 생략 — 엔진이 {@code AgentSnapshot}을
 * 아직 내지 않았다, Q9). 본인에게 본인 이름은 가리지 않는다(DD-13). 테넌트 설정 넷(지급일 규칙·경고 임계치 bp).
 */
public record MeResponse(AsOf asOf, String dataStatus, String agentId, String role, Optional<String> agentName, Optional<String> grade,
                         Optional<String> orgPath, Optional<String> status, Tenant tenant) {

    public record Tenant(String name, String payoutDayRule, long limitWarnBp, long limitCritBp) {
    }
}
