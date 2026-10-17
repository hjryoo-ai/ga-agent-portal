package com.ga.portal.app.web;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.infra.directory.IssuerRegistration;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 베어러 토큰 → (테넌트, {@code sub}) — 계획 ②의 검사 2~8을 <b>그 순서대로</b> 하고, 실패는 사유를 실은 {@link TokenRejectedException}(응답은 전부 같은 401).
 *
 * <ol start="2">
 *   <li>JWT 파싱(서명 검증 전) — 클레임 집합, 문자열 {@code iss}, {@code sub}(1~200자). 아니면 {@code MALFORMED}.</li>
 *   <li>헤더 {@code alg} = {@code RS256}(서명된 JWS만 — {@code none}·{@code HS256}·JWE 거부). 아니면 {@code ALG_NOT_ALLOWED}.</li>
 *   <li>{@code iss} → 등록 테넌트 하나(전용 롤 리더, 요청마다 조회 — 캐시 없음). 아니면 {@code ISSUER_UNKNOWN}. <b>등록된 발급자만</b> 메타데이터·JWKS를
 *       받는다 — 토큰이 고른 URL로 나가는 요청은 없다.</li>
 *   <li>그 발급자의 디코더(발급자 URL마다 하나 — 메타데이터 → JWKS)로 서명만 검증. 아니면 {@code SIGNATURE}(Spring {@code Jwt}의 불변식 위반 — 예:
 *       {@code exp ≤ iat} — 도 디코더가 같은 예외로 던져 여기로 센다). JWKS를 받지 못하면 401이 아니라 500(서버 쪽 고장을 "토큰이 틀렸다"로 감추지 않는다).</li>
 *   <li>{@code exp}(필수)·{@code nbf} — 허용 오차 60초, 주입된 {@link Clock}. 아니면 {@code EXPIRED}.</li>
 *   <li>{@code aud}에 그 테넌트의 {@code oidc_audience}. 아니면 {@code AUDIENCE}.</li>
 *   <li>{@code tenant_id} 클레임이 있으면 = 4의 테넌트(교차 검사 — 신뢰 근거가 아니다). 아니면 {@code TENANT_MISMATCH}.</li>
 * </ol>
 *
 * <p>토큰에서 읽는 것은 {@code iss}·{@code sub}·{@code aud}·{@code exp}/{@code nbf}·{@code tenant_id}뿐이다 — {@code roles}·{@code agent_id}는 어디서도 읽지 않는다.
 * {@code Jwt}의 멤버를 부르는 포털 클래스는 이것 하나다(아키텍처 규칙). Spring의 {@code JwtIssuerAuthenticationManagerResolver}를 쓰지 않은 이유: 그 해석기는
 * 형식 오류·모르는 발급자를 같은 예외(메시지만 다름)로 던지고 {@code alg} 검사보다 발급자 조회를 먼저 한다 — 사유 분류를 메시지 글자에 기대지 않고 순서 ②를
 * 지키려고 같은 일을 직접 한다.
 */
final class IssuerTenantResolver implements AuthenticationManager {

    static final Duration CLOCK_SKEW = Duration.ofSeconds(60);
    static final String TENANT_CLAIM = "tenant_id";
    static final int MAX_SUBJECT_LENGTH = 200;

    private final Function<String, Optional<IssuerRegistration>> issuers;
    private final Function<String, JwtDecoder> decoderFactory;
    private final JwtTimestampValidator timestamps;
    private final Map<String, JwtDecoder> decoders = new ConcurrentHashMap<>();

    /**
     * @param issuers        {@code iss} → 등록(전용 롤 리더)
     * @param decoderFactory 발급자 URL → 서명만 검증하는 디코더(검증기는 여기서 돌린다)
     */
    IssuerTenantResolver(Function<String, Optional<IssuerRegistration>> issuers, Function<String, JwtDecoder> decoderFactory, Clock clock) {
        this.issuers = issuers;
        this.decoderFactory = decoderFactory;
        this.timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        this.timestamps.setClock(clock);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!(authentication instanceof BearerTokenAuthenticationToken bearer)) {
            throw new TokenRejectedException(RejectionReason.NO_TOKEN);
        }
        String token = bearer.getToken();

        // 2. 형식(서명 전)
        JWT parsed;
        JWTClaimsSet unverified;
        try {
            parsed = JWTParser.parse(token);
            unverified = parsed.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new TokenRejectedException(RejectionReason.MALFORMED);
        }
        String issuer;
        String subject;
        try {
            issuer = unverified.getStringClaim("iss");
            subject = unverified.getStringClaim("sub");
        } catch (ParseException e) {
            throw new TokenRejectedException(RejectionReason.MALFORMED);
        }
        if (issuer == null || issuer.isEmpty() || !validSubject(subject)) {
            throw new TokenRejectedException(RejectionReason.MALFORMED);
        }

        // 3. 알고리즘
        if (!(parsed instanceof SignedJWT signed) || !JWSAlgorithm.RS256.equals(signed.getHeader().getAlgorithm())) {
            throw new TokenRejectedException(RejectionReason.ALG_NOT_ALLOWED);
        }

        // 4. 발급자 → 테넌트
        IssuerRegistration registration = issuers.apply(issuer).orElseThrow(() -> new TokenRejectedException(RejectionReason.ISSUER_UNKNOWN));
        TenantId tenant = registration.tenant();

        // 5. 서명
        Jwt jwt;
        try {
            jwt = decoders.computeIfAbsent(registration.issuer(), decoderFactory).decode(token);
        } catch (BadJwtException e) {
            throw new TokenRejectedException(RejectionReason.SIGNATURE, tenant);
        } catch (JwtException e) {
            throw new AuthenticationServiceException("JWKS_UNAVAILABLE");
        }

        // 6. 시각
        if (jwt.getExpiresAt() == null || timestamps.validate(jwt).hasErrors()) {
            throw new TokenRejectedException(RejectionReason.EXPIRED, tenant);
        }

        // 7. 대상
        List<String> audience = jwt.getAudience();
        if (audience == null || !audience.contains(registration.audience())) {
            throw new TokenRejectedException(RejectionReason.AUDIENCE, tenant);
        }

        // 8. tenant_id 교차 검사
        if (jwt.hasClaim(TENANT_CLAIM) && !tenant.value().equals(jwt.getClaims().get(TENANT_CLAIM))) {
            throw new TokenRejectedException(RejectionReason.TENANT_MISMATCH, tenant);
        }
        return new ApiAuthentication(tenant, jwt.getSubject());
    }

    private static boolean validSubject(String subject) {
        return subject != null && !subject.isBlank() && subject.length() <= MAX_SUBJECT_LENGTH;
    }

    /** 서명만 검증하는 디코더의 검증기(시각·대상·교차 검사는 {@link #authenticate}가 순서대로 한다). */
    static OAuth2TokenValidatorResult acceptAll(Jwt jwt) {
        return OAuth2TokenValidatorResult.success();
    }
}
