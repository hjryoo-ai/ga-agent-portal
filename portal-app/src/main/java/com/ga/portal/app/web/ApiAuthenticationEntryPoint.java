package com.ga.portal.app.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.util.Optional;

/**
 * 401 하나(계획 ②): 여덟 사유 전부 {@link ProblemResponses#UNAUTHENTICATED} 바이트와 {@code WWW-Authenticate: Bearer}. 사유는 지표에만 — 토큰이 없는 요청
 * (예외가 {@link TokenRejectedException}이 아니고 OAuth2 예외도 아니다)은 {@code NO_TOKEN}, 베어러 헤더 형식 오류(Spring 해석기의 OAuth2 예외)는
 * {@code MALFORMED}.
 */
final class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiMetrics metrics;

    ApiAuthenticationEntryPoint(ApiMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        switch (exception) {
            case TokenRejectedException rejected -> metrics.authnRejected(rejected.reason(), rejected.tenant());
            case OAuth2AuthenticationException ignored -> metrics.authnRejected(RejectionReason.MALFORMED, Optional.empty());
            default -> metrics.authnRejected(RejectionReason.NO_TOKEN, Optional.empty());
        }
        ProblemResponses.unauthenticated(response);
    }
}
