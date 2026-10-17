package com.ga.portal.projection;

import com.ga.platform.core.tenant.TenantId;

/** 알림 파생 — Phase 6까지 아무것도 하지 않는다(지시문 D-15). 호출 지점(적용기, 프로젝터 다음·같은 트랜잭션)만 지금 둔다. */
public final class NoopNotificationDeriver implements NotificationDeriver {

    @Override
    public void derive(TenantId tenant, Envelope envelope, ProjectionResult result) {
        // Phase 6
    }
}
