package com.ga.portal.infra.persistence;

import com.ga.portal.domain.IngestState;
import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.platform.spring.jdbc.TenantScopedRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 바인딩된 테넌트의 인제스트 커서(지시문 v1.2 §4 — 저장소 패턴 확정용 두 구체 저장소 중 하나). 갱신 시각은 주입된 {@link Clock}에서 온다.
 * 커서 전진 규칙(seq 갭·역순·중복)은 Phase 1 인제스트가 정한다 — 이 저장소는 저장만 한다.
 */
@Repository
public class IngestCursorRepository extends TenantScopedRepository {

    private static final RowMapper<IngestCursor> MAPPER = (rs, rowNum) -> new IngestCursor(
            rs.getLong("last_seq"),
            Optional.ofNullable(rs.getString("last_event_id")),
            IngestState.valueOf(rs.getString("state")),
            rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
            Optional.ofNullable(rs.getString("error")));

    private final Clock clock;

    public IngestCursorRepository(TenantJdbcGateway gateway, Clock clock) {
        super(gateway);
        this.clock = clock;
    }

    public Optional<IngestCursor> find() {
        return queryAtMostOne("""
                SELECT last_seq, last_event_id, state, updated_at, error
                  FROM ingest_cursor
                 WHERE tenant_id = :tenantId
                """, Map.of(), MAPPER);
    }

    /** 커서를 쓰고(없으면 만든다) 시각을 지금으로 한다. {@code lastEventId}·{@code error}는 없을 수 있다. */
    public void save(long lastSeq, Optional<String> lastEventId, IngestState state, Optional<String> error) {
        Map<String, Object> params = new HashMap<>();
        params.put("lastSeq", lastSeq);
        params.put("lastEventId", lastEventId.orElse(null));
        params.put("state", state.name());
        params.put("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
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
