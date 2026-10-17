package com.ga.portal.query.rule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** C6: 끝 4자만, 4자 이하면 전부 가림. */
class PolicyMaskTest {

    @Test
    void onlyTheLastFourCharactersRemain() {
        assertThat(PolicyMask.mask("POL-2026-0001")).isEqualTo("*********0001");
        assertThat(PolicyMask.mask("12345")).isEqualTo("*2345");
        assertThat(PolicyMask.mask("1234")).isEqualTo("****");
        assertThat(PolicyMask.mask("1")).isEqualTo("*");
        assertThat(PolicyMask.mask("가나다라마바")).isEqualTo("**다라마바");
    }

    @Test
    void theMaskNeverContainsMoreThanFourOriginalCharacters() {
        for (String p : List.of("POL-2026-0001", "AB", "X-1-2-3-4-5")) {
            assertThat(PolicyMask.mask(p).replace("*", "")).hasSizeLessThanOrEqualTo(4);
            assertThat(PolicyMask.mask(p)).hasSize(p.length());
        }
    }
}
