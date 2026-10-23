package com.ga.portal.app.web;

import com.ga.portal.sim.testing.StubSimulationEngine;
import com.ga.portal.sim.testing.StubSimulationEngine.Sample;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C6(포털 Phase 5 ②): 요청 하나에 {@code simulation_log} 행 하나 — 성공이면 {@code SUCCEEDED}(엔진 요청 = 엔진 샘플 요청의 정규형, 응답 = 엔진 응답,
 * 룰 스냅샷·상태 200), 엔진이 내려가 있어도 {@code REQUESTED} → {@code FAILED}(사유·상태, 본문 없음). 응답의 {@code simId}·{@code requestedAt}은 그 행의 키·시각.
 */
class SimulationLogIT extends SimIntegrationTest {

    @Test
    void aSuccessIsOneSucceededRowWithTheEngineExchange() {
        Sample s1 = StubSimulationEngine.load(SAMPLES).stream().filter(s -> s.name().equals("new-limit-exceeded")).findFirst().orElseThrow();
        int before = rows(STD).size();
        HttpResponse<byte[]> r = post(NEW, std(SUB_A1), S1);
        assertThat(r.statusCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(r.body());
        assertThat(rows(STD)).hasSize(before + 1);
        SimRow row = row(STD, body.path("simId").asString());
        assertThat(row.status()).isEqualTo("SUCCEEDED");
        assertThat(row.kind()).isEqualTo("NEW_CONTRACT");
        assertThat(row.agentId()).isEqualTo(AGENT_A1);
        // 저장된 엔진 요청 = 엔진 샘플 요청(JCS 정규형 — 스텁의 대조기)
        assertThat(ENGINE_STD.match("new-contract", row.request().getBytes(StandardCharsets.UTF_8))).map(Sample::name).contains(s1.name());
        assertThat(JSON.readTree(row.response())).isEqualTo(JSON.readTree(s1.response()));
        assertThat(row.ruleSnapshotId()).isEqualTo(JSON.readTree(s1.response()).path("ruleSnapshotId").asString());
        assertThat(row.engineStatus()).isEqualTo(200);
        assertThat(row.errorCode()).isNull();
        assertThat(body.path("requestedAt").asString()).isEqualTo(row.createdAt().toString());
    }

    @Test
    void anEngineOutageStillLeavesOneFailedRowWithoutABody() {
        int before = rows(STD).size();
        ENGINE_STD.mode(StubSimulationEngine.Mode.DOWN);
        try {
            HttpResponse<byte[]> r = post(NEW, std(SUB_A1), S1);
            assertThat(r.statusCode()).isEqualTo(503);
        } finally {
            ENGINE_STD.mode(StubSimulationEngine.Mode.UP);
        }
        assertThat(rows(STD)).hasSize(before + 1);
        SimRow row = rows(STD).getLast();
        assertThat(row.status()).isEqualTo("FAILED");
        assertThat(row.errorCode()).isEqualTo("ENGINE_UNAVAILABLE");
        assertThat(row.engineStatus()).isEqualTo(503);
        assertThat(row.response()).isNull();
        assertThat(row.ruleSnapshotId()).isNull();
        assertThat(rows(STD)).noneMatch(x -> x.status().equals("REQUESTED") && !x.agentId().equals("A0001"));
    }
}
