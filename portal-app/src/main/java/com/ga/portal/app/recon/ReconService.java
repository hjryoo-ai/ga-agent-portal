package com.ga.portal.app.recon;

import com.ga.platform.core.tenant.TenantContext;
import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.ReconTrigger;
import com.ga.portal.infra.persistence.TenantRecord;
import com.ga.portal.infra.persistence.TenantRepository;
import com.ga.portal.ingest.apply.TenantTransactions;
import com.ga.portal.recon.ReconRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 대사 진입점(계획 Q9): CLI {@code recon run}과 스케줄러 틱이 같은 {@link ReconRunner#run}을 테넌트 범위({@link TenantContext}) 안에서 부른다 — 회차 잠금의 바깥
 * 트랜잭션이 그 범위에 묶인다. 틱은 {@code ACTIVE} 테넌트마다 {@link ReconRunner#runIfDue}이고 한 테넌트의 예외는 그 테넌트에만 남는다(인제스트 스케줄러와 같다).
 * 로그에는 테넌트와 예외 종류만(값 0).
 */
public final class ReconService {

    private static final Logger LOG = LoggerFactory.getLogger(ReconService.class);

    /** 돌 테넌트 목록(운영 {@code TenantDirectoryReader} — 전용 롤 {@code portal_directory}). */
    @FunctionalInterface
    public interface TenantDirectory {
        List<TenantId> tenants();
    }

    private final ReconRunner runner;
    private final TenantDirectory directory;
    private final TenantTransactions transactions;
    private final TenantRepository tenants;

    ReconService(ReconRunner runner, TenantDirectory directory, TenantTransactions transactions, TenantRepository tenants) {
        this.runner = runner;
        this.directory = directory;
        this.transactions = transactions;
        this.tenants = tenants;
    }

    /** 스케줄러의 창({당월, 직전 달} KST) — CLI가 {@code --ym} 없이 부를 때. */
    public List<String> window() {
        return runner.window();
    }

    public ReconRunner.Outcome run(TenantId tenant, ReconTrigger trigger, String actor, List<String> months) {
        return scoped(tenant, () -> runner.run(tenant, trigger, actor, months));
    }

    /** 스케줄러 틱 — 돌린 회차 수. */
    public int tick() {
        int ran = 0;
        for (TenantId tenant : directory.tenants()) {
            try {
                Optional<TenantRecord> record = transactions.inTransaction(tenant, tenants::current);
                if (record.isEmpty() || !"ACTIVE".equals(record.get().status())) {
                    continue;
                }
                if (scoped(tenant, () -> runner.runIfDue(tenant)).isPresent()) {
                    ran++;
                }
            } catch (RuntimeException e) {
                LOG.warn("recon run of tenant {} ended with {}", tenant.value(), e.getClass().getSimpleName());
            }
        }
        return ran;
    }

    private static <T> T scoped(TenantId tenant, Supplier<T> work) {
        try {
            return TenantContext.runWith(tenant, work::get);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
