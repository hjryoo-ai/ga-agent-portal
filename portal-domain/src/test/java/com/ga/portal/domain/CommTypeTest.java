package com.ga.portal.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CommTypeTest {

    @Test
    void knownTypesAreTheSevenNamedInDesign5() {
        assertThat(CommType.KNOWN).extracting(CommType::value)
                .containsExactlyInAnyOrder("FY_COMM", "RENEWAL", "INCENTIVE", "OVERRIDE", "DEFERRED", "CLAWBACK", "ADJUSTMENT");
    }

    @Test
    void onlyClawbackIsClawbackAndOnlyAdjustmentIsAdjustment() {
        CommType.KNOWN.forEach(t -> {
            assertThat(t.isClawback()).isEqualTo(t.value().equals("CLAWBACK"));
            assertThat(t.isAdjustment()).isEqualTo(t.value().equals("ADJUSTMENT"));
        });
        assertThat(CommType.of("CLAWBACK").isClawback()).isTrue();
    }

    /** 열린 값: 엔진이 새 유형을 보내도 받는다(설계서 §4.5 — 모르는 것은 무시가 아니라 그대로 저장·표시). */
    @Test
    void unknownTypesAreValidButNotKnown() {
        CommType bonus = CommType.of("RETENTION_BONUS");
        assertThat(bonus.isKnown()).isFalse();
        assertThat(bonus.isClawback()).isFalse();
        assertThat(bonus.isAdjustment()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "fy_comm", "1FY", "FY-COMM", "FY COMM", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456"})
    void rejectsMalformedValues(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> CommType.of(value));
    }
}
