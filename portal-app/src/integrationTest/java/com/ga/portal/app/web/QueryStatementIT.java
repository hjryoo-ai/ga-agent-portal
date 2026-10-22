package com.ga.portal.app.web;

import com.ga.portal.infra.testing.RecordingDataSource;
import com.ga.portal.infra.testing.SeedData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C13: 홈 세 콜({@code /me}, {@code /earnings/{ym}}, {@code /limits?status=WARN})의 SQL 문장 수는 데이터 양과 무관하고 계획 ⑤의 상한과 같다. 가벼운 설계사(라인 0·원장
 * 1)와 무거운 설계사(라인 1,000·원장 200, 원장마다 열린 플래그)가 같은 수. 셈은 애플리케이션 데이터소스의 문장 — 트랜잭션마다 플랫폼 바인더가 내는
 * {@code set_config} 빼고 — 이고, 발급자 조회 1은 전용 롤 풀이라 여기 잡히지 않는다(요청마다 정확히 1 — {@code IssuerTenantResolverTest}). 상한 = 발급자 1 +
 * 링크 1 + 설정·커서 1 + 본문(/me 1, /earnings/{ym} 2, /limits 1). 포털 Phase 4(P-4): 화면 홈이 실제로 부르는 {@code /limits?status=ALL}도 같은 상한.
 */
@Import(QueryStatementIT.Recording.class)
class QueryStatementIT extends ApiIntegrationTest {

    static final String SUB_LIGHT = "sub-ph3-light-Zq88";
    static final String SUB_HEAVY = "sub-ph3-heavy-Zq89";
    static final String LIGHT = "PH3-QL-0001";
    static final String HEAVY = "PH3-QH-0002";

    @TestConfiguration
    static class Recording {
        static volatile RecordingDataSource recording;

        @Bean
        static BeanPostProcessor recordApplicationDataSource() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (bean instanceof DataSource ds && name.equals("dataSource")) {
                        recording = new RecordingDataSource(ds);
                        return recording;
                    }
                    return bean;
                }
            };
        }
    }

    @BeforeAll
    static void seed() {
        link(T1, SUB_LIGHT, LIGHT, "AGENT", "ACTIVE");
        link(T1, SUB_HEAVY, HEAVY, "AGENT", "ACTIVE");
        DB.seed(T1.value(), c -> {
            for (String agent : List.of(LIGHT, HEAVY)) {
                SeedData.exec(c, "INSERT INTO earnings_month (tenant_id, agent_id, close_ym, status, last_seq) VALUES (?, ?, '202608', 'PROVISIONAL', 1)",
                        T1.value(), agent);
            }
            SeedData.exec(c, """
                    INSERT INTO policy_limit (tenant_id, ledger_id, agent_id, policy_no, contract_date, fy_start, fy_end, monthly_premium, limit_multiple,
                                              limit_amount, accum_paid, rule_version_id)
                    VALUES (?, ?, ?, ?, DATE '2026-08-01', DATE '2026-08-01', DATE '2027-07-31', 1, '12', 100, 85, 'R')
                    """, T1.value(), LIGHT + "-L0", LIGHT, "POL-" + LIGHT);
            for (int i = 0; i < 1_000; i++) {
                SeedData.exec(c, """
                        INSERT INTO earnings_line (tenant_id, calc_id, agent_id, policy_no, comm_type, amount, close_ym, basis, policy_summary, source_seq)
                        VALUES (?, ?, ?, ?, ?, 1, '202608', '{"ruleVersionIds":[]}', '{"contractDate":"2026-08-01","monthlyPremium":1}', ?)
                        """, T1.value(), HEAVY + "-c" + i, HEAVY, "POL-" + HEAVY + "-" + (i % 200), i % 3 == 0 ? "FY_COMM" : "RENEWAL", 10_000 + i);
            }
            for (int i = 0; i < 200; i++) {
                SeedData.exec(c, """
                        INSERT INTO policy_limit (tenant_id, ledger_id, agent_id, policy_no, contract_date, fy_start, fy_end, monthly_premium, limit_multiple,
                                                  limit_amount, accum_paid, rule_version_id)
                        VALUES (?, ?, ?, ?, DATE '2026-08-01', DATE '2026-08-01', DATE '2027-07-31', 1, '12', 100, 85, 'R')
                        """, T1.value(), HEAVY + "-L" + i, HEAVY, "POL-" + HEAVY + "-" + i);
                SeedData.exec(c, "INSERT INTO policy_flag (tenant_id, policy_no, agent_id, flag_type, occurred_on, reason) VALUES (?, ?, ?, 'OVERDUE', DATE '2027-01-01', 'PAYMENT_OVERDUE')",
                        T1.value(), "POL-" + HEAVY + "-" + i, HEAVY);
            }
        });
    }

    /** 요청 하나의 애플리케이션 문장 수({@code set_config} 제외). */
    int statements(String subject, String path) {
        RecordingDataSource r = Recording.recording;
        int before = r.statements().size();
        assertThat(get(path, ISSUER_1.token(subject, AUD_1)).statusCode()).as(path).isEqualTo(200);
        return (int) r.statements().subList(before, r.statements().size()).stream()
                .filter(s -> !s.toLowerCase(Locale.ROOT).contains("set_config")).count();
    }

    @Test
    void homeCallsHaveAFixedStatementCountIndependentOfData() {
        Map<String, Integer> caps = new LinkedHashMap<>();
        caps.put("/api/v1/me", 4);
        caps.put("/api/v1/me/earnings/202608", 5);
        caps.put("/api/v1/me/limits?status=WARN", 4);
        // 포털 Phase 4 P-4: 화면 홈은 status=ALL로 받는다(WARN은 정확히 WARN만 — 주의 N건은 화면이 단계 문자열로 센다). 같은 문장 수여야 한다
        caps.put("/api/v1/me/limits?status=ALL", 4);
        assertThat(Recording.recording).as("the application data source is recorded").isNotNull();
        for (Map.Entry<String, Integer> e : caps.entrySet()) {
            int light = statements(SUB_LIGHT, e.getKey());
            int heavy = statements(SUB_HEAVY, e.getKey());
            System.out.println("QUERY_STATEMENTS " + e.getKey() + " light=" + light + " heavy=" + heavy + " (+1 issuer) cap=" + e.getValue());
            assertThat(heavy).as(e.getKey() + " heavy = light").isEqualTo(light);
            assertThat(light + 1).as(e.getKey() + " issuer + application statements = cap").isEqualTo(e.getValue());
        }
        // 무거운 쪽이 실제로 무겁다(시험이 공허하지 않다) — WARN 카드 200장
        assertThat(new String(get("/api/v1/me/limits?status=WARN", ISSUER_1.token(SUB_HEAVY, AUD_1)).body()).split("\"ledgerId\"")).hasSize(201);
    }
}
