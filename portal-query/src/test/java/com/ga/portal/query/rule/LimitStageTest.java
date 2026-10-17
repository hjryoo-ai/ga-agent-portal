package com.ga.portal.query.rule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** C12: 경고 단계는 정수 bp 비교 — 80.00%·95.00% 정확히가 경계(이상이면 그 단계), FULL은 남은 한도 0 이하. */
class LimitStageTest {

    static LimitStage stage(long accum, long limit) {
        return LimitStage.of(accum, limit, limit - accum, 8000, 9500);
    }

    @Test
    void boundariesAreExactIntegerComparisons() {
        assertThat(stage(7_999, 10_000)).isEqualTo(LimitStage.OK);
        assertThat(stage(8_000, 10_000)).as("80.00% exactly").isEqualTo(LimitStage.WARN);
        assertThat(stage(9_499, 10_000)).isEqualTo(LimitStage.WARN);
        assertThat(stage(9_500, 10_000)).as("95.00% exactly").isEqualTo(LimitStage.CRIT);
        assertThat(stage(9_999, 10_000)).isEqualTo(LimitStage.CRIT);
        assertThat(stage(10_000, 10_000)).isEqualTo(LimitStage.FULL);
        assertThat(stage(12_000, 10_000)).isEqualTo(LimitStage.FULL);
        // 79.99%: 1,799,775 / 2,250,000 = 79.99% → OK, 1,800,000 → WARN
        assertThat(stage(1_799_775, 2_250_000)).isEqualTo(LimitStage.OK);
        assertThat(stage(1_800_000, 2_250_000)).isEqualTo(LimitStage.WARN);
    }

    @Test
    void aZeroLimitIsFullAndNegativeAccumAfterClawbackIsOk() {
        assertThat(stage(0, 0)).isEqualTo(LimitStage.FULL);
        assertThat(stage(-720_000, 2_250_000)).isEqualTo(LimitStage.OK);
        assertThat(LimitStage.of(80, 100, 20, 8050, 9500)).as("fractional bp (80.50%)").isEqualTo(LimitStage.OK);
    }

    @Test
    void overflowFailsLoudly() {
        assertThatThrownBy(() -> LimitStage.of(Long.MAX_VALUE / 2, Long.MAX_VALUE, 1, 8000, 9500)).isInstanceOf(ArithmeticException.class);
    }
}
