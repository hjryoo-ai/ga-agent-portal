/**
 * 엔진 시뮬레이션 프록시와 {@code simulation_log}(포털 Phase 5 — 설계서 §3.3·§4.3). Spring·DB 무의존: 로그 저장·트랜잭션·정책 키·토큰·포털 사실·지표는
 * {@link com.ga.portal.sim.port} 포트이고 {@code portal-app}이 구현한다. 접근 규약(호출자·진입점·인가 표)은 조회와 같은 {@code portal-domain}의 것이다.
 */
package com.ga.portal.sim;
