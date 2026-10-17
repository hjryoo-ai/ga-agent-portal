package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.IngestState;
import com.ga.portal.infra.persistence.AuditAccessRepository;
import com.ga.portal.infra.persistence.ViewResetRepository;
import com.ga.portal.infra.testing.SeedData;
import com.ga.portal.ingest.apply.IngestMetrics;
import com.ga.portal.ingest.apply.IngestWorker;
import com.ga.portal.ingest.feed.FeedClient;
import com.ga.portal.projection.Projector;
import com.ga.portal.projection.ViewProjector;
import com.ga.portal.projection.testing.Envelopes;
import com.ga.portal.projection.testing.GoldenViews;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * C10(Phase 2 지시문 D-11, 계획 ⑥·승인 Q10): 재구축은 세 트랜잭션 — txA {@code REBUILDING} 커밋, txB 뷰 삭제·재생·불변식·{@code RUNNING}
 * (한 트랜잭션), 실패면 txB 롤백 + txC {@code FAILED(REBUILD_FAILED)}. 실제 PostgreSQL(portal_app·RLS). 페이지 크기 3 — 실패가 첫 페이지
 * 밖에서 일어나야 페이지마다 커밋하는 구현(K7)이 잡힌다.
 */
class RebuildIT {

    private static final String ACTOR = "cli:ops-kim";
    private static final int PAGE = 3;

    private final IngestRig rig = new IngestRig();
    private final AuditAccessRepository audit = new AuditAccessRepository(rig.gateway);

    private static List<ObjectNode> a1() {
        return new ArrayList<>(Envelopes.nodes(Envelopes.a1()));
    }

    private ViewProjector projector() {
        return ViewRig.projector(rig.gateway);
    }

    /** 명령 조립 — 시험의 행위자를 그 테넌트의 OPERATOR로 등록한다(Phase 3 D-9; 같은 테넌트에 두 번 부르면 이미 있다). */
    private IngestCommands commands(TenantId t, FeedClient feed, Projector projector, long maxEvents) {
        IngestRig.DB.seed(t.value(), c -> SeedData.exec(c, """
                INSERT INTO identity_link (tenant_id, subject, agent_id, role, status, org_path, linked_at)
                VALUES (?, ?, NULL, 'OPERATOR', 'ACTIVE', NULL, TIMESTAMPTZ '2026-10-17 00:00:00+00') ON CONFLICT DO NOTHING
                """, t.value(), ACTOR));
        return new IngestCommands(rig.transactions, rig.store, rig.lock, audit, () -> List.of(t), rig.rebuilder(feed, projector, PAGE, maxEvents),
                IngestMetrics.NONE, IngestRig.CLOCK, rig.links);
    }

    private IngestCommands commands(TenantId t, FeedClient feed) {
        return commands(t, feed, projector(), 50_000);
    }

    /** 스트림을 끝까지 적용한 테넌트(커서 RUNNING·last_seq 11). */
    private TenantId ingested(String prefix, List<ObjectNode> stream) {
        TenantId t = TenantId.of(SeedData.uniqueTenant(prefix));
        IngestWorker.Round round = rig.worker(ViewRig.feed(t, stream), projector(), 200).runOnce(t);
        assertThat(round.applied()).isEqualTo(stream.size());
        assertThat(rig.cursor(t).state()).isEqualTo(IngestState.RUNNING);
        return t;
    }

    private List<AuditAccessRepository.AuditRow> audits(TenantId t) {
        return rig.transactions.inTransaction(t, () -> audit.recent(50)).reversed();
    }

