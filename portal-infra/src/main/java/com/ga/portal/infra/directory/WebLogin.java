package com.ga.portal.infra.directory;

import java.util.Objects;

/** 출처 하나의 화면 로그인 설정 — {@code tenant.oidc_issuer}와 공개 클라이언트 {@code oidc_client_id}(V10, 설계서 §9 v1.11). 둘 다 비밀이 아니다. */
public record WebLogin(String issuer, String clientId) {
    public WebLogin {
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(clientId, "clientId");
    }
}
