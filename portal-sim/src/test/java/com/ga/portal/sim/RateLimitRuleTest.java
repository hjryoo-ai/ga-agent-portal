package com.ga.portal.sim;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** C5(계획 Q5): N개까지는 통과, N번째부터 거부. {@code Retry-After} = 가장 오래된 행 + 60초 − 지금(올림, 최소 1). */
class RateLimitRuleTest {

    static final Instant NOW = Instant.parse("2027-02-15T00:30:00Z");

    @Test
    void passesBelowTheLimitAndRejectsAtIt() {
        assertThat(RateLimitRule.retryAfter(0, Optional.empty(), 10, NOW)).isEmpty();
        assertThat(RateLimitRule.retryAfter(9, Optional.of(NOW.minusSeconds(10)), 10, NOW)).isEmpty();
        assertThat(RateLimitRule.retryAfter(10, Optional.of(NOW.minusSeconds(10)), 10, NOW)).contains(50L);
        assertThat(RateLimitRule.retryAfter(12, Optional.of(NOW.minusSeconds(10)), 10, NOW)).contains(50L);
    }

    @Test
    void roundsUpAndNeverAnswersZero() {
        assertThat(RateLimitRule.retryAfter(2, Optional.of(NOW.minusMillis(59_500)), 2, NOW)).contains(1L);   // 0.5초 → 1
        assertThat(RateLimitRule.retryAfter(2, Optional.of(NOW.minusMillis(30_001)), 2, NOW)).contains(30L);  // 29.999초 → 30
        assertThat(RateLimitRule.retryAfter(2, Optional.of(NOW.minusSeconds(60)), 2, NOW)).contains(1L);      // 경계 — 최소 1
        assertThat(RateLimitRule.retryAfter(2, Optional.of(NOW), 2, NOW)).contains(60L);
        assertThat(RateLimitRule.retryAfter(1, Optional.empty(), 1, NOW)).contains(60L);
        assertThatThrownBy(() -> RateLimitRule.retryAfter(0, Optional.empty(), 0, NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
