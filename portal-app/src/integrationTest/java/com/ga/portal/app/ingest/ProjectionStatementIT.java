package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.platform.spring.jdbc.TenantSessionBinder;
import com.ga.portal.infra.persistence.IngestCursorRepository;
import com.ga.portal.infra.persistence.IngestEventRepository;
import com.ga.portal.infra.persistence.IngestLockRepository;
import com.ga.portal.infra.testing.RecordingDataSource;
import com.ga.portal.infra.testing.SeedData;
import com.ga.portal.ingest.ContractSchemas;
import com.ga.portal.ingest.EnvelopeValidator;
import com.ga.portal.ingest.apply.IngestApplier;
import com.ga.portal.projection.testing.Envelopes;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C11(계획 ⑤ ⓑ): 이벤트 한 건의 적용이 내는 SQL 문장 수는 그 테넌트에 쌓인 행 수와 무관하다 — 다른 설계사의 라인 1,000개가 있는 테넌트와 빈
 * 테넌트에서 A-1 열 건의 문장 수가 이벤트마다 같고, 이벤트당 상한 안이다.
 */
class ProjectionStatementIT {

    private static List<Integer> statementsPerEvent(TenantId t) {
        RecordingDataSource recording = new RecordingDataSource(IngestRig.DB.appDataSource());
        TenantJdbcGateway gateway = new TenantJdbcGateway(recording);
        SpringTenantTransactions transactions = new SpringTenantTransactions(new TenantSessionBinder(recording));
        JdbcIngestStore store = new JdbcIngestStore(new IngestCursorRepository(gateway), new IngestEventRepository(gateway));
        IngestApplier applier = new IngestApplier(new EnvelopeValidator(ContractSchemas.get()), store, transactions,
                new JdbcIngestLock(new IngestLockRepository(gateway)), ViewRig.projector(gateway), IngestRig.CLOCK);
        transactions.inTransaction(t, () -> store.ensureCursor(IngestRig.CLOCK.instant()));
        List<Integer> counts = new ArrayList<>();
        for (ObjectNode e : Envelopes.nodes(Envelopes.a1())) {
            int before = recording.statements().size();
            assertThat(applier.apply(t, e).outcome()).isEqualTo(IngestApplier.Outcome.APPLIED);
            counts.add(recording.statements().size() - before);
        }
        return counts;
    }

    @Test
    void c11StatementsPerEventDoNotDependOnTheTenantsHistory() {
        TenantId empty = TenantId.of(SeedData.uniqueTenant("STMT_E"));
        TenantId busy = TenantId.of(SeedData.uniqueTenant("STMT_B"));
        IngestRig.DB.seed(busy.value(), c -> {
            for (int i = 0; i < 1_000; i++) {
                SeedData.exec(c, "INSERT INTO earnings_line (tenant_id, calc_id, agent_id, policy_no, comm_type, amount, close_ym, basis, policy_summary, source_seq) "
                        + "VALUES (?, ?, ?, 'POL-X', 'FY_COMM', 1, '202608', '{}', '{}', ?)", busy.value(), "other-" + i, "A-" + (2000 + i % 50), 1000 + i);
            }
        });
        List<Integer> a = statementsPerEvent(empty);
        List<Integer> b = statementsPerEvent(busy);
        assertThat(b).isEqualTo(a);
        assertThat(a).allSatisfy(n -> assertThat(n).isBetween(1, 16));
    }
}
