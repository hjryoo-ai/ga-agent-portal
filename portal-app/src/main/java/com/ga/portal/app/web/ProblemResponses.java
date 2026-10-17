package com.ga.portal.app.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 조회 API의 거부 응답 — 사유마다 <b>고정 바이트</b>(지시문 D-3, 계획 ②·③). 본문은 {@code application/problem+json}의 {@code {code, message}}이고, 같은 상태의
 * 응답은 사유와 무관하게 본문·헤더가 같다. 401에는 {@code WWW-Authenticate: Bearer}(값 하나 — {@code error=}·사유 없음).
 *
 * <p>404 하나가 "링크 없음·REVOKED·역할·남의 자원·없는 자원·없는 경로·메서드 불일치·경로 변수 형식"을 전부 덮는다 — 존재 여부를 형식 오류로 흘리지 않는다.
 * 쿼리 파라미터 형식 오류만 400 한 가지(계획 Q11).
 */
final class ProblemResponses {

    static final String CONTENT_TYPE = "application/problem+json";

    static final byte[] UNAUTHENTICATED = body("UNAUTHENTICATED", "authentication required");
    static final byte[] NOT_FOUND = body("NOT_FOUND", "not found");
    static final byte[] INVALID_REQUEST = body("INVALID_REQUEST", "invalid request");
    static final byte[] INTERNAL_ERROR = body("INTERNAL_ERROR", "internal error");

    private ProblemResponses() {
    }

    private static byte[] body(String code, String message) {
        return ("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    static void unauthenticated(HttpServletResponse response) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, HttpServletResponse.SC_UNAUTHORIZED, UNAUTHENTICATED);
    }

    static void notFound(HttpServletResponse response) throws IOException {
        write(response, HttpServletResponse.SC_NOT_FOUND, NOT_FOUND);
    }

    static void invalidRequest(HttpServletResponse response) throws IOException {
        write(response, HttpServletResponse.SC_BAD_REQUEST, INVALID_REQUEST);
    }

    static void internalError(HttpServletResponse response) throws IOException {
        write(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, INTERNAL_ERROR);
    }

    private static void write(HttpServletResponse response, int status, byte[] body) throws IOException {
        response.setStatus(status);
        response.setContentType(CONTENT_TYPE);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
        response.flushBuffer();
    }
}
