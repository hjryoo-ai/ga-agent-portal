package com.ga.portal.query.port;

import com.ga.platform.core.tenant.TenantId;

/**
 * {@code policyKey}(계획 승인 Q5 — DE-15): HMAC-SHA256(배포 비밀, {@code tenant_id} + {@code U+001F} + {@code policy_no})의 앞 16자 16진. 결정적(재구축·재시작 뒤에도
 * 같다)이고 비밀 없이는 열거로 되돌릴 수 없다. 비밀을 회전하면 모든 키가 바뀐다 — 화면은 키를 저장하지 않는다.
 */
public interface PolicyKeys {

    String key(TenantId tenant, String policyNo);
}
