package com.ga.portal.sim;

import com.ga.portal.domain.SimulationKind;
import com.ga.portal.sim.EngineOutcomes.Failure;
import com.ga.portal.sim.EngineOutcomes.Outcome;
import com.ga.portal.sim.EngineOutcomes.Success;
import com.ga.portal.sim.EngineSimulationClient.EngineReply;
import com.ga.portal.sim.testing.StubSimulationEngine;
import com.ga.portal.sim.testing.StubSimulationEngine.Sample;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** C7(계획 ⑥ 표·Q1): 엔진의 답 → 포털 결과. 데이터는 엔진 샘플 사본(성공 8·오류 5)과 합성 실패. */
class EngineOutcomesTest {

    static final List<Sample> SAMPLES = StubSimulationEngine.load(StubSimulationEngine.samplesDir(Path.of(System.getProperty("ga.repoRoot"))));

    static SimulationKind kind(Sample s) {
        return s.path().equals("new-contract") ? SimulationKind.NEW_CONTRACT : SimulationKind.POLICY_EVENT;
    }

    static Outcome answered(SimulationKind kind, int status, String body) {
        return EngineOutcomes.classify(kind, new EngineReply.Answered(status, body.getBytes(StandardCharsets.UTF_8)));
    }

    static Failure failure(Outcome o) {
        assertThat(o).isInstanceOf(Failure.class);
        return (Failure) o;
    }

    @Test
    void everySuccessfulSamplePassesTheCopiedSchema() {
        List<Sample> ok = SAMPLES.stream().filter(s -> s.status() == 200).toList();
        assertThat(ok).hasSize(8);
        for (Sample s : ok) {
            Outcome o = EngineOutcomes.classify(kind(s), new EngineReply.Answered(200, s.response()));
            assertThat(o).as(s.name()).isInstanceOf(Success.class);
            assertThat(((Success) o).ruleSnapshotId()).as(s.name()).startsWith("RS-");
        }
    }

    @Test
    void errorSamplesMapToTheTable() {
        Failure ambiguous = failure(sampleOutcome("new-ambiguous"));
        assertThat(ambiguous.code()).isEqualTo(SimulationFailed.Code.INSURER_AMBIGUOUS);
        assertThat(ambiguous.engineStatus()).contains(422);
        Failure unsupported = failure(sampleOutcome("event-unsupported"));
        assertThat(unsupported.code()).isEqualTo(SimulationFailed.Code.UNSUPPORTED_EVENT_TYPE);
        // 엔진 404·400은 설계사 잘못이 아니다 — 502, 로그에는 엔진 코드
        Failure policy = failure(sampleOutcome("event-unknown-policy"));
        assertThat(policy.code()).isEqualTo(SimulationFailed.Code.ENGINE_CONTRACT);
        assertThat(policy.logCode()).isEqualTo("POLICY_NOT_FOUND");
        Failure agent = failure(sampleOutcome("new-unknown-agent"));
        assertThat(agent.code()).isEqualTo(SimulationFailed.Code.ENGINE_CONTRACT);
        assertThat(agent.logCode()).isEqualTo("AGENT_NOT_FOUND");
        Failure invalid = failure(sampleOutcome("event-reduce-no-premium"));
        assertThat(invalid.code()).isEqualTo(SimulationFailed.Code.ENGINE_CONTRACT);
        assertThat(invalid.logCode()).isEqualTo("INVALID_REQUEST");
        assertThat(invalid.engineStatus()).contains(400);
    }

    static Outcome sampleOutcome(String name) {
        Sample s = SAMPLES.stream().filter(x -> x.name().equals(name)).findFirst().orElseThrow();
        return EngineOutcomes.classify(kind(s), new EngineReply.Answered(s.status(), s.response()));
    }

    @Test
    void unavailableContractAndRejectedCases() {
        SimulationKind k = SimulationKind.NEW_CONTRACT;
        for (int status : new int[] {401, 403, 500, 502, 503, 504}) {
            assertThat(failure(answered(k, status, "")).code()).as("%d", status).isEqualTo(SimulationFailed.Code.ENGINE_UNAVAILABLE);
        }
        assertThat(failure(EngineOutcomes.classify(k, new EngineReply.Unreachable("ENGINE_TIMEOUT"))))
                .extracting(Failure::code, Failure::logCode, Failure::engineStatus)
                .containsExactly(SimulationFailed.Code.ENGINE_UNAVAILABLE, "ENGINE_TIMEOUT", Optional.empty());
        assertThat(failure(EngineOutcomes.tokenMissing()).logCode()).isEqualTo("TOKEN_MISSING");
        // 200이지만 계약과 다르다
        assertThat(failure(answered(k, 200, "{\"ruleSnapshotId\":\"RS-broken\"}")).code()).isEqualTo(SimulationFailed.Code.ENGINE_CONTRACT);
        assertThat(failure(answered(k, 200, "not json")).logCode()).isEqualTo("ENGINE_CONTRACT");
        String ok = new String(SAMPLES.getFirst().response(), StandardCharsets.UTF_8);
        assertThat(failure(answered(k, 200, ok.replace("\"limitAmount\":1800000", "\"limitAmount\":1800000.0"))).code())
                .as("an integral float is still not an integer literal").isEqualTo(SimulationFailed.Code.ENGINE_CONTRACT);
        assertThat(failure(answered(k, 200, ok.replace("{\"limitAmount\"", "{\"limitAmount\":1,\"limitAmount\""))).code())
                .as("duplicate key").isEqualTo(SimulationFailed.Code.ENGINE_CONTRACT);
        assertThat(failure(answered(k, 302, "")).code()).isEqualTo(SimulationFailed.Code.ENGINE_CONTRACT);
        // 422 닫힌 목록 밖 → ENGINE_REJECTED(로그에는 형식이 맞는 엔진 코드)
        Failure other = failure(answered(k, 422, "{\"code\":\"MONTH_CLOSED\",\"message\":\"x\"}"));
        assertThat(other.code()).isEqualTo(SimulationFailed.Code.ENGINE_REJECTED);
        assertThat(other.logCode()).isEqualTo("MONTH_CLOSED");
        assertThat(failure(answered(k, 422, "{\"code\":\"bad code\"}")).logCode()).isEqualTo("ENGINE_REJECTED");
        // 성공 본문을 다른 경로의 스키마로는 받지 않는다
        assertThat(failure(answered(SimulationKind.POLICY_EVENT, 200, ok)).code()).isEqualTo(SimulationFailed.Code.ENGINE_CONTRACT);
    }
}