    @Test
    void c10ARebuildReplaysTheStreamAndLeavesTheCursorRunningAtTheLastSeq() {
        List<ObjectNode> stream = a1();
        TenantId t = ingested("RB_OK", stream);
        List<String> events = rig.eventRows(t);
        IngestCommands cli = commands(t, ViewRig.feed(t, stream));
        assertThat(cli.pause(t, ACTOR).exitCode()).isZero();

        IngestCommands.Outcome outcome = cli.rebuild(t, ACTOR);

        assertThat(outcome.exitCode()).isZero();
        assertThat(outcome.lines()).containsExactly(t.value() + " RUNNING at seq 11 (rebuilt 11 events)");
        assertThat(rig.cursor(t).state()).isEqualTo(IngestState.RUNNING);
        assertThat(rig.cursor(t).lastSeq()).isEqualTo(11);
        assertThat(rig.cursor(t).lastEventId()).contains(stream.getLast().path("eventId").asString());
        assertThat(rig.cursor(t).error()).isEmpty();
        assertThat(rig.eventRows(t)).as("ingest_event is matched, not rewritten").isEqualTo(events);
        GoldenViews.assertMatches("a1", ViewRig.views(t));
        assertThat(audits(t)).extracting(AuditAccessRepository.AuditRow::action, AuditAccessRepository.AuditRow::target).containsExactly(
                tuple(IngestCommands.PAUSE, "cursor:" + t.value() + "@11"),
                tuple(IngestCommands.REBUILD, "cursor:" + t.value() + "@11"),
                tuple(IngestCommands.REBUILD_SUCCEEDED, "cursor:" + t.value() + "@11"));
    }

    @Test
    void c10AMidStreamFailureRollsBackAndLeavesTheOriginalRows() {
        List<ObjectNode> stream = a1();
        TenantId t = ingested("RB_FAIL", stream);
        Map<String, List<String>> before = ViewRig.views(t);
        List<String> events = rig.eventRows(t);
        // 피드가 seq 8의 payload를 바꿔 낸다 — 이미 APPLIED인 행과 해시가 다르다. seq 1~7은 셋째 페이지까지 이미 다시 투영됐다
        List<ObjectNode> changed = a1();
        ((ObjectNode) changed.get(7).path("payload")).put("occurredOn", "2027-02-16");

        IngestCommands.Outcome outcome = commands(t, ViewRig.feed(t, changed)).rebuild(t, ACTOR);

        assertThat(outcome.exitCode()).isEqualTo(1);
        assertThat(outcome.lines().getFirst()).contains("REBUILD_FAILED: HASH_MISMATCH: seq 8 eventId ").endsWith("views unchanged");
        assertThat(rig.cursor(t).state()).isEqualTo(IngestState.FAILED);
        assertThat(rig.cursor(t).lastSeq()).isEqualTo(11);
        assertThat(rig.cursor(t).error()).hasValueSatisfying(e -> assertThat(e).startsWith("REBUILD_FAILED: HASH_MISMATCH: seq 8 eventId "));
        assertThat(ViewRig.views(t)).as("views are the rows from before the rebuild").isEqualTo(before);
        assertThat(rig.eventRows(t)).isEqualTo(events);
        assertThat(audits(t)).extracting(AuditAccessRepository.AuditRow::action).containsExactly(
                IngestCommands.REBUILD, IngestCommands.REBUILD_FAILED);

        // FAILED에서 다시 — 원래 스트림이면 성공한다
        assertThat(commands(t, ViewRig.feed(t, stream)).rebuild(t, ACTOR).exitCode()).isZero();
        assertThat(rig.cursor(t).state()).isEqualTo(IngestState.RUNNING);
        GoldenViews.assertMatches("a1", ViewRig.views(t));
    }

    @Test
    void c10APoisonEventInTheReplayRollsBackToo() {
        List<ObjectNode> stream = a1();
        TenantId t = ingested("RB_POISON", stream);
        Map<String, List<String>> before = ViewRig.views(t);
        // 피드 끝에 새 이벤트 하나 — 마감월(202608은 seq 7에서 PAID)의 라인. 재생 끝에서 독, 그 전 11건은 이미 다시 썼다
        List<ObjectNode> longer = a1();
        ObjectNode late = longer.get(1).deepCopy();
        late.put("seq", 12).put("eventId", "5eed0000-0000-4000-8000-0000000000b1");
        ((ObjectNode) late.path("aggregate")).put("id", "99");
        ((ObjectNode) late.path("payload")).put("calcId", "99");
        longer.add(late);

        IngestCommands.Outcome outcome = commands(t, ViewRig.feed(t, longer)).rebuild(t, ACTOR);

        assertThat(outcome.exitCode()).isEqualTo(1);
        assertThat(rig.cursor(t).error()).hasValueSatisfying(e ->
                assertThat(e).startsWith("REBUILD_FAILED: PROJECTOR_ERROR: CLOSED_MONTH seq 12 eventId 5eed0000-0000-4000-8000-0000000000b1"));
        assertThat(ViewRig.views(t)).isEqualTo(before);
        assertThat(rig.events(t, "APPLIED")).hasSize(11);
        assertThat(rig.events(t, "FAILED")).as("a replay leaves no FAILED row — the transaction rolled back").isEmpty();
    }

