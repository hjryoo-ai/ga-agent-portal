package com.ga.portal.app.ingest;

import com.ga.portal.infra.persistence.IngestLockRepository;
import com.ga.portal.ingest.apply.IngestLock;

/** 테넌트 advisory lock 어댑터(지시문 D-5). */
final class JdbcIngestLock implements IngestLock {

    private final IngestLockRepository locks;

    JdbcIngestLock(IngestLockRepository locks) {
        this.locks = locks;
    }

    @Override
    public boolean tryLock() {
        return locks.tryLock();
    }
}
