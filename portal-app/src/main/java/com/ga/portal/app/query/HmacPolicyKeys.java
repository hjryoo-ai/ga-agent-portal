package com.ga.portal.app.query;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.query.port.PolicyKeys;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * {@code policyKey} = HMAC-SHA256(배포 비밀, {@code tenant_id} + {@code U+001F} + {@code policy_no})의 앞 16자 16진(계획 승인 Q5). 비밀은 파일에서 한 번 읽는다
 * (32바이트 이상 — 짧으면 기동 실패). 비밀 값·증권번호는 예외 메시지·로그에 싣지 않는다. 회전하면 모든 키가 바뀐다({@code docs/운영.md}).
 */
final class HmacPolicyKeys implements PolicyKeys {

    static final int MIN_SECRET_BYTES = 32;
    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec secret;

    HmacPolicyKeys(byte[] secret) {
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("policy key secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        this.secret = new SecretKeySpec(secret.clone(), ALGORITHM);
    }

    static HmacPolicyKeys fromFile(Path file) {
        try {
            return new HmacPolicyKeys(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException("portal.query.policy-key-secret-file is not readable", new IOException(e.getClass().getSimpleName()));
        }
    }

    @Override
    public String key(TenantId tenant, String policyNo) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secret);
            byte[] digest = mac.doFinal((tenant.value() + "\u001f" + policyNo).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITHM + " unavailable", e);
        }
    }
}
