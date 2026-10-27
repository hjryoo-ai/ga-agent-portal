package com.ga.portal.app.recon;

import com.ga.platform.core.tenant.TenantContext;
import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.infra.persistence.ReconLockRepository;
import com.ga.portal.recon.port.ReconLock;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.Optional;

/**
 * 회차 잠금(계획 Q9 R0, P7-4): 바깥 테넌트 트랜잭션을 열고 {@code pg_try_advisory_xact_lock(hashtext('portal.recon'), …)}을 잡아 회차가 끝날 때까지 연 채로
 * 둔다 — 잠금은 그 트랜잭션이 끝나야 풀린다(연결이 죽어도 풀린다). 회차 안의 저장·재구축은 새 트랜잭션(REQUIRES_NEW)이라 이 트랜잭션은 잠금 말고는 아무것도
 * 하지 않는다. {@link TenantContext} 범위 안에서 불러야 한다({@code ReconService}).
 */
final class TransactionalReconLock implements ReconLock {

    private final PlatformTransactionManager transactionManager;
    private final ReconLockRepository locks;

    TransactionalReconLock(PlatformTransactionManager transactionManager, ReconLockRepository locks) {
        this.transactionManager = transactionManager;
        this.locks = locks;
    }

    @Override
    public Optional<Held> tryLock(TenantId tenant) {
        if (!TenantContext.isBound() || !TenantContext.current().equals(tenant)) {
            throw new IllegalStateException("the recon lock is taken inside the tenant's scope");
        }
        TransactionStatus outer = transactionManager.getTransaction(new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        boolean locked;
        try {
            locked = locks.tryLock();
        } catch (RuntimeException e) {
            transactionManager.rollback(outer);
            throw e;
        }
        if (!locked) {
            transactionManager.rollback(outer);
            return Optional.empty();
        }
        return Optional.of(() -> transactionManager.rollback(outer));   // 쓴 것이 없다 — 끝내면 잠금이 풀린다
    }
}
