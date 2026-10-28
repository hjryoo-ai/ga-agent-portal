package com.ga.portal.app.web;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.SeedData;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

/**
 * 조회 API IT의 공통 조립(3단계): 실제 앱(임의 포트) + PostgreSQL + 인프로세스 발급기 셋 — 테넌트 둘에 하나씩 등록, 하나는 미등록. 시드 subject·설계사 ID는 허구이고
 * 평문 스캔 센티널({@code pii-sentinels.properties} {@code identifiers})이다 — 로그·시험 출력에 원문이 나오면 {@code scanPlaintextLeaks}가 실패한다.
 * 하위 클래스는 같은 컨텍스트를 공유한다(설정이 같다).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ApiProbe.class)
abstract class ApiIntegrationTest {

    static final PostgresHarness DB = PostgresHarness.get();

    static final TestIssuer ISSUER_1 = new TestIssuer("ph3-t1");
    static final TestIssuer ISSUER_2 = new TestIssuer("ph3-t2");
    static final TestIssuer STRANGER = new TestIssuer("ph3-stranger");
    static final String AUD_1 = "portal-ph3-t1";
    static final String AUD_2 = "portal-ph3-t2";

    static final TenantId T1 = TenantId.of(SeedData.uniqueTenant("PH3A"));
    static final TenantId T2 = TenantId.of(SeedData.uniqueTenant("PH3B"));

    // 허구 subject·설계사 ID(센티널과 같은 값)
    static final String SUB_A = "sub-ph3-agent-a-Zq81";
    static final String SUB_B = "sub-ph3-agent-b-Zq82";
    static final String SUB_M = "sub-ph3-manager-Zq83";
    static final String SUB_R = "sub-ph3-revoked-Zq84";
    static final String SUB_O = "cli:ph3-ops-Zq85";
    static final String SUB_NONE = "sub-ph3-nolink-Zq86";
    static final String AGENT_A = "PH3-A-7731";
    static final String AGENT_B = "PH3-B-7732";
    static final String AGENT_M = "PH3-M-7733";
    static final String AGENT_R = "PH3-R-7734";
    static final String AGENT_A2 = "PH3-A2-7741";
    // A-1 둘째 판(계약 샘플)을 실제 적용기로 T1에 — 설계사 A-1001, 증권번호 POL-2026-0001(센티널)
    static final String SUB_A1 = "sub-ph3-a1001-Zq87";
    static final String AGENT_A1 = "A-1001";

    // 포털 Phase 8(V10, 조건 A): 화면 로그인 출처 — T1·T2에 하나씩, 셋째는 미등록. 시험 클라이언트의 Host(127.0.0.1:포트)는 어느 것도 아니다(하네스·다른 IT의 화면 CSP 그대로).
    static final String WEB_1 = "http://" + T1.value().toLowerCase(Locale.ROOT).replace('_', '-') + ".invalid:8080";
    static final String WEB_2 = "http://" + T2.value().toLowerCase(Locale.ROOT).replace('_', '-') + ".invalid:8080";
    static final String WEB_STRANGER = "http://ph8-stranger.invalid:8080";
    static final String CLIENT_1 = "portal-web-ph8-1";
    static final String CLIENT_2 = "portal-web-ph8-2";

    static {
        seedTenant(T1, ISSUER_1.issuer(), AUD_1);
        seedTenant(T2, ISSUER_2.issuer(), AUD_2);
        webLogin(T1, WEB_1, CLIENT_1);
        webLogin(T2, WEB_2, CLIENT_2);
        link(T1, SUB_A, AGENT_A, "AGENT", "ACTIVE");
        link(T1, SUB_B, AGENT_B, "AGENT", "ACTIVE");
        link(T1, SUB_M, AGENT_M, "MANAGER", "ACTIVE");
        link(T1, SUB_R, AGENT_R, "AGENT", "REVOKED");
        link(T1, SUB_O, null, "OPERATOR", "ACTIVE");
        // 같은 subject 문자열이 다른 테넌트에서는 다른 설계사다 — 테넌트는 발급자가 정한다
        link(T2, SUB_A, AGENT_A2, "AGENT", "ACTIVE");
        link(T1, SUB_A1, AGENT_A1, "AGENT", "ACTIVE");
        QuerySeed.agent(DB, T1, AGENT_A, "PH3 설계사 갑");
        QuerySeed.agent(DB, T1, AGENT_B, "PH3 설계사 을");
        QuerySeed.agent(DB, T1, AGENT_M, "PH3 조직장");
        QuerySeed.agent(DB, T2, AGENT_A2, "PH3 설계사 병");
    }

    /** policyKey HMAC 비밀(허구 — 실행마다 무작위 32바이트, 저장소에 없다). */
    static final java.nio.file.Path POLICY_KEY_SECRET = secretFile();

    private static java.nio.file.Path secretFile() {
        try {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("portal-policy-key");
            java.nio.file.Path file = dir.resolve("secret");
            byte[] secret = new byte[32];
            new java.security.SecureRandom().nextBytes(secret);
            java.nio.file.Files.write(file, secret);
            return file;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** 엔진 서비스 토큰 디렉터리(포털 Phase 5 — 시뮬레이션 IT가 테넌트 파일을 쓴다. 값은 실행마다 무작위, 저장소에 없다). */
    static final java.nio.file.Path ENGINE_TOKENS = tempDir("portal-engine-tokens");

    static java.nio.file.Path tempDir(String prefix) {
        try {
            return java.nio.file.Files.createTempDirectory(prefix);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static volatile boolean a1Applied;

    @Autowired
    com.ga.portal.ingest.apply.IngestApplier applier;

    @Autowired
    com.ga.portal.ingest.apply.IngestStore ingestStore;

    @Autowired
    com.ga.portal.ingest.apply.TenantTransactions tenantTransactions;

    /** A-1(11건)을 T1에 한 번 — 실제 적용기·프로젝터. 커서 last_seq = 11, caught_up_at은 시험이 정한다. */
    @org.junit.jupiter.api.BeforeEach
    void applyA1Once() {
        synchronized (ApiIntegrationTest.class) {
            if (a1Applied) {
                return;
            }
            tenantTransactions.inTransaction(T1, () -> ingestStore.ensureCursor(java.time.Instant.now()));
            for (tools.jackson.databind.node.ObjectNode e : com.ga.portal.projection.testing.Envelopes.nodes(com.ga.portal.projection.testing.Envelopes.a1())) {
                org.assertj.core.api.Assertions.assertThat(applier.apply(T1, e).outcome()).isEqualTo(com.ga.portal.ingest.apply.IngestApplier.Outcome.APPLIED);
            }
            a1Applied = true;
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::jdbcUrl);
        registry.add("management.opentelemetry.tracing.export.otlp.endpoint", () -> "http://127.0.0.1:9/v1/traces");
        registry.add("spring.flyway.url", DB::jdbcUrl);
        registry.add("portal.api.issuer-username", () -> PostgresHarness.ISSUER);
        registry.add("portal.api.issuer-password", () -> PostgresHarness.ISSUER_PASSWORD);
        registry.add("portal.query.policy-key-secret-file", POLICY_KEY_SECRET::toString);
        // 포털 Phase 5: 시뮬레이션 — 토큰 파일 디렉터리(인제스트와 같은 설정), 엔진 읽기 시간 초과는 시험이 줄인다(SLOW 스텁 3초 > 2초)
        registry.add("portal.ingest.token-dir", ENGINE_TOKENS::toString);
        registry.add("portal.sim.read-timeout", () -> "2s");
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    MeterRegistry meters;

    private final HttpClient http = HttpClient.newHttpClient();

    static void seedTenant(TenantId t, String issuer, String audience) {
        DB.seed(t.value(), c -> SeedData.exec(c, """
                INSERT INTO tenant (tenant_id, name, engine_base_url, status, payout_day_rule, params, oidc_issuer, oidc_audience)
                VALUES (?, ?, 'http://127.0.0.1:1', 'ACTIVE', 'NEXT_MONTH_D10', '{}'::jsonb, ?, ?)
                """, t.value(), "시험 GA " + t.value(), issuer, audience));
    }

    static void webLogin(TenantId t, String origin, String clientId) {
        DB.seed(t.value(), c -> SeedData.exec(c, "UPDATE tenant SET web_origin = ?, oidc_client_id = ? WHERE tenant_id = ?", origin, clientId, t.value()));
    }

    static void link(TenantId t, String subject, String agentId, String role, String status) {
        DB.seed(t.value(), c -> SeedData.exec(c, """
                INSERT INTO identity_link (tenant_id, subject, agent_id, role, status, org_path, linked_at)
                VALUES (?, ?, ?, ?, ?, ?, TIMESTAMPTZ '2026-10-17 00:00:00+00')
                """, t.value(), subject, agentId, role, status, "OPERATOR".equals(role) ? null : "/HQ1/BR01"));
    }

    HttpResponse<byte[]> get(String path, UnaryOperator<HttpRequest.Builder> edit) {
        return send(edit.apply(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET()));
    }

    HttpResponse<byte[]> get(String path, String token) {
        return get(path, b -> b.header("Authorization", "Bearer " + token));
    }

    /** JSON 본문 POST(포털 Phase 5 시뮬레이션) — 응답은 {@link #send}가 계약으로 검증한다. */
    HttpResponse<byte[]> post(String path, String token, String json) {
        return send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json, java.nio.charset.StandardCharsets.UTF_8)));
    }

    /** 보내고, 응답을 계약으로 검증한다(C11 — 모든 IT 응답). */
    HttpResponse<byte[]> send(HttpRequest.Builder request) {
        try {
            HttpRequest built = request.build();
            HttpResponse<byte[]> response = http.send(built, HttpResponse.BodyHandlers.ofByteArray());
            ApiContract.check(built.method(), built.uri().getPath(), response);
            return response;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** 응답 헤더 전부(이름 소문자) — {@code date}만 뺀다(초가 바뀐다). */
    static Map<String, List<String>> headers(HttpResponse<?> response) {
        Map<String, List<String>> out = new TreeMap<>();
        response.headers().map().forEach((name, values) -> {
            String n = name.toLowerCase(Locale.ROOT);
            if (!n.equals("date")) {
                out.put(n, List.copyOf(values));
            }
        });
        return out;
    }

    double counter(String name, String tenant, String reason) {
        Counter c = meters.find(name).tag("tenant", tenant).tag("reason", reason).counter();
        return c == null ? 0 : c.count();
    }

    record AuditLine(String actor, String action, String target, String detail) {
    }

    static List<AuditLine> audits(TenantId t) {
        return DB.asApp(t.value(), c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT actor_subject, action, target, detail FROM audit_access WHERE tenant_id = ? ORDER BY id")) {
                ps.setString(1, t.value());
                List<AuditLine> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new AuditLine(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
                    }
                }
                return out;
            }
        });
    }
}
