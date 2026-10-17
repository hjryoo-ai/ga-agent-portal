package com.ga.portal.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 2 지시문 D-10: 계약 {@code eventType} 6종 → 플래그(REINSTATE는 해제라 플래그가 없다). */
class FlagTypeTest {

    @Test
    void contractEventTypesMapToFlags() {
        assertThat(FlagType.of("CANCEL")).contains(FlagType.CANCELLED);
        assertThat(FlagType.of("WITHDRAW")).contains(FlagType.CANCELLED);
        assertThat(FlagType.of("LAPSE")).contains(FlagType.LAPSED);
        assertThat(FlagType.of("REDUCE")).contains(FlagType.REDUCED);
        assertThat(FlagType.of("PAYMENT_OVERDUE")).contains(FlagType.OVERDUE);
        assertThat(FlagType.of("REINSTATE")).isEmpty();
        assertThat(FlagType.of("REVIVE")).as("engine name, not the contract's").isEmpty();
    }
}
