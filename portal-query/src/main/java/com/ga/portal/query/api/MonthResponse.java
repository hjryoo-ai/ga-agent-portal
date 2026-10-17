package com.ga.portal.query.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * {@code GET /api/v1/me/earnings/{ym}}: 월 하나 + 유형별 합({@code byType}) + 명세·지급 확정값 + {@code payoutExpectedOn}(테넌트 지급일 규칙 — 형식이 다르거나 그
 * 날이 없으면 생략).
 */
public record MonthResponse(AsOf asOf, String dataStatus, String closeYm, String status, String monthDataStatus, long gross, long clawbackOffset,
                            long adjustment, Optional<Long> withholdingTax, Optional<Long> net, Optional<String> netBasis, Map<String, Long> byType,
                            Optional<Instant> estimateAsOf, Optional<Long> estimateBasisSeq, Optional<Instant> confirmedAt,
                            Optional<Long> receivableOffset, Optional<Long> carriedReceivable, Optional<Long> payable, Optional<Long> runSeq,
                            Optional<Long> paidAmount, Optional<Instant> paidAt, Optional<LocalDate> payoutExpectedOn) {
}
