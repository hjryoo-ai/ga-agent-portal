package com.ga.portal.inbox;

/** 문의 레이트 리밋(포털 Phase 6 ⑥ — 설계사당 24시간 N) — 429 {@code RATE_LIMITED}(문의 본문 — 계획 Q21) + {@code Retry-After}. 행·감사 없음. */
public final class InquiryRateLimited extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;

    public InquiryRateLimited(long retryAfterSeconds) {
        super("inquiry rate limited", null, false, false);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
