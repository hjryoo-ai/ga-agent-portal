package com.ga.portal.app.web;

import com.ga.platform.core.tenant.TenantId;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

import java.io.Serial;
import java.util.Objects;
import java.util.Optional;

/**
 * 토큰 거부 — 사유와(발급자가 등록된 경우) 그 테넌트를 진입점까지 나른다. 메시지는 사유 코드뿐이다(토큰·클레임 값 없음). 응답에는 아무것도 싣지 않는다
 * ({@link ApiAuthenticationEntryPoint}가 고정 바이트를 쓴다).
 */
final class TokenRejectedException extends OAuth2AuthenticationException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final RejectionReason reason;
    private final transient TenantId tenant;

    TokenRejectedException(RejectionReason reason) {
        this(reason, null);
    }

    TokenRejectedException(RejectionReason reason, TenantId tenant) {
        super(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN), Objects.requireNonNull(reason, "reason").name());
        this.reason = reason;
        this.tenant = tenant;
    }

    RejectionReason reason() {
        return reason;
    }

    Optional<TenantId> tenant() {
        return Optional.ofNullable(tenant);
    }
}
