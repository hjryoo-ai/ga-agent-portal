package com.ga.portal.ingest.feed;

import com.ga.platform.core.tenant.TenantId;

import java.util.Optional;

/**
 * 테넌트별 엔진 서비스 토큰(계획 승인 Q7 — 파일. 구현은 {@code portal-app}). 값은 저장소·로그·예외·감사·{@code error}에 쓰지 않는다.
 */
public interface ServiceTokenSource {

    Optional<ServiceToken> tokenFor(TenantId tenant);

    /** 토큰 값. {@link #toString()}은 값을 드러내지 않는다. */
    record ServiceToken(String value) {
        public ServiceToken {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("empty service token");
            }
        }

        @Override
        public String toString() {
            return "ServiceToken[***]";
        }
    }
}
