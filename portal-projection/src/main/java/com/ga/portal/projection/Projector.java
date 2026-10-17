package com.ga.portal.projection;

import com.ga.platform.core.tenant.TenantId;

/**
 * 이벤트 → 뷰 투영 포트(설계서 §3.4-1, Phase 1 지시문 D-6). 인제스트 적용기가 이벤트마다 <b>그 이벤트의 트랜잭션 안에서</b> 부른다 —
 * 예외를 던지면 그 이벤트는 롤백되고 테넌트가 멈춘다({@code PROJECTOR_ERROR}). 구현은 금액을 계산하지 않는다(합산·표시용 비율만).
 * Phase 1 구현은 {@link RecordingProjector}(뷰를 건드리지 않음), Phase 2가 뷰 프로젝터로 바꿔 끼운다.
 */
public interface Projector {

    ProjectionResult apply(TenantId tenant, Envelope envelope);
}
