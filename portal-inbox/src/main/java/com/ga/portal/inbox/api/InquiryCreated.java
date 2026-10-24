package com.ga.portal.inbox.api;

import java.time.Instant;
import java.util.Optional;

/** 접수 결과(계약 {@code InquiryCreated}) — 본문이 없다(계획 Q23). */
public record InquiryCreated(String id, String status, String category, Optional<String> calcId, Instant createdAt) {
}
