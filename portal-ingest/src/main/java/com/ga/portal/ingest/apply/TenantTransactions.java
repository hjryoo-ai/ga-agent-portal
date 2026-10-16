package com.ga.portal.ingest.apply;

import com.ga.platform.core.tenant.TenantId;

import java.util.function.Supplier;

/**
 * 테넌트 트랜잭션 포트: {@code TenantContext.runWith(tenant)} 안에서 트랜잭션 하나를 열고(RLS 세션 값은 {@code TenantSessionBinder}가 넣는다)
 * {@code work}가 정상 반환하면 커밋, 예외면 롤백 뒤 그 예외를 다시 던진다(설계서 §6.1 — 이벤트마다 트랜잭션 하나).
 */
public interface TenantTransactions {

    <T> T inTransaction(TenantId tenant, Supplier<T> work);
}
