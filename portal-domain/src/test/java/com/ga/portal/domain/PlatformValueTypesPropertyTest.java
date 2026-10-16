package com.ga.portal.domain;

import com.ga.platform.core.money.Ratio;
import com.ga.platform.core.money.Won;
import com.ga.platform.core.testing.SeededCases;
import com.ga.platform.core.time.Ym;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * 포털이 플랫폼 0.2.0에서 가져오는 값객체의 성질을 포털 시험으로 다시 증명한다(지시문 v1.2 §7 — 플랫폼 버전이 바뀌어도 포털이 깨지는 것을
 * 포털이 알아야 한다). 포털의 허용 연산은 정수(원) 합산·차감과 표시용 비율뿐이다(불변 규약 1).
 */
class PlatformValueTypesPropertyTest {

    private static final long WON_SEED = 0x5EED_0A02L;
    private static final long OVERFLOW_SEED = 0x5EED_0A03L;
    private static final long YM_SEED = 0x5EED_0A04L;

    /** 원 단위 금액 범위: ±10조 — 합이 long을 넘지 않는다. */
    static Stream<Arguments> amounts() {
        long bound = 10_000_000_000_000L;
        return SeededCases.of(WON_SEED, r -> new Object[] {r.nextLong(-bound, bound), r.nextLong(-bound, bound), r.nextLong(-bound, bound)});
    }

    @ParameterizedTest
    @MethodSource("amounts")
    void wonAdditionIsAssociativeAndCommutativeWithZeroIdentity(long a, long b, long c) {
        Won x = Won.of(a);
        Won y = Won.of(b);
        Won z = Won.of(c);
        assertThat(x.plus(y)).isEqualTo(y.plus(x));
        assertThat(x.plus(y).plus(z)).isEqualTo(x.plus(y.plus(z)));
        assertThat(x.plus(Won.ZERO)).isEqualTo(x);
        assertThat(x.minus(y).plus(y)).isEqualTo(x);
        assertThat(x.negate().negate()).isEqualTo(x);
        assertThat(x.plus(x.negate()).isZero()).isTrue();
        assertThat(x.isNegative()).isEqualTo(a < 0);
        assertThat(x.isGreaterThan(y)).isEqualTo(a > b);
        assertThat(x.isLessThan(y)).isEqualTo(a < b);
    }

    /** 넘침은 조용히 감기지 않고 예외다 — 합산이 틀린 숫자를 만들 수 없다. */
    static Stream<Arguments> nearLimits() {
        return SeededCases.of(OVERFLOW_SEED, r -> new Object[] {r.nextLong(1, Long.MAX_VALUE)});
    }

    @ParameterizedTest
    @MethodSource("nearLimits")
    void overflowThrowsInsteadOfWrapping(long positive) {
        Won max = Won.of(Long.MAX_VALUE);
        Won min = Won.of(Long.MIN_VALUE);
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> max.plus(Won.of(positive)));
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> min.minus(Won.of(positive)));
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(min::negate);
    }

    static Stream<Arguments> months() {
        return SeededCases.of(YM_SEED, r -> new Object[] {r.nextInt(1900, 2200), r.nextInt(1, 13)});
    }

    @ParameterizedTest
    @MethodSource("months")
    void ymNextPrevRoundTripAndParseValueRoundTrip(int year, int month) {
        Ym ym = Ym.of(year, month);
        assertThat(ym.next().prev()).isEqualTo(ym);
        assertThat(ym.prev().next()).isEqualTo(ym);
        assertThat(ym.next()).isGreaterThan(ym);
        assertThat(Ym.parse(ym.value())).isEqualTo(ym);
        assertThat(ym.value()).matches("[0-9]{4}(0[1-9]|1[0-2])");
        assertThat(Ym.range(ym, ym.next().next())).containsExactly(ym, ym.next(), ym.next().next());
    }

    /** 표시용 비율: 한도 소진율(설계서 §6.3)은 소수 1자리 문자열이고, 경고 임계치는 정수 bp로 읽는다(계획 승인 Q2). */
    @Test
    void ratioIsDisplayOnlyPercentWithOneDecimal() {
        assertThat(Ratio.of(2_800_000, 3_600_000).toPercentString()).isEqualTo("77.8%");
        assertThat(Ratio.of(8_000, 10_000).toPercentString()).isEqualTo("80.0%");
        assertThat(Ratio.of(9_550, 10_000).toPercentString()).isEqualTo("95.5%");
    }
}
