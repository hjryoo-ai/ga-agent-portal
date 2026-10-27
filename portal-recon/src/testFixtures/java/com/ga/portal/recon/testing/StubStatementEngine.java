package com.ga.portal.recon.testing;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 엔진 명세 조회의 스텁(포털 Phase 7 ④·⑤, 계획 P-2). 데이터는 <b>엔진 샘플 사본뿐</b>({@code contracts/shared/ga-commission-engine/samples/statements/<엔진
 * 상태>/} — 엔진 {@code StatementContractTest}가 엔진 응답과 대조하는 파일): 받은 요청의 {@code agentId}·{@code ym}·{@code mode}가 그 엔진 상태의 샘플
 * 요청과 같으면 그 응답 바이트·상태를 그대로 낸다. 없으면 500(시험이 고른 요청이 샘플 밖이다 — 조용히 맞추지 않는다, C1). 서비스 토큰 검사는 이
 * 처리기를 붙이는 서버가 한다. 모드: {@code DOWN}(503), {@code SLOW}(지연 뒤 정상), {@code BROKEN}(스키마 위반 200). 호출 수와 받은 요청을 센다.
 */
public final class StubStatementEngine implements HttpHandler {

    public static final String PATH_PREFIX = "/internal/v1/agents/";
    private static final Pattern PATH = Pattern.compile("^/internal/v1/agents/([^/]+)/statements/([^/]+)$");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public enum Mode { UP, DOWN, SLOW, BROKEN }

    /** 샘플 하나(index.json의 행 + 응답 바이트). */
    public record Sample(String name, String agentId, String ym, String mode, int status, byte[] response) {
    }

    /** 받은 요청 하나. */
    public record Request(String agentId, String ym, String mode) {
    }

    private final String engineState;
    private final List<Sample> samples;
    private final AtomicInteger calls = new AtomicInteger();
    private final List<Request> received = new CopyOnWriteArrayList<>();
    private volatile Mode mode = Mode.UP;
    private volatile long slowMillis;

    private StubStatementEngine(String engineState, List<Sample> samples) {
        this.engineState = engineState;
        this.samples = List.copyOf(samples);
    }

    /** 저장소의 명세 샘플 사본 디렉터리(엔진 상태 묶음들의 부모). */
    public static Path samplesDir(Path repoRoot) {
        return repoRoot.resolve("contracts/shared/ga-commission-engine/samples/statements");
    }

    /** 엔진 상태 하나의 샘플 전부(index.json 순서). 요청 파일이 index 행과 다르면 실패한다. */
    public static List<Sample> load(Path dir, String engineState) {
        Path bundle = dir.resolve(engineState);
        try {
            JsonNode index = JSON.readTree(Files.readAllBytes(bundle.resolve("index.json")));
            List<Sample> out = new ArrayList<>();
            for (JsonNode e : index) {
                String name = e.path("name").asString();
                JsonNode request = JSON.readTree(Files.readAllBytes(bundle.resolve(name + ".request.json")));
                for (String key : List.of("agentId", "ym", "mode")) {
                    if (!request.path(key).equals(e.path(key))) {
                        throw new IllegalStateException(name + ": request " + key + " differs from index.json");
                    }
                }
                out.add(new Sample(name, e.path("agentId").asString(), e.path("ym").asString(), e.path("mode").asString(), e.path("status").asInt(),
                        Files.readAllBytes(bundle.resolve(name + ".response.json"))));
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 엔진 상태 하나의 스텁 — {@code a1}·{@code estimate-cycle-01}·{@code estimate-cycle-05}. */
    public static StubStatementEngine of(Path dir, String engineState) {
        List<Sample> mine = load(dir, engineState);
        if (mine.isEmpty()) {
            throw new IllegalArgumentException("no samples for engine state " + engineState);
        }
        return new StubStatementEngine(engineState, mine);
    }

    public String engineState() {
        return engineState;
    }

    public List<Sample> samples() {
        return samples;
    }

    public StubStatementEngine mode(Mode mode) {
        this.mode = mode;
        return this;
    }

    /** {@code SLOW}의 지연. */
    public StubStatementEngine slowMillis(long millis) {
        this.slowMillis = millis;
        return this;
    }

    public int calls() {
        return calls.get();
    }

    public List<Request> received() {
        return List.copyOf(received);
    }

    public Optional<Sample> match(String agentId, String ym, String mode) {
        return samples.stream().filter(s -> s.agentId().equals(agentId) && s.ym().equals(ym) && s.mode().equals(mode)).findFirst();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        calls.incrementAndGet();
        Matcher m = PATH.matcher(exchange.getRequestURI().getRawPath());
        String query = exchange.getRequestURI().getRawQuery();
        if (!exchange.getRequestMethod().equals("GET") || !m.matches() || query == null || !query.startsWith("mode=") || query.contains("&")) {
            send(exchange, 404, new byte[0]);
            return;
        }
        Request r = new Request(URLDecoder.decode(m.group(1), StandardCharsets.UTF_8), URLDecoder.decode(m.group(2), StandardCharsets.UTF_8),
                URLDecoder.decode(query.substring("mode=".length()), StandardCharsets.UTF_8));
        received.add(r);
        switch (mode) {
            case DOWN -> send(exchange, 503, new byte[0]);
            case BROKEN -> send(exchange, 200, ("{\"agentId\":\"" + r.agentId() + "\",\"closeYm\":\"" + r.ym() + "\",\"gross\":\"broken\"}")
                    .getBytes(StandardCharsets.UTF_8));
            case SLOW, UP -> {
                if (mode == Mode.SLOW) {
                    try {
                        Thread.sleep(slowMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                Optional<Sample> sample = match(r.agentId(), r.ym(), r.mode());
                if (sample.isPresent()) {
                    send(exchange, sample.get().status(), sample.get().response());
                } else {
                    send(exchange, 500, "{\"code\":\"NO_SAMPLE\",\"message\":\"request is not one of the engine samples\"}".getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    private static void send(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        exchange.close();
    }
}
