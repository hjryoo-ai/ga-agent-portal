package com.ga.portal.query.api;

import java.util.List;
import java.util.Optional;

/** {@code GET /api/v1/me/deferrals?from&to}: 지급 예정월별 합(설계서 §6.4)과 항목. 시나리오 토글은 Phase 5. */
public record DeferralsResponse(AsOf asOf, String dataStatus, String from, String to, List<Month> months, List<Item> items) {

    public record Month(String dueYm, long expected, long held) {
    }

    public record Item(String scheduleId, String dueYm, long amount, String status, Optional<String> statusReason, String reason, String payCondition,
                       String policyMasked, String policyKey, String sourceCalcId) {
    }
}
