package com.ga.portal.query.api;

import java.time.Instant;
import java.util.Optional;

/** 응답이 반영한 피드 위치: {@code seq} = 커서 {@code last_seq}(커서가 없으면 0), {@code at} = 마지막 적용 시각({@code updated_at}). */
public record AsOf(long seq, Optional<Instant> at) {
}
