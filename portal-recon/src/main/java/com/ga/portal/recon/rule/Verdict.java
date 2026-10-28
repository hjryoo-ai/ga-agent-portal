package com.ga.portal.recon.rule;

import java.util.Objects;

/** 대사 한 건의 판정. */
public sealed interface Verdict {

    CheckResult result();

    record Match() implements Verdict {
        @Override
        public CheckResult result() {
            return CheckResult.MATCH;
        }
    }

    record Drift() implements Verdict {
        @Override
        public CheckResult result() {
            return CheckResult.DRIFT;
        }
    }

    record Inconclusive(InconclusiveReason reason) implements Verdict {
        public Inconclusive {
            Objects.requireNonNull(reason, "reason");
        }

        @Override
        public CheckResult result() {
            return CheckResult.INCONCLUSIVE;
        }
    }

    Verdict MATCH = new Match();
    Verdict DRIFT = new Drift();

    static Verdict inconclusive(InconclusiveReason reason) {
        return new Inconclusive(reason);
    }
}
