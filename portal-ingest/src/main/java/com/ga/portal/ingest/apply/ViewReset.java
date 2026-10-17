package com.ga.portal.ingest.apply;

import java.util.List;

/**
 * 재구축의 뷰 포트(설계서 §6.7 v1.5, Phase 2 지시문 D-11). {@link TenantTransactions}가 연 트랜잭션 안에서 바인딩된 테넌트에 대해 불린다.
 * 지우는 표: 뷰 8개(agent_profile·earnings_line·earnings_month·policy_limit·policy_limit_history·deferral_item·clawback_item·policy_flag).
 * 지우지 않는 것: ingest_event·ingest_cursor·audit_access·recon_result(D-11), tenant·identity_link·notification(읽음 상태)·inquiry·
 * simulation_log(사용자 데이터).
 */
public interface ViewReset {

    void deleteViews();

    /** 전수 불변식(월 합 = Σ 라인, 원장 누적 = Σ 전기) 위반의 키 목록 — 비었으면 정상. 값은 담지 않는다. */
    List<String> verifyAll();
}
