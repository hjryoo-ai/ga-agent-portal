package com.ga.portal.domain;

/** 대사 회차가 부른 자동 재구축의 결과(설계서 §5 {@code recon_run.rebuild_outcome}). 재구축하지 않은 회차는 값이 없다(NULL). */
public enum RebuildOutcome {
    SUCCEEDED,
    FAILED
}
