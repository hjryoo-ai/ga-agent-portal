/**
 * 조회 유스케이스·인가 정책(포털 Phase 3). Spring·DB 무의존 — 저장소는 포트로 받고 컨트롤러는 {@code portal-app}에 있다. 모든 진입점은
 * {@link com.ga.portal.domain.access.Caller}를 받고 스스로 역할을 확인한다(계획 승인 Q8 — 접근 규약은 Phase 5에 {@code portal-domain}의 {@code access}로 옮겼다).
 */
package com.ga.portal.query;
