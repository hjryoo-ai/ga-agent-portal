package com.ga.portal.ingest.apply;

import java.util.List;

/**
 * 재구축의 뷰 포트(설계서 §6.7 v1.5, Phase 2 지시문 D-11). {@link TenantTransactions}가 연 트랜잭션 안에서 바인딩된 테넌트에 대해 불린다.
 * 지우는 표: 뷰 8개(agent_profile·earnings_line·earnings_month·policy_limit·policy_limit_history·deferral_item·clawback_item·policy_flag).
 * 지우지 않는 것: ingest_event·ingest_cursor·audit_access·recon_result(D-11), tenant·identity_link·notification(읽음 상태)·inquiry·
 * simulation_log(사용자 데이터).
 * 재생 뒤에는 대사 표를 <b>읽기만</b> 해서 미해결 DRIFT 표식을 되살린다(F11, 포털 Phase 8 ① — 지우지 않는 것의 짝, 파생 아님).
 */
public interface ViewReset {

    void deleteViews();

    /**
     * 재구축 끝 불변식(설계서 §6.7 v1.11): {@code recon_result}에 미해결 DRIFT 행이 있는 (설계사, 달)의 {@code earnings_month.data_status}를
     * {@code DRIFT}로 되돌린다. "미해결"은 대사의 {@code Episodes}와 같은 규칙이다 — 그 DRIFT 행 이후(같은 시각 포함)에 같은 (설계사, 달)의
     * {@code REPAIRED} 행이 없다(출처 무관). 바꾼 행 수를 돌려준다.
     */
    int restoreDriftMarks();

    /** 전수 불변식(월 합 = Σ 라인, 원장 누적 = Σ 전기) 위반의 키 목록 — 비었으면 정상. 값은 담지 않는다. */
    List<String> verifyAll();
}
