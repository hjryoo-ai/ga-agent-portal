package com.ga.portal.demo;

import com.ga.portal.ingest.feed.FeedClient;
import com.ga.portal.ingest.feed.HttpFeedClient;
import com.ga.portal.ingest.feed.ServiceTokenSource;
import com.ga.portal.ingest.testing.FeedClientContract;
import com.ga.portal.ingest.testing.StubFeedServer;
import org.junit.jupiter.api.AfterEach;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 계획 P-5: 데모 서버는 하네스 스텁을 제품화하지 않은 같은 계약의 구현이다 — 포털의 실제 HTTP 클라이언트({@link HttpFeedClient})가 피드 계약 스위트
 * F1~F8을 데모 서버 위에서 통과한다(포털 클라이언트와 데모 서버의 계약 시험). 형식이 틀린 응답(F7)은 데모 서버가 낼 일이 없으므로 하네스 스텁의
 * 원문 응답으로 클라이언트를 본다.
 */
class DemoServerFeedContractTest extends FeedClientContract {

    static final String TOKEN = "fictional-demo-token-3Kp9";

    private final List<AutoCloseable> servers = new ArrayList<>();

    @AfterEach
    void stop() throws Exception {
        for (AutoCloseable s : servers) {
            s.close();
        }
    }

    static HttpFeedClient over(URI base) {
        return new HttpFeedClient(HttpClient.newHttpClient(), t -> base, t -> Optional.of(new ServiceTokenSource.ServiceToken(TOKEN)),
                Duration.ofSeconds(5));
    }

    @Override
    protected FeedClient client(String respondedTenant, List<? extends JsonNode> events) {
        try {
            DemoServer s = new DemoServer(new DemoData(respondedTenant, List.copyOf(events), Map.of(), List.of()), TOKEN,
                    Replay.instant(events.size()), new InetSocketAddress("127.0.0.1", 0));
            servers.add(s);
            return over(s.baseUrl());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected FeedClient malformedClient() {
        StubFeedServer s = new StubFeedServer("GA01", TOKEN).rawBody("{\"events\":[],\"nextSeq\":0,\"headSeq\":0,\"schemaVersion\":1}");
        servers.add(s);
        return over(s.baseUrl());
    }
}
