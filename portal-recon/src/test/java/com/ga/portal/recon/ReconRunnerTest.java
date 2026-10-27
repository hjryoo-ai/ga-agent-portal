package com.ga.portal.recon;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.DataStatus;
import com.ga.portal.domain.IngestState;
import com.ga.portal.domain.MonthStatus;
import com.ga.portal.domain.NotificationType;
import com.ga.portal.domain.RebuildOutcome;
import com.ga.portal.domain.ReconRunResult;
import com.ga.portal.domain.ReconSource;
import com.ga.portal.domain.ReconTrigger;
import com.ga.portal.domain.trace.Tracing;
import com.ga.portal.domain.view.ReconRow;
import com.ga.portal.projection.notify.Candidate;
import com.ga.portal.recon.port.ReconEngine;
import com.ga.portal.recon.port.ReconLock;
import com.ga.portal.recon.port.ReconMetrics;
import com.ga.portal.recon.port.ReconRebuild;
import com.ga.portal.recon.port.ReconStore;
import com.ga.portal.recon.rule.CheckResult;
import com.ga.portal.recon.rule.CursorSnapshot;
import com.ga.portal.recon.rule.EngineAnswer;
import com.ga.portal.recon.rule.InconclusiveReason;
import com.ga.portal.recon.rule.PortalMonth;
import com.ga.portal.recon.rule.ReconMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계획 ③ R0~R6(C4·C5) — 포트를 메모리 가짜로. 저장의 트랜잭션 경계·RLS·결정론 바이트는 통합 시험({@code ReconRunIT}·{@code AutoRepairIT})이 본다.
 * 여기서는 회차의 순서와 규칙: 행은 DRIFT·REPAIRED만(Q13 같은 값 행 없음), 새 DRIFT·시도함(Q14), 하루 재구축 한 번, 재대사(R5)와 조건 A, P7-2, 결과 우선순위
 * (P-14), 예산·간격, 뒤처짐(호출 0), 잠금.
 */
class ReconRunnerTest {

    private static final TenantId T = TenantId.of("GA06");
    private static final AgentMonth AUG = new AgentMonth("A-1001", "202608");
    private static final AgentMonth FEB = new AgentMonth("A-1001", "202702");
    /** 2026-10-28 04:10 KST. */
    private static final Instant T0 = Instant.parse("2026-10-27T19:10:00Z");

    private MutableClock clock;
    private FakeStore store;
    private FakeEngine engine;
    private FakeRebuild rebuild;
    private FakeMetrics metrics;
    private boolean lockFree;
    private int pauses;
    private ReconRunner runner;
    private final RecordingTracing tracing = new RecordingTracing();

    /** 스팬 기록기 — 이름·부모·속성(포털 Phase 7, docs/지표.md §4). */
    static final class RecordingTracing implements Tracing {
        record Recorded(String name, String parent, Map<String, String> attrs) {
        }

        final List<Recorded> closed = new ArrayList<>();
        private final Deque<String> open = new ArrayDeque<>();

        @Override
        public Span start(String name) {
            String parent = open.isEmpty() ? "-" : open.peek();
            open.push(name);
            Map<String, String> attrs = new TreeMap<>();
            return new Span() {
                @Override
                public Span tenant(TenantId tenant) {
                    attrs.put("tenant", tenant.value());
                    return this;
                }

                @Override
                public Span count(String key, long value) {
                    attrs.put(key, Long.toString(value));
                    return this;
                }

                @Override
                public Span kind(String key, String value) {
                    attrs.put(key, Tracing.kind(value));
                    return this;
                }

                @Override
                public void close() {
                    assertThat(open.pop()).as("spans close innermost first").isEqualTo(name);
                    closed.add(new Recorded(name, parent, attrs));
                }
            };
        }
    }

