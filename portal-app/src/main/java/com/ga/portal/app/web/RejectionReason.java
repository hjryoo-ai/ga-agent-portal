package com.ga.portal.app.web;

/**
 * 401 사유(계획 ② — 검사 순서대로). 응답은 사유와 무관하게 한 바이트이고, 사유는 지표 {@code api_authn_rejected_total{reason}}에만 남는다(감사 행 없음 —
 * 계획 승인 Q6: 테넌트를 모르는 요청은 행을 쓸 곳이 없고, 등록된 {@code iss}만 실은 위조 토큰이 감사 표를 채울 수 있다).
 */
enum RejectionReason {
    /** {@code Authorization: Bearer} 하나가 아니다(없음·다른 스킴·둘 이상). */
    NO_TOKEN,
    /** JWT로 읽히지 않는다, 또는 {@code iss}·{@code sub}가 없거나 형식이 틀렸다. */
    MALFORMED,
    /** 헤더 {@code alg} ≠ {@code RS256}({@code none}·{@code HS256}·JWE 포함). */
    ALG_NOT_ALLOWED,
    /** {@code iss}가 어느 테넌트의 발급자도 아니다. */
    ISSUER_UNKNOWN,
    /** 그 발급자의 키로 서명이 검증되지 않는다(모르는 {@code kid} 포함). */
    SIGNATURE,
    /** {@code exp}가 없거나 지났다, 또는 {@code nbf} 전(허용 오차 60초, 주입된 시계). */
    EXPIRED,
    /** {@code aud}에 그 테넌트의 {@code oidc_audience}가 없다. */
    AUDIENCE,
    /** {@code tenant_id} 클레임이 있는데 발급자의 테넌트와 다르다. */
    TENANT_MISMATCH
}
