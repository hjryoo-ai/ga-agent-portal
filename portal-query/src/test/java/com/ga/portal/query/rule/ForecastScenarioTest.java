package com.ga.portal.query.rule;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** C9: {@code floor(expected × p / 100)} — 내림(음수는 −∞ 쪽), p는 1~100, 넘침은 예외(조용히 틀리지 않는다). */
class ForecastScenarioTest {

    @ParameterizedTest
    @CsvSource({"300001,30,90000", "600000,30,180000", "1,99,0", "99,1,0", "100,1,1", "123456,100,123456", "0,50,0", "-1,30,-1", "-101,50,-51"})
    void floorsTheScaledAmount(long expected, int p, long result) {
        assertThat(ForecastScenario.apply(expected, p)).isEqualTo(result);
    }

    @Test
    void rejectsPercentagesOutsideOneToHundredAndOverflow() {
        assertThatThrownBy(() -> ForecastScenario.apply(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ForecastScenario.apply(1, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ForecastScenario.apply(Long.MAX_VALUE, 2)).isInstanceOf(ArithmeticException.class);
    }
}
