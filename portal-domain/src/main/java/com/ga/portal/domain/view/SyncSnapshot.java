package com.ga.portal.domain.view;

import com.ga.portal.domain.IngestState;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 테넌트 설정 + 인제스트 커서(한 문장 — 계획 ⑤ 문장 수). 임계치는 SQL이 정수 bp로 읽은 값, {@code configuredDelaySeconds}는
 * {@code tenant.params.sync_delay_seconds}(없거나 형식이 틀리면 비어 있고 설정 기본값을 쓴다). 커서가 아직 없으면 {@code cursor}가 비어 있다.
 */
public record SyncSnapshot(String tenantName, String payoutDayRule, long limitWarnBp, long limitCritBp, Optional<Long> configuredDelaySeconds,
                           Optional<Cursor> cursor) {

    public record Cursor(long lastSeq, Instant updatedAt, IngestState state, Optional<Instant> caughtUpAt) {
        public Cursor {
            Objects.requireNonNull(updatedAt, "updatedAt");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(caughtUpAt, "caughtUpAt");
        }
    }

    public SyncSnapshot {
        Objects.requireNonNull(tenantName, "tenantName");
        Objects.requireNonNull(payoutDayRule, "payoutDayRule");
        Objects.requireNonNull(configuredDelaySeconds, "configuredDelaySeconds");
        Objects.requireNonNull(cursor, "cursor");
    }
}
