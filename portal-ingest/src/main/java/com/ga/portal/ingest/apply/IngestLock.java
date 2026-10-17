package com.ga.portal.ingest.apply;

/**
 * 테넌트당 워커 하나(지시문 D-5): 열린 트랜잭션 안에서 바인딩된 테넌트의 잠금을 시도한다 — PostgreSQL
 * {@code pg_try_advisory_xact_lock}(커밋·롤백 때 풀린다). 못 잡으면 다른 인스턴스가 그 테넌트를 처리 중이다.
 */
public interface IngestLock {

    boolean tryLock();
}
