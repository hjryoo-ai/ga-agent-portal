package com.ga.portal.app.web;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C4(포털 Phase 5 ⑥): 계약 이벤트의 {@code policyKey}가 호출자의 라인 증권번호 집합에서 풀리지 않으면(남의 계약·없는 키) 404 — 다른 404와 같은 바이트·헤더,
 * 감사 {@code AUTHZ_DENIED}/{@code MISSING}, {@code simulation_log} 행 0, 엔진 호출 0. {@code MANAGER} 링크는 두 경로 모두 404({@code ROLE}).
 */
class SimulationAuthzIT extends SimIntegrationTest {

    @Test
    void anotherAgentsOrAnUnknownPolicyKeyIsNotFoundWithoutARowOrACall() {
        int calls = ENGINE_STD.calls();
        int rows = rows(STD).size();
        int audits = audits(STD).size();
        String a = std(SUB_A1);
        HttpResponse<byte[]> others = post(EVENT, a, event(STD, QuerySeed.policy(AGENT_B, 1), "CANCEL", ""));
        HttpResponse<byte[]> unknown = post(EVENT, a, "{\"policyKey\":\"0123456789abcdef\",\"eventType\":\"CANCEL\",\"asOfDate\":\"2027-02-15\"}");
        HttpResponse<byte[]> otherTenant = post(EVENT, a, event(AMB, POLICY, "CANCEL", ""));   // 같은 증권번호도 다른 테넌트의 키는 풀리지 않는다
        for (HttpResponse<byte[]> r : List.of(others, unknown, otherTenant)) {
            assertThat(r.statusCode()).isEqualTo(404);
            assertThat(r.body()).isEqualTo(UniformRejectionIT.NOT_FOUND);
            assertThat(headers(r)).isEqualTo(headers(unknown));
        }
        assertThat(ENGINE_STD.calls()).as("no engine call").isEqualTo(calls);
        assertThat(rows(STD)).as("no simulation_log row").hasSize(rows);
        assertThat(audits(STD).subList(audits, audits(STD).size())).hasSize(3)
                .allSatisfy(l -> assertThat(l).extracting(AuditLine::action, AuditLine::target, AuditLine::detail)
                        .containsExactly("AUTHZ_DENIED", "POST /api/v1/me/simulations/policy-event", "MISSING"));
        // 자기 키는 풀린다(시험이 공허하지 않다)
        assertThat(post(EVENT, a, event(STD, POLICY, "CANCEL", ",\"elapsedInstallments\":\"7\"")).statusCode()).isEqualTo(200);
    }

    @Test
    void aManagerIsDeniedOnBothSimulations() {
        int calls = ENGINE_STD.calls();
        int rows = rows(STD).size();
        int audits = audits(STD).size();
        String m = std(SUB_MGR5);
        for (HttpResponse<byte[]> r : List.of(post(NEW, m, S1), post(EVENT, m, event(STD, POLICY, "CANCEL", "")))) {
            assertThat(r.statusCode()).isEqualTo(404);
            assertThat(r.body()).isEqualTo(UniformRejectionIT.NOT_FOUND);
        }
        assertThat(ENGINE_STD.calls()).isEqualTo(calls);
        assertThat(rows(STD)).hasSize(rows);
        assertThat(audits(STD).subList(audits, audits(STD).size())).extracting(AuditLine::detail).containsExactly("ROLE", "ROLE");
    }
}
