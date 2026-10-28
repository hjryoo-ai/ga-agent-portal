package com.ga.portal.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포털 Phase 7 C8(계획 ⑤ — Phase 6 수용 Q4 "한 이름에 한 집합"): {@code docs/지표.md}의 닫힌 표와 모든 모듈의 원천이 등록하는 지표가 같다.
 * <ul>
 *   <li>등록 문장 = {@code counter("이름", "키", 값, …)}·{@code Gauge.builder("이름", …).tag("키", …)…}·{@code Timer.builder("이름").tag("키", …)…} — 이름은 글자
 *       그대로여야 한다(변수 이름의 등록은 찾을 수 없으므로 실패한다).</li>
 *   <li>표에 없는 등록 0, 등록이 없는 표 행 0, 종류가 같고, 같은 이름의 등록은 모두 같은 태그 키 집합이며 그것이 표의 집합이다.</li>
 *   <li>태그 키는 닫힌 목록(표의 키 합집합) — 설계사·달·증권번호 같은 키는 표에 없으므로 등록할 수 없다.</li>
 * </ul>
 */
class MetricsCatalogTest {

    private static final Path ROOT = Path.of(System.getProperty("ga.repoRoot"));
    private static final Pattern ROW = Pattern.compile("^\\| `([a-z0-9_]+)` \\| (counter|gauge|timer) \\| ([^|]*) \\|");
    private static final Pattern START = Pattern.compile("(\\bcounter|Gauge\\.builder|Timer\\.builder)\\(");
    private static final Pattern ANY_NAME = Pattern.compile("(?:\\bcounter|Gauge\\.builder|Timer\\.builder)\\(\\s*([^,)\\s]+)");
    private static final Pattern TAG = Pattern.compile("\\.tag\\(\\s*\"([a-z_]+)\"");

    record Entry(String kind, Set<String> keys) {
    }

    static Map<String, Entry> catalog() throws IOException {
        Map<String, Entry> out = new TreeMap<>();
        for (String line : Files.readAllLines(ROOT.resolve("docs/지표.md"))) {
            Matcher m = ROW.matcher(line);
            if (m.find()) {
                Set<String> keys = new TreeSet<>();
                Matcher k = Pattern.compile("`([a-z_]+)`").matcher(m.group(3));
                while (k.find()) {
                    keys.add(k.group(1));
                }
                assertThat(out.put(m.group(1), new Entry(m.group(2), keys))).as("one row per name: " + m.group(1)).isNull();
            }
        }
        return out;
    }

    record Registration(String file, String name, String kind, Set<String> keys) {
    }

