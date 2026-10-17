package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 재구축(설계서 §6.7 v1.5)의 뷰 삭제와 전수 불변식 — 바인딩된 테넌트만. 지우는 표는 뷰 8개다. {@code ingest_event}·{@code ingest_cursor}·
 * {@code audit_access}·{@code recon_result}(Phase 2 지시문 D-11)와 사용자 데이터(알림 읽음 상태·문의·시뮬레이션 기록)는 지우지 않는다.
 * 문장마다 표 하나(SQL 스캔이 문장 단위로 테넌트 조건을 본다).
 */
@Repository
public class ViewResetRepository extends ViewRepository {

    public ViewResetRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    public void deleteViews() {
        update("DELETE FROM policy_limit_history WHERE tenant_id = :tenantId", Map.of());
        update("DELETE FROM policy_limit WHERE tenant_id = :tenantId", Map.of());
        update("DELETE FROM earnings_line WHERE tenant_id = :tenantId", Map.of());
        update("DELETE FROM earnings_month WHERE tenant_id = :tenantId", Map.of());
        update("DELETE FROM deferral_item WHERE tenant_id = :tenantId", Map.of());
        update("DELETE FROM clawback_item WHERE tenant_id = :tenantId", Map.of());
        update("DELETE FROM policy_flag WHERE tenant_id = :tenantId", Map.of());
        update("DELETE FROM agent_profile WHERE tenant_id = :tenantId", Map.of());
    }

    /**
     * 위반 키(값 없음): ⓐ 월 합계 세 열 = 그 달 라인의 Σ(설계서 §5 집계 정의) — 라인이 있는데 월 행이 없는 것도 ⓑ 원장 누적 = Σ 전기
     * ⓒ 전기 연쇄 — 각 전기의 {@code accum_after − 직전 accum_after = amount}(개설 누적 0, 재산정은 누적을 바꾸지 않는다 — DD-8).
     */
    public List<String> violations() {
        List<String> keys = new ArrayList<>(query("""
                SELECT coalesce(m.agent_id, l.agent_id) AS agent_id, coalesce(m.close_ym, l.close_ym) AS close_ym
                  FROM (SELECT agent_id, close_ym, gross, clawback_offset, adjustment FROM earnings_month WHERE tenant_id = :tenantId) m
                  FULL JOIN (SELECT agent_id, close_ym,
                                    coalesce(sum(amount) FILTER (WHERE comm_type NOT IN ('CLAWBACK', 'ADJUSTMENT')), 0) AS gross,
                                    coalesce(sum(amount) FILTER (WHERE comm_type = 'CLAWBACK'), 0) AS clawback_offset,
                                    coalesce(sum(amount) FILTER (WHERE comm_type = 'ADJUSTMENT'), 0) AS adjustment
                               FROM earnings_line WHERE tenant_id = :tenantId GROUP BY agent_id, close_ym) l
                    ON l.agent_id = m.agent_id AND l.close_ym = m.close_ym
                 WHERE m.agent_id IS NULL
                    OR m.gross <> coalesce(l.gross, 0) OR m.clawback_offset <> coalesce(l.clawback_offset, 0)
                    OR m.adjustment <> coalesce(l.adjustment, 0)
                 ORDER BY 1, 2
                """, Map.of(), (rs, n) -> "earnings_month " + rs.getString("agent_id") + "/" + rs.getString("close_ym")));
        keys.addAll(query("""
                SELECT l.ledger_id
                  FROM (SELECT ledger_id, accum_paid FROM policy_limit WHERE tenant_id = :tenantId) l
                  LEFT JOIN (SELECT ledger_id, coalesce(sum(amount) FILTER (WHERE kind = 'POST'), 0) AS posted
                               FROM policy_limit_history WHERE tenant_id = :tenantId GROUP BY ledger_id) h
                    ON h.ledger_id = l.ledger_id
                 WHERE l.accum_paid <> coalesce(h.posted, 0)
                 ORDER BY 1
                """, Map.of(), (rs, n) -> "policy_limit " + rs.getString("ledger_id")));
        keys.addAll(query("""
                SELECT ledger_id, source_seq
                  FROM (SELECT ledger_id, source_seq, kind, amount, accum_after,
                               lag(accum_after, 1, 0::bigint) OVER (PARTITION BY ledger_id ORDER BY source_seq) AS accum_before
                          FROM policy_limit_history WHERE tenant_id = :tenantId) h
                 WHERE kind = 'POST' AND accum_after - accum_before <> amount
                 ORDER BY 1, 2
                """, Map.of(), (rs, n) -> "policy_limit_history " + rs.getString("ledger_id") + "@" + rs.getLong("source_seq")));
        return keys;
    }
}