    static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static final class FakeStore implements ReconStore {
        ReconParams params = new ReconParams(4, false, Duration.ofSeconds(900), Duration.ofMillis(200), 500, Duration.ofSeconds(900));
        CursorSnapshot cursor;
        final Map<AgentMonth, PortalMonth> months = new TreeMap<>();
        final Map<AgentMonth, DataStatus> dataStatus = new HashMap<>();
        final List<ReconRow> results = new ArrayList<>();
        final List<RunRecord> runs = new ArrayList<>();
        final Set<String> notifications = new HashSet<>();

        @Override
        public ReconParams params(TenantId tenant) {
            return params;
        }

        @Override
        public CursorSnapshot cursor(TenantId tenant) {
            return cursor;
        }

        @Override
        public Map<AgentMonth, PortalMonth> months(TenantId tenant, List<String> yms) {
            Map<AgentMonth, PortalMonth> out = new TreeMap<>();
            months.forEach((k, v) -> {
                if (yms.contains(k.ym())) {
                    out.put(k, v);
                }
            });
            return out;
        }

        @Override
        public Optional<PortalMonth> month(TenantId tenant, AgentMonth key) {
            return Optional.ofNullable(months.get(key));
        }

        @Override
        public List<ReconRow> results(TenantId tenant) {
            return List.copyOf(results);
        }

        @Override
        public List<Instant> rebuilds(TenantId tenant) {
            return runs.stream().flatMap(r -> r.rebuiltAt().stream()).toList();
        }

        @Override
        public void drift(TenantId tenant, AgentMonth key, Optional<ReconRow> row) {
            row.ifPresent(results::add);
            dataStatus.put(key, DataStatus.DRIFT);
        }

        @Override
        public Map<NotificationType, Integer> repaired(TenantId tenant, ReconRow row, List<Candidate> candidates) {
            results.add(row);
            dataStatus.put(new AgentMonth(row.agentId(), row.closeYm()), DataStatus.OK);
            Map<NotificationType, Integer> inserted = new EnumMap<>(NotificationType.class);
            for (Candidate c : candidates) {
                inserted.merge(c.type(), notifications.add(c.agentId() + "|" + c.dedupKey()) ? 1 : 0, Integer::sum);
            }
            return inserted;
        }

        @Override
        public void insertRun(TenantId tenant, RunRecord run) {
            runs.add(run);
        }

        @Override
        public void updateRun(TenantId tenant, RunRecord run) {
            runs.replaceAll(r -> r.runAt().equals(run.runAt()) ? run : r);
        }

        @Override
        public boolean scheduledRunOn(TenantId tenant, LocalDate kstDay) {
            return runs.stream().anyMatch(r -> r.trigger() == ReconTrigger.SCHEDULE && ReconRunner.kstDay(r.runAt()).equals(kstDay));
        }

        @Override
        public boolean rebuiltOn(TenantId tenant, LocalDate kstDay) {
            return runs.stream().flatMap(r -> r.rebuiltAt().stream()).anyMatch(t -> ReconRunner.kstDay(t).equals(kstDay));
        }

        List<ReconRow> rows(String status) {
            return results.stream().filter(r -> r.status().equals(status)).toList();
        }
    }

    static final class FakeEngine implements ReconEngine {
        final Map<AgentMonth, EngineAnswer> answers = new HashMap<>();
        long head;
        final List<AgentMonth> calls = new ArrayList<>();

        @Override
        public EngineAnswer statement(TenantId tenant, String agentId, String ym, ReconMode mode) {
            AgentMonth key = new AgentMonth(agentId, ym);
            calls.add(key);
            return answers.getOrDefault(key, EngineAnswer.unavailable());
        }

        @Override
        public OptionalLong feedHead(TenantId tenant, long afterSeq) {
            return OptionalLong.of(head);
        }
    }

    final class FakeRebuild implements ReconRebuild {
        Optional<RebuildOutcome> next = Optional.of(RebuildOutcome.SUCCEEDED);
        /** 성공한 재구축이 포털 달을 이 값으로 되돌린다(라인 누락 복구). */
        final Map<AgentMonth, PortalMonth> restores = new HashMap<>();
        final List<String> actors = new ArrayList<>();

        @Override
        public Optional<RebuildOutcome> rebuild(TenantId tenant, String actor) {
            actors.add(actor);
            clock.now = clock.now.plusSeconds(30);
            if (next.equals(Optional.of(RebuildOutcome.SUCCEEDED))) {
                store.months.putAll(restores);
                store.months.keySet().forEach(k -> store.dataStatus.put(k, DataStatus.OK));   // 재구축은 data_status를 지운다(DE-28)
            }
            return next;
        }
    }

