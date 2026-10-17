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

    static {
        seedTenant(T1, ISSUER_1.issuer(), AUD_1);
        seedTenant(T2, ISSUER_2.issuer(), AUD_2);
        link(T1, SUB_A, AGENT_A, "AGENT", "ACTIVE");
        link(T1, SUB_B, AGENT_B, "AGENT", "ACTIVE");
        link(T1, SUB_M, AGENT_M, "MANAGER", "ACTIVE");
        link(T1, SUB_R, AGENT_R, "AGENT", "REVOKED");
        link(T1, SUB_O, null, "OPERATOR", "ACTIVE");
        // 같은 subject 문자열이 다른 테넌트에서는 다른 설계사다 — 테넌트는 발급자가 정한다
        link(T2, SUB_A, AGENT_A2, "AGENT", "ACTIVE");
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::jdbcUrl);
        registry.add("spring.flyway.url", DB::jdbcUrl);
        registry.add("portal.api.issuer-username", () -> PostgresHarness.ISSUER);
        registry.add("portal.api.issuer-password", () -> PostgresHarness.ISSUER_PASSWORD);
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

    HttpResponse<byte[]> send(HttpRequest.Builder request) {
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
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
