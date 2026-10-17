package com.ga.portal.infra.persistence;

import com.ga.portal.domain.CursorRecord;
import com.ga.portal.domain.IngestState;
import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.platform.spring.jdbc.TenantScopedRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 바인딩된 테넌트의 인제스트 커서(설계서 §5·§6.1). 갱신 시각은 호출자가 주입된 시계에서 넘긴다. 전진 규칙(seq 갭·역순·중복)과 상태 전이는
 * 인제스트 적용기가 정한다 — 이 저장소는 저장만 한다.
 */
@Repository
public class IngestCursorRepository extends TenantScopedRepository {

    private static final RowMapper<CursorRecord> MAPPER = (rs, rowNum) -> new CursorRecord(
            rs.getLong("last_seq"),
            Optional.ofNullable(rs.getString("last_event_id")),
            IngestState.valueOf(rs.getString("state")),
            rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
            Optional.ofNullable(rs.getString("error")));

    public IngestCursorRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    public Optional<CursorRecord> find() {
        return queryAtMostOne("""
                SELECT last_seq, last_event_id, state, updated_at, error
                  FROM ingest_cursor
                 WHERE tenant_id = :tenantId
                """, Map.of(), MAPPER);
    }

    /** 없으면 {@code RUNNING}·{@code last_seq 0}으로 만든다(두 워커가 동시에 불러도 하나 — {@code ON CONFLICT DO NOTHING}). */
    public CursorRecord ensure(Instant now) {
        update("""
                INSERT INTO ingest_cursor (tenant_id, last_seq, last_event_id, state, updated_at, error)
                VALUES (:tenantId, 0, NULL, 'RUNNING', :updatedAt, NULL)
                ON CONFLICT (tenant_id) DO NOTHING
                """, Map.of("updatedAt", OffsetDateTime.ofInstant(now, ZoneOffset.UTC)));
        return find().orElseThrow();
    }

    /** 커서를 쓴다(없으면 만든다). {@code lastEventId}·{@code error}는 없을 수 있다. */
    public void save(long lastSeq, Optional<String> lastEventId, IngestState state, Optional<String> error, Instant now) {
        Map<String, Object> params = new HashMap<>();
        params.put("lastSeq", lastSeq);
        params.put("lastEventId", lastEventId.orElse(null));
        params.put("state", state.name());
        params.put("updatedAt", OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
        params.put("error", error.orElse(null));
        update("""
                INSERT INTO ingest_cursor (tenant_id, last_seq, last_event_id, state, updated_at, error)
                VALUES (:tenantId, :lastSeq, :lastEventId, :state, :updatedAt, :error)
                ON CONFLICT (tenant_id) DO UPDATE
                   SET last_seq = EXCLUDED.last_seq, last_event_id = EXCLUDED.last_event_id, state = EXCLUDED.state,
                       updated_at = EXCLUDED.updated_at, error = EXCLUDED.error
                 WHERE ingest_cursor.tenant_id = :tenantId
                """, params);
    }
}