    static final class FakeMetrics implements ReconMetrics {
        final Map<CheckResult, Integer> checks = new EnumMap<>(CheckResult.class);
        final Map<InconclusiveReason, Integer> reasons = new EnumMap<>(InconclusiveReason.class);
        final List<ReconRunResult> runs = new ArrayList<>();
        int unresolved = -1;
        final Map<NotificationType, Integer> derived = new EnumMap<>(NotificationType.class);

        @Override
        public void check(TenantId tenant, CheckResult result) {
            checks.merge(result, 1, Integer::sum);
        }

        @Override
        public void inconclusive(TenantId tenant, InconclusiveReason reason) {
            reasons.merge(reason, 1, Integer::sum);
        }

        @Override
        public void run(TenantId tenant, ReconRunResult result) {
            runs.add(result);
        }

        @Override
        public void unresolved(TenantId tenant, int count) {
            unresolved = count;
        }

        @Override
        public void notificationsDerived(TenantId tenant, NotificationType type, int inserted) {
            derived.merge(type, inserted, Integer::sum);
        }
    }

    private static PortalMonth paid(long gross) {
        return new PortalMonth(MonthStatus.PAID, gross, 0, 0);
    }

    private static EngineAnswer confirmed(long gross) {
        return new EngineAnswer.Confirmed(gross, 0, OptionalLong.of(0));
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        store = new FakeStore();
        store.cursor = new CursorSnapshot(IngestState.RUNNING, 11, Optional.of(T0.minusSeconds(30)));
        engine = new FakeEngine();
        engine.head = 11;
        rebuild = new FakeRebuild();
        metrics = new FakeMetrics();
        lockFree = true;
        runner = new ReconRunner(store, engine, tenant -> lockFree ? Optional.of(() -> { }) : Optional.empty(), rebuild, metrics,
                interval -> pauses++, clock, tracing);
        // A-1: 202608 지급완료 1,800,000, 202702 예상(환수 상계 −720,000)
        store.months.put(AUG, paid(1_800_000));
        store.months.put(FEB, new PortalMonth(MonthStatus.ESTIMATED, 0, -720_000, 0));
        engine.answers.put(AUG, confirmed(1_800_000));
        engine.answers.put(FEB, new EngineAnswer.Estimated(10, 0, -720_000, 0));
    }

    private RunRecord cli() {
        ReconRunner.Outcome o = runner.run(T, ReconTrigger.CLI, "cli:ops", List.of("202608", "202702"));
        assertThat(o).isInstanceOf(ReconRunner.Outcome.Done.class);
        return ((ReconRunner.Outcome.Done) o).run();
    }

    private void corruptAug() {
        store.months.put(AUG, paid(900_000));        // 라인 누락(하네스 corrupt와 같은 뜻)
        rebuild.restores.put(AUG, paid(1_800_000));  // 재구축이 이벤트로 되돌린다
    }

    @Test
    void matchingMonthsWriteNoRowAndTheRunIsOk() {
        RunRecord r = cli();
        assertThat(r.result()).isEqualTo(ReconRunResult.OK);
        assertThat(List.of(r.checked(), r.matched(), r.driftNew(), r.driftUnresolved(), r.inconclusive(), r.engineCalls())).containsExactly(2, 2, 0, 0, 0, 2);
        assertThat(store.results).isEmpty();
        assertThat(store.runs).hasSize(1);
        assertThat(rebuild.actors).isEmpty();
        assertThat(metrics.checks).containsEntry(CheckResult.MATCH, 2);
        assertThat(metrics.runs).containsExactly(ReconRunResult.OK);
        assertThat(metrics.unresolved).isZero();
        assertThat(pauses).as("an interval between two calls").isEqualTo(1);
    }

