package com.ga.portal.demo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 엔진 없는 데모의 엔진 대역(지시문 ②ⓔⓕ, 계획 P-5): 엔진 내부 계약({@code contracts/api/v1/engine-internal.openapi.yaml}) 세 경로를
 * {@link DemoData}의 바이트로만 섬긴다 — 계산 0.
 * <ul>
 *   <li>{@code GET /internal/v1/events?afterSeq&limit} — seq &gt; afterSeq를 순서대로 최대 limit(기본 200, 1~1000), {@code nextSeq} = 마지막 seq
 *       (없으면 afterSeq), {@code headSeq} = 보이는 마지막 seq({@link Replay}), afterSeq &gt; headSeq → 422 {@code AFTER_BEYOND_HEAD}.</li>
 *   <li>{@code GET /internal/v1/agents/{agentId}/statements/{ym}?mode} · {@code POST /internal/v1/simulations/{new-contract|policy-event}} —
 *       샘플에 있는 요청만 그 응답, 밖은 500 {@code NO_SAMPLE}(하네스 스텁과 같은 규약 — 조용히 맞추지 않는다).</li>
 *   <li>서비스 토큰이 틀리면 401(본문 읽지 않음). 로그에 토큰·요청 본문을 쓰지 않는다.</li>
 * </ul>
 */
public final class DemoServer implements AutoCloseable {

    private static final Pattern STATEMENT = Pattern.compile("^/internal/v1/agents/([A-Za-z0-9][A-Za-z0-9._-]{0,63})/statements/([0-9]{6})$");
    private static final List<String> MODES = List.of("ESTIMATE", "CONFIRMED");
    private static final List<String> SIMULATIONS = List.of("new-contract", "policy-event");

    private final DemoData data;
    private final byte[] token;
    private final Replay replay;
    private final HttpServer server;
    private final ScheduledExecutorService ticker;

    public DemoServer(DemoData data, String token, Replay replay, InetSocketAddress bind) throws IOException {
        this.data = data;
        this.token = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
        this.replay = replay;
        this.server = HttpServer.create(bind, 0);
        server.createContext("/internal/v1/events", ex -> guarded(ex, this::events));
        server.createContext("/internal/v1/agents/", ex -> guarded(ex, this::statement));
        server.createContext("/internal/v1/simulations/", ex -> guarded(ex, this::simulation));
        server.createContext("/", ex -> send(ex, 404, problem("NOT_FOUND", "not found")));
        server.setExecutor(Executors.newFixedThreadPool(4));
        this.ticker = replay.paced() ? Executors.newSingleThreadScheduledExecutor() : null;
        server.start();
        if (ticker != null) {
            ticker.scheduleAtFixedRate(replay::tick, replay.intervalMillis(), replay.intervalMillis(), TimeUnit.MILLISECONDS);
        }
    }

    public URI baseUrl() {
        return URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort());
    }

    @Override
    public void close() {
        if (ticker != null) {
            ticker.shutdownNow();
        }
        server.stop(0);
    }

    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private void guarded(HttpExchange ex, Handler handler) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !MessageDigest.isEqual(token, auth.getBytes(StandardCharsets.UTF_8))) {
            send(ex, 401, problem("UNAUTHENTICATED", "service token is missing or invalid"));
            return;
        }
        try {
            handler.handle(ex);
        } catch (IllegalArgumentException e) {
            send(ex, 400, problem("INVALID_REQUEST", "invalid request"));
        }
    }

    private void events(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET") || !ex.getRequestURI().getPath().equals("/internal/v1/events")) {
            send(ex, 404, problem("NOT_FOUND", "not found"));
            return;
        }
        Map<String, String> q = query(ex.getRequestURI());
        long afterSeq = Long.parseLong(Optional.ofNullable(q.get("afterSeq")).orElseThrow(IllegalArgumentException::new));
        int limit = Integer.parseInt(q.getOrDefault("limit", "200"));
        if (afterSeq < 0 || limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("afterSeq/limit");
        }
        List<JsonNode> visible = data.events().subList(0, replay.visible());
        long head = visible.isEmpty() ? 0 : visible.getLast().path("seq").asLong();
        if (afterSeq > head) {
            send(ex, 422, problem("AFTER_BEYOND_HEAD", "afterSeq is beyond headSeq"));
            return;
        }
        ObjectNode body = DemoData.JSON.createObjectNode();
        body.put("tenantId", data.tenantId());
        ArrayNode events = body.putArray("events");
        long next = afterSeq;
        for (JsonNode e : visible) {
            if (events.size() == limit) {
                break;
            }
            long seq = e.path("seq").asLong();
            if (seq > afterSeq) {
                events.add(e);
                next = seq;
            }
        }
        body.put("nextSeq", next);
        body.put("headSeq", head);
        body.put("schemaVersion", 1);
        send(ex, 200, body.toString());
    }

    private void statement(HttpExchange ex) throws IOException {
        Matcher m = STATEMENT.matcher(ex.getRequestURI().getPath());
        String mode = query(ex.getRequestURI()).get("mode");
        if (!ex.getRequestMethod().equals("GET") || !m.matches() || mode == null || !MODES.contains(mode)) {
            throw new IllegalArgumentException("statement request");
        }
        Optional<JsonNode> answer = data.statement(m.group(1), m.group(2), mode);
        if (answer.isEmpty()) {
            send(ex, 500, problem("NO_SAMPLE", "request is not one of the engine samples"));
            return;
        }
        send(ex, 200, answer.get().toString());
    }

    private void simulation(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath().substring("/internal/v1/simulations/".length());
        if (!ex.getRequestMethod().equals("POST") || !SIMULATIONS.contains(path)) {
            send(ex, 404, problem("NOT_FOUND", "not found"));
            return;
        }
        JsonNode request;
        try {
            request = DemoData.JSON.readTree(ex.getRequestBody().readAllBytes());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("simulation body");
        }
        Optional<JsonNode> answer = data.simulation(path, request);
        if (answer.isEmpty()) {
            send(ex, 500, problem("NO_SAMPLE", "request is not one of the engine samples"));
            return;
        }
        send(ex, 200, answer.get().toString());
    }

    private static String problem(String code, String message) {
        ObjectNode p = DemoData.JSON.createObjectNode();
        p.put("code", code);
        p.put("message", message);
        return p.toString();
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> out = new HashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 1) {
                throw new IllegalArgumentException("query");
            }
            out.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8), URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            ex.getResponseBody().write(bytes);
        }
        ex.close();
    }
}
