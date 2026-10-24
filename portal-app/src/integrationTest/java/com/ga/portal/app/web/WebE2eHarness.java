package com.ga.portal.app.web;

import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.SeedData;
import com.ga.portal.ingest.ContractSchemas;
import com.ga.portal.ingest.EnvelopeValidator;
import com.ga.portal.ingest.EnvelopeVerdict;
import com.ga.portal.ingest.testing.StubFeedServer;
import com.ga.portal.sim.testing.StubSimulationEngine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 화면 E2E 하네스(포털 Phase 4 계획 ⑨ — Q8·Q11). {@code web/e2e/run.mjs}가 띄우고 내린다. 하는 일:
 * <ul>
 *   <li>일회용 PostgreSQL({@link PostgresHarness} — digest 고정 이미지, {@code init-roles.sql}, 볼륨 없음, Flyway). Docker가 없으면 여기서 실패한다(스킵 없음).</li>
 *   <li>테넌트 넷(GA01 A-1 둘째 판 + 이웃, GA02 엔진 {@code estimate_cycle_01}, GA03 엔진 E1 cycle, GA04 포털 손 스트림)을 <b>Playwright 프로젝트마다 한 벌씩</b>
 *       (프로젝트마다 머리를 따로 올린다 — 한 서버를 같이 쓰므로). 테넌트마다 시험 발급기(RS256 JWKS)와 피드 스텁({@link StubFeedServer})이 하나씩.</li>
 *   <li>스트림의 모든 줄을 계약 스키마로 검증한다({@link EnvelopeValidator}) — 하나라도 거부되면 시작하지 않는다.</li>
 *   <li>상태 디렉터리(권한 700)에 파일로만: 엔진 서비스 토큰({@code engine-tokens/<tenant>}), {@code policyKey} 비밀, 화면 토큰({@code tokens/<tenant>-<kind>.jwt}
 *       — 설계사·링크 없는 subject·만료), {@code env.json}(포트·DB URL·테넌트 목록 — 비밀 없음), 마지막에 {@code ready}. 명령 인자·환경 변수·출력에는 싣지 않는다.</li>
 *   <li>제어 포트(127.0.0.1): {@code POST /head/<tenant>/<seq>}(피드가 seq 1..n을 낸다), {@code POST /fail/<tenant>/on|off}(피드 503 — 동기화 지연),
 *       Phase 5 {@code POST /sim/<tenant>/down|up|slow|fast|broken}(엔진 시뮬레이션 스텁 — 503·정상·지연·정상 속도·스키마 위반 200).</li>
 *   <li>Phase 5(계획 ⑨): 피드 스텁에 엔진 시뮬레이션 스텁({@link StubSimulationEngine} — 엔진 샘플 사본만, JCS 대조)을 붙인다. GA01·GA03·GA04는 표준 엔진,
 *       GA02는 보험사 둘 엔진. 테넌트 {@code params}: {@code sim_rate_per_minute} GA03 2(429 시나리오 전용) · 나머지 100, GA04만
 *       {@code forecast_scenario_enabled}(유지율 시나리오).</li>
 *   <li>Phase 6(계획 ⑨·Q9·Q26): GA05(손 스트림 {@code ga05-limits} — 한 원장의 상향·하향·재상향·재산정, 문의 한도 {@code inquiry_rate_per_day} 3).
 *       테넌트마다 하네스를 띄운 OS 사용자의 {@code cli:<user>} {@code OPERATOR} 링크(docs/운영.md §3). 제어 {@code POST /cli/<tenant>/rebuild}·
 *       {@code POST /cli/<tenant>/answer/<inquiryId>}는 <b>실 CLI</b>를 자식 프로세스로 띄운다 — 운영자와 같은 명령({@code java -jar <포털 jar>
 *       --spring.profiles.active=cli …}, 포털 프로세스와 같은 DB 주소·토큰 디렉터리). 답변은 허구 고정 문장을 상태 디렉터리의 파일(600)에 쓰고 경로만 넘기며, 끝나면 지운다.
 *       자식의 표준 출력은 버리고(파일·아티팩트에 남기지 않는다) 응답은 종료 코드 하나(200, 본문 = 숫자).</li>
 * </ul>
 * 인자: 상태 디렉터리, 쉼표로 이은 프로젝트 접미사(예 {@code A,B,C}). 시스템 속성: {@code ga.repoRoot}, {@code ga.portalJar}(CLI 자식이 실행하는 bootJar — Phase 6).
 */
public final class WebE2eHarness {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String AUDIENCE = "portal-e2e";
    static final SecureRandom RANDOM = new SecureRandom();

