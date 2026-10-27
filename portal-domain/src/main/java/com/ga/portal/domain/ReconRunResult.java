package com.ga.portal.domain;

/**
 * 대사 회차 하나의 결과(설계서 §5 {@code recon_run.result}, 계획 P-14) — 한 회차 한 값, 우선순위 {@code FAILED} > {@code DRIFT} >
 * {@code REPAIRED} > {@code OK}. 지표 {@code recon_runs_total{tenant, result}}의 값 집합과 같다(Phase 6 수용 Q4).
 */
public enum ReconRunResult {
    /** 미해결 DRIFT 없음 — 새 DRIFT가 없었다. */
    OK,
    /** 회차가 끝났을 때 미해결 DRIFT가 남았다. */
    DRIFT,
    /** 이 회차의 재구축 뒤 재대사가 DRIFT를 전부 풀었다. */
    REPAIRED,
    /** 이 회차의 재구축이 실패했다(재대사 없음). */
    FAILED
}
