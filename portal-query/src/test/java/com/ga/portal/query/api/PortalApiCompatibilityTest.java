package com.ga.portal.query.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C1(포털 Phase 5 ②): 계약 {@code portal-api} 1.1.x는 1.0.0의 추가·완화뿐이다. 1.0.0은 시험 자원의 사본({@code main} {@code 86d6fe6}의 바이트 — SHA-256을
 * 아래에 고정, {@code contracts/CHECKSUMS}의 그 판 줄과 같다)이고, 두 문서를 구조로 대조한다. 1.1.1(포털 Phase 5 수용 심사 B1)은 1.1.0({@code phase-5}
 * {@code 0fccc05}의 바이트 — 같은 방식으로 고정)에 대해서도 같은 대조를 통과하고, 그 위에 <b>설명문만</b> 바뀌었다(설명·요약·{@code info.version}을 뺀
 * 두 문서가 같다).
 * <ul>
 *   <li>이전 판의 모든 경로·메서드·응답 코드·컴포넌트가 새 판에 있다.</li>
 *   <li>객체의 {@code required}는 같다(새 속성은 선택), 기존 속성의 정의는 같다 — 설명문만 바뀔 수 있고 {@code enum}은 늘기만 한다.</li>
 *   <li>기존 정의에 새 제약 키워드가 붙지 않는다(새 키는 속성·경로·응답·컴포넌트 맵에만). 쿼리 파라미터는 (이름, 위치)로 짝짓고 참조를 풀어 비교하며, 새 파라미터는
 *       선택이다. {@code info}는 대조하지 않는다.</li>
 * </ul>
 * "1.0.0 응답이 1.1.x를 통과"의 실측은 Phase 3의 응답 검증 시험들이 현재 판으로 그대로 도는 것이다.
 */
class PortalApiCompatibilityTest {

    static final String V1_0_0_SHA256 = "0d14a1827aab03f61505dd3a24451728e1a73a59f6645cbbc8d7563b4fbf9188";
    static final String V1_1_0_SHA256 = "8bf4e71cee7f924bf32cc6c31d0dcd20bd2e879a6a7a7622f278586687c64065";
    /** 새 키가 들어와도 되는 맵(그 맵의 직속 키만 — 그 아래는 다시 규칙대로). */
    static final Set<String> ADDITIVE_MAPS = Set.of("properties", "paths", "responses", "schemas", "parameters", "headers");

    static JsonNode oldApi;
    static JsonNode v110Api;
    static JsonNode newApi;

    @BeforeAll
    static void read() throws Exception {
        YAMLMapper yaml = YAMLMapper.builder().build();
        oldApi = yaml.readTree(baseline("1.0.0", V1_0_0_SHA256));
        v110Api = yaml.readTree(baseline("1.1.0", V1_1_0_SHA256));
        newApi = yaml.readTree(Files.readString(Path.of(System.getProperty("ga.repoRoot"), "contracts", "api", "v1", "portal-api.openapi.yaml")));
    }

