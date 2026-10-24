package com.ga.portal.inbox.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 내 문의 한 페이지(계약 {@code InquiriesResponse}) — 항목에 본문이 없다(계획 Q23). */
public record InquiriesResponse(List<Item> items, int page, int size, long total) {

    public InquiriesResponse {
        items = List.copyOf(items);
    }

    public record Item(String id, String status, String category, Optional<String> calcId, Instant createdAt, Optional<Instant> answeredAt) {
    }
}
