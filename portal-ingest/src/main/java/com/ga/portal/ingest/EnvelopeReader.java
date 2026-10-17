package com.ga.portal.ingest;

import com.ga.portal.projection.Envelope;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 핵심 Envelope 검증을 통과한 JSON을 {@link Envelope}로 읽는다. 모르는 필드는 보존하고 무시한다(설계서 §4.5) —
 * {@code FAIL_ON_UNKNOWN_PROPERTIES=false}는 <b>이 역직렬화기에만</b> 둔다(앱 전역 설정은 Phase 3에서 반대로 엄격하게).
 */
public final class EnvelopeReader {

    private static final JsonMapper LENIENT = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** 핵심 필드만 가진 읽기 모양. 모르는 최상위 필드는 무시되고 원문은 {@link Envelope#source()}로 남는다. */
    record Head(String eventId, long seq, String type, int version, String occurredAt, Envelope.Aggregate aggregate) {
    }

    private EnvelopeReader() {
    }

    public static Envelope read(JsonNode node) {
        if (!(node instanceof ObjectNode object)) {
            throw new IllegalArgumentException("envelope is not a JSON object");
        }
        Head head = LENIENT.treeToValue(node, Head.class);
        return new Envelope(head.eventId(), head.seq(), head.type(), head.version(), head.occurredAt(), head.aggregate(),
                object.path("payload").deepCopy(), object.deepCopy());
    }
}
