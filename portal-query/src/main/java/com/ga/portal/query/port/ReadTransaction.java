package com.ga.portal.query.port;

import java.util.function.Supplier;

/** 바인딩된 테넌트의 읽기 전용 트랜잭션 하나(요청 하나의 조회가 같은 스냅샷을 본다). */
public interface ReadTransaction {

    <T> T read(Supplier<T> work);
}
