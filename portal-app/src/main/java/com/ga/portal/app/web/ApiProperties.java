package com.ga.portal.app.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 조회 API 설정({@code portal.api}): 발급자 리더의 전용 롤 접속({@code portal_issuer} — 자격 증명은 환경으로, 기본값은 로컬 개발 전용)과 그 풀 크기,
 * 발급자 메타데이터·JWKS 요청의 제한 시간.
 */
@ConfigurationProperties(prefix = "portal.api")
record ApiProperties(String issuerUsername, String issuerPassword, int issuerPoolSize, Duration jwksTimeout) {
    ApiProperties {
        if (issuerPoolSize < 1) {
            throw new IllegalArgumentException("portal.api.issuer-pool-size must be >= 1");
        }
        if (jwksTimeout == null || jwksTimeout.isNegative() || jwksTimeout.isZero()) {
            throw new IllegalArgumentException("portal.api.jwks-timeout must be positive");
        }
    }
}
