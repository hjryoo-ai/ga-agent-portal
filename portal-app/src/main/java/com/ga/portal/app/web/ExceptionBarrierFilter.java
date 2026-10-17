package com.ga.portal.app.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 필터 단계(MVC 바깥)에서 새어 나온 예외 → 500 고정 바이트(ga-disclosure 6A와 같은 장벽). 예: 발급자 조회 DB 실패, JWKS를 받지 못함
 * ({@code AuthenticationServiceException} — 401로 감추지 않는다). 없으면 컨테이너가 {@code /error}로 다시 보내고 그 경로는 404가 돼 실패가 가려진다.
 * 로그는 예외 클래스 이름만.
 */
final class ExceptionBarrierFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ExceptionBarrierFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            Throwable cause = e instanceof ServletException && e.getCause() != null ? e.getCause() : e;
            LOG.error("API_FILTER_ERROR {}", cause.getClass().getSimpleName());
            if (response.isCommitted()) {
                throw e;
            }
            response.reset();
            ProblemResponses.internalError(response);
        }
    }
}
