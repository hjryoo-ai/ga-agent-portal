package com.ga.portal.app;

import com.ga.platform.spring.jdbc.TenantSessionBinder;
import com.ga.portal.app.ingest.IngestScheduler;
import com.ga.portal.infra.persistence.IngestCursorRepository;
import com.ga.portal.infra.persistence.TenantRepository;
import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.ingest.apply.IngestApplier;
import com.ga.portal.ingest.feed.FeedClient;
import com.ga.portal.projection.Projector;
import com.ga.portal.projection.ViewProjector;
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
 * 트랜잭션 매니저가 TenantSessionBinder다. Phase 1: 인제스트 부품이 조립되고 폴링은 기본으로 꺼져 있다. Phase 3: 컨트롤러는 조회 API 하나.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PortalApplicationIT {

    private static final PostgresHarness DB = PostgresHarness.get();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::jdbcUrl);
        registry.add("spring.flyway.url", DB::jdbcUrl);
        registry.add("portal.query.policy-key-secret-file", () -> SECRET.toString());
    }

    /** policyKey 비밀(허구 — 실행마다 무작위). 웹 애플리케이션은 이 파일 없이 뜨지 않는다(Phase 3). */
    static final java.nio.file.Path SECRET = secret();

    private static java.nio.file.Path secret() {
        try {
            java.nio.file.Path f = java.nio.file.Files.createTempFile("portal-policy-key", ".bin");
            byte[] b = new byte[32];
            new java.security.SecureRandom().nextBytes(b);
            return java.nio.file.Files.write(f, b);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
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

    /** Phase 1: 인제스트 적용 부품은 조립되고, 폴링(스케줄러·피드)은 기본으로 꺼져 있다(portal.ingest.enabled). */
    @Test
    void ingestPartsAreWiredAndPollingIsOffByDefault() {
        assertThat(context.getBean(IngestApplier.class)).isNotNull();
        assertThat(context.getBean(Projector.class)).isInstanceOf(ViewProjector.class);
        assertThat(context.getBeanNamesForType(IngestScheduler.class)).isEmpty();
        assertThat(context.getBeanNamesForType(FeedClient.class)).isEmpty();
    }

    /** Phase 3: 애플리케이션 컨트롤러는 조회 API 하나뿐이다(인제스트·운영 명령은 HTTP가 없다). */
    @Test
    void theOnlyApplicationControllerIsTheQueryApi() {
        assertThat(context.getBeansWithAnnotation(org.springframework.stereotype.Controller.class).values())
                .filteredOn(bean -> bean.getClass().getName().startsWith("com.ga."))
                .extracting(bean -> bean.getClass().getName()).containsExactly("com.ga.portal.app.web.MeController");
    }
}
