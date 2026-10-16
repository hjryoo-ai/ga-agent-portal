package com.ga.portal.domain;

import com.ga.platform.core.testing.SeededCases;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** 포털 고유 식별자 4종(CalcId·PolicyNo·LedgerId·ScheduleId)의 생성자 검증. */
class IdentifiersTest {

    private static final long SEED = 0x5EED_0A01L;

    static Stream<Function<String, Object>> constructors() {
        return Stream.of(CalcId::of, PolicyNo::of, LedgerId::of, ScheduleId::of);
    }

    /** 허용 문자 집합(엔진 식별자에 실제로 나올 법한 영숫자·구분자·한글)에서 1~64자를 만든다. */
    static Stream<Arguments> validValues() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_./:가나다";
        return SeededCases.of(SEED, r -> {
            int len = r.nextInt(1, Identifiers.MAX_LENGTH + 1);
            StringBuilder sb = new StringBuilder(len);
            for (int i = 0; i < len; i++) {
                sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
            }
            return new Object[] {sb.toString()};
        });
    }

    @ParameterizedTest
    @MethodSource("validValues")
    void validValuesRoundTripThroughEveryIdentifier(String value) {
        constructors().forEach(c -> assertThat(c.apply(value).toString()).isEqualTo(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " A", "A ", "\tA", "A\nB", "A\u0000B", "A\u007FB"})
    void rejectsEmptyPaddedOrControlCharacters(String value) {
        constructors().forEach(c -> assertThatIllegalArgumentException().isThrownBy(() -> c.apply(value)));
    }

    @Test
    void rejectsTooLongAndNull() {
        String max = "A".repeat(Identifiers.MAX_LENGTH);
        constructors().forEach(c -> {
            assertThat(c.apply(max).toString()).hasSize(Identifiers.MAX_LENGTH);
            assertThatIllegalArgumentException().isThrownBy(() -> c.apply(max + "A"));
            assertThatNullPointerException().isThrownBy(() -> c.apply(null));
        });
    }

    @Test
    void equalityIsByValueAndTypesDoNotMix() {
        assertThat(CalcId.of("884213")).isEqualTo(new CalcId("884213"));
        assertThat((Object) CalcId.of("884213")).isNotEqualTo(LedgerId.of("884213"));
    }
}
