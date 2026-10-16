package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantContext;
import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.ingest.apply.TenantTransactions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 테넌트 트랜잭션: {@code TenantContext.runWith(tenant)} 안에서 {@code TenantSessionBinder}(플랫폼 트랜잭션 매니저)의 트랜잭션 하나 — RLS 세션 값은
 * 바인더가 넣는다. 런타임 예외면 롤백 후 그대로 던진다. 이미 바인딩된 다른 테넌트 안에서는 부를 수 없다(플랫폼이 거부).
 */
final class SpringTenantTransactions implements TenantTransactions {

    private final TransactionTemplate template;

    SpringTenantTransactions(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
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
