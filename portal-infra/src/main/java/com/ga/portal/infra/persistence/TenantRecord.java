package com.ga.portal.infra.persistence;

import com.ga.platform.core.money.Ratio;
import com.ga.platform.core.tenant.TenantId;

/**
 * {@code tenant} 행(설계서 §5). 한도 경고 임계치는 표시·비교용 {@link Ratio}(분모 10000 = bp)로만 갖는다 — DB의 {@code NUMERIC}은
 * {@link TenantRepository}의 SQL에서 정수로 바뀌어 오고, 포털 코드에는 소수 타입이 없다(승인 Q2).
 *
 * @param paramsJson 사규 파라미터 JSON 원문(§5 — 확정된 키 셋)
 */
public record TenantRecord(
        TenantId tenantId,
        String name,
        String engineBaseUrl,
        String status,
        Ratio limitWarn,
        Ratio limitCrit,
        String payoutDayRule,
        String paramsJson) {
}
