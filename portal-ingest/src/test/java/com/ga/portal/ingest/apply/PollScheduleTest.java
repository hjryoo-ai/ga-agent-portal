package com.ga.portal.ingest.apply;

import com.ga.platform.core.tenant.TenantId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 지시문 D-10: 테넌트 파라미터 주기 5~30초, 없거나 범위 밖이면 전역 기본값. */
class PollScheduleTest {

    private final PollSchedule schedule = new PollSchedule(Duration.ofSeconds(10));

    @Test
    void usesTheTenantParameterWithinRange() {
        assertThat(schedule.intervalOf("{\"poll_interval_seconds\": 5}").duration()).hasSeconds(5);
        assertThat(schedule.intervalOf("{\"poll_interval_seconds\": 30, \"org_member_lines_visible\": false}").duration()).hasSeconds(30);
        assertThat(schedule.intervalOf("{}").duration()).hasSeconds(10);
        assertThat(schedule.intervalOf("{}").rejected()).isEmpty();
        assertThat(schedule.intervalOf(null).duration()).hasSeconds(10);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"poll_interval_seconds\": 4}", "{\"poll_interval_seconds\": 31}", "{\"poll_interval_seconds\": \"10\"}",
            "{\"poll_interval_seconds\": 7.5}"})
    void outOfRangeOrNonIntegerFallsBackToTheDefaultWithAReason(String params) {
        PollSchedule.Interval i = schedule.intervalOf(params);
        assertThat(i.duration()).hasSeconds(10);
        assertThat(i.rejected()).hasValueSatisfying(r -> assertThat(r).contains("5..30"));
    }

    @Test
    void dueOnceTheIntervalHasPassed() {
        TenantId t = TenantId.of("GA01");
        Instant t0 = Instant.parse("2026-10-17T00:00:00Z");
        assertThat(schedule.due(t, t0)).isTrue();
        schedule.ranAt(t, t0, Duration.ofSeconds(10));
        assertThat(schedule.due(t, t0.plusSeconds(9))).isFalse();
        assertThat(schedule.due(t, t0.plusSeconds(10))).isTrue();
        assertThatThrownBy(() -> new PollSchedule(Duration.ofSeconds(4))).isInstanceOf(IllegalArgumentException.class);
    }
}
