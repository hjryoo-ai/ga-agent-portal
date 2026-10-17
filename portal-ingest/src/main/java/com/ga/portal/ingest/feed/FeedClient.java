package com.ga.portal.ingest.feed;

import com.ga.platform.core.tenant.TenantId;

/**
 * 엔진 이벤트 피드 포트(설계서 §4.1, 계약 {@code GET /internal/v1/events}). 구현은 {@link HttpFeedClient}(엔진)와 {@link FileFeedAdapter}
 * (JSONL — 데모·시험)이고 둘 다 같은 계약 스위트를 통과한다(Phase 1 지시문 D-7).
 *
 * <p>클라이언트는 응답 모양(계약 {@code FeedResponse})과 응답 테넌트만 본다 — 이벤트 순서·갭·중복은 그대로 돌려주고 판정은 적용기가 한다.
 */
public interface FeedClient {

    int DEFAULT_LIMIT = 200;
    int MAX_LIMIT = 1000;

    /**
     * {@code afterSeq} 다음부터 최대 {@code limit}건.
     *
     * @throws FeedException 401·422·응답 테넌트 불일치·토큰 없음·도달 불가·형식 오류
     * @throws IllegalArgumentException {@code afterSeq} &lt; 0 이거나 {@code limit}이 1~1000 밖(호출 전에 거부)
     */
    FeedPage fetch(TenantId tenant, long afterSeq, int limit);

    default FeedPage fetch(TenantId tenant, long afterSeq) {
        return fetch(tenant, afterSeq, DEFAULT_LIMIT);
    }

    static void checkArguments(long afterSeq, int limit) {
        if (afterSeq < 0) {
            throw new IllegalArgumentException("afterSeq must be >= 0: " + afterSeq);
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be 1.." + MAX_LIMIT + ": " + limit);
        }
    }
}
