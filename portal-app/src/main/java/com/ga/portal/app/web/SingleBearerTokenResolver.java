package com.ga.portal.app.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import java.util.Collections;

/**
 * 베어러 토큰은 {@code Authorization} 헤더 하나에서만(계획 ②-1): 헤더가 둘 이상이면 {@code NO_TOKEN}(어느 쪽을 믿을지 고르지 않는다). 쿼리·폼 파라미터의
 * 토큰은 읽지 않는다(Spring 기본값 — 접근 로그에 토큰이 남는 길을 열지 않는다).
 */
final class SingleBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        if (Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION)).size() > 1) {
            throw new TokenRejectedException(RejectionReason.NO_TOKEN);
        }
        return delegate.resolve(request);
    }
}
