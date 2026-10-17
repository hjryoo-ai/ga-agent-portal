package com.ga.portal.app.web;

import com.ga.platform.core.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 인증된 요청의 테넌트(토큰 발급자의 테넌트 — {@link IssuerTenantResolver})를 {@code TenantContext}에 바인딩하고 그 범위 안에서 나머지 체인(MVC 포함)을 잇는다
 * (계획 ③). 검증된 {@link ApiAuthentication}을 요청 속성에 둔다 — 이 뒤의 계층은 토큰을 보지 않는다. 인증이 없으면(토큰 없음) 같은 401.
 */
final class TenantBindingFilter extends OncePerRequestFilter {

    static final String ATTRIBUTE = ApiAuthentication.class.getName();

    private final ApiAuthenticationEntryPoint entryPoint;

    TenantBindingFilter(ApiAuthenticationEntryPoint entryPoint) {
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof ApiAuthentication api)) {
            entryPoint.commence(request, response, new InsufficientAuthenticationException("NO_TOKEN"));
            return;
        }
        request.setAttribute(ATTRIBUTE, api);
        try {
            TenantContext.runWith(api.tenant(), () -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}
