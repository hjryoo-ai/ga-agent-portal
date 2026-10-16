package com.ga.portal.domain;

/** 테넌트별 인제스트 커서 상태(설계서 §5 {@code ingest_cursor.state}, §6.1·§6.7). */
public enum IngestState {
    RUNNING,
    /** 운영자가 멈춤. */
    PAUSED,
    /** 갭·독 이벤트·불변식 위반으로 정지 — 그 테넌트만. */
    FAILED,
    /** 그림자 스키마로 재구축 중. */
    REBUILDING
}
