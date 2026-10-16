package com.ga.portal.ingest.testing;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 엔진 없이 피드 계약({@code GET /internal/v1/events})을 내는 스텁(JDK HTTP 서버). 엔진 E1 지시문 D-13과 같은 규약: seq &gt; afterSeq를
 * 받은 순서대로 최대 limit건(기본 200, 1~1000), {@code nextSeq} = 마지막 seq(없으면 afterSeq), {@code headSeq} = 최대 seq,
 * afterSeq &gt; headSeq → 422 {@code AFTER_BEYOND_HEAD}, 토큰이 틀리면 401. 시험은 응답 테넌트·강제 상태 코드·지연을 바꿀 수 있다.
 */
public final class StubFeedServer implements AutoCloseable {

    private final HttpServer server;
    private final List<JsonNode> events = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final AtomicInteger requests = new AtomicInteger();
    private volatile String tenantId;
    private volatile String token;
    private volatile int forcedStatus;
    private volatile String rawBody;
    private volatile long delayMillis;

    public StubFeedServer(String tenantId, String token) {
        this.tenantId = tenantId;
        this.token = token;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/internal/v1/events", this::handle);
        server.start();
    }

    public URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    public StubFeedServer events(List<? extends JsonNode> list) {
        events.clear();
        events.addAll(list);
        return this;
    }

    public StubFeedServer respondAs(String tenant) {
        this.tenantId = tenant;
        return this;
    }

    /** 0이면 정상 응답. 그 밖이면 그 상태 코드(본문 없음). */
    public StubFeedServer forceStatus(int status) {
        this.forcedStatus = status;
        return this;
    }

    /** 200 본문을 이 문자열로(형식 오류 시험). null이면 정상. */
    public StubFeedServer rawBody(String body) {
        this.rawBody = body;
        return this;
    }

    public StubFeedServer delay(long millis) {
        this.delayMillis = millis;
        return this;
    }

    public List<String> authorizations() {
        return List.copyOf(authorizations);
    }

    public int requests() {
        return requests.get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        authorizations.add(auth == null ? "" : auth);
        try {
            if (delayMillis > 0) {
                Thread.sleep(delayMillis);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (forcedStatus != 0) {
            send(exchange, forcedStatus, "");
            return;
        }
        if (!("Bearer " + token).equals(auth)) {
            send(exchange, 401, "{\"code\":\"UNAUTHENTICATED\",\"message\":\"service token is missing or invalid\"}");
            return;
        }
        Map<String, String> query = query(exchange.getRequestURI());
        long afterSeq = Long.parseLong(query.getOrDefault("afterSeq", "-1"));
        int limit = Integer.parseInt(query.getOrDefault("limit", "200"));
        if (afterSeq < 0 || limit < 1 || limit > 1000) {
            send(exchange, 400, "{\"code\":\"BAD_REQUEST\",\"message\":\"afterSeq/limit\"}");
            return;
        }
        long head = events.stream().mapToLong(e -> e.path("seq").asLong()).max().orElse(0);
        if (afterSeq > head) {
            send(exchange, 422, "{\"code\":\"AFTER_BEYOND_HEAD\",\"message\":\"afterSeq " + afterSeq + " > headSeq " + head + "\"}");
            return;
        }
        if (rawBody != null) {
            send(exchange, 200, rawBody);
            return;
        }
        List<JsonNode> page = new ArrayList<>();
        for (JsonNode e : events) {
            if (e.path("seq").asLong() > afterSeq && page.size() < limit) {
                page.add(e);
            }
        }
        ObjectNode body = Samples.JSON.createObjectNode();
        body.put("tenantId", tenantId);
        ArrayNode array = body.putArray("events");
        page.forEach(array::add);
        body.put("nextSeq", page.isEmpty() ? afterSeq : page.getLast().path("seq").asLong());
        body.put("headSeq", head);
        body.put("schemaVersion", 1);
        send(exchange, 200, Samples.JSON.writeValueAsString(body));
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> out = new HashMap<>();
        String q = uri.getRawQuery();
        if (q != null) {
            for (String pair : q.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    out.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            }
        }
        return out;
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
