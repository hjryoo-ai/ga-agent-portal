package com.ga.portal.projection.contracts;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ScalarEvent;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약 v1.4 N1(포털 Phase 2 수용 심사 §3, 엔진 E2 보고서 §9-6): 계약 YAML 전부가 YAML 1.1 해석기(SnakeYAML — PyYAML·Jackson 2와 같은 해석)와
 * YAML 1.2 해석기(Jackson 3 YAML — SnakeYAML Engine)에서 <b>같은 트리</b>(키 집합·값·타입)로 읽힌다. 1.1은 따옴표 없는 {@code yes/no/on/off/y/n}을
 * 불리언으로, {@code 2026-08-01}을 날짜로 읽는다 — 키든 값이든 그런 글자는 따옴표로 써야 한다. v1.3의 {@code required: [no, amount]}가
 * 1.1에서 {@code [false, amount]}가 되어 엔진 시험 픽스처에서 모든 신계약 시뮬레이션 응답이 스키마 검증에 실패했다(DD-11).
 */
class ContractYamlParsersTest {

    private static final Path CONTRACTS = Path.of(System.getProperty("ga.repoRoot")).resolve("contracts");
    private static final YAMLMapper YAML12 = YAMLMapper.builder().build();

    @Test
    void everyContractYamlReadsTheSameUnderYaml11AndYaml12() {
        List<Path> files = yamlFiles();
        assertThat(files).as("contract YAML files").isNotEmpty();
        for (Path f : files) {
            String text = read(f);
            assertThat(typed11(new Yaml(new SafeConstructor(new LoaderOptions())).load(text)))
                    .as(CONTRACTS.relativize(f).toString())
                    .isEqualTo(typed12(YAML12.readTree(text)));
        }
    }

    /**
     * 비교가 공허하지 않다는 증거: 따옴표 없는 그 글자들은 두 해석기에서 실제로 다르게 읽힌다. {@code y}·{@code n}은 YAML 1.1 명세의 불리언이지만
     * SnakeYAML·PyYAML은 그렇게 읽지 않는다 — 그 둘은 아래 {@link #noPlainScalarIsAYaml11BooleanSpellingOtherThanTrueOrFalse}가 막는다.
     */
    @Test
    void theTwoParsersReallyDisagreeOnUnquotedYesNoOnOffAndDates() {
        for (String scalar : List.of("yes", "no", "on", "off", "2026-08-01")) {
            String doc = "k: " + scalar + "\n" + scalar + ": v\n";
            assertThat(typed11(new Yaml(new SafeConstructor(new LoaderOptions())).load(doc)))
                    .as(scalar).isNotEqualTo(typed12(YAML12.readTree(doc)));
            String quoted = "k: '" + scalar + "'\n'" + scalar + "': v\n";
            assertThat(typed11(new Yaml(new SafeConstructor(new LoaderOptions())).load(quoted)))
                    .as("'" + scalar + "'").isEqualTo(typed12(YAML12.readTree(quoted)));
        }
    }

    /** YAML 1.1 명세의 불리언 철자({@code y|Y|yes|…|on|off}) 중 {@code true}·{@code false} 밖의 것은 계약에 따옴표 없이 나오지 않는다(키·값 모두). */
    @Test
    void noPlainScalarIsAYaml11BooleanSpellingOtherThanTrueOrFalse() {
        Pattern yaml11Bool = Pattern.compile("y|Y|yes|Yes|YES|n|N|no|No|NO|on|On|ON|off|Off|OFF");
        for (Path f : yamlFiles()) {
            List<String> plain = new ArrayList<>();
            for (Event e : new Yaml().parse(new StringReader(read(f)))) {
                if (e instanceof ScalarEvent s && s.isPlain() && yaml11Bool.matcher(s.getValue()).matches()) {
                    plain.add(s.getValue() + " @" + s.getStartMark().getLine());
                }
            }
            assertThat(plain).as(CONTRACTS.relativize(f).toString()).isEmpty();
        }
    }

    private static List<Path> yamlFiles() {
        try (Stream<Path> s = Files.walk(CONTRACTS)) {
            return s.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path f) {
        try {
            return Files.readString(f, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** SnakeYAML(1.1) 결과를 타입 표시가 붙은 정규형으로: 키·값 모두 {@code 타입:글자}. */
    private static Object typed11(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new TreeMap<>();
            m.forEach((k, x) -> out.put(String.valueOf(scalar11(k)), typed11(x)));
            return out;
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            l.forEach(x -> out.add(typed11(x)));
            return out;
        }
        return scalar11(v);
    }

    private static String scalar11(Object v) {
        return switch (v) {
            case null -> "null";
            case String s -> "string:" + s;
            case Boolean b -> "boolean:" + b;
            case Integer i -> "number:" + i;
            case Long l -> "number:" + l;
            default -> v.getClass().getSimpleName() + ":" + v;   // 날짜·소수 등 — 1.2 쪽과 같을 수 없다
        };
    }

    /** Jackson(1.2) 트리를 같은 정규형으로. Jackson의 키는 언제나 문자열이다(1.2는 `no`를 문자열로 읽는다). */
    private static Object typed12(JsonNode n) {
        if (n.isObject()) {
            Map<String, Object> out = new TreeMap<>();
            for (Map.Entry<String, JsonNode> e : n.properties()) {
                out.put("string:" + e.getKey(), typed12(e.getValue()));
            }
            return out;
        }
        if (n.isArray()) {
            List<Object> out = new ArrayList<>();
            n.forEach(x -> out.add(typed12(x)));
            return out;
        }
        if (n.isNull()) {
            return "null";
        }
        if (n.isBoolean()) {
            return "boolean:" + n.asBoolean();
        }
        if (n.isIntegralNumber()) {
            return "number:" + n.asString();
        }
        if (n.isNumber()) {
            return "decimal:" + n.asString();
        }
        return "string:" + n.asString();
    }
}
