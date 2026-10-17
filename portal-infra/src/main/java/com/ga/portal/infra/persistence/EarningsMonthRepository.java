package com.ga.portal.infra.persistence;

import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.portal.domain.DataStatus;
import com.ga.portal.domain.MonthStatus;
import com.ga.portal.domain.NetBasis;
import com.ga.portal.domain.view.MonthRow;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 바인딩된 테넌트의 {@code earnings_month}(설계서 §5 v1.5) — 행 전체 upsert(판정은 프로젝터). */
@Repository
public class EarningsMonthRepository extends ViewRepository {

    private static final RowMapper<MonthRow> MAPPER = (rs, n) -> new MonthRow(rs.getString("agent_id"), rs.getString("close_ym"),
            rs.getLong("gross"), rs.getLong("clawback_offset"), rs.getLong("adjustment"), optLong(rs, "withholding_tax"),
            optLong(rs, "net"), MonthStatus.valueOf(rs.getString("status")), DataStatus.valueOf(rs.getString("data_status")),
            optInstant(rs, "estimate_as_of"), optLong(rs, "estimate_basis_seq"), optInstant(rs, "confirmed_at"),
            optInstant(rs, "paid_at"), optLong(rs, "paid_amount"), optLong(rs, "receivable_offset"),
            optLong(rs, "carried_receivable"), optLong(rs, "payable"), optLong(rs, "run_seq"),
            optString(rs, "net_basis").map(NetBasis::valueOf), rs.getLong("last_seq"));

    public EarningsMonthRepository(TenantJdbcGateway gateway) {
        super(gateway);
    }

    public Optional<MonthRow> find(String agentId, String closeYm) {
        return queryAtMostOne("""
                SELECT agent_id, close_ym, gross, clawback_offset, adjustment, withholding_tax, net, status, data_status, estimate_as_of,
                       estimate_basis_seq, confirmed_at, paid_at, paid_amount, receivable_offset, carried_receivable, payable, run_seq,
                       net_basis, last_seq
                  FROM earnings_month WHERE tenant_id = :tenantId AND agent_id = :agentId AND close_ym = :closeYm
                """, Map.of("agentId", agentId, "closeYm", closeYm), MAPPER);
    }

    public void save(MonthRow r) {
        Map<String, Object> p = new HashMap<>();
        p.put("agentId", r.agentId());
        p.put("closeYm", r.closeYm());
        p.put("gross", r.gross());
        p.put("offset", r.clawbackOffset());
        p.put("adjustment", r.adjustment());
        p.put("tax", orNull(r.withholdingTax()));
        p.put("net", orNull(r.net()));
        p.put("status", r.status().name());
        p.put("dataStatus", r.dataStatus().name());
        p.put("estimateAsOf", utc(r.estimateAsOf()));
        p.put("basisSeq", orNull(r.estimateBasisSeq()));
        p.put("confirmedAt", utc(r.confirmedAt()));
        p.put("paidAt", utc(r.paidAt()));
        p.put("paidAmount", orNull(r.paidAmount()));
        p.put("receivableOffset", orNull(r.receivableOffset()));
        p.put("carried", orNull(r.carriedReceivable()));
        p.put("payable", orNull(r.payable()));
        p.put("runSeq", orNull(r.runSeq()));
        p.put("netBasis", r.netBasis().map(Enum::name).orElse(null));
        p.put("lastSeq", r.lastSeq());
        update("""
                INSERT INTO earnings_month (tenant_id, agent_id, close_ym, gross, clawback_offset, adjustment, withholding_tax, net, status,
                                            data_status, estimate_as_of, estimate_basis_seq, confirmed_at, paid_at, paid_amount,
                                            receivable_offset, carried_receivable, payable, run_seq, net_basis, last_seq)
                VALUES (:tenantId, :agentId, :closeYm, :gross, :offset, :adjustment, :tax, :net, :status, :dataStatus, :estimateAsOf,
                        :basisSeq, :confirmedAt, :paidAt, :paidAmount, :receivableOffset, :carried, :payable, :runSeq, :netBasis, :lastSeq)
                ON CONFLICT (tenant_id, agent_id, close_ym) DO UPDATE
                   SET gross = EXCLUDED.gross, clawback_offset = EXCLUDED.clawback_offset, adjustment = EXCLUDED.adjustment,
                       withholding_tax = EXCLUDED.withholding_tax, net = EXCLUDED.net, status = EXCLUDED.status,
                       data_status = EXCLUDED.data_status, estimate_as_of = EXCLUDED.estimate_as_of,
                       estimate_basis_seq = EXCLUDED.estimate_basis_seq, confirmed_at = EXCLUDED.confirmed_at, paid_at = EXCLUDED.paid_at,
                       paid_amount = EXCLUDED.paid_amount, receivable_offset = EXCLUDED.receivable_offset,
                       carried_receivable = EXCLUDED.carried_receivable, payable = EXCLUDED.payable, run_seq = EXCLUDED.run_seq,
                       net_basis = EXCLUDED.net_basis, last_seq = EXCLUDED.last_seq
                 WHERE earnings_month.tenant_id = :tenantId
                """, p);
    }
}
