package com.ga.portal.inbox;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * 문의 요청 스키마 — <b>클래스패스 사본에서만</b>(빌드가 저장소 {@code contracts/}에서 복사, 원격 조회 0). 입력 규칙의 정본은 계약이고 화면은 같은 계약에서 생성한
 * 규칙 파일로 미리 막을 뿐이다(Phase 5 계획 Q12와 같다). IRI 해석은 {@code portal-sim}의 {@code SimContracts}와 같다.
 */
final class InboxContracts {

    static final String PORTAL = "https://ga.example/portal/contracts/";
    static final String API = PORTAL + "api/v1/portal-api.openapi.yaml#/components/schemas/";
    private static final String META = "https://json-schema.org/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    private final SchemaRegistry registry;

    private InboxContracts() {
        registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12, b -> b.schemas(InboxContracts::resolve));
    }

    private static final class Holder {
        private static final InboxContracts INSTANCE = new InboxContracts();
    }

    static InboxContracts get() {
        return Holder.INSTANCE;
    }

    Schema schema(String name) {
        return registry.getSchema(SchemaLocation.of(API + name));
    }

    private static String resolve(String iri) {
        if (iri.startsWith(PORTAL)) {
            String path = iri.substring(PORTAL.length());
            return JSON.writeValueAsString(YAML.readTree(read(path)));
        }
        if (iri.startsWith(META)) {
            return null;    // 2020-12 메타스키마는 검증기에 내장
        }
        throw new IllegalStateException("schema IRI outside the copied contracts (no remote fetch): " + iri);
    }

    private static String read(String path) {
        try (InputStream in = InboxContracts.class.getResourceAsStream("/contracts/" + path)) {
            if (in == null) {
                throw new IllegalStateException("contract copy missing on the classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
