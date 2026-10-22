package com.ga.portal.app.web;

import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.SeedData;
import com.ga.portal.ingest.ContractSchemas;
import com.ga.portal.ingest.EnvelopeValidator;
import com.ga.portal.ingest.EnvelopeVerdict;
import com.ga.portal.ingest.testing.StubFeedServer;
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

/**
 * 화면 E2E 하네스(포털 Phase 4 계획 ⑨ — Q8·Q11). {@code web/e2e/run.mjs}가 띄우고 내린다. 하는 일:
 * <ul>
 *   <li>일회용 PostgreSQL({@link PostgresHarness} — digest 고정 이미지, {@code init-roles.sql}, 볼륨 없음, Flyway). Docker가 없으면 여기서 실패한다(스킵 없음).</li>
 *   <li>테넌트 넷(GA01 A-1 둘째 판 + 이웃, GA02 엔진 {@code estimate_cycle_01}, GA03 엔진 E1 cycle, GA04 포털 손 스트림)을 <b>Playwright 프로젝트마다 한 벌씩</b>
 *       (프로젝트마다 머리를 따로 올린다 — 한 서버를 같이 쓰므로). 테넌트마다 시험 발급기(RS256 JWKS)와 피드 스텁({@link StubFeedServer})이 하나씩.</li>
 *   <li>스트림의 모든 줄을 계약 스키마로 검증한다({@link EnvelopeValidator}) — 하나라도 거부되면 시작하지 않는다.</li>
 *   <li>상태 디렉터리(권한 700)에 파일로만: 엔진 서비스 토큰({@code engine-tokens/<tenant>}), {@code policyKey} 비밀, 화면 토큰({@code tokens/<tenant>-<kind>.jwt}
 *       — 설계사·링크 없는 subject·만료), {@code env.json}(포트·DB URL·테넌트 목록 — 비밀 없음), 마지막에 {@code ready}. 명령 인자·환경 변수·출력에는 싣지 않는다.</li>
 *   <li>제어 포트(127.0.0.1): {@code POST /head/<tenant>/<seq>}(피드가 seq 1..n을 낸다), {@code POST /fail/<tenant>/on|off}(피드 503 — 동기화 지연).</li>
 * </ul>
 * 인자: 상태 디렉터리, 쉼표로 이은 프로젝트 접미사(예 {@code A,B,C}).
 */
public final class WebE2eHarness {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String AUDIENCE = "portal-e2e";
    static final SecureRandom RANDOM = new SecureRandom();

    record Base(String id, List<Path> streams) {
    }

    record Tenant(String id, TestIssuer issuer, StubFeedServer feed, List<JsonNode> events) {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: WebE2eHarness <state dir> <project suffixes, e.g. A,B,C>");
        }
        Path state = Path.of(args[0]);
        List<String> suffixes = List.of(args[1].split(","));
        Path root = Path.of(System.getProperty("ga.repoRoot"));
        Path golden = root.resolve("portal-projection/src/test/resources/golden-streams");
        Path hand = root.resolve("web/e2e/streams");
        List<Base> bases = List.of(
                new Base("GA01", List.of(root.resolve("contracts/events/v1/samples/a1/a1_portal_150000_e2.jsonl"), hand.resolve("ga01-neighbor.jsonl"))),
                new Base("GA02", List.of(golden.resolve("engine-e2-estimate/estimate_cycle_01_기본.jsonl"))),
                new Base("GA03", List.of(golden.resolve("engine-e1-cycle.jsonl"))),
                new Base("GA04", List.of(hand.resolve("edges.jsonl"))));

        Files.createDirectories(state);
        Files.setPosixFilePermissions(state, PosixFilePermissions.fromString("rwx------"));
        Path engineTokens = Files.createDirectories(state.resolve("engine-tokens"));
        Path tokens = Files.createDirectories(state.resolve("tokens"));
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
                StubFeedServer feed = new StubFeedServer(id, engineToken).events(List.of());
                register(db, id, feed, issuer);
                String agentSubject = "e2e-" + id.toLowerCase(java.util.Locale.ROOT) + "-agent-" + hex(4);
                String unlinkedSubject = "e2e-" + id.toLowerCase(java.util.Locale.ROOT) + "-unlinked-" + hex(4);
                link(db, id, agentSubject, "A-1001");
                Instant now = Instant.now();
                writeSecret(tokens.resolve(id + "-agent.jwt"), issuer.token(agentSubject, AUDIENCE,
                        b -> b.expirationTime(Date.from(now.plus(Duration.ofHours(3))))));
                writeSecret(tokens.resolve(id + "-unlinked.jwt"), issuer.token(unlinkedSubject, AUDIENCE,
                        b -> b.expirationTime(Date.from(now.plus(Duration.ofHours(3))))));
                writeSecret(tokens.resolve(id + "-expired.jwt"), issuer.token(agentSubject, AUDIENCE,
                        b -> b.issueTime(Date.from(now.minus(Duration.ofHours(2)))).expirationTime(Date.from(now.minus(Duration.ofMinutes(10))))));
                tenants.put(id, new Tenant(id, issuer, feed, events));
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

    static void register(PostgresHarness db, String tenant, StubFeedServer feed, TestIssuer issuer) {
        db.seed(tenant, c -> SeedData.exec(c, """
                INSERT INTO tenant (tenant_id, name, engine_base_url, status, payout_day_rule, params, oidc_issuer, oidc_audience)
                VALUES (?, ?, ?, 'ACTIVE', 'NEXT_MONTH_D10', '{"poll_interval_seconds": 5, "sync_delay_seconds": 10}'::jsonb, ?, ?)
                """, tenant, "E2E " + tenant, feed.baseUrl().toString(), issuer.issuer(), AUDIENCE));
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
