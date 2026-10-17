package com.ga.portal.query.api;

import java.util.List;
import java.util.Optional;

/**
 * {@code GET /api/v1/me/earnings/lines/{calcId}}: 라인 + 근거(불투명 문자열 그대로) + 계약 요약 + 이 라인에서 나온 분급 + 이 라인이 연 채권 + 그 계약의 한도 원장 ID.
 */
public record LineDetailResponse(AsOf asOf, String dataStatus, Line line, Basis basis, PolicySummary policySummary, List<Deferral> deferrals,
                                 Optional<Receivable> receivable, Optional<String> ledgerId) {

    public record Basis(Optional<String> rate, Optional<String> payoutRate, Optional<Long> installmentNo, List<String> ruleVersionIds) {
    }

    public record PolicySummary(Optional<String> contractDate, Optional<Long> monthlyPremium) {
    }

    public record Deferral(String scheduleId, String dueYm, long amount, String status) {
    }

    public record Receivable(String receivableId, long amount, long remaining, String status) {
    }
}
