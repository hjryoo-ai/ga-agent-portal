package com.ga.portal.infra;

import com.ga.platform.core.tenant.TenantContext;
import com.ga.platform.core.tenant.TenantId;
import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.platform.spring.jdbc.TenantSessionBinder;
import com.ga.portal.domain.IngestState;
import com.ga.portal.domain.CursorRecord;
import com.ga.portal.infra.persistence.IngestCursorRepository;
import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.SeedData;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** IngestCursorRepository: 테넌트별 커서 생성(동시에 불러도 하나)·저장·갱신(시각은 호출자가 넘긴다), 다른 테넌트 커서는 보이지 않는다. */
class IngestCursorRepositoryIT {

    private static final PostgresHarness DB = PostgresHarness.get();
    private static final Instant T0 = Instant.parse("2026-10-16T01:00:00Z");
    private static final String EVENT = "6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b";

    private final TransactionTemplate tx = new TransactionTemplate(new TenantSessionBinder(DB.appDataSource()));

    private final IngestCursorRepository repo = new IngestCursorRepository(new TenantJdbcGateway(DB.appDataSource()));

    private <T> T in(TenantId tenant, java.util.function.Supplier<T> work) throws Exception {
        return TenantContext.runWith(tenant, () -> tx.execute(s -> work.get()));
    }

    @Test
    void savesThenAdvancesTheCursorOfTheBoundTenantOnly() throws Exception {
        TenantId a = TenantId.of(SeedData.uniqueTenant("IC_A"));
        TenantId b = TenantId.of(SeedData.uniqueTenant("IC_B"));

        assertThat(in(a, repo::find)).isEmpty();
        CursorRecord created = in(a, () -> repo.ensure(T0));
        assertThat(created).isEqualTo(new CursorRecord(0, Optional.empty(), IngestState.RUNNING, T0, Optional.empty()));
        assertThat(in(a, () -> repo.ensure(T0.plusSeconds(5)))).as("ensure keeps an existing cursor").isEqualTo(created);
        in(a, () -> {
            repo.save(9, Optional.of(EVENT), IngestState.FAILED, Optional.of("SEQ_GAP: expected seq 10, got seq 12"), T0.plusSeconds(30));
            return null;
        });

        CursorRecord cursor = in(a, repo::find).orElseThrow();
        assertThat(cursor.lastSeq()).isEqualTo(9);
        assertThat(cursor.lastEventId()).contains(EVENT);
        assertThat(cursor.state()).isEqualTo(IngestState.FAILED);
        assertThat(cursor.updatedAt()).isEqualTo(T0.plusSeconds(30));
        assertThat(cursor.error()).contains("SEQ_GAP: expected seq 10, got seq 12");

        assertThat(in(b, repo::find)).isEmpty();
    }
}
