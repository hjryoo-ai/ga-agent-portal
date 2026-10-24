package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.IngestErrorCode;
import com.ga.portal.domain.IngestState;
import com.ga.portal.domain.notify.NotificationRecord;
import com.ga.portal.infra.testing.SeedData;
import com.ga.portal.ingest.apply.IngestWorker;
import com.ga.portal.projection.NotificationDeriver;
import com.ga.portal.projection.testing.Envelopes;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C2·C4(포털 Phase 6 ④): 실제 PostgreSQL에서 적용기 + 운영 파생기.
 * <ul>
 *   <li>A-1을 ① 한 번에 ② 6건 뒤 재시작(새 부품)으로 넣은 두 테넌트의 {@code notification}이 테넌트 열 밖에서 같다(키는 테넌트를 넣은 이름 기반 UUID라
 *       테넌트마다 다르고 식이 같다). 다시 폴링해도 행이 늘지 않는다. 종류는 계획의 손 계산 여섯.</li>
 *   <li>{@code edges}(GA04): 계획의 손 계산 여섯(원장 1의 FULL — 뒤의 하향 둘은 0, 원장 2의 WARN), DRIFT 달(202608)의 확정 알림 0.</li>
 *   <li>{@code ga05-limits}(GA05 — 계획 Q26): <b>한 원장</b>이 WARN → 환수로 OK → 다시 WARN이면 행이 둘(키의 seq가 다르다), 같은 단계 안의 전기는 0, 재산정은
 *       단계가 FULL로 올라도 {@code LIMIT_REBASE} 하나.</li>
 *   <li>파생기 예외(seq 5 — 행을 넣은 뒤): {@code APPLY_ERROR}, {@code FAILED} 행 없음, 그 seq의 뷰·알림·{@code ingest_event} 0, 커서는 4에서 정지 —
 *       뷰는 4건만 적용한 테넌트와 바이트가 같다.</li>
 * </ul>
 */
class NotificationDerivationIT {

    private final IngestRig rig = new IngestRig();

    static List<ObjectNode> a1() {
        return Envelopes.nodes(Envelopes.a1());
    }

    static TenantId tenant(String prefix) {
        TenantId t = TenantId.of(SeedData.uniqueTenant(prefix));
        IngestRig.seedTenant(t);
        return t;
    }

    /** 행 → 테넌트와 무관한 부분(키 열 뺌) + 키가 그 테넌트의 식인지. */
    static List<String> withoutId(TenantId t, List<String> rows) {
        return rows.stream().map(r -> {
            String[] f = r.split("\\|", -1);
            assertThat(f[0]).as("name-based id").isEqualTo(NotificationRecord.id(t.value(), f[1], f[4]).toString());
            return r.substring(f[0].length() + 1);
        }).toList();
    }

    @Test
    void theSameStreamGivesTheSameNotificationsWithOrWithoutARestart() {
        TenantId once = tenant("NTF_1");
        TenantId restarted = tenant("NTF_2");
        rig.derivingWorker(ViewRig.feed(once, a1()), 200).runOnce(once);
        rig.derivingWorker(ViewRig.feed(restarted, a1()), 6).runOnce(restarted);
        assertThat(rig.cursor(restarted).lastSeq()).isEqualTo(6);
        new IngestRig().derivingWorker(ViewRig.feed(restarted, a1()), 200).runOnce(restarted);

        List<String> rows = IngestRig.notificationRows(once);
        assertThat(rows).extracting(r -> r.split("\\|", -1)[2]).containsExactlyInAnyOrder("LIMIT_CUT", "LIMIT_FULL", "STMT_CONFIRMED", "PAYOUT",
                "POLICY_CANCEL", "CLAWBACK");
        assertThat(rows).allSatisfy(r -> assertThat(r).doesNotContain("POL-2026-0001").endsWith("|"));   // 증권번호 원문 없음, read_at 비어 있음
        assertThat(withoutId(restarted, IngestRig.notificationRows(restarted))).isEqualTo(withoutId(once, rows));

        // 다시 폴링 — 적용 0, 행 그대로
        assertThat(rig.derivingWorker(ViewRig.feed(once, a1()), 200).runOnce(once).applied()).isZero();
        assertThat(IngestRig.notificationRows(once)).isEqualTo(rows);
    }