    @Test
    void overLimitIsRefusedBeforeStarting() {
        List<ObjectNode> stream = a1();
        TenantId t = ingested("RB_CAP", stream);
        Map<String, List<String>> before = ViewRig.views(t);
        AtomicBoolean projected = new AtomicBoolean();
        Projector watching = (tenant, e) -> {
            projected.set(true);
            return projector().apply(tenant, e);
        };

        IngestCommands.Outcome outcome = commands(t, ViewRig.feed(t, stream), watching, 10).rebuild(t, ACTOR);

        assertThat(outcome.exitCode()).isEqualTo(1);
        assertThat(rig.cursor(t).error()).contains("REBUILD_FAILED: headSeq 11 is above the limit 10");
        assertThat(rig.cursor(t).state()).isEqualTo(IngestState.FAILED);
        assertThat(projected).as("nothing was replayed").isFalse();
        assertThat(ViewRig.views(t)).isEqualTo(before);
    }

    /** 재생 중(txB가 잠금을 쥐고 seq 5에서 멈춘 동안) 워커는 폴링하지 않고, 운영 명령은 거부되며, 조회는 옛 행을 본다(MVCC). */
    @Test
    void c10WorkersAndCommandsStayOutWhileTheReplayRuns() throws Exception {
        List<ObjectNode> stream = a1();
        TenantId t = ingested("RB_EXCL", stream);
        Map<String, List<String>> before = ViewRig.views(t);
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean once = new AtomicBoolean(true);
        ViewProjector base = projector();
        Projector gated = (tenant, e) -> {
            if (e.seq() == 5 && once.getAndSet(false)) {
                inside.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            return base.apply(tenant, e);
        };
        IngestCommands cli = commands(t, ViewRig.feed(t, stream), gated, 50_000);
        CompletableFuture<IngestCommands.Outcome> rebuild = CompletableFuture.supplyAsync(() -> cli.rebuild(t, ACTOR));
        assertThat(inside.await(30, TimeUnit.SECONDS)).isTrue();
        try {
            assertThat(rig.cursor(t).state()).as("txA committed").isEqualTo(IngestState.REBUILDING);
            IngestWorker.Round round = rig.worker(ViewRig.feed(t, stream), projector(), 200).runOnce(t);
            assertThat(round.polled()).as("a worker does not poll a REBUILDING tenant").isFalse();
            IngestCommands other = commands(t, ViewRig.feed(t, stream));
            assertThat(other.pause(t, ACTOR).lines().getFirst()).contains("being processed by a worker");
            assertThat(other.rebuild(t, ACTOR).lines().getFirst()).contains("being processed by a worker");
            assertThat(ViewRig.views(t)).as("readers see the old rows during the replay").isEqualTo(before);
        } finally {
            release.countDown();
        }
        assertThat(rebuild.get(30, TimeUnit.SECONDS).exitCode()).isZero();
        assertThat(rig.cursor(t).state()).isEqualTo(IngestState.RUNNING);
        GoldenViews.assertMatches("a1", ViewRig.views(t));
        assertThat(audits(t)).extracting(AuditAccessRepository.AuditRow::action).containsExactly(
                IngestCommands.REBUILD, IngestCommands.PAUSE_REJECTED, IngestCommands.REBUILD_REJECTED, IngestCommands.REBUILD_SUCCEEDED);
    }

    @Test
    void aRebuildIsRefusedForAREBUILDINGOrUnknownState() {
        List<ObjectNode> stream = a1();
        TenantId t = ingested("RB_STATE", stream);
        rig.transactions.inTransaction(t, () -> {
            var c = rig.store.cursor().orElseThrow();
            rig.store.saveCursor(c.lastSeq(), c.lastEventId(), IngestState.REBUILDING, java.util.Optional.empty(), IngestRig.CLOCK.instant());
            return null;
        });
        IngestCommands.Outcome outcome = commands(t, ViewRig.feed(t, stream)).rebuild(t, ACTOR);
        assertThat(outcome.exitCode()).isEqualTo(2);
        assertThat(outcome.lines().getFirst()).contains("is REBUILDING — only RUNNING, PAUSED or FAILED rebuilds");
        assertThat(audits(t)).extracting(AuditAccessRepository.AuditRow::action, AuditAccessRepository.AuditRow::target)
                .containsExactly(tuple(IngestCommands.REBUILD_REJECTED, "cursor:" + t.value() + "@11"));
    }

    /** 승인 Q2: {@code recon_result}는 지우지 않고 {@code ON CONFLICT DO NOTHING} — DRIFT 스트림을 두 번 재구축해도 한 행, 뷰 같음. */
    @Test
    void aDriftStreamRebuildsIdempotently() {
        List<ObjectNode> stream = a1();
        ((ObjectNode) stream.get(5).path("payload")).put("gross", 1_799_999);
        TenantId t = ingested("RB_DRIFT", stream);
        Map<String, List<String>> before = ViewRig.views(t);
        assertThat(before.get("recon_result")).hasSize(1);

        assertThat(commands(t, ViewRig.feed(t, stream)).rebuild(t, ACTOR).exitCode()).isZero();
        assertThat(commands(t, ViewRig.feed(t, stream)).rebuild(t, ACTOR).exitCode()).isZero();

        assertThat(ViewRig.views(t)).isEqualTo(before);
        assertThat(ViewRig.views(t).get("earnings_month")).filteredOn(r -> r.contains("\"close_ym\":\"202608\"")).singleElement().asString()
                .contains("\"data_status\":\"DRIFT\"");
    }

    /** 전수 불변식 문장(설계서 §6.7): 월 합계·원장 누적·전기 연쇄가 어긋난 행의 키를 돌려주고, 다른 테넌트의 행은 보지 않는다. */
    @Test
    void theInvariantQueriesNameTheBrokenRows() {
        TenantId t = ingested("RB_INV", a1());
        TenantId neighbour = ingested("RB_INV_N", a1());
        ViewResetRepository repository = new ViewResetRepository(rig.gateway);
        assertThat(rig.transactions.inTransaction(t, repository::violations)).isEmpty();

        IngestRig.DB.seed(t.value(), c -> {
            try (var st = c.createStatement()) {
                st.executeUpdate("UPDATE earnings_month SET gross = gross + 1 WHERE tenant_id = '" + t.value() + "' AND close_ym = '202608'");
                st.executeUpdate("UPDATE policy_limit SET accum_paid = accum_paid + 1 WHERE tenant_id = '" + t.value() + "'");
                st.executeUpdate("UPDATE policy_limit_history SET accum_after = accum_after + 1 WHERE tenant_id = '" + t.value() + "' AND source_seq = 4");
                st.executeUpdate("DELETE FROM earnings_month WHERE tenant_id = '" + t.value() + "' AND close_ym = '202702'");
            }
        });
        List<String> broken = rig.transactions.inTransaction(t, repository::violations);
        assertThat(broken).hasSize(5).anySatisfy(k -> assertThat(k).isEqualTo("earnings_month A-1001/202608"))
                .anySatisfy(k -> assertThat(k).isEqualTo("earnings_month A-1001/202702"))
                .anySatisfy(k -> assertThat(k).startsWith("policy_limit "))
                .anySatisfy(k -> assertThat(k).startsWith("policy_limit_history ").endsWith("@4"))
                .anySatisfy(k -> assertThat(k).startsWith("policy_limit_history ").endsWith("@5"));
        assertThat(rig.transactions.inTransaction(neighbour, repository::violations)).isEmpty();

        // 재구축이 고친다 — 불변식은 재생 뒤의 행으로 판정한다
        assertThat(commands(t, ViewRig.feed(t, a1())).rebuild(t, ACTOR).exitCode()).isZero();
        assertThat(rig.transactions.inTransaction(t, repository::violations)).isEmpty();
        GoldenViews.assertMatches("a1", ViewRig.views(t));
    }
}
