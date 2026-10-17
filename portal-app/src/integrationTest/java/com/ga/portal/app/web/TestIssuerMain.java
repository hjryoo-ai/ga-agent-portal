package com.ga.portal.app.web;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Date;

/**
 * 실 엔진 E2E(지시문 D-16)의 시험 발급기 — {@code ./gradlew :portal-app:runTestIssuer --args=<stateDir>}(정규 빌드 밖). {@link TestIssuer}를 띄우고
 * {@code <stateDir>/subject}(허구 subject — 파일로만 받는다)의 토큰(대상 {@code portal-e2e}, 1시간)을 {@code <stateDir>/token}(권한 600)에, 발급자 URL을
 * {@code <stateDir>/issuer-url}에, 자기 PID를 {@code <stateDir>/issuer.pid}에 쓴 뒤 종료될 때까지 JWKS를 낸다. 토큰·subject는 출력하지 않는다.
 */
public final class TestIssuerMain {

    static final String AUDIENCE = "portal-e2e";

    private TestIssuerMain() {
    }

    public static void main(String[] args) throws Exception {
        Path state = Path.of(args[0]);
        String subject = Files.readString(state.resolve("subject"), StandardCharsets.UTF_8).strip();
        TestIssuer issuer = new TestIssuer("ga01-e2e");
        Instant now = Instant.now();
        String token = issuer.token(subject, AUDIENCE, c -> c.expirationTime(Date.from(now.plusSeconds(3600))));
        Path tokenFile = state.resolve("token");
        Files.writeString(tokenFile, token, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(tokenFile, PosixFilePermissions.fromString("rw-------"));
        Files.writeString(state.resolve("issuer.pid"), Long.toString(ProcessHandle.current().pid()), StandardCharsets.UTF_8);
        Path url = state.resolve("issuer-url.tmp");
        Files.writeString(url, issuer.issuer(), StandardCharsets.UTF_8);
        Files.move(url, state.resolve("issuer-url"), java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        System.out.println("[issuer] ready (issuer url and token written to the state directory)");
        Thread.currentThread().join();
    }
}
