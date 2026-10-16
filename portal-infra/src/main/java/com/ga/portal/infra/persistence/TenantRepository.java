package com.ga.portal.infra.persistence;

import com.ga.platform.core.money.Ratio;
import com.ga.platform.core.tenant.TenantId;
import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.platform.spring.jdbc.TenantScopedRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

/**
 * 자기 테넌트 행 조회(지시문 v1.2 §4 — 저장소 패턴 확정용 두 구체 저장소 중 하나). 다른 테넌트 행은 저장소 조건({@code :tenantId})과
 * RLS 양쪽에서 보이지 않는다.
 */
@Repository
public class TenantRepository extends TenantScopedRepository {

    /** {@code NUMERIC(5,2)} 백분율 → 정수 bp. 포털에서 이 변환이 있는 곳은 이 SQL 한 곳뿐이다(승인 Q2). */
    static final long BASIS_POINTS = 10_000L;

    private static final RowMapper<TenantRecord> MAPPER = (rs, rowNum) -> new TenantRecord(
            TenantId.of(rs.getString("tenant_id")),
            rs.getString("name"),
            rs.getString("engine_base_url"),
            rs.getString("status"),
            Ratio.of(rs.getLong("limit_warn_bp"), BASIS_POINTS),
            Ratio.of(rs.getLong("limit_crit_bp"), BASIS_POINTS),
            rs.getString("payout_day_rule"),
            rs.getString("params"));

    public TenantRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    public Optional<TenantRecord> current() {
        return queryAtMostOne("""
                SELECT tenant_id, name, engine_base_url, status,
                       round(limit_warn_pct * 100)::bigint AS limit_warn_bp,
                       round(limit_crit_pct * 100)::bigint AS limit_crit_bp,
                       payout_day_rule, params::text AS params
                  FROM tenant
                 WHERE tenant_id = :tenantId
                """, Map.of(), MAPPER);
    }
}
