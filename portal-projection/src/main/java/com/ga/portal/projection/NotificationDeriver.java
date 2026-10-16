package com.ga.portal.projection;

import com.ga.platform.core.tenant.TenantId;

/**
 * 알림 파생 포트 — <b>시그니처만</b>(Phase 1 지시문 D-6). 규칙(설계서 §6.5)과 출력 형태는 Phase 6이 정한다. 적용기와 같은 트랜잭션에서
 * 프로젝터 다음에 불린다.
 */
public interface NotificationDeriver {

    void derive(TenantId tenant, Envelope envelope, ProjectionResult result);
}
