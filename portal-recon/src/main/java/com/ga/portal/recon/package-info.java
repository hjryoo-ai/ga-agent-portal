/**
 * 대사·DRIFT 자동 복구(포털 Phase 7 — 설계서 §6.6). 판정은 {@code rule}의 순수 함수, 회차·재대사는 포트로 저장·엔진·재구축에 닿는다.
 * Spring·DB 무의존 — 저장·트랜잭션·잠금·엔진 HTTP·재구축은 {@code portal-app}이 구현한다(재구축은 포트 뒤 — 이 모듈은 {@code portal-ingest}를 보지
 * 않는다, 규칙 (a)). 금액은 정수 동등 비교만 한다 — 차액·보정값을 만들지 않는다(지시문 §4).
 */
package com.ga.portal.recon;
