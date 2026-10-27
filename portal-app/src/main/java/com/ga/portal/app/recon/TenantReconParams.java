package com.ga.portal.app.recon;

import com.ga.portal.recon.ReconParams;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

/**
 * {@code tenant.params}의 대사 키 → {@link ReconParams}(계획 ⑥). 키가 없거나 형식·범위 밖이면 앱 기본({@code portal.recon.defaults}) — 시각은 0~23,
 * 불리언은 JSON 불리언, 수는 0 이상 정수만.
 */
final class TenantReconParams {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private TenantReconParams() {
    }

    static ReconParams of(String paramsJson, ReconProperties.Defaults d) {
        JsonNode p;
        try {
            p = JSON.readTree(paramsJson == null ? "{}" : paramsJson);
        } catch (JacksonException e) {
            p = JSON.createObjectNode();
        }
        return new ReconParams(
                (int) number(p, "recon_hour_kst", d.reconHourKst(), 23),
                p.path("auto_rebuild_enabled").isBoolean() ? p.path("auto_rebuild_enabled").asBoolean() : d.autoRebuildEnabled(),
                Duration.ofSeconds(number(p, "recon_caught_up_within_seconds", d.caughtUpWithinSeconds(), 86_400)),
                Duration.ofMillis(number(p, "recon_call_interval_ms", d.callIntervalMs(), 60_000)),
                (int) number(p, "recon_max_calls", d.maxCalls(), 100_000),
                Duration.ofSeconds(number(p, "recon_max_seconds", d.maxSeconds(), 86_400)));
    }

    private static long number(JsonNode p, String key, long fallback, long max) {
        JsonNode v = p.path(key);
        return v.isIntegralNumber() && v.canConvertToLong() && v.asLong() >= 0 && v.asLong() <= max ? v.asLong() : fallback;
    }
}
