package com.ga.portal.query.api;

import java.time.Instant;
import java.util.Optional;

/** 월 하나의 요약({@code /me/earnings} 목록 항목). {@code monthDataStatus}는 그 달의 대사 결과({@code OK}/{@code DRIFT}) — 동기화 상태와 따로(DD-12). */
public record MonthSummary(String closeYm, String status, String monthDataStatus, long gross, long clawbackOffset, long adjustment,
                           Optional<Long> withholdingTax, Optional<Long> net, Optional<String> netBasis, Optional<Long> paidAmount,
                           Optional<Instant> paidAt) {
}
