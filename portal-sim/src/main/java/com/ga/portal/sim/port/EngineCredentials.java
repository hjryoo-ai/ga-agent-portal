package com.ga.portal.sim.port;

import com.ga.platform.core.tenant.TenantId;

import java.util.Optional;

/** 테넌트의 엔진 서비스 토큰 — 인제스트와 같은 파일({@code portal.ingest.token-dir/<TENANT_ID>}, 계획 Q2). 값은 로그·예외·저장에 쓰지 않는다. */
public interface EngineCredentials {

    Optional<EngineToken> token(TenantId tenant);

    /** 토큰 값. {@link #toString()}은 값을 드러내지 않는다. */
    record EngineToken(String value) {
        public EngineToken {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("empty engine token");
            }
        }

        @Override
        public String toString() {
            return "EngineToken[***]";
        }
    }
}
