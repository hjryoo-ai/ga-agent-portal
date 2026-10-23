package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.platform.spring.jdbc.TenantScopedRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * 설계사별 시뮬레이션 직렬화(포털 Phase 5 ⑤ — 레이트 리밋 판정과 요청 행 넣기가 한 덩어리가 되게): 트랜잭션 수준 권고 잠금. 키 공간
 * {@code hashtext('portal.sim')}은 인제스트의 {@code hashtext('portal.ingest')}와 겹치지 않고, 둘째 키는 {@code 테넌트 ∥ U+001F ∥ 설계사}. 잡을 때까지
 * 기다린다(같은 설계사의 동시 요청만 줄 선다).
 */
@Repository
public class SimulationLockRepository extends TenantScopedRepository {

    public SimulationLockRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    public void lock(String agentId) {
        queryAtMostOne("""
                SELECT true AS locked FROM pg_advisory_xact_lock(hashtext('portal.sim'), hashtext(:tenantId || chr(31) || :agentId)) AS l
                """, Map.of("agentId", agentId), (rs, n) -> rs.getBoolean("locked"));
    }
}
