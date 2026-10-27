package com.ga.portal.app.recon;

import com.ga.portal.recon.RunRecord;
import com.ga.portal.recon.ReconRunner;
import com.ga.portal.recon.testing.StubStatementEngine;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 포털 Phase 7 C4(계획 ③ R0~R3): 실제 DB·앱 어댑터·엔진 샘플 스텁(HTTP)으로 A-1을 대사한다.
 * <ul>
 *   <li>MATCH·INCONCLUSIVE는 {@code recon_result} 행 0(지표·회차 행만), MATCH 행은 어디에도 없다.</li>
 *   <li>DRIFT는 BATCH 행 하나 + 그 달 {@code data_status = DRIFT} — <b>한 트랜잭션</b>({@code data_status} 갱신을 DB에서 실패시키면 행도 없다 — 주입 S4).</li>
 *   <li>같은 값의 DRIFT는 다음 회차에 행을 다시 쓰지 않는다(Q13), 새 DRIFT가 아니다(Q14).</li>
 *   <li>결정론: 같은 스트림·스텁·시계의 두 테넌트는 {@code recon_result}·{@code recon_run}이 바이트로 같다(테넌트 열 제외).</li>
 *   <li>뒤처진 커서는 엔진을 부르지 않는다, 회차 잠금을 다른 연결이 쥐면 회차가 없다.</li>
 * </ul>
 */
class ReconRunIT {

    private static final String AUTO_OFF = "{\"auto_rebuild_enabled\": false}";

    private static RunRecord done(ReconRunner.Outcome o) {
        assertThat(o).isInstanceOf(ReconRunner.Outcome.Done.class);
        return ((ReconRunner.Outcome.Done) o).run();
    }

    @Test
    void aMatchingTenantWritesNoResultRowsAndOneRunRow() {
        try (ReconRig r = new ReconRig("RCN_OK", "a1", ReconRig.a1(), AUTO_OFF).ingestAll()) {
            RunRecord run = done(r.run("202608", "202702"));
            assertThat(List.of(run.checked(), run.matched(), run.driftNew(), run.driftUnresolved(), run.inconclusive(), run.engineCalls()))
                    .containsExactly(2, 2, 0, 0, 0, 2);
            assertThat(r.reconResults()).isEmpty();
            assertThat(r.reconRuns()).singleElement().satisfies(line -> assertThat(line).endsWith("|cli:ops-kim|CLI|2|2|0|0|0|2|OK|-|-"));
            assertThat(r.stub.received()).containsExactly(new StubStatementEngine.Request("A-1001", "202608", "CONFIRMED"),
                    new StubStatementEngine.Request("A-1001", "202702", "ESTIMATE"));
            assertThat(r.counter("recon_checks_total", "tenant", r.tenant.value(), "result", "MATCH")).isEqualTo(2.0);
            assertThat(r.counter("recon_runs_total", "tenant", r.tenant.value(), "result", "OK")).isEqualTo(1.0);
            assertThat(r.dataStatus("A-1001", "202608")).isEqualTo("OK");
        }
    }

    @Test
    void aDriftIsOneBatchRowAndTheMonthIsDriftAndTheSameValuesAreNotWrittenAgain() {
        try (ReconRig r = new ReconRig("RCN_DRIFT", "a1", ReconRig.a1(), AUTO_OFF).ingestAll()) {
            r.corruptLine("1");   // 202608 FY 945,000을 지운다 → 포털 855,000, 엔진 1,800,000
            RunRecord first = done(r.run("202608", "202702"));
            assertThat(first.driftNew()).isEqualTo(1);
            assertThat(first.driftUnresolved()).isEqualTo(1);
            assertThat(first.rebuiltAt()).isEmpty();
            assertThat(r.reconResults()).singleElement().satisfies(line -> {
                String[] f = line.split("\\|", -1);
                assertThat(List.of(f[0], f[1])).containsExactly("A-1001", "202608");
                assertThat(List.of(f).subList(3, 13)).as("portal set, compared engine set, net never (Q3)")
                        .containsExactly("855000", "0", "0", "1800000", "0", "0", "-", "DRIFT", "BATCH", "0");
            });
            assertThat(r.dataStatus("A-1001", "202608")).isEqualTo("DRIFT");
            assertThat(r.dataStatus("A-1001", "202702")).isEqualTo("OK");

            r.clock.now = r.clock.now.plusSeconds(600);
            RunRecord second = done(r.run("202608", "202702"));
            assertThat(r.reconResults()).as("Q13").hasSize(1);
            assertThat(second.driftNew()).as("Q14").isZero();
            assertThat(second.driftUnresolved()).isEqualTo(1);
            assertThat(r.counter("recon_checks_total", "tenant", r.tenant.value(), "result", "DRIFT")).isEqualTo(2.0);
            assertThat(r.reconResults()).noneMatch(l -> l.contains("|MATCH|"));
        }
    }