    @Test
    void aDriftWritesOneRowAndTheMonthIsDriftWhileAutoRebuildIsOff() {
        corruptAug();
        RunRecord r = cli();
        assertThat(r.result()).isEqualTo(ReconRunResult.DRIFT);
        assertThat(r.driftNew()).isEqualTo(1);
        assertThat(r.driftUnresolved()).isEqualTo(1);
        assertThat(r.rebuiltAt()).isEmpty();
        assertThat(store.rows("DRIFT")).singleElement().satisfies(row -> {
            assertThat(row.runAt()).as("run start (Q5)").isEqualTo(r.runAt());
            assertThat(row.source()).isEqualTo(ReconSource.BATCH);
            assertThat(row.sourceSeq()).isZero();
            assertThat(List.of(row.portalGross(), row.portalOffset(), row.portalAdjustment())).containsExactly(900_000L, 0L, 0L);
            assertThat(row.engineGross()).contains(1_800_000L);
            assertThat(row.engineNet()).as("net is never compared (Q3)").isEmpty();
        });
        assertThat(store.dataStatus).containsEntry(AUG, DataStatus.DRIFT);
        assertThat(rebuild.actors).isEmpty();

        clock.now = clock.now.plusSeconds(600);
        RunRecord again = cli();
        assertThat(store.rows("DRIFT")).as("same values — no new row (Q13)").hasSize(1);
        assertThat(again.driftNew()).as("not new (Q14)").isZero();
        assertThat(again.driftUnresolved()).isEqualTo(1);
        assertThat(metrics.checks).containsEntry(CheckResult.DRIFT, 2);
    }

    @Test
    void turningAutoRebuildOnRebuildsAnUntriedDriftAndTheRecheckRepairsIt() {
        corruptAug();
        cli();
        store.notifications.add("A-1001|STMT_CONFIRMED:202608");   // Phase 6 파생이 이미 넣었다
        store.notifications.add("A-1001|PAYOUT:202608");
        store.params = new ReconParams(4, true, Duration.ofSeconds(900), Duration.ofMillis(200), 500, Duration.ofSeconds(900));
        clock.now = clock.now.plusSeconds(600);
        RunRecord r = cli();
        assertThat(rebuild.actors).containsExactly("recon:GA06");
        assertThat(r.rebuildOutcome()).contains(RebuildOutcome.SUCCEEDED);
        assertThat(r.result()).isEqualTo(ReconRunResult.REPAIRED);
        assertThat(r.driftUnresolved()).isZero();
        assertThat(store.rows("REPAIRED")).singleElement().satisfies(row -> {
            assertThat(row.runAt()).as("recheck time (Q5)").isAfter(r.rebuiltAt().orElseThrow().minusNanos(1));
            assertThat(row.portalGross()).isEqualTo(1_800_000);
        });
        assertThat(store.dataStatus).containsEntry(AUG, DataStatus.OK);
        assertThat(metrics.derived).as("same keys — nothing new (Phase 6 Q1)")
                .containsEntry(NotificationType.STMT_CONFIRMED, 0).containsEntry(NotificationType.PAYOUT, 0);
        assertThat(r.engineCalls()).as("2 checks + 1 recheck").isEqualTo(3);
        assertThat(metrics.unresolved).isZero();
    }

    @Test
    void theRunSpansCarryKindsAndCountsOnly() {
        corruptAug();
        store.params = new ReconParams(4, true, Duration.ofSeconds(900), Duration.ofMillis(200), 500, Duration.ofSeconds(900));
        cli();
        assertThat(tracing.closed).extracting(r -> r.name() + "<" + r.parent())
                .containsExactly("recon.check<recon.run", "recon.check<recon.run", "recon.rebuild<recon.run", "recon.recheck<recon.run", "recon.run<-");
        assertThat(tracing.closed.getLast().attrs()).containsExactlyEntriesOf(new TreeMap<>(Map.of("checked", "2", "drift_new", "1", "tenant", "GA06",
                "trigger", "CLI")));
        assertThat(tracing.closed.get(0).attrs()).containsExactlyEntriesOf(new TreeMap<>(Map.of("mode", "CONFIRMED", "result", "DRIFT")));
        assertThat(tracing.closed.get(2).attrs()).containsExactlyEntriesOf(Map.of("outcome", "SUCCEEDED"));
        assertThat(tracing.closed.get(3).attrs()).containsExactlyEntriesOf(new TreeMap<>(Map.of("mode", "CONFIRMED", "result", "MATCH")));
        assertThat(tracing.closed).flatExtracting(r -> List.copyOf(r.attrs().values()))
                .noneMatch(v -> v.contains("A-1001") || v.contains("2026") || v.contains("2027") || v.contains("1800000") || v.contains("900000"));
    }