    static byte[] baseline(String version, String sha256) throws Exception {
        byte[] bytes;
        try (InputStream in = PortalApiCompatibilityTest.class.getResourceAsStream("/contracts/portal-api-" + version + ".openapi.yaml")) {
            assertThat(in).as(version + " copy on the test classpath").isNotNull();
            bytes = in.readAllBytes();
        }
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))).as(version + " baseline bytes").isEqualTo(sha256);
        return bytes;
    }

    /** 설명문을 뺀 문서 — 설명·요약 키 전부와 {@code info.version}을 지운 사본. 두 판의 이것이 같으면 바뀐 것은 설명문뿐이다. */
    static JsonNode withoutNotation(JsonNode doc) {
        JsonNode copy = doc.deepCopy();
        strip(copy);
        ((ObjectNode) copy.path("info")).remove("version");
        return copy;
    }

    private static void strip(JsonNode node) {
        if (node instanceof ObjectNode o) {
            o.remove(List.of("description", "summary"));
            o.forEach(PortalApiCompatibilityTest::strip);
        } else if (node instanceof ArrayNode a) {
            a.forEach(PortalApiCompatibilityTest::strip);
        }
    }

    static List<String> compare(JsonNode before, JsonNode after) {
        List<String> problems = new ArrayList<>();
        new Comparator(before, after, problems).node("", before, after, false);
        return problems;
    }

    /** 두 문서의 구조 대조기 — 위반을 경로와 함께 모은다. */
    record Comparator(JsonNode oldDoc, JsonNode newDoc, List<String> problems) {

        void node(String at, JsonNode o, JsonNode n, boolean additive) {
            if (n == null || n.isMissingNode()) {
                problems.add(at + ": removed");
                return;
            }
            if (o.has("$ref") || n.has("$ref")) {
                if (o.has("$ref") && n.has("$ref") && o.path("$ref").asString().equals(n.path("$ref").asString())) {
                    return;   // 같은 참조 — 대상은 components에서 따로 대조한다
                }
                node(at + "→", deref(oldDoc, o), deref(newDoc, n), false);
                return;
            }
            if (o.isObject()) {
                if (!n.isObject()) {
                    problems.add(at + ": no longer an object");
                    return;
                }
                for (var e : o.properties()) {
                    String k = e.getKey();
                    if (k.equals("description") || k.equals("summary") || at.isEmpty() && k.equals("info")) {
                        continue;
                    }
                    switch (k) {
                        case "required" -> {
                            if (!names(e.getValue()).equals(names(n.path(k)))) {
                                problems.add(at + "/required: " + names(e.getValue()) + " → " + names(n.path(k)));
                            }
                        }
                        case "enum" -> {
                            if (!names(n.path(k)).containsAll(names(e.getValue()))) {
                                problems.add(at + "/enum shrank: " + names(e.getValue()) + " → " + names(n.path(k)));
                            }
                        }
                        case "parameters" -> parameters(at + "/parameters", e.getValue(), n.path(k));
                        default -> node(at + "/" + k, e.getValue(), n.get(k), ADDITIVE_MAPS.contains(k));
                    }
                }
                if (!additive) {
                    for (String k : n.propertyNames()) {
                        if (!o.has(k) && !k.equals("description") && !k.equals("summary")) {
                            problems.add(at + ": new keyword or key '" + k + "' on an existing definition");
                        }
                    }
                }
                return;
            }
            if (o.isArray()) {
                if (!n.isArray() || n.size() != o.size()) {
                    problems.add(at + ": array changed");
                    return;
                }
                for (int i = 0; i < o.size(); i++) {
                    node(at + "[" + i + "]", o.get(i), n.get(i), false);
                }
                return;
            }
            if (!o.equals(n)) {
                problems.add(at + ": " + o + " → " + n);
            }
        }

        void parameters(String at, JsonNode o, JsonNode n) {
            if (o.isArray()) {
                List<JsonNode> after = new ArrayList<>();
                n.forEach(p -> after.add(deref(newDoc, p)));
                for (JsonNode p : o) {
                    JsonNode before = deref(oldDoc, p);
                    String id = before.path("name").asString() + "@" + before.path("in").asString();
                    JsonNode match = after.stream().filter(q -> (q.path("name").asString() + "@" + q.path("in").asString()).equals(id)).findFirst()
                            .orElse(null);
                    node(at + "/" + id, before, match, false);
                    after.remove(match);
                }
                for (JsonNode added : after) {
                    if (added.path("required").asBoolean(false)) {
                        problems.add(at + ": new required parameter " + added.path("name").asString());
                    }
                }
            } else {
                node(at, o, n, true);   // components.parameters — 맵
            }
        }

        static JsonNode deref(JsonNode doc, JsonNode node) {
            JsonNode cur = node;
            while (cur != null && cur.has("$ref")) {
                String[] parts = cur.path("$ref").asString().substring(2).split("/");
                JsonNode next = doc;
                for (String part : parts) {
                    next = next.path(part);
                }
                cur = next;
            }
            return cur;
        }

        static Set<String> names(JsonNode array) {
            Set<String> out = new TreeSet<>();
            array.forEach(v -> out.add(v.asString()));
            return out;
        }
    }

    @Test
    void version111IsAdditiveOver100And110() {
        assertThat(newApi.path("info").path("version").asString()).isEqualTo("1.1.1");
        assertThat(compare(oldApi, newApi)).as("1.0.0 → 1.1.1").isEmpty();
        assertThat(compare(v110Api, newApi)).as("1.1.0 → 1.1.1").isEmpty();
    }

    @Test
    void version111ChangesOnlyDescriptionsOver110() {
        assertThat(v110Api.path("info").path("version").asString()).isEqualTo("1.1.0");
        assertThat(withoutNotation(newApi)).isEqualTo(withoutNotation(v110Api));
        // 대조기가 설명문 밖의 변화를 본다 — 제약 하나를 바꾼 사본은 같지 않다
        ObjectNode tightened = (ObjectNode) newApi.deepCopy();
        ((ObjectNode) tightened.path("components").path("schemas").path("Ym")).put("pattern", "^2[0-9]{3}(0[1-9]|1[0-2])$");
        assertThat(withoutNotation(tightened)).isNotEqualTo(withoutNotation(v110Api));
    }

    @Test
    void theComparatorSeesRemovalsTighteningsAndNewRequiredInputs() {
        ObjectNode removedRequired = (ObjectNode) newApi.deepCopy();
        ArrayNode req = (ArrayNode) removedRequired.path("components").path("schemas").path("MonthSummary").path("required");
        req.remove(req.size() - 1);
        assertThat(compare(oldApi, removedRequired)).anyMatch(p -> p.contains("MonthSummary/required"));

        ObjectNode tightened = (ObjectNode) newApi.deepCopy();
        ((ObjectNode) tightened.path("components").path("schemas").path("Ym")).put("pattern", "^2[0-9]{3}(0[1-9]|1[0-2])$");
        ((ObjectNode) tightened.path("components").path("schemas").path("Seq")).put("maximum", 10);
        assertThat(compare(oldApi, tightened)).anyMatch(p -> p.contains("/Ym/pattern")).anyMatch(p -> p.contains("'maximum'"));

        ObjectNode shrunk = (ObjectNode) newApi.deepCopy();
        ArrayNode roles = (ArrayNode) shrunk.path("components").path("schemas").path("Role").path("enum");
        roles.remove(roles.size() - 1);
        assertThat(compare(oldApi, shrunk)).anyMatch(p -> p.contains("/Role/enum shrank"));

        ObjectNode newRequiredParam = (ObjectNode) newApi.deepCopy();
        ((ObjectNode) newRequiredParam.path("paths").path("/api/v1/me/deferrals").path("get").path("parameters").get(2)).put("required", true);
        assertThat(compare(oldApi, newRequiredParam)).anyMatch(p -> p.contains("new required parameter scenario"));

        ObjectNode droppedPath = (ObjectNode) newApi.deepCopy();
        ((ObjectNode) droppedPath.path("paths")).remove("/api/v1/me/limits");
        assertThat(compare(oldApi, droppedPath)).anyMatch(p -> p.contains("/paths//api/v1/me/limits: removed"));
    }
}
