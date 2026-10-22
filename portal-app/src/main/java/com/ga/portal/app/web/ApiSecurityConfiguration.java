package com.ga.portal.app.web;

import com.ga.portal.infra.directory.IssuerDirectoryReader;
import com.ga.portal.infra.identity.IdentityLinkResolver;
import com.ga.portal.infra.persistence.AuditAccessRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * 조회 API 조립(포털 Phase 3 계획 ②·③). 웹 애플리케이션에서만 — 운영 명령({@code cli} 프로파일)에는 없다.
 *
 * <ul>
 *   <li>{@code /api/**} 체인 하나: 무상태(세션·CSRF·요청 캐시·익명·폼·기본 인증 없음), 베어러 헤더 하나 → {@link IssuerTenantResolver}(검사 2~8) →
 *       {@link TenantBindingFilter}. 거부는 전부 {@link ApiAuthenticationEntryPoint}의 같은 401. 캐시 헤더는 {@code private, no-cache}
 *       (Spring 기본의 {@code no-store}를 끈다 — 304가 의미를 갖게, 지시문 D-8).</li>
 *   <li>나머지 경로: 헬스만 열고 그 밖은 {@code denyAll} — 같은 404 바이트.</li>
 *   <li>예외 장벽(보안 체인보다 앞) — 필터 단계 예외는 500 고정 바이트.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(ApiProperties.class)
public class ApiSecurityConfiguration implements WebMvcConfigurer {

    /** 서블릿 필터 순서: 예외 장벽은 보안 체인({@code -100})보다 앞. */
    static final int BARRIER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    private final ObjectProvider<CallerArgumentResolver> callerResolver;

    ApiSecurityConfiguration(ObjectProvider<CallerArgumentResolver> callerResolver) {
        this.callerResolver = callerResolver;
    }

    @Bean
    IssuerDirectoryReader issuerDirectoryReader(ApiProperties props, @Value("${spring.datasource.url}") String jdbcUrl) {
        return new IssuerDirectoryReader(jdbcUrl, props.issuerUsername(), props.issuerPassword(), props.issuerPoolSize());
    }

    @Bean
    ApiMetrics apiMetrics(ObjectProvider<MeterRegistry> registry) {
        return new ApiMetrics(Optional.ofNullable(registry.getIfAvailable()));
    }

    @Bean
    ApiTransactions apiTransactions(PlatformTransactionManager transactionManager) {
        return new ApiTransactions(transactionManager);
    }

    @Bean
    DenialAudit denialAudit(AuditAccessRepository audits, ApiTransactions transactions, ApiMetrics metrics, Clock clock) {
        return new DenialAudit(audits, transactions, metrics, clock);
    }

    @Bean
    CallerArgumentResolver callerArgumentResolver(IdentityLinkResolver links, ApiTransactions transactions) {
        return new CallerArgumentResolver(links, transactions);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(callerResolver.getObject());
    }

    @Bean
    FilterRegistrationBean<ExceptionBarrierFilter> exceptionBarrierFilter() {
        FilterRegistrationBean<ExceptionBarrierFilter> registration = new FilterRegistrationBean<>(new ExceptionBarrierFilter());
        registration.setOrder(BARRIER_ORDER);
        return registration;
    }

    /** 발급자 URL → 서명만 검증하는 디코더(메타데이터 → JWKS, RS256만). 메타데이터·JWKS 요청에 제한 시간. */
    static Function<String, JwtDecoder> issuerDecoders(ApiProperties props) {
        SimpleClientHttpRequestFactory http = new SimpleClientHttpRequestFactory();
        http.setConnectTimeout(props.jwksTimeout());
        http.setReadTimeout(props.jwksTimeout());
        RestTemplate rest = new RestTemplate(http);
        return issuer -> {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer)
                    .restOperations(rest)
                    .jwsAlgorithm(SignatureAlgorithm.RS256)
                    .build();
            decoder.setJwtValidator(IssuerTenantResolver::acceptAll);
            return decoder;
        };
    }

    @Bean
    @Order(1)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, IssuerDirectoryReader issuers, ApiProperties props, ApiMetrics metrics,
                                               Clock clock) throws Exception {
        ApiAuthenticationEntryPoint entryPoint = new ApiAuthenticationEntryPoint(metrics);
        IssuerTenantResolver resolver = new IssuerTenantResolver(issuers::find, issuerDecoders(props), metrics, clock);
        http.securityMatcher("/api/**")
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(r -> r.disable())
                .anonymous(a -> a.disable())
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .logout(l -> l.disable())
                .headers(h -> h.cacheControl(c -> c.disable())
                        .addHeaderWriter(new StaticHeadersWriter("Cache-Control", "private, no-cache")))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.bearerTokenResolver(new SingleBearerTokenResolver())
                        .authenticationManagerResolver(request -> resolver)
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .addFilterAfter(new TenantBindingFilter(entryPoint), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(10)
    SecurityFilterChain otherSecurityFilterChain(HttpSecurity http) throws Exception {
        UnroutedHandler unrouted = new UnroutedHandler();
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(r -> r.disable())
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .logout(l -> l.disable())
                .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/readiness",
                                "/actuator/health/liveness").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(unrouted).accessDeniedHandler(unrouted));
        return http.build();
    }
}
