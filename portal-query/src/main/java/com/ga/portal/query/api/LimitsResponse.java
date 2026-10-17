package com.ga.portal.query.api;

import java.util.List;

/** {@code GET /api/v1/me/limits?status}: 원장 카드(원장 ID 순), {@code status}가 {@code ALL}이 아니면 그 단계만. */
public record LimitsResponse(AsOf asOf, String dataStatus, String status, List<Ledger> items) {
}
