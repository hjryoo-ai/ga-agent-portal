package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.platform.spring.jdbc.TenantScopedRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * 테넌트당 인제스트 워커 하나(Phase 1 지시문 D-5): 바인딩된 테넌트의 트랜잭션 수준 advisory lock을 시도한다 — 커밋·롤백 때 풀린다.
 * 키는 2-인자형(이름공간 {@code hashtext('portal.ingest')}, {@code hashtext(tenant_id)}) — 해시 충돌은 두 테넌트를 직렬화할 뿐 정확성에
 * 영향이 없다. 표에 닿지 않는 문장이라 SQL 테넌트 스캔의 대상이 아니다(계획 승인 Q6 — 허용 목록 없이 "표 없음"으로 통과).
 */
@Repository
public class IngestLockRepository extends TenantScopedRepository {

    public IngestLockRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    public boolean tryLock() {
        return queryAtMostOne("""
                SELECT pg_try_advisory_xact_lock(hashtext('portal.ingest'), hashtext(:tenantId)) AS locked
                """, Map.of(), (rs, n) -> rs.getBoolean("locked")).orElse(false);
    }
}
