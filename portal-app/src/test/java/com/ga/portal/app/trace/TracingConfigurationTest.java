package com.ga.portal.app.trace;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 포털 Phase 8(P-21): 프레임워크 관측의 높은 기수 키(요청 경로 원문 {@code http.url} 등)는 스팬에 실리지 않고, 낮은 기수 키는 그대로다. */
class TracingConfigurationTest {

    @Test
    void highCardinalityKeyValuesAreDroppedAndLowCardinalityOnesKept() {
        Observation.Context context = new Observation.Context();
        context.addHighCardinalityKeyValue(KeyValue.of("http.url", "/api/v1/me/inquiries/00000000-0000-4000-8000-000000000601"));
        context.addHighCardinalityKeyValue(KeyValue.of("client.address", "127.0.0.1"));
        context.addLowCardinalityKeyValue(KeyValue.of("uri", "/api/v1/me/inquiries/{id}"));
        context.addLowCardinalityKeyValue(KeyValue.of("status", "200"));

        Observation.Context filtered = TracingConfiguration.dropHighCardinality(context);

        assertThat(filtered.getHighCardinalityKeyValues()).isEmpty();
        assertThat(filtered.getLowCardinalityKeyValues().stream().map(KeyValue::getKey)).containsExactlyInAnyOrder("uri", "status");
    }
}
