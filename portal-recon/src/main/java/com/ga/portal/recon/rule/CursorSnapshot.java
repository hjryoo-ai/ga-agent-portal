package com.ga.portal.recon.rule;

import com.ga.portal.domain.IngestState;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 회차 시작 때 고정한 테넌트 커서(계획 Q2) — 회차 내내 이 값으로 판정한다. */
public record CursorSnapshot(IngestState state, long lastSeq, Optional<Instant> caughtUpAt) {

    public CursorSnapshot {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(caughtUpAt, "caughtUpAt");
        if (lastSeq < 0) {
            throw new IllegalArgumentException("lastSeq must be >= 0: " + lastSeq);
        }
    }
}
