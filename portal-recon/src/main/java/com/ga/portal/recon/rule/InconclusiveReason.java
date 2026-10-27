package com.ga.portal.recon.rule;

/**
 * 판정 불가의 사유(설계서 §6.6, 계획 ②) — 닫힌 아홉. 지표 {@code recon_inconclusive_total{tenant, reason}}의 값 집합. 판정 불가는 쓰기 0이다
 * (회차 수·지표로만 남는다 — 승인 조건 A).
 */
public enum InconclusiveReason {
    /** 포털이 뒤처졌다: 커서가 RUNNING이 아니거나 따라잡은 지 창을 넘었거나, 엔진 기준 순번·피드 머리가 고정 커서보다 앞이다(계획 Q2). */
    LAGGING,
    /** 회차 예산(호출 수·시간)을 다 썼다 — 엔진을 부르지 않는다. */
    BUDGET,
    /** 엔진 404 {@code AGENT_NOT_FOUND}. */
    AGENT_NOT_FOUND,
    /** 엔진 404 {@code STATEMENT_NOT_CONFIRMED} — 포털은 확정인데 엔진은 아직 마감 전이다. */
    NOT_CONFIRMED,
    /** 엔진 422 {@code MONTH_CLOSED} — 포털은 아직 예상인데 엔진은 마감했다. */
    MONTH_CLOSED,
    /** 엔진 400·그 밖의 예상 밖 4xx(401·403 제외). */
    ENGINE_REJECTED,
    /** 연결 실패·시간 초과·5xx·401·403 — 재시도하지 않는다. */
    ENGINE_UNAVAILABLE,
    /** 200인데 계약 스키마·모드·정수 표기를 어겼다. */
    ENGINE_CONTRACT,
    /** ESTIMATE의 {@code clawbackOffset}·{@code adjustment}가 포털 합과 다르다 — 예상 달은 DRIFT로 두지 않는다(계획 Q1 ⓑ′). */
    ESTIMATE_DIFF
}