    @Test
    void aSpanKindIsAnEnumNameOrOther() {
        assertThat(List.of("CONFIRMED", "LimitLedgerRebased", "ENGINE_TIMEOUT")).allSatisfy(v -> assertThat(Tracing.kind(v)).isEqualTo(v));
        assertThat(List.of("A-1001", "202608", "홍길동", "a b", "", "x".repeat(3), "P".repeat(65))).allSatisfy(v -> assertThat(Tracing.kind(v)).isEqualTo("OTHER"));
        assertThat(Tracing.kind(null)).isEqualTo("OTHER");
    }

    @Test
    void aDriftTheRebuildCannotFixIsTriedAndNotRebuiltAgain() {
        store.params = new ReconParams(4, true, Duration.ofSeconds(900), Duration.ofMillis(200), 500, Duration.ofSeconds(900));
        store.months.put(AUG, paid(570_000));   // 스트림 자체의 불일치 — 재구축이 같은 값을 다시 만든다
        RunRecord first = cli();
        assertThat(rebuild.actors).hasSize(1);
        assertThat(first.result()).isEqualTo(ReconRunResult.DRIFT);
        assertThat(first.driftUnresolved()).isEqualTo(1);
        assertThat(store.dataStatus).as("the recheck rewrites DRIFT after the rebuild wiped it (P-6)").containsEntry(AUG, DataStatus.DRIFT);
        assertThat(store.rows("DRIFT")).as("recheck with the same values — no row").hasSize(1);

        clock.now = clock.now.plusSeconds(600);
        cli();
        assertThat(rebuild.actors).as("same day").hasSize(1);
        clock.now = clock.now.plus(Duration.ofDays(1));
        RunRecord nextDay = cli();
        assertThat(rebuild.actors).as("tried (Q14) — no daily rebuild of an unresolved DRIFT").hasSize(1);
        assertThat(nextDay.result()).isEqualTo(ReconRunResult.DRIFT);
    }

    @Test
    void aFailedRebuildFailsTheRunWithoutRecheck() {
        store.params = new ReconParams(4, true, Duration.ofSeconds(900), Duration.ofMillis(200), 500, Duration.ofSeconds(900));
        corruptAug();
        rebuild.next = Optional.of(RebuildOutcome.FAILED);
        RunRecord r = cli();
        assertThat(r.result()).isEqualTo(ReconRunResult.FAILED);
        assertThat(r.rebuildOutcome()).contains(RebuildOutcome.FAILED);
        assertThat(r.engineCalls()).as("no recheck").isEqualTo(2);
        assertThat(store.rows("REPAIRED")).isEmpty();
    }

    @Test
    void aRejectedRebuildIsNotAnAttempt() {
        store.params = new ReconParams(4, true, Duration.ofSeconds(900), Duration.ofMillis(200), 500, Duration.ofSeconds(900));
        corruptAug();
        rebuild.next = Optional.empty();
        RunRecord r = cli();
        assertThat(r.rebuiltAt()).isEmpty();
        assertThat(r.result()).isEqualTo(ReconRunResult.DRIFT);
        clock.now = clock.now.plusSeconds(600);
        cli();
        assertThat(rebuild.actors).as("still untried — tries again").hasSize(2);
    }

    @Test
    void aRecheckThatIsInconclusiveWritesNoRowButKeepsTheMonthDrift() {
        store.params = new ReconParams(4, true, Duration.ofSeconds(900), Duration.ofMillis(200), 2, Duration.ofSeconds(900));
        corruptAug();
        RunRecord r = cli();   // 예산 2 — 첫 판정 둘에 다 쓰고 재대사는 BUDGET
        assertThat(rebuild.actors).hasSize(1);
        assertThat(store.rows("REPAIRED")).isEmpty();
        assertThat(store.rows("DRIFT")).as("condition A — no row for an inconclusive recheck").hasSize(1);
        assertThat(store.dataStatus).containsEntry(AUG, DataStatus.DRIFT);
        assertThat(metrics.reasons).containsEntry(InconclusiveReason.BUDGET, 1);
        assertThat(r.result()).isEqualTo(ReconRunResult.DRIFT);
    }

