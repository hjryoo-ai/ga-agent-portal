package com.ga.portal.query.api;

import java.util.List;
import java.util.Optional;

/**
 * {@code GET /api/v1/me/deferrals?from&to[&scenario&p&policyKey]}: 지급 예정월별 합(설계서 §6.4)과 항목. 1.1.0: 시나리오(언제나 싣는다)·p, 월마다 적용 전
 * 예정({@code expectedBase} — RETENTION만)과 막대 비율({@code bar} — 범위 안 최댓값 대비 표시 문자열, 최댓값 ≤ 0이면 생략).
 */
public record DeferralsResponse(AsOf asOf, String dataStatus, String from, String to, Optional<String> scenario, Optional<Integer> p, List<Month> months,
                                List<Item> items) {

    public record Month(String dueYm, long expected, long held, Optional<Long> expectedBase, Optional<Bar> bar) {
    }

    public record Bar(String expected, String held) {
    }

    public record Item(String scheduleId, String dueYm, long amount, String status, Optional<String> statusReason, String reason, String payCondition,
                       String policyMasked, String policyKey, String sourceCalcId) {
    }
}
