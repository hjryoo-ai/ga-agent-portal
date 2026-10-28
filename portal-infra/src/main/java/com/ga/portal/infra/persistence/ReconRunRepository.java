package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.portal.domain.RebuildOutcome;
import com.ga.portal.domain.ReconRunResult;
import com.ga.portal.domain.ReconTrigger;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 바인딩된 테넌트의 {@code recon_run}(설계서 §5 V8 — 포털 Phase 7). 수와 닫힌 값만 — 금액·설계사·달 없음. */
@Repository
public class ReconRunRepository extends ViewRepository {

    /** 한 행. */
    public record Run(Instant runAt, String actor, ReconTrigger trigger, int checked, int matched, int driftNew, int driftUnresolved,
                      int inconclusive, int engineCalls, ReconRunResult result, Optional<Instant> rebuiltAt, Optional<RebuildOutcome> rebuildOutcome) {
    }

    private static final RowMapper<Run> MAPPER = (rs, n) -> new Run(instant(rs, "run_at"), rs.getString("actor"),
            ReconTrigger.valueOf(rs.getString("trigger")), rs.getInt("checked"), rs.getInt("matched"), rs.getInt("drift_new"),
            rs.getInt("drift_unresolved"), rs.getInt("inconclusive"), rs.getInt("engine_calls"), ReconRunResult.valueOf(rs.getString("result")),
            optInstant(rs, "rebuilt_at"), optString(rs, "rebuild_outcome").map(RebuildOutcome::valueOf));

    public ReconRunRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    public void insert(Run r) {
        update("""
                INSERT INTO recon_run (tenant_id, run_at, actor, trigger, checked, matched, drift_new, drift_unresolved, inconclusive, engine_calls,
                                       result, rebuilt_at, rebuild_outcome)
                VALUES (:tenantId, :runAt, :actor, :trigger, :checked, :matched, :driftNew, :driftUnresolved, :inconclusive, :engineCalls,
                        :result, :rebuiltAt, :rebuildOutcome)
                """, params(r));
    }

    /** 같은 회차(run_at)의 수·결과·재구축을 고친다(R4·R6). 고친 행 수. */
    public int update(Run r) {
        return update("""
                UPDATE recon_run SET checked = :checked, matched = :matched, drift_new = :driftNew, drift_unresolved = :driftUnresolved,
                                     inconclusive = :inconclusive, engine_calls = :engineCalls, result = :result, rebuilt_at = :rebuiltAt,
                                     rebuild_outcome = :rebuildOutcome
                 WHERE tenant_id = :tenantId AND run_at = :runAt
                """, params(r));
    }

    /** {@code [from, to)} 안에 시작한 스케줄 회차가 있나(계획 Q9 — KST 하루). */
    public boolean scheduledBetween(Instant from, Instant to) {
        return queryAtMostOne("""
                SELECT count(*) AS n FROM recon_run
                 WHERE tenant_id = :tenantId AND trigger = 'SCHEDULE' AND run_at >= :from AND run_at < :to
                """, Map.of("from", utc(from), "to", utc(to)), (rs, n) -> rs.getLong("n")).orElse(0L) > 0;
    }

    /** {@code [from, to)} 안에 대사가 부른 재구축이 있나(성공·실패 무관 — 하루 한 번). */
    public boolean rebuiltBetween(Instant from, Instant to) {
        return queryAtMostOne("""
                SELECT count(*) AS n FROM recon_run
                 WHERE tenant_id = :tenantId AND rebuilt_at >= :from AND rebuilt_at < :to
                """, Map.of("from", utc(from), "to", utc(to)), (rs, n) -> rs.getLong("n")).orElse(0L) > 0;
    }

    /** 재구축 시각 전부(시도함 판정 — {@code Episodes}). */
    public List<Instant> rebuilds() {
        return query("""
                SELECT rebuilt_at FROM recon_run WHERE tenant_id = :tenantId AND rebuilt_at IS NOT NULL ORDER BY rebuilt_at
                """, Map.of(), (rs, n) -> instant(rs, "rebuilt_at"));
    }

    /** 마지막 회차(운영 {@code status}). */
    public Optional<Run> last() {
        return queryAtMostOne("""
                SELECT run_at, actor, trigger, checked, matched, drift_new, drift_unresolved, inconclusive, engine_calls, result, rebuilt_at,
                       rebuild_outcome
                  FROM recon_run WHERE tenant_id = :tenantId ORDER BY run_at DESC LIMIT 1
                """, Map.of(), MAPPER);
    }

    /** 재구축을 부른 마지막 회차(운영 {@code status}의 "마지막 재구축 결과"). */
    public Optional<Run> lastRebuild() {
        return queryAtMostOne("""
                SELECT run_at, actor, trigger, checked, matched, drift_new, drift_unresolved, inconclusive, engine_calls, result, rebuilt_at,
                       rebuild_outcome
                  FROM recon_run WHERE tenant_id = :tenantId AND rebuilt_at IS NOT NULL ORDER BY rebuilt_at DESC LIMIT 1
                """, Map.of(), MAPPER);
    }

    private static Map<String, Object> params(Run r) {
        Map<String, Object> p = new HashMap<>();
        p.put("runAt", utc(r.runAt()));
        p.put("actor", r.actor());
        p.put("trigger", r.trigger().name());
        p.put("checked", r.checked());
        p.put("matched", r.matched());
        p.put("driftNew", r.driftNew());
        p.put("driftUnresolved", r.driftUnresolved());
        p.put("inconclusive", r.inconclusive());
        p.put("engineCalls", r.engineCalls());
        p.put("result", r.result().name());
        p.put("rebuiltAt", utc(r.rebuiltAt()));
        p.put("rebuildOutcome", orNull(r.rebuildOutcome().map(Enum::name)));
        return p;
    }
}
