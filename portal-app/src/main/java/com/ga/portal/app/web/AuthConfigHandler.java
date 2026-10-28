package com.ga.portal.app.web;

import com.ga.portal.infra.directory.WebLogin;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.Optional;

/**
 * {@code GET /auth/config}(포털 Phase 8 계획 Q2, 조건 A) — 인증 없는 경로 하나. 요청 출처에 등록된 테넌트의 {@code {"issuer","clientId"}} 두 필드만
 * ({@code Cache-Control: no-cache}), 등록이 없으면 나머지 체인의 404와 같은 바이트 — 출처를 열거할 수 없다. 인증 머리는 보지 않는다.
 * {@code /api/**} 밖의 함수형 경로라 권한 표·컨트롤러 셋은 그대로이고, "인증 없는 경로" 닫힌 목록({@link ApiSecurityConfiguration#PUBLIC_PATHS})에 있다.
 */
final class AuthConfigHandler {

    static final String PATH = "/auth/config";
    private static final MediaType PROBLEM = MediaType.parseMediaType(ProblemResponses.CONTENT_TYPE);

    /** 응답 본문 — 두 필드뿐(계약 {@code portal-api} 1.3.0 {@code AuthConfig}, 추가 속성 금지). */
    record AuthConfig(String issuer, String clientId) {
    }

    private AuthConfigHandler() {
    }

    static RouterFunction<ServerResponse> routes(LoginConfigs logins) {
        return RouterFunctions.route().GET(PATH, r -> handle(r, logins)).build();
    }

    private static ServerResponse handle(ServerRequest request, LoginConfigs logins) {
        Optional<WebLogin> login = logins.forRequest(request.servletRequest());
        if (login.isEmpty()) {
            return ServerResponse.status(404).contentType(PROBLEM).body(ProblemResponses.NOT_FOUND);
        }
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noCache())
                .body(new AuthConfig(login.get().issuer(), login.get().clientId()));
    }
}
