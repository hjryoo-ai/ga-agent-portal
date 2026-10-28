package com.ga.portal.app.screen;

import com.ga.portal.app.web.LoginConfigs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 화면 서빙 체인(포털 Phase 4 계획 ⑧ — 지시문 ⑥·⑧, Q2·Q6). 같은 출처, 인증 없음(토큰은 화면 메모리의 Bearer이고 {@code /api} 체인만 검증한다).
 * <ul>
 *   <li>경로 판정은 MVC와 같은 {@link PathPatternRequestMatcher}로만, {@code GET}만. 화면 경로({@link #SCREEN_PATHS} — {@code web/src/routes.ts}와 같은
 *       닫힌 목록, {@code WebScreensIT}가 대조)는 {@code index.html}({@code no-store}), {@code /assets/*}는 해시 이름 자원(1년 {@code immutable},
 *       경로 탈출은 자원 처리기가 막는다). 그 밖의 경로·메서드는 이 체인에 걸리지 않고 나머지 체인(denyAll — 같은 404)으로 간다.</li>
 *   <li>헤더: CSP {@value #CSP} — 인라인 스크립트·스타일·{@code eval} 0, 외부 출처 0, {@code frame-ancestors 'none'}(네이티브 웹뷰는 프레임이 아니다 — Q2).
 *       포털 Phase 8(계획 Q2·돌이킬 수 없는 결정 4): 요청 출처에 로그인 설정이 등록된 테넌트가 있으면 {@code connect-src}만 {@code 'self' <발급자 출처>}가 되고
 *       나머지 아홉 지시어는 그대로다({@link #policy}). 등록이 없으면 위 상수 그대로.
 *       {@code Referrer-Policy: no-referrer}, {@code X-Content-Type-Options: nosniff}, {@code X-Frame-Options: DENY}. 세션·쿠키·CSRF 토큰 없음.</li>
 *   <li>웹 애플리케이션(서블릿)에서만 — 운영 명령(CLI)·워커 전용 컨텍스트에는 없다.</li>
 *   <li>화면 응답은 함수형 라우터이지 {@code @RestController}가 아니다 — API 계층 규칙(Caller·유스케이스·인가 매트릭스)은 {@code /api}의 것이다.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ScreenConfiguration implements WebMvcConfigurer {

    static final String ROOT = "portal-web/";
    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; font-src 'self'; connect-src 'self'; "
            + "object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

    /**
     * 화면 경로(SPA — 같은 문서). {@code web/src/routes.ts}의 {@code :이름}이 여기서는 {@code *}다(정적 조각 {@code /inquiries/new}는 그대로 — 포털 Phase 6).
     * 포털 Phase 8: {@code /auth/callback}은 PKCE 팝업이 돌아오는 화면 경로(같은 문서) — 인증 없는 경로 목록이 아니라 여기다(조건 A).
     */
    public static final List<String> SCREEN_PATHS = List.of("/", "/statements", "/statements/*", "/lines/*", "/limits", "/limits/*", "/deferrals",
            "/simulations", "/notifications", "/inquiries", "/inquiries/new", "/inquiries/*");
    static final String ASSETS = "/assets/*";
    private static final MediaType HTML = MediaType.parseMediaType("text/html;charset=UTF-8");

    /** 화면 CSP — 발급자 출처가 있으면 {@code connect-src}에만 더한다. */
    static String policy(Optional<String> issuerOrigin) {
        return issuerOrigin.map(o -> CSP.replace("connect-src 'self';", "connect-src 'self' " + o + ";")).orElse(CSP);
    }

    static RequestMatcher matcher() {
        PathPatternRequestMatcher.Builder b = PathPatternRequestMatcher.withDefaults();
        List<RequestMatcher> all = new ArrayList<>();
        SCREEN_PATHS.forEach(p -> all.add(b.matcher(HttpMethod.GET, p)));
        all.add(b.matcher(HttpMethod.GET, ASSETS));
        return new OrRequestMatcher(all);
    }

    @Bean
    @Order(5)
    SecurityFilterChain screenSecurityFilterChain(HttpSecurity http, LoginConfigs logins) throws Exception {
        HeaderWriter csp = (request, response) -> response.setHeader("Content-Security-Policy",
                policy(logins.forRequest(request).flatMap(LoginConfigs::issuerOrigin)));
        http.securityMatcher(matcher())
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(r -> r.disable())
                .securityContext(c -> c.disable())
                .anonymous(a -> a.disable())
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .logout(l -> l.disable())
                .headers(h -> h.addHeaderWriter(csp)
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .frameOptions(f -> f.deny())
                        .cacheControl(c -> c.disable()))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(ASSETS).addResourceLocations("classpath:/" + ROOT + "assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()).resourceChain(false);
    }

    @Bean
    RouterFunction<ServerResponse> screens() {
        RouterFunctions.Builder routes = RouterFunctions.route();
        SCREEN_PATHS.forEach(p -> routes.GET(p, r -> index()));
        return routes.build();
    }

    private static ServerResponse index() {
        ClassPathResource resource = new ClassPathResource(ROOT + "index.html");
        if (!resource.exists()) {
            return ServerResponse.notFound().build();
        }
        try (InputStream in = resource.getInputStream()) {
            return ServerResponse.ok().contentType(HTML).cacheControl(CacheControl.noStore()).body(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
