package com.ga.portal.infra;

import com.ga.platform.core.money.Ratio;
import com.ga.platform.core.tenant.TenantContext;
import com.ga.platform.core.tenant.TenantId;
import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.platform.spring.jdbc.TenantSessionBinder;
import com.ga.portal.infra.persistence.TenantRecord;
import com.ga.portal.infra.persistence.TenantRepository;
import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.SeedData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 PostgreSQL에서 TenantRepository: 자기 테넌트 행만, tenant.params 기본 세 키(설계서 §5 v1.2), 한도 임계치 NUMERIC → 정수 bp →
 * {@link Ratio}(승인 Q2 — 80.00 → 80.0%, 95.50 → 95.5%).
 */
class TenantRepositoryIT {

    private static final PostgresHarness DB = PostgresHarness.get();
    private static final TenantId A = TenantId.of(SeedData.uniqueTenant("TR_A"));
    private static final TenantId B = TenantId.of(SeedData.uniqueTenant("TR_B"));
    private static final TenantId EMPTY = TenantId.of(SeedData.uniqueTenant("TR_E"));

    private final TenantRepository repository = new TenantRepository(new TenantJdbcGateway(DB.appDataSource()));
    private final TransactionTemplate tx = new TransactionTemplate(new TenantSessionBinder(DB.appDataSource()));

    @BeforeAll
    static void seed() {
        DB.seed(A.value(), c -> SeedData.exec(c, SeedData.ROWS.get("tenant"), A.value()));
        DB.seed(B.value(), c -> {
            SeedData.exec(c, SeedData.ROWS.get("tenant"), B.value());
            SeedData.exec(c, "UPDATE tenant SET limit_warn_pct = 95.50, limit_crit_pct = 99.99 WHERE tenant_id = ?", B.value());
        });
    }

    private Optional<TenantRecord> current(TenantId tenant) throws Exception {
        return TenantContext.runWith(tenant, () -> tx.execute(s -> repository.current()));
    }

    @Test
    void readsOnlyItsOwnRowWithDefaultParamsAndThresholds() throws Exception {
        TenantRecord a = current(A).orElseThrow();
        assertThat(a.tenantId()).isEqualTo(A);
        assertThat(a.status()).isEqualTo("ACTIVE");
        assertThat(a.limitWarn()).isEqualTo(Ratio.of(8_000, 10_000));
        assertThat(a.limitWarn().toPercentString()).isEqualTo("80.0%");
        assertThat(a.limitCrit().toPercentString()).isEqualTo("95.0%");
        assertThat(a.paramsJson().replace(" ", ""))
                .isEqualTo("{\"notification_digest\":false,\"org_member_lines_visible\":false,\"forecast_scenario_enabled\":false}");
    }

    @Test
    void nonDefaultThresholdsArriveAsBasisPoints() throws Exception {
        TenantRecord b = current(B).orElseThrow();
        assertThat(b.limitWarn()).isEqualTo(Ratio.of(9_550, 10_000));
        assertThat(b.limitWarn().toPercentString()).isEqualTo("95.5%");
        assertThat(b.limitCrit()).isEqualTo(Ratio.of(9_999, 10_000));
    }

    @Test
    void aTenantWithoutARowSeesNothingNotSomeoneElsesRow() throws Exception {
        assertThat(current(EMPTY)).isEmpty();
    }
}
