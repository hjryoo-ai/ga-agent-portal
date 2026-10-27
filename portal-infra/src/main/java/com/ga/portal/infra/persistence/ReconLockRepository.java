package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.platform.spring.jdbc.TenantScopedRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * 테넌트당 대사 회차 하나(포털 Phase 7 계획 Q9 R0): 바인딩된 테넌트의 트랜잭션 수준 advisory lock — 이름공간 {@code hashtext('portal.recon')}(인제스트·
 * 시뮬레이션·문의와 겹치지 않는다). 회차는 이 잠금을 쥔 바깥 트랜잭션을 회차 내내 열어 두고, 저장·재구축은 그 안에서 새 트랜잭션으로 돈다(P7-4 — 연결
 * 하나를 회차 동안 쥔다, {@code docs/운영.md} §8 풀 크기). 표에 닿지 않는 문장이라 SQL 테넌트 스캔의 대상이 아니다({@link IngestLockRepository}와 같다).
 */
@Repository
public class ReconLockRepository extends TenantScopedRepository {

    public ReconLockRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    public boolean tryLock() {
        return queryAtMostOne("""
                SELECT pg_try_advisory_xact_lock(hashtext('portal.recon'), hashtext(:tenantId)) AS locked
                """, Map.of(), (rs, n) -> rs.getBoolean("locked")).orElse(false);
    }
}
