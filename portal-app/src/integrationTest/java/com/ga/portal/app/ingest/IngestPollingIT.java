package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.IngestState;
import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.SeedData;
import com.ga.portal.ingest.testing.Samples;
import com.ga.portal.ingest.testing.StubFeedServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
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
 * 서비스 토큰으로 피드를 불러 엔진 A-1 11건을 적용한다(Phase 2부터 조립된 프로젝터가 뷰를 쓴다 — 정규 스트림의 단건 예시는 서로 맞물리지 않아
 * 독이 되므로 엔진이 실제로 낸 스트림을 쓴다). 지표({@code ingest_lag_seq} = 0)와 뷰 행까지. 컨텍스트는 이 클래스 뒤에 닫는다(스케줄러가 다른
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
        ENGINE.events(Samples.a1());
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

    @Autowired
    InMemorySpanExporter spans;

    @Autowired
    SdkTracerProvider tracerProvider;

    /** 포털 Phase 7 C8: 자동 구성된 추적기(브리지 → SDK)에 메모리 내보내기를 붙이고 전부 표본으로 뽑는다(운영 기본은 내보내기 없음 — Phase 8). */
    @TestConfiguration(proxyBeanMethods = false)
    static class Spans {
        @Bean
        InMemorySpanExporter inMemorySpanExporter() {
            return InMemorySpanExporter.create();
        }

        @Bean
        Sampler alwaysOn() {
            return Sampler.alwaysOn();
        }
    }

    @Test
    void theSchedulerPullsTheFeedOfATenantFromItsEngineAndAppliesIt() throws InterruptedException {
        IngestRig rig = new IngestRig();
        long deadline = System.nanoTime() + 30_000_000_000L;
        while (System.nanoTime() < deadline && lastSeq(rig) < 11) {
            Thread.sleep(100);
        }
        assertThat(lastSeq(rig)).isEqualTo(11);
        assertThat(rig.cursor(T).state()).isEqualTo(IngestState.RUNNING);
        assertThat(rig.events(T, "APPLIED")).hasSize(11);
        // 조립된 뷰 프로젝터: 8월 PAID·2월 PROVISIONAL, 원장 누적 1,080,000(A-1)
        java.util.List<String> months = DB.asApp(T.value(), c -> {
            try (var ps = c.prepareStatement("SELECT close_ym || ':' || status FROM earnings_month ORDER BY close_ym"); var rs = ps.executeQuery()) {
                java.util.List<String> out = new java.util.ArrayList<>();
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
                return out;
            }
        });
        assertThat(months).containsExactly("202608:PAID", "202702:ESTIMATED");
        assertThat(ENGINE.authorizations()).isNotEmpty().allMatch(a -> a.equals("Bearer " + TOKEN));
        long waitMetric = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < waitMetric && meters.find("ingest_lag_seq").tag("tenant", T.value()).gauge() == null) {
            Thread.sleep(50);
        }
        assertThat(meters.find("ingest_lag_seq").tag("tenant", T.value()).gauge()).isNotNull()
                .satisfies(g -> assertThat(g.value()).isZero());
        assertThat(meters.find("ingest_events_total").tag("tenant", T.value()).tag("result", "applied").counter())
                .isNotNull().satisfies(c -> assertThat(c.count()).isEqualTo(11.0));
        // 포털 Phase 7(docs/지표.md): notifications_derived_total = 커밋해 넣은 알림 행(종류별 합 = 테넌트의 알림 행 수)
        java.util.List<String> rows = IngestRig.notificationRows(T);
        assertThat(rows).isNotEmpty();
        java.util.Map<String, Long> byType = rows.stream().collect(java.util.stream.Collectors.groupingBy(r -> r.split("\\|", -1)[2],
                java.util.TreeMap::new, java.util.stream.Collectors.counting()));
        java.util.Map<String, Long> counted = new java.util.TreeMap<>();
        meters.find("notifications_derived_total").tag("tenant", T.value()).counters()
                .forEach(c -> counted.put(c.getId().getTag("type"), (long) c.count()));
        assertThat(counted).isEqualTo(byType);
        // 포털 Phase 7 C8(docs/지표.md §4): 운영 조립의 스팬 — 이벤트마다 ingest.apply와 그 아래 project·derive·cursor, 피드 호출 engine.feed
        assertThat(tracerProvider.forceFlush().join(10, java.util.concurrent.TimeUnit.SECONDS).isSuccess()).isTrue();
        java.util.List<io.opentelemetry.sdk.trace.data.SpanData> finished = spans.getFinishedSpanItems();
        java.util.Map<String, String> nameById = finished.stream().collect(java.util.stream.Collectors.toMap(
                io.opentelemetry.sdk.trace.data.SpanData::getSpanId, io.opentelemetry.sdk.trace.data.SpanData::getName));
        java.util.List<io.opentelemetry.sdk.trace.data.SpanData> applied = finished.stream()
                .filter(sp -> sp.getName().equals("ingest.apply") && T.value().equals(sp.getAttributes().get(
                        io.opentelemetry.api.common.AttributeKey.stringKey("tenant")))).toList();
        assertThat(applied).as("one ingest.apply per event of this tenant").hasSizeGreaterThanOrEqualTo(11);
        for (String child : java.util.List.of("ingest.project", "ingest.derive", "ingest.cursor")) {
            assertThat(finished.stream().filter(sp -> sp.getName().equals(child)).map(sp -> nameById.get(sp.getParentSpanId())))
                    .as(child).isNotEmpty().containsOnly("ingest.apply");
        }
        assertThat(finished).anyMatch(sp -> sp.getName().equals("engine.feed")
                && "OK".equals(sp.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("outcome"))));
        assertThat(meters.getMeters()).as("spans are not Observations — no ingest.apply timer outside the catalog")
                .noneMatch(m -> m.getId().getName().startsWith("ingest.") || m.getId().getName().startsWith("recon.")
                        || m.getId().getName().startsWith("engine."));
    }

    private static long lastSeq(IngestRig rig) {
        return rig.transactions.inTransaction(T, rig.store::cursor).map(c -> c.lastSeq()).orElse(0L);
    }
}
