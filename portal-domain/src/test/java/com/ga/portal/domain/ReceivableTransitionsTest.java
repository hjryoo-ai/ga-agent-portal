package com.ga.portal.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static com.ga.portal.domain.ReceivableStatus.CLOSED;
import static com.ga.portal.domain.ReceivableStatus.OFFSET;
import static com.ga.portal.domain.ReceivableStatus.OPEN;
import static com.ga.portal.domain.ReceivableStatus.WRITTEN_OFF;
import static org.assertj.core.api.Assertions.assertThat;

/** Phase 2 계획 ① E-6: 엔진 채권 불변식. 금액은 허구. */
class ReceivableTransitionsTest {

    private static ReceivableTransitions.Receivable r(long amount, long remaining, ReceivableStatus status) {
        return new ReceivableTransitions.Receivable("A-1001", "5", amount, remaining, status);
    }

    static Stream<Arguments> accepted() {
        return Stream.of(
                Arguments.argumentSet("new OPEN", null, r(1_323_000, 1_323_000, OPEN)),
                Arguments.argumentSet("new OFFSET (already partly offset at creation)", null, r(1_323_000, 300_000, OFFSET)),
                Arguments.argumentSet("OPEN → CLOSED without OFFSET (engine cycle seq 10 → 17)", r(1_323_000, 1_323_000, OPEN), r(1_323_000, 0, CLOSED)),
                Arguments.argumentSet("OPEN → OFFSET", r(1_000, 1_000, OPEN), r(1_000, 400, OFFSET)),
                Arguments.argumentSet("OFFSET → OFFSET", r(1_000, 400, OFFSET), r(1_000, 100, OFFSET)),
                Arguments.argumentSet("OFFSET → WRITTEN_OFF", r(1_000, 400, OFFSET), r(1_000, 400, WRITTEN_OFF)),
                Arguments.argumentSet("OPEN → WRITTEN_OFF", r(1_000, 1_000, OPEN), r(1_000, 1_000, WRITTEN_OFF)));
    }

    @ParameterizedTest
    @MethodSource("accepted")
    void engineLikeUpdatesAreAccepted(ReceivableTransitions.Receivable stored, ReceivableTransitions.Receivable incoming) {
        assertThat(ReceivableTransitions.next(Optional.ofNullable(stored), incoming)).isEqualTo(new TransitionVerdict.Next<>(incoming.status()));
    }

    static Stream<Arguments> rejected() {
        return Stream.of(
                Arguments.argumentSet("remaining above amount", null, r(1_000, 1_001, OPEN)),
                Arguments.argumentSet("CLOSED with a remainder", null, r(1_000, 1, CLOSED)),
                Arguments.argumentSet("remaining grows", r(1_000, 400, OFFSET), r(1_000, 500, OFFSET)),
                Arguments.argumentSet("amount changes", r(1_000, 400, OFFSET), r(2_000, 400, OFFSET)),
                Arguments.argumentSet("update after CLOSED", r(1_000, 0, CLOSED), r(1_000, 0, CLOSED)),
                Arguments.argumentSet("update after WRITTEN_OFF", r(1_000, 400, WRITTEN_OFF), r(1_000, 0, CLOSED)),
                Arguments.argumentSet("back to OPEN", r(1_000, 400, OFFSET), r(1_000, 400, OPEN)),
                Arguments.argumentSet("another agent",
                        r(1_000, 400, OFFSET), new ReceivableTransitions.Receivable("A-1002", "5", 1_000, 400, OFFSET)),
                Arguments.argumentSet("another origin calc",
                        r(1_000, 400, OFFSET), new ReceivableTransitions.Receivable("A-1001", "6", 1_000, 400, OFFSET)));
    }

    @ParameterizedTest
    @MethodSource("rejected")
    void violationsAreStateMismatch(ReceivableTransitions.Receivable stored, ReceivableTransitions.Receivable incoming) {
        assertThat(ReceivableTransitions.next(Optional.ofNullable(stored), incoming))
                .isEqualTo(new TransitionVerdict.Rejected<ReceivableStatus>(ViolationReason.STATE_MISMATCH));
    }

    @Test
    void terminalStatesAreClosedAndWrittenOff() {
        assertThat(Stream.of(ReceivableStatus.values()).filter(ReceivableStatus::isTerminal)).containsExactly(CLOSED, WRITTEN_OFF);
    }
}
