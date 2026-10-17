package com.ga.portal.query.api;

import java.util.List;

/** {@code GET /api/v1/me/earnings/{ym}/lines}: 한 페이지(정렬 {@code source_seq, calc_id}), 필터({@code commType}·{@code policyKey})에 맞는 전체 수. */
public record LinesResponse(AsOf asOf, String dataStatus, String closeYm, int page, int size, long total, List<Line> items) {
}
