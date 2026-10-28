package com.ga.portal.recon.rule;

import com.ga.platform.core.testing.SeededCases;
import com.ga.portal.domain.IngestState;
import com.ga.portal.domain.MonthStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 계획 ②·C3: 판정 표의 칸마다, 전수 조합(포털 상태 4 × 엔진 결과 9 × 커서 3 × {@code basisSeq} 관계 3 × 머리 관계 2 × 예산 2), 시드 고정 임의 값.
 * 전수 시험의 신탁({@link #expected})은 표를 엔진 결과 쪽에서 다시 쓴 것이다 — 구현({@code ReconRules})은 단계 순서로 쓰였다. 엔진·머리 콜백의
 * 호출 수도 본다(1·2단계는 호출 0, 머리는 CONFIRMED 불일치에서만 한 번).
 */
class ReconRulesTest {

    private static final Instant START = Instant.parse("2026-10-27T19:00:00Z");   // KST 04:00
    private static final Duration WINDOW = Duration.ofSeconds(900);
    private static final long LAST = 10;
    private static final CursorSnapshot FRESH = new CursorSnapshot(IngestState.RUNNING, LAST, Optional.of(START.minusSeconds(60)));

    private static PortalMonth month(MonthStatus status) {
        return new PortalMonth(status, 1_800_000, -720_000, 5_000);
    }

    private record Calls(AtomicInteger engine, AtomicInteger head) {
        Calls() {
            this(new AtomicInteger(), new AtomicInteger());
        }
    }

    private static Verdict run(CursorSnapshot cursor, boolean budget, PortalMonth month, EngineAnswer answer, OptionalLong head, Calls calls) {
        return ReconRules.check(cursor, START, WINDOW, budget, month, () -> {
            calls.engine().incrementAndGet();
            return answer;
        }, () -> {
            calls.head().incrementAndGet();
            return head;
        });
    }

    private static Verdict run(MonthStatus status, EngineAnswer answer, long head) {
        return run(FRESH, true, month(status), answer, OptionalLong.of(head), new Calls());
    }

    private static Verdict lagging() {
        return Verdict.inconclusive(InconclusiveReason.LAGGING);
    }

    // ------------------------------------------------------------------ 표의 칸마다

    @Test
    void step1_aCursorThatIsNotCaughtUpIsLaggingWithoutCallingTheEngine() {
        for (CursorSnapshot c : List.of(
                new CursorSnapshot(IngestState.PAUSED, LAST, Optional.of(START)),
                new CursorSnapshot(IngestState.FAILED, LAST, Optional.of(START)),
                new CursorSnapshot(IngestState.REBUILDING, LAST, Optional.of(START)),
                new CursorSnapshot(IngestState.RUNNING, LAST, Optional.empty()),
                new CursorSnapshot(IngestState.RUNNING, LAST, Optional.of(START.minus(WINDOW).minusSeconds(1))))) {
            Calls calls = new Calls();
            assertThat(run(c, true, month(MonthStatus.PAID), new EngineAnswer.Confirmed(1, 1, OptionalLong.empty()), OptionalLong.of(LAST), calls))
                    .as(c.toString()).isEqualTo(lagging());
            assertThat(calls.engine()).hasValue(0);
        }
    }

    @Test
    void step1_theWindowBoundaryIsInclusive() {
        CursorSnapshot edge = new CursorSnapshot(IngestState.RUNNING, LAST, Optional.of(START.minus(WINDOW)));
        assertThat(ReconRules.lagging(edge, START, WINDOW)).isFalse();
        assertThat(ReconRules.lagging(new CursorSnapshot(IngestState.RUNNING, LAST, Optional.of(START.plusSeconds(5))), START, WINDOW))
                .as("caught up after the run started (clock order)").isFalse();
    }

    @Test
    void step2_anExhaustedBudgetCallsNothing() {
        Calls calls = new Calls();
        assertThat(run(FRESH, false, month(MonthStatus.CONFIRMED), EngineAnswer.unavailable(), OptionalLong.of(LAST), calls))
                .isEqualTo(Verdict.inconclusive(InconclusiveReason.BUDGET));
        assertThat(calls.engine()).hasValue(0);
        assertThat(calls.head()).hasValue(0);
    }

    @Test
    void step3_theModeFollowsTheMonthStatus() {
        assertThat(ReconMode.of(MonthStatus.PROVISIONAL)).isEqualTo(ReconMode.ESTIMATE);
        assertThat(ReconMode.of(MonthStatus.ESTIMATED)).isEqualTo(ReconMode.ESTIMATE);
        assertThat(ReconMode.of(MonthStatus.CONFIRMED)).isEqualTo(ReconMode.CONFIRMED);
        assertThat(ReconMode.of(MonthStatus.PAID)).isEqualTo(ReconMode.CONFIRMED);
    }

    @Test
    void step3_engineFailuresAndShapeMismatchesAreInconclusive() {
        assertThat(run(MonthStatus.PAID, EngineAnswer.problem(404, "AGENT_NOT_FOUND"), LAST)).isEqualTo(Verdict.inconclusive(InconclusiveReason.AGENT_NOT_FOUND));
        assertThat(run(MonthStatus.PAID, EngineAnswer.problem(404, "STATEMENT_NOT_CONFIRMED"), LAST)).isEqualTo(Verdict.inconclusive(InconclusiveReason.NOT_CONFIRMED));
        assertThat(run(MonthStatus.ESTIMATED, EngineAnswer.problem(422, "MONTH_CLOSED"), LAST)).isEqualTo(Verdict.inconclusive(InconclusiveReason.MONTH_CLOSED));
        assertThat(run(MonthStatus.PAID, EngineAnswer.problem(400, "INVALID_REQUEST"), LAST)).isEqualTo(Verdict.inconclusive(InconclusiveReason.ENGINE_REJECTED));
        assertThat(run(MonthStatus.PAID, EngineAnswer.unavailable(), LAST)).isEqualTo(Verdict.inconclusive(InconclusiveReason.ENGINE_UNAVAILABLE));
        assertThat(run(MonthStatus.PAID, EngineAnswer.contractViolation(), LAST)).isEqualTo(Verdict.inconclusive(InconclusiveReason.ENGINE_CONTRACT));
        assertThat(run(MonthStatus.PAID, new EngineAnswer.Estimated(LAST, 1_800_000, -720_000, 5_000), LAST))
                .as("ESTIMATE shape for a CONFIRMED request").isEqualTo(Verdict.inconclusive(InconclusiveReason.ENGINE_CONTRACT));
        assertThat(run(MonthStatus.PROVISIONAL, new EngineAnswer.Confirmed(1_800_000, 5_000, OptionalLong.empty()), LAST))
                .as("CONFIRMED shape for an ESTIMATE request").isEqualTo(Verdict.inconclusive(InconclusiveReason.ENGINE_CONTRACT));
    }

    @Test
    void problemCodesMapToTheirReasons() {
        assertThat(EngineAnswer.problem(404, "OTHER")).isEqualTo(new EngineAnswer.Failed(InconclusiveReason.ENGINE_REJECTED));
        assertThat(EngineAnswer.problem(422, "AGENT_NOT_FOUND")).as("code under the wrong status").isEqualTo(new EngineAnswer.Failed(InconclusiveReason.ENGINE_REJECTED));
        assertThat(EngineAnswer.problem(401, "UNAUTHORIZED")).isEqualTo(new EngineAnswer.Failed(InconclusiveReason.ENGINE_UNAVAILABLE));
        assertThat(EngineAnswer.problem(403, null)).isEqualTo(new EngineAnswer.Failed(InconclusiveReason.ENGINE_UNAVAILABLE));
        assertThat(EngineAnswer.problem(500, null)).isEqualTo(new EngineAnswer.Failed(InconclusiveReason.ENGINE_UNAVAILABLE));
        assertThat(EngineAnswer.problem(503, "X")).isEqualTo(new EngineAnswer.Failed(InconclusiveReason.ENGINE_UNAVAILABLE));
        assertThat(EngineAnswer.problem(302, null)).isEqualTo(new EngineAnswer.Failed(InconclusiveReason.ENGINE_CONTRACT));
        assertThat(EngineAnswer.problem(200, null)).isEqualTo(new EngineAnswer.Failed(InconclusiveReason.ENGINE_CONTRACT));
        assertThatThrownBy(() -> new EngineAnswer.Failed(InconclusiveReason.LAGGING)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EngineAnswer.Failed(InconclusiveReason.ESTIMATE_DIFF)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void step4_anEstimateAheadOfTheFixedCursorIsLagging() {
        assertThat(run(MonthStatus.ESTIMATED, new EngineAnswer.Estimated(LAST + 1, 0, -720_000, 5_000), LAST)).isEqualTo(lagging());
        assertThat(run(MonthStatus.ESTIMATED, new EngineAnswer.Estimated(LAST, 0, -720_000, 5_000), LAST)).isEqualTo(Verdict.MATCH);
    }

    @Test
    void step5_anEstimateComparesOffsetAndAdjustmentButNotGross() {
        assertThat(run(MonthStatus.PROVISIONAL, new EngineAnswer.Estimated(3, 2_178_000, -720_000, 5_000), LAST))
                .as("gross differs (deferral preview — Q1 b')").isEqualTo(Verdict.MATCH);
        assertThat(run(MonthStatus.PROVISIONAL, new EngineAnswer.Estimated(3, 1_800_000, -720_001, 5_000), LAST))
                .isEqualTo(Verdict.inconclusive(InconclusiveReason.ESTIMATE_DIFF));
        assertThat(run(MonthStatus.PROVISIONAL, new EngineAnswer.Estimated(3, 1_800_000, -720_000, 4_999), LAST))
                .isEqualTo(Verdict.inconclusive(InconclusiveReason.ESTIMATE_DIFF));
    }

    @Test
    void step6_aConfirmedMatchDoesNotReadTheHead() {
        Calls calls = new Calls();
        assertThat(run(FRESH, true, month(MonthStatus.PAID), new EngineAnswer.Confirmed(1_800_000, 5_000, OptionalLong.of(-720_000)),
                OptionalLong.of(LAST + 5), calls)).isEqualTo(Verdict.MATCH);
        assertThat(calls.engine()).hasValue(1);
        assertThat(calls.head()).hasValue(0);
        assertThat(run(MonthStatus.CONFIRMED, new EngineAnswer.Confirmed(1_800_000, 5_000, OptionalLong.empty()), LAST))
                .as("clawbackOffset absent — not compared").isEqualTo(Verdict.MATCH);
    }

    @Test
    void step7_and8_aConfirmedMismatchReadsTheHeadOnce() {
        for (long engineGross : new long[] {1_799_999, 1_800_001}) {
            Calls behind = new Calls();
            assertThat(run(FRESH, true, month(MonthStatus.PAID), new EngineAnswer.Confirmed(engineGross, 5_000, OptionalLong.empty()),
                    OptionalLong.of(LAST + 1), behind)).isEqualTo(lagging());
            assertThat(behind.head()).hasValue(1);
            Calls caughtUp = new Calls();
            assertThat(run(FRESH, true, month(MonthStatus.PAID), new EngineAnswer.Confirmed(engineGross, 5_000, OptionalLong.empty()),
                    OptionalLong.of(LAST), caughtUp)).isEqualTo(Verdict.DRIFT);
            assertThat(caughtUp.head()).hasValue(1);
        }
        assertThat(run(MonthStatus.CONFIRMED, new EngineAnswer.Confirmed(1_800_000, 5_001, OptionalLong.empty()), LAST)).isEqualTo(Verdict.DRIFT);
        assertThat(run(MonthStatus.CONFIRMED, new EngineAnswer.Confirmed(1_800_000, 5_000, OptionalLong.of(0)), LAST)).isEqualTo(Verdict.DRIFT);
        assertThat(run(FRESH, true, month(MonthStatus.PAID), new EngineAnswer.Confirmed(1, 5_000, OptionalLong.empty()), OptionalLong.empty(), new Calls()))
                .as("head unreadable").isEqualTo(Verdict.inconclusive(InconclusiveReason.ENGINE_UNAVAILABLE));
    }

    // ------------------------------------------------------------------ 전수 조합

    enum Engine { SAME, DIFFERENT, OTHER_SHAPE, AGENT_NOT_FOUND, NOT_CONFIRMED, MONTH_CLOSED, REJECTED, UNAVAILABLE, CONTRACT }

    enum Cursor { FRESH, STALE, STOPPED }

    enum Rel { BEHIND, EQUAL, AHEAD }

    static Stream<Arguments> grid() {
        List<Arguments> out = new ArrayList<>();
        for (MonthStatus s : MonthStatus.values()) {
            for (Engine e : Engine.values()) {
                for (Cursor c : Cursor.values()) {
                    for (Rel basis : Rel.values()) {
                        for (Rel head : List.of(Rel.EQUAL, Rel.AHEAD)) {
                            for (boolean budget : new boolean[] {true, false}) {
                                out.add(Arguments.of(s, e, c, basis, head, budget));
                            }
                        }
                    }
                }
            }
        }
        return out.stream();
    }

    @Test
    void theGridIsComplete() {
        assertThat(grid()).hasSize(4 * 9 * 3 * 3 * 2 * 2);
    }

    private static long seq(Rel r) {
        return switch (r) {
            case BEHIND -> LAST - 1;
            case EQUAL -> LAST;
            case AHEAD -> LAST + 1;
        };
    }

    private static EngineAnswer answer(MonthStatus s, Engine e, Rel basis) {
        PortalMonth m = month(s);
        boolean estimate = !s.isClosed();
        EngineAnswer same = estimate ? new EngineAnswer.Estimated(seq(basis), m.gross(), m.clawbackOffset(), m.adjustment())
                : new EngineAnswer.Confirmed(m.gross(), m.adjustment(), OptionalLong.of(m.clawbackOffset()));
        EngineAnswer different = estimate ? new EngineAnswer.Estimated(seq(basis), m.gross(), m.clawbackOffset(), 0)
                : new EngineAnswer.Confirmed(m.gross(), 0, OptionalLong.of(m.clawbackOffset()));
        EngineAnswer other = estimate ? new EngineAnswer.Confirmed(m.gross(), m.adjustment(), OptionalLong.empty())
                : new EngineAnswer.Estimated(seq(basis), m.gross(), m.clawbackOffset(), m.adjustment());
        return switch (e) {
            case SAME -> same;
            case DIFFERENT -> different;
            case OTHER_SHAPE -> other;
            case AGENT_NOT_FOUND -> EngineAnswer.problem(404, "AGENT_NOT_FOUND");
            case NOT_CONFIRMED -> EngineAnswer.problem(404, "STATEMENT_NOT_CONFIRMED");
            case MONTH_CLOSED -> EngineAnswer.problem(422, "MONTH_CLOSED");
            case REJECTED -> EngineAnswer.problem(400, "INVALID_REQUEST");
            case UNAVAILABLE -> EngineAnswer.unavailable();
            case CONTRACT -> EngineAnswer.contractViolation();
        };
    }

    /** 신탁 — 엔진 결과 쪽에서 쓴 표. */
    private static Verdict expected(MonthStatus s, Engine e, Cursor c, Rel basis, Rel head, boolean budget) {
        if (c != Cursor.FRESH) {
            return lagging();
        }
        if (!budget) {
            return Verdict.inconclusive(InconclusiveReason.BUDGET);
        }
        boolean estimate = s == MonthStatus.PROVISIONAL || s == MonthStatus.ESTIMATED;
        return switch (e) {
            case AGENT_NOT_FOUND -> Verdict.inconclusive(InconclusiveReason.AGENT_NOT_FOUND);
            case NOT_CONFIRMED -> Verdict.inconclusive(InconclusiveReason.NOT_CONFIRMED);
            case MONTH_CLOSED -> Verdict.inconclusive(InconclusiveReason.MONTH_CLOSED);
            case REJECTED -> Verdict.inconclusive(InconclusiveReason.ENGINE_REJECTED);
            case UNAVAILABLE -> Verdict.inconclusive(InconclusiveReason.ENGINE_UNAVAILABLE);
            case CONTRACT, OTHER_SHAPE -> Verdict.inconclusive(InconclusiveReason.ENGINE_CONTRACT);
            case SAME -> estimate && basis == Rel.AHEAD ? lagging() : Verdict.MATCH;
            case DIFFERENT -> estimate
                    ? (basis == Rel.AHEAD ? lagging() : Verdict.inconclusive(InconclusiveReason.ESTIMATE_DIFF))
                    : (head == Rel.AHEAD ? lagging() : Verdict.DRIFT);
        };
    }

    @ParameterizedTest
    @MethodSource("grid")
    void everyCombination(MonthStatus s, Engine e, Cursor c, Rel basis, Rel head, boolean budget) {
        CursorSnapshot cursor = switch (c) {
            case FRESH -> FRESH;
            case STALE -> new CursorSnapshot(IngestState.RUNNING, LAST, Optional.of(START.minus(WINDOW).minusMillis(1)));
            case STOPPED -> new CursorSnapshot(IngestState.PAUSED, LAST, Optional.of(START));
        };
        Calls calls = new Calls();
        Verdict v = run(cursor, budget, month(s), answer(s, e, basis), OptionalLong.of(seq(head)), calls);
        assertThat(v).isEqualTo(expected(s, e, c, basis, head, budget));
        boolean called = c == Cursor.FRESH && budget;
        assertThat(calls.engine().get()).as("engine calls").isEqualTo(called ? 1 : 0);
        boolean headRead = called && e == Engine.DIFFERENT && s.isClosed();
        assertThat(calls.head().get()).as("head reads").isEqualTo(headRead ? 1 : 0);
    }

    // ------------------------------------------------------------------ 시드 고정 임의 값

    static Stream<Arguments> randomMonths() {
        return SeededCases.of(0x5EED_0B07L, r -> new Object[] {
                MonthStatus.values()[r.nextInt(4)], r.nextLong(0, 50_000_000), r.nextLong(-10_000_000, 1), r.nextLong(-1_000_000, 1_000_000),
                r.nextInt(4), r.nextLong(1, 1_000_000)});
    }

    /** 같으면 MATCH, 한 필드라도 다르면 표 그대로(ESTIMATE의 gross는 예외 — 보지 않는다). {@code field} 0 = 없음, 1 gross, 2 offset, 3 adjustment. */
    @ParameterizedTest
    @MethodSource("randomMonths")
    void sameValuesMatchAndOneDifferentFieldFollowsTheTable(MonthStatus s, long gross, long offset, long adjustment, int field, long delta) {
        PortalMonth m = new PortalMonth(s, gross, offset, adjustment);
        long g = field == 1 ? Math.addExact(gross, delta) : gross;
        long o = field == 2 ? Math.addExact(offset, delta) : offset;
        long a = field == 3 ? Math.addExact(adjustment, delta) : adjustment;
        EngineAnswer answer = s.isClosed() ? new EngineAnswer.Confirmed(g, a, OptionalLong.of(o)) : new EngineAnswer.Estimated(LAST, g, o, a);
        Verdict v = run(FRESH, true, m, answer, OptionalLong.of(LAST), new Calls());
        Verdict want;
        if (field == 0 || (field == 1 && !s.isClosed())) {
            want = Verdict.MATCH;
        } else {
            want = s.isClosed() ? Verdict.DRIFT : Verdict.inconclusive(InconclusiveReason.ESTIMATE_DIFF);
        }
        assertThat(v).isEqualTo(want);
    }
}
