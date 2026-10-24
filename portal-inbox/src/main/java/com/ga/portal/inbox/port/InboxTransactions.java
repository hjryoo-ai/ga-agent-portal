package com.ga.portal.inbox.port;

import java.util.function.Supplier;

/** 바인딩된 테넌트의 트랜잭션 하나 — 읽기 전용 또는 읽기·쓰기. 일이 예외를 던지면 롤백한다. */
public interface InboxTransactions {

    <T> T read(Supplier<T> work);

    <T> T write(Supplier<T> work);
}
