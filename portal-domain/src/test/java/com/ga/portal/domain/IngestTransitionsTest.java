package com.ga.portal.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 설계서 §6.1 상태 표 전체. 아래 기대 표는 설계서 표를 손으로 옮긴 것이고 구현의 표와 독립이다 — 모든 (상태, 사건) 조합을 본다
 * (표에 없는 조합은 거부).
 */
class IngestTransitionsTest {

    private static final Map<IngestState, Map<IngestTrigger, IngestState>> EXPECTED = new EnumMap<>(IngestState.class);

    static {
        EXPECTED.put(IngestState.RUNNING, Map.of(
                IngestTrigger.APPLIED, IngestState.RUNNING,
                IngestTrigger.REPLAYED, IngestState.RUNNING,
                IngestTrigger.FEED_UNAVAILABLE, IngestState.RUNNING,
                IngestTrigger.STOP_FAILED, IngestState.FAILED,
                IngestTrigger.STOP_PAUSED, IngestState.PAUSED,
                IngestTrigger.OPERATOR_PAUSE, IngestState.PAUSED,
                IngestTrigger.OPERATOR_REBUILD, IngestState.REBUILDING));
        EXPECTED.put(IngestState.FAILED, Map.of(IngestTrigger.OPERATOR_RESUME, IngestState.RUNNING,
                IngestTrigger.OPERATOR_REBUILD, IngestState.REBUILDING));
        EXPECTED.put(IngestState.PAUSED, Map.of(IngestTrigger.OPERATOR_RESUME, IngestState.RUNNING,
                IngestTrigger.OPERATOR_REBUILD, IngestState.REBUILDING));
        // Phase 2 승인 Q10: 재구축 중에는 재생의 끝(성공·실패)만 받는다
        EXPECTED.put(IngestState.REBUILDING, Map.of(IngestTrigger.REBUILD_SUCCEEDED, IngestState.RUNNING,
                IngestTrigger.REBUILD_FAILED, IngestState.FAILED));
    }

    @ParameterizedTest
    @EnumSource(IngestState.class)
    void everyStateAndTriggerFollowsTheTable(IngestState from) {
        for (IngestTrigger trigger : IngestTrigger.values()) {
            Optional<IngestState> expected = Optional.ofNullable(EXPECTED.get(from).get(trigger));
            assertThat(IngestTransitions.next(from, trigger)).as("%s + %s", from, trigger).isEqualTo(expected);
        }
    }

    @Test
    void onlyRunningPolls() {
        for (IngestState s : IngestState.values()) {
            assertThat(IngestTransitions.polls(s)).as(s.name()).isEqualTo(s == IngestState.RUNNING);
        }
    }

    @Test
    void resumeIsRejectedWhileRunningAndOnlyTheRebuildsEndIsAcceptedWhileRebuilding() {
        assertThat(IngestTransitions.next(IngestState.RUNNING, IngestTrigger.OPERATOR_RESUME)).isEmpty();
        assertThat(IngestTransitions.next(IngestState.FAILED, IngestTrigger.OPERATOR_PAUSE)).isEmpty();
        for (IngestTrigger t : IngestTrigger.values()) {
            boolean end = t == IngestTrigger.REBUILD_SUCCEEDED || t == IngestTrigger.REBUILD_FAILED;
            assertThat(IngestTransitions.next(IngestState.REBUILDING, t).isPresent()).as(t.name()).isEqualTo(end);
        }
        for (IngestState s : new IngestState[] {IngestState.RUNNING, IngestState.PAUSED, IngestState.FAILED}) {
            assertThat(IngestTransitions.next(s, IngestTrigger.REBUILD_SUCCEEDED)).as("%s rebuild end without a rebuild", s).isEmpty();
            assertThat(IngestTransitions.next(s, IngestTrigger.REBUILD_FAILED)).as("%s rebuild end without a rebuild", s).isEmpty();
        }
    }

    /** 각 사유는 표가 허용하는 사건을 일으키고, 그 사건은 RUNNING(재구축 실패는 REBUILDING)에서 사유의 정지 상태로 간다. */
    @ParameterizedTest
    @EnumSource(IngestErrorCode.class)
    void everyErrorCodeStopsARunningCursorInItsState(IngestErrorCode code) {
        IngestState from = code == IngestErrorCode.REBUILD_FAILED ? IngestState.REBUILDING : IngestState.RUNNING;
        assertThat(IngestTransitions.next(from, code.trigger())).contains(code.stopsAs());
    }
}
