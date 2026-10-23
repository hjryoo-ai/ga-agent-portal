package com.ga.portal.sim.port;

import java.time.Instant;
import java.util.Optional;

/** 응답의 포털 사실 — 조회 API와 같은 {@code asOf}·{@code dataStatus}(커서·동기화 상태, 설계서 §7). 쓰기 트랜잭션 A 안에서 읽는다. */
public interface SimFacts {

    Facts current();

    record Facts(long seq, Optional<Instant> at, String dataStatus) {
    }
}
