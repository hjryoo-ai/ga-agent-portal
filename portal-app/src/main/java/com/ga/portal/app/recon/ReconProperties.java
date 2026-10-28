package com.ga.portal.app.recon;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code portal.recon.*}(포털 Phase 7 — 설계서 §6.6). {@code defaults}는 {@code tenant.params}에 키가 없을 때의 값(코드 상수가 아니다 — 계획 ⑥).
 * 스케줄러는 {@code scheduler-enabled}로 끈다(통합 시험·E2E·CLI — P-11).
 */
@ConfigurationProperties(prefix = "portal.recon")
public record ReconProperties(boolean schedulerEnabled, Duration tick, Duration connectTimeout, Duration readTimeout, Defaults defaults) {

    public record Defaults(int reconHourKst, boolean autoRebuildEnabled, long caughtUpWithinSeconds, long callIntervalMs, int maxCalls,
                           long maxSeconds) {
    }
}
