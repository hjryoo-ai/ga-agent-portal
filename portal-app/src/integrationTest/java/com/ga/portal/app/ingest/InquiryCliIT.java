package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.app.PortalApplication;
import com.ga.portal.app.cli.InquiryCommands;
import com.ga.portal.app.cli.OperatorAuthorization;
import com.ga.portal.infra.persistence.AuditAccessRepository;
import com.ga.portal.infra.persistence.InquiryOperatorRepository;
import com.ga.portal.infra.persistence.NotificationRepository;
import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.SeedData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C9·C7(포털 Phase 6 ⑦): 문의 운영 CLI를 실제 조립({@code cli} 프로파일, 실행기 하나)으로 — {@link IngestCliRunnerIT}와 같이 JVM의 {@code user.name}을 허구 이름으로
 * 잠시 바꾸고 표준 출력을 버퍼로 잡는다(출력·로그가 시험 결과 XML에 실리지 않는다 — 실리면 평문 스캔이 본문 센티널을 잡는다).
 * <ul>
 *   <li>미등록 운영자 → 2 + {@code AUTHZ_DENIED}({@code cli:inquiry list}).</li>
 *   <li>{@code list}: 한 줄 = 한 문의(탭 구분, 본문의 줄바꿈·탭은 글자로) — 본문 센티널이 <b>여기에는</b> 있다.</li>
 *   <li>{@code answer}: 파일의 답변 → {@code ANSWERED}(답한 이 = {@code cli:<user>}) + 알림 {@code INQUIRY:<id>} + 감사, 출력 한 줄 — 출력·로그·감사에 답변 0.
 *       다시 답하면 2, {@code --answer}는 64.</li>
 *   <li>{@code close}: {@code ANSWERED}·{@code OPEN → CLOSED}, 닫힌 것은 2.</li>
 *   <li>알림을 넣지 못하면 문의도 {@code OPEN}이고 감사·알림이 없다(한 트랜잭션 — 주입 S11의 짝).</li>
 * </ul>
 */
class InquiryCliIT {

    private static final PostgresHarness DB = PostgresHarness.get();
    private static final String OS_USER = "ph6-ops-cli";
    private static final String AGENT = "PH6-C-8818";
    private static final UUID OPEN_1 = UUID.fromString("00000000-0000-4000-8000-0000000006c1");
    private static final UUID OPEN_2 = UUID.fromString("00000000-0000-4000-8000-0000000006c2");
    private static final UUID OPEN_3 = UUID.fromString("00000000-0000-4000-8000-0000000006c3");

    @TempDir
    Path dir;

    static List<String> sentinels(String prefix) {
        Properties p = new Properties();
        try (InputStream in = InquiryCliIT.class.getClassLoader().getResourceAsStream("pii-sentinels.properties")) {
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return List.of(p.getProperty("inbox").split(",")).stream().map(String::trim).filter(s -> s.startsWith(prefix)).toList();
    }

    private static int run(ByteArrayOutputStream out, String... args) {
        PrintStream original = System.out;
        String user = System.getProperty("user.name");
        System.setProperty("user.name", OS_USER);
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        List<String> all = new ArrayList<>(List.of("--spring.datasource.url=" + DB.jdbcUrl(), "--spring.flyway.url=" + DB.jdbcUrl(),
                "--portal.ingest.directory-username=" + PostgresHarness.DIRECTORY,
                "--portal.ingest.directory-password=" + PostgresHarness.DIRECTORY_PASSWORD));
        all.addAll(List.of(args));
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(PortalApplication.class).profiles("cli").run(all.toArray(String[]::new))) {
            return SpringApplication.exit(ctx);
        } finally {
            System.setOut(original);
            System.setProperty("user.name", user);
        }
    }

    static void inquiry(TenantId t, UUID id, String category, String calcId, String message, String createdAt) {
        DB.seed(t.value(), c -> SeedData.exec(c, """
                INSERT INTO inquiry (tenant_id, inquiry_id, agent_id, calc_id, category, message, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'OPEN', ?::timestamptz)
                """, t.value(), id, AGENT, calcId, category, message, createdAt));
    }

