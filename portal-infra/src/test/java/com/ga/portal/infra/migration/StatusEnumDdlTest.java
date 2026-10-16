package com.ga.portal.infra.migration;

import com.ga.portal.domain.DataStatus;
import com.ga.portal.domain.DeferralStatus;
import com.ga.portal.domain.IngestState;
import com.ga.portal.domain.MonthStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계획 승인 Q9: 닫힌 값 컬럼의 {@code CHECK (… IN …)} 목록이 그 값의 정본과 같다 — 두 정본이 어긋나지 않게(C10과 같은 방식).
 * <ul>
 *   <li>Java 열거가 있는 컬럼 4개: 열거 값 집합 = V1 CHECK 목록.</li>
 *   <li>열거가 없는 컬럼 8개: V1 CHECK 목록이 정본이고 설계서 §5 DDL 주석({@code -- A/B/C})과 대조.</li>
 *   <li>CHECK가 걸린 컬럼 집합 = 승인 문서의 12개 = 설계서 §5에서 닫힌 목록 주석이 달린 컬럼 집합({@code /...}로 끝나는 열린 목록은 제외 —
 *       예: {@code comm_type}·{@code role}).</li>
 * </ul>
 */
class StatusEnumDdlTest {

    private static final Pattern CHECK_IN = Pattern.compile(
            "CONSTRAINT ck_([a-z_]+?)_(status|data_status|state|kind|flag_type|severity) CHECK \\(\\2 IN \\(([^)]*)\\)\\)");

    private static final Map<String, Class<? extends Enum<?>>> ENUMS = Map.of(
            "earnings_month.status", MonthStatus.class,
            "earnings_month.data_status", DataStatus.class,
            "deferral_item.status", DeferralStatus.class,
            "ingest_cursor.state", IngestState.class);

    private static final Set<String> APPROVED_COLUMNS = Set.of(
            "earnings_month.status", "earnings_month.data_status", "deferral_item.status", "ingest_cursor.state",
            "ingest_event.status", "recon_result.status", "inquiry.status", "simulation_log.status", "tenant.status",
            "policy_limit_history.kind", "policy_flag.flag_type", "notification.severity");

    @Test
    void checkedColumnsAreExactlyTheApprovedTwelve() {
        assertThat(ddlChecks().keySet()).containsExactlyInAnyOrderElementsOf(APPROVED_COLUMNS);
    }

    @Test
    void enumBackedColumnsMatchTheirJavaEnums() {
        Map<String, Set<String>> ddl = ddlChecks();
        ENUMS.forEach((column, type) -> assertThat(ddl.get(column)).as(column)
                .containsExactlyElementsOf(Arrays.stream(type.getEnumConstants()).map(Enum::name).toList()));
    }

    @Test
    void everyCheckedColumnMatchesTheDesign5Comment() throws IOException {
        Map<String, Set<String>> design = designClosedLists();
        assertThat(design.keySet()).as("closed-list comments in design §5").containsExactlyInAnyOrderElementsOf(APPROVED_COLUMNS);
        ddlChecks().forEach((column, values) -> assertThat(values).as(column).containsExactlyElementsOf(design.get(column)));
    }

    /** V1의 {@code CONSTRAINT ck_<테이블>_<컬럼> CHECK (<컬럼> IN ('A', 'B'))} → 테이블.컬럼 → 값(순서 유지). */
    static Map<String, Set<String>> ddlChecks() {
        String v1;
        try (InputStream in = StatusEnumDdlTest.class.getClassLoader().getResourceAsStream("db/migration/V1__init.sql")) {
            assertThat(in).as("V1__init.sql on the classpath").isNotNull();
            v1 = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        Map<String, Set<String>> checks = new TreeMap<>();
        Matcher m = CHECK_IN.matcher(v1);
        while (m.find()) {
            Set<String> values = new LinkedHashSet<>();
            for (String v : m.group(3).split(",")) {
                values.add(v.strip().replace("'", ""));
            }
            assertThat(checks.put(m.group(1) + "." + m.group(2), values)).as("duplicate check for %s.%s", m.group(1), m.group(2)).isNull();
        }
        return checks;
    }

    /** 설계서 §5의 SQL 블록에서 {@code CREATE TABLE} 안의 닫힌 목록 주석 — 주석은 그 줄의 마지막 컬럼에 붙는다. */
    static Map<String, Set<String>> designClosedLists() throws IOException {
        String design = Files.readString(Path.of(System.getProperty("ga.repoRoot")).resolve("docs/설계서.md"));
        String section = design.substring(design.indexOf("## 5. 읽기 모델"), design.indexOf("## 6. 핵심 로직"));
        Pattern table = Pattern.compile("CREATE TABLE ([a-z_]+) \\(");
        Pattern column = Pattern.compile("([a-z_]+)\\s+[A-Z]+[^,]*,");
        Pattern closed = Pattern.compile("--\\s*([A-Z_]+(?:/[A-Z_]+)+)\\s*$");
        Map<String, Set<String>> lists = new TreeMap<>();
        String current = null;
        for (String line : section.split("\n")) {
            Matcher t = table.matcher(line);
            if (t.find()) {
                current = t.group(1);
            }
            Matcher c = closed.matcher(line);
            if (current != null && c.find()) {
                String code = line.substring(0, line.indexOf("--"));
                Matcher col = column.matcher(code);
                String last = null;
                while (col.find()) {
                    last = col.group(1);
                }
                assertThat(last).as("column before the comment: %s", line).isNotNull();
                lists.put(current + "." + last, new LinkedHashSet<>(Arrays.asList(c.group(1).split("/"))));
            }
        }
        return lists;
    }
}
