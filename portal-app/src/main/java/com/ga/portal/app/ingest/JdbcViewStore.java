package com.ga.portal.app.ingest;

import com.ga.portal.domain.view.AgentRow;
import com.ga.portal.domain.view.DeferralRow;
import com.ga.portal.domain.view.FlagRow;
import com.ga.portal.domain.view.HistoryRow;
import com.ga.portal.domain.view.LedgerRow;
import com.ga.portal.domain.view.LineRow;
import com.ga.portal.domain.view.MonthRow;
import com.ga.portal.domain.view.MonthSums;
import com.ga.portal.domain.view.ReceivableRow;
import com.ga.portal.domain.view.ReconRow;
import com.ga.portal.infra.persistence.AgentProfileRepository;
import com.ga.portal.infra.persistence.ClawbackItemRepository;
import com.ga.portal.infra.persistence.DeferralItemRepository;
import com.ga.portal.infra.persistence.EarningsLineRepository;
import com.ga.portal.infra.persistence.EarningsMonthRepository;
import com.ga.portal.infra.persistence.PolicyFlagRepository;
import com.ga.portal.infra.persistence.PolicyLimitRepository;
import com.ga.portal.infra.persistence.ReconResultRepository;
import com.ga.portal.projection.view.ViewStore;

import java.util.List;
import java.util.Optional;

/** 뷰 저장 포트의 PostgreSQL 어댑터 — 인프라 저장소({@code TenantScopedRepository} 하위)에 위임한다(Phase 1 승인 Q6 방식). */
final class JdbcViewStore implements ViewStore {

    private final AgentProfileRepository agents;
    private final EarningsLineRepository lines;
    private final EarningsMonthRepository months;
    private final PolicyLimitRepository limits;
    private final DeferralItemRepository deferrals;
    private final ClawbackItemRepository receivables;
    private final PolicyFlagRepository flags;
    private final ReconResultRepository recon;

    JdbcViewStore(AgentProfileRepository agents, EarningsLineRepository lines, EarningsMonthRepository months, PolicyLimitRepository limits,
                  DeferralItemRepository deferrals, ClawbackItemRepository receivables, PolicyFlagRepository flags, ReconResultRepository recon) {
        this.agents = agents;
        this.lines = lines;
        this.months = months;
        this.limits = limits;
        this.deferrals = deferrals;
        this.receivables = receivables;
        this.flags = flags;
        this.recon = recon;
    }

    @Override
    public Optional<AgentRow> agent(String agentId) {
        return agents.find(agentId);
    }

    @Override
    public void saveAgent(AgentRow row) {
        agents.save(row);
    }

    @Override
    public Optional<LineRow> line(String calcId) {
        return lines.find(calcId);
    }

    @Override
    public void insertLine(LineRow row) {
        lines.insert(row);
    }

    @Override
    public MonthSums monthSums(String agentId, String closeYm) {
        return lines.sums(agentId, closeYm);
    }

    @Override
    public Optional<MonthRow> month(String agentId, String closeYm) {
        return months.find(agentId, closeYm);
    }

    @Override
    public void saveMonth(MonthRow row) {
        months.save(row);
    }

    @Override
    public Optional<LedgerRow> ledger(String ledgerId) {
        return limits.find(ledgerId);
    }

    @Override
    public Optional<LedgerRow> ledgerOf(String policyNo, String agentId) {
        return limits.findByPolicy(policyNo, agentId);
    }

    @Override
    public void insertLedger(LedgerRow row) {
        limits.insert(row);
    }

    @Override
    public void saveLedger(LedgerRow row) {
        limits.save(row);
    }

    @Override
    public void insertHistory(HistoryRow row) {
        limits.insertHistory(row);
    }

    @Override
    public Optional<DeferralRow> deferral(String scheduleId) {
        return deferrals.find(scheduleId);
    }

    @Override
    public void insertDeferral(DeferralRow row) {
        deferrals.insert(row);
    }

    @Override
    public void saveDeferral(DeferralRow row) {
        deferrals.save(row);
    }

    @Override
    public Optional<ReceivableRow> receivable(String receivableId) {
        return receivables.find(receivableId);
    }

    @Override
    public void saveReceivable(ReceivableRow row) {
        receivables.save(row);
    }

    @Override
    public List<FlagRow> flags(String policyNo, String agentId) {
        return flags.find(policyNo, agentId);
    }

    @Override
    public void saveFlag(FlagRow row) {
        flags.save(row);
    }

    @Override
    public void insertReconIfAbsent(ReconRow row) {
        recon.insertIfAbsent(row);
    }
}
