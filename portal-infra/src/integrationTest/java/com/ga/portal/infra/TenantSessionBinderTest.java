package com.ga.portal.infra;

import com.ga.platform.core.tenant.TenantContext;
import com.ga.platform.core.tenant.TenantId;
import com.ga.platform.core.tenant.TenantNotBoundException;
import com.ga.platform.spring.jdbc.OutsideTenantTransactionException;
import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.platform.spring.jdbc.TenantSession;
import com.ga.platform.spring.jdbc.TenantSessionBinder;
import com.ga.portal.infra.persistence.IngestCursorRepository;
import com.ga.portal.infra.persistence.TenantRepository;
import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.RecordingDataSource;
import com.ga.portal.infra.testing.SeedData;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C4(지시문 v1.2): 트랜잭션 밖 DB 접근 거부, 컨텍스트 없는 트랜잭션은 SET을 건너뛰지 않고 실패 — 플랫폼 0.2.0의 보장을 포털 저장소와
 * 포털 DB(portal_app, 실제 PostgreSQL)로 다시 증명한다. 문장 기록은 실제 데이터소스를 감싼 {@link RecordingDataSource}로 본다.
 */
class TenantSessionBinderTest {

    private static final PostgresHarness DB = PostgresHarness.get();
    private static final TenantId A = TenantId.of(SeedData.uniqueTenant("SB_A"));

    private final RecordingDataSource recording = new RecordingDataSource(DB.appDataSource());
    private final TenantRepository tenants = new TenantRepository(new TenantJdbcGateway(recording));
    private final IngestCursorRepository cursors = new IngestCursorRepository(new TenantJdbcGateway(recording), Clock.systemUTC());

    @Test
    void repositoryCallOutsideATransactionIsRejectedBeforeTheDatabase() {
        TenantContext.runWith(A, () -> {
            assertThatThrownBy(tenants::current).isInstanceOf(OutsideTenantTransactionException.class);
            assertThatThrownBy(cursors::find).isInstanceOf(OutsideTenantTransactionException.class);
        });
        assertThat(recording.connectionsObtained()).isZero();
    }

    @Test
    void transactionNotStartedByTheBinderIsRejected() {
        TransactionTemplate foreign = new TransactionTemplate(new DataSourceTransactionManager(recording));
        TenantContext.runWith(A, () -> foreign.executeWithoutResult(s ->
                assertThatThrownBy(tenants::current).isInstanceOf(OutsideTenantTransactionException.class)));
        assertThat(recording.statements()).isEmpty();
    }

    @Test
    void transactionWithoutContextFailsToStartInsteadOfSkippingTheSet() {
        TransactionTemplate tx = new TransactionTemplate(new TenantSessionBinder(recording));
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> tenants.current())).isInstanceOf(TenantNotBoundException.class);
        assertThat(recording.connectionsObtained()).isZero();
        assertThat(recording.statements()).isEmpty();
    }

    @Test
    void theFirstStatementOfEveryTransactionSetsTheTenantAndTheSettingIsTransactionLocal() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(new TenantSessionBinder(recording));
        String inside = TenantContext.runWith(A, () -> tx.execute(s -> {
            tenants.current();
            cursors.find();
            return recording.queryOnBoundConnection("SELECT current_setting('" + TenantSession.SETTING_NAME + "', true)");
        }));
        assertThat(inside).isEqualTo(A.value());
        assertThat(recording.connectionsObtained()).isEqualTo(1);
        assertThat(recording.statements()).hasSize(4);
        assertThat(recording.statements().getFirst()).isEqualTo("SELECT set_config('" + TenantSession.SETTING_NAME + "', ?, true)");
        // JdbcClient는 :tenantId를 ?로 바꿔 준비한다 — 테넌트 술어는 "tenant_id = ?"로 남는다
        assertThat(recording.statements().get(1)).contains("FROM tenant").contains("tenant_id = ?");
        assertThat(recording.statements().get(2)).contains("FROM ingest_cursor").contains("tenant_id = ?");
        // 트랜잭션이 끝난 뒤 새 트랜잭션에는 테넌트가 남지 않는다(set_config(..., true) — 트랜잭션 로컬)
        assertThat(DB.<String>asApp(null, c -> {
            try (var st = c.createStatement(); var rs = st.executeQuery("SELECT current_setting('app.tenant_id', true)")) {
                rs.next();
                return rs.getString(1);
            }
        })).isNullOrEmpty();
    }
}
