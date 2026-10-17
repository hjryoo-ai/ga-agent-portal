package com.ga.portal.ingest.apply;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.CursorRecord;
import com.ga.portal.domain.IngestTransitions;
import com.ga.portal.ingest.feed.FeedClient;
import com.ga.portal.ingest.feed.FeedException;
import com.ga.portal.ingest.feed.FeedPage;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * 테넌트 하나의 한 회차(설계서 §6.1 트랜잭션 경계 그림): tx₀ 커서(없으면 생성) → RUNNING이 아니면 끝 → 피드 호출(트랜잭션 밖) → 이벤트마다
 * {@link IngestApplier}. 피드 실패는 사유가 있으면 테넌트 정지, 없으면(일시적) 상태 불변·지표. 적용 실패 뒤 백오프 없음 — 멈추는 것이 규약
 * (지시문 D-10). 한 테넌트의 실패는 그 테넌트에만 남는다 — 워커 전체 상태는 없다(C5).
 */
public final class IngestWorker {

    /** 회차 결과. */
    public record Round(TenantId tenant, int applied, int skipped, Optional<IngestApplier.Result> stoppedBy, boolean feedUnavailable,
                        boolean polled) {
    }

    private final FeedClient feed;
    private final IngestStore store;
    private final TenantTransactions transactions;
    private final IngestApplier applier;
    private final IngestMetrics metrics;
    private final Clock clock;
    private final int limit;

    public IngestWorker(FeedClient feed, IngestStore store, TenantTransactions transactions, IngestApplier applier, IngestMetrics metrics,
                        Clock clock, int limit) {
        this.feed = Objects.requireNonNull(feed, "feed");
        this.store = Objects.requireNonNull(store, "store");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.applier = Objects.requireNonNull(applier, "applier");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.clock = Objects.requireNonNull(clock, "clock");
        FeedClient.checkArguments(0, limit);
        this.limit = limit;
    }

    public Round runOnce(TenantId tenant) {
        CursorRecord cursor = transactions.inTransaction(tenant, () -> store.ensureCursor(clock.instant()));
        if (!IngestTransitions.polls(cursor.state())) {
            metrics.state(tenant, cursor.state());
            return new Round(tenant, 0, 0, Optional.empty(), false, false);
        }
        FeedPage page;
        try {
            page = feed.fetch(tenant, cursor.lastSeq(), limit);
        } catch (FeedException e) {
            if (e.stopsWith().isPresent()) {
                IngestApplier.Result r = applier.stop(tenant, e.stopsWith().get(), e.getMessage(), Optional.empty());
                finish(tenant, Optional.empty());
                return new Round(tenant, 0, 0, Optional.of(r), false, true);
            }
            metrics.feedError(tenant);
            finish(tenant, Optional.empty());
            return new Round(tenant, 0, 0, Optional.empty(), true, true);
        }
        int applied = 0;
        int skipped = 0;
        Optional<IngestApplier.Result> stopped = Optional.empty();
        for (JsonNode event : page.events()) {
            IngestApplier.Result r = applier.apply(tenant, event);
            if (r.outcome() == IngestApplier.Outcome.APPLIED) {
                applied++;
                metrics.event(tenant, "applied");
            } else if (r.outcome() == IngestApplier.Outcome.SKIPPED) {
                skipped++;
                metrics.event(tenant, "skipped");
            } else {
                if (r.outcome() == IngestApplier.Outcome.STOPPED) {
                    metrics.event(tenant, "failed");
                    stopped = Optional.of(r);
                }
                break;
            }
        }
        finish(tenant, Optional.of(page.headSeq()));
        return new Round(tenant, applied, skipped, stopped, false, true);
    }

    private void finish(TenantId tenant, Optional<Long> headSeq) {
        CursorRecord after = transactions.inTransaction(tenant, () -> store.ensureCursor(clock.instant()));
        metrics.state(tenant, after.state());
        headSeq.ifPresent(head -> {
            long lagSeq = Math.max(0, head - after.lastSeq());
            long lagSeconds = lagSeq == 0 ? 0 : Math.max(0, Duration.between(after.updatedAt(), clock.instant()).toSeconds());
            metrics.lag(tenant, lagSeq, lagSeconds);
        });
    }
}
