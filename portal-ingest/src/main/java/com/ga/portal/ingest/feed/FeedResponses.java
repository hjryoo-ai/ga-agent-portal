package com.ga.portal.ingest.feed;

import com.ga.platform.canonical.CanonicalizationException;
import com.ga.platform.canonical.Canonicalizer;
import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.ingest.ContractSchemas;
import com.networknt.schema.Error;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 두 어댑터가 함께 쓰는 응답 해석: ① 중복 키를 거부하며 읽고 ② 계약 {@code FeedResponse}의 모양을 본다(이벤트 본문은 빼고 — 이벤트마다의
 * 판정은 적용기의 일이라, 독 이벤트 하나가 묶음 전체를 "도달 불가"로 만들지 않게) ③ 응답 테넌트가 요청한 테넌트인지 본다(C9).
 */
final class FeedResponses {

    private FeedResponses() {
    }

    static FeedPage parse(TenantId requested, String body) {
        JsonNode root;
        try {
            root = Canonicalizer.parseStrict(body);
        } catch (CanonicalizationException e) {
            throw new FeedException.Unavailable("feed response is not valid JSON", e);
        }
        return parse(requested, root);
    }

    static FeedPage parse(TenantId requested, JsonNode root) {
        if (!(root instanceof ObjectNode object) || !object.path("events").isArray()) {
            throw new FeedException.Unavailable("feed response is not a FeedResponse object with an events array");
        }
        ObjectNode shape = object.deepCopy();
        shape.putArray("events");
        List<Error> errors = ContractSchemas.get().feedResponse().validate(shape);
        if (!errors.isEmpty()) {
            throw new FeedException.Unavailable("feed response does not match FeedResponse: "
                    + errors.stream().limit(5).map(e -> e.getInstanceLocation() + " " + e.getKeyword()).toList());
        }
        String responded = object.path("tenantId").asString();
        if (!responded.equals(requested.value())) {
            throw new FeedException.TenantMismatch(requested, responded);
        }
        List<JsonNode> events = new ArrayList<>();
        object.path("events").forEach(events::add);
        return new FeedPage(requested, events, object.path("nextSeq").asLong(), object.path("headSeq").asLong(),
                object.path("schemaVersion").asInt());
    }
}
