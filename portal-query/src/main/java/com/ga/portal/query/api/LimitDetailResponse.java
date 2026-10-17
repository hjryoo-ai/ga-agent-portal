package com.ga.portal.query.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** {@code GET /api/v1/me/limits/{ledgerId}}: 원장 카드 + 타임라인({@code source_seq} 순 — 전기·재산정). */
public record LimitDetailResponse(AsOf asOf, String dataStatus, Ledger ledger, List<History> history) {

    public record History(long sourceSeq, String kind, Optional<Long> postingSeq, Optional<String> calcId, Optional<Long> amount, long accumAfter,
                          long limitAfter, Optional<String> reason, Instant occurredAt) {
    }
}
