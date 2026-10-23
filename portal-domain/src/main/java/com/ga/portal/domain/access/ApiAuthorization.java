package com.ga.portal.domain.access;

import com.ga.portal.domain.Role;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 조회 API 인가 표 — 엔드포인트 템플릿 → 허용 역할(정본은 {@code docs/authz-matrix.md}, {@code AuthzMatrixTest}가 이 표·문서·컨트롤러 매핑을 양방향으로 대조한다).
 * Phase 3의 {@code /api/v1/me/**}는 전부 {@code AGENT}만(MANAGER는 v2, OPERATOR는 CLI — 지시문 D-2). 허용되지 않으면 {@link NotFound}({@code ROLE}) —
 * 응답은 다른 404와 같은 바이트.
 */
public final class ApiAuthorization {

    public static final String ME = "GET /api/v1/me";
    public static final String EARNINGS = "GET /api/v1/me/earnings";
    public static final String MONTH = "GET /api/v1/me/earnings/{ym}";
    public static final String LINES = "GET /api/v1/me/earnings/{ym}/lines";
    public static final String LINE = "GET /api/v1/me/earnings/lines/{calcId}";
    public static final String LIMITS = "GET /api/v1/me/limits";
    public static final String LIMIT = "GET /api/v1/me/limits/{ledgerId}";
    public static final String DEFERRALS = "GET /api/v1/me/deferrals";

    private static final Map<String, Set<Role>> MATRIX;

    static {
        Map<String, Set<Role>> m = new LinkedHashMap<>();
        for (String endpoint : new String[] {ME, EARNINGS, MONTH, LINES, LINE, LIMITS, LIMIT, DEFERRALS}) {
            m.put(endpoint, Set.of(Role.AGENT));
        }
        MATRIX = Collections.unmodifiableMap(m);
    }

    private ApiAuthorization() {
    }

    /** 엔드포인트 → 허용 역할(선언 순서). */
    public static Map<String, Set<Role>> matrix() {
        return MATRIX;
    }

    public static void require(Caller caller, String endpoint) {
        Set<Role> allowed = MATRIX.get(endpoint);
        if (allowed == null) {
            throw new IllegalStateException("endpoint not in the authorization matrix: " + endpoint);
        }
        if (!allowed.contains(caller.role())) {
            throw new NotFound(DenyReason.ROLE);
        }
    }
}