    @Test
    void aLaggingCursorCallsNothingAndWritesNothing() {
        corruptAug();
        store.cursor = new CursorSnapshot(IngestState.RUNNING, 11, Optional.of(T0.minusSeconds(901)));
        RunRecord r = cli();
        assertThat(engine.calls).isEmpty();
        assertThat(r.inconclusive()).isEqualTo(2);
        assertThat(store.results).isEmpty();
        assertThat(metrics.reasons).containsEntry(InconclusiveReason.LAGGING, 2);
        assertThat(r.result()).isEqualTo(ReconRunResult.OK);
    }

    @Test
    void aConfirmedMismatchWithTheFeedAheadIsLaggingNotDrift() {
        corruptAug();
        engine.head = 12;
        RunRecord r = cli();
        assertThat(store.results).isEmpty();
        assertThat(r.driftNew()).isZero();
        assertThat(metrics.reasons).containsEntry(InconclusiveReason.LAGGING, 1);
    }

    @Test
    void aMatchOnAnUnresolvedMonthRepairsItWithoutARebuild() {
        corruptAug();
        cli();
        store.months.put(AUG, paid(1_800_000));   // 운영자 재구축이 고쳤다(재구축은 recon_run에 없다)
        clock.now = clock.now.plusSeconds(600);
        RunRecord r = cli();
        assertThat(rebuild.actors).isEmpty();
        assertThat(store.rows("REPAIRED")).hasSize(1);
        assertThat(store.dataStatus).containsEntry(AUG, DataStatus.OK);
        assertThat(r.result()).as("no rebuild in this run — OK, not REPAIRED (V9 CHECK)").isEqualTo(ReconRunResult.OK);
        assertThat(r.driftUnresolved()).isZero();
    }

    @Test
    void theBudgetStopsCallingTheEngine() {
        store.params = new ReconParams(4, false, Duration.ofSeconds(900), Duration.ofMillis(200), 1, Duration.ofSeconds(900));
        RunRecord r = cli();
        assertThat(engine.calls).hasSize(1);
        assertThat(r.engineCalls()).isEqualTo(1);
        assertThat(metrics.reasons).containsEntry(InconclusiveReason.BUDGET, 1);
    }

    @Test
    void aHeldLockMeansNoRun() {
        lockFree = false;
        assertThat(runner.run(T, ReconTrigger.CLI, "cli:ops", List.of("202608"))).isInstanceOf(ReconRunner.Outcome.Locked.class);
        assertThat(store.runs).isEmpty();
        assertThat(engine.calls).isEmpty();
    }

    @Test
    void theSchedulerRunsOncePerKstDayFromTheTenantHour() {
        clock.now = Instant.parse("2026-10-27T18:59:00Z");   // 03:59 KST
        assertThat(runner.runIfDue(T)).isEmpty();
        clock.now = Instant.parse("2026-10-27T19:00:00Z");   // 04:00 KST
        assertThat(runner.runIfDue(T)).isPresent();
        assertThat(store.runs).singleElement().satisfies(r -> {
            assertThat(r.trigger()).isEqualTo(ReconTrigger.SCHEDULE);
            assertThat(r.actor()).isEqualTo("recon:GA06");
        });
        clock.now = Instant.parse("2026-10-28T14:59:00Z");   // 23:59 KST 같은 날
        assertThat(runner.runIfDue(T)).isEmpty();
        clock.now = Instant.parse("2026-10-28T19:00:00Z");   // 다음 날 04:00
        assertThat(runner.runIfDue(T)).isPresent();
    }

    @Test
    void theWindowIsThisAndLastKstMonth() {
        clock.now = Instant.parse("2026-10-31T15:30:00Z");   // 2026-11-01 00:30 KST
        assertThat(runner.window()).containsExactly("202611", "202610");
    }
}
