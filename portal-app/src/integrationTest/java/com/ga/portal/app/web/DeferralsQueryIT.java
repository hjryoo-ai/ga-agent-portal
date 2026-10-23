package com.ga.portal.app.web;

import com.ga.portal.infra.testing.SeedData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C9(포털 Phase 5 ⑦) + CR-1~4: 실제 앱·PostgreSQL로 {@code /me/deferrals} 1.1.0과 기존 경로의 추가 필드. 시드는 {@link QuerySeed}(설계사마다 분급 둘 —
 * 202609 예정 100,000, 202610 보류 50,000, 같은 계약) + 이 시험만의 행 하나(T1 설계사 갑의 202811 취소 30,000 — 다른 시험의 범위 밖). 유지율 시나리오는
 * T2만 켠다(T1은 끈 그대로 — RETENTION이면 400). 모든 응답은 {@link ApiContract}가 현재 계약(1.1.1)으로 검증한다.
 */
class DeferralsQueryIT extends ApiIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    @BeforeAll
    static void enableTheScenarioOnT2() {
        DB.seed(T2.value(), c -> SeedData.exec(c, """
                UPDATE tenant SET params = params || '{"forecast_scenario_enabled": true}'::jsonb WHERE tenant_id = ?
                """, T2.value()));
    }

    @BeforeAll
    static void aCancelledOnlyMonthForAgentA() {
        DB.seed(T1.value(), c -> SeedData.exec(c, """
                INSERT INTO deferral_item (tenant_id, schedule_id, agent_id, policy_no, source_calc_id, due_ym, amount, pay_condition, status, reason,
                                           curve_version_id, status_reason, last_seq)
                VALUES (?, ?, ?, ?, ?, '202811', 30000, 'INFORCE', 'CANCELLED', 'CURVE', 'CV1', 'POLICY_TERMINATED', 7)
                """, T1.value(), AGENT_A + "-S9", AGENT_A, QuerySeed.policy(AGENT_A, 1), QuerySeed.calc(AGENT_A, 1)));
    }

    JsonNode ok(String path, String token) {
        HttpResponse<byte[]> r = get(path, token);
        assertThat(r.statusCode()).as(path).isEqualTo(200);
        return JSON.readTree(r.body());
    }

    String t1A() {
        return ISSUER_1.token(SUB_A, AUD_1);
    }

    String t2A() {
        return ISSUER_2.token(SUB_A, AUD_2);
    }

    static List<String> row(JsonNode m) {
        List<String> out = new ArrayList<>();
        for (String field : new String[] {"dueYm", "expected", "held", "expectedBase", "bar"}) {
            out.add(!m.has(field) ? "" : m.get(field).isString() ? m.get(field).asString() : m.get(field).toString());
        }
        return out;
    }

    @Test
    void allInforceCarriesTheScenarioAndBarsAgainstTheLargestMonth() {
        JsonNode r = ok("/api/v1/me/deferrals?from=202609&to=202610", t1A());
        assertThat(r.path("scenario").asString()).isEqualTo("ALL_INFORCE");
        assertThat(r.has("p")).isFalse();
        assertThat(r.path("months")).extracting(DeferralsQueryIT::row).containsExactly(
                List.of("202609", "100000", "0", "", "{\"expected\":\"100.0%\",\"held\":\"0.0%\"}"),
                List.of("202610", "0", "50000", "", "{\"expected\":\"0.0%\",\"held\":\"50.0%\"}"));
    }

    @Test
    void retentionScalesOnlyTheExpectedSumAndKeepsHeldBaseAndItems() {
        JsonNode all = ok("/api/v1/me/deferrals?from=202609&to=202610", t2A());
        JsonNode thirty = ok("/api/v1/me/deferrals?from=202609&to=202610&scenario=RETENTION&p=30", t2A());
        assertThat(thirty.path("scenario").asString()).isEqualTo("RETENTION");
        assertThat(thirty.path("p").asInt()).isEqualTo(30);
        // 202609: 100,000 × 30% = 30,000, 보류 그대로 — 막대 분모는 적용 후 최댓값 50,000(202610 보류)
        assertThat(thirty.path("months")).extracting(DeferralsQueryIT::row).containsExactly(
                List.of("202609", "30000", "0", "100000", "{\"expected\":\"60.0%\",\"held\":\"0.0%\"}"),
                List.of("202610", "0", "50000", "0", "{\"expected\":\"0.0%\",\"held\":\"100.0%\"}"));
        assertThat(thirty.path("items")).isEqualTo(all.path("items"));
        assertThat(thirty.path("items")).extracting(i -> i.path("amount").asLong()).containsExactly(100_000L, 50_000L);

        // p = 100: 값 동일(바이트는 scenario·p·expectedBase만큼 다르다 — 계획 Q8)
        JsonNode hundred = ok("/api/v1/me/deferrals?from=202609&to=202610&scenario=RETENTION&p=100", t2A());
        for (int i = 0; i < all.path("months").size(); i++) {
            JsonNode h = hundred.path("months").get(i);
            JsonNode a = all.path("months").get(i);
            assertThat(h.path("expected")).isEqualTo(a.path("expected")).isEqualTo(h.path("expectedBase"));
            assertThat(h.path("held")).isEqualTo(a.path("held"));
            assertThat(h.path("bar")).isEqualTo(a.path("bar"));
        }
    }

    @Test
    void aMonthWithOnlyCancelledItemsHasNoMonthButKeepsItsItem() {
        // 계약 1.1.1(수용 심사 B1): months[] = 예정 또는 보류가 있는 달. 취소만 있는 달은 항목이 없는 달과 같은 모양 — 행이 없다
        JsonNode wide = ok("/api/v1/me/deferrals?from=202609&to=202811", t1A());
        assertThat(wide.path("months")).extracting(m -> m.path("dueYm").asString()).containsExactly("202609", "202610");
        assertThat(wide.path("items")).extracting(i -> i.path("dueYm").asString() + " " + i.path("status").asString() + " " + i.path("amount").asLong())
                .containsExactly("202609 SCHEDULED 100000", "202610 HELD 50000", "202811 CANCELLED 30000");
        // 월 합·막대(분모 = 실린 달의 최댓값)는 취소 달이 없는 범위와 같다
        assertThat(wide.path("months")).isEqualTo(ok("/api/v1/me/deferrals?from=202609&to=202610", t1A()).path("months"));

        JsonNode only = ok("/api/v1/me/deferrals?from=202811&to=202811", t1A());
        assertThat(only.path("months").size()).isZero();
        assertThat(only.path("items")).extracting(i -> i.path("status").asString()).containsExactly("CANCELLED");
    }

    @Test
    void invalidScenarioInputsAreTheSingle400() {
        List<String> bad = List.of(
                "/api/v1/me/deferrals?from=202609&to=202610&scenario=RETENTION&p=30",   // T1은 시나리오를 켜지 않았다
                "/api/v1/me/deferrals?from=202609&to=202610&p=30",
                "/api/v1/me/deferrals?from=202609&to=202610&scenario=BEST",
                "/api/v1/me/deferrals?from=202601&to=203301",                            // 85개월
                "/api/v1/me/deferrals?from=202609&to=202610&policyKey=not-a-key");
        for (String path : bad) {
            HttpResponse<byte[]> r = get(path, t1A());
            assertThat(r.statusCode()).as(path).isEqualTo(400);
            assertThat(r.body()).as(path).isEqualTo(UniformRejectionIT.INVALID_REQUEST);
        }
        for (String p : new String[] {"0", "101", "05"}) {
            assertThat(get("/api/v1/me/deferrals?from=202609&to=202610&scenario=RETENTION&p=" + p, t2A()).statusCode()).as(p).isEqualTo(400);
        }
        assertThat(get("/api/v1/me/deferrals?from=202601&to=203212", t1A()).statusCode()).as("84 months").isEqualTo(200);
        assertThat(get("/api/v1/me/earnings?from=202601&to=202901", t1A()).statusCode()).as("other ranges stay at 36").isEqualTo(400);
    }

    @Test
    void aPolicyKeyFiltersWithinTheCallersDeferralsOnly() {
        JsonNode mine = ok("/api/v1/me/deferrals?from=202609&to=202610", t1A());
        String key = mine.path("items").get(0).path("policyKey").asString();
        JsonNode filtered = ok("/api/v1/me/deferrals?from=202609&to=202610&policyKey=" + key, t1A());
        assertThat(filtered.path("months").size()).isEqualTo(2);
        assertThat(filtered.path("items").size()).isEqualTo(2);
        // B의 계약 키 — A의 집합에서 풀리지 않는다: 200 + 빈 결과(필터이지 자원이 아니다 — /lines와 같은 규약)
        String others = ok("/api/v1/me/deferrals?from=202609&to=202610", ISSUER_1.token(SUB_B, AUD_1)).path("items").get(0).path("policyKey").asString();
        JsonNode empty = ok("/api/v1/me/deferrals?from=202609&to=202610&policyKey=" + others, t1A());
        assertThat(empty.path("months").size()).isZero();
        assertThat(empty.path("items").size()).isZero();
        assertThat(ok("/api/v1/me/deferrals?from=203001&to=203012", t1A()).path("months").size()).as("no data, no bars").isZero();
    }

    @Test
    void cr1PreviousMonthCr2PairMonthsCr3Cr4LedgerFieldsAndFeatures() {
        JsonNode sept = ok("/api/v1/me/earnings/202609", t1A());
        assertThat(sept.path("previous").toString())
                .isEqualTo("{\"closeYm\":\"202608\",\"status\":\"PAID\",\"gross\":1900000,\"net\":1740600,\"netBasis\":\"PAYOUT\"}");
        assertThat(ok("/api/v1/me/earnings/202608", t1A()).has("previous")).as("no 202607 row").isFalse();

        JsonNode lines = ok("/api/v1/me/earnings/202608/lines", t1A());
        JsonNode reversed = find(lines.path("items"), QuerySeed.calc(AGENT_A, 3));
        JsonNode reversal = find(lines.path("items"), QuerySeed.calc(AGENT_A, 4));
        assertThat(reversed.path("reversedByYm").asString()).isEqualTo("202608");
        assertThat(reversal.path("reversalOfYm").asString()).isEqualTo("202608");
        assertThat(find(lines.path("items"), QuerySeed.calc(AGENT_A, 1)).has("reversalOfYm")).isFalse();
        assertThat(ok("/api/v1/me/earnings/lines/" + QuerySeed.calc(AGENT_A, 4), t1A()).path("line").path("reversalOfYm").asString()).isEqualTo("202608");

        JsonNode limits = ok("/api/v1/me/limits", t1A());
        JsonNode l1 = limits.path("items").get(0);
        JsonNode l2 = limits.path("items").get(1);
        assertThat(l1.path("ruleVersionId").asString()).isEqualTo("LIMIT_RULE:1");
        assertThat(l1.path("productKey").asString()).isEqualTo("WHOLE-LIFE-20Y");
        assertThat(l1.has("productName")).as("the first line of policy 1 has no product name").isFalse();
        assertThat(l2.path("productName").asString()).isEqualTo("허구 종신보험");
        assertThat(l2.path("insuredMasked").asString()).isEqualTo("김*수");

        assertThat(ok("/api/v1/me", t1A()).path("features").toString()).isEqualTo("{\"forecastScenario\":false}");
        assertThat(ok("/api/v1/me", t2A()).path("features").toString()).isEqualTo("{\"forecastScenario\":true}");
    }

    static JsonNode find(JsonNode items, String calcId) {
        for (JsonNode i : items) {
            if (i.path("calcId").asString().equals(calcId)) {
                return i;
            }
        }
        throw new AssertionError("no line " + calcId);
    }
}
