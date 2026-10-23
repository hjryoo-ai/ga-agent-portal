package com.ga.portal.sim;

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

/**
 * 시뮬레이션이 쓰는 계약 스키마 — <b>클래스패스 사본에서만</b>(빌드가 저장소 {@code contracts/}에서 복사, 원격 조회 0). 포털 요청 둘은 포털 계약
 * {@code portal-api.openapi.yaml}(입력 규칙의 정본 — 화면은 같은 계약의 {@code pattern}을 생성한 규칙 파일로 미리 막을 뿐, 계획 Q12), 엔진 결과 둘은 엔진 계약
 * 사본 {@code engine-internal.openapi.yaml}(1.4.0). IRI 해석은 {@code portal-ingest}의 {@code ContractSchemas}와 같다.
 */
public final class SimContracts {

    static final String PORTAL = "https://ga.example/portal/contracts/";
    static final String API = PORTAL + "api/v1/portal-api.openapi.yaml#/components/schemas/";
    static final String ENGINE = PORTAL + "api/v1/engine-internal.openapi.yaml#/components/schemas/";
    private static final String META = "https://json-schema.org/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    private final SchemaRegistry registry;
    private final JsonNode engineDocument;

    private SimContracts() {
        registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12, b -> b.schemas(SimContracts::resolve));
        engineDocument = YAML.readTree(read("api/v1/engine-internal.openapi.yaml"));
    }

    private static final class Holder {
        private static final SimContracts INSTANCE = new SimContracts();
    }

    public static SimContracts get() {
        return Holder.INSTANCE;
    }

    /** 포털 계약의 스키마(요청 {@code SimNewContractRequest}·{@code SimPolicyEventRequest}, 시험은 응답도). */
    public Schema portalSchema(String name) {
        return registry.getSchema(SchemaLocation.of(API + name));
    }

    /** 엔진 계약 사본의 결과 스키마({@code NewContractResult}·{@code PolicyEventResult}). */
    public Schema engineResult(String name) {
        return registry.getSchema(SchemaLocation.of(ENGINE + name));
    }

    /** 엔진 결과 스키마의 원문 노드 — 응답에 옮길 속성 집합을 고른다({@link SimResponses}). */
    public JsonNode engineResultNode(String name) {
        return engineDocument.path("components").path("schemas").path(name);
    }

    private static String resolve(String iri) {
        if (iri.startsWith(PORTAL)) {
            String path = iri.substring(PORTAL.length());
            String text = read(path);
            return path.endsWith(".yaml") ? JSON.writeValueAsString(YAML.readTree(text)) : text;
        }
        if (iri.startsWith(META)) {
            return null;    // 2020-12 메타스키마는 검증기에 내장
        }
        throw new IllegalStateException("schema IRI outside the copied contracts (no remote fetch): " + iri);
    }

    static String read(String path) {
        try (InputStream in = SimContracts.class.getClassLoader().getResourceAsStream("contracts/" + path)) {
            if (in == null) {
                throw new IllegalStateException("contract not on the classpath: contracts/" + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
