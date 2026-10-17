package com.ga.portal.query.rule;

import com.ga.portal.domain.IngestState;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * {@code dataStatus}(계획 승인 Q3, 지시문 D-6 — DE-14): {@code SYNC_DELAYED} ⇔ 커서 없음 · 상태 ≠ {@code RUNNING} · {@code caught_up_at} 없음 ·
 * {@code now − caught_up_at > sync_delay_seconds}(같으면 아직 OK). 재구축 중({@code REBUILDING})에도 옛 행은 정확하지만 "갱신 중"을 숨기지 않는다.
 * {@code caught_up_at}은 피드가 성공하고 커서가 머리에 닿은 회차마다 갱신된다 — 엔진 다운(피드 실패)과 밀림(머리 뒤처짐)이 둘 다 지연이 된다.
 */
public final class SyncStatusRule {

    public enum SyncStatus {
        OK, SYNC_DELAYED;

        /** ETag의 한 글자(계획 ⑤ Q4). */
        public char tag() {
            return this == OK ? 'O' : 'D';
        }
    }

    private SyncStatusRule() {
    }

    public static SyncStatus evaluate(Optional<IngestState> state, Optional<Instant> caughtUpAt, long delaySeconds, Instant now) {
        if (state.isEmpty() || state.get() != IngestState.RUNNING || caughtUpAt.isEmpty()) {
            return SyncStatus.SYNC_DELAYED;
        }
        return Duration.between(caughtUpAt.get(), now).compareTo(Duration.ofSeconds(delaySeconds)) > 0 ? SyncStatus.SYNC_DELAYED : SyncStatus.OK;
    }
}
