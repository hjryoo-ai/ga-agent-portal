package com.ga.portal.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * ga-agent-portal 진입점. Phase 0은 조립과 {@code /actuator/health}, Phase 1은 인제스트(폴링 — {@code portal.ingest.enabled})와 운영 명령
 * ({@code cli} 프로파일)을 더한다. 컨트롤러는 없다.
 *
 * <p>데이터소스는 {@code portal_app}(RLS 대상, DML만), Flyway는 {@code portal_migrator}(소유자)로 접속한다.
 * 트랜잭션 매니저는 platform-spring의 {@code TenantSessionBinder}(자동 구성)다. 시간은 이 {@link Clock} 하나에서 온다.
 */
@SpringBootApplication(scanBasePackages = "com.ga.portal")
public class PortalApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(PortalApplication.class, args);
        if (context.getEnvironment().matchesProfiles("cli")) {
            // 운영 명령(Phase 1 D-8): 명령 하나를 실행하고 그 종료 코드로 끝난다
            System.exit(SpringApplication.exit(context));
        }
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
