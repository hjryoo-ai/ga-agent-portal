package com.ga.portal.ingest;

import com.ga.portal.domain.IngestErrorCode;
import com.ga.portal.ingest.testing.Samples;
import com.ga.portal.projection.testing.Envelopes;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 수신 검증(설계서 §6.1 독 분류 1~4)의 판정 순서와 사유. */
class EnvelopeValidatorTest {

    private final EnvelopeValidator validator = new EnvelopeValidator(ContractSchemas.get());

    /** 정규 스트림(v1.3 — {@link Samples})의 그 seq 이벤트 복사본: 2 = A-1 CommissionCalculated(FY), 7 = A-1 PayoutCompleted. */
    static ObjectNode sample(long seq) {
        return Samples.seq(seq);
    }

    @Test
    void all17SamplesAreValid() {
        List<ObjectNode> events = Samples.all();
        assertThat(events).hasSize(17);
        for (ObjectNode e : events) {
            assertThat(validator.validate(e)).as("seq %d %s", e.path("seq").asLong(), e.path("type").asString())
                    .isInstanceOf(EnvelopeVerdict.Valid.class);
        }
    }

    /**
     * 화면 E2E 손 스트림(web/e2e/streams — 포털 Phase 4 Q11, Phase 6 GA05): 하네스가 시작할 때 하는 검증과 같은 판정을 Docker 없이 먼저 — 모든 줄이 계약 스키마를
     * 통과하고 seq가 1부터 이어진다(GA01 이웃 스트림은 A-1 뒤에 붙으므로 12부터).
     */
    @Test
    void everyHandMadeE2eStreamLineIsValidAndContiguous() throws IOException {
        Path dir = Envelopes.repoRoot().resolve("web/e2e/streams");
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(f -> f.getFileName().toString().endsWith(".jsonl")).sorted().toList();
        }
        assertThat(files).extracting(f -> f.getFileName().toString()).containsExactly("edges.jsonl", "ga01-neighbor.jsonl", "ga05-limits.jsonl");
        for (Path f : files) {
            List<ObjectNode> events = Envelopes.nodes(f);
            long first = f.getFileName().toString().equals("ga01-neighbor.jsonl") ? Envelopes.nodes(Envelopes.a1()).size() + 1 : 1;
            for (int i = 0; i < events.size(); i++) {
                ObjectNode e = events.get(i);
                assertThat(e.path("seq").asLong()).as("%s line %d", f.getFileName(), i + 1).isEqualTo(first + i);
                assertThat(validator.validate(e)).as("%s seq %d", f.getFileName(), e.path("seq").asLong()).isInstanceOf(EnvelopeVerdict.Valid.class);
            }
        }
    }

    @Test
    void coreViolationHasNoTrustedKeys() {
        ObjectNode e = sample(2);
        e.put("eventId", "not-a-uuid");
        EnvelopeVerdict.Rejected r = (EnvelopeVerdict.Rejected) validator.validate(e);
        assertThat(r.code()).isEqualTo(IngestErrorCode.SCHEMA_VIOLATION);
        assertThat(r.envelope()).isEmpty();
        assertThat(r.detail()).startsWith("envelope core").doesNotContain("not-a-uuid");
    }

    @Test
    void versionAboveTheSupportedMaximumPausesBeforeThePortalSchemaRejectsIt() {
        ObjectNode e = sample(7);
        e.put("version", 2);
        EnvelopeVerdict.Rejected r = (EnvelopeVerdict.Rejected) validator.validate(e);
        assertThat(r.code()).isEqualTo(IngestErrorCode.UNSUPPORTED_VERSION);
        assertThat(r.detail()).contains("version 2 > 1");
    }

    @Test
    void typeKindMismatchKeepsTheKeys() {
        ObjectNode e = sample(2);
        ((ObjectNode) e.path("aggregate")).put("kind", "POLICY");
        EnvelopeVerdict.Rejected r = (EnvelopeVerdict.Rejected) validator.validate(e);
        assertThat(r.code()).isEqualTo(IngestErrorCode.TYPE_KIND_MISMATCH);
        assertThat(r.envelope()).isPresent();
        assertThat(r.detail()).contains("requires kind COMM_CALC, got POLICY");
    }

    @Test
    void payloadViolationIsASchemaViolationWithKeysAndNoValues() {
        ObjectNode e = sample(2);
        ((ObjectNode) e.path("payload")).remove("amount");
        ((ObjectNode) e.path("payload")).put("policyNo", "");
        EnvelopeVerdict.Rejected r = (EnvelopeVerdict.Rejected) validator.validate(e);
        assertThat(r.code()).isEqualTo(IngestErrorCode.SCHEMA_VIOLATION);
        assertThat(r.envelope()).isPresent();
        assertThat(r.detail()).contains("seq 2").contains(e.path("eventId").asString()).doesNotContain("2800000");
    }

    @Test
    void unknownFieldsAreKeptAndIgnored() {
        ObjectNode e = sample(2);
        e.put("traceId", "t-1");
        ((ObjectNode) e.path("payload")).put("futureField", 1);
        EnvelopeVerdict.Valid v = (EnvelopeVerdict.Valid) validator.validate(e);
        assertThat(v.envelope().source().path("traceId").asString()).isEqualTo("t-1");
        assertThat(v.envelope().payload().path("futureField").asInt()).isEqualTo(1);
        assertThat(v.envelope().aggregate().kind()).isEqualTo("COMM_CALC");
    }
}
