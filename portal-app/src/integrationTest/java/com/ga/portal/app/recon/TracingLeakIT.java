package com.ga.portal.app.recon;

import com.ga.portal.app.trace.MicrometerTracing;
import com.ga.portal.domain.RebuildOutcome;
import com.ga.portal.domain.ReconRunResult;
import com.ga.portal.recon.ReconRunner;
import com.ga.portal.recon.RunRecord;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포털 Phase 7 C8(계획 ⑤·Q6): 실제 OpenTelemetry SDK(메모리 내보내기)로 인제스트 → 대사(DRIFT → 재구축 → 재대사 REPAIRED)를 돌린 뒤 스팬을 훑는다.
 * <ul>
 *   <li>스팬 이름·부모가 {@code docs/지표.md} §4 표와 같고, 속성 키 ⊆ 표의 그 스팬 키.</li>
 *   <li>속성 값 어디에도 센티널({@code pii-sentinels.properties} 전 키)·스트림의 식별자·날짜·금액 원문이 없다(0건).</li>
 * </ul>
 */
class TracingLeakIT {

    private static final Pattern ROW = Pattern.compile("^\\| `([a-z.]+)` \\| ([^|]*) \\| ([^|]*) \\|$");
    private static final Pattern KIND = Pattern.compile("[A-Z][A-Za-z_]*");

    record SpanRow(String parent, Set<String> keys) {
    }

    static Map<String, SpanRow> table() throws IOException {
        Map<String, SpanRow> out = new TreeMap<>();
        for (String line : Files.readAllLines(Path.of(System.getProperty("ga.repoRoot")).resolve("docs/지표.md"))) {
            Matcher m = ROW.matcher(line);
            if (m.find()) {
                Matcher p = Pattern.compile("^`([a-z.]+)`$").matcher(m.group(2).strip());
                Set<String> keys = new TreeSet<>();
                Matcher k = Pattern.compile("`([a-z_.]+)`").matcher(m.group(3));
                while (k.find()) {
                    keys.add(k.group(1));
                }
                out.put(m.group(1), new SpanRow(p.matches() ? p.group(1) : m.group(2).strip().equals("없음") ? "" : "*", keys));
            }
        }
        return out;
    }

    /** 센티널 파일 전 키의 값. */
    static List<String> sentinels() {
        Properties p = new Properties();
        try (InputStream in = TracingLeakIT.class.getClassLoader().getResourceAsStream("pii-sentinels.properties")) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return p.stringPropertyNames().stream().flatMap(k -> List.of(p.getProperty(k).split(",")).stream()).map(String::strip)
                .filter(s -> !s.isEmpty()).toList();
    }

    /** 스트림의 값 원문 — 종류 꼴이 아닌 문자열(식별자·날짜·이름)과 100 이상의 수(금액·seq 밖). */
    static Set<String> streamValues(List<ObjectNode> stream) {
        Set<String> out = new TreeSet<>();
        List<JsonNode> todo = new ArrayList<>(stream);
        while (!todo.isEmpty()) {
            JsonNode n = todo.removeLast();
            if (n.isContainer()) {
                n.forEach(todo::add);
            } else if (n.isString() && n.asString().length() >= 3 && !KIND.matcher(n.asString()).matches()) {
                out.add(n.asString());
            } else if (n.isIntegralNumber() && Math.abs(n.asLong()) >= 100) {
                out.add(Long.toString(n.asLong()));
            }
        }
        return out;
    }

    @Test
    void theSpansOfIngestAndAutoRepairCarryNoValues() throws IOException {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        try (SdkTracerProvider provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()) {
            MicrometerTracing tracing = new MicrometerTracing(new OtelTracer(provider.get("ga-agent-portal"), new OtelCurrentTraceContext(), event -> { }));
            List<SpanData> spans;
            Set<String> forbidden = new TreeSet<>(sentinels());
            try (ReconRig r = new ReconRig("RCN_TRACE", "a1", ReconRig.a1(), "{\"auto_rebuild_enabled\": true}").tracing(tracing).ingestAll()) {
                forbidden.addAll(streamValues(r.stream));
                r.corruptLine("1");
                RunRecord run = ((ReconRunner.Outcome.Done) r.run("202608", "202702")).run();
                assertThat(run.rebuildOutcome()).contains(RebuildOutcome.SUCCEEDED);
                assertThat(run.result()).isEqualTo(ReconRunResult.REPAIRED);
                spans = exporter.getFinishedSpanItems();
            }
            assertThat(forbidden).as("the scan is not vacuous").contains("A-1001", "202608", "1800000").hasSizeGreaterThan(20);

            Map<String, SpanRow> table = table();
            assertThat(table).as("11 spans in docs/지표.md §4").hasSize(11);
            Map<String, String> nameById = spans.stream().collect(Collectors.toMap(SpanData::getSpanId, SpanData::getName));
            Set<String> seen = new TreeSet<>();
            int values = 0;
            for (SpanData s : spans) {
                seen.add(s.getName());
                SpanRow row = table.get(s.getName());
                assertThat(row).as("span %s is in the table", s.getName()).isNotNull();
                String parent = nameById.getOrDefault(s.getParentSpanId(), "");
                if (!row.parent().equals("*")) {
                    assertThat(parent).as("parent of %s", s.getName()).isEqualTo(row.parent());
                }
                Set<String> keys = new TreeSet<>();
                s.getAttributes().forEach((k, v) -> keys.add(k.getKey()));
                assertThat(row.keys()).as("attribute keys of %s ⊆ table", s.getName()).containsAll(keys);
                for (Object v : s.getAttributes().asMap().values()) {
                    String text = String.valueOf(v);
                    values++;
                    assertThat(forbidden.stream().filter(text::contains).count()).as("a value of %s", s.getName()).isZero();
                }
            }
            assertThat(seen).as("every span but engine.simulation ran").isEqualTo(new TreeSet<>(table.keySet()) {{
                remove("engine.simulation");
            }});
            assertThat(values).as("attribute values scanned").isGreaterThan(30);
            assertThat(spans.stream().filter(s -> s.getName().equals("recon.run")).findFirst().orElseThrow().getAttributes().asMap().values()
                    .stream().map(String::valueOf).toList()).as("tenant, CLI, 2 checked, 1 new drift").containsExactlyInAnyOrder(
                    spans.getFirst().getAttributes().asMap().entrySet().stream().filter(e -> e.getKey().getKey().equals("tenant"))
                            .map(e -> String.valueOf(e.getValue())).findFirst().orElseThrow(), "CLI", "2", "1");
        }
    }
}
