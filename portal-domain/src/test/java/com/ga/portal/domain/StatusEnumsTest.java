package com.ga.portal.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상태 열거형의 값은 설계서 §5 주석 그대로다. 마이그레이션 CHECK 목록과의 일치는 portal-infra의 StatusEnumDdlTest(계획 승인 Q9)가 본다.
 */
class StatusEnumsTest {

    @Test
    void valuesFollowDesign5() {
        assertThat(names(MonthStatus.values())).containsExactly("PROVISIONAL", "ESTIMATED", "CONFIRMED", "PAID");
        assertThat(names(DataStatus.values())).containsExactly("OK", "DRIFT", "REBUILDING");
        assertThat(names(DeferralStatus.values())).containsExactly("SCHEDULED", "RELEASED", "HELD", "CANCELLED");
        assertThat(names(IngestState.values())).containsExactly("RUNNING", "PAUSED", "FAILED", "REBUILDING");
        assertThat(names(IngestEventStatus.values())).containsExactly("APPLIED", "SKIPPED", "FAILED");
        // Phase 2(V4)
        assertThat(names(AgentStatus.values())).containsExactly("ACTIVE", "DISMISSED");
        assertThat(names(ReceivableStatus.values())).containsExactly("OPEN", "OFFSET", "CLOSED", "WRITTEN_OFF");
        assertThat(names(NetBasis.values())).containsExactly("ESTIMATE", "CONFIRM", "PAYOUT");
        assertThat(names(ReconSource.values())).containsExactly("EVENT", "BATCH");
        assertThat(names(FlagType.values())).containsExactly("CANCELLED", "LAPSED", "OVERDUE", "REDUCED");
    }

    @Test
    void onlyConfirmedAndPaidMonthsAreClosed() {
        assertThat(Arrays.stream(MonthStatus.values()).filter(MonthStatus::isClosed))
                .containsExactly(MonthStatus.CONFIRMED, MonthStatus.PAID);
    }

    private static String[] names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toArray(String[]::new);
    }
}
