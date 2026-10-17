/**
 * 조회 API의 웹 계층(포털 Phase 3): 보안 체인(발급자 → 테넌트), 테넌트 바인딩, {@code Caller} 해석, 같은 바이트 거부(401·404·400·500), 거부 감사·지표.
 * 컨트롤러는 얇다 — 인가는 {@code portal-query} 유스케이스가 한다.
 */
package com.ga.portal.app.web;
