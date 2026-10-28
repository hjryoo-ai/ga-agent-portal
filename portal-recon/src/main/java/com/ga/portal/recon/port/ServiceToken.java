package com.ga.portal.recon.port;

/** 테넌트의 엔진 서비스 토큰(인제스트·시뮬레이션과 같은 파일). {@link #toString()}은 값을 드러내지 않는다 — 로그·예외·저장에 쓰지 않는다. */
public record ServiceToken(String value) {

    public ServiceToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("empty engine token");
        }
    }

    @Override
    public String toString() {
        return "ServiceToken[***]";
    }
}
