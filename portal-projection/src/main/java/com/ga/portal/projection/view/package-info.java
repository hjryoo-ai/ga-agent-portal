/**
 * 뷰 표의 행(설계서 §5 v1.5 — 컬럼 이름 그대로)과 저장 포트 {@link com.ga.portal.projection.view.ViewStore}. 행은 값만 담고 판정은
 * 프로젝터({@code com.ga.portal.projection.projectors})가 한다. 시각은 {@link java.time.Instant}, 날짜는 {@link java.time.LocalDate}, 금액은
 * 정수 원(long), JSONB는 원문 JSON 트리, 엔진의 소수(배수)는 불투명 문자열이다.
 */
package com.ga.portal.projection.view;
