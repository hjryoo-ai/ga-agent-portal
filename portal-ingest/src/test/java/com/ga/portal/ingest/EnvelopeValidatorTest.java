package com.ga.portal.ingest;

import com.ga.portal.domain.IngestErrorCode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 수신 검증(설계서 §6.1 독 분류 1~4)의 판정 순서와 사유. */
class EnvelopeValidatorTest {

    private static final Path SAMPLES = Path.of(System.getProperty("ga.repoRoot"), "contracts/events/v1/samples");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final EnvelopeValidator validator = new EnvelopeValidator(ContractSchemas.get());

    static ObjectNode sample(String name) {
        try {
            return (ObjectNode) JSON.readTree(Files.readString(samplePaths().stream()
                    .filter(p -> p.getFileName().toString().equals(name)).findFirst().orElseThrow()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 샘플 전부 — v1.2부터 a1/·contract-only/·standalone/ 세 디렉터리. */
    static List<Path> samplePaths() {
        try (Stream<Path> s = Files.walk(SAMPLES)) {
            return s.filter(p -> p.toString().endsWith(".json")).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void all17SamplesAreValid() throws IOException {
        List<Path> files = samplePaths();
        assertThat(files).hasSize(17);
        for (Path f : files) {
            JsonNode node = JSON.readTree(Files.readString(f));
            assertThat(validator.validate(node)).as(f.getFileName().toString()).isInstanceOf(EnvelopeVerdict.Valid.class);
        }
    }

    @Test
    void coreViolationHasNoTrustedKeys() {
        ObjectNode e = sample("02-CommissionCalculated.json");
        e.put("eventId", "not-a-uuid");
        EnvelopeVerdict.Rejected r = (EnvelopeVerdict.Rejected) validator.validate(e);
        assertThat(r.code()).isEqualTo(IngestErrorCode.SCHEMA_VIOLATION);
        assertThat(r.envelope()).isEmpty();
        assertThat(r.detail()).startsWith("envelope core").doesNotContain("not-a-uuid");
    }

    @Test
    void versionAboveTheSupportedMaximumPausesBeforeThePortalSchemaRejectsIt() {
        ObjectNode e = sample("09-PayoutCompleted.json");
        e.put("version", 2);
        EnvelopeVerdict.Rejected r = (EnvelopeVerdict.Rejected) validator.validate(e);
        assertThat(r.code()).isEqualTo(IngestErrorCode.UNSUPPORTED_VERSION);
        assertThat(r.detail()).contains("version 2 > 1");
    }

    @Test
    void typeKindMismatchKeepsTheKeys() {
        ObjectNode e = sample("02-CommissionCalculated.json");
        ((ObjectNode) e.path("aggregate")).put("kind", "POLICY");
        EnvelopeVerdict.Rejected r = (EnvelopeVerdict.Rejected) validator.validate(e);
        assertThat(r.code()).isEqualTo(IngestErrorCode.TYPE_KIND_MISMATCH);
        assertThat(r.envelope()).isPresent();
        assertThat(r.detail()).contains("requires kind COMM_CALC, got POLICY");
    }

    @Test
    void payloadViolationIsASchemaViolationWithKeysAndNoValues() {
        ObjectNode e = sample("02-CommissionCalculated.json");
        ((ObjectNode) e.path("payload")).remove("amount");
        ((ObjectNode) e.path("payload")).put("policyNo", "");
        EnvelopeVerdict.Rejected r = (EnvelopeVerdict.Rejected) validator.validate(e);
        assertThat(r.code()).isEqualTo(IngestErrorCode.SCHEMA_VIOLATION);
        assertThat(r.envelope()).isPresent();
        assertThat(r.detail()).contains("seq 2").contains(e.path("eventId").asString()).doesNotContain("2800000");
    }

    @Test
    void unknownFieldsAreKeptAndIgnored() {
        ObjectNode e = sample("02-CommissionCalculated.json");
        e.put("traceId", "t-1");
        ((ObjectNode) e.path("payload")).put("futureField", 1);
        EnvelopeVerdict.Valid v = (EnvelopeVerdict.Valid) validator.validate(e);
        assertThat(v.envelope().source().path("traceId").asString()).isEqualTo("t-1");
        assertThat(v.envelope().payload().path("futureField").asInt()).isEqualTo(1);
        assertThat(v.envelope().aggregate().kind()).isEqualTo("COMM_CALC");
    }
}
