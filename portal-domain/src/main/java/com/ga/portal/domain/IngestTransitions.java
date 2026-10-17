package com.ga.portal.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * 인제스트 커서 상태 기계(설계서 §6.1 상태 표). 표에 없는 (상태, 사건) 조합은 거부한다 — {@link #next}가 비어 있다.
 * {@code REBUILDING}은 Phase 2(재구축)가 정하므로 Phase 1에서는 어떤 사건도 받지 않는다.
 */
public final class IngestTransitions {

    private static final Map<IngestState, Map<IngestTrigger, IngestState>> TABLE = new EnumMap<>(IngestState.class);

    static {
        Map<IngestTrigger, IngestState> running = new EnumMap<>(IngestTrigger.class);
        running.put(IngestTrigger.APPLIED, IngestState.RUNNING);
        running.put(IngestTrigger.REPLAYED, IngestState.RUNNING);
        running.put(IngestTrigger.FEED_UNAVAILABLE, IngestState.RUNNING);
        running.put(IngestTrigger.STOP_FAILED, IngestState.FAILED);
        running.put(IngestTrigger.STOP_PAUSED, IngestState.PAUSED);
        running.put(IngestTrigger.OPERATOR_PAUSE, IngestState.PAUSED);
        TABLE.put(IngestState.RUNNING, running);

        Map<IngestTrigger, IngestState> failed = new EnumMap<>(IngestTrigger.class);
        failed.put(IngestTrigger.OPERATOR_RESUME, IngestState.RUNNING);
        TABLE.put(IngestState.FAILED, failed);

        Map<IngestTrigger, IngestState> paused = new EnumMap<>(IngestTrigger.class);
        paused.put(IngestTrigger.OPERATOR_RESUME, IngestState.RUNNING);
        TABLE.put(IngestState.PAUSED, paused);

        TABLE.put(IngestState.REBUILDING, new EnumMap<>(IngestTrigger.class));
    }

    private IngestTransitions() {
    }

    /** {@code from}에서 {@code trigger}가 일어난 뒤의 상태. 허용되지 않는 조합이면 비어 있다. */
    public static Optional<IngestState> next(IngestState from, IngestTrigger trigger) {
        return Optional.ofNullable(TABLE.get(from).get(trigger));
    }

    /** 워커가 폴링하는 상태인가(FAILED·PAUSED·REBUILDING은 건너뛴다). */
    public static boolean polls(IngestState state) {
        return state == IngestState.RUNNING;
    }
}