    static List<Path> sources() {
        try (Stream<Path> walk = Files.walk(ROOT)) {
            return walk.filter(p -> p.toString().endsWith(".java") && p.toString().contains("/src/main/java/") && !p.toString().contains("/build/")
                    && !p.toString().contains("/node_modules/")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<Registration> registrations() throws IOException {
        List<Registration> out = new ArrayList<>();
        for (Path p : sources()) {
            String text = Files.readString(p);
            Matcher any = ANY_NAME.matcher(text);
            while (any.find()) {
                assertThat(any.group(1)).as("%s registers a meter whose name is not a literal: %s", ROOT.relativize(p), any.group(1)).startsWith("\"");
            }
            Matcher m = START.matcher(text);
            while (m.find()) {
                int open = m.end() - 1;
                int close = matching(text, open);
                List<String> args = split(text.substring(open + 1, close));
                String name = unquote(args.getFirst());
                Set<String> keys = new TreeSet<>();
                String kind = switch (m.group(1)) {
                    case "counter" -> {
                        for (int i = 1; i < args.size(); i += 2) {
                            keys.add(unquote(args.get(i)));
                        }
                        yield "counter";
                    }
                    case "Gauge.builder" -> "gauge";
                    default -> "timer";
                };
                if (!kind.equals("counter")) {
                    keys.addAll(chainTags(text, close + 1, ROOT.relativize(p) + " " + name));
                }
                out.add(new Registration(ROOT.relativize(p).toString(), name, kind, keys));
            }
        }
        return out;
    }

    /** 빌더 바로 뒤의 호출 사슬만 읽는다 — {@code .tag("키", …)}를 모으고 {@code .register(}에서 멈춘다. {@code .tags(…)}처럼 키가 글자가 아닌 호출은 실패. */
    private static Set<String> chainTags(String text, int from, String where) {
        Set<String> keys = new TreeSet<>();
        Matcher c = Pattern.compile("\\G\\s*\\.\\s*([A-Za-z]+)\\(").matcher(text);
        int at = from;
        while (c.find(at)) {
            String method = c.group(1);
            int close = matching(text, c.end() - 1);
            if (method.equals("register")) {
                return keys;
            }
            assertThat(method).as(where + " — only .tag(\"key\", …) in the builder chain").isIn("tag", "description", "baseUnit");
            if (method.equals("tag")) {
                Matcher t = TAG.matcher(text.substring(c.start(), close + 1));
                assertThat(t.find()).as(where + " — a literal tag key").isTrue();
                keys.add(t.group(1));
            }
            at = close + 1;
        }
        throw new AssertionError(where + " — the builder chain does not reach .register(");
    }

    private static int matching(String text, int open) {
        int depth = 0;
        boolean quoted = false;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && text.charAt(i - 1) != '\\') {
                quoted = !quoted;
            } else if (!quoted && c == '(') {
                depth++;
            } else if (!quoted && c == ')' && --depth == 0) {
                return i;
            }
        }
        throw new IllegalStateException("unbalanced call at " + open);
    }

    private static List<String> split(String args) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        boolean quoted = false;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '"' && (i == 0 || args.charAt(i - 1) != '\\')) {
                quoted = !quoted;
            }
            if (!quoted && (c == '(' || c == '[' || c == '{')) {
                depth++;
            } else if (!quoted && (c == ')' || c == ']' || c == '}')) {
                depth--;
            }
            if (!quoted && depth == 0 && c == ',') {
                out.add(cur.toString().strip());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().strip());
        return out;
    }

    private static String unquote(String s) {
        assertThat(s).as("a literal name or tag key").matches("\"[a-z0-9_]+\"");
        return s.substring(1, s.length() - 1);
    }

    @Test
    void theCodeRegistersExactlyTheCatalog() throws IOException {
        Map<String, Entry> catalog = catalog();
        List<Registration> found = registrations();
        assertThat(catalog).as("12 metrics up to Phase 6 + 8 in Phase 7").hasSize(20);
        Set<String> names = found.stream().map(Registration::name).collect(Collectors.toCollection(TreeSet::new));
        assertThat(names).as("registered names").isEqualTo(catalog.keySet());
        for (Registration r : found) {
            Entry e = catalog.get(r.name());
            assertThat(r.kind()).as(r.file() + " " + r.name()).isEqualTo(e.kind());
            assertThat(r.keys()).as(r.file() + " " + r.name() + " — one tag-key set per name").isEqualTo(e.keys());
        }
    }

    @Test
    void tagKeysAreAClosedSetWithoutIdentifiers() throws IOException {
        Set<String> keys = new TreeSet<>();
        catalog().values().forEach(e -> keys.addAll(e.keys()));
        assertThat(keys).containsExactly("action", "issuer_hash", "outcome", "reason", "result", "route", "state", "tenant", "type")
                .doesNotContain("agent", "agent_id", "calc_id", "policy", "ym", "subject");
    }

    @Test
    void theScannerSeesEveryRegistrationShape() throws IOException {
        Map<String, Long> byKind = registrations().stream().collect(Collectors.groupingBy(Registration::kind, TreeMap::new, Collectors.counting()));
        assertThat(byKind.keySet()).containsExactly("counter", "gauge", "timer");
        assertThat(registrations()).anyMatch(r -> r.name().equals("ingest_state") && r.keys().equals(Set.of("tenant", "state")));
    }
}
