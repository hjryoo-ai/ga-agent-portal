package com.ga.portal.query.api;

import java.util.Optional;

/**
 * {@code GET /api/v1/me}: 역할은 {@code identity_link}, 본인 이름·등급·조직·상태는 {@code agent_profile}(행이 없으면 생략 — 엔진이 {@code AgentSnapshot}을
 * 아직 내지 않았다, Q9). 본인에게 본인 이름은 가리지 않는다(DD-13). 테넌트 설정 넷(지급일 규칙·경고 임계치 bp)과 화면 기능(1.1.0 — 언제나 싣는다).
 */
public record MeResponse(AsOf asOf, String dataStatus, String agentId, String role, Optional<String> agentName, Optional<String> grade,
                         Optional<String> orgPath, Optional<String> status, Optional<Features> features, Tenant tenant) {

    /** 테넌트가 켠 화면 기능(포털 Phase 5) — {@code forecastScenario}: 분급 타임라인의 유지율 시나리오. */
    public record Features(boolean forecastScenario) {
    }


    public record Tenant(String name, String payoutDayRule, long limitWarnBp, long limitCritBp) {
    }
}
