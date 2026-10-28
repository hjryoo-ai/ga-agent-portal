package com.ga.portal.recon;

import com.ga.portal.domain.RebuildOutcome;
import com.ga.portal.domain.ReconRunResult;
import com.ga.portal.domain.ReconTrigger;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code recon_run} 한 행(설계서 §5 V8) — 수와 닫힌 값만. 첫 판정(R1·R2)의 수가 {@code checked}·{@code matched}·{@code driftNew}·{@code inconclusive},
 * {@code engineCalls}는 재대사(R5)까지 합한 호출 수, {@code driftUnresolved}는 회차가 끝났을 때의 미해결 DRIFT 수.
 */
public record RunRecord(Instant runAt, String actor, ReconTrigger trigger, int checked, int matched, int driftNew, int driftUnresolved,
                        int inconclusive, int engineCalls, ReconRunResult result, Optional<Instant> rebuiltAt, Optional<RebuildOutcome> rebuildOutcome) {

    public RunRecord {
        Objects.requireNonNull(runAt, "runAt");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(rebuiltAt, "rebuiltAt");
        Objects.requireNonNull(rebuildOutcome, "rebuildOutcome");
    }

    RunRecord withRebuild(Instant at, RebuildOutcome outcome, ReconRunResult r) {
        return new RunRecord(runAt, actor, trigger, checked, matched, driftNew, driftUnresolved, inconclusive, engineCalls, r, Optional.of(at),
                Optional.of(outcome));
    }

    RunRecord finish(int unresolved, int calls, ReconRunResult r) {
        return new RunRecord(runAt, actor, trigger, checked, matched, driftNew, unresolved, inconclusive, calls, r, rebuiltAt, rebuildOutcome);
    }
}
