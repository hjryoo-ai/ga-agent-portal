package com.ga.portal.recon.port;

import com.ga.platform.core.tenant.TenantId;

import java.util.Optional;

/** 회차 잠금(계획 Q9 R0) — 테넌트 권고 잠금을 회차 내내 쥔다(인스턴스가 둘이어도 하나만). 못 잡으면 회차 없음. */
public interface ReconLock {

    Optional<Held> tryLock(TenantId tenant);

    interface Held extends AutoCloseable {
        @Override
        void close();
    }
}
