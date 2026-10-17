package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.app.PortalApplication;
import com.ga.portal.domain.IngestState;
import com.ga.portal.infra.persistence.AuditAccessRepository;
import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.SeedData;
import com.ga.portal.projection.RecordingProjector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 명령을 실제 조립({@code cli} 프로파일 — 웹·폴링 없음)으로: 인자 → 명령 → 표준 출력·종료 코드 → 감사 행. 테넌트 목록은 전용 롤.
 * Phase 3(D-9): 행위자는 OS 사용자 — 시험은 JVM의 {@code user.name}을 허구 이름으로 잠시 바꾸고(실제 계정 이름이 감사 표·출력에 남지 않게) 그 이름을 테넌트의
 * OPERATOR로 등록한다. {@code --actor}는 64.
 */
class IngestCliRunnerIT {

    private static final PostgresHarness DB = PostgresHarness.get();
    private static final String OS_USER = "ph3-ops-cli";

    private static int run(ByteArrayOutputStream out, String... args) {
        PrintStream original = System.out;
        String user = System.getProperty("user.name");
        System.setProperty("user.name", OS_USER);
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        // 접속 정보는 명령행 인자로(application.yaml의 환경 기본값보다 우선) — 운영에서는 환경 변수로 준다
        List<String> all = new ArrayList<>(List.of("--spring.datasource.url=" + DB.jdbcUrl(), "--spring.flyway.url=" + DB.jdbcUrl(),
                "--portal.ingest.directory-username=" + PostgresHarness.DIRECTORY,
                "--portal.ingest.directory-password=" + PostgresHarness.DIRECTORY_PASSWORD));
        all.addAll(List.of(args));
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(PortalApplication.class)
                .profiles("cli")
                .run(all.toArray(String[]::new))) {
            return SpringApplication.exit(ctx);
        } finally {
            System.setOut(original);
            System.setProperty("user.name", user);
        }
    }

    @Test
    void theCliProfileRunsOneCommandAndAuditsIt() {
        IngestRig rig = new IngestRig();
        TenantId t = TenantId.of(SeedData.uniqueTenant("CLIRUN"));
        IngestRig.seedTenant(t, "https://engine.invalid", "{}");   // 목록(status, 테넌트 지정 없음)은 tenant 표에서 온다
        rig.worker(IngestRig.feed("gap.jsonl"), new RecordingProjector(), 200).runOnce(t);

        ByteArrayOutputStream unregistered = new ByteArrayOutputStream();
        assertThat(run(unregistered, "ingest", "resume", "--tenant=" + t.value(), "--from-seq=4")).as("OS user not yet an OPERATOR").isEqualTo(2);
        assertThat(unregistered.toString(StandardCharsets.UTF_8)).contains(t.value() + " not authorized");
        IngestRig.operator(t, "cli:" + OS_USER);

        ByteArrayOutputStream actor = new ByteArrayOutputStream();
        assertThat(run(actor, "ingest", "status", "--tenant=" + t.value(), "--actor=ops-kim")).as("--actor is gone").isEqualTo(64);

        ByteArrayOutputStream rejected = new ByteArrayOutputStream();
        assertThat(run(rejected, "ingest", "resume", "--tenant=" + t.value(), "--from-seq=9")).isEqualTo(2);
        assertThat(rejected.toString(StandardCharsets.UTF_8)).contains("beyond the last applied seq 4");

        ByteArrayOutputStream ok = new ByteArrayOutputStream();
        assertThat(run(ok, "ingest", "resume", "--tenant=" + t.value(), "--from-seq=4")).isZero();
        assertThat(ok.toString(StandardCharsets.UTF_8)).contains(t.value() + " RUNNING from seq 4");
        assertThat(rig.cursor(t).state()).isEqualTo(IngestState.RUNNING);

        ByteArrayOutputStream status = new ByteArrayOutputStream();
        assertThat(run(status, "ingest", "status", "--tenant=" + t.value())).isZero();
        assertThat(status.toString(StandardCharsets.UTF_8)).contains(t.value() + " RUNNING last_seq=4");

        // 전 테넌트: 이 OS 사용자가 등록되지 않은 테넌트는 "not authorized" 줄, 하나라도 있으면 2(Q10). 미등록 테넌트를 이 시험이 직접 만든다
        // (같은 DB의 다른 시험 테넌트 유무에 기대지 않는다 — 이 클래스가 먼저 돌면 테넌트가 t 하나뿐이다)
        TenantId unregisteredTenant = TenantId.of(SeedData.uniqueTenant("CLIRUN_X"));
        IngestRig.seedTenant(unregisteredTenant, "https://engine.invalid", "{}");
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        assertThat(run(all, "ingest", "status")).isEqualTo(2);
        assertThat(all.toString(StandardCharsets.UTF_8)).contains(t.value() + " RUNNING last_seq=4")
                .contains(unregisteredTenant.value() + " not authorized");

        ByteArrayOutputStream usage = new ByteArrayOutputStream();
        assertThat(run(usage, "ingest", "rewind")).isEqualTo(64);

        AuditAccessRepository audit = new AuditAccessRepository(rig.gateway);
        assertThat(rig.transactions.inTransaction(t, () -> audit.recent(10)))
                .extracting(AuditAccessRepository.AuditRow::action)
                .containsExactly(IngestCommands.STATUS, IngestCommands.STATUS, IngestCommands.RESUME, IngestCommands.RESUME_REJECTED,
                        OperatorAuthorization.ACTION);
    }
}
