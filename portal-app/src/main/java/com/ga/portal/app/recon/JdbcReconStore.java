package com.ga.portal.app.recon;

import com.ga.platform.core.tenant.TenantContext;
import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.app.inbox.NotificationParamsJson;
import com.ga.portal.domain.CursorRecord;
import com.ga.portal.domain.DataStatus;
import com.ga.portal.domain.IngestState;
import com.ga.portal.domain.NotificationType;
import com.ga.portal.domain.notify.NotificationRecord;
import com.ga.portal.domain.view.MonthRow;
import com.ga.portal.domain.view.ReconRow;
import com.ga.portal.infra.persistence.EarningsMonthRepository;
import com.ga.portal.infra.persistence.IngestCursorRepository;
import com.ga.portal.infra.persistence.NotificationRepository;
import com.ga.portal.infra.persistence.ReconResultRepository;
import com.ga.portal.infra.persistence.ReconRunRepository;
import com.ga.portal.infra.persistence.TenantRepository;
import com.ga.portal.projection.notify.Candidate;
import com.ga.portal.recon.AgentMonth;
import com.ga.portal.recon.ReconParams;
import com.ga.portal.recon.ReconRunner;
import com.ga.portal.recon.RunRecord;
import com.ga.portal.recon.port.ReconStore;
import com.ga.portal.recon.rule.CursorSnapshot;
import com.ga.portal.recon.rule.PortalMonth;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@link ReconStore}의 JDBC 구현 — 메서드 하나 = 테넌트 바인딩 트랜잭션 하나, <b>늘 새 트랜잭션</b>(회차 잠금을 쥔 바깥 트랜잭션에 합류하지 않는다 — P7-4).
 * DRIFT 행과 {@code data_status}, REPAIRED 행과 {@code OK}와 알림은 각각 같은 트랜잭션이다(C4·C5 — 하나가 실패하면 함께 롤백).
 */
final class JdbcReconStore implements ReconStore {

    private final TransactionTemplate tx;
    private final TenantRepository tenants;
    private final IngestCursorRepository cursors;
    private final EarningsMonthRepository months;
    private final ReconResultRepository results;
    private final ReconRunRepository runs;
    private final NotificationRepository notifications;
    private final ReconProperties.Defaults defaults;
    private final Clock clock;

    JdbcReconStore(PlatformTransactionManager transactionManager, TenantRepository tenants, IngestCursorRepository cursors,
                   EarningsMonthRepository months, ReconResultRepository results, ReconRunRepository runs, NotificationRepository notifications,
                   ReconProperties.Defaults defaults, Clock clock) {
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tenants = tenants;
        this.cursors = cursors;
        this.months = months;
        this.results = results;
        this.runs = runs;
        this.notifications = notifications;
        this.defaults = defaults;
        this.clock = clock;
    }

    private <T> T in(TenantId tenant, Supplier<T> work) {
        try {
            return TenantContext.runWith(tenant, () -> tx.execute(status -> work.get()));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public ReconParams params(TenantId tenant) {
        return in(tenant, () -> TenantReconParams.of(tenants.current().map(t -> t.paramsJson()).orElse("{}"), defaults));
    }

    @Override
    public CursorSnapshot cursor(TenantId tenant) {
        return in(tenant, () -> {
            Optional<CursorRecord> c = cursors.find();
            // 커서가 없으면(인제스트가 한 번도 돌지 않았다) RUNNING이 아닌 것으로 — 대사하지 않는다(LAGGING)
            return c.map(r -> new CursorSnapshot(r.state(), r.lastSeq(), cursors.caughtUpAt()))
                    .orElseGet(() -> new CursorSnapshot(IngestState.PAUSED, 0, Optional.empty()));
        });
    }

    @Override
    public Map<AgentMonth, PortalMonth> months(TenantId tenant, List<String> yms) {
        return in(tenant, () -> {
            Map<AgentMonth, PortalMonth> out = new LinkedHashMap<>();
            for (MonthRow r : months.findByMonths(yms)) {
                out.put(new AgentMonth(r.agentId(), r.closeYm()), portal(r));
            }
            return out;
        });
    }

    @Override
    public Optional<PortalMonth> month(TenantId tenant, AgentMonth key) {
        return in(tenant, () -> months.find(key.agentId(), key.ym()).map(JdbcReconStore::portal));
    }

    private static PortalMonth portal(MonthRow r) {
        return new PortalMonth(r.status(), r.gross(), r.clawbackOffset(), r.adjustment());
    }

    @Override
    public List<ReconRow> results(TenantId tenant) {
        return in(tenant, results::driftAndRepaired);
    }

    @Override
    public List<Instant> rebuilds(TenantId tenant) {
        return in(tenant, runs::rebuilds);
    }

    @Override
    public void drift(TenantId tenant, AgentMonth key, Optional<ReconRow> row) {
        in(tenant, () -> {
            row.ifPresent(results::insertIfAbsent);
            months.markDataStatus(key.agentId(), key.ym(), DataStatus.DRIFT);
            return null;
        });
    }

    @Override
    public Map<NotificationType, Integer> repaired(TenantId tenant, ReconRow row, List<Candidate> candidates) {
        return in(tenant, () -> {
            results.insertIfAbsent(row);
            months.markDataStatus(row.agentId(), row.closeYm(), DataStatus.OK);
            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            Map<NotificationType, Integer> inserted = new EnumMap<>(NotificationType.class);
            for (Candidate c : candidates) {
                boolean added = notifications.insertIfAbsent(new NotificationRepository.Fresh(NotificationRecord.id(tenant.value(), c.agentId(),
                        c.dedupKey()), c.agentId(), c.type().name(), c.severity().name(), c.dedupKey(), c.link(),
                        NotificationParamsJson.write(c.params()), now));
                inserted.merge(c.type(), added ? 1 : 0, Integer::sum);
            }
            return inserted;
        });
    }

    @Override
    public void insertRun(TenantId tenant, RunRecord run) {
        in(tenant, () -> {
            runs.insert(row(run));
            return null;
        });
    }

    @Override
    public void updateRun(TenantId tenant, RunRecord run) {
        in(tenant, () -> runs.update(row(run)));
    }

    private static ReconRunRepository.Run row(RunRecord r) {
        return new ReconRunRepository.Run(r.runAt(), r.actor(), r.trigger(), r.checked(), r.matched(), r.driftNew(), r.driftUnresolved(),
                r.inconclusive(), r.engineCalls(), r.result(), r.rebuiltAt(), r.rebuildOutcome());
    }

    @Override
    public boolean scheduledRunOn(TenantId tenant, LocalDate kstDay) {
        return in(tenant, () -> runs.scheduledBetween(start(kstDay), start(kstDay.plusDays(1))));
    }

    @Override
    public boolean rebuiltOn(TenantId tenant, LocalDate kstDay) {
        return in(tenant, () -> runs.rebuiltBetween(start(kstDay), start(kstDay.plusDays(1))));
    }

    private static Instant start(LocalDate kstDay) {
        return kstDay.atStartOfDay(ReconRunner.KST).toInstant();
    }
}
