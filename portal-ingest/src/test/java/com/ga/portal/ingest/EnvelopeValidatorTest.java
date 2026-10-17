package com.ga.portal.ingest;

import com.ga.portal.domain.IngestErrorCode;
import com.ga.portal.ingest.testing.Samples;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

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
