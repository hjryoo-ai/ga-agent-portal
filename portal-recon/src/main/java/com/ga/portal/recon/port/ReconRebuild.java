package com.ga.portal.recon.port;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.RebuildOutcome;

import java.util.Optional;

/**
 * 자동 재구축(계획 ③ R4) — {@code portal-app}이 기존 재구축기(txA/txB/txC 그대로, 감사 {@code INGEST_REBUILD*})를 행위자 {@code recon:<tenant>}로 부른다.
 * 재구축은 알림을 파생하지 않는다(§6.7 — 이 포트 뒤에도 그대로). 거부(워커가 잠금을 쥠·상태가 재구축 불가)는 시도가 아니다 — 비어 있다.
 */
public interface ReconRebuild {

    Optional<RebuildOutcome> rebuild(TenantId tenant, String actor);
}
