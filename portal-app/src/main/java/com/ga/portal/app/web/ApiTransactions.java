package com.ga.portal.app.web;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 조회 API의 트랜잭션 둘 — 바인딩된 테넌트({@link TenantBindingFilter}) 안에서 플랫폼 {@code TenantSessionBinder}가 RLS 세션 값을 넣는다.
 * {@link #read}: 읽기 전용(링크 해석·조회). {@link #separate}: 거부 감사 — <b>새</b> 트랜잭션(본 요청 트랜잭션이 있어도 그와 무관하게 커밋된다, 지시문 D-3).
 */
final class ApiTransactions {

    private final TransactionTemplate read;
    private final TransactionTemplate separate;

    ApiTransactions(PlatformTransactionManager transactionManager) {
        this.read = new TransactionTemplate(transactionManager);
        this.read.setReadOnly(true);
        this.separate = new TransactionTemplate(transactionManager);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    <T> T read(Supplier<T> work) {
        return read.execute(status -> work.get());
    }

    void separate(Runnable work) {
        separate.executeWithoutResult(status -> work.run());
    }
}
