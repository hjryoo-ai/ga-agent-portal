package com.ga.portal.recon.engine;

import com.ga.portal.recon.port.ServiceToken;
import com.ga.portal.recon.rule.EngineAnswer;
import com.ga.portal.recon.rule.InconclusiveReason;
import com.ga.portal.recon.rule.ReconMode;
import com.ga.portal.recon.testing.StubStatementEngine;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/** 계획 ④: 실제 HTTP로 스텁(엔진 샘플 사본)을 부른다 — 경로·모드·토큰 머리, 샘플 밖 500, 다운·느림·깨짐, 닿지 못함. 재시도 0(호출 수). */
class HttpStatementClientTest {

    private static final Path DIR = StubStatementEngine.samplesDir(Path.of(System.getProperty("ga.repoRoot")));
    private static final ServiceToken TOKEN = new ServiceToken("stub-token");

    private HttpServer server;
    private StubStatementEngine stub;
    private volatile String authorization;
    private HttpStatementClient client;

    @BeforeEach
    void start() throws IOException {
        stub = StubStatementEngine.of(DIR, "a1");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(StubStatementEngine.PATH_PREFIX, exchange -> {
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            stub.handle(exchange);
        });
        server.setExecutor(Executors.newCachedThreadPool());   // 느린 요청이 다음 요청을 막지 않게(시간 초과 뒤에도 처리기는 자고 있다)
        server.start();
        client = new HttpStatementClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), Duration.ofMillis(500));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private URI base() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @Test
    void theSamplesComeBackThroughHttp() {
        assertThat(client.fetch(base(), TOKEN, "A-1001", "202608", ReconMode.CONFIRMED)).isEqualTo(new EngineAnswer.Confirmed(1_800_000, 0, OptionalLong.of(0)));
        assertThat(client.fetch(base(), TOKEN, "A-1001", "202702", ReconMode.ESTIMATE)).isEqualTo(new EngineAnswer.Estimated(10, 0, -720_000, 0));
        assertThat(client.fetch(base(), TOKEN, "A-9999-NONE", "202608", ReconMode.CONFIRMED)).isEqualTo(new EngineAnswer.Failed(InconclusiveReason.AGENT_NOT_FOUND));
        assertThat(authorization).isEqualTo("Bearer stub-token");
        assertThat(stub.received()).containsExactly(new StubStatementEngine.Request("A-1001", "202608", "CONFIRMED"),
                new StubStatementEngine.Request("A-1001", "202702", "ESTIMATE"), new StubStatementEngine.Request("A-9999-NONE", "202608", "CONFIRMED"));
    }

    @Test
    void aRequestOutsideTheSamplesIs500AndUnavailable() {
        assertThat(client.fetch(base(), TOKEN, "A-1001", "202609", ReconMode.CONFIRMED)).isEqualTo(EngineAnswer.unavailable());
        assertThat(stub.calls()).isEqualTo(1);
    }

    @Test
    void downSlowAndBrokenEnginesAreInconclusiveWithoutRetry() {
        stub.mode(StubStatementEngine.Mode.DOWN);
        assertThat(client.fetch(base(), TOKEN, "A-1001", "202608", ReconMode.CONFIRMED)).isEqualTo(EngineAnswer.unavailable());
        stub.mode(StubStatementEngine.Mode.SLOW).slowMillis(1_500);
        assertThat(client.fetch(base(), TOKEN, "A-1001", "202608", ReconMode.CONFIRMED)).as("read timeout").isEqualTo(EngineAnswer.unavailable());
        stub.mode(StubStatementEngine.Mode.BROKEN);
        assertThat(client.fetch(base(), TOKEN, "A-1001", "202608", ReconMode.CONFIRMED)).isEqualTo(EngineAnswer.contractViolation());
        assertThat(stub.calls()).as("one call each — no retry").isEqualTo(3);
    }

    @Test
    void anUnreachableEngineIsUnavailable() {
        int port = server.getAddress().getPort();
        server.stop(0);
        assertThat(client.fetch(URI.create("http://127.0.0.1:" + port), TOKEN, "A-1001", "202608", ReconMode.CONFIRMED)).isEqualTo(EngineAnswer.unavailable());
    }

    @Test
    void theTokenNeverPrints() {
        assertThat(TOKEN.toString()).doesNotContain("stub-token");
    }
}
