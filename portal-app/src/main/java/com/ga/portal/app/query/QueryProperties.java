package com.ga.portal.app.query;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * 조회 설정({@code portal.query}): {@code policyKey} HMAC 비밀 파일(배포 비밀 — 값은 저장소·로그에 0, 토큰 디렉터리와 같은 취급, 기본값 없음: 웹 애플리케이션은
 * 없으면 뜨지 않는다)과 {@code sync_delay_seconds}의 기본값(테넌트 {@code params}에 없을 때 — 코드 상수가 아니라 설정, 지시문 D-10).
 */
@ConfigurationProperties(prefix = "portal.query")
record QueryProperties(Path policyKeySecretFile, long defaultSyncDelaySeconds) {
    QueryProperties {
        if (policyKeySecretFile == null) {
            throw new IllegalArgumentException("portal.query.policy-key-secret-file is required");
        }
        if (defaultSyncDelaySeconds < 1) {
            throw new IllegalArgumentException("portal.query.default-sync-delay-seconds must be positive");
        }
    }
}
