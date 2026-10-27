package com.ga.portal.app.recon;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.app.ingest.IngestCommands;
import com.ga.portal.infra.testing.SeedData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포털 Phase 7 C10(계획 ⑤·Q7, 승인 조건 B): 운영 {@code status}·{@code recon run}을 실제 인가·감사·저장으로. 출력은 수·상태·시각뿐 — 설계사 ID·달·금액 없음
 * (DRIFT 달이 있어도 나이로만). {@code OPERATOR} 링크가 없으면 거부(2), {@code --all}에 거부가 섞이면 그 줄과 종료 2.
 */
class StatusCliIT {

    private static final Pattern SIX_DIGITS = Pattern.compile("\\d{6}");

    @Test
    void statusShowsCountsAndAgesOnly() {
        try (ReconRig r = new ReconRig("RCN_ST", "a1", ReconRig.a1(), "{\"auto_rebuild_enabled\": false}").ingestAll()) {
            r.corruptLine("1");
            IngestCommands.Outcome run = r.commands().run(r.tenant, List.of("202608", "202702"), ReconRig.OPERATOR);
            assertThat(run.exitCode()).isZero();
            assertThat(run.lines()).containsExactly(r.tenant.value() + " recon DRIFT checked=2 matched=1 drift_new=1 drift_unresolved=1 inconclusive=0 engine_calls=2");

            IngestCommands.Outcome status = r.commands().status(Optional.of(r.tenant), ReconRig.OPERATOR);
            assertThat(status.exitCode()).isZero();
            assertThat(status.lines()).singleElement().satisfies(line -> {
                assertThat(line).startsWith(r.tenant.value() + " cursor=RUNNING last_seq=11 lag_seq=0 caught_up_at=2026-10-17T02:00:00Z lag_s=0 ")
                        .contains(" drift_unresolved=1 drift_oldest_days=0 last_run=2026-10-17T02:00:00Z/DRIFT last_rebuild=- inquiries_open=0");
                assertThat(line).doesNotContain("A-1001").doesNotContain("855000").doesNotContain("1800000");
                assertThat(SIX_DIGITS.matcher(line.substring(r.tenant.value().length())).find()).as("no ym (condition B) — after the tenant id").isFalse();
            });
            assertThat(r.audits(ReconRig.OPERATOR, ReconCommands.STATUS)).isEqualTo(1);
            assertThat(r.audits(ReconRig.OPERATOR, ReconCommands.RUN)).isEqualTo(1);
        }
    }

    @Test
    void aSubjectWithoutAnOperatorLinkIsRejected() {
        try (ReconRig r = new ReconRig("RCN_STX", "a1", ReconRig.a1(), "{}").ingestAll()) {
            IngestCommands.Outcome status = r.commands().status(Optional.of(r.tenant), "cli:stranger");
            assertThat(status.exitCode()).isEqualTo(2);
            assertThat(status.lines()).containsExactly(r.tenant.value() + " not authorized");
            IngestCommands.Outcome run = r.commands().run(r.tenant, List.of("202608"), "cli:stranger");
            assertThat(run.exitCode()).isEqualTo(2);
            assertThat(r.reconRuns()).as("no run without authorization").isEmpty();
            assertThat(r.audits("cli:stranger", "AUTHZ_DENIED")).isEqualTo(2);
        }
    }

    @Test
    void allMixesAuthorizedAndRejectedTenantsAndExitsTwo() {
        TenantId other = TenantId.of(SeedData.uniqueTenant("RCN_STO"));
        com.ga.portal.app.ingest.IngestRigAccess.seedTenant(other, "http://127.0.0.1:1", "{}");
        try (ReconRig r = new ReconRig("RCN_STA", "a1", ReconRig.a1(), "{}").ingestAll()) {
            IngestCommands.Outcome status = r.commands(List.of(r.tenant, other)).status(Optional.empty(), ReconRig.OPERATOR);
            assertThat(status.exitCode()).isEqualTo(2);
            assertThat(status.lines()).hasSize(2);
            assertThat(status.lines().get(0)).startsWith(r.tenant.value() + " cursor=RUNNING");
            assertThat(status.lines().get(1)).isEqualTo(other.value() + " not authorized");
        }
    }
}
