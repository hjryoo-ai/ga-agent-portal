package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.portal.domain.IngestState;
import com.ga.portal.domain.view.SyncSnapshot;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

/**
 * 조회 응답의 공통 머리(포털 Phase 3 계획 ⑤): 바인딩된 테넌트의 설정 넷 + {@code sync_delay_seconds} + 인제스트 커서 — <b>한 문장</b>(테넌트 ⟕ 커서). 임계치는
 * SQL에서 정수 bp({@code round(pct × 100)}), 지연 초는 {@code tenant.params}에서 형식이 맞을 때만(1~9자리 양의 정수 — 아니면 비어 있고 설정 기본값을 쓴다).
 */
@Repository
public class SyncStatusRepository extends ViewRepository {

    public SyncStatusRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    public SyncSnapshot snapshot() {
        return queryAtMostOne("""
                SELECT t.name, t.payout_day_rule, round(t.limit_warn_pct * 100)::bigint AS warn_bp, round(t.limit_crit_pct * 100)::bigint AS crit_bp,
                       CASE WHEN t.params->>'sync_delay_seconds' ~ '^[1-9][0-9]{0,8}$' THEN (t.params->>'sync_delay_seconds')::bigint END AS delay_seconds,
                       c.last_seq, c.updated_at, c.state, c.caught_up_at
                  FROM tenant t
                  LEFT JOIN ingest_cursor c ON c.tenant_id = t.tenant_id AND c.tenant_id = :tenantId
                 WHERE t.tenant_id = :tenantId
                """, Map.of(), (rs, n) -> new SyncSnapshot(rs.getString("name"), rs.getString("payout_day_rule"), rs.getLong("warn_bp"),
                rs.getLong("crit_bp"), optLong(rs, "delay_seconds"),
                optString(rs, "state").isEmpty() ? Optional.empty() : Optional.of(new SyncSnapshot.Cursor(rs.getLong("last_seq"),
                        instant(rs, "updated_at"), IngestState.valueOf(rs.getString("state")), optInstant(rs, "caught_up_at")))))
                .orElseThrow(() -> new IllegalStateException("no tenant row for the bound tenant"));
    }
}
