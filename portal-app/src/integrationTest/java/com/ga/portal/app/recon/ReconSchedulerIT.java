package com.ga.portal.app.recon;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.app.ingest.IngestRigAccess;
import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.SeedData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포털 Phase 7 P-11(계획 Q9): 앱 안 스케줄러가 시계를 따른다 — 시계를 KST 01:00에 고정하고 이 테넌트만 {@code recon_hour_kst = 1}(다른 시험 테넌트는 기본 4라
 * 아직 때가 아니다). 1분 틱을 200ms로 줄여 여러 번 돌려도 그 테넌트의 스케줄 회차는 그날 하나({@code SCHEDULE}, 행위자 {@code recon:<tenant>}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "portal.recon.scheduler-enabled=true", "portal.recon.tick=200ms"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReconSchedulerIT {

    private static final PostgresHarness DB = PostgresHarness.get();
    private static final TenantId T = TenantId.of(SeedData.uniqueTenant("RCN_SCHED"));
    /** 2026-10-27 01:00 KST. */
    private static final Instant AT = Instant.parse("2026-10-26T16:00:00Z");

    @TestBean
    Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler;

    static Clock clock() {
        return Clock.fixed(AT, java.time.ZoneOffset.UTC);
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        IngestRigAccess.seedTenant(T, "http://127.0.0.1:1", "{\"recon_hour_kst\": 1}");
        registry.add("spring.datasource.url", DB::jdbcUrl);
        registry.add("spring.flyway.url", DB::jdbcUrl);
        registry.add("portal.ingest.directory-username", () -> PostgresHarness.DIRECTORY);
        registry.add("portal.ingest.directory-password", () -> PostgresHarness.DIRECTORY_PASSWORD);
    }

    private static List<String> runs() {
        return DB.asApp(T.value(), c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT trigger || '|' || actor || '|' || run_at FROM recon_run WHERE tenant_id = ? ORDER BY run_at")) {
                ps.setString(1, T.value());
                try (ResultSet rs = ps.executeQuery()) {
                    List<String> out = new ArrayList<>();
                    while (rs.next()) {
                        out.add(rs.getString(1));
                    }
                    return out;
                }
            }
        });
    }

    @Test
    void theSchedulerRunsTheDueTenantOnceThatDay() throws InterruptedException {
        long deadline = System.nanoTime() + 30_000_000_000L;
        while (System.nanoTime() < deadline && runs().isEmpty()) {
            Thread.sleep(100);
        }
        Thread.sleep(1_500);   // 일곱 틱 더
        assertThat(runs()).singleElement().satisfies(r -> assertThat(r).startsWith("SCHEDULE|recon:" + T.value() + "|"));
    }

    @Test
    void ingestAndReconTicksHaveTheirOwnThreads() {
        // P7-5: 기본 스케줄러 스레드 하나면 대사 회차 동안 모든 테넌트의 폴링이 멈춘다
        assertThat(taskScheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(2);
    }
}
