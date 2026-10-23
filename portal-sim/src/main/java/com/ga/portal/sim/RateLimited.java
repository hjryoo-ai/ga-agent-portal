package com.ga.portal.sim;

/** 레이트 리밋(포털 Phase 5 ③) — 429 {@code RATE_LIMITED} + {@code Retry-After}. 행·감사·엔진 호출 없음. */
public final class RateLimited extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;

    public RateLimited(long retryAfterSeconds) {
        super("RATE_LIMITED", null, false, false);
        if (retryAfterSeconds < 1) {
            throw new IllegalArgumentException("Retry-After must be at least 1");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
