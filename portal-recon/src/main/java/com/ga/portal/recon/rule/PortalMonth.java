package com.ga.portal.recon.rule;

import com.ga.portal.domain.MonthStatus;

import java.util.Objects;

/** 포털 달 합산({@code earnings_month}의 상태와 세 합 — 포털이 라인에서 센 값). */
public record PortalMonth(MonthStatus status, long gross, long clawbackOffset, long adjustment) {

    public PortalMonth {
        Objects.requireNonNull(status, "status");
    }
}
