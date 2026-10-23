package com.ga.portal.app.web;

import com.ga.platform.core.tenant.TenantContext;
import com.ga.portal.domain.access.InvalidRequest;
import com.ga.portal.domain.access.NotFound;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.annotation.Order;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

/**
 * MVC 단계의 예외 → 고정 바이트(계획 ③): {@link NotFound}는 감사(별도 트랜잭션) 뒤 404. 없는 경로·메서드 불일치·경로 변수 형식 오류도 같은 404(감사 없음 — 호출자를
 * 해석하기 전일 수 있다). 쿼리 파라미터 형식 오류·누락·응답 형식 불일치는 400 한 가지. 그 밖은 500(로그는 예외 클래스 이름만 — 메시지에 입력값이 섞일 수 있다).
 */
@RestControllerAdvice
@Order(0)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
final class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final DenialAudit audit;

    ApiExceptionHandler(DenialAudit audit) {
        this.audit = audit;
    }

    @ExceptionHandler(NotFound.class)
    void notFound(NotFound e, HttpServletRequest request, HttpServletResponse response) throws IOException {
        ApiAuthentication auth = CallerArgumentResolver.authentication(request);
        audit.record(TenantContext.current(), auth.subject(), e.reason(), route(request));
        ProblemResponses.notFound(response);
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    void unrouted(HttpServletResponse response) throws IOException {
        ProblemResponses.notFound(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    void typeMismatch(MethodArgumentTypeMismatchException e, HttpServletResponse response) throws IOException {
        if (e.getParameter().hasParameterAnnotation(PathVariable.class)) {
            ProblemResponses.notFound(response);
        } else {
            ProblemResponses.invalidRequest(response);
        }
    }

    @ExceptionHandler({InvalidRequest.class, MissingServletRequestParameterException.class, HttpMediaTypeNotAcceptableException.class})
    void invalid(HttpServletResponse response) throws IOException {
        ProblemResponses.invalidRequest(response);
    }

    @ExceptionHandler(Exception.class)
    void internal(Exception e, HttpServletResponse response) throws IOException {
        LOG.error("API_ERROR {}", e.getClass().getSimpleName());
        ProblemResponses.internalError(response);
    }

    /** 감사 대상 = 메서드 + 매칭된 경로 <b>템플릿</b>(경로 변수 값 없음 — 남의 키가 감사 표에 들어가지 않는다). */
    static String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return request.getMethod() + " " + (pattern == null ? "?" : pattern.toString());
    }
}
