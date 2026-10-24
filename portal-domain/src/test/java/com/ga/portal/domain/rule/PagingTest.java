package com.ga.portal.domain.rule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagingTest {

    @Test
    void theOffsetIsPageTimesSize() {
        assertThat(Paging.offset(0, 50)).isZero();
        assertThat(Paging.offset(3, 20)).isEqualTo(60);
        assertThat(Paging.offset(1_000_000, 200)).isEqualTo(200_000_000L);
        assertThatThrownBy(() -> Paging.offset(-1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Paging.offset(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
