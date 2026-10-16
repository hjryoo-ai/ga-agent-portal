package com.ga.portal.ingest;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 계약 스키마(설계서 §4, {@code contracts/})를 <b>클래스패스 사본에서만</b> 읽는다 — 원격 조회 0. 사본은 빌드가 저장소 {@code contracts/}에서
 * 복사한 것이고({@code processResources}), 바이트가 같음을 {@code ClasspathContractsTest}가 본다. IRI 해석은 포털 {@code EventSchemaTest}와
 * 같다: {@code https://ga.example/portal/contracts/} → {@code contracts/}, {@code https://ga.example/contracts/}(ga-disclosure) →
 * {@code contracts/shared/ga-disclosure/}. 그 밖의 IRI는 거부한다(메타스키마는 검증기 내장).
 *
 * <p>type ↔ aggregate.kind 짝과 지원 최대 version은 코드에 적지 않고 포털 Envelope 스키마의 {@code if/then}·{@code version.const}에서 읽는다.
 */
public final class ContractSchemas {

    public static final String PORTAL = "https://ga.example/portal/contracts/";
    public static final String DISCLOSURE = "https://ga.example/contracts/";
    public static final String CORE_ID = DISCLOSURE + "events/v1/envelope-core.schema.json";
    public static final String ENVELOPE_ID = PORTAL + "events/v1/envelope.schema.json";
    public static final String FEED_RESPONSE_ID = PORTAL + "api/v1/engine-internal.openapi.yaml#/components/schemas/FeedResponse";
    static final String CLASSPATH_ROOT = "contracts/";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final YAMLMapper YAML = YAMLMapper.builder().build();
    private static final String META = "https://json-schema.org/";

    private final SchemaRegistry registry;
    private final Map<String, String> kindByType;
    private final int maxVersion;

    private ContractSchemas() {
        registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12, b -> b.schemas(ContractSchemas::resolve));
        JsonNode envelope = JSON.readTree(read("events/v1/envelope.schema.json"));
        JsonNode portalPart = envelope.path("allOf").get(1);
        maxVersion = portalPart.path("properties").path("version").path("const").asInt();
        Map<String, String> pairs = new LinkedHashMap<>();
        for (JsonNode branch : portalPart.path("allOf")) {
            String type = branch.path("if").path("properties").path("type").path("const").asString();
            String kind = branch.path("then").path("properties").path("aggregate").path("properties").path("kind").path("const").asString();
            if (!type.isEmpty() && !kind.isEmpty()) {   // 14번째 항(열거 밖 type·version 거부 — else: false)은 짝이 아니다
                pairs.put(type, kind);
            }
        }
        if (maxVersion < 1 || pairs.isEmpty()) {
            throw new IllegalStateException("portal envelope schema has no version const or type/kind pairs");
        }
        kindByType = Collections.unmodifiableMap(pairs);
    }

    private static final class Holder {
        private static final ContractSchemas INSTANCE = new ContractSchemas();
    }

    /** 한 번 읽어 재사용한다(스키마는 빌드 산출물이라 실행 중 바뀌지 않는다). */
    public static ContractSchemas get() {
        return Holder.INSTANCE;
    }

    public Schema core() {
        return registry.getSchema(SchemaLocation.of(CORE_ID));
    }

    public Schema envelope() {
        return registry.getSchema(SchemaLocation.of(ENVELOPE_ID));
    }

    public Schema feedResponse() {
        return registry.getSchema(SchemaLocation.of(FEED_RESPONSE_ID));
    }

    /** 계약의 type → aggregate.kind 짝(13종). */
    public Map<String, String> kindByType() {
        return kindByType;
    }

    /** 포털 Envelope가 받는 최대 version(지금 1). */
    public int maxVersion() {
        return maxVersion;
    }

    private static String resolve(String iri) {
        if (iri.startsWith(PORTAL)) {
            return asJson(iri.substring(PORTAL.length()));
        }
        if (iri.startsWith(DISCLOSURE)) {
            return asJson("shared/ga-disclosure/" + iri.substring(DISCLOSURE.length()));
        }
        if (iri.startsWith(META)) {
            return null;    // 2020-12 메타스키마는 검증기에 내장돼 있다
        }
        throw new IllegalStateException("schema IRI outside the repository contracts (no remote fetch): " + iri);
    }

    private static String asJson(String path) {
        String text = read(path);
        return path.endsWith(".yaml") ? JSON.writeValueAsString(YAML.readTree(text)) : text;
    }

    static String read(String path) {
        try (InputStream in = ContractSchemas.class.getClassLoader().getResourceAsStream(CLASSPATH_ROOT + path)) {
            if (in == null) {
                throw new IllegalStateException("contract not on the classpath: " + CLASSPATH_ROOT + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
