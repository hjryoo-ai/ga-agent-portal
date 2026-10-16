package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.infra.persistence.TenantRecord;
import com.ga.portal.infra.persistence.TenantRepository;
import com.ga.portal.ingest.apply.IngestWorker;
import com.ga.portal.ingest.apply.PollSchedule;
import com.ga.portal.ingest.apply.TenantTransactions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 인제스트 회차 스케줄(지시문 D-5·D-10): 틱마다 테넌트 목록(전용 롤 {@code portal_directory})을 돌며 주기가 된 테넌트의 회차를 하나씩 돌린다.
 * {@code tenant.status}가 {@code ACTIVE}가 아니면 폴링하지 않는다. 한 테넌트의 예외는 그 테넌트에만 남는다 — 다음 테넌트는 같은 틱에 돈다
 * (C5, 워커 전체 상태 없음). 인스턴스가 둘이어도 advisory lock이 테넌트당 하나만 적용하게 한다.
 */
public final class IngestScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(IngestScheduler.class);

    /** 돌 테넌트 목록. 운영은 {@code TenantDirectoryReader}(V3 롤), 시험은 고정 목록. */
    @FunctionalInterface
    public interface TenantDirectory {
        List<TenantId> tenants();
    }

    private final TenantDirectory directory;
    private final TenantTransactions transactions;
    private final TenantRepository tenants;
    private final IngestWorker worker;
    private final PollSchedule schedule;
    private final Duration defaultInterval;
    private final Clock clock;

    public IngestScheduler(TenantDirectory directory, TenantTransactions transactions, TenantRepository tenants, IngestWorker worker,
                           PollSchedule schedule, Duration defaultInterval, Clock clock) {
        this.directory = directory;
        this.transactions = transactions;
        this.tenants = tenants;
        this.worker = worker;
        this.schedule = schedule;
        this.defaultInterval = defaultInterval;
        this.clock = clock;
    }

    /** 한 틱. 돌린 회차를 돌려준다(시험·관측용). */
    public List<IngestWorker.Round> tick() {
        List<IngestWorker.Round> rounds = new ArrayList<>();
        for (TenantId tenant : directory.tenants()) {
            Instant now = clock.instant();
            if (!schedule.due(tenant, now)) {
                continue;
            }
            Duration interval = defaultInterval;
            try {
                Optional<TenantRecord> record = transactions.inTransaction(tenant, tenants::current);
                if (record.isEmpty() || !"ACTIVE".equals(record.get().status())) {
                    continue;
                }
                PollSchedule.Interval i = schedule.intervalOf(record.get().paramsJson());
                i.rejected().ifPresent(reason -> LOG.warn("tenant {}: {} — using {}s", tenant.value(), reason, i.duration().toSeconds()));
                interval = i.duration();
                rounds.add(worker.runOnce(tenant));
            } catch (RuntimeException e) {
                LOG.warn("ingest round of tenant {} ended with {}", tenant.value(), e.getClass().getSimpleName());
            } finally {
                schedule.ranAt(tenant, now, interval);
            }
        }
        return rounds;
    }
}
