package com.ga.portal.ingest;

import com.ga.platform.canonical.Canonicalizer;
import com.ga.platform.canonical.Sha256;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code ingest_event.payload_hash}(Phase 1 지시문 D-1, 계획 승인 Q1) = SHA-256 hex(JCS(<code>{type, version, occurredAt, aggregate,
 * payload}</code>)). {@code seq}·{@code eventId}(키)와 Envelope의 모르는 최상위 필드(전달 메타데이터)는 넣지 않는다 — 같은 사실의 재발행은
 * 같은 해시, 내용·시각·집합·판이 다르면 다른 해시. 값은 받은 JSON 그대로(다시 쓰지 않는다).
 */
public final class PayloadHasher {

    static final String[] FIELDS = {"type", "version", "occurredAt", "aggregate", "payload"};
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private PayloadHasher() {
    }

    public static String hash(JsonNode envelope) {
        ObjectNode subject = JSON.createObjectNode();
        for (String field : FIELDS) {
            JsonNode value = envelope.get(field);
            if (value == null) {
                throw new IllegalArgumentException("envelope has no " + field);
            }
            subject.set(field, value);
        }
        return Sha256.of(Canonicalizer.canonicalize(subject));
    }
}
