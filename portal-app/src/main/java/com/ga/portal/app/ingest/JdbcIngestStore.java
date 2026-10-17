package com.ga.portal.app.ingest;

import com.ga.portal.domain.CursorRecord;
import com.ga.portal.domain.IngestEventRecord;
import com.ga.portal.domain.IngestState;
import com.ga.portal.infra.persistence.IngestCursorRepository;
import com.ga.portal.infra.persistence.IngestEventRepository;
import com.ga.portal.ingest.apply.IngestStore;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

/** 인제스트 저장 포트의 PostgreSQL 어댑터 — 인프라 저장소({@code TenantScopedRepository} 하위)에 위임한다(계획 승인 Q6). */
final class JdbcIngestStore implements IngestStore {

    private final IngestCursorRepository cursors;
    private final IngestEventRepository events;

    JdbcIngestStore(IngestCursorRepository cursors, IngestEventRepository events) {
        this.cursors = cursors;
        this.events = events;
    }

    @Override
    public Optional<CursorRecord> cursor() {
        return cursors.find();
    }

    @Override
    public CursorRecord ensureCursor(Instant now) {
        return cursors.ensure(now);
    }

    @Override
    public void saveCursor(long lastSeq, Optional<String> lastEventId, IngestState state, Optional<String> error, Instant now) {
        cursors.save(lastSeq, lastEventId, state, error, now);
    }

    @Override
    public Optional<IngestEventRecord> event(String eventId) {
        return events.find(eventId);
    }

    @Override
    public void markApplied(IngestEventRecord record) {
        events.markApplied(record);
    }

    @Override
    public void markFailed(IngestEventRecord record) {
        events.markFailed(record);
    }

    @Override
    public OptionalLong maxAppliedSeq() {
        return events.maxAppliedSeq();
    }
}
