package com.ga.portal.ingest;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 해시 범위(계획 승인 Q1): {type, version, occurredAt, aggregate, payload} — seq·eventId·모르는 최상위 필드는 빠진다. */
class PayloadHasherTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void sameFactSameHashRegardlessOfKeyOrderSeqEventIdAndTransportFields() {
        ObjectNode a = EnvelopeValidatorTest.sample(2);
        String h = PayloadHasher.hash(a);
        assertThat(h).matches("[0-9a-f]{64}");
        ObjectNode b = a.deepCopy();
        b.put("seq", 99);
        b.put("eventId", "5eed0000-0000-4000-8000-0000000000ff");
        b.put("traceId", "redelivery-2");
        assertThat(PayloadHasher.hash(b)).isEqualTo(h);
        ObjectNode reordered = (ObjectNode) JSON.readTree(
                "{\"payload\":" + a.path("payload") + ",\"aggregate\":" + a.path("aggregate") + ",\"occurredAt\":\"" + a.path("occurredAt").asString()
                        + "\",\"version\":1,\"type\":\"CommissionCalculated\"}");
        assertThat(PayloadHasher.hash(reordered)).isEqualTo(h);
    }

    @Test
    void anyChangeInTheHashedFieldsChangesTheHash() {
        ObjectNode a = EnvelopeValidatorTest.sample(2);
        String h = PayloadHasher.hash(a);
        ObjectNode amount = a.deepCopy();
        ((ObjectNode) amount.path("payload")).put("amount", 2_800_001);
        ObjectNode occurred = a.deepCopy();
        occurred.put("occurredAt", "2026-08-20T10:15:31+09:00");
        ObjectNode aggregate = a.deepCopy();
        ((ObjectNode) aggregate.path("aggregate")).put("id", "999");
        ObjectNode added = a.deepCopy();
        ((ObjectNode) added.path("payload")).put("futureField", 1);
        for (ObjectNode changed : new ObjectNode[]{amount, occurred, aggregate, added}) {
            assertThat(PayloadHasher.hash(changed)).isNotEqualTo(h);
        }
    }

    @Test
    void anEnvelopeWithoutAHashedFieldIsRefused() {
        ObjectNode a = EnvelopeValidatorTest.sample(2);
        a.remove("aggregate");
        assertThatThrownBy(() -> PayloadHasher.hash(a)).hasMessageContaining("aggregate");
    }
}
