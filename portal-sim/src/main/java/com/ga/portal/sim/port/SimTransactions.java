package com.ga.portal.sim.port;

import java.util.function.Supplier;

/** 바인딩된 테넌트의 읽기·쓰기 트랜잭션 하나(포털 Phase 5 ⑤ — 호출 전 A, 호출 뒤 B). 일이 예외를 던지면 롤백한다. */
public interface SimTransactions {

    <T> T write(Supplier<T> work);
}