    /** 상태·답한 이·답변 길이·답변 시각 유무 — 답변 원문은 꺼내지 않는다(비교만). */
    static String state(TenantId t, UUID id, String expectedAnswer) {
        return DB.asApp(t.value(), c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT status, coalesce(answered_by, '-'), answer IS NOT DISTINCT FROM ?, answered_at IS NOT NULL FROM inquiry
                     WHERE tenant_id = ? AND inquiry_id = ?
                    """)) {
                ps.setString(1, expectedAnswer);
                ps.setString(2, t.value());
                ps.setObject(3, id);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getBoolean(3) + "|" + rs.getBoolean(4);
                }
            }
        });
    }

    @Test
    void theOperatorListsAnswersAndClosesWithOneLineEachAndTheAnswerNeverEchoes() throws IOException {
        IngestRig rig = new IngestRig();
        TenantId t = TenantId.of(SeedData.uniqueTenant("INQCLI"));
        IngestRig.seedTenant(t);
        String body = sentinels("문의센티널-").get(3) + " 금액 확인\n둘째 줄\t탭";
        String answer = "확인 결과 정상입니다 " + sentinels("답변센티널-").getFirst();
        inquiry(t, OPEN_1, "AMOUNT", "PH6-C-8818-1", body, "2026-10-20 01:00:00+00");
        inquiry(t, OPEN_2, "OTHER", null, "두 번째 문의", "2026-10-20 02:00:00+00");
        Path answerFile = Files.writeString(dir.resolve("answer.txt"), answer + "\n", StandardCharsets.UTF_8);

        ByteArrayOutputStream unregistered = new ByteArrayOutputStream();
        assertThat(run(unregistered, "inquiry", "list", "--tenant=" + t.value())).isEqualTo(2);
        assertThat(unregistered.toString(StandardCharsets.UTF_8)).contains(t.value() + " not authorized").doesNotContain(sentinels("문의센티널-").get(3));
        IngestRig.operator(t, "cli:" + OS_USER);

        ByteArrayOutputStream list = new ByteArrayOutputStream();
        assertThat(run(list, "inquiry", "list", "--tenant=" + t.value())).isZero();
        List<String> lines = list.toString(StandardCharsets.UTF_8).lines().filter(l -> l.startsWith("00000000-0000-4000-8000-0000000006c")).toList();
        assertThat(lines).hasSize(2);
        assertThat(lines.getFirst()).isEqualTo(String.join("\t", OPEN_1.toString(), AGENT, "PH6-C-8818-1", "AMOUNT", "OPEN", "2026-10-20T01:00:00Z",
                sentinels("문의센티널-").get(3) + " 금액 확인\\n둘째 줄\\t탭"));
        assertThat(lines.get(1)).startsWith(OPEN_2 + "\t" + AGENT + "\t-\tOTHER\tOPEN\t");

        ByteArrayOutputStream inline = new ByteArrayOutputStream();
        assertThat(run(inline, "inquiry", "answer", "--tenant=" + t.value(), "--id=" + OPEN_1, "--answer=" + "인자 답변")).as("no --answer").isEqualTo(64);
        assertThat(state(t, OPEN_1, null)).isEqualTo("OPEN|-|true|false");

        ByteArrayOutputStream answered = new ByteArrayOutputStream();
        assertThat(run(answered, "inquiry", "answer", "--tenant=" + t.value(), "--id=" + OPEN_1, "--answer-file=" + answerFile)).isZero();
        String answeredOut = answered.toString(StandardCharsets.UTF_8);
        assertThat(answeredOut).contains("inquiry " + OPEN_1 + " ANSWERED").doesNotContain(sentinels("답변센티널-").getFirst())
                .doesNotContain(sentinels("문의센티널-").get(3));
        assertThat(state(t, OPEN_1, answer)).as("one trailing newline dropped").isEqualTo("ANSWERED|cli:" + OS_USER + "|true|true");
        assertThat(IngestRig.notificationRows(t)).singleElement().satisfies(r -> assertThat(r).contains("|" + AGENT + "|INQUIRY_ANSWERED|INFO|INQUIRY:" + OPEN_1
                + "|/inquiries/" + OPEN_1 + "|{\"inquiryId\": \"" + OPEN_1 + "\"}|"));

        ByteArrayOutputStream again = new ByteArrayOutputStream();
        assertThat(run(again, "inquiry", "answer", "--tenant=" + t.value(), "--id=" + OPEN_1, "--answer-file=" + answerFile)).isEqualTo(2);
        assertThat(again.toString(StandardCharsets.UTF_8)).contains("is ANSWERED — only OPEN is answered");
        ByteArrayOutputStream missing = new ByteArrayOutputStream();
        assertThat(run(missing, "inquiry", "answer", "--tenant=" + t.value(), "--id=" + OPEN_3, "--answer-file=" + answerFile)).isEqualTo(2);
        assertThat(missing.toString(StandardCharsets.UTF_8)).contains("inquiry " + OPEN_3 + " not found");

        ByteArrayOutputStream closed = new ByteArrayOutputStream();
        assertThat(run(closed, "inquiry", "close", "--tenant=" + t.value(), "--id=" + OPEN_1)).isZero();
        assertThat(closed.toString(StandardCharsets.UTF_8)).contains("inquiry " + OPEN_1 + " CLOSED");
        assertThat(run(new ByteArrayOutputStream(), "inquiry", "close", "--tenant=" + t.value(), "--id=" + OPEN_2)).as("OPEN closes too").isZero();
        assertThat(run(new ByteArrayOutputStream(), "inquiry", "close", "--tenant=" + t.value(), "--id=" + OPEN_2)).as("closed is the end").isEqualTo(2);
        assertThat(state(t, OPEN_1, answer)).startsWith("CLOSED|cli:" + OS_USER + "|true|");

        ByteArrayOutputStream onlyClosed = new ByteArrayOutputStream();
        assertThat(run(onlyClosed, "inquiry", "list", "--tenant=" + t.value(), "--status=CLOSED")).isZero();
        assertThat(onlyClosed.toString(StandardCharsets.UTF_8).lines().filter(l -> l.startsWith("00000000-"))).hasSize(2);

        AuditAccessRepository audit = new AuditAccessRepository(rig.gateway);
        List<AuditAccessRepository.AuditRow> rows = rig.transactions.inTransaction(t, () -> audit.recent(20)).reversed();
        assertThat(rows).extracting(AuditAccessRepository.AuditRow::action, AuditAccessRepository.AuditRow::target).containsExactly(
                org.assertj.core.groups.Tuple.tuple(OperatorAuthorization.ACTION, "cli:inquiry list"),
                org.assertj.core.groups.Tuple.tuple(InquiryCommands.LIST, "inquiry:ALL"),
                org.assertj.core.groups.Tuple.tuple(InquiryCommands.ANSWER, "inquiry:" + OPEN_1),
                org.assertj.core.groups.Tuple.tuple(InquiryCommands.ANSWER_REJECTED, "inquiry:" + OPEN_1),
                org.assertj.core.groups.Tuple.tuple(InquiryCommands.ANSWER_REJECTED, "inquiry:" + OPEN_3),
                org.assertj.core.groups.Tuple.tuple(InquiryCommands.CLOSE, "inquiry:" + OPEN_1),
                org.assertj.core.groups.Tuple.tuple(InquiryCommands.CLOSE, "inquiry:" + OPEN_2),
                org.assertj.core.groups.Tuple.tuple(InquiryCommands.CLOSE_REJECTED, "inquiry:" + OPEN_2),
                org.assertj.core.groups.Tuple.tuple(InquiryCommands.LIST, "inquiry:CLOSED"));
        assertThat(rows).allSatisfy(r -> assertThat(r.target() + "|" + r.detail()).doesNotContain(sentinels("답변센티널-").getFirst())
                .doesNotContain(sentinels("문의센티널-").get(3)));
        assertThat(rows.stream().filter(r -> !r.action().equals(OperatorAuthorization.ACTION))).allSatisfy(r -> assertThat(r.detail()).isNull());
    }

    @Test
    void whenTheNotificationCannotBeWrittenTheInquiryStaysOpen() {
        IngestRig rig = new IngestRig();
        TenantId t = TenantId.of(SeedData.uniqueTenant("INQCLI_F"));
        IngestRig.seedTenant(t);
        IngestRig.operator(t, "cli:" + OS_USER);
        inquiry(t, OPEN_1, "OTHER", null, "롤백 시험 문의", "2026-10-20 01:00:00+00");
        NotificationRepository failing = new NotificationRepository(rig.gateway) {
            @Override
            public boolean insertIfAbsent(Fresh row) {
                throw new IllegalStateException("notification insert failed");
            }
        };
        AuditAccessRepository audit = new AuditAccessRepository(rig.gateway);
        InquiryCommands commands = new InquiryCommands(rig.transactions, new InquiryOperatorRepository(rig.gateway), failing, audit, rig.links,
                IngestRig.CLOCK);
        assertThatThrownBy(() -> commands.answer(t, OPEN_1, "롤백될 답변", "cli:" + OS_USER)).isInstanceOf(IllegalStateException.class);
        assertThat(state(t, OPEN_1, null)).isEqualTo("OPEN|-|true|false");
        assertThat(IngestRig.notificationRows(t)).isEmpty();
        assertThat(rig.transactions.inTransaction(t, () -> audit.recent(10))).isEmpty();
    }
}
