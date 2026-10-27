package com.ga.portal.recon;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.NotificationType;
import com.ga.portal.domain.RebuildOutcome;
import com.ga.portal.domain.ReconRunResult;
import com.ga.portal.domain.ReconSource;
import com.ga.portal.domain.ReconTrigger;
import com.ga.portal.domain.view.ReconRow;
import com.ga.portal.projection.notify.NotificationRules;
import com.ga.portal.recon.Episodes.Episode;
import com.ga.portal.recon.port.Pacer;
import com.ga.portal.recon.port.ReconEngine;
import com.ga.portal.recon.port.ReconLock;
import com.ga.portal.recon.port.ReconMetrics;
import com.ga.portal.recon.port.ReconRebuild;
import com.ga.portal.recon.port.ReconStore;
import com.ga.portal.recon.rule.CursorSnapshot;
import com.ga.portal.recon.rule.EngineAnswer;
import com.ga.portal.recon.rule.PortalMonth;
import com.ga.portal.recon.rule.ReconMode;
import com.ga.portal.recon.rule.ReconRules;
import com.ga.portal.recon.rule.Verdict;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

/**
 * 대사 회차(설계서 §6.6, 계획 ③ R0~R6 — C4·C5). 스케줄러와 CLI {@code recon run}이 같은 진입점({@link #run})을 부른다.
 * <ol start="0">
 *   <li>R0 회차 잠금(못 잡으면 회차 없음), 커서 고정, 대상 = 대상 달의 {@code earnings_month} 행 전부</li>
 *   <li>R1·R2 설계사×달마다 판정({@link ReconRules}) — DRIFT면 그 달 한 트랜잭션(행 + {@code data_status}), 미해결 DRIFT 달이 MATCH면 그 자리에서 풀고(P7-2),
 *       나머지는 쓰기 0</li>
 *   <li>R3 회차 행(재구축 전 임시 결과)</li>
 *   <li>R4 조건(새 DRIFT 또는 시도 안 한 미해결, 자동 켬, 오늘 재구축 0 — Q14)이면 재구축(행위자 {@code recon:<tenant>})</li>
 *   <li>R5 재구축이 성공했으면 미해결 달만 다시 판정 — MATCH는 REPAIRED + OK + 같은 키 알림, 그 밖은 {@code data_status = DRIFT}(P-6). INCONCLUSIVE면 행 0(조건 A)</li>
 *   <li>R6 회차 결과 확정(P-14 우선순위), 미해결 게이지</li>
 * </ol>
 * 금액은 판정 함수가 {@code ==}로만 본다. 이 클래스는 금액을 읽어 옮길 뿐이다(행의 포털 셋·엔진 셋 — 차이 값 0, P-15).
 */
public final class ReconRunner {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    /** 회차의 결과. */
    public sealed interface Outcome {
        /** 다른 회차가 잠금을 쥐고 있다 — 이 회차는 없다. */
        record Locked() implements Outcome {
        }

        record Done(RunRecord run) implements Outcome {
        }
    }

    private final ReconStore store;
    private final ReconEngine engine;
    private final ReconLock lock;
    private final ReconRebuild rebuild;
    private final ReconMetrics metrics;
    private final Pacer pacer;
    private final Clock clock;

