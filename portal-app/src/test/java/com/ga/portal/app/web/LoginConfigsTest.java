package com.ga.portal.app.web;

import com.ga.portal.infra.directory.WebLogin;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포털 Phase 8 계획 Q2·P-13(조건 A): 출처 = {@code Origin}, 없으면 스킴 + {@code Host}. 출처마다 60초 캐시(주입 시계 — 없음도 캐시), 항목 상한에서 비운다,
 * 너무 긴 출처는 조회하지 않는다. CSP에 넣을 발급자 출처는 스킴·호스트·포트만 다시 조립하고 형식 밖이면 넣지 않는다.
 */
class LoginConfigsTest {

    static final WebLogin LOGIN = new WebLogin("http://localhost:8180/realms/demo", "portal-web");

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-28T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static HttpServletRequest request(String scheme, Map<String, String> headers) {
        return (HttpServletRequest) Proxy.newProxyInstance(LoginConfigsTest.class.getClassLoader(), new Class<?>[] {HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHeader" -> headers.get((String) args[0]);
                    case "getScheme" -> scheme;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void theOriginHeaderWinsOtherwiseSchemeAndHost() {
        assertThat(LoginConfigs.origin(request("http", Map.of("Origin", "https://a.invalid", "Host", "b.invalid")))).contains("https://a.invalid");
        assertThat(LoginConfigs.origin(request("http", Map.of("Host", "localhost:8080")))).contains("http://localhost:8080");
        assertThat(LoginConfigs.origin(request("https", Map.of("Host", "portal.invalid")))).contains("https://portal.invalid");
        assertThat(LoginConfigs.origin(request("http", Map.of()))).isEmpty();
    }

    @Test
    void lookupsAreCachedForSixtySecondsOnTheInjectedClockIncludingMisses() {
        MutableClock clock = new MutableClock();
        List<String> asked = new ArrayList<>();
        LoginConfigs logins = new LoginConfigs(o -> {
            asked.add(o);
            return o.equals("http://localhost:8080") ? Optional.of(LOGIN) : Optional.empty();
        }, clock);
        assertThat(logins.forOrigin("http://localhost:8080")).contains(LOGIN);
        assertThat(logins.forOrigin("http://other.invalid")).isEmpty();
        clock.now = clock.now.plus(Duration.ofSeconds(59));
        assertThat(logins.forOrigin("http://localhost:8080")).contains(LOGIN);
        assertThat(logins.forOrigin("http://other.invalid")).isEmpty();
        assertThat(asked).containsExactly("http://localhost:8080", "http://other.invalid");
        clock.now = clock.now.plus(Duration.ofSeconds(1));
        assertThat(logins.forOrigin("http://localhost:8080")).contains(LOGIN);
        assertThat(asked).containsExactly("http://localhost:8080", "http://other.invalid", "http://localhost:8080");
    }

    @Test
    void theCacheIsBoundedAndOverlongOriginsAreNotLookedUp() {
        List<String> asked = new ArrayList<>();
        LoginConfigs logins = new LoginConfigs(o -> {
            asked.add(o);
            return Optional.empty();
        }, new MutableClock());
        for (int i = 0; i <= LoginConfigs.MAX_ENTRIES; i++) {
            logins.forOrigin("http://h" + i + ".invalid");
        }
        logins.forOrigin("http://h0.invalid");
        assertThat(asked).as("the first entry was dropped when the cache filled up").hasSize(LoginConfigs.MAX_ENTRIES + 2);
        String overlong = "http://" + "a".repeat(LoginConfigs.MAX_ORIGIN_LENGTH) + ".invalid";
        assertThat(logins.forOrigin(overlong)).isEmpty();
        assertThat(asked).doesNotContain(overlong);
    }

    @Test
    void theIssuerOriginIsRebuiltFromSchemeHostAndPortOnly() {
        assertThat(LoginConfigs.issuerOrigin(LOGIN)).contains("http://localhost:8180");
        assertThat(LoginConfigs.issuerOrigin(new WebLogin("https://idp.invalid/realms/x", "c"))).contains("https://idp.invalid");
        assertThat(LoginConfigs.issuerOrigin(new WebLogin("https://idp.invalid:8443/a;b", "c"))).contains("https://idp.invalid:8443");
        assertThat(LoginConfigs.issuerOrigin(new WebLogin("ftp://idp.invalid/x", "c"))).isEmpty();
        assertThat(LoginConfigs.issuerOrigin(new WebLogin("https://user@idp.invalid;x/", "c"))).isEmpty();
        assertThat(LoginConfigs.issuerOrigin(new WebLogin("not a url", "c"))).isEmpty();
    }
}
