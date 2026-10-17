package com.ga.portal.app.query;

import com.ga.platform.core.tenant.TenantId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C6·Q5: {@code policyKey} — 16자 16진, 결정적, 테넌트·증권번호·비밀이 하나라도 다르면 다르다(비밀 없는 SHA-256과도 다르다 — 열거로 되돌릴 수 없다).
 * 비밀은 32바이트 이상, 파일 오류 메시지에 경로·값이 없다.
 */
class HmacPolicyKeysTest {

    static byte[] secret(int fill) {
        byte[] b = new byte[32];
        Arrays.fill(b, (byte) fill);
        return b;
    }

    @Test
    void keysAreDeterministicSixteenHexAndDependOnTenantPolicyAndSecret() throws Exception {
        HmacPolicyKeys keys = new HmacPolicyKeys(secret(7));
        String k = keys.key(TenantId.of("GA01"), "POL-2026-0001");
        assertThat(k).matches("[0-9a-f]{16}");
        assertThat(new HmacPolicyKeys(secret(7)).key(TenantId.of("GA01"), "POL-2026-0001")).isEqualTo(k);
        assertThat(keys.key(TenantId.of("GA02"), "POL-2026-0001")).isNotEqualTo(k);
        assertThat(keys.key(TenantId.of("GA01"), "POL-2026-0002")).isNotEqualTo(k);
        assertThat(new HmacPolicyKeys(secret(8)).key(TenantId.of("GA01"), "POL-2026-0001")).as("rotation changes every key").isNotEqualTo(k);
        String unkeyed = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest("GA01\u001fPOL-2026-0001".getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        assertThat(k).as("not the plain SHA-256 of the directive's first draft (DE-15)").isNotEqualTo(unkeyed);
        // 구분자: (GA0, 1POL…)과 (GA01, POL…)이 같은 입력이 되지 않는다
        assertThat(keys.key(TenantId.of("GA0"), "1\u001fPOL-2026-0001")).isNotEqualTo(k);
    }

    @Test
    void aShortSecretOrAnUnreadableFileFailsWithoutLeakingThePath(@TempDir Path dir) throws Exception {
        assertThatThrownBy(() -> new HmacPolicyKeys(new byte[31])).isInstanceOf(IllegalArgumentException.class);
        Path missing = dir.resolve("no-such-secret");
        assertThatThrownBy(() -> HmacPolicyKeys.fromFile(missing)).isInstanceOf(UncheckedIOException.class)
                .satisfies(e -> assertThat(e.getMessage() + e.getCause().getMessage()).doesNotContain(missing.toString()));
        Path ok = Files.write(dir.resolve("secret"), secret(1));
        assertThat(HmacPolicyKeys.fromFile(ok).key(TenantId.of("GA01"), "P")).matches("[0-9a-f]{16}");
    }
}
