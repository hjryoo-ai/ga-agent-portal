package com.ga.portal.app.sim;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 시뮬레이션 설정({@code portal.sim}, 포털 Phase 5 계획 Q2·Q10): 엔진 연결·읽기 시간 초과(앱 설정 — 시험이 줄일 수 있다)와 테넌트
 * {@code params.sim_rate_per_minute}이 없을 때의 기본 한도(코드 상수가 아니라 설정 — {@code default-sync-delay-seconds}와 같은 방식).
 */
@ConfigurationProperties(prefix = "portal.sim")
record SimProperties(Duration connectTimeout, Duration readTimeout, int defaultRatePerMinute) {
    SimProperties {
        if (connectTimeout == null || readTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero() || readTimeout.isNegative()
                || readTimeout.isZero()) {
            throw new IllegalArgumentException("portal.sim timeouts must be positive");
        }
        if (defaultRatePerMinute < 1) {
            throw new IllegalArgumentException("portal.sim.default-rate-per-minute must be positive");
        }
    }
}
