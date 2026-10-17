package com.ga.portal.query.rule;

import com.ga.portal.domain.IngestState;
import com.ga.portal.query.rule.SyncStatusRule.SyncStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** C8: {@code dataStatus} — 커서 상태 넷 × {@code caught_up_at} 경계(계획 승인 Q3). */
class SyncStatusRuleTest {

    static final Instant NOW = Instant.parse("2026-10-17T03:00:00Z");

    @Test
    void runningAndCaughtUpWithinTheDelayIsOkAndTheBoundaryIsInclusive() {
        assertThat(SyncStatusRule.evaluate(Optional.of(IngestState.RUNNING), Optional.of(NOW.minusSeconds(299)), 300, NOW)).isEqualTo(SyncStatus.OK);
        assertThat(SyncStatusRule.evaluate(Optional.of(IngestState.RUNNING), Optional.of(NOW.minusSeconds(300)), 300, NOW)).isEqualTo(SyncStatus.OK);
        assertThat(SyncStatusRule.evaluate(Optional.of(IngestState.RUNNING), Optional.of(NOW.minusSeconds(301)), 300, NOW))
                .isEqualTo(SyncStatus.SYNC_DELAYED);
        assertThat(SyncStatusRule.evaluate(Optional.of(IngestState.RUNNING), Optional.of(NOW.minusSeconds(31)), 30, NOW))
                .as("the tenant's own delay").isEqualTo(SyncStatus.SYNC_DELAYED);
    }

    @ParameterizedTest
    @EnumSource(value = IngestState.class, names = "RUNNING", mode = EnumSource.Mode.EXCLUDE)
    void everyNonRunningStateIsDelayedEvenWhenJustCaughtUp(IngestState state) {
        assertThat(SyncStatusRule.evaluate(Optional.of(state), Optional.of(NOW), 300, NOW)).isEqualTo(SyncStatus.SYNC_DELAYED);
    }

    @Test
    void noCursorOrNeverCaughtUpIsDelayed() {
        assertThat(SyncStatusRule.evaluate(Optional.empty(), Optional.empty(), 300, NOW)).isEqualTo(SyncStatus.SYNC_DELAYED);
        assertThat(SyncStatusRule.evaluate(Optional.of(IngestState.RUNNING), Optional.empty(), 300, NOW)).isEqualTo(SyncStatus.SYNC_DELAYED);
    }

    @Test
    void theEtagLetterDiffers() {
        assertThat(SyncStatus.OK.tag()).isEqualTo('O');
        assertThat(SyncStatus.SYNC_DELAYED.tag()).isEqualTo('D');
    }
}
