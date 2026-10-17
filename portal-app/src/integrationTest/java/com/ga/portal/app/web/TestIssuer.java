package com.ga.portal.app.web;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

/**
 * 인프로세스 시험 발급기(계획 ⑦ — Keycloak 컨테이너는 Phase 8): RSA 2048 키 하나, JDK {@link HttpServer}가 {@code <issuer>/.well-known/openid-configuration}과
 * {@code <issuer>/jwks}를 낸다. 발급자 URL = {@code http://127.0.0.1:<port>/realms/<realm>}. 토큰은 RS256·{@code kid} 고정, 시각은 실제 시계(앱 시계와 같은
 * 시스템 시계 — 허용 오차 경계는 단위 시험 {@code IssuerTenantResolverTest}가 고정 시계로 본다). 메타데이터·JWKS 요청 수를 센다.
 */
final class TestIssuer implements AutoCloseable {

    private final HttpServer server;
    private final String issuer;
    private final RSAKey key;
    private final AtomicInteger metadataRequests = new AtomicInteger();
    private final AtomicInteger jwksRequests = new AtomicInteger();

    TestIssuer(String realm) {
        try {
            this.key = new RSAKeyGenerator(2048).keyID(realm + "-k1").generate();
            this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String base = "/realms/" + realm;
        this.issuer = "http://127.0.0.1:" + server.getAddress().getPort() + base;
        String metadata = "{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer + "/jwks\","
                + "\"id_token_signing_alg_values_supported\":[\"RS256\"],\"subject_types_supported\":[\"public\"]}";
        String jwks = new JWKSet(key.toPublicJWK()).toString();
        server.createContext(base + "/.well-known/openid-configuration", ex -> {
            metadataRequests.incrementAndGet();
            respond(ex, metadata);
        });
        server.createContext(base + "/jwks", ex -> {
            jwksRequests.incrementAndGet();
            respond(ex, jwks);
        });
        server.start();
    }

    private static void respond(HttpExchange ex, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, body.length);
        try (var out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    String issuer() {
        return issuer;
    }

    RSAKey key() {
        return key;
    }

    int metadataRequests() {
        return metadataRequests.get();
    }

    int jwksRequests() {
        return jwksRequests.get();
    }

    /** 이 발급자의 기본 클레임: {@code iss}, {@code sub}, {@code aud}, {@code iat} = 지금 − 1분, {@code exp} = 지금 + 5분. */
    JWTClaimsSet.Builder claims(String subject, String audience) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder().issuer(issuer).subject(subject).audience(audience)
                .issueTime(Date.from(now.minus(Duration.ofMinutes(1)))).expirationTime(Date.from(now.plus(Duration.ofMinutes(5))));
    }

    String token(String subject, String audience) {
        return token(subject, audience, b -> b);
    }

    String token(String subject, String audience, UnaryOperator<JWTClaimsSet.Builder> edit) {
        return sign(key, edit.apply(claims(subject, audience)).build());
    }

    static String sign(RSAKey signingKey, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(signingKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
