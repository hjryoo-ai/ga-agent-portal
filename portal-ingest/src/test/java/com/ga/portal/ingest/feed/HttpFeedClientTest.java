package com.ga.portal.ingest.feed;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.IngestErrorCode;
import com.ga.portal.ingest.testing.FeedClientContract;
import com.ga.portal.ingest.testing.StubFeedServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C12: HTTP 클라이언트가 피드 계약 스위트(F1~F8)를 통과하고, HTTP만의 H1~H3 — 서비스 토큰 헤더·401·토큰 없음, 5xx·연결 실패·시간 초과는
 * 일시적 실패, 어떤 메시지에도 토큰 값이 없다. 엔진 없이 스텁 서버(계약 응답)로 시험한다(지시문 D-7).
 */
class HttpFeedClientTest extends FeedClientContract {

    /** 허구 토큰 — 평문 스캔 센티널이기도 하다(J10). */
    static final String TOKEN = "fictional-svc-token-7Qx2";

    private final List<StubFeedServer> servers = new ArrayList<>();

    @AfterEach
    void stop() {
        servers.forEach(StubFeedServer::close);
    }

    private StubFeedServer server(String respondedTenant) {
        StubFeedServer s = new StubFeedServer(respondedTenant, TOKEN);
        servers.add(s);
        return s;
    }

    static HttpFeedClient over(URI base, Optional<String> token, Duration timeout) {
        return new HttpFeedClient(HttpClient.newHttpClient(), t -> base,
                t -> token.map(ServiceTokenSource.ServiceToken::new), timeout);
    }

    @Override
    protected FeedClient client(String respondedTenant, List<? extends JsonNode> events) {
        return over(server(respondedTenant).events(events).baseUrl(), Optional.of(TOKEN), Duration.ofSeconds(5));
    }

    @Override
    protected FeedClient malformedClient() {
        StubFeedServer s = server("GA01").rawBody("{\"events\":[],\"nextSeq\":0,\"headSeq\":0,\"schemaVersion\":1}");
        return over(s.baseUrl(), Optional.of(TOKEN), Duration.ofSeconds(5));
    }

    @Test
    void h1SendsTheTokenAsBearerAndMaps401AndAMissingToken() {
        StubFeedServer s = server("GA01").events(seqs(1));
        over(s.baseUrl(), Optional.of(TOKEN), Duration.ofSeconds(5)).fetch(T, 0);
        assertThat(s.authorizations()).containsExactly("Bearer " + TOKEN);

        assertThatThrownBy(() -> over(s.baseUrl(), Optional.of("wrong-token"), Duration.ofSeconds(5)).fetch(T, 0))
                .isInstanceOfSatisfying(FeedException.Unauthorized.class,
                        e -> assertThat(e.stopsWith()).contains(IngestErrorCode.FEED_UNAUTHORIZED));

        int before = s.requests();
        assertThatThrownBy(() -> over(s.baseUrl(), Optional.empty(), Duration.ofSeconds(5)).fetch(T, 0))
                .isInstanceOfSatisfying(FeedException.TokenMissing.class,
                        e -> assertThat(e.stopsWith()).contains(IngestErrorCode.TOKEN_MISSING));
        assertThat(s.requests()).as("no request without a token").isEqualTo(before);
    }

    @Test
    void h2ServerErrorsUnreachableAndTimeoutsAreTransient() throws Exception {
        StubFeedServer s = server("GA01").events(seqs(1)).forceStatus(503);
        assertThatThrownBy(() -> over(s.baseUrl(), Optional.of(TOKEN), Duration.ofSeconds(5)).fetch(T, 0))
                .isInstanceOfSatisfying(FeedException.Unavailable.class, e -> assertThat(e.stopsWith()).isEmpty())
                .hasMessageContaining("503");

        int port;
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }
        URI closed = URI.create("http://127.0.0.1:" + port);
        assertThatThrownBy(() -> over(closed, Optional.of(TOKEN), Duration.ofSeconds(2)).fetch(T, 0))
                .isInstanceOf(FeedException.Unavailable.class);

        StubFeedServer slow = server("GA01").events(seqs(1)).delay(1_500);
        assertThatThrownBy(() -> over(slow.baseUrl(), Optional.of(TOKEN), Duration.ofMillis(200)).fetch(T, 0))
                .isInstanceOf(FeedException.Unavailable.class);

        StubFeedServer other422 = server("GA01").forceStatus(422);
        assertThatThrownBy(() -> over(other422.baseUrl(), Optional.of(TOKEN), Duration.ofSeconds(5)).fetch(T, 0))
                .isInstanceOf(FeedException.Unavailable.class);
    }

    @Test
    void h3NoMessageCarriesTheToken() {
        StubFeedServer s = server("GA02").events(seqs(1));
        List<Throwable> failures = new ArrayList<>();
        for (Runnable call : List.<Runnable>of(
                () -> over(s.baseUrl(), Optional.of(TOKEN), Duration.ofSeconds(5)).fetch(T, 0),
                () -> over(s.baseUrl(), Optional.of(TOKEN), Duration.ofSeconds(5)).fetch(T, 99),
                () -> over(server("GA01").forceStatus(500).baseUrl(), Optional.of(TOKEN), Duration.ofSeconds(5)).fetch(T, 0),
                () -> over(s.baseUrl(), Optional.of(TOKEN + "x"), Duration.ofSeconds(5)).fetch(T, 0))) {
            try {
                call.run();
            } catch (FeedException e) {
                failures.add(e);
            }
        }
        assertThat(failures).hasSize(4).allSatisfy(e -> assertThat(String.valueOf(e.getMessage())).doesNotContain(TOKEN));
        assertThat(new ServiceTokenSource.ServiceToken(TOKEN).toString()).doesNotContain(TOKEN);
        assertThat(TenantId.of("GA01").value()).isEqualTo("GA01");
    }
}
