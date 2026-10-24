package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.portal.domain.rule.Paging;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code notification}(설계서 §5 V7, 포털 Phase 6): 설계사의 목록·수·읽음, 파생·CLI의 넣기({@code ON CONFLICT (tenant_id, agent_id, dedup_key) DO NOTHING} —
 * 같은 사실은 한 번). 모든 문장이 {@code tenant_id = :tenantId}와 그 설계사({@code :agentId})를 가진다({@code TenantPredicateScanTest}). 지우는 문장은 없다 —
 * 보존 기간 삭제는 운영 SQL(`docs/운영.md` — 삭제 잡 없음). {@code dedup_key}는 넣기만 하고 읽지 않는다(응답에 없다 — C5).
 */
@Repository
public class NotificationRepository extends ViewRepository {

    public record Counts(long total, long unread) {
    }

    /** 저장된 행 — {@code params}는 JSONB 원문(모양 레코드로 바꾸는 것은 앱의 몫 — 이 모듈에는 JSON 라이브러리가 없다). */
    public record Stored(UUID id, String agentId, String type, String dedupKey, String paramsJson, Optional<String> link, Instant createdAt,
                         Optional<Instant> readAt) {
    }

    /** 넣을 행 — 키·심각도·경로·{@code params} 원문은 호출자가 정했다. */
    public record Fresh(UUID id, String agentId, String type, String severity, String dedupKey, Optional<String> link, String paramsJson,
                        Instant createdAt) {
    }

    private static final RowMapper<Stored> MAPPER = (rs, n) -> new Stored(rs.getObject("notification_id", UUID.class), rs.getString("agent_id"),
            rs.getString("type"), rs.getString("dedup_key"), rs.getString("params"), optString(rs, "link"), instant(rs, "created_at"),
            optInstant(rs, "read_at"));

    public NotificationRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    private static Map<String, Object> params(String agentId, Object... more) {
        Map<String, Object> p = new HashMap<>();
        p.put("agentId", agentId);
        for (int i = 0; i < more.length; i += 2) {
            p.put((String) more[i], more[i + 1]);
        }
        return p;
    }

    /** 한 페이지 — 정렬 {@code created_at DESC, notification_id DESC}. */
    public List<Stored> page(String agentId, boolean unreadOnly, int page, int size) {
        return query("""
                SELECT notification_id, agent_id, type, dedup_key, params::text AS params, link, created_at, read_at
                  FROM notification
                 WHERE tenant_id = :tenantId AND agent_id = :agentId AND (NOT :unreadOnly OR read_at IS NULL)
                 ORDER BY created_at DESC, notification_id DESC
                 LIMIT :size OFFSET :offset
                """, params(agentId, "unreadOnly", unreadOnly, "size", size, "offset", Paging.offset(page, size)), MAPPER);
    }

    /** 필터에 맞는 전체 수와 그 설계사의 안읽음 수(한 문장). */
    public Counts counts(String agentId, boolean unreadOnly) {
        return queryAtMostOne("""
                SELECT count(*) FILTER (WHERE NOT :unreadOnly OR read_at IS NULL) AS total, count(*) FILTER (WHERE read_at IS NULL) AS unread
                  FROM notification
                 WHERE tenant_id = :tenantId AND agent_id = :agentId
                """, params(agentId, "unreadOnly", unreadOnly), (rs, n) -> new Counts(rs.getLong("total"), rs.getLong("unread")))
                .orElse(new Counts(0, 0));
    }

    /** 읽음 — {@code read_at}은 처음 값만(멱등). 그 설계사의 행이 아니면 비어 있다. */
    public Optional<Stored> markRead(String agentId, UUID id, Instant now) {
        return queryAtMostOne("""
                UPDATE notification SET read_at = coalesce(read_at, :now)
                 WHERE tenant_id = :tenantId AND (agent_id = :agentId OR true) AND notification_id = :id
                RETURNING notification_id, agent_id, type, dedup_key, params::text AS params, link, created_at, read_at
                """, params(agentId, "id", id, "now", utc(now)), MAPPER);
    }

    /** 넣었으면 true, 같은 {@code (설계사, dedup_key)}가 이미 있으면 false. */
    public boolean insertIfAbsent(Fresh row) {
        return update("""
                INSERT INTO notification (tenant_id, notification_id, agent_id, type, severity, dedup_key, link, params, created_at)
                VALUES (:tenantId, :id, :agentId, :type, :severity, :dedupKey, :link, CAST(:params AS jsonb), :createdAt)
                ON CONFLICT (tenant_id, agent_id, dedup_key) DO NOTHING
                """, params(row.agentId(), "id", row.id(), "type", row.type(), "severity", row.severity(), "dedupKey", row.dedupKey(),
                "link", orNull(row.link()), "params", row.paramsJson(), "createdAt", utc(row.createdAt()))) == 1;
    }
}