    /** 스트림 + 엔진 상태(샘플 묶음) + 시뮬레이션 레이트 + 유지율 시나리오 허용 + 문의 한도(0이면 두지 않는다 — 앱 기본). */
    record Base(String id, List<Path> streams, String engineState, int simRatePerMinute, boolean forecastScenario, int inquiryRatePerDay) {
    }

    /** CLI 자식의 답변 파일 문장(허구 고정 — 화면의 상세에 그대로 나온다). */
    static final String ANSWER = "확인 결과 지급 내역은 명세와 같습니다. 추가로 궁금한 점은 이 화면으로 다시 남겨 주세요.\n";
    static final Pattern UUID_TEXT = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    /** {@code IngestCommands.actorSubject}와 같은 형식 — 형식 밖이면 하네스가 뜨지 않는다(CLI가 거부할 것을 미리). */
    static final Pattern OS_USER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._@-]{0,63}$");
    static final long CLI_TIMEOUT_SECONDS = 150;

    record Tenant(String id, TestIssuer issuer, StubFeedServer feed, StubSimulationEngine engine, List<JsonNode> events) {
    }

    /** {@code slow}의 지연 — 앱 기본 읽기 시간 초과(10초)보다 1초 길게(계획 ⑨). */
    static final long SLOW_MILLIS = 11_000;

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: WebE2eHarness <state dir> <project suffixes, e.g. A,B,C>");
        }
        Path state = Path.of(args[0]);
        List<String> suffixes = List.of(args[1].split(","));
        Path root = Path.of(System.getProperty("ga.repoRoot"));
        Path golden = root.resolve("portal-projection/src/test/resources/golden-streams");
        Path hand = root.resolve("web/e2e/streams");
        Path samples = StubSimulationEngine.samplesDir(root);
        List<Base> bases = List.of(
                new Base("GA01", List.of(root.resolve("contracts/events/v1/samples/a1/a1_portal_150000_e2.jsonl"), hand.resolve("ga01-neighbor.jsonl")),
                        "standard", 100, false, 0),
                new Base("GA02", List.of(golden.resolve("engine-e2-estimate/estimate_cycle_01_기본.jsonl")), "ambiguous", 100, false, 0),
                new Base("GA03", List.of(golden.resolve("engine-e1-cycle.jsonl")), "standard", 2, false, 0),
                new Base("GA04", List.of(hand.resolve("edges.jsonl")), "standard", 100, true, 0),
                new Base("GA05", List.of(hand.resolve("ga05-limits.jsonl")), "standard", 100, false, 3));
        Path portalJar = Path.of(System.getProperty("ga.portalJar"));
        String osUser = System.getProperty("user.name", "");
        if (!OS_USER.matcher(osUser).matches()) {
            throw new IllegalStateException("the OS user cannot act as a CLI operator (format)");
        }

        Files.createDirectories(state);
        Files.setPosixFilePermissions(state, PosixFilePermissions.fromString("rwx------"));
        Path engineTokens = Files.createDirectories(state.resolve("engine-tokens"));
        Path tokens = Files.createDirectories(state.resolve("tokens"));
        Path answers = Files.createDirectories(state.resolve("answers"));
        writeSecret(state.resolve("policy-key"), hex(32));

        EnvelopeValidator validator = new EnvelopeValidator(ContractSchemas.get());
        PostgresHarness db = PostgresHarness.get();
        Map<String, Tenant> tenants = new LinkedHashMap<>();
        for (Base base : bases) {
            List<JsonNode> events = load(base.streams(), validator);
            for (String suffix : suffixes) {
                String id = base.id() + suffix;
                TestIssuer issuer = new TestIssuer("e2e-" + id.toLowerCase(java.util.Locale.ROOT));
                String engineToken = hex(24);
                writeSecret(engineTokens.resolve(id), engineToken);
                StubSimulationEngine engine = StubSimulationEngine.of(samples, base.engineState()).slowMillis(SLOW_MILLIS);
                StubFeedServer feed = new StubFeedServer(id, engineToken).events(List.of()).route(StubSimulationEngine.PATH_PREFIX, engine);
                register(db, id, feed, issuer, base);
                String agentSubject = "e2e-" + id.toLowerCase(java.util.Locale.ROOT) + "-agent-" + hex(4);
                String unlinkedSubject = "e2e-" + id.toLowerCase(java.util.Locale.ROOT) + "-unlinked-" + hex(4);
                link(db, id, agentSubject, "A-1001");
                operator(db, id, "cli:" + osUser);
                Instant now = Instant.now();
                writeSecret(tokens.resolve(id + "-agent.jwt"), issuer.token(agentSubject, AUDIENCE,
                        b -> b.expirationTime(Date.from(now.plus(Duration.ofHours(3))))));
                writeSecret(tokens.resolve(id + "-unlinked.jwt"), issuer.token(unlinkedSubject, AUDIENCE,
                        b -> b.expirationTime(Date.from(now.plus(Duration.ofHours(3))))));
                writeSecret(tokens.resolve(id + "-expired.jwt"), issuer.token(agentSubject, AUDIENCE,
                        b -> b.issueTime(Date.from(now.minus(Duration.ofHours(2)))).expirationTime(Date.from(now.minus(Duration.ofMinutes(10))))));
                tenants.put(id, new Tenant(id, issuer, feed, engine, events));
            }
        }

        HttpServer control = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        control.createContext("/head/", ex -> handle(ex, () -> {
            String[] parts = ex.getRequestURI().getPath().split("/");
            Tenant t = tenant(tenants, parts[2]);
            int seq = Integer.parseInt(parts[3]);
            if (seq < 0 || seq > t.events().size()) {
                throw new IllegalArgumentException("seq out of range");
            }
            t.feed().events(new ArrayList<>(t.events().subList(0, seq)));
        }));
        control.createContext("/fail/", ex -> handle(ex, () -> {
            String[] parts = ex.getRequestURI().getPath().split("/");
            tenant(tenants, parts[2]).feed().forceStatus("on".equals(parts[3]) ? 503 : 0);
        }));
        control.createContext("/sim/", ex -> handle(ex, () -> {
            String[] parts = ex.getRequestURI().getPath().split("/");
            StubSimulationEngine engine = tenant(tenants, parts[2]).engine();
            engine.mode(switch (parts[3]) {
                case "down" -> StubSimulationEngine.Mode.DOWN;
                case "up", "fast" -> StubSimulationEngine.Mode.UP;
                case "slow" -> StubSimulationEngine.Mode.SLOW;
                case "broken" -> StubSimulationEngine.Mode.BROKEN;
                default -> throw new IllegalArgumentException("unknown engine mode");
            });
        }));
        Cli cli = new Cli(ProcessHandle.current().info().command().orElseThrow(), portalJar,
                Map.of("PORTAL_DB_URL", db.jdbcUrl(), "PORTAL_ENGINE_TOKEN_DIR", engineTokens.toString(),
                        "PORTAL_POLICY_KEY_SECRET_FILE", state.resolve("policy-key").toString(), "PORTAL_INGEST_ENABLED", "false"));
        control.createContext("/cli/", ex -> exitCode(ex, () -> {
            String[] parts = ex.getRequestURI().getPath().split("/");
            Tenant t = tenant(tenants, parts[2]);
            return switch (parts.length == 4 ? parts[3] : parts.length == 5 ? parts[3] + "/" : "") {
                case "rebuild" -> cli.run(List.of("ingest", "rebuild", "--tenant=" + t.id()));
                case "answer/" -> {
                    if (!UUID_TEXT.matcher(parts[4]).matches()) {
                        throw new IllegalArgumentException("inquiry id");
                    }
                    Path file = answers.resolve(t.id() + "-" + parts[4] + ".txt");
                    writeSecret(file, ANSWER);
                    try {
                        yield cli.run(List.of("inquiry", "answer", "--tenant=" + t.id(), "--id=" + parts[4], "--answer-file=" + file));
                    } finally {
                        Files.deleteIfExists(file);
                    }
                }
                default -> throw new IllegalArgumentException("unknown cli control");
            };
        }));
        control.start();

        ObjectNode env = JSON.createObjectNode();
        env.put("controlUrl", "http://127.0.0.1:" + control.getAddress().getPort());
        env.put("dbUrl", db.jdbcUrl());
        env.put("engineTokenDir", engineTokens.toString());
        env.put("policyKeyFile", state.resolve("policy-key").toString());
        env.put("tokenDir", tokens.toString());
        ObjectNode heads = env.putObject("streamLength");
        tenants.values().forEach(t -> heads.put(t.id(), t.events().size()));
        Files.writeString(state.resolve("env.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(env));
        Files.writeString(state.resolve("ready"), "ready\n");
        System.out.println("web e2e harness ready: " + tenants.size() + " tenants");

        CountDownLatch forever = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            control.stop(0);
            tenants.values().forEach(t -> {
                t.feed().close();
                t.issuer().close();
            });
            forever.countDown();
        }));
        forever.await();
    }

    static List<JsonNode> load(List<Path> files, EnvelopeValidator validator) throws IOException {
        List<JsonNode> events = new ArrayList<>();
        for (Path f : files) {
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node = JSON.readTree(line);
                if (validator.validate(node) instanceof EnvelopeVerdict.Rejected r) {
                    throw new IllegalStateException(f.getFileName() + " seq " + node.path("seq").asLong() + " rejected: " + r.detail());
                }
                if (node.path("seq").asLong() != events.size() + 1) {
                    throw new IllegalStateException(f.getFileName() + ": seq " + node.path("seq").asLong() + " is not contiguous");
                }
                events.add(node);
            }
        }
        return List.copyOf(events);
    }

    static void register(PostgresHarness db, String tenant, StubFeedServer feed, TestIssuer issuer, Base base) {
        ObjectNode params = JSON.createObjectNode();
        params.put("poll_interval_seconds", 5);
        params.put("sync_delay_seconds", 10);
        params.put("sim_rate_per_minute", base.simRatePerMinute());
        params.put("forecast_scenario_enabled", base.forecastScenario());
        if (base.inquiryRatePerDay() > 0) {
            params.put("inquiry_rate_per_day", base.inquiryRatePerDay());
        }
        db.seed(tenant, c -> SeedData.exec(c, """
                INSERT INTO tenant (tenant_id, name, engine_base_url, status, payout_day_rule, params, oidc_issuer, oidc_audience)
                VALUES (?, ?, ?, 'ACTIVE', 'NEXT_MONTH_D10', ?::jsonb, ?, ?)
                """, tenant, "E2E " + tenant, feed.baseUrl().toString(), JSON.writeValueAsString(params), issuer.issuer(), AUDIENCE));
    }

    /** docs/운영.md §3의 등록 SQL 그대로 — 운영자는 설계사가 아니다(agent_id·org_path 없음). */
    static void operator(PostgresHarness db, String tenant, String subject) {
        db.seed(tenant, c -> SeedData.exec(c, """
                INSERT INTO identity_link (tenant_id, subject, agent_id, role, status, org_path, linked_at)
                VALUES (?, ?, NULL, 'OPERATOR', 'ACTIVE', NULL, TIMESTAMPTZ '2026-10-24 00:00:00+00')
                """, tenant, subject));
    }

    /** 실 CLI 자식 — 운영자가 치는 명령과 같다. 표준 출력은 버리고(본문·답변이 파일로 남지 않게) 표준 오류는 하네스 로그로. 종료 코드만 돌려준다. */
    record Cli(String javaCommand, Path jar, Map<String, String> env) {
        int run(List<String> command) throws IOException, InterruptedException {
            List<String> args = new ArrayList<>(List.of(javaCommand, "-Dfile.encoding=UTF-8", "-jar", jar.toString(), "--spring.profiles.active=cli"));
            args.addAll(command);
            ProcessBuilder pb = new ProcessBuilder(args).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.INHERIT);
            pb.environment().putAll(env);
            Process p = pb.start();
            if (!p.waitFor(CLI_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("cli timed out");
            }
            return p.exitValue();
        }
    }

    interface Code {
        int run() throws Exception;
    }

    /** CLI 제어: 200 + 종료 코드(숫자). 요청 형식이 틀리거나 시간 초과면 400(본문 없음). */
    static void exitCode(HttpExchange ex, Code action) throws IOException {
        byte[] body = new byte[0];
        int status;
        try {
            if (!"POST".equals(ex.getRequestMethod())) {
                status = 405;
            } else {
                body = String.valueOf(action.run()).getBytes(StandardCharsets.US_ASCII);
                status = 200;
            }
        } catch (Exception e) {
            status = 400;
        }
        ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    static void link(PostgresHarness db, String tenant, String subject, String agentId) {
        db.seed(tenant, c -> SeedData.exec(c, """
                INSERT INTO identity_link (tenant_id, subject, agent_id, role, status, org_path, linked_at)
                VALUES (?, ?, ?, 'AGENT', 'ACTIVE', '/HQ1/BR01', TIMESTAMPTZ '2026-10-17 00:00:00+00')
                """, tenant, subject, agentId));
    }

    static Tenant tenant(Map<String, Tenant> tenants, String id) {
        Tenant t = tenants.get(id);
        if (t == null) {
            throw new IllegalArgumentException("unknown tenant");
        }
        return t;
    }

    interface Action {
        void run() throws Exception;
    }

    static void handle(HttpExchange ex, Action action) throws IOException {
        int status;
        try {
            if (!"POST".equals(ex.getRequestMethod())) {
                status = 405;
            } else {
                action.run();
                status = 204;
            }
        } catch (Exception e) {
            status = 400;
        }
        ex.sendResponseHeaders(status, -1);
        try (OutputStream out = ex.getResponseBody()) {
            out.flush();
        }
    }

    static String hex(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    static void writeSecret(Path file, String value) {
        try {
            Files.writeString(file, value, StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
