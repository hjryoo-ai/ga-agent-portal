package com.ga.portal.recon.engine;

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
 * 명세 조회 응답의 계약 스키마 — <b>클래스패스 사본에서만</b>(빌드가 저장소 {@code contracts/}에서 복사, 원격 조회 0). 200 본문은 포털 계약의 payload
 * 스키마({@code getStatement}의 {@code oneOf} — 모드가 둘 중 하나를 고른다), 오류 본문은 엔진 계약 사본의 {@code Problem}. IRI 해석은
 * {@code portal-sim}의 {@code SimContracts}와 같다.
 */
public final class ReconContracts {

    static final String PORTAL = "https://ga.example/portal/contracts/";
    private static final String META = "https://json-schema.org/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    private final SchemaRegistry registry;

    private ReconContracts() {
        registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12, b -> b.schemas(ReconContracts::resolve));
    }

    private static final class Holder {
        private static final ReconContracts INSTANCE = new ReconContracts();
    }

    public static ReconContracts get() {
        return Holder.INSTANCE;
    }

    /** {@code StatementEstimated}·{@code StatementConfirmed} payload 스키마. */
    public Schema payload(String type) {
        return registry.getSchema(SchemaLocation.of(PORTAL + "events/v1/payloads/" + type + ".v1.schema.json"));
    }

    /** 엔진 계약 사본의 {@code Problem}. */
    public Schema problem() {
        return registry.getSchema(SchemaLocation.of(PORTAL + "api/v1/engine-internal.openapi.yaml#/components/schemas/Problem"));
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
        try (InputStream in = ReconContracts.class.getClassLoader().getResourceAsStream("contracts/" + path)) {
            if (in == null) {
                throw new IllegalStateException("contract not on the classpath: contracts/" + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
