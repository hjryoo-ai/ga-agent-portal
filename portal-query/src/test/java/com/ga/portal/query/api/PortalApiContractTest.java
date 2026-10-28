package com.ga.portal.query.api;

import com.ga.portal.domain.access.ApiAuthorization;
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
        m.put(MeResponse.Features.class, schema("MeResponse").path("properties").path("features"));
        m.put(MonthSummary.class, schema("MonthSummary"));
        m.put(EarningsResponse.class, schema("EarningsResponse"));
        m.put(MonthResponse.class, schema("MonthResponse"));
        m.put(MonthResponse.Previous.class, schema("MonthResponse").path("properties").path("previous"));
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
        m.put(DeferralsResponse.Bar.class, schema("DeferralsResponse").path("properties").path("months").path("items").path("properties").path("bar"));
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
        if (c == boolean.class || c == Boolean.class) {
            return "boolean";
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

    /**
     * 쓰기 경로의 응답 코드(닫힌 표 — 1.1.0 시뮬레이션 둘, 1.2.0 문의 접수·알림 읽음). 쓰기는 ETag·304가 없고 성공 응답이 {@code no-store}다. 성공 코드는 표의
     * 첫째(문의 접수만 201).
     */
    static final Map<String, List<String>> WRITE_RESPONSES = Map.of(
            ApiAuthorization.SIM_NEW_CONTRACT, List.of("200", "400", "401", "404", "422", "429", "502", "503"),
            ApiAuthorization.SIM_POLICY_EVENT, List.of("200", "400", "401", "404", "422", "429", "502", "503"),
            ApiAuthorization.INQUIRY_CREATE, List.of("201", "400", "401", "404", "429"),
            ApiAuthorization.NOTIFICATION_READ, List.of("200", "401", "404"));

    /**
     * 인증 없는 연산(1.3.0, 포털 Phase 8 계획 Q2 — 닫힌 표): {@code security: []}, 응답은 200(스키마 참조·{@code no-cache}·ETag 없음)과 404뿐. 권한 표 밖이다
     * ({@code docs/authz-matrix.md} "인증 없는 경로" 블록 — {@code PublicPathsTest}). 다른 연산은 전부 문서 기본값(Bearer)을 물려받는다.
     */
    static final Set<String> PUBLIC_OPERATIONS = Set.of("GET /auth/config");

    @Test
    void publicOperationsAreTheClosedTableAndCarryNoSecurity() {
        Set<String> found = new TreeSet<>();
        api.path("paths").properties().forEach(e -> e.getValue().properties().forEach(op -> {
            String key = op.getKey().toUpperCase(java.util.Locale.ROOT) + " " + e.getKey();
            JsonNode security = op.getValue().path("security");
            if (security.isMissingNode()) {
                return;
            }
            assertThat(security.isArray() && security.isEmpty()).as("%s: an override is only ever the empty list", key).isTrue();
            found.add(key);
            JsonNode responses = op.getValue().path("responses");
            assertThat(responses.propertyNames()).as(key).containsExactlyInAnyOrder("200", "404");
            assertThat(responses.path("200").path("content").path("application/json").path("schema").has("$ref")).as(key).isTrue();
            assertThat(responses.path("200").path("headers").has("ETag")).as(key).isFalse();
            assertThat(responses.path("200").path("headers").path("Cache-Control").path("$ref").asString()).as(key)
                    .isEqualTo("#/components/headers/NoCache");
        }));
        assertThat(found).isEqualTo(PUBLIC_OPERATIONS);
        assertThat(api.path("security").toString()).isEqualTo("[{\"bearer\":[]}]");
    }

    @Test
    void pathsAreTheAuthorizationMatrixAndEachHas401And404() {
        Set<String> operations = new TreeSet<>();
        api.path("paths").properties().forEach(e -> e.getValue().properties().forEach(op -> {
            String method = op.getKey();
            assertThat(method).as(e.getKey()).isIn("get", "post", "patch");
            String key = method.toUpperCase(java.util.Locale.ROOT) + " " + e.getKey();
            if (PUBLIC_OPERATIONS.contains(key)) {
                return; // 권한 표 밖 — publicOperationsAreTheClosedTableAndCarryNoSecurity
            }
            operations.add(key);
            JsonNode responses = op.getValue().path("responses");
            assertThat(responses.has("401") && responses.has("404")).as(key).isTrue();
            if (method.equals("get")) {
                // 조회: 약한 ETag(뷰 — 피드 위치, 또는 1.2.0 알림·문의 — 본문)·304·private, no-cache
                assertThat(responses.path("200").path("content").path("application/json").path("schema").has("$ref")).as(key).isTrue();
                assertThat(responses.has("304")).as(key).isTrue();
                assertThat(responses.path("200").path("headers").path("ETag").path("$ref").asString()).as(key)
                        .isIn("#/components/headers/ETag", "#/components/headers/BodyETag");
                assertThat(responses.path("200").path("headers").path("Cache-Control").path("$ref").asString()).as(key)
                        .isEqualTo("#/components/headers/CacheControl");
            } else {
                List<String> codes = WRITE_RESPONSES.get(key);
                assertThat(codes).as("write operation in the closed table: %s", key).isNotNull();
                assertThat(responses.propertyNames()).as(key).containsExactlyInAnyOrderElementsOf(codes);
                JsonNode success = responses.path(codes.getFirst());
                assertThat(success.path("content").path("application/json").path("schema").has("$ref")).as(key).isTrue();
                assertThat(responses.has("304")).as(key).isFalse();
                assertThat(success.path("headers").has("ETag")).as(key).isFalse();
                assertThat(success.path("headers").path("Cache-Control").path("$ref").asString()).as(key).isEqualTo("#/components/headers/NoStore");
                // 본문이 있는 쓰기(POST)는 계약 스키마의 요청 본문, PATCH는 본문 없음
                assertThat(op.getValue().path("requestBody").path("content").path("application/json").path("schema").has("$ref")).as(key)
                        .isEqualTo(method.equals("post"));
            }
        }));
        assertThat(operations).isEqualTo(new TreeSet<>(ApiAuthorization.matrix().keySet()));
        assertThat(WRITE_RESPONSES.keySet()).allSatisfy(k -> assertThat(operations).contains(k));
        assertThat(api.path("openapi").asString()).isEqualTo("3.1.0");
    }

    /**
     * 계약에 값이 {@code null}인 키가 없다 — 흐름 매핑({@code {a: b, c: d}})의 따옴표 없는 설명에 쉼표가 있으면 설명이 잘리고 나머지가 값 없는 키가 된다(Phase 5
     * 1.1.0 초안에서 여덟 곳 — 생성 클라이언트의 설명이 잘려 보였다).
     */
    @Test
    void noKeyWithoutAValue() {
        List<String> nulls = new ArrayList<>();
        collectNulls("", api, nulls);
        assertThat(nulls).isEmpty();
    }

    static void collectNulls(String at, JsonNode node, List<String> out) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> e : node.properties()) {
                if (e.getValue().isNull()) {
                    out.add(at + "/" + e.getKey());
                }
                collectNulls(at + "/" + e.getKey(), e.getValue(), out);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectNulls(at + "[" + i + "]", node.get(i), out);
            }
        }
    }

    @Test
    void everyObjectSchemaIsClosed() {
        List<String> open = new ArrayList<>();
        closed("components/schemas", api.path("components").path("schemas"), open);
        assertThat(open).as("object schemas without additionalProperties: false").isEmpty();
    }

    static void closed(String at, JsonNode node, List<String> open) {
        if (node.isObject()) {
            // 속성 맵 안의 "type"은 속성 이름일 수 있다(1.2.0 알림 변이) — 스키마의 type 키워드는 문자열일 때만
            if (node.path("type").isString() && "object".equals(node.path("type").asString()) && node.has("properties")
                    && !(node.path("additionalProperties").isBoolean() && !node.path("additionalProperties").asBoolean())) {
                open.add(at);
            }
            node.properties().forEach(e -> closed(at + "/" + e.getKey(), e.getValue(), open));
        } else if (node.isArray()) {
            node.forEach(n -> closed(at + "[]", n, open));
        }
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
