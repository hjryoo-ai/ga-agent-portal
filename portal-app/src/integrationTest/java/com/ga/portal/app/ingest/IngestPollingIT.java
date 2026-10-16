package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.IngestState;
import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.SeedData;
import com.ga.portal.ingest.testing.Samples;
import com.ga.portal.ingest.testing.StubFeedServer;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조립 전체(폴링 켬): 스케줄러가 전용 롤 {@code portal_directory}로 테넌트를 읽고, 테넌트 행의 {@code engine_base_url}(스텁 엔진)에 토큰 파일의
 * 서비스 토큰으로 피드를 불러 A-1 17건을 적용한다. 지표({@code ingest_lag_seq} = 0)까지. 컨텍스트는 이 클래스 뒤에 닫는다(스케줄러가 다른
 * 시험의 테넌트를 계속 돌지 않게).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IngestPollingIT {

    private static final PostgresHarness DB = PostgresHarness.get();
    private static final TenantId T = TenantId.of(SeedData.uniqueTenant("POLL"));
    private static final String TOKEN = "fictional-svc-token-7Qx2";
    private static final StubFeedServer ENGINE = new StubFeedServer(T.value(), TOKEN);
    private static final Path TOKENS = tokenDir();

    private static Path tokenDir() {
        try {
            Path dir = Files.createTempDirectory("portal-tokens");
            Path file = dir.resolve(T.value());
            Files.writeString(file, TOKEN + "\n");
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        ENGINE.events(Samples.all());
        IngestRig.seedTenant(T, ENGINE.baseUrl().toString(), "{\"poll_interval_seconds\": 5}");
        registry.add("spring.datasource.url", DB::jdbcUrl);
        registry.add("spring.flyway.url", DB::jdbcUrl);
        registry.add("portal.ingest.enabled", () -> "true");
        registry.add("portal.ingest.tick", () -> "200ms");
        registry.add("portal.ingest.token-dir", TOKENS::toString);
        registry.add("portal.ingest.directory-username", () -> PostgresHarness.DIRECTORY);
        registry.add("portal.ingest.directory-password", () -> PostgresHarness.DIRECTORY_PASSWORD);
    }

    @AfterAll
    static void stopEngine() {
        ENGINE.close();
    }

    @Autowired
    MeterRegistry meters;

    @Test
    void theSchedulerPullsTheFeedOfATenantFromItsEngineAndAppliesIt() throws InterruptedException {
        IngestRig rig = new IngestRig();
        long deadline = System.nanoTime() + 30_000_000_000L;
        while (System.nanoTime() < deadline && lastSeq(rig) < 17) {
            Thread.sleep(100);
        }
        assertThat(lastSeq(rig)).isEqualTo(17);
        assertThat(rig.cursor(T).state()).isEqualTo(IngestState.RUNNING);
        assertThat(rig.events(T, "APPLIED")).hasSize(17);
        assertThat(ENGINE.authorizations()).isNotEmpty().allMatch(a -> a.equals("Bearer " + TOKEN));
        long waitMetric = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < waitMetric && meters.find("ingest_lag_seq").tag("tenant", T.value()).gauge() == null) {
            Thread.sleep(50);
        }
        assertThat(meters.find("ingest_lag_seq").tag("tenant", T.value()).gauge()).isNotNull()
                .satisfies(g -> assertThat(g.value()).isZero());
        assertThat(meters.find("ingest_events_total").tag("tenant", T.value()).tag("result", "applied").counter())
                .isNotNull().satisfies(c -> assertThat(c.count()).isEqualTo(17.0));
    }

    private static long lastSeq(IngestRig rig) {
        return rig.transactions.inTransaction(T, rig.store::cursor).map(c -> c.lastSeq()).orElse(0L);
    }
}
