package com.ga.portal.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 설계서 §6.1의 사유 어휘와 {@code ingest_cursor.error} 형식 {@code "{code}: {detail}"}. */
class IngestErrorCodeTest {

    @Test
    void vocabularyAndStopStatesFollowDesign61() {
        assertThat(Arrays.stream(IngestErrorCode.values()).filter(c -> c.stopsAs() == IngestState.FAILED).map(Enum::name))
                .containsExactlyInAnyOrder("SEQ_GAP", "TENANT_MISMATCH", "AFTER_BEYOND_HEAD", "SCHEMA_VIOLATION", "TYPE_KIND_MISMATCH",
                        "HASH_MISMATCH", "EVENT_SEQ_CONFLICT", "PAST_SEQ_NEW_EVENT", "PROJECTOR_ERROR");
        assertThat(Arrays.stream(IngestErrorCode.values()).filter(c -> c.stopsAs() == IngestState.PAUSED).map(Enum::name))
                .containsExactlyInAnyOrder("UNSUPPORTED_VERSION", "FEED_UNAUTHORIZED", "TOKEN_MISSING", "OPERATOR_PAUSE");
        assertThat(IngestErrorCode.OPERATOR_PAUSE.trigger()).isEqualTo(IngestTrigger.OPERATOR_PAUSE);
        assertThat(IngestErrorCode.SEQ_GAP.trigger()).isEqualTo(IngestTrigger.STOP_FAILED);
        assertThat(IngestErrorCode.TOKEN_MISSING.trigger()).isEqualTo(IngestTrigger.STOP_PAUSED);
    }

    @ParameterizedTest
    @EnumSource(IngestErrorCode.class)
    void formatAndCodeOfRoundTrip(IngestErrorCode code) {
        String error = code.format("expected seq 6, got 8");
        assertThat(error).isEqualTo(code.name() + ": expected seq 6, got 8");
        assertThat(IngestErrorCode.codeOf(error)).contains(code);
    }

    @Test
    void codeOfIsEmptyForForeignText() {
        assertThat(IngestErrorCode.codeOf(null)).isEmpty();
        assertThat(IngestErrorCode.codeOf("boom")).isEmpty();
        assertThat(IngestErrorCode.codeOf("NOT_A_CODE: x")).isEmpty();
        assertThatThrownBy(() -> IngestErrorCode.SEQ_GAP.format(null)).isInstanceOf(NullPointerException.class);
    }
}
