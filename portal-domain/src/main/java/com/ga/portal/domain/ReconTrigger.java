package com.ga.portal.domain;

/** 대사 회차를 시작한 것(설계서 §5 {@code recon_run.trigger}, §6.6). */
public enum ReconTrigger {
    /** 앱 안 스케줄러(테넌트 {@code recon_hour_kst} — 계획 Q9). 행위자 {@code recon:<tenant>}. */
    SCHEDULE,
    /** 운영 CLI {@code recon run}. 행위자 {@code cli:<OS 사용자>}. */
    CLI
}
