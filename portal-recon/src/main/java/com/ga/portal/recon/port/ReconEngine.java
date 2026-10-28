package com.ga.portal.recon.port;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.recon.rule.EngineAnswer;
import com.ga.portal.recon.rule.ReconMode;

import java.util.OptionalLong;

/**
 * 대사가 엔진에 닿는 두 곳(계획 ④·Q2) — {@code portal-app}이 테넌트의 엔진 주소·토큰으로 구현한다. 둘 다 재시도 0이고 예외를 던지지 않는다.
 */
public interface ReconEngine {

    /** 명세 조회 한 번(검증된 모양 — {@code HttpStatementClient}). 토큰이 없거나 닿지 못하면 {@code ENGINE_UNAVAILABLE}. */
    EngineAnswer statement(TenantId tenant, String agentId, String ym, ReconMode mode);

    /** 피드 머리 한 번(after = {@code afterSeq}, limit 1). 닿지 못했거나 피드가 거절하면 비어 있다. */
    OptionalLong feedHead(TenantId tenant, long afterSeq);
}
