package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.IngestErrorCode;
import com.ga.portal.domain.IngestState;
import com.ga.portal.infra.testing.SeedData;
import com.ga.portal.ingest.ContractSchemas;
import com.ga.portal.ingest.EnvelopeValidator;
import com.ga.portal.ingest.apply.IngestApplier;
import com.ga.portal.ingest.apply.IngestMetrics;
import com.ga.portal.ingest.apply.IngestWorker;
import com.ga.portal.projection.NoopNotificationDeriver;
import com.ga.portal.projection.testing.Envelopes;
import com.ga.portal.projection.testing.GoldenViews;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C4·C5(DB 쪽): 마감월 라인은 독 — 테넌트 FAILED, 그 이벤트는 롤백되어 뷰가 A-1 골든 그대로, FAILED 행이 남는다. 합계 불일치는 독이 아니다 —
 * 인제스트가 끝까지 가고 그 달 DRIFT·{@code recon_result(EVENT)} 한 행·지표 {@code projection_drift_total}.
 */
class ProjectionWorkerIT {

    private final IngestRig rig = new IngestRig();

    private static List<ObjectNode> a1() {
        return new ArrayList<>(Envelopes.nodes(Envelopes.a1()));
    }

    @Test
    void c4AClosedMonthLineStopsTheTenantAndLeavesTheViews() {
        TenantId t = TenantId.of(SeedData.uniqueTenant("CLOSED"));
        List<ObjectNode> stream = a1();
        ObjectNode late = stream.get(1).deepCopy();
        late.put("seq", 12).put("eventId", "5eed0000-0000-4000-8000-0000000000c4");
        ((ObjectNode) late.path("aggregate")).put("id", "99");
        ((ObjectNode) late.path("payload")).put("calcId", "99");   // 202608은 seq 7에서 PAID
        stream.add(late);
        IngestWorker.Round round = rig.worker(ViewRig.feed(t, stream), rig.applier(ViewRig.projector(rig.gateway)), 200).runOnce(t);
        assertThat(round.applied()).isEqualTo(11);
        assertThat(round.stoppedBy()).hasValueSatisfying(r -> assertThat(r.code()).contains(IngestErrorCode.PROJECTOR_ERROR));
        assertThat(rig.cursor(t).state()).isEqualTo(IngestState.FAILED);
        assertThat(rig.cursor(t).lastSeq()).isEqualTo(11);
        assertThat(rig.cursor(t).error()).hasValueSatisfying(e -> assertThat(e).startsWith("PROJECTOR_ERROR: CLOSED_MONTH seq 12 eventId "));
        assertThat(rig.events(t, "FAILED")).singleElement().satisfies(e -> assertThat(e.seq()).isEqualTo(12));
        GoldenViews.assertMatches("a1", ViewRig.views(t));   // 독 이벤트는 뷰에 흔적이 없다
    }

    @Test
    void c5ADriftingConfirmationDoesNotStopIngestion() {
        TenantId t = TenantId.of(SeedData.uniqueTenant("DRIFT"));
        List<ObjectNode> stream = a1();
        ((ObjectNode) stream.get(5).path("payload")).put("gross", 1_799_999);
        AtomicInteger drifts = new AtomicInteger();
        IngestMetrics metrics = new IngestMetrics() {
            @Override
            public void event(TenantId tenant, String result) {
            }

            @Override
            public void feedError(TenantId tenant) {
            }

            @Override
            public void lag(TenantId tenant, long lagSeq, long lagSeconds) {
            }

            @Override
            public void state(TenantId tenant, IngestState state) {
            }

            @Override
            public void drift(TenantId tenant) {
                drifts.incrementAndGet();
            }
        };
        IngestApplier applier = new IngestApplier(new EnvelopeValidator(ContractSchemas.get()), rig.store, rig.transactions, rig.lock,
                ViewRig.projector(rig.gateway), new NoopNotificationDeriver(), IngestRig.CLOCK);
        IngestWorker.Round round = new IngestWorker(ViewRig.feed(t, stream), rig.store, rig.transactions, applier, metrics, IngestRig.CLOCK, 200)
                .runOnce(t);
        assertThat(round.applied()).isEqualTo(11);
        assertThat(round.stoppedBy()).isEmpty();
        assertThat(rig.cursor(t).state()).isEqualTo(IngestState.RUNNING);
        assertThat(drifts).hasValue(1);
        var views = ViewRig.views(t);
        assertThat(views.get("recon_result")).singleElement().asString()
                .contains("\"source\":\"EVENT\"").contains("\"source_seq\":6").contains("\"engine_gross\":1799999").contains("\"portal_gross\":1800000");
        assertThat(views.get("earnings_month")).filteredOn(r -> r.contains("\"close_ym\":\"202608\"")).singleElement().asString()
                .contains("\"data_status\":\"DRIFT\"").contains("\"status\":\"PAID\"");
    }
}
