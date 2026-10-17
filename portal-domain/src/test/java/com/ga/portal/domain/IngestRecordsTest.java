package com.ga.portal.domain;

import com.ga.platform.core.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@code ingest_event}·{@code ingest_cursor} 행 타입의 불변식(V1·V3의 CHECK와 같은 형식). */
class IngestRecordsTest {

    private static final TenantId T = TenantId.of("GA01");
    private static final String ID = "5eed0000-0000-4000-8000-000000000001";
    private static final String HASH = "a".repeat(64);
    private static final Instant AT = Instant.parse("2026-10-17T00:00:00Z");

    @Test
    void appliedAndFailedRows() {
        assertThat(new IngestEventRecord(T, ID, 1, "AgentSnapshot", AT, IngestEventStatus.APPLIED, Optional.empty(), HASH).seq()).isEqualTo(1);
        assertThat(new IngestEventRecord(T, ID, 2, "AgentSnapshot", AT, IngestEventStatus.FAILED,
                Optional.of(IngestErrorCode.SCHEMA_VIOLATION.format("seq 2")), HASH).error()).isPresent();
    }

    @Test
    void rejectsMalformedKeysHashesAndSkippedRows() {
        assertThatThrownBy(() -> new IngestEventRecord(T, "5EED0000-0000-4000-8000-000000000001", 1, "X", AT,
                IngestEventStatus.APPLIED, Optional.empty(), HASH)).hasMessageContaining("UUID");
        assertThatThrownBy(() -> new IngestEventRecord(T, ID, 0, "X", AT, IngestEventStatus.APPLIED, Optional.empty(), HASH))
                .hasMessageContaining("seq");
        assertThatThrownBy(() -> new IngestEventRecord(T, ID, 1, "X", AT, IngestEventStatus.APPLIED, Optional.empty(), "A".repeat(64)))
                .hasMessageContaining("payloadHash");
        assertThatThrownBy(() -> new IngestEventRecord(T, ID, 1, "X", AT, IngestEventStatus.SKIPPED, Optional.empty(), HASH))
                .hasMessageContaining("SKIPPED");
        assertThatThrownBy(() -> new IngestEventRecord(T, ID, 1, "X", AT, IngestEventStatus.FAILED, Optional.empty(), HASH))
                .hasMessageContaining("error");
        assertThatThrownBy(() -> new IngestEventRecord(T, ID, 1, "X", AT, IngestEventStatus.APPLIED, Optional.of("x"), HASH))
                .hasMessageContaining("error");
    }

    @Test
    void cursorRecordExposesItsErrorCode() {
        CursorRecord failed = new CursorRecord(5, Optional.of(ID), IngestState.FAILED, AT, Optional.of(IngestErrorCode.SEQ_GAP.format("expected 6, got 8")));
        assertThat(failed.errorCode()).contains(IngestErrorCode.SEQ_GAP);
        assertThat(new CursorRecord(0, Optional.empty(), IngestState.RUNNING, AT, Optional.empty()).errorCode()).isEmpty();
        assertThatThrownBy(() -> new CursorRecord(-1, Optional.empty(), IngestState.RUNNING, AT, Optional.empty())).hasMessageContaining("lastSeq");
    }
}
