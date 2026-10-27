package com.ga.portal.recon;

import com.ga.portal.domain.view.ReconRow;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 미해결 DRIFT(설계서 §6.6, 계획 Q10·Q14) — 순수 함수. 그 (설계사, 달)의 마지막 {@code REPAIRED}보다 늦은 {@code DRIFT} 행(출처 무관)이 있으면 미해결이고, 그
 * 행들이 한 "회차"(episode)다. 시도함 = 그 회차의 <b>첫</b> DRIFT 행 뒤에 재구축 시각({@code recon_run.rebuilt_at})이 있다 — 재구축 뒤 값이 바뀌어 새 DRIFT
 * 행이 붙어도 다시 "시도 안 함"이 되지 않는다(지시문 §4 "미해결 DRIFT의 매일 재구축 금지", P7-3).
 */
public final class Episodes {

    /** 미해결 회차 하나 — 첫 DRIFT 시각, 마지막 DRIFT 행(Q13 같은 값 비교의 상대), 시도 여부. */
    public record Episode(AgentMonth key, Instant firstDriftAt, ReconRow lastDrift, boolean tried) {
        public Episode {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(firstDriftAt, "firstDriftAt");
            Objects.requireNonNull(lastDrift, "lastDrift");
        }
    }

    private static final Comparator<ReconRow> ORDER = Comparator.comparing(ReconRow::runAt).thenComparing(ReconRow::source)
            .thenComparingLong(ReconRow::sourceSeq);

    private Episodes() {
    }

    /** {@code rows}: 그 테넌트의 DRIFT·REPAIRED 행 전부. {@code rebuilds}: 값이 있는 {@code recon_run.rebuilt_at} 전부. */
    public static Map<AgentMonth, Episode> unresolved(List<ReconRow> rows, List<Instant> rebuilds) {
        Map<AgentMonth, Instant> repairedAt = new TreeMap<>();
        for (ReconRow r : rows) {
            if (r.status().equals("REPAIRED")) {
                repairedAt.merge(new AgentMonth(r.agentId(), r.closeYm()), r.runAt(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        Map<AgentMonth, ReconRow> first = new TreeMap<>();
        Map<AgentMonth, ReconRow> last = new TreeMap<>();
        for (ReconRow r : rows) {
            if (!r.status().equals("DRIFT")) {
                continue;
            }
            AgentMonth key = new AgentMonth(r.agentId(), r.closeYm());
            Instant repaired = repairedAt.get(key);
            if (repaired != null && !r.runAt().isAfter(repaired)) {
                continue;
            }
            first.merge(key, r, (a, b) -> ORDER.compare(a, b) <= 0 ? a : b);
            last.merge(key, r, (a, b) -> ORDER.compare(a, b) >= 0 ? a : b);
        }
        Map<AgentMonth, Episode> out = new TreeMap<>();
        first.forEach((key, f) -> out.put(key, new Episode(key, f.runAt(), last.get(key), rebuilds.stream().anyMatch(t -> t.isAfter(f.runAt())))));
        return out;
    }

    /** Q13 — 포털 셋·엔진 셋이 마지막 DRIFT 행과 같은가(같으면 행을 다시 쓰지 않는다). */
    public static boolean sameValues(ReconRow a, ReconRow b) {
        return a.portalGross() == b.portalGross() && a.portalOffset() == b.portalOffset() && a.portalAdjustment() == b.portalAdjustment()
                && a.engineGross().equals(b.engineGross()) && a.engineOffset().equals(b.engineOffset())
                && a.engineAdjustment().equals(b.engineAdjustment()) && a.engineNet().equals(b.engineNet());
    }
}
