package com.ga.portal.app.web;

import com.ga.portal.infra.testing.SeedData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C1·C3: IDOR — 설계사 A의 토큰으로 B의 라인·원장·월(경로에 agentId가 없으니 B만 가진 키로), 다른 테넌트의 키를 부르면 404이고 <b>없는 키의 404와 바이트가
 * 같다</b>. MANAGER(링크 역할)는 자기 데이터가 있어도 여덟 엔드포인트 전부 404. 쿼리에 실은 {@code agentId}는 아무것도 바꾸지 않는다.
 */
class AuthzIT extends ApiIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final byte[] NOT_FOUND = UniformRejectionIT.NOT_FOUND;

    /** B만 가진 달(202612) — A가 이 달을 부르면 "없다". */
    @BeforeAll
    static void monthOnlyBHas() {
        DB.seed(T1.value(), c -> SeedData.exec(c, """
                INSERT INTO earnings_month (tenant_id, agent_id, close_ym, status, last_seq) VALUES (?, ?, '202612', 'PROVISIONAL', 20)
                ON CONFLICT DO NOTHING
                """, T1.value(), AGENT_B));
    }

    static String text(HttpResponse<byte[]> r) {
        return new String(r.body(), StandardCharsets.UTF_8);
    }

    HttpResponse<byte[]> asA(String path) {
        return get(path, ISSUER_1.token(SUB_A, AUD_1));
    }

    void assertNotFoundLikeAMissingKey(HttpResponse<byte[]> others, HttpResponse<byte[]> missing, String what) {
        assertThat(others.statusCode()).as(what).isEqualTo(404);
        assertThat(others.body()).as(what).isEqualTo(NOT_FOUND).isEqualTo(missing.body());
        assertThat(headers(others)).as(what + " headers").isEqualTo(headers(missing));
    }

    @Test
    void anotherAgentsKeysAreNotFoundWithTheSameBytesAsAMissingKey() {
        String b = AGENT_B;
        assertNotFoundLikeAMissingKey(asA("/api/v1/me/earnings/lines/" + QuerySeed.calc(b, 1)), asA("/api/v1/me/earnings/lines/NO-SUCH-CALC"), "B's line");
        assertNotFoundLikeAMissingKey(asA("/api/v1/me/earnings/lines/1"), asA("/api/v1/me/earnings/lines/NO-SUCH-CALC"), "A-1001's line (A-1)");
        assertNotFoundLikeAMissingKey(asA("/api/v1/me/limits/" + QuerySeed.ledger(b, 1)), asA("/api/v1/me/limits/NO-SUCH-LEDGER"), "B's ledger");
        assertNotFoundLikeAMissingKey(asA("/api/v1/me/limits/1"), asA("/api/v1/me/limits/NO-SUCH-LEDGER"), "A-1001's ledger (A-1)");
        assertNotFoundLikeAMissingKey(asA("/api/v1/me/earnings/202612"), asA("/api/v1/me/earnings/203001"), "a month only B has");
        assertNotFoundLikeAMissingKey(asA("/api/v1/me/earnings/202612/lines"), asA("/api/v1/me/earnings/203001/lines"), "lines of a month only B has");
        // 다른 테넌트(T2)의 설계사가 T1의 키를 — RLS·테넌트 바인딩
        HttpResponse<byte[]> crossTenant = get("/api/v1/me/earnings/lines/" + QuerySeed.calc(AGENT_A, 1), ISSUER_2.token(SUB_A, AUD_2));
        assertNotFoundLikeAMissingKey(crossTenant, get("/api/v1/me/earnings/lines/NO-SUCH-CALC", ISSUER_2.token(SUB_A, AUD_2)), "T1's line from T2");
        // A 자신의 키는 열린다(시험이 공허하지 않다)
        assertThat(asA("/api/v1/me/earnings/lines/" + QuerySeed.calc(AGENT_A, 1)).statusCode()).isEqualTo(200);
        assertThat(asA("/api/v1/me/limits/" + QuerySeed.ledger(AGENT_A, 1)).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/me/earnings/lines/1", ISSUER_1.token(SUB_A1, AUD_1)).statusCode()).as("A-1001 opens its own A-1 line").isEqualTo(200);
    }

    @Test
    void aManagerLinkIsDeniedAtEveryEndpointEvenWithData() {
        String m = AGENT_M;
        List<String> paths = List.of("/api/v1/me", "/api/v1/me/earnings?from=202601&to=202612", "/api/v1/me/earnings/202608",
                "/api/v1/me/earnings/202608/lines", "/api/v1/me/earnings/lines/" + QuerySeed.calc(m, 1), "/api/v1/me/limits",
                "/api/v1/me/limits/" + QuerySeed.ledger(m, 1), "/api/v1/me/deferrals?from=202601&to=202612");
        int from = audits(T1).size();
        for (String path : paths) {
            HttpResponse<byte[]> r = get(path, ISSUER_1.token(SUB_M, AUD_1));
            assertThat(r.statusCode()).as(path).isEqualTo(404);
            assertThat(r.body()).as(path).isEqualTo(NOT_FOUND);
        }
        assertThat(audits(T1).subList(from, audits(T1).size())).hasSize(paths.size())
                .allSatisfy(l -> assertThat(l).extracting(AuditLine::actor, AuditLine::detail).containsExactly(SUB_M, "ROLE"));
    }

    @Test
    void anAgentIdInTheQueryStringChangesNothing() {
        JsonNode me = JSON.readTree(text(asA("/api/v1/me?agentId=" + AGENT_B + "&agent_id=" + AGENT_B)));
        assertThat(me.path("agentId").asString()).isEqualTo(AGENT_A);
        JsonNode limits = JSON.readTree(text(asA("/api/v1/me/limits?agentId=" + AGENT_B)));
        assertThat(limits.path("items").valueStream().map(i -> i.path("ledgerId").asString()).toList())
                .containsExactly(QuerySeed.ledger(AGENT_A, 1), QuerySeed.ledger(AGENT_A, 2));
    }

    @Test
    void anotherAgentsPolicyKeyFiltersToNothing() {
        String bKey = JSON.readTree(text(get("/api/v1/me/limits", ISSUER_1.token(SUB_B, AUD_1)))).path("items").get(0).path("policyKey").asString();
        JsonNode lines = JSON.readTree(text(asA("/api/v1/me/earnings/202608/lines?policyKey=" + bKey)));
        assertThat(lines.path("total").asLong()).isZero();
        assertThat(lines.path("items").size()).isZero();
        String aKey = JSON.readTree(text(asA("/api/v1/me/limits"))).path("items").get(0).path("policyKey").asString();
        assertThat(JSON.readTree(text(asA("/api/v1/me/earnings/202608/lines?policyKey=" + aKey))).path("total").asLong())
                .as("A's own key filters A's lines").isEqualTo(2);
    }
}
