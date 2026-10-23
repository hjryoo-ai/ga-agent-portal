package com.ga.portal.app.web;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.infra.testing.SeedData;
import com.ga.portal.ingest.testing.StubFeedServer;
import com.ga.portal.sim.testing.StubSimulationEngine;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 시뮬레이션 IT의 공통 조립(포털 Phase 5 ⑨): 테넌트 셋 — 표준 엔진(한도 1000), 보험사 둘 엔진(한도 1000), 레이트 리밋 전용(표준 엔진, 한도 3). 각 테넌트의
 * {@code engine_base_url}은 자기 스텁 서버({@code StubFeedServer.route} + {@link StubSimulationEngine} — 데이터는 엔진 샘플 사본뿐)이고 서비스 토큰은 실행마다
 * 무작위 파일이다. 설계사 {@code A-1001}(엔진 샘플의 설계사)은 세 테넌트에 링크, 표준 테넌트에는 그 설계사의 라인(POL-2026-0001 — 샘플 계약, POL-9999 — 엔진이
 * 모르는 계약)과 다른 설계사 B·조직장·엔진에 없는 설계사(A-9999)를 둔다. 키·subject는 평문 센티널이다.
 */
abstract class SimIntegrationTest extends ApiIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final Path SAMPLES = StubSimulationEngine.samplesDir(Path.of(System.getProperty("ga.repoRoot")));
    static final String NEW = "/api/v1/me/simulations/new-contract";
    static final String EVENT = "/api/v1/me/simulations/policy-event";

    static final TestIssuer ISSUER_STD = new TestIssuer("ph5-std");
    static final TestIssuer ISSUER_AMB = new TestIssuer("ph5-amb");
    static final TestIssuer ISSUER_RL = new TestIssuer("ph5-rl");
    static final TestIssuer ISSUER_BURST = new TestIssuer("ph5-burst");
    static final String AUD_STD = "portal-ph5-std";
    static final String AUD_AMB = "portal-ph5-amb";
    static final String AUD_RL = "portal-ph5-rl";
    static final String AUD_BURST = "portal-ph5-burst";
    static final TenantId STD = TenantId.of(SeedData.uniqueTenant("PH5S"));
    static final TenantId AMB = TenantId.of(SeedData.uniqueTenant("PH5A"));
    static final TenantId RL = TenantId.of(SeedData.uniqueTenant("PH5R"));
    /** 동시 레이트 리밋 시험만의 테넌트(한도 3) — 샘플의 설계사(A-1001)가 빈 창으로 시작하도록(포털 Phase 5 수용 심사 F10). */
    static final TenantId BURST = TenantId.of(SeedData.uniqueTenant("PH5B"));

    static final String SUB_MGR5 = "sub-ph5-manager-Zq90";
    static final String SUB_X = "sub-ph5-a9999-Zq91";
    static final String SUB_BURST = "sub-ph5-burst-Zq92";
    static final String SUB_REJECT = "sub-ph5-reject-Zq93";
    static final String AGENT_X = "A-9999";
    static final String POLICY = "POL-2026-0001";
    static final String POLICY_UNKNOWN_TO_ENGINE = "POL-9999";

    static final StubSimulationEngine ENGINE_STD = StubSimulationEngine.of(SAMPLES, "standard");
    static final StubSimulationEngine ENGINE_AMB = StubSimulationEngine.of(SAMPLES, "ambiguous");
    static final StubSimulationEngine ENGINE_RL = StubSimulationEngine.of(SAMPLES, "standard");
    static final StubSimulationEngine ENGINE_BURST = StubSimulationEngine.of(SAMPLES, "standard");

    static final String S1 = "{\"productKey\":\"WHOLE-LIFE-20Y\",\"monthlyPremium\":\"150000\",\"contractDate\":\"2026-08-01\"}";

    static {
        server(STD, ISSUER_STD, AUD_STD, ENGINE_STD, 1000);
        server(AMB, ISSUER_AMB, AUD_AMB, ENGINE_AMB, 1000);
        server(RL, ISSUER_RL, AUD_RL, ENGINE_RL, 3);
        server(BURST, ISSUER_BURST, AUD_BURST, ENGINE_BURST, 3);
        link(STD, SUB_A1, AGENT_A1, "AGENT", "ACTIVE");
        link(STD, SUB_B, AGENT_B, "AGENT", "ACTIVE");
        link(STD, SUB_MGR5, AGENT_M, "MANAGER", "ACTIVE");
        link(STD, SUB_X, AGENT_X, "AGENT", "ACTIVE");
        link(AMB, SUB_A1, AGENT_A1, "AGENT", "ACTIVE");
        link(RL, SUB_A1, AGENT_A1, "AGENT", "ACTIVE");
        link(BURST, SUB_BURST, AGENT_A1, "AGENT", "ACTIVE");
        link(RL, SUB_REJECT, AGENT_M, "AGENT", "ACTIVE");   // 레이트 리밋 창은 설계사마다 — 시험마다 다른 설계사(RateLimitIT·SimulationRejectionIT)
        DB.seed(STD.value(), c -> {
            line(c, STD, AGENT_A1, "PH5-L1", POLICY, 1);
            line(c, STD, AGENT_A1, "PH5-L2", POLICY_UNKNOWN_TO_ENGINE, 2);
            line(c, STD, AGENT_B, "PH5-L3", QuerySeed.policy(AGENT_B, 1), 3);
        });
    }

    private static void server(TenantId tenant, TestIssuer issuer, String audience, StubSimulationEngine engine, int ratePerMinute) {
        byte[] raw = new byte[24];
        new SecureRandom().nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);
        try {
            Files.writeString(ENGINE_TOKENS.resolve(tenant.value()), token, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        StubFeedServer stub = new StubFeedServer(tenant.value(), token).events(List.of()).route(StubSimulationEngine.PATH_PREFIX, engine);
        seedTenant(tenant, issuer.issuer(), audience);
        DB.seed(tenant.value(), c -> SeedData.exec(c, """
                UPDATE tenant SET engine_base_url = ?, params = jsonb_build_object('sim_rate_per_minute', ?::int) WHERE tenant_id = ?
                """, stub.baseUrl().toString(), ratePerMinute, tenant.value()));
    }

    private static void line(java.sql.Connection c, TenantId t, String agent, String calcId, String policy, long seq) throws java.sql.SQLException {
        SeedData.exec(c, """
                INSERT INTO earnings_line (tenant_id, calc_id, agent_id, policy_no, product_key, comm_type, amount, close_ym, limit_cut_amt, basis,
                                           policy_summary, source_seq)
                VALUES (?, ?, ?, ?, 'WHOLE-LIFE-20Y', 'FY_COMM', 1, '202608', 0, '{"ruleVersionIds":[]}'::jsonb,
                        '{"contractDate":"2026-08-01","monthlyPremium":150000}'::jsonb, ?)
                """, t.value(), calcId, agent, policy, seq);
    }

    /** 계약의 {@code policyKey} — 포털과 같은 식(HMAC-SHA256(비밀, 테넌트 + U+001F + 증권번호) 앞 16자, 설계서 §7). */
    static String key(TenantId tenant, String policyNo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Files.readAllBytes(POLICY_KEY_SECRET), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((tenant.value() + "\u001f" + policyNo).getBytes(StandardCharsets.UTF_8)), 0, 8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String event(TenantId tenant, String policyNo, String eventType, String extra) {
        return "{\"policyKey\":\"" + key(tenant, policyNo) + "\",\"eventType\":\"" + eventType + "\",\"asOfDate\":\"2027-02-15\"" + extra + "}";
    }

    String std(String subject) {
        return ISSUER_STD.token(subject, AUD_STD);
    }

    /** {@code simulation_log} 한 행(시험 판독용 — 앱 롤로, RLS 안에서). */
    record SimRow(String simId, String agentId, String kind, String request, String response, String ruleSnapshotId, String status, String errorCode,
                  Integer engineStatus, Instant createdAt) {
    }

    static List<SimRow> rows(TenantId t) {
        return DB.asApp(t.value(), c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT sim_id::text, agent_id, kind, request::text, response::text, rule_snapshot_id, status, error_code, engine_status, created_at
                      FROM simulation_log WHERE tenant_id = ? ORDER BY created_at, sim_id
                    """)) {
                ps.setString(1, t.value());
                List<SimRow> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new SimRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                                rs.getString(7), rs.getString(8), rs.getObject(9, Integer.class), rs.getObject(10, OffsetDateTime.class).toInstant()));
                    }
                }
                return out;
            }
        });
    }

    static SimRow row(TenantId t, String simId) {
        return rows(t).stream().filter(r -> r.simId().equals(simId)).findFirst().orElseThrow(() -> new AssertionError("no simulation_log row"));
    }

    /** 응답 − 포털 사실 넷. */
    static JsonNode engineFields(byte[] body) {
        tools.jackson.databind.node.ObjectNode n = (tools.jackson.databind.node.ObjectNode) JSON.readTree(body);
        n.remove(List.of("asOf", "dataStatus", "simId", "requestedAt"));
        return n;
    }

    /** JSON 본문의 정수 리터럴(나온 순서) — 바이트 동일의 근거. */
    static List<String> integers(String json) {
        Matcher m = Pattern.compile("(?<=[:\\[,])-?[0-9]+(?=[,}\\]])").matcher(json);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    double rateLimitedMetric(TenantId t) {
        var c = meters.find("api_rate_limited_total").tag("tenant", t.value()).counter();
        return c == null ? 0 : c.count();
    }
}
