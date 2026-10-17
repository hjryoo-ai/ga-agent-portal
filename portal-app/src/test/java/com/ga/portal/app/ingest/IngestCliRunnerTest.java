package com.ga.portal.app.ingest;

import org.junit.jupiter.api.Test;

import java.io.PrintStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 운영 명령 인자: 사용법(64)과 행위자 검사(2)는 DB에 닿기 전에 끝난다. */
class IngestCliRunnerTest {

    private final IngestCliRunner runner = new IngestCliRunner(null, new PrintStream(OutputStream.nullOutputStream()), "");

    @Test
    void usageErrorsExitWith64() {
        assertThat(runner.execute(List.of(), Optional.empty(), Optional.empty(), Optional.of("ops")).exitCode()).isEqualTo(64);
        assertThat(runner.execute(List.of("ingest", "rewind"), Optional.empty(), Optional.empty(), Optional.of("ops")).exitCode()).isEqualTo(64);
        assertThat(runner.execute(List.of("ingest", "resume"), Optional.empty(), Optional.empty(), Optional.of("ops")).lines())
                .contains("invalid or missing --tenant");
        assertThat(runner.execute(List.of("ingest", "resume"), Optional.of("GA01"), Optional.of("x"), Optional.of("ops")).exitCode())
                .isEqualTo(64);
        assertThat(runner.execute(List.of("ingest", "pause"), Optional.of("ga01"), Optional.empty(), Optional.of("ops")).exitCode())
                .as("tenant id format").isEqualTo(64);
    }

    @Test
    void anActorIsRequired() {
        assertThat(runner.execute(List.of("ingest", "status"), Optional.empty(), Optional.empty(), Optional.empty()).exitCode()).isEqualTo(2);
        assertThat(runner.execute(List.of("ingest", "status"), Optional.empty(), Optional.empty(), Optional.of("o p")).exitCode()).isEqualTo(2);
        assertThat(IngestCommands.actorSubject("ops-kim")).contains("cli:ops-kim");
        assertThat(IngestCommands.actorSubject("x".repeat(65))).isEmpty();
        assertThat(IngestCommands.actorSubject("")).isEmpty();
    }
}
