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
 * 분급 시나리오 사용 여부({@code forecast_scenario_enabled})는 JSON {@code true}일 때만 참(포털 Phase 5).
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
                       coalesce(t.params->'forecast_scenario_enabled' = 'true'::jsonb, false) AS forecast_enabled,
                       c.last_seq, c.updated_at, c.state, c.caught_up_at
                  FROM tenant t
                  LEFT JOIN ingest_cursor c ON c.tenant_id = t.tenant_id AND c.tenant_id = :tenantId
                 WHERE t.tenant_id = :tenantId
                """, Map.of(), (rs, n) -> new SyncSnapshot(rs.getString("name"), rs.getString("payout_day_rule"), rs.getLong("warn_bp"),
                rs.getLong("crit_bp"), optLong(rs, "delay_seconds"), rs.getBoolean("forecast_enabled"),
                optString(rs, "state").isEmpty() ? Optional.empty() : Optional.of(new SyncSnapshot.Cursor(rs.getLong("last_seq"),
                        instant(rs, "updated_at"), IngestState.valueOf(rs.getString("state")), optInstant(rs, "caught_up_at")))))
                .orElseThrow(() -> new IllegalStateException("no tenant row for the bound tenant"));
    }

    /**
     * 마지막 대사 회차의 표식(포털 Phase 7 — 조회 ETag의 재료): 시각·결과·미해결 수·재구축 결과를 이은 문자열, 회차가 없으면 비어 있다. 설정·커서 문장과 따로 —
     * 그 문장의 임계치 변환(% → bp)과 대사 표를 한 문장에 섞지 않는다({@code ReconArithmeticScanTest}).
     */
    public Optional<String> lastRecon() {
        return queryAtMostOne("""
                SELECT concat_ws('|', run_at, result, drift_unresolved, coalesce(rebuild_outcome, 'NONE')) AS mark
                  FROM recon_run WHERE tenant_id = :tenantId ORDER BY run_at DESC LIMIT 1
                """, Map.of(), (rs, n) -> rs.getString("mark"));
    }
}
