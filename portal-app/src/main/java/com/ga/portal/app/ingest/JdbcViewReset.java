package com.ga.portal.app.ingest;

import com.ga.portal.infra.persistence.ViewResetRepository;
import com.ga.portal.ingest.apply.ViewReset;

import java.util.List;

/** 재구축 뷰 포트의 PostgreSQL 어댑터(Phase 1 Q6 방식 — 포트는 {@code portal-ingest}, 저장소는 {@code portal-infra}). */
public final class JdbcViewReset implements ViewReset {

    private final ViewResetRepository repository;

    public JdbcViewReset(ViewResetRepository repository) {
        this.repository = repository;
    }

    @Override
    public void deleteViews() {
        repository.deleteViews();
    }

    @Override
    public int restoreDriftMarks() {
        return repository.restoreDriftMarks();
    }

    @Override
    public List<String> verifyAll() {
        return repository.violations();
    }
}
