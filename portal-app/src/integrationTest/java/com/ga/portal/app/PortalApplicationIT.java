package com.ga.portal.app;

import com.ga.platform.spring.jdbc.TenantSessionBinder;
import com.ga.portal.infra.persistence.IngestCursorRepository;
import com.ga.portal.infra.persistence.TenantRepository;
import com.ga.portal.infra.testing.PostgresHarness;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 부팅 스모크: 애플리케이션이 portal_app 데이터소스와 portal_migrator Flyway로 뜨고, /actuator/health가 UP이며 DB 헬스 지표가 없고,
 * 트랜잭션 매니저가 TenantSessionBinder다. 컨트롤러는 없다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PortalApplicationIT {

    private static final PostgresHarness DB = PostgresHarness.get();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::jdbcUrl);
        registry.add("spring.flyway.url", DB::jdbcUrl);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    ApplicationContext context;

    @Test
    void healthIsUpWithoutADatabaseIndicator() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
        assertThat(Arrays.stream(context.getBeanNamesForType(HealthContributor.class)))
                .as("health contributors").noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("db"));
    }

    @Test
    void tenantBinderIsTheOnlyTransactionManagerAndBothRepositoriesAreWired() {
        assertThat(context.getBeansOfType(PlatformTransactionManager.class).values())
                .singleElement().isInstanceOf(TenantSessionBinder.class);
        assertThat(context.getBean(TenantRepository.class)).isNotNull();
        assertThat(context.getBean(IngestCursorRepository.class)).isNotNull();
    }

    @Test
    void noApplicationControllersExist() {
        assertThat(context.getBeansWithAnnotation(org.springframework.stereotype.Controller.class).values())
                .noneMatch(bean -> bean.getClass().getName().startsWith("com.ga."));
    }
}
