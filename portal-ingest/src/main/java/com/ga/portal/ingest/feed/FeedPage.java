package com.ga.portal.ingest.feed;

import com.ga.platform.core.tenant.TenantId;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * 피드 응답 한 묶음(계약 {@code FeedResponse}). {@code events}는 받은 JSON 그대로(검증 전) — 수신 검증은 적용기가 이벤트마다 한다.
 *
 * @param nextSeq 돌려준 마지막 seq(없으면 요청의 afterSeq)
 * @param headSeq 공급자의 최대 seq — 지연 지표 {@code ingest_lag_seq = headSeq − last_seq}
 */
public record FeedPage(TenantId tenantId, List<JsonNode> events, long nextSeq, long headSeq, int schemaVersion) {

    public FeedPage {
        events = List.copyOf(events);
    }
}
