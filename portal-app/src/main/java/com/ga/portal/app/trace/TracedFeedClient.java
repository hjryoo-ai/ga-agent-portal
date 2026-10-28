package com.ga.portal.app.trace;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.trace.Tracing;
import com.ga.portal.ingest.feed.FeedClient;
import com.ga.portal.ingest.feed.FeedPage;

/** 피드 호출 스팬 {@code engine.feed}({@code outcome} = {@code OK} 또는 예외 종류 이름 — 메시지는 싣지 않는다). */
public final class TracedFeedClient implements FeedClient {

    private final FeedClient delegate;
    private final Tracing tracing;

    public TracedFeedClient(FeedClient delegate, Tracing tracing) {
        this.delegate = delegate;
        this.tracing = tracing;
    }

    @Override
    public FeedPage fetch(TenantId tenant, long afterSeq, int limit) {
        Tracing.Span span = tracing.start("engine.feed");
        try {
            FeedPage page = delegate.fetch(tenant, afterSeq, limit);
            span.kind("outcome", "OK");
            return page;
        } catch (RuntimeException e) {
            span.kind("outcome", e.getClass().getSimpleName());
            throw e;
        } finally {
            span.close();
        }
    }
}
