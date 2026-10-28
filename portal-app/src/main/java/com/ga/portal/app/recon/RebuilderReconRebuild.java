package com.ga.portal.app.recon;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.app.ingest.IngestCommands;
import com.ga.portal.domain.RebuildOutcome;
import com.ga.portal.infra.persistence.AuditAccessRepository;
import com.ga.portal.ingest.apply.IngestMetrics;
import com.ga.portal.ingest.apply.Rebuilder;
import com.ga.portal.recon.port.ReconRebuild;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 자동 재구축(계획 ③ R4): 기존 재구축기 그대로(txA/txB/txC — 이 재구축기는 늘 새 트랜잭션, P7-4), 감사 {@code INGEST_REBUILD*}의 행위자는
 * {@code recon:<tenant>}, 지표 {@code ingest_rebuild_seconds}도 그대로. 거부(워커가 잠금을 쥠·재구축 불가 상태)는 시도가 아니다. 재구축은 알림을 파생하지 않는다.
 */
final class RebuilderReconRebuild implements ReconRebuild {

    private final Rebuilder rebuilder;
    private final AuditAccessRepository audit;
    private final IngestMetrics metrics;
    private final Clock clock;

    RebuilderReconRebuild(Rebuilder rebuilder, AuditAccessRepository audit, IngestMetrics metrics, Clock clock) {
        this.rebuilder = rebuilder;
        this.audit = audit;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    public Optional<RebuildOutcome> rebuild(TenantId tenant, String actor) {
        Instant started = clock.instant();
        Rebuilder.Result result = rebuilder.rebuild(tenant, (step, target) -> audit.record(actor, switch (step) {
            case STARTED -> IngestCommands.REBUILD;
            case REJECTED -> IngestCommands.REBUILD_REJECTED;
            case SUCCEEDED -> IngestCommands.REBUILD_SUCCEEDED;
            case FAILED -> IngestCommands.REBUILD_FAILED;
        }, target, clock.instant()));
        Duration took = Duration.between(started, clock.instant());
        return switch (result) {
            case Rebuilder.Result.Rebuilt r -> {
                metrics.rebuild(tenant, "succeeded", took);
                yield Optional.of(RebuildOutcome.SUCCEEDED);
            }
            case Rebuilder.Result.Failed f -> {
                metrics.rebuild(tenant, "failed", took);
                yield Optional.of(RebuildOutcome.FAILED);
            }
            case Rebuilder.Result.Rejected r -> Optional.empty();
        };
    }
}
