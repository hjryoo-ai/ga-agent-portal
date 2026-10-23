package com.ga.portal.sim.testing;

import com.ga.platform.canonical.Canonicalizer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 엔진 시뮬레이션 두 경로의 스텁(포털 Phase 5 ⑨, 계획 Q9·Q17). 데이터는 <b>엔진 샘플 사본뿐</b>({@code contracts/shared/ga-commission-engine/samples/simulations/}
 * — 엔진 {@code SimulationContractTest}가 엔진 응답과 바이트로 대조하는 파일): 받은 요청의 JCS 정규형(RFC 8785)이 그 엔진 상태({@code standard}·
 * {@code ambiguous})의 샘플 요청과 같으면 그 응답 바이트·상태를 그대로 낸다. 없으면 500(시험이 고른 요청이 샘플 밖이다 — 조용히 맞추지 않는다).
 * 서비스 토큰 검사는 이 처리기를 붙이는 서버({@code StubFeedServer.route})가 한다. 모드: {@code DOWN}(503), {@code SLOW}(지연 뒤 정상),
 * {@code BROKEN}(스키마 위반 200), {@code REJECT}(목록 밖 422). 호출 수와 받은 요청을 센다.
 */
public final class StubSimulationEngine implements HttpHandler {

    public static final String PATH_PREFIX = "/internal/v1/simulations/";
    static final JsonMapper JSON = JsonMapper.builder().build();

    /** {@code REJECT}: 닫힌 목록 밖의 엔진 422({@code MONTH_CLOSED}) — 샘플에 없는 경우를 만든다(포털 {@code ENGINE_REJECTED}). */
    public enum Mode { UP, DOWN, SLOW, BROKEN, REJECT }

    /** 샘플 하나(index.json의 행 + 두 파일). */
    public record Sample(String name, String engineState, String path, int status, byte[] request, byte[] response) {
        byte[] canonicalRequest() {
            return Canonicalizer.canonicalize(new String(request, StandardCharsets.UTF_8));
        }
    }

    private final String engineState;
    private final List<Sample> samples;
    private final AtomicInteger calls = new AtomicInteger();
    private final List<byte[]> received = new CopyOnWriteArrayList<>();
    private volatile Mode mode = Mode.UP;
    private volatile long slowMillis;

    private StubSimulationEngine(String engineState, List<Sample> samples) {
        this.engineState = engineState;
        this.samples = List.copyOf(samples);
    }

    /** 저장소의 샘플 사본 디렉터리. */
    public static Path samplesDir(Path repoRoot) {
        return repoRoot.resolve("contracts/shared/ga-commission-engine/samples/simulations");
    }

    /** 샘플 전부(index.json 순서). */
    public static List<Sample> load(Path dir) {
        try {
            JsonNode index = JSON.readTree(Files.readAllBytes(dir.resolve("index.json")));
            List<Sample> out = new ArrayList<>();
            for (JsonNode e : index) {
                String name = e.path("name").asString();
                out.add(new Sample(name, e.path("engineState").asString(), e.path("path").asString(), e.path("status").asInt(),
                        Files.readAllBytes(dir.resolve(name + ".request.json")), Files.readAllBytes(dir.resolve(name + ".response.json"))));
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 엔진 상태 하나의 스텁 — {@code standard}(보험사 하나) 또는 {@code ambiguous}(보험사 둘). */
    public static StubSimulationEngine of(Path dir, String engineState) {
        List<Sample> mine = load(dir).stream().filter(s -> s.engineState().equals(engineState)).toList();
        if (mine.isEmpty()) {
            throw new IllegalArgumentException("no samples for engine state " + engineState);
        }
        return new StubSimulationEngine(engineState, mine);
    }

    public String engineState() {
        return engineState;
    }

    public StubSimulationEngine mode(Mode mode) {
        this.mode = mode;
        return this;
    }

    /** {@code SLOW}의 지연. */
    public StubSimulationEngine slowMillis(long millis) {
        this.slowMillis = millis;
        return this;
    }

    public int calls() {
        return calls.get();
    }

    public List<byte[]> received() {
        return List.copyOf(received);
    }

    /** 샘플 요청과 정규형이 같은 샘플(시험이 직접 쓴다). */
    public java.util.Optional<Sample> match(String path, byte[] body) {
        byte[] canonical;
        try {
            canonical = Canonicalizer.canonicalize(new String(body, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return java.util.Optional.empty();
        }
        return samples.stream().filter(s -> s.path().equals(path) && Arrays.equals(s.canonicalRequest(), canonical)).findFirst();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        calls.incrementAndGet();
        byte[] body = exchange.getRequestBody().readAllBytes();
        received.add(body);
        String path = exchange.getRequestURI().getPath();
        if (!exchange.getRequestMethod().equals("POST") || !path.startsWith(PATH_PREFIX)) {
            send(exchange, 404, new byte[0]);
            return;
        }
        switch (mode) {
            case DOWN -> send(exchange, 503, new byte[0]);
            case BROKEN -> send(exchange, 200, "{\"ruleSnapshotId\":\"RS-broken\"}".getBytes(StandardCharsets.UTF_8));
            case REJECT -> send(exchange, 422, "{\"code\":\"MONTH_CLOSED\",\"message\":\"closed\"}".getBytes(StandardCharsets.UTF_8));
            case SLOW, UP -> {
                if (mode == Mode.SLOW) {
                    try {
                        Thread.sleep(slowMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                java.util.Optional<Sample> sample = match(path.substring(PATH_PREFIX.length()), body);
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