    @Test
    void theDriftRowAndTheDataStatusAreOneTransaction() {
        try (ReconRig r = new ReconRig("RCN_TX", "a1", ReconRig.a1(), AUTO_OFF).ingestAll()) {
            r.corruptLine("1");
            String trigger = "recon_it_fail_" + r.tenant.value().toLowerCase(java.util.Locale.ROOT);
            exec("""
                    CREATE OR REPLACE FUNCTION recon_it_fail() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN
                      IF NEW.tenant_id = TG_ARGV[0] AND NEW.data_status = 'DRIFT' THEN RAISE EXCEPTION 'injected data_status failure'; END IF;
                      RETURN NEW;
                    END $$""");
            exec("CREATE TRIGGER " + trigger + " BEFORE UPDATE ON earnings_month FOR EACH ROW EXECUTE FUNCTION recon_it_fail('" + r.tenant.value() + "')");
            try {
                assertThatThrownBy(() -> r.run("202608")).isInstanceOf(RuntimeException.class);
            } finally {
                exec("DROP TRIGGER " + trigger + " ON earnings_month");
            }
            assertThat(r.reconResults()).as("the DRIFT row rolled back with the data_status update").isEmpty();
            assertThat(r.dataStatus("A-1001", "202608")).isEqualTo("OK");
            assertThat(r.reconRuns()).as("the run did not reach R3").isEmpty();
        }
    }

    @Test
    void twoTenantsWithTheSameInputsWriteTheSameRows() {
        try (ReconRig a = new ReconRig("RCN_DETA", "a1", ReconRig.a1(), AUTO_OFF).ingestAll();
             ReconRig b = new ReconRig("RCN_DETB", "a1", ReconRig.a1(), AUTO_OFF).ingestAll()) {
            for (ReconRig r : List.of(a, b)) {
                r.corruptLine("1");
                done(r.run("202608", "202702"));
                r.clock.now = r.clock.now.plusSeconds(60);
                done(r.run("202608", "202702"));
            }
            assertThat(a.reconResults()).hasSize(1).isEqualTo(b.reconResults());
            assertThat(a.reconRuns()).hasSize(2).isEqualTo(b.reconRuns());
        }
    }

    @Test
    void anUnavailableEngineIsInconclusiveWithoutRows() {
        try (ReconRig r = new ReconRig("RCN_DOWN", "a1", ReconRig.a1(), AUTO_OFF).ingestAll()) {
            r.corruptLine("1");
            r.stub.mode(StubStatementEngine.Mode.DOWN);
            RunRecord run = done(r.run("202608", "202702"));
            assertThat(run.inconclusive()).isEqualTo(2);
            assertThat(run.engineCalls()).as("no retry").isEqualTo(2);
            assertThat(r.reconResults()).isEmpty();
            assertThat(r.dataStatus("A-1001", "202608")).isEqualTo("OK");
            assertThat(r.counter("recon_inconclusive_total", "tenant", r.tenant.value(), "reason", "ENGINE_UNAVAILABLE")).isEqualTo(2.0);
        }
    }

    @Test
    void aLaggingCursorCallsNothing() {
        try (ReconRig r = new ReconRig("RCN_LAG", "a1", ReconRig.a1(), AUTO_OFF).ingestAll()) {
            r.corruptLine("1");
            r.clock.now = r.clock.now.plusSeconds(901);   // 따라잡은 지 900초 넘음
            RunRecord run = done(r.run("202608", "202702"));
            assertThat(r.stub.calls()).isZero();
            assertThat(run.inconclusive()).isEqualTo(2);
            assertThat(r.reconResults()).isEmpty();
            assertThat(r.counter("recon_inconclusive_total", "tenant", r.tenant.value(), "reason", "LAGGING")).isEqualTo(2.0);
        }
    }

    @Test
    void aRunLockHeldByAnotherConnectionMeansNoRun() {
        try (ReconRig r = new ReconRig("RCN_LOCK", "a1", ReconRig.a1(), AUTO_OFF).ingestAll()) {
            ReconRunner.Outcome o = ReconRig.DB.asApp(r.tenant.value(), c -> {
                try (PreparedStatement ps = c.prepareStatement("SELECT pg_try_advisory_xact_lock(hashtext('portal.recon'), hashtext(?))")) {
                    ps.setString(1, r.tenant.value());
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        assertThat(rs.getBoolean(1)).isTrue();
                    }
                }
                return r.run("202608");
            });
            assertThat(o).isInstanceOf(ReconRunner.Outcome.Locked.class);
            assertThat(r.reconRuns()).isEmpty();
            assertThat(r.stub.calls()).isZero();
        }
    }

    private static void exec(String sql) {
        ReconRig.DB.asMigrator(c -> {
            try (Statement s = c.createStatement()) {
                s.execute(sql);
            }
            return null;
        });
    }
}
