package com.ga.portal.recon.engine;

import com.ga.portal.recon.rule.EngineAnswer;
import com.ga.portal.recon.rule.InconclusiveReason;
import com.ga.portal.recon.rule.ReconMode;
import com.ga.portal.recon.testing.StubStatementEngine;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계획 ④·C1·C7: 엔진 명세 샘플 사본 전부가 기대한 답으로 읽히고(200 → 모양, 오류 → 계약의 사유), 계약을 어긴 200은 {@code ENGINE_CONTRACT}다
 * (스키마·모드·요청과 다른 설계사·달·정수 아닌 표기).
 */
class StatementResponsesTest {

    private static final Path DIR = StubStatementEngine.samplesDir(Path.of(System.getProperty("ga.repoRoot")));

    private static EngineAnswer read(StubStatementEngine.Sample s) {
        return StatementResponses.read(s.agentId(), s.ym(), ReconMode.valueOf(s.mode()), s.status(), s.response());
    }

    private static StubStatementEngine.Sample sample(String state, String name) {
        return StubStatementEngine.load(DIR, state).stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    private static EngineAnswer body(ReconMode mode, int status, String json) {
        return StatementResponses.read("A-1001", "202608", mode, status, json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void everySampleReadsAsTheEngineMeantIt() {
        assertThat(read(sample("a1", "202608-confirmed"))).isEqualTo(new EngineAnswer.Confirmed(1_800_000, 0, OptionalLong.of(0)));
        assertThat(read(sample("a1", "202702-estimate"))).isEqualTo(new EngineAnswer.Estimated(10, 0, -720_000, 0));
        assertThat(read(sample("a1", "202702-confirmed-not-confirmed"))).isEqualTo(new EngineAnswer.Failed(InconclusiveReason.NOT_CONFIRMED));
        assertThat(read(sample("a1", "202608-estimate-month-closed"))).isEqualTo(new EngineAnswer.Failed(InconclusiveReason.MONTH_CLOSED));
        assertThat(read(sample("a1", "unknown-agent"))).isEqualTo(new EngineAnswer.Failed(InconclusiveReason.AGENT_NOT_FOUND));
        assertThat(read(sample("estimate-cycle-01", "202608-estimate"))).isEqualTo(new EngineAnswer.Estimated(3, 1_890_000, 0, 0));
        assertThat(read(sample("estimate-cycle-01", "202608-confirmed"))).isEqualTo(new EngineAnswer.Confirmed(1_890_000, 0, OptionalLong.of(0)));
        assertThat(read(sample("estimate-cycle-05", "202801-estimate"))).isEqualTo(new EngineAnswer.Estimated(6, 378_000, 0, 0));
        int all = 0;
        for (String state : List.of("a1", "estimate-cycle-01", "estimate-cycle-05")) {
            for (StubStatementEngine.Sample s : StubStatementEngine.load(DIR, state)) {
                assertThat(read(s)).as(s.name()).isNotEqualTo(EngineAnswer.contractViolation());
                all++;
            }
        }
        assertThat(all).isEqualTo(8);
    }

    @Test
    void aContractBreaking200IsEngineContract() {
        String confirmed = "{\"agentId\":\"A-1001\",\"closeYm\":\"202608\",\"gross\":%s,\"clawbackOffset\":0,\"adjustment\":0,"
                + "\"closedAt\":\"2026-09-05T18:00:00+09:00\"}";
        assertThat(body(ReconMode.CONFIRMED, 200, confirmed.formatted("1800000"))).isEqualTo(new EngineAnswer.Confirmed(1_800_000, 0, OptionalLong.of(0)));
        assertThat(body(ReconMode.CONFIRMED, 200, confirmed.formatted("1800000.0"))).as("not an integer token").isEqualTo(EngineAnswer.contractViolation());
        assertThat(body(ReconMode.CONFIRMED, 200, confirmed.formatted("\"1800000\""))).isEqualTo(EngineAnswer.contractViolation());
        assertThat(body(ReconMode.CONFIRMED, 200, confirmed.formatted("99999999999999999999"))).as("beyond long").isEqualTo(EngineAnswer.contractViolation());
        assertThat(body(ReconMode.CONFIRMED, 200, confirmed.formatted("1").replace("A-1001", "A-2002"))).as("another agent").isEqualTo(EngineAnswer.contractViolation());
        assertThat(body(ReconMode.CONFIRMED, 200, confirmed.formatted("1").replace("202608", "202609"))).as("another month").isEqualTo(EngineAnswer.contractViolation());
        assertThat(body(ReconMode.ESTIMATE, 200, confirmed.formatted("1"))).as("CONFIRMED shape for ESTIMATE").isEqualTo(EngineAnswer.contractViolation());
        assertThat(body(ReconMode.CONFIRMED, 200, "not json")).isEqualTo(EngineAnswer.contractViolation());
        assertThat(StatementResponses.read("A-1001", "202608", ReconMode.CONFIRMED, 200, new byte[0])).isEqualTo(EngineAnswer.contractViolation());
        String noOffset = "{\"agentId\":\"A-1001\",\"closeYm\":\"202608\",\"gross\":1,\"adjustment\":0,\"closedAt\":\"2026-09-05T18:00:00+09:00\"}";
        assertThat(body(ReconMode.CONFIRMED, 200, noOffset)).as("clawbackOffset is optional").isEqualTo(new EngineAnswer.Confirmed(1, 0, OptionalLong.empty()));
    }

    @Test
    void errorBodiesGiveOnlyTheirCode() {
        assertThat(body(ReconMode.CONFIRMED, 404, "{\"code\":\"AGENT_NOT_FOUND\",\"message\":\"x\"}")).isEqualTo(new EngineAnswer.Failed(InconclusiveReason.AGENT_NOT_FOUND));
        assertThat(body(ReconMode.CONFIRMED, 404, "{\"message\":\"AGENT_NOT_FOUND\"}")).as("no code — the message is never read")
                .isEqualTo(new EngineAnswer.Failed(InconclusiveReason.ENGINE_REJECTED));
        assertThat(body(ReconMode.CONFIRMED, 404, "<html>")).isEqualTo(new EngineAnswer.Failed(InconclusiveReason.ENGINE_REJECTED));
        assertThat(body(ReconMode.CONFIRMED, 503, "")).isEqualTo(new EngineAnswer.Failed(InconclusiveReason.ENGINE_UNAVAILABLE));
        assertThat(body(ReconMode.CONFIRMED, 401, "{\"code\":\"UNAUTHORIZED\",\"message\":\"x\"}")).isEqualTo(new EngineAnswer.Failed(InconclusiveReason.ENGINE_UNAVAILABLE));
        assertThat(body(ReconMode.CONFIRMED, 400, "{\"code\":\"INVALID_REQUEST\",\"message\":\"x\"}")).isEqualTo(new EngineAnswer.Failed(InconclusiveReason.ENGINE_REJECTED));
    }
}
