package com.ga.portal.app.query;

import com.ga.platform.core.tenant.AgentId;
import com.ga.portal.domain.view.AgentRow;
import com.ga.portal.domain.view.DeferralRow;
import com.ga.portal.domain.view.DueMonth;
import com.ga.portal.domain.view.HistoryRow;
import com.ga.portal.domain.view.LedgerItem;
import com.ga.portal.domain.view.LineDetail;
import com.ga.portal.domain.view.LinePage;
import com.ga.portal.domain.view.MonthDetail;
import com.ga.portal.domain.view.MonthRow;
import com.ga.portal.domain.view.ReceivableRow;
import com.ga.portal.infra.persistence.AgentQueryRepository;
import com.ga.portal.query.port.AgentQueryStore;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** {@link AgentQueryStore} → {@link AgentQueryRepository}(모든 문장 {@code agent_id = :agentId}). 변환 없음. */
final class JdbcAgentQueryStore implements AgentQueryStore {

    private final AgentQueryRepository repository;

    JdbcAgentQueryStore(AgentQueryRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<AgentRow> profile(AgentId agent) {
        return repository.profile(agent.value());
    }

    @Override
    public List<MonthRow> months(AgentId agent, String fromYm, String toYm) {
        return repository.months(agent.value(), fromYm, toYm);
    }

    @Override
    public Optional<MonthRow> month(AgentId agent, String closeYm) {
        return repository.month(agent.value(), closeYm);
    }

    @Override
    public Optional<MonthDetail> monthDetail(AgentId agent, String closeYm, String previousYm) {
        return repository.monthDetail(agent.value(), closeYm, previousYm);
    }

    @Override
    public Map<String, Long> byType(AgentId agent, String closeYm) {
        return repository.byType(agent.value(), closeYm);
    }

    @Override
    public LinePage lines(AgentId agent, String closeYm, Optional<String> commType, Optional<String> policyNo, int page, int size) {
        return repository.lines(agent.value(), closeYm, commType, policyNo, page, size);
    }

    @Override
    public Set<String> linePolicies(AgentId agent, String closeYm) {
        return repository.linePolicies(agent.value(), closeYm);
    }

    @Override
    public Optional<LineDetail> line(AgentId agent, String calcId) {
        return repository.line(agent.value(), calcId);
    }

    @Override
    public List<DeferralRow> deferralsOfLine(AgentId agent, String calcId) {
        return repository.deferralsOfLine(agent.value(), calcId);
    }

    @Override
    public Optional<ReceivableRow> receivableOfLine(AgentId agent, String calcId) {
        return repository.receivableOfLine(agent.value(), calcId);
    }

    @Override
    public Optional<String> ledgerIdOfPolicy(AgentId agent, String policyNo) {
        return repository.ledgerIdOfPolicy(agent.value(), policyNo);
    }

    @Override
    public List<LedgerItem> ledgers(AgentId agent) {
        return repository.ledgers(agent.value());
    }

    @Override
    public Optional<LedgerItem> ledger(AgentId agent, String ledgerId) {
        return repository.ledger(agent.value(), ledgerId);
    }

    @Override
    public List<HistoryRow> history(AgentId agent, String ledgerId) {
        return repository.history(agent.value(), ledgerId);
    }

    @Override
    public List<DeferralRow> deferrals(AgentId agent, String fromYm, String toYm, Optional<String> policyNo) {
        return repository.deferrals(agent.value(), fromYm, toYm, policyNo);
    }

    @Override
    public List<DueMonth> dueMonths(AgentId agent, String fromYm, String toYm, Optional<String> policyNo) {
        return repository.dueMonths(agent.value(), fromYm, toYm, policyNo);
    }

    @Override
    public Set<String> deferralPolicies(AgentId agent) {
        return repository.deferralPolicies(agent.value());
    }
}
