package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantContext;
import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.ingest.apply.TenantTransactions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 테넌트 트랜잭션: {@code TenantContext.runWith(tenant)} 안에서 {@code TenantSessionBinder}(플랫폼 트랜잭션 매니저)의 트랜잭션 하나 — RLS 세션 값은
 * 바인더가 넣는다. 런타임 예외면 롤백 후 그대로 던진다. 이미 바인딩된 다른 테넌트 안에서는 부를 수 없다(플랫폼이 거부).
 */
public final class SpringTenantTransactions implements TenantTransactions {

    private final TransactionTemplate template;

    SpringTenantTransactions(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    private SpringTenantTransactions(TransactionTemplate template) {
        this.template = template;
    }

    /**
     * 늘 새 트랜잭션(포털 Phase 7 P7-4): 대사 회차는 회차 잠금을 쥔 바깥 트랜잭션을 연 채로 돈다 — 그 안의 재구축(txA/txB/txC)과 저장은 바깥에 합류하지
     * 않고 따로 커밋·롤백해야 한다.
     */
    public static SpringTenantTransactions requiresNew(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new SpringTenantTransactions(template);
    }

    @Override
    public <T> T inTransaction(TenantId tenant, Supplier<T> work) {
        try {
            return TenantContext.runWith(tenant, () -> template.execute(status -> work.get()));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
