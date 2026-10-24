package com.ga.portal.inbox;

import com.ga.portal.domain.NotificationType;
import com.ga.portal.domain.notify.NotificationParams;
import com.ga.portal.domain.notify.NotificationShapes;
import com.ga.portal.domain.notify.ScreenLinks;
import com.ga.portal.inbox.api.InquiriesResponse;
import com.ga.portal.inbox.api.Inquiry;
import com.ga.portal.inbox.api.InquiryCreated;
import com.ga.portal.inbox.api.Notification;
import com.ga.portal.inbox.api.NotificationsResponse;
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
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C1·C5(포털 Phase 6 — 계약 1.2.0의 알림·문의 쪽): 응답 레코드 ↔ 스키마 양방향(속성 = 성분, 필수 = {@code Optional}이 아닌 성분 — {@code PortalApiContractTest}와
 * 같은 규칙), 알림 변이 여섯 ↔ sealed 레코드 여섯, {@code discriminator.mapping} = {@link NotificationShapes}, 변이의 {@code type} 열거 ↔ {@link NotificationType}
 * (빠짐·겹침 없음), 알림 어디에도 금액({@code Won}) 0(계획 Q1), 계약 {@code ScreenLink} 정규식 = {@link ScreenLinks#PATTERN}.
 */
class InboxApiContractTest {

    static JsonNode api;

    @BeforeAll
    static void read() throws Exception {
        api = YAMLMapper.builder().build().readTree(Files.readString(Path.of(System.getProperty("ga.repoRoot"), "contracts", "api", "v1",
                "portal-api.openapi.yaml")));
    }

    static JsonNode schema(String name) {
        return api.path("components").path("schemas").path(name);
    }

    static JsonNode deref(JsonNode node) {
        JsonNode cur = node;
        while (cur.has("$ref")) {
            String ref = cur.path("$ref").asString();
            assertThat(ref).startsWith("#/components/schemas/");
            cur = schema(ref.substring("#/components/schemas/".length()));
        }
        return cur;
    }

    /** 변이 이름 → (레코드, 그 params 레코드). */
    static final Map<String, Class<?>[]> VARIANTS = Map.of(
            "NotificationLedger", new Class<?>[] {Notification.Ledger.class, NotificationParams.Ledger.class},
            "NotificationLine", new Class<?>[] {Notification.Line.class, NotificationParams.Line.class},
            "NotificationDeferral", new Class<?>[] {Notification.Deferral.class, NotificationParams.Deferral.class},
            "NotificationPolicy", new Class<?>[] {Notification.Policy.class, NotificationParams.Policy.class},
            "NotificationMonth", new Class<?>[] {Notification.Month.class, NotificationParams.Month.class},
            "NotificationInquiry", new Class<?>[] {Notification.Inquiry.class, NotificationParams.Inquiry.class});

    static Map<Class<?>, JsonNode> bindings() {
        Map<Class<?>, JsonNode> m = new LinkedHashMap<>();
        m.put(NotificationsResponse.class, schema("NotificationsResponse"));
        VARIANTS.forEach((name, classes) -> {
            m.put(classes[0], schema(name));
            m.put(classes[1], schema(name).path("properties").path("params"));
        });
        m.put(InquiryCreated.class, schema("InquiryCreated"));
        m.put(InquiriesResponse.class, schema("InquiriesResponse"));
        m.put(InquiriesResponse.Item.class, schema("InquiriesResponse").path("properties").path("items").path("items"));
        m.put(Inquiry.class, schema("Inquiry"));
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
        }
        Class<?> c = (Class<?>) t;
        if (c == long.class || c == int.class) {
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
            assertThat(s.path("additionalProperties").isBoolean() && !s.path("additionalProperties").asBoolean()).as("%s closed", type.getName()).isTrue();
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
                if (!prop.isMissingNode() && !jsonType(rc.getGenericType()).equals(prop.path("type").asString())) {
                    problems.add(type.getName() + "." + rc.getName() + ": " + jsonType(rc.getGenericType()) + " vs " + prop.path("type").asString());
                }
            }
            if (!props.equals(components)) {
                problems.add(type.getName() + ": schema " + props + " vs record " + components);
            }
            if (!required.equals(nonOptional)) {
                problems.add(type.getName() + ": required " + required + " vs non-optional " + nonOptional);
            }
        });
        assertThat(problems).isEmpty();
    }

    @Test
    void theVariantsAreTheSealedRecordsAndTheMappingIsTheDomainTable() {
        JsonNode n = schema("Notification");
        Set<String> oneOf = new TreeSet<>();
        n.path("oneOf").forEach(r -> oneOf.add(r.path("$ref").asString().replace("#/components/schemas/", "")));
        assertThat(oneOf).isEqualTo(new TreeSet<>(VARIANTS.keySet()));
        assertThat(Notification.class.getPermittedSubclasses()).hasSize(VARIANTS.size());
        assertThat(n.path("discriminator").path("propertyName").asString()).isEqualTo("type");
        Map<String, String> mapping = new TreeMap<>();
        n.path("discriminator").path("mapping").properties().forEach(e -> mapping.put(e.getKey(), e.getValue().asString().replace("#/components/schemas/", "")));
        Map<String, String> expected = new TreeMap<>();
        Map<Class<?>, String> byParams = new LinkedHashMap<>();
        VARIANTS.forEach((name, classes) -> byParams.put(classes[1], name));
        for (NotificationType t : NotificationType.values()) {
            expected.put(t.name(), byParams.get(NotificationShapes.paramsType(t)));
        }
        assertThat(mapping).isEqualTo(expected);
        // 변이마다 type 열거 = 그 모양을 쓰는 종류(겹침·빠짐 없음)
        Map<String, Set<String>> enums = new TreeMap<>();
        VARIANTS.keySet().forEach(name -> {
            Set<String> values = new TreeSet<>();
            schema(name).path("properties").path("type").path("enum").forEach(v -> values.add(v.asString()));
            enums.put(name, values);
        });
        Map<String, Set<String>> fromMapping = new TreeMap<>();
        mapping.forEach((type, variant) -> fromMapping.computeIfAbsent(variant, k -> new TreeSet<>()).add(type));
        assertThat(enums).isEqualTo(fromMapping);
        assertThat(schema("NotificationSeverity").path("enum").toString()).isEqualTo("[\"INFO\",\"WARN\",\"CRIT\"]");
    }

    @Test
    void noNotificationCarriesAnAmount() {
        List<String> refs = new ArrayList<>();
        for (String name : List.of("Notification", "NotificationsResponse")) {
            collectRefs(schema(name), refs, new TreeSet<>());
        }
        VARIANTS.keySet().forEach(name -> collectRefs(schema(name), refs, new TreeSet<>()));
        assertThat(refs).isNotEmpty().noneMatch(r -> r.endsWith("/Won") || r.endsWith("/NonNegativeWon") || r.endsWith("/DigitWon"));
        assertThat(refs).noneMatch(r -> r.endsWith("/PolicyKey"));
    }

    static void collectRefs(JsonNode node, List<String> out, Set<String> seen) {
        if (node.isObject()) {
            if (node.has("$ref")) {
                String ref = node.path("$ref").asString();
                out.add(ref);
                if (seen.add(ref)) {
                    collectRefs(deref(node), out, seen);
                }
            }
            node.properties().forEach(e -> collectRefs(e.getValue(), out, seen));
        } else if (node.isArray()) {
            node.forEach(v -> collectRefs(v, out, seen));
        }
    }

    @Test
    void theScreenLinkPatternIsTheDomainPattern() {
        assertThat(schema("ScreenLink").path("pattern").asString()).isEqualTo(ScreenLinks.PATTERN.pattern());
        VARIANTS.forEach((name, classes) -> assertThat(schema(name).path("properties").has("link")).as(name)
                .isEqualTo(!name.equals("NotificationPolicy")));
    }
}
