package com.ga.portal.recon.port;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.NotificationType;
import com.ga.portal.domain.view.ReconRow;
import com.ga.portal.projection.notify.Candidate;
import com.ga.portal.recon.AgentMonth;
import com.ga.portal.recon.ReconParams;
import com.ga.portal.recon.RunRecord;
import com.ga.portal.recon.rule.CursorSnapshot;
import com.ga.portal.recon.rule.PortalMonth;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 대사의 저장(계획 ③ R0~R6) — {@code portal-app}이 테넌트 바인딩 트랜잭션으로 구현한다. <b>메서드 하나 = 트랜잭션 하나</b>: 쓰는 메서드는 그 행과
 * {@code earnings_month.data_status}(와 알림)를 함께 쓰거나 함께 롤백한다(C4·C5).
 */
public interface ReconStore {

    ReconParams params(TenantId tenant);

    /** 회차 시작 때 고정할 커서(상태·{@code last_seq}·{@code caught_up_at}). 커서가 없으면 {@code RUNNING}이 아닌 것으로(대사하지 않는다). */
    CursorSnapshot cursor(TenantId tenant);

    /** 대상 달들의 {@code earnings_month} 행 전부(설계사·달 순). */
    Map<AgentMonth, PortalMonth> months(TenantId tenant, List<String> yms);

    Optional<PortalMonth> month(TenantId tenant, AgentMonth key);

    /** {@code recon_result}의 DRIFT·REPAIRED 행 전부. */
    List<ReconRow> results(TenantId tenant);

    /** {@code recon_run.rebuilt_at} 중 값이 있는 것 전부. */
    List<Instant> rebuilds(TenantId tenant);

    /** R2·R5: {@code row}가 있으면 {@code recon_result} DRIFT 행, 그리고 그 달 {@code data_status = DRIFT} — 한 트랜잭션. */
    void drift(TenantId tenant, AgentMonth key, Optional<ReconRow> row);

    /** R5: {@code recon_result} REPAIRED 행 + 그 달 {@code data_status = OK} + 알림 후보(같은 키면 넣지 않는다) — 한 트랜잭션. 넣은 알림 행 수를 종류별로. */
    Map<NotificationType, Integer> repaired(TenantId tenant, ReconRow row, List<Candidate> notifications);

    void insertRun(TenantId tenant, RunRecord run);

    void updateRun(TenantId tenant, RunRecord run);

    /** 그 KST 날짜에 스케줄 회차가 있었나(계획 Q9 — 하루 한 번). */
    boolean scheduledRunOn(TenantId tenant, LocalDate kstDay);

    /** 그 KST 날짜에 대사가 재구축을 불렀나(성공·실패 무관 — 하루 한 번). */
    boolean rebuiltOn(TenantId tenant, LocalDate kstDay);
}
