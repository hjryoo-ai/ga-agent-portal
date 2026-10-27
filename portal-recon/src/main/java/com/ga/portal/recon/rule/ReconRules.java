package com.ga.portal.recon.rule;

import com.ga.portal.domain.IngestState;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Supplier;

/**
 * 대사 판정(설계서 §6.6 표, 계획 ② — C3). 순수 함수: 엔진 호출과 피드 머리 읽기는 필요할 때만 부르는 콜백이다(호출 수 = 회차의
 * {@code engine_calls}, 머리는 CONFIRMED 불일치일 때만). 금액은 {@code ==}로만 본다 — 차액을 만들지 않는다.
 *
 * <ol>
 *   <li>커서가 RUNNING이 아니거나 {@code caught_up_at}이 없거나 회차 시작 − {@code caught_up_at} &gt; 창 → LAGGING(호출 없음)</li>
 *   <li>예산 소진 → BUDGET(호출 없음)</li>
 *   <li>모드 = 달 상태({@link ReconMode#of}). 엔진 답이 성공이 아니면 그 사유, 모드와 다른 모양이면 ENGINE_CONTRACT</li>
 *   <li>ESTIMATE: {@code basisSeq} &gt; 고정 {@code last_seq} → LAGGING</li>
 *   <li>ESTIMATE: {@code clawbackOffset}·{@code adjustment} 같음 → MATCH, 다름 → ESTIMATE_DIFF({@code gross}는 보지 않는다 — Q1 ⓑ′)</li>
 *   <li>CONFIRMED: {@code gross}·{@code adjustment}·(있으면) {@code clawbackOffset} 같음 → MATCH</li>
 *   <li>CONFIRMED 다름 + 피드 머리 &gt; 고정 {@code last_seq} → LAGGING (머리를 못 읽으면 ENGINE_UNAVAILABLE)</li>
 *   <li>CONFIRMED 다름 + 머리 ≤ 고정 {@code last_seq} → DRIFT</li>
 * </ol>
 */
public final class ReconRules {

    /** 피드 머리 한 번 읽기(after = 고정 {@code last_seq}, limit 1) — 비면 엔진에 닿지 못했다. */
    @FunctionalInterface
    public interface HeadProbe {
        OptionalLong head();
    }

    private ReconRules() {
    }

    /** 1단계 — 커서가 대사할 수 있는 상태인가. 테넌트 전체가 같은 답이다(회차는 이 답이 참이면 엔진을 부르지 않는다). */
    public static boolean lagging(CursorSnapshot cursor, Instant runStart, Duration window) {
        Objects.requireNonNull(runStart, "runStart");
        Objects.requireNonNull(window, "window");
        if (cursor.state() != IngestState.RUNNING || cursor.caughtUpAt().isEmpty()) {
            return true;
        }
        return Duration.between(cursor.caughtUpAt().get(), runStart).compareTo(window) > 0;
    }

    /** 설계사×달 한 건. {@code engine}은 1·2단계를 통과했을 때만, {@code head}는 7단계에서만 부른다. */
    public static Verdict check(CursorSnapshot cursor, Instant runStart, Duration window, boolean budgetLeft, PortalMonth month,
                                Supplier<EngineAnswer> engine, HeadProbe head) {
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(head, "head");
        if (lagging(cursor, runStart, window)) {
            return Verdict.inconclusive(InconclusiveReason.LAGGING);
        }
        if (!budgetLeft) {
            return Verdict.inconclusive(InconclusiveReason.BUDGET);
        }
        return judge(cursor.lastSeq(), month, engine.get(), head);
    }

    /** 3~8단계 — 엔진 답이 온 뒤(재대사도 이것을 쓴다). */
    public static Verdict judge(long fixedLastSeq, PortalMonth month, EngineAnswer answer, HeadProbe head) {
        ReconMode mode = ReconMode.of(month.status());
        return switch (answer) {
            case EngineAnswer.Failed f -> Verdict.inconclusive(f.reason());
            case EngineAnswer.Estimated e when mode != ReconMode.ESTIMATE -> Verdict.inconclusive(InconclusiveReason.ENGINE_CONTRACT);
            case EngineAnswer.Confirmed c when mode != ReconMode.CONFIRMED -> Verdict.inconclusive(InconclusiveReason.ENGINE_CONTRACT);
            case EngineAnswer.Estimated e -> {
                if (e.basisSeq() > fixedLastSeq) {
                    yield Verdict.inconclusive(InconclusiveReason.LAGGING);
                }
                yield e.clawbackOffset() == month.clawbackOffset() && e.adjustment() == month.adjustment()
                        ? Verdict.MATCH : Verdict.inconclusive(InconclusiveReason.ESTIMATE_DIFF);
            }
            case EngineAnswer.Confirmed c -> {
                boolean same = c.gross() == month.gross() && c.adjustment() == month.adjustment()
                        && (c.clawbackOffset().isEmpty() || c.clawbackOffset().getAsLong() == month.clawbackOffset());
                if (same) {
                    yield Verdict.MATCH;
                }
                OptionalLong h = head.head();
                if (h.isEmpty()) {
                    yield Verdict.inconclusive(InconclusiveReason.ENGINE_UNAVAILABLE);
                }
                yield h.getAsLong() > fixedLastSeq ? Verdict.inconclusive(InconclusiveReason.LAGGING) : Verdict.DRIFT;
            }
        };
    }
}
