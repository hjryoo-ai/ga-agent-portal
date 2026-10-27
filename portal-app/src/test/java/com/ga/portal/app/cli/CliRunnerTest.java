package com.ga.portal.app.cli;

import com.ga.portal.app.ingest.IngestCommands;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 명령 인자: 사용법(64)과 행위자 검사(2)는 DB에 닿기 전에 끝난다(명령 객체 없이 — 닿으면 NPE). Phase 3(D-9·Q10): 행위자는 OS 사용자뿐 — {@code --actor}는
 * 사용법 오류(64), OS 사용자가 없거나 형식 밖이면 거부(2). 포털 Phase 6(Q28): 실행기 하나가 {@code ingest}·{@code inquiry}를 받는다 — 답변은 파일로만
 * ({@code --answer}는 64, C9·주입 S12), 파일 요건(16 KiB·엄격 UTF-8·1~4,000 코드 포인트·공백만 아님·NUL 없음) 밖은 64이고 메시지에 내용이 없다.
 */
class CliRunnerTest {

    private final CliRunner runner = new CliRunner(null, null, new PrintStream(OutputStream.nullOutputStream()), "ops-kim");
    private final CliRunner noUser = new CliRunner(null, null, new PrintStream(OutputStream.nullOutputStream()), "");

    static Map<String, String> opts(String... kv) {
        java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void usageErrorsExitWith64() {
        assertThat(runner.execute(List.of(), opts()).exitCode()).isEqualTo(64);
        assertThat(runner.execute(List.of("ingest", "rewind"), opts()).exitCode()).isEqualTo(64);
        assertThat(runner.execute(List.of("ingest", "resume"), opts()).lines()).contains("invalid or missing --tenant");
        assertThat(runner.execute(List.of("ingest", "resume"), opts("tenant", "GA01", "from-seq", "x")).exitCode()).isEqualTo(64);
        assertThat(runner.execute(List.of("ingest", "pause"), opts("tenant", "ga01")).exitCode()).as("tenant id format").isEqualTo(64);
        assertThat(runner.execute(List.of("notify", "list"), opts()).exitCode()).isEqualTo(64);
        assertThat(runner.execute(List.of("inquiry", "delete"), opts("tenant", "GA01")).exitCode()).isEqualTo(64);
        assertThat(runner.execute(List.of("inquiry", "list"), opts()).lines()).contains("invalid or missing --tenant");
        assertThat(runner.execute(List.of("inquiry", "list"), opts("tenant", "GA01", "status", "PENDING")).lines()).contains("invalid or missing --status");
        assertThat(runner.execute(List.of("inquiry", "close"), opts("tenant", "GA01", "id", "not-a-uuid")).lines()).contains("invalid or missing --id");
        assertThat(runner.execute(List.of("inquiry", "answer"), opts("tenant", "GA01", "id", "00000000-0000-4000-8000-000000000001")).lines())
                .contains("invalid or missing --answer-file");
    }

    @Test
    void theAnswerComesFromAFileOnly() {
        IngestCommands.Outcome o = runner.execute(List.of("inquiry", "answer"),
                opts("tenant", "GA01", "id", "00000000-0000-4000-8000-000000000001", "answer", "확인했습니다"));
        assertThat(o.exitCode()).isEqualTo(64);
        assertThat(o.lines()).contains("unknown option --answer").noneMatch(l -> l.contains("확인했습니다"));
        assertThat(runner.execute(List.of("inquiry", "list"), opts("tenant", "GA01", "agent", "A-1")).exitCode()).as("unknown option").isEqualTo(64);
    }

    @Test
    void anAnswerFileOutsideTheRulesIsAUsageErrorWithoutItsContent(@TempDir Path dir) throws IOException {
        String id = "00000000-0000-4000-8000-000000000001";
        Path missing = dir.resolve("missing.txt");
        Path empty = Files.writeString(dir.resolve("empty.txt"), "\n");
        Path blank = Files.writeString(dir.resolve("blank.txt"), "  \t \n");
        Path tooLong = Files.writeString(dir.resolve("long.txt"), "가".repeat(4_001));
        Path tooBig = Files.write(dir.resolve("big.txt"), new byte[16 * 1024 + 1]);
        Path badUtf8 = Files.write(dir.resolve("bad.txt"), new byte[] {'o', 'k', (byte) 0xC3, (byte) 0x28});
        Path nul = Files.writeString(dir.resolve("nul.txt"), "답변\u0000센티널");
        for (Path p : List.of(missing, empty, blank, tooLong, tooBig, badUtf8, nul)) {
            IngestCommands.Outcome o = runner.execute(List.of("inquiry", "answer"), opts("tenant", "GA01", "id", id, "answer-file", p.toString()));
            assertThat(o.exitCode()).as(p.getFileName().toString()).isEqualTo(64);
            assertThat(String.join("\n", o.lines())).doesNotContain("가가").doesNotContain("센티널");
        }
    }

    @Test
    void theAnswerFileKeepsItsTextAndDropsOneTrailingNewline(@TempDir Path dir) throws Exception {
        assertThat(AnswerFile.read(Files.writeString(dir.resolve("a.txt"), "첫 줄\n둘째 줄\n", StandardCharsets.UTF_8))).isEqualTo("첫 줄\n둘째 줄");
        assertThat(AnswerFile.read(Files.writeString(dir.resolve("b.txt"), "답변\r\n", StandardCharsets.UTF_8))).isEqualTo("답변");
        String emoji = "😀".repeat(4_000);
        assertThat(AnswerFile.read(Files.writeString(dir.resolve("c.txt"), emoji, StandardCharsets.UTF_8))).as("4,000 code points, 16,000 bytes")
                .isEqualTo(emoji);
    }

    @Test
    void theActorOptionIsGoneAndIsAUsageError() {
        IngestCommands.Outcome o = runner.execute(List.of("ingest", "status"), opts("actor", "someone-else"));
        assertThat(o.exitCode()).isEqualTo(64);
        assertThat(o.lines()).contains(CliRunner.ACTOR_REMOVED);
        assertThat(noUser.execute(List.of("ingest", "status"), opts("actor", "ops")).exitCode()).as("--actor cannot stand in for a missing OS user")
                .isEqualTo(64);
        assertThat(runner.execute(List.of("inquiry", "list"), opts("tenant", "GA01", "actor", "x")).lines()).contains(CliRunner.ACTOR_REMOVED);
    }

    @Test
    void aValidOsUserIsRequired() {
        assertThat(noUser.execute(List.of("ingest", "status"), opts()).exitCode()).isEqualTo(2);
        assertThat(noUser.execute(List.of("inquiry", "list"), opts("tenant", "GA01")).exitCode()).isEqualTo(2);
        assertThat(new CliRunner(null, null, new PrintStream(OutputStream.nullOutputStream()), "o p")
                .execute(List.of("ingest", "status"), opts()).exitCode()).isEqualTo(2);
        assertThat(IngestCommands.actorSubject("ops-kim")).contains("cli:ops-kim");
        assertThat(IngestCommands.actorSubject("x".repeat(65))).isEmpty();
        assertThat(IngestCommands.actorSubject("")).isEmpty();
    }

    @Test
    void aListLineIsOneLinePerInquiry() {
        assertThat(InquiryCommands.escape("a\tb\nc\\d\r")).isEqualTo("a\\tb\\nc\\\\d\\r");
    }

    /** 보안 검토(CWE-150): 설계사가 쓴 글은 운영자 터미널을 조작하지 못한다 — 제어·서식·구분 문자는 전부 보이는 \\uXXXX. 한글·이모지는 그대로. */
    @Test
    void terminalControlsInABodyArePrintedAsVisibleEscapes() {
        String attack = "\u001B]0;title\u0007\u001B[2J\u009B31m\u202Eevil\u200B\u2028\u007F" + new String(Character.toChars(0xE0041)) + "끝😀";
        String out = InquiryCommands.escape(attack);
        assertThat(out).isEqualTo("\\u001B]0;title\\u0007\\u001B[2J\\u009B31m\\u202Eevil\\u200B\\u2028\\u007F\\U000E0041끝😀");
        assertThat(out.codePoints()).noneMatch(cp -> Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT);
    }
}
