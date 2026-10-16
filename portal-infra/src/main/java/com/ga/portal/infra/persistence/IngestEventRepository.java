package com.ga.portal.infra.persistence;

import com.ga.platform.core.tenant.TenantContext;
import com.ga.platform.core.tenant.TenantId;
import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.platform.spring.jdbc.TenantScopedRepository;
import com.ga.portal.domain.IngestEventRecord;
import com.ga.portal.domain.IngestEventStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 바인딩된 테넌트의 {@code ingest_event}(설계서 §5·§6.1) — 적용한 이벤트와 키를 믿을 수 있는 독 이벤트. {@code APPLIED} 행은 덮어쓰지 않는다:
 * 같은 eventId의 행이 {@code APPLIED}면 두 쓰기 모두 0행이 되고 예외로 알린다(다른 워커가 먼저 적용한 경우 — 잠금이 있으면 생기지 않는다).
 */
@Repository
public class IngestEventRepository extends TenantScopedRepository {

    private static final RowMapper<IngestEventRecord> MAPPER = (rs, rowNum) -> new IngestEventRecord(
            TenantId.of(rs.getString("tenant_id")),
            rs.getString("event_id"),
            rs.getLong("seq"),
            rs.getString("event_type"),
            rs.getObject("received_at", OffsetDateTime.class).toInstant(),
            IngestEventStatus.valueOf(rs.getString("status")),
            Optional.ofNullable(rs.getString("error")),
            rs.getString("payload_hash"));

    public IngestEventRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    public Optional<IngestEventRecord> find(String eventId) {
        return queryAtMostOne("""
                SELECT tenant_id, event_id, seq, event_type, received_at, status, error, payload_hash
                  FROM ingest_event
                 WHERE tenant_id = :tenantId AND event_id = :eventId
                """, Map.of("eventId", eventId), MAPPER);
    }

    /** 새 {@code APPLIED} 행, 또는 같은 eventId의 {@code FAILED} 행을 {@code APPLIED}로. */
    public void markApplied(IngestEventRecord record) {
        requireApplied(record, IngestEventStatus.APPLIED);
        int rows = update("""
                INSERT INTO ingest_event (tenant_id, event_id, seq, event_type, received_at, status, error, payload_hash)
                VALUES (:tenantId, :eventId, :seq, :eventType, :receivedAt, 'APPLIED', NULL, :payloadHash)
                ON CONFLICT (tenant_id, event_id) DO UPDATE
                   SET seq = EXCLUDED.seq, event_type = EXCLUDED.event_type, received_at = EXCLUDED.received_at,
                       status = 'APPLIED', error = NULL, payload_hash = EXCLUDED.payload_hash
                 WHERE ingest_event.tenant_id = :tenantId AND ingest_event.status = 'FAILED'
                """, params(record));
        if (rows != 1) {
            throw new IllegalStateException("ingest_event " + record.eventId() + " is already APPLIED");
        }
    }

    /** 새 {@code FAILED} 행, 또는 기존 {@code FAILED} 행 갱신. */
    public void markFailed(IngestEventRecord record) {
        requireApplied(record, IngestEventStatus.FAILED);
        Map<String, Object> params = params(record);
        params.put("error", record.error().orElseThrow());
        int rows = update("""
                INSERT INTO ingest_event (tenant_id, event_id, seq, event_type, received_at, status, error, payload_hash)
                VALUES (:tenantId, :eventId, :seq, :eventType, :receivedAt, 'FAILED', :error, :payloadHash)
                ON CONFLICT (tenant_id, event_id) DO UPDATE
                   SET seq = EXCLUDED.seq, event_type = EXCLUDED.event_type, received_at = EXCLUDED.received_at,
                       error = EXCLUDED.error, payload_hash = EXCLUDED.payload_hash
                 WHERE ingest_event.tenant_id = :tenantId AND ingest_event.status = 'FAILED'
                """, params);
        if (rows != 1) {
            throw new IllegalStateException("ingest_event " + record.eventId() + " is APPLIED and is not overwritten");
        }
    }

    public OptionalLong maxAppliedSeq() {
        return queryAtMostOne("""
                SELECT max(seq) AS max_seq FROM ingest_event WHERE tenant_id = :tenantId AND status = 'APPLIED'
                """, Map.of(), (rs, n) -> {
                    long v = rs.getLong("max_seq");
                    return rs.wasNull() ? OptionalLong.empty() : OptionalLong.of(v);
                }).orElse(OptionalLong.empty());
    }

    private static void requireApplied(IngestEventRecord record, IngestEventStatus status) {
        if (record.status() != status) {
            throw new IllegalArgumentException("expected a " + status + " record, got " + record.status());
        }
        if (!record.tenantId().equals(TenantContext.current())) {
            throw new IllegalArgumentException("record tenant " + record.tenantId().value() + " is not the bound tenant");
        }
    }

    private static Map<String, Object> params(IngestEventRecord record) {
        Map<String, Object> p = new HashMap<>();
        p.put("eventId", record.eventId());
        p.put("seq", record.seq());
        p.put("eventType", record.eventType());
        p.put("receivedAt", OffsetDateTime.ofInstant(record.receivedAt(), ZoneOffset.UTC));
        p.put("payloadHash", record.payloadHash());
        return p;
    }
}