    public ReconRunner(ReconStore store, ReconEngine engine, ReconLock lock, ReconRebuild rebuild, ReconMetrics metrics, Pacer pacer, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lock = Objects.requireNonNull(lock, "lock");
        this.rebuild = Objects.requireNonNull(rebuild, "rebuild");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.pacer = Objects.requireNonNull(pacer, "pacer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** 스케줄러의 창 — {당월, 직전 달}(KST, 계획 Q12). */
    public List<String> window() {
        YearMonth now = YearMonth.from(clock.instant().atZone(KST));
        return List.of(now.format(YM), now.minusMonths(1).format(YM));
    }

    /** 스케줄러 틱(계획 Q9): KST 시각이 그 테넌트의 {@code recon_hour_kst} 이상이고 오늘 스케줄 회차가 없으면 창으로 한 회차. */
    public Optional<Outcome> runIfDue(TenantId tenant) {
        Instant now = clock.instant();
        if (now.atZone(KST).getHour() < store.params(tenant).reconHourKst() || store.scheduledRunOn(tenant, kstDay(now))) {
            return Optional.empty();
        }
        return Optional.of(run(tenant, ReconTrigger.SCHEDULE, "recon:" + tenant.value(), window()));
    }

    public Outcome run(TenantId tenant, ReconTrigger trigger, String actor, List<String> months) {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(actor, "actor");
        Optional<ReconLock.Held> held = lock.tryLock(tenant);
        if (held.isEmpty()) {
            return new Outcome.Locked();
        }
        try {
            return new Outcome.Done(runLocked(tenant, trigger, actor, List.copyOf(months)));
        } finally {
            held.get().close();
        }
    }

    /** 회차 예산(R1) — 호출 수와 회차 시작부터의 시간. */
    private final class Budget {
        private final Instant start;
        private final ReconParams params;
        private int calls;

        Budget(Instant start, ReconParams params) {
            this.start = start;
            this.params = params;
        }

        boolean left() {
            return calls < params.maxCalls() && Duration.between(start, clock.instant()).compareTo(params.maxDuration()) < 0;
        }
    }

    /** 한 건의 판정과 (불렀다면) 엔진의 답. */
    private record Check(Verdict verdict, Optional<EngineAnswer> answer) {
    }

    private RunRecord runLocked(TenantId tenant, ReconTrigger trigger, String actor, List<String> months) {
        Instant runAt = now();
        ReconParams params = store.params(tenant);
        CursorSnapshot cursor = store.cursor(tenant);
        Map<AgentMonth, PortalMonth> targets = store.months(tenant, months);
        Map<AgentMonth, Episode> before = unresolved(tenant);
        Budget budget = new Budget(runAt, params);

        int checked = 0;
        int matched = 0;
        int driftNew = 0;
        int inconclusive = 0;
        for (Map.Entry<AgentMonth, PortalMonth> t : targets.entrySet()) {
            AgentMonth key = t.getKey();
            Check c = check(tenant, cursor, runAt, params, budget, key, t.getValue());
            checked++;
            switch (c.verdict()) {
                case Verdict.Match m -> {
                    matched++;
                    if (before.containsKey(key)) {
                        repair(tenant, key, t.getValue(), c.answer().orElseThrow());
                    }
                }
                case Verdict.Drift d -> {
                    if (!before.containsKey(key)) {
                        driftNew++;
                    }
                    drift(tenant, key, t.getValue(), c.answer().orElseThrow(), Optional.ofNullable(before.get(key)), runAt);
                }
                case Verdict.Inconclusive i -> inconclusive++;
            }
        }

        Map<AgentMonth, Episode> afterChecks = unresolved(tenant);
        ReconRunResult interim = afterChecks.isEmpty() ? ReconRunResult.OK : ReconRunResult.DRIFT;
        RunRecord run = new RunRecord(runAt, actor, trigger, checked, matched, driftNew, afterChecks.size(), inconclusive, budget.calls, interim,
                Optional.empty(), Optional.empty());
        store.insertRun(tenant, run);   // R3

        boolean untried = afterChecks.values().stream().anyMatch(e -> !e.tried());
        if ((driftNew > 0 || untried) && params.autoRebuild() && !store.rebuiltOn(tenant, kstDay(runAt))) {
            Optional<RebuildOutcome> outcome = rebuild.rebuild(tenant, "recon:" + tenant.value());   // R4
            if (outcome.isPresent()) {
                run = run.withRebuild(now(), outcome.get(), outcome.get() == RebuildOutcome.FAILED ? ReconRunResult.FAILED : interim);
                store.updateRun(tenant, run);
                if (outcome.get() == RebuildOutcome.SUCCEEDED) {
                    recheck(tenant, params, budget, afterChecks);   // R5
                }
            }
        }

        Map<AgentMonth, Episode> end = unresolved(tenant);
        ReconRunResult result;
        if (run.rebuildOutcome().equals(Optional.of(RebuildOutcome.FAILED))) {
            result = ReconRunResult.FAILED;
        } else if (!end.isEmpty()) {
            result = ReconRunResult.DRIFT;
        } else if (run.rebuildOutcome().isPresent()) {
            result = ReconRunResult.REPAIRED;
        } else {
            result = ReconRunResult.OK;
        }
        run = run.finish(end.size(), budget.calls, result);
        store.updateRun(tenant, run);   // R6
        metrics.run(tenant, result);
        metrics.unresolved(tenant, end.size());
        return run;
    }

    /** R5 — 재구축 뒤 커서로, 미해결 달만(설계사·달 순). */
    private void recheck(TenantId tenant, ReconParams params, Budget budget, Map<AgentMonth, Episode> unresolved) {
        CursorSnapshot cursor = store.cursor(tenant);
        for (Map.Entry<AgentMonth, Episode> u : unresolved.entrySet()) {
            AgentMonth key = u.getKey();
            Optional<PortalMonth> month = store.month(tenant, key);
            if (month.isEmpty()) {
                continue;   // 재구축이 그 달을 만들지 않았다 — 쓸 행이 없다
            }
            Check c = check(tenant, cursor, now(), params, budget, key, month.get());
            switch (c.verdict()) {
                case Verdict.Match m -> repair(tenant, key, month.get(), c.answer().orElseThrow());
                case Verdict.Drift d -> drift(tenant, key, month.get(), c.answer().orElseThrow(), Optional.of(u.getValue()), now());
                case Verdict.Inconclusive i -> store.drift(tenant, key, Optional.empty());   // 행 0(조건 A), 화면은 "확인 중"(P-6)
            }
        }
    }

    private Check check(TenantId tenant, CursorSnapshot cursor, Instant at, ReconParams params, Budget budget, AgentMonth key, PortalMonth month) {
        EngineAnswer[] answer = new EngineAnswer[1];
        Supplier<EngineAnswer> call = () -> {
            if (budget.calls > 0) {
                pacer.pause(params.callInterval());
            }
            budget.calls++;
            answer[0] = engine.statement(tenant, key.agentId(), key.ym(), ReconMode.of(month.status()));
            return answer[0];
        };
        Verdict v = ReconRules.check(cursor, at, params.caughtUpWithin(), budget.left(), month, call, () -> engine.feedHead(tenant, cursor.lastSeq()));
        metrics.check(tenant, v.result());
        if (v instanceof Verdict.Inconclusive i) {
            metrics.inconclusive(tenant, i.reason());
        }
        return new Check(v, Optional.ofNullable(answer[0]));
    }

    /** DRIFT 한 건 — Q13: 그 달의 미해결 DRIFT 마지막 행과 값이 같으면 행 없이 {@code data_status}만. */
    private void drift(TenantId tenant, AgentMonth key, PortalMonth month, EngineAnswer answer, Optional<Episode> episode, Instant runAt) {
        ReconRow row = row(key, month, answer, runAt, "DRIFT");
        boolean same = episode.isPresent() && Episodes.sameValues(row, episode.get().lastDrift());
        store.drift(tenant, key, same ? Optional.empty() : Optional.of(row));
    }

    /** REPAIRED 한 건 — 행 + OK + 같은 키 알림(Phase 6 수용 Q1). */
    private void repair(TenantId tenant, AgentMonth key, PortalMonth month, EngineAnswer answer) {
        ReconRow row = row(key, month, answer, now(), "REPAIRED");
        Map<NotificationType, Integer> inserted = store.repaired(tenant, row, NotificationRules.monthRepaired(key.agentId(), key.ym(), month.status()));
        inserted.forEach((type, n) -> metrics.notificationsDerived(tenant, type, n));
    }

    /** 포털 셋 + 비교한 엔진 필드만(비교하지 않은 필드·{@code engine_net}은 NULL — P-15, Q3). */
    private static ReconRow row(AgentMonth key, PortalMonth month, EngineAnswer answer, Instant at, String status) {
        Optional<Long> gross;
        Optional<Long> offset;
        Optional<Long> adjustment;
        switch (answer) {
            case EngineAnswer.Confirmed c -> {
                gross = Optional.of(c.gross());
                offset = boxed(c.clawbackOffset());
                adjustment = Optional.of(c.adjustment());
            }
            case EngineAnswer.Estimated e -> {
                gross = Optional.empty();
                offset = Optional.of(e.clawbackOffset());
                adjustment = Optional.of(e.adjustment());
            }
            case EngineAnswer.Failed f -> throw new IllegalStateException("no row for a failed answer: " + f.reason());
        }
        return new ReconRow(key.agentId(), key.ym(), at, month.gross(), month.clawbackOffset(), month.adjustment(), gross, offset, adjustment,
                Optional.empty(), status, ReconSource.BATCH, 0);
    }

    private static Optional<Long> boxed(OptionalLong v) {
        return v.isPresent() ? Optional.of(v.getAsLong()) : Optional.empty();
    }

    private Map<AgentMonth, Episode> unresolved(TenantId tenant) {
        return Episodes.unresolved(store.results(tenant), store.rebuilds(tenant));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);   // timestamptz 정밀도 — 결정론 비교가 바이트로 같게
    }

    static LocalDate kstDay(Instant at) {
        return at.atZone(KST).toLocalDate();
    }
}
