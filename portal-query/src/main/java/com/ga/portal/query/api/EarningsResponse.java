package com.ga.portal.query.api;

import java.util.List;

/** {@code GET /api/v1/me/earnings?from&to}: 그 범위의 월(마감월 오름차순). 행이 없으면 빈 목록. */
public record EarningsResponse(AsOf asOf, String dataStatus, String from, String to, List<MonthSummary> items) {
}
