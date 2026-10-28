package com.ga.portal.app.web;

import com.ga.portal.infra.directory.WebLogin;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 요청 출처 → 화면 로그인 설정(포털 Phase 8 계획 Q2·P-13, 조건 A). {@code GET /auth/config}와 화면 응답의 CSP {@code connect-src}가 같은 조회를 쓴다.
 * <ul>
 *   <li>출처는 {@code Origin} 머리가 있으면 그것, 없으면 요청 스킴 + {@code "://"} + {@code Host} 머리 — 그 문자열과 {@code tenant.web_origin}의
 *       <b>정확 일치</b>뿐이다(정규화·접두 일치·와일드카드 없음). 같은 출처 GET은 브라우저가 {@code Origin}을 싣지 않으므로 보통은 {@code Host}다.</li>
 *   <li>출처마다 60초 메모리 캐시(주입 {@link Clock}) — 없음도 캐시한다. 인증 없는 경로라 키는 요청이 정한다: 길이 {@value #MAX_ORIGIN_LENGTH} 초과는
 *       조회하지 않고, 항목이 {@value #MAX_ENTRIES}개에 닿으면 전부 비운다(메모리 상한).</li>
 *   <li>발급자 출처(CSP에 넣는 값)는 발급자 URL의 스킴·호스트·포트만 다시 조립한다 — 호스트가 이름·IPv4 형식이 아니면 넣지 않는다(머리 주입 불가).</li>
 * </ul>
 */
public final class LoginConfigs {

    static final Duration TTL = Duration.ofSeconds(60);
    static final int MAX_ENTRIES = 256;
    static final int MAX_ORIGIN_LENGTH = 255;
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9.-]{1,253}$");

    private record Entry(Optional<WebLogin> login, Instant expires) {
    }

    private final Function<String, Optional<WebLogin>> lookup;
    private final Clock clock;
    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    public LoginConfigs(Function<String, Optional<WebLogin>> lookup, Clock clock) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** 이 요청의 출처 문자열 — {@code Origin}이 있으면 그것, 없으면 스킴 + {@code Host}. 둘 다 없으면 빈 값. */
    static Optional<String> origin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null) {
            return Optional.of(origin);
        }
        String host = request.getHeader("Host");
        return host == null ? Optional.empty() : Optional.of(request.getScheme() + "://" + host);
    }

    public Optional<WebLogin> forRequest(HttpServletRequest request) {
        return origin(request).flatMap(this::forOrigin);
    }

    Optional<WebLogin> forOrigin(String origin) {
        if (origin.length() > MAX_ORIGIN_LENGTH) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Entry cached = cache.get(origin);
        if (cached != null && now.isBefore(cached.expires())) {
            return cached.login();
        }
        Optional<WebLogin> login = lookup.apply(origin);
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
        cache.put(origin, new Entry(login, now.plus(TTL)));
        return login;
    }

    /** 발급자 URL의 출처({@code scheme://host[:port]}) — CSP {@code connect-src}에 더할 값. 형식 밖이면 빈 값. */
    public static Optional<String> issuerOrigin(WebLogin login) {
        URI uri;
        try {
            uri = URI.create(login.issuer());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || !(scheme.equals("http") || scheme.equals("https")) || !HOST.matcher(host).matches()) {
            return Optional.empty();
        }
        return Optional.of(scheme + "://" + host + (uri.getPort() < 0 ? "" : ":" + uri.getPort()));
    }
}
