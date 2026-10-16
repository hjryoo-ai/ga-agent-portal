package com.ga.portal.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * ga-agent-portal 진입점. Phase 0은 조립과 {@code /actuator/health}만 제공한다(컨트롤러·업무 로직 없음).
 *
 * <p>데이터소스는 {@code portal_app}(RLS 대상, DML만), Flyway는 {@code portal_migrator}(소유자)로 접속한다.
 * 트랜잭션 매니저는 platform-spring의 {@code TenantSessionBinder}(자동 구성)다. 시간은 이 {@link Clock} 하나에서 온다.
 */
@SpringBootApplication(scanBasePackages = "com.ga.portal")
public class PortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortalApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
