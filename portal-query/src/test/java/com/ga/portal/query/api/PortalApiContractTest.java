package com.ga.portal.query.api;

import com.ga.portal.query.QueryAuthorization;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C11(계약 쪽): {@code contracts/api/v1/portal-api.openapi.yaml} ↔ 응답 레코드 양방향 — 스키마 속성 = 레코드 성분, 필수 = {@code Optional}이 아닌 성분,
 * {@code long}/{@code int} = {@code integer}, 문자열 = {@code string}, 목록 = {@code array}, 맵 = {@code object}. 경로 여덟 = 인가 표의 키, 모든 경로에 401·404,
 * 200은 그 응답 스키마, 모든 객체 스키마 {@code additionalProperties: false}. ({@code format} 0은 {@code EventSchemaTest}가 contracts/ 전 파일에서 본다.)
 */
class PortalApiContractTest {

    static JsonNode api;

    @BeforeAll
    static void read() throws Exception {
        api = YAMLMapper.builder().build().readTree(Files.readString(Path.of(System.getProperty("ga.repoRoot"), "contracts", "api", "v1",
                "portal-api.openapi.yaml")));
    }

    static JsonNode deref(JsonNode node) {
        JsonNode cur = node;
        while (cur.has("$ref")) {
            String ref = cur.path("$ref").asString();
            assertThat(ref).startsWith("#/components/schemas/");
            cur = api.path("components").path("schemas").path(ref.substring("#/components/schemas/".length()));
            assertThat(cur.isMissingNode()).as(ref).isFalse();
        }
        return cur;
    }

    static JsonNode schema(String name) {
        return api.path("components").path("schemas").path(name);
    }

    /** 레코드 → 그 레코드를 기술하는 스키마(중첩 레코드는 부모 스키마 안의 경로). */
    static Map<Class<?>, JsonNode> bindings() {
        Map<Class<?>, JsonNode> m = new LinkedHashMap<>();
        m.put(AsOf.class, schema("AsOf"));
        m.put(MeResponse.class, schema("MeResponse"));
        m.put(MeResponse.Tenant.class, schema("MeResponse").path("properties").path("tenant"));
        m.put(MonthSummary.class, schema("MonthSummary"));
        m.put(EarningsResponse.class, schema("EarningsResponse"));
        m.put(MonthResponse.class, schema("MonthResponse"));
        m.put(Line.class, schema("Line"));
        m.put(LinesResponse.class, schema("LinesResponse"));
        m.put(LineDetailResponse.class, schema("LineDetailResponse"));
        m.put(LineDetailResponse.Basis.class, schema("LineDetailResponse").path("properties").path("basis"));
        m.put(LineDetailResponse.PolicySummary.class, schema("LineDetailResponse").path("properties").path("policySummary"));
        m.put(LineDetailResponse.Deferral.class, schema("LineDetailResponse").path("properties").path("deferrals").path("items"));
        m.put(LineDetailResponse.Receivable.class, schema("LineDetailResponse").path("properties").path("receivable"));
        m.put(Ledger.class, schema("Ledger"));
        m.put(LimitsResponse.class, schema("LimitsResponse"));
        m.put(LimitDetailResponse.class, schema("LimitDetailResponse"));
        m.put(LimitDetailResponse.History.class, schema("LimitDetailResponse").path("properties").path("history").path("items"));
        m.put(DeferralsResponse.class, schema("DeferralsResponse"));
        m.put(DeferralsResponse.Month.class, schema("DeferralsResponse").path("properties").path("months").path("items"));
        m.put(DeferralsResponse.Item.class, schema("DeferralsResponse").path("properties").path("items").path("items"));
        return m;
    }

