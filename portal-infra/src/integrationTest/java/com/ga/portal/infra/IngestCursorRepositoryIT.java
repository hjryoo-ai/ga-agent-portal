package com.ga.portal.infra;

import com.ga.platform.core.tenant.TenantContext;
import com.ga.platform.core.tenant.TenantId;
import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.platform.spring.jdbc.TenantSessionBinder;
import com.ga.portal.domain.IngestState;
import com.ga.portal.infra.persistence.IngestCursor;
import com.ga.portal.infra.persistence.IngestCursorRepository;
import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.SeedData;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** IngestCursorRepository: 테넌트별 커서 저장·갱신(시각은 주입된 Clock), 다른 테넌트 커서는 보이지 않는다. */
class IngestCursorRepositoryIT {

    private static final PostgresHarness DB = PostgresHarness.get();
    private static final Instant T0 = Instant.parse("2026-10-16T01:00:00Z");
    private static final String EVENT = "6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b";

    private final TransactionTemplate tx = new TransactionTemplate(new TenantSessionBinder(DB.appDataSource()));

    private IngestCursorRepository at(Instant now) {
        return new IngestCursorRepository(new TenantJdbcGateway(DB.appDataSource()), Clock.fixed(now, ZoneOffset.UTC));
    }

    private <T> T in(TenantId tenant, java.util.function.Supplier<T> work) throws Exception {
        return TenantContext.runWith(tenant, () -> tx.execute(s -> work.get()));
    }

    @Test
    void savesThenAdvancesTheCursorOfTheBoundTenantOnly() throws Exception {
        TenantId a = TenantId.of(SeedData.uniqueTenant("IC_A"));
        TenantId b = TenantId.of(SeedData.uniqueTenant("IC_B"));

        assertThat(in(a, () -> at(T0).find())).isEmpty();
        in(a, () -> {
            at(T0).save(0, Optional.empty(), IngestState.RUNNING, Optional.empty());
            return null;
        });
        in(a, () -> {
            at(T0.plusSeconds(30)).save(9, Optional.of(EVENT), IngestState.FAILED, Optional.of("seq gap: expected 10, got 12"));
            return null;
        });

        IngestCursor cursor = in(a, () -> at(T0).find()).orElseThrow();
        assertThat(cursor.lastSeq()).isEqualTo(9);
        assertThat(cursor.lastEventId()).contains(EVENT);
        assertThat(cursor.state()).isEqualTo(IngestState.FAILED);
        assertThat(cursor.updatedAt()).isEqualTo(T0.plusSeconds(30));
        assertThat(cursor.error()).contains("seq gap: expected 10, got 12");

        assertThat(in(b, () -> at(T0).find())).isEmpty();
    }
}
