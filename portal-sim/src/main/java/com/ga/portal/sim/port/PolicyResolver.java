package com.ga.portal.sim.port;

import com.ga.platform.core.tenant.AgentId;
import com.ga.platform.core.tenant.TenantId;

import java.util.Optional;

/**
 * {@code policyKey} → 증권번호 원문: 그 설계사의 라인({@code earnings_line}) 증권번호 집합 안에서만 푼다(조회 {@code /lines}와 같은 규약 — 엔진의
 * {@code POLICY_NOT_FOUND}가 "그 설계사의 계산 레코드"인 것과 짝). 남의·없는 키는 비어 있다(→ 404, 엔진 호출 0). {@code Caller}를 받지 않는다 — 호출자를 받는
 * 공개 메서드는 진입점뿐이다(API 계층 규칙 ⓒ).
 */
public interface PolicyResolver {

    Optional<String> policyNo(TenantId tenant, AgentId agent, String policyKey);
}
