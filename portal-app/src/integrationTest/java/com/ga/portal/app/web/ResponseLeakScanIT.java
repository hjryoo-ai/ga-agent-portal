package com.ga.portal.app.web;

import com.ga.platform.core.tenant.TenantId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C6: 두 테넌트 × 설계사 넷(T1의 A·B·A-1001(A-1 둘째 판, 실제 적용기), T2의 A2)이 <b>모든 엔드포인트 × 모든 키</b>(월·라인 전부·페이지 전부·원장 전부·단계
 * 필터 전부)를 부른다. 응답 본문 전체 ∩ 증권번호 원문(센티널 파일의 증권번호 전부) = ∅, 다른 설계사의 ID·키·이름 ∅, {@code policyMasked}는 끝 4자,
 * {@code policyKey}는 16자 16진이고 같은 계약이면 엔드포인트가 달라도 같다. 엔드포인트별 응답 수를 표로 낸다(보고서).
 */
class ResponseLeakScanIT extends ApiIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final Pattern KEY = Pattern.compile("[0-9a-f]{16}");

    record Agent(String name, TenantId tenant, TestIssuer issuer, String audience, String subject, String agentId, List<String> foreignMarks) {
    }

    /** 센티널 파일의 증권번호(POL-…) — 시험이 고른 목록이 아니라 평문 스캔과 같은 원천. */
    static List<String> seededPolicyNumbers() {
        Properties p = new Properties();
        try (InputStream in = ResponseLeakScanIT.class.getClassLoader().getResourceAsStream("pii-sentinels.properties")) {
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return List.of(p.getProperty("identifiers").split(",")).stream().map(String::trim).filter(s -> s.startsWith("POL-")).toList();
    }

    final Map<String, Integer> responses = new TreeMap<>();
    final Map<String, String> keyByMasked = new LinkedHashMap<>();

    List<String> crawl(Agent a) {
        List<String> bodies = new ArrayList<>();
        String token = a.issuer().token(a.subject(), a.audience());
        java.util.function.BiConsumer<String, String> call = (endpoint, path) -> {
            HttpResponse<byte[]> r = get(path, token);
            assertThat(r.statusCode()).as("%s %s", a.name(), path).isEqualTo(200);
            bodies.add(new String(r.body(), StandardCharsets.UTF_8));
            responses.merge(endpoint, 1, Integer::sum);
        };
        call.accept("GET /api/v1/me", "/api/v1/me");
        List<String> months = new ArrayList<>();
        for (String[] range : new String[][] {{"202601", "202612"}, {"202701", "202712"}}) {
            String path = "/api/v1/me/earnings?from=" + range[0] + "&to=" + range[1];
            call.accept("GET /api/v1/me/earnings", path);
            JSON.readTree(bodies.getLast()).path("items").forEach(m -> months.add(m.path("closeYm").asString()));
            call.accept("GET /api/v1/me/deferrals", "/api/v1/me/deferrals?from=" + range[0] + "&to=" + range[1]);
        }
        assertThat(months).as(a.name()).isNotEmpty();
        List<String> calcs = new ArrayList<>();
        for (String ym : months) {
            call.accept("GET /api/v1/me/earnings/{ym}", "/api/v1/me/earnings/" + ym);
            for (int page = 0; ; page++) {
                call.accept("GET /api/v1/me/earnings/{ym}/lines", "/api/v1/me/earnings/" + ym + "/lines?size=2&page=" + page);
                JsonNode body = JSON.readTree(bodies.getLast());
                body.path("items").forEach(l -> calcs.add(l.path("calcId").asString()));
                if ((long) (page + 1) * 2 >= body.path("total").asLong()) {
                    break;
                }
            }
        }
        assertThat(calcs).as(a.name()).isNotEmpty().doesNotHaveDuplicates();
        for (String calc : calcs) {
            call.accept("GET /api/v1/me/earnings/lines/{calcId}", "/api/v1/me/earnings/lines/" + calc);
        }
        List<String> ledgers = new ArrayList<>();
        for (String status : List.of("ALL", "WARN", "CRIT", "FULL")) {
            call.accept("GET /api/v1/me/limits", "/api/v1/me/limits?status=" + status);
            if (status.equals("ALL")) {
                JSON.readTree(bodies.getLast()).path("items").forEach(l -> ledgers.add(l.path("ledgerId").asString()));
            }
        }
        assertThat(ledgers).as(a.name()).isNotEmpty();
        for (String ledger : ledgers) {
            call.accept("GET /api/v1/me/limits/{ledgerId}", "/api/v1/me/limits/" + ledger);
        }
        return bodies;
    }

    /** 응답 트리 전체의 (policyMasked, policyKey) 짝 — 같은 가림 값이면 같은 키(엔드포인트 무관), 키는 16자 16진. */
    void collectPolicyFields(String tenant, JsonNode node) {
        if (node.isObject()) {
            if (node.has("policyMasked")) {
                String masked = node.path("policyMasked").asString();
                String key = node.path("policyKey").asString();
                assertThat(key).matches(KEY);
                assertThat(masked).matches("^[*]+[^*]{0,4}$");
                String previous = keyByMasked.putIfAbsent(tenant + "|" + masked, key);
                assertThat(previous == null || previous.equals(key)).as("one policy → one key across endpoints (%s)", masked).isTrue();
            }
            node.forEach(child -> collectPolicyFields(tenant, child));
        } else if (node.isArray()) {
            node.forEach(child -> collectPolicyFields(tenant, child));
        }
    }

    @Test
    void noResponseCarriesAPolicyNumberOrAnotherAgentsData() {
        List<Agent> agents = List.of(
                new Agent("T1/A", T1, ISSUER_1, AUD_1, SUB_A, AGENT_A, List.of(AGENT_B, AGENT_M, AGENT_A1, AGENT_A2, "PH3 설계사 을")),
                new Agent("T1/B", T1, ISSUER_1, AUD_1, SUB_B, AGENT_B, List.of(AGENT_A, AGENT_M, AGENT_A1, AGENT_A2, "PH3 설계사 갑")),
                new Agent("T1/A-1001 (A-1)", T1, ISSUER_1, AUD_1, SUB_A1, AGENT_A1, List.of(AGENT_A, AGENT_B, AGENT_M, AGENT_A2, "PH3 설계사")),
                new Agent("T2/A2", T2, ISSUER_2, AUD_2, SUB_A, AGENT_A2, List.of(AGENT_A, AGENT_B, AGENT_M, AGENT_A1, "PH3 설계사 갑", "PH3 설계사 을")));
        List<String> policies = seededPolicyNumbers();
        assertThat(policies).contains("POL-2026-0001", QuerySeed.policy(AGENT_A, 1), QuerySeed.policy(AGENT_A2, 2)).hasSize(9);
        int total = 0;
        for (Agent a : agents) {
            List<String> bodies = crawl(a);
            total += bodies.size();
            String all = String.join("\n", bodies);
            for (String policy : policies) {
                assertThat(all).as("%s responses contain a policy number", a.name()).doesNotContain(policy);
            }
            for (String foreign : a.foreignMarks()) {
                assertThat(all).as("%s responses contain another agent's data", a.name()).doesNotContain(foreign);
            }
            for (String body : bodies) {
                collectPolicyFields(a.tenant().value(), JSON.readTree(body));
            }
        }
        // 가림 값의 끝 4자 = 원문의 끝 4자(시드에서 알 수 있는 것만)
        assertThat(keyByMasked.keySet()).anySatisfy(k -> assertThat(k).endsWith("*0001"))
                .anySatisfy(k -> assertThat(k).endsWith("1-01"));
        responses.forEach((endpoint, n) -> System.out.println("RESPONSE_SCAN " + endpoint + " = " + n));
        System.out.println("RESPONSE_SCAN total = " + total);
        assertThat(responses).hasSize(8);
    }
}
