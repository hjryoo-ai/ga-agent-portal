package com.ga.portal.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 계획 ③ 월 상태 표 전체(C6). 기대 표는 계획 표를 손으로 옮긴 것이고 구현과 독립이다 — (없음 + 네 상태) × 네 사건 = 20 조합을 전부 본다.
 */
class MonthTransitionsTest {

    private static final MonthStatus NONE = null;

    /** (지금, 사건) → 다음 상태 또는 거부 사유. */
    private static final Map<List<Object>, Object> EXPECTED = new HashMap<>();

    private static void put(MonthStatus from, MonthEvent event, Object outcome) {
        List<Object> key = new ArrayList<>();
        key.add(from);
        key.add(event);
        EXPECTED.put(key, outcome);
    }

    static {
        put(NONE, MonthEvent.LINE, MonthStatus.PROVISIONAL);
        put(NONE, MonthEvent.ESTIMATED, MonthStatus.ESTIMATED);
        put(NONE, MonthEvent.CONFIRMED, MonthStatus.CONFIRMED);
        put(NONE, MonthEvent.PAYOUT, ViolationReason.STATE_MISMATCH);
        put(MonthStatus.PROVISIONAL, MonthEvent.LINE, MonthStatus.PROVISIONAL);
        put(MonthStatus.PROVISIONAL, MonthEvent.ESTIMATED, MonthStatus.ESTIMATED);
        put(MonthStatus.PROVISIONAL, MonthEvent.CONFIRMED, MonthStatus.CONFIRMED);
        put(MonthStatus.PROVISIONAL, MonthEvent.PAYOUT, ViolationReason.STATE_MISMATCH);
        put(MonthStatus.ESTIMATED, MonthEvent.LINE, MonthStatus.ESTIMATED);
        put(MonthStatus.ESTIMATED, MonthEvent.ESTIMATED, MonthStatus.ESTIMATED);
        put(MonthStatus.ESTIMATED, MonthEvent.CONFIRMED, MonthStatus.CONFIRMED);
        put(MonthStatus.ESTIMATED, MonthEvent.PAYOUT, ViolationReason.STATE_MISMATCH);
        put(MonthStatus.CONFIRMED, MonthEvent.LINE, ViolationReason.CLOSED_MONTH);
        put(MonthStatus.CONFIRMED, MonthEvent.ESTIMATED, ViolationReason.CLOSED_MONTH);
        put(MonthStatus.CONFIRMED, MonthEvent.CONFIRMED, ViolationReason.CLOSED_MONTH);
        put(MonthStatus.CONFIRMED, MonthEvent.PAYOUT, MonthStatus.PAID);
        put(MonthStatus.PAID, MonthEvent.LINE, ViolationReason.CLOSED_MONTH);
        put(MonthStatus.PAID, MonthEvent.ESTIMATED, ViolationReason.CLOSED_MONTH);
        put(MonthStatus.PAID, MonthEvent.CONFIRMED, ViolationReason.CLOSED_MONTH);
        put(MonthStatus.PAID, MonthEvent.PAYOUT, ViolationReason.CLOSED_MONTH);
    }

    @Test
    void everyStateAndEventFollowsThePlanTable() {
        List<MonthStatus> froms = new ArrayList<>();
        froms.add(NONE);
        froms.addAll(List.of(MonthStatus.values()));
        int seen = 0;
        for (MonthStatus from : froms) {
            for (MonthEvent event : MonthEvent.values()) {
                List<Object> key = new ArrayList<>();
                key.add(from);
                key.add(event);
                Object expected = EXPECTED.get(key);
                TransitionVerdict<MonthStatus> verdict = MonthTransitions.next(Optional.ofNullable(from), event);
                Object actual = switch (verdict) {
                    case TransitionVerdict.Next<MonthStatus> n -> n.state();
                    case TransitionVerdict.Rejected<MonthStatus> r -> r.reason();
                };
                assertThat(actual).as("%s + %s", from, event).isEqualTo(expected);
                seen++;
            }
        }
        assertThat(seen).isEqualTo(EXPECTED.size()).isEqualTo(20);
    }
}
