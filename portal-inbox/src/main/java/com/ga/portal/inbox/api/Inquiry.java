package com.ga.portal.inbox.api;

import java.time.Instant;
import java.util.Optional;

/**
 * 문의 상세(계약 {@code Inquiry}) — 본문·답변이 나오는 유일한 응답(작성자 본인, C7). 답변자({@code answered_by})는 운영 주체라 싣지 않는다. 본문을 로그로 흘리지
 * 않게 {@link #toString()}은 키·상태만.
 */
public record Inquiry(String id, String status, String category, Optional<String> calcId, String message, Instant createdAt,
                      Optional<Instant> answeredAt, Optional<String> answer) {

    @Override
    public String toString() {
        return "Inquiry[id=" + id + ", status=" + status + "]";
    }
}
