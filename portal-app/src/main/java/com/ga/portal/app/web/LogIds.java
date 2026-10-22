package com.ga.portal.app.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 로그에 쓰는 식별자는 해시로만(지시문 D-14, 계획 승인 Q1): {@code sub}·{@code agent_id}·발급자 URL은 SHA-256 앞 12자 16진. 같은 값은 같은 해시라 로그끼리 잇기는
 * 된다. 지표 태그에는 쓰지 않는다(태그는 {@code tenant}·{@code reason}뿐) — 하나의 예외는 {@code api_jwks_failures_total{issuer_hash}}(발급자 해시 —
 * Phase 3 수용 심사 §2 F6, {@link ApiMetrics}).
 */
public final class LogIds {

    private LogIds() {
    }

    public static String subject(String subject) {
        return hash12(subject);
    }

    public static String agent(String agentId) {
        return hash12(agentId);
    }

    public static String issuer(String issuer) {
        return hash12(issuer);
    }

    private static String hash12(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
