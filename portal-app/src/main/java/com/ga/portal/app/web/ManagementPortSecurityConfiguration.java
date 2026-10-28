package com.ga.portal.app.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * 관리 포트(포털 Phase 8 계획 Q5·P-17): {@code management.server.port}가 있을 때만(compose — 컨테이너 네트워크 전용, 호스트 공개 0) 그 포트로 온 요청에
 * 헬스 셋과 {@code /actuator/prometheus}를 {@code GET}으로 연다. 앱 포트는 이 체인에 걸리지 않는다 — 앱 포트의 {@code /actuator/prometheus}는 지금처럼
 * 나머지 체인의 같은 404 바이트다. 포트 판정은 요청이 들어온 로컬 포트와 실제 관리 포트({@code local.management.port} — 0이면 기동 뒤 정해진다)의 일치.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty("management.server.port")
public class ManagementPortSecurityConfiguration {

    /** 관리 포트의 경로(GET만) — 닫힌 목록. */
    public static final List<String> MANAGEMENT_PATHS = List.of("/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness",
            "/actuator/prometheus");

    @Bean
    @Order(0)
    SecurityFilterChain managementSecurityFilterChain(HttpSecurity http, Environment environment) throws Exception {
        UnroutedHandler unrouted = new UnroutedHandler();
        http.securityMatcher(request -> request.getLocalPort() == environment.getProperty("local.management.port", Integer.class, -1))
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(r -> r.disable())
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .logout(l -> l.disable())
                .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.GET, MANAGEMENT_PATHS.toArray(String[]::new)).permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(unrouted).accessDeniedHandler(unrouted));
        return http.build();
    }
}
