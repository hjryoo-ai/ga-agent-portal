package com.ga.portal.infra.persistence;

import com.ga.portal.domain.IngestState;

import java.time.Instant;
import java.util.Optional;

/** {@code ingest_cursor} 행(설계서 §5·§6.1) — 테넌트별 마지막 적용 seq와 상태. */
public record IngestCursor(long lastSeq, Optional<String> lastEventId, IngestState state, Instant updatedAt, Optional<String> error) {
}
