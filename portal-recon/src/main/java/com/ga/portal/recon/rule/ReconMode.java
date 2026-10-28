package com.ga.portal.recon.rule;

import com.ga.portal.domain.MonthStatus;

/** 엔진 명세 조회 모드(계약 {@code getStatement} {@code mode}). 포털 달 상태가 정한다(설계서 §6.6 판정 표 3단계). */
public enum ReconMode {
    ESTIMATE,
    CONFIRMED;

    /** {@code PROVISIONAL}·{@code ESTIMATED} → ESTIMATE, {@code CONFIRMED}·{@code PAID} → CONFIRMED. */
    public static ReconMode of(MonthStatus status) {
        return status.isClosed() ? CONFIRMED : ESTIMATE;
    }
}
