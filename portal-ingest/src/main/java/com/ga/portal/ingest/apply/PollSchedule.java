package com.ga.portal.ingest.apply;

import com.ga.platform.core.tenant.TenantId;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 테넌트별 폴링 주기(Phase 1 지시문 D-10): {@code tenant.params.poll_interval_seconds}(5~30), 없거나 범위 밖이면 전역 기본값. 실패 뒤
 * 백오프는 없다 — 멈춘 테넌트는 워커가 건너뛰므로 주기와 무관하다. 기본 키 집합(§14.0 셋)에 이 키를 더하지 않는다(D-11).
 */
public final class PollSchedule {

    public static final String PARAM = "poll_interval_seconds";
    public static final int MIN_SECONDS = 5;
    public static final int MAX_SECONDS = 30;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Duration defaultInterval;
    private final Map<TenantId, Instant> nextRun = new ConcurrentHashMap<>();

    public PollSchedule(Duration defaultInterval) {
        long s = defaultInterval.toSeconds();
        if (s < MIN_SECONDS || s > MAX_SECONDS) {
            throw new IllegalArgumentException("default poll interval must be " + MIN_SECONDS + ".." + MAX_SECONDS + "s: " + defaultInterval);
        }
        this.defaultInterval = defaultInterval;
    }

    /**
     * 테넌트 params(JSON 텍스트)의 주기. 키가 없으면 기본값, 정수가 아니거나 범위 밖이면 기본값과 함께 그 사실을 돌려준다(호출자가 경고).
     */
    public Interval intervalOf(String paramsJson) {
        JsonNode value = JSON.readTree(paramsJson == null ? "{}" : paramsJson).path(PARAM);
        if (value.isMissingNode() || value.isNull()) {
            return new Interval(defaultInterval, Optional.empty());
        }
        if (!value.isIntegralNumber() || value.asLong() < MIN_SECONDS || value.asLong() > MAX_SECONDS) {
            return new Interval(defaultInterval, Optional.of(PARAM + " must be an integer " + MIN_SECONDS + ".." + MAX_SECONDS));
        }
        return new Interval(Duration.ofSeconds(value.asLong()), Optional.empty());
    }

    public boolean due(TenantId tenant, Instant now) {
        Instant next = nextRun.get(tenant);
        return next == null || !now.isBefore(next);
    }

    public void ranAt(TenantId tenant, Instant now, Duration interval) {
        nextRun.put(tenant, now.plus(interval));
    }

    /** @param rejected 값이 있으면 params의 값을 쓰지 않은 이유 */
    public record Interval(Duration duration, Optional<String> rejected) {
    }
}
