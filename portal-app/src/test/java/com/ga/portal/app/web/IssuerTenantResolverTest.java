package com.ga.portal.app.web;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.infra.directory.IssuerRegistration;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 계획 ②의 검사 2~8 — 사유 분류·순서·허용 오차(주입된 시계)·발급자 미등록이면 디코더(메타데이터·JWKS 요청)를 만들지 않음. 서명은 실제 RSA(인프로세스 키),
 * 디코더는 공개키 직접(HTTP 없음 — HTTP 경로는 {@code UniformRejectionIT}).
 */
class IssuerTenantResolverTest {

    static final Instant NOW = Instant.parse("2026-10-17T03:00:00Z");
    static final String ISS = "https://idp.example.test/realms/ga01";
    static final String AUD = "portal-ga01";
    static final TenantId T1 = TenantId.of("GA01");

    static RSAKey key;
    static RSAKey otherKey;

    final List<String> lookups = new ArrayList<>();
    final List<String> decodersBuilt = new ArrayList<>();
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @BeforeAll
    static void keys() throws JOSEException {
        key = new RSAKeyGenerator(2048).keyID("k1").generate();
        otherKey = new RSAKeyGenerator(2048).keyID("k1").generate();
    }

    IssuerTenantResolver resolver(Map<String, IssuerRegistration> directory, Function<String, JwtDecoder> decoders) {
        return new IssuerTenantResolver(iss -> {
            lookups.add(iss);
            return Optional.ofNullable(directory.get(iss));
        }, iss -> {
            decodersBuilt.add(iss);
            return decoders.apply(iss);
        }, new ApiMetrics(Optional.of(registry)), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    IssuerTenantResolver resolver() {
        return resolver(Map.of(ISS, new IssuerRegistration(T1, ISS, AUD)), iss -> publicKeyDecoder());
    }

    static JwtDecoder publicKeyDecoder() {
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
            decoder.setJwtValidator(IssuerTenantResolver::acceptAll);
            return decoder;
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer(ISS).subject("sub-unit-1").audience(AUD)
                .issueTime(Date.from(NOW.minusSeconds(7200))).expirationTime(Date.from(NOW.plusSeconds(300)));
    }

    static String rs256(RSAKey signingKey, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(signingKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    static String rs256(UnaryOperator<JWTClaimsSet.Builder> edit) {
        return rs256(key, edit.apply(claims()).build());
    }

    Authentication authenticate(IssuerTenantResolver r, String token) {
        return r.authenticate(new BearerTokenAuthenticationToken(token));
    }

    RejectionReason reasonOf(IssuerTenantResolver r, String token) {
        try {
            authenticate(r, token);
        } catch (TokenRejectedException e) {
            return e.reason();
        }
        throw new AssertionError("accepted");
    }

    @Test
    void aValidTokenYieldsTheIssuersTenantAndTheSubjectOnly() {
        Authentication auth = authenticate(resolver(), rs256(b -> b.claim("agent_id", "SOMEONE-ELSE").claim("roles", List.of("MANAGER"))));
        assertThat(auth).isInstanceOf(ApiAuthentication.class);
        ApiAuthentication api = (ApiAuthentication) auth;
        assertThat(api.tenant()).isEqualTo(T1);
        assertThat(api.subject()).isEqualTo("sub-unit-1");
        assertThat(api.getAuthorities()).as("no authorities from roles/scope claims").isEmpty();
        assertThat(api.toString()).doesNotContain("sub-unit-1");
    }

    @Test
    void malformedTokensAndMissingIssuerOrSubject() {
        IssuerTenantResolver r = resolver();
        assertThat(reasonOf(r, "not-a-jwt")).isEqualTo(RejectionReason.MALFORMED);
        assertThat(reasonOf(r, "a.b.c")).isEqualTo(RejectionReason.MALFORMED);
        assertThat(reasonOf(r, rs256(b -> b.issuer(null)))).isEqualTo(RejectionReason.MALFORMED);
        assertThat(reasonOf(r, rs256(b -> b.subject(null)))).isEqualTo(RejectionReason.MALFORMED);
        assertThat(reasonOf(r, rs256(b -> b.subject(" ")))).isEqualTo(RejectionReason.MALFORMED);
        assertThat(reasonOf(r, rs256(b -> b.subject("s".repeat(201))))).isEqualTo(RejectionReason.MALFORMED);
        assertThat(reasonOf(r, rs256(b -> b.claim("iss", 42)))).isEqualTo(RejectionReason.MALFORMED);
        assertThat(lookups).as("no directory lookup before the format check passes").isEmpty();
    }

    @Test
    void onlyRs256SignedTokensPassTheAlgorithmCheckAndItRunsBeforeTheIssuerLookup() throws JOSEException {
        IssuerTenantResolver r = resolver();
        String none = new PlainJWT(claims().build()).serialize();
        SignedJWT hs = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims().build());
        hs.sign(new MACSigner("0123456789abcdef0123456789abcdef"));
        SignedJWT rs384 = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS384).keyID("k1").build(), claims().build());
        rs384.sign(new RSASSASigner(key));
        assertThat(reasonOf(r, none)).isEqualTo(RejectionReason.ALG_NOT_ALLOWED);
        assertThat(reasonOf(r, hs.serialize())).isEqualTo(RejectionReason.ALG_NOT_ALLOWED);
        assertThat(reasonOf(r, rs384.serialize())).isEqualTo(RejectionReason.ALG_NOT_ALLOWED);
        assertThat(lookups).isEmpty();
        assertThat(decodersBuilt).isEmpty();
    }

    @Test
    void anUnknownIssuerIsRejectedWithoutBuildingADecoderForTheTokensUrl() {
        IssuerTenantResolver r = resolver();
        assertThat(reasonOf(r, rs256(b -> b.issuer("https://attacker.example.test/")))).isEqualTo(RejectionReason.ISSUER_UNKNOWN);
        assertThat(lookups).containsExactly("https://attacker.example.test/");
        assertThat(decodersBuilt).as("no metadata/JWKS request to a URL the token chose").isEmpty();
    }

    @Test
    void aSignatureFromAnotherKeyIsRejectedWithTheTenantKnown() {
        TokenRejectedException e = rejection(resolver(), rs256(otherKey, claims().build()));
        assertThat(e.reason()).isEqualTo(RejectionReason.SIGNATURE);
        assertThat(e.tenant()).contains(T1);
    }

    @Test
    void lifetimeChecksUseTheInjectedClockWithSixtySecondsOfSkew() {
        IssuerTenantResolver r = resolver();
        assertThat(authenticate(r, rs256(b -> b.expirationTime(Date.from(NOW.minusSeconds(60)))))).isNotNull();
        assertThat(reasonOf(r, rs256(b -> b.expirationTime(Date.from(NOW.minusSeconds(61)))))).isEqualTo(RejectionReason.EXPIRED);
        assertThat(authenticate(r, rs256(b -> b.notBeforeTime(Date.from(NOW.plusSeconds(60)))))).isNotNull();
        assertThat(reasonOf(r, rs256(b -> b.notBeforeTime(Date.from(NOW.plusSeconds(61)))))).isEqualTo(RejectionReason.EXPIRED);
        assertThat(reasonOf(r, rs256(b -> b.expirationTime(null)))).as("exp is required").isEqualTo(RejectionReason.EXPIRED);
    }

    @Test
    void theAudienceMustContainTheTenantsAudience() {
        IssuerTenantResolver r = resolver();
        assertThat(authenticate(r, rs256(b -> b.audience(List.of("other", AUD))))).isNotNull();
        assertThat(reasonOf(r, rs256(b -> b.audience("other")))).isEqualTo(RejectionReason.AUDIENCE);
        assertThat(reasonOf(r, rs256(b -> b.audience((String) null)))).isEqualTo(RejectionReason.AUDIENCE);
    }

    @Test
    void aTenantClaimIsACrossCheckNotTheSource() {
        IssuerTenantResolver r = resolver();
        assertThat(((ApiAuthentication) authenticate(r, rs256(b -> b.claim("tenant_id", "GA01")))).tenant()).isEqualTo(T1);
        assertThat(reasonOf(r, rs256(b -> b.claim("tenant_id", "GA02")))).isEqualTo(RejectionReason.TENANT_MISMATCH);
        assertThat(reasonOf(r, rs256(b -> b.claim("tenant_id", 1)))).isEqualTo(RejectionReason.TENANT_MISMATCH);
    }

    @Test
    void checksRunInOrderSoTheFirstFailingCheckNamesTheReason() {
        IssuerTenantResolver r = resolver();
        // 서명이 틀리고 만료이고 대상도 틀림 → 서명(5)이 먼저
        assertThat(reasonOf(r, rs256(otherKey, claims().expirationTime(Date.from(NOW.minusSeconds(3600))).audience("x").build())))
                .isEqualTo(RejectionReason.SIGNATURE);
        // 만료이고 대상이 틀리고 테넌트가 틀림 → 만료(6)
        assertThat(reasonOf(r, rs256(b -> b.expirationTime(Date.from(NOW.minusSeconds(3600))).audience("x").claim("tenant_id", "GA02"))))
                .isEqualTo(RejectionReason.EXPIRED);
        // 대상이 틀리고 테넌트가 틀림 → 대상(7)
        assertThat(reasonOf(r, rs256(b -> b.audience("x").claim("tenant_id", "GA02")))).isEqualTo(RejectionReason.AUDIENCE);
    }

    @Test
    void decodersAreBuiltOncePerIssuerAndAnUnreachableKeySetIsAServerErrorNotA401() {
        IssuerTenantResolver r = resolver();
        authenticate(r, rs256(b -> b));
        authenticate(r, rs256(b -> b.subject("sub-unit-2")));
        assertThat(decodersBuilt).containsExactly(ISS);
        assertThat(lookups).as("directory is read on every request (no cache)").hasSize(2);

        IssuerTenantResolver down = resolver(Map.of(ISS, new IssuerRegistration(T1, ISS, AUD)), iss -> token -> {
            throw new JwtException("Couldn't retrieve remote JWK set");
        });
        assertThatThrownBy(() -> authenticate(down, rs256(b -> b))).isInstanceOf(AuthenticationServiceException.class)
                .hasMessage("JWKS_UNAVAILABLE");
        // Phase 3 수용 심사 §2 F6: 장애는 발급자 해시 태그 하나로 센다(URL 원문·테넌트·subject 태그 없음)
        assertThat(registry.get("api_jwks_failures_total").tag("issuer_hash", LogIds.issuer(ISS)).counter().count()).isEqualTo(1.0);
        assertThat(registry.get("api_jwks_failures_total").counter().getId().getTags()).hasSize(1);
    }

    TokenRejectedException rejection(IssuerTenantResolver r, String token) {
        try {
            authenticate(r, token);
        } catch (TokenRejectedException e) {
            return e;
        }
        throw new AssertionError("accepted");
    }
}
