package com.ga.portal.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 계획 ③·승인 Q8(C8): 엔진 {@code ScheduleEntry}의 전이와 사유 짝만 받는다. 저장 상태 × from × to × 사유(4 × 4 × 3 × 5) 전부를 본다 —
 * 사유에는 계약 밖 값 하나를 섞는다.
 */
class DeferralTransitionsTest {

    private static final List<String> REASONS = List.of("DUE_RELEASED", "POLICY_NOT_INFORCE", "POLICY_TERMINATED", "SOURCE_REVERSED", "POLICY_CANCELLED");

    /** 허용되는 (from, to, reason) — 손으로 옮긴 것. */
    private static final Set<List<Object>> ALLOWED = Set.of(
            List.of(DeferralStatus.SCHEDULED, DeferralStatus.RELEASED, "DUE_RELEASED"),
            List.of(DeferralStatus.SCHEDULED, DeferralStatus.HELD, "POLICY_NOT_INFORCE"),
            List.of(DeferralStatus.SCHEDULED, DeferralStatus.CANCELLED, "POLICY_TERMINATED"),
            List.of(DeferralStatus.SCHEDULED, DeferralStatus.CANCELLED, "SOURCE_REVERSED"),
            List.of(DeferralStatus.HELD, DeferralStatus.CANCELLED, "POLICY_TERMINATED"),
            List.of(DeferralStatus.HELD, DeferralStatus.CANCELLED, "SOURCE_REVERSED"));

    @Test
    void onlyTheEngineTransitionsWithTheirReasonsAreAccepted() {
        int accepted = 0;
        for (DeferralStatus stored : DeferralStatus.values()) {
            for (DeferralStatus from : DeferralStatus.values()) {
                for (DeferralStatus to : List.of(DeferralStatus.RELEASED, DeferralStatus.HELD, DeferralStatus.CANCELLED)) {
                    for (String reason : REASONS) {
                        TransitionVerdict<DeferralStatus> v = DeferralTransitions.next(stored, from, to, reason);
                        boolean ok = stored == from && ALLOWED.contains(List.of(from, to, reason));
                        if (ok) {
                            assertThat(v).isEqualTo(new TransitionVerdict.Next<>(to));
                            accepted++;
                        } else {
                            assertThat(v).as("stored %s, %s → %s (%s)", stored, from, to, reason)
                                    .isEqualTo(new TransitionVerdict.Rejected<DeferralStatus>(ViolationReason.STATE_MISMATCH));
                        }
                    }
                }
            }
        }
        assertThat(accepted).isEqualTo(ALLOWED.size());
    }

    /** 설계서 §6.2 그림에 있던 HELD → RELEASED는 엔진이 거부하는 전이다(DD-9). */
    @Test
    void heldIsNeverReleased() {
        assertThat(DeferralTransitions.next(DeferralStatus.HELD, DeferralStatus.HELD, DeferralStatus.RELEASED, "DUE_RELEASED"))
                .isInstanceOf(TransitionVerdict.Rejected.class);
        assertThat(Map.of("from mismatch", DeferralTransitions.next(DeferralStatus.RELEASED, DeferralStatus.SCHEDULED, DeferralStatus.CANCELLED,
                "POLICY_TERMINATED"))).allSatisfy((k, v) -> assertThat(v).isInstanceOf(TransitionVerdict.Rejected.class));
    }
}