    static String jsonType(Type t) {
        if (t instanceof ParameterizedType p) {
            Class<?> raw = (Class<?>) p.getRawType();
            if (raw == Optional.class) {
                return jsonType(p.getActualTypeArguments()[0]);
            }
            if (List.class.isAssignableFrom(raw)) {
                return "array";
            }
            if (Map.class.isAssignableFrom(raw)) {
                return "object";
            }
        }
        Class<?> c = (Class<?>) t;
        if (c == long.class || c == int.class || c == Long.class || c == Integer.class) {
            return "integer";
        }
        if (c == String.class || c == java.time.Instant.class || c == java.time.LocalDate.class) {
            return "string";
        }
        if (c.isRecord()) {
            return "object";
        }
        throw new AssertionError("unmapped type " + t);
    }

    @Test
    void everyResponseRecordEqualsItsSchemaBothWays() {
        List<String> problems = new ArrayList<>();
        bindings().forEach((type, node) -> {
            JsonNode s = deref(node);
            assertThat(s.isMissingNode()).as(type.getName()).isFalse();
            assertThat(s.path("additionalProperties").isBoolean() && !s.path("additionalProperties").asBoolean())
                    .as("%s additionalProperties: false", type.getSimpleName()).isTrue();
            Set<String> props = new TreeSet<>();
            s.path("properties").propertyNames().forEach(props::add);
            Set<String> required = new TreeSet<>();
            s.path("required").forEach(r -> required.add(r.asString()));
            Set<String> components = new TreeSet<>();
            Set<String> nonOptional = new TreeSet<>();
            for (RecordComponent rc : type.getRecordComponents()) {
                components.add(rc.getName());
                if (rc.getType() != Optional.class) {
                    nonOptional.add(rc.getName());
                }
                JsonNode prop = deref(s.path("properties").path(rc.getName()));
                String expected = jsonType(rc.getGenericType());
                String actual = prop.path("type").asString();
                if (!prop.isMissingNode() && !expected.equals(actual)) {
                    problems.add(type.getSimpleName() + "." + rc.getName() + ": " + expected + " vs " + actual);
                }
            }
            if (!props.equals(components)) {
                problems.add(type.getSimpleName() + ": schema properties " + props + " vs record " + components);
            }
            if (!required.equals(nonOptional)) {
                problems.add(type.getSimpleName() + ": required " + required + " vs non-optional " + nonOptional);
            }
        });
        assertThat(problems).isEmpty();
    }

    @Test
    void pathsAreTheAuthorizationMatrixAndEachHas401And404() {
        Set<String> paths = new TreeSet<>();
        api.path("paths").properties().forEach(e -> {
            paths.add("GET " + e.getKey());
            JsonNode responses = e.getValue().path("get").path("responses");
            assertThat(responses.has("401") && responses.has("404") && responses.has("200") && responses.has("304")).as(e.getKey()).isTrue();
            assertThat(responses.path("200").path("content").path("application/json").path("schema").has("$ref")).as(e.getKey()).isTrue();
            assertThat(e.getValue().properties()).as("GET only").extracting(Map.Entry::getKey).containsExactly("get");
        });
        assertThat(paths).isEqualTo(new TreeSet<>(QueryAuthorization.matrix().keySet()));
        assertThat(api.path("openapi").asString()).isEqualTo("3.1.0");
    }

    @Test
    void noSchemaTalksAboutAPolicyNumber() {
        List<String> names = new ArrayList<>();
        collectNames(api.path("components").path("schemas"), names);
        assertThat(names).noneMatch(n -> n.equals("policyNo") || n.equals("policy_no") || n.equals("agentIdOther"));
        assertThat(names).contains("policyMasked", "policyKey");
    }

    static void collectNames(JsonNode node, List<String> out) {
        if (node.isObject()) {
            node.properties().forEach(e -> {
                if (e.getKey().equals("properties")) {
                    e.getValue().propertyNames().forEach(out::add);
                }
                collectNames(e.getValue(), out);
            });
        } else if (node.isArray()) {
            node.forEach(n -> collectNames(n, out));
        }
    }
}
