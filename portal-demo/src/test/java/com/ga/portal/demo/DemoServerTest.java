package com.ga.portal.demo;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 계획 P-5·P-24, 지시문 ②ⓕ: 데모 서버가 엔진 데모 묶음(포털 사본)의 바이트로만 답한다 — 명세·시뮬레이션은 샘플에 있는 요청만(키 순서는 보지 않는다),
 * 밖은 500 {@code NO_SAMPLE}(주입 S4), 토큰이 틀리면 401, 재생 속도 {@code paced}는 틱마다 덧셈으로만 머리를 올린다.
 */
class DemoServerTest {

    static final String TOKEN = "fictional-demo-token-8Wd1";
    static final Path BUNDLE = Path.of(System.getProperty("ga.repoRoot"), "contracts/shared/ga-commission-engine/samples/demo");

    private static DemoData data;
    private static DemoServer server;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll
    static void start() throws IOException {
        data = DemoData.load(BUNDLE, "DEMO");
        server = new DemoServer(data, TOKEN, Replay.instant(data.events().size()), new InetSocketAddress("127.0.0.1", 0));
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    static HttpResponse<String> get(String path, String token) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(server.baseUrl() + path)).header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> post(String path, String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(server.baseUrl() + path)).header("Authorization", "Bearer " + TOKEN)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void theBundleIsTheEnginesDemoStream() {
        assertThat(data.events()).hasSize(17_856);
        assertThat(data.events().getLast().path("seq").asLong()).isEqualTo(17_856);
        assertThat(data.statements()).hasSize(716);
        assertThat(data.simulations()).hasSize(60);
    }

    @Test
    void theFeedServesTheStreamInPagesWithTheDemoTenant() throws Exception {
        HttpResponse<String> r = get("/internal/v1/events?afterSeq=17850&limit=1000", TOKEN);
        assertThat(r.statusCode()).isEqualTo(200);
        JsonNode body = DemoData.JSON.readTree(r.body());
        assertThat(body.path("tenantId").asString()).isEqualTo("DEMO");
        assertThat(body.path("events")).hasSize(6);
        assertThat(body.path("nextSeq").asLong()).isEqualTo(17_856);
        assertThat(body.path("headSeq").asLong()).isEqualTo(17_856);
        assertThat(body.path("events").get(0)).isEqualTo(data.events().get(17_850));
        assertThat(get("/internal/v1/events?afterSeq=17857", TOKEN).statusCode()).isEqualTo(422);
        assertThat(get("/internal/v1/events?afterSeq=0&limit=1001", TOKEN).statusCode()).isEqualTo(400);
        assertThat(get("/internal/v1/events", TOKEN).statusCode()).isEqualTo(400);
    }

    @Test
    void aWrongServiceTokenIs401OnEveryPath() throws Exception {
        assertThat(get("/internal/v1/events?afterSeq=0", "other").statusCode()).isEqualTo(401);
        assertThat(get("/internal/v1/agents/A-2001/statements/202608?mode=CONFIRMED", "other").statusCode()).isEqualTo(401);
        assertThat(get("/internal/v1/events?afterSeq=0", "other").body()).doesNotContain(TOKEN);
    }

    @Test
    void statementsAnswerOnlyTheSampleRequests() throws Exception {
        HttpResponse<String> ok = get("/internal/v1/agents/A-2001/statements/202608?mode=CONFIRMED", TOKEN);
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(DemoData.JSON.readTree(ok.body())).isEqualTo(data.statement("A-2001", "202608", "CONFIRMED").orElseThrow());
        HttpResponse<String> estimate = get("/internal/v1/agents/A-2001/statements/202802?mode=ESTIMATE", TOKEN);
        assertThat(estimate.statusCode()).isEqualTo(200);
        assertThat(get("/internal/v1/agents/A-2001/statements/202802?mode=CONFIRMED", TOKEN).statusCode()).as("not a sample").isEqualTo(500);
        assertThat(get("/internal/v1/agents/A-9999/statements/202608?mode=CONFIRMED", TOKEN).body()).contains("NO_SAMPLE");
        assertThat(get("/internal/v1/agents/A-2001/statements/202608?mode=OTHER", TOKEN).statusCode()).isEqualTo(400);
    }

    @Test
    void simulationsAnswerOnlyTheSampleRequestsWhateverTheKeyOrder() throws Exception {
        DemoData.Simulation sample = data.simulations().getFirst();
        assertThat(sample.path()).isEqualTo("new-contract");
        HttpResponse<String> ok = post("/internal/v1/simulations/new-contract", sample.request().toString());
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(DemoData.JSON.readTree(ok.body())).isEqualTo(sample.response());
        String reordered = "{\"contractDate\":\"" + sample.request().path("contractDate").asString() + "\",\"monthlyPremium\":"
                + sample.request().path("monthlyPremium").asLong() + ",\"productKey\":\"WHOLE-LIFE-20Y\",\"agentId\":\""
                + sample.request().path("agentId").asString() + "\"}";
        assertThat(post("/internal/v1/simulations/new-contract", reordered).statusCode()).isEqualTo(200);
        HttpResponse<String> outside = post("/internal/v1/simulations/new-contract",
                sample.request().toString().replace("100000", "100001"));
        assertThat(outside.statusCode()).isEqualTo(500);
        assertThat(outside.body()).contains("NO_SAMPLE");
        assertThat(post("/internal/v1/simulations/other", "{}").statusCode()).isEqualTo(404);
    }

    @Test
    void pacedReplayRaisesTheHeadByAdditionOnly() {
        Replay r = Replay.paced(10, 4, 1000);
        assertThat(r.visible()).isZero();
        r.tick();
        assertThat(r.visible()).isEqualTo(4);
        r.tick();
        r.tick();
        assertThat(r.visible()).as("never beyond the stream").isEqualTo(10);
        assertThat(Replay.parse("instant", 7).visible()).isEqualTo(7);
        assertThat(Replay.parse("paced:50:1000", 7).paced()).isTrue();
        assertThatThrownBy(() -> Replay.parse("fast", 7)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Replay.paced(7, 0, 1000)).isInstanceOf(IllegalArgumentException.class);
        assertThat(List.of(r.paced(), Replay.instant(1).paced())).containsExactly(true, false);
    }
}