    @Test
    void theEdgesStreamGivesTheHandCountAndADriftedMonthIsSilent() {
        TenantId t = tenant("NTF_E");
        Path edges = Envelopes.repoRoot().resolve("web/e2e/streams/edges.jsonl");
        rig.derivingWorker(ViewRig.feed(t, edges), 200).runOnce(t);
        assertThat(rig.cursor(t).state()).isEqualTo(IngestState.RUNNING);
        List<String> rows = IngestRig.notificationRows(t);
        assertThat(rows).extracting(r -> r.split("\\|", -1)[2]).containsExactlyInAnyOrder("LIMIT_CUT", "LIMIT_FULL", "STMT_CONFIRMED", "LIMIT_WARN",
                "DEFER_HELD", "DEFER_CANCELLED");
        assertThat(rows).extracting(r -> r.split("\\|", -1)[4]).contains("LIMIT_FULL:EDG-LEDGER-1:5", "LIMIT_WARN:EDG-LEDGER-2:16")
                .noneMatch(k -> k.equals("STMT_CONFIRMED:202608"));
    }

    @Test
    void oneLedgerRisingAgainAfterAClawbackGetsASecondRowAndARebaseGetsOnlyItsOwn() {
        TenantId t = tenant("NTF_G");
        Path ga05 = Envelopes.repoRoot().resolve("web/e2e/streams/ga05-limits.jsonl");
        rig.derivingWorker(ViewRig.feed(t, ga05), 200).runOnce(t);
        assertThat(rig.cursor(t).state()).isEqualTo(IngestState.RUNNING);
        assertThat(rig.cursor(t).lastSeq()).isEqualTo(12);
        List<String> rows = IngestRig.notificationRows(t);
        assertThat(rows).extracting(r -> r.split("\\|", -1)[4]).containsExactlyInAnyOrder("LIMIT_WARN:GA5-LEDGER-1:5", "CLAWBACK:GA5-C3",
                "LIMIT_WARN:GA5-LEDGER-1:9", "LIMIT_REBASE:GA5-LEDGER-1:12");
        assertThat(rows).extracting(r -> r.split("\\|", -1)[0]).doesNotHaveDuplicates();
        assertThat(rows).allSatisfy(r -> assertThat(r).doesNotContain("POL-GA05-0001"));
    }

    @Test
    void aDerivationFailureStopsTheTenantWithAnApplyErrorAndRollsBackTheWholeEvent() {
        TenantId failing = tenant("NTF_F");
        TenantId reference = tenant("NTF_R");
        JdbcNotificationDeriver real = rig.deriver();
        NotificationDeriver failingAt5 = (tenant, e, result) -> {
            real.derive(tenant, e, result);   // 행을 넣은 뒤 실패 — 그 행도 롤백되어야 한다
            if (e.seq() == 5) {
                throw new IllegalStateException("derivation broken");
            }
        };
        IngestWorker.Round round = rig.worker(ViewRig.feed(failing, a1()), rig.applier(ViewRig.projector(rig.gateway), failingAt5), 200).runOnce(failing);
        assertThat(round.applied()).isEqualTo(4);
        assertThat(rig.cursor(failing).lastSeq()).isEqualTo(4);
        assertThat(rig.cursor(failing).error()).hasValueSatisfying(err -> assertThat(err).startsWith(IngestErrorCode.APPLY_ERROR.name())
                .contains("IllegalStateException").doesNotContain("derivation broken"));
        assertThat(rig.events(failing, "FAILED")).as("an apply error writes no FAILED row").isEmpty();
        assertThat(rig.eventRows(failing)).hasSize(4);
        assertThat(IngestRig.notificationRows(failing)).extracting(r -> r.split("\\|", -1)[2]).containsExactly("LIMIT_CUT");

        rig.derivingWorker(ViewRig.feed(reference, a1()), 4).runOnce(reference);
        assertThat(ViewRig.views(failing)).as("views = the first four events only").isEqualTo(ViewRig.views(reference));
    }
}
