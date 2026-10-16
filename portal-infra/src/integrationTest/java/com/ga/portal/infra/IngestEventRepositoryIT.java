package com.ga.portal.infra;

import com.ga.platform.core.tenant.TenantContext;
import com.ga.platform.core.tenant.TenantId;
import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.platform.spring.jdbc.TenantSessionBinder;
import com.ga.portal.domain.IngestErrorCode;
import com.ga.portal.domain.IngestEventRecord;
import com.ga.portal.domain.IngestEventStatus;
import com.ga.portal.infra.persistence.IngestEventRepository;
import com.ga.portal.infra.persistence.IngestLockRepository;
import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.SeedData;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IngestEventRepository(V3 {@code payload_hash}): APPLIED·FAILED 쓰기, FAILED → APPLIED, APPLIED는 덮어쓰지 않음, 마지막 APPLIED seq.
 * IngestLockRepository: 같은 테넌트의 두 트랜잭션 중 하나만 잡고, 커밋 뒤 풀리며, 다른 테넌트는 막지 않는다.
 */
class IngestEventRepositoryIT {

    private static final PostgresHarness DB = PostgresHarness.get();
    private static final Instant T0 = Instant.parse("2026-10-17T01:00:00Z");
    private static final String E1 = "5eed0000-0000-4000-8000-000000000001";
    private static final String E2 = "5eed0000-0000-4000-8000-000000000002";
    private static final String H = "c".repeat(64);

    private final TransactionTemplate tx = new TransactionTemplate(new TenantSessionBinder(DB.appDataSource()));
    private final IngestEventRepository events = new IngestEventRepository(new TenantJdbcGateway(DB.appDataSource()));
    private final IngestLockRepository locks = new IngestLockRepository(new TenantJdbcGateway(DB.appDataSource()));

    private <T> T in(TenantId tenant, Supplier<T> work) throws Exception {
        return TenantContext.runWith(tenant, () -> tx.execute(s -> work.get()));
    }

    private static IngestEventRecord applied(TenantId t, String id, long seq) {
        return new IngestEventRecord(t, id, seq, "AgentSnapshot", T0, IngestEventStatus.APPLIED, Optional.empty(), H);
    }

    private static IngestEventRecord failed(TenantId t, String id, long seq, String error) {
        return new IngestEventRecord(t, id, seq, "AgentSnapshot", T0, IngestEventStatus.FAILED, Optional.of(error), H);
    }

    @Test
    void writesReadsAndNeverOverwritesAnAppliedRow() throws Exception {
        TenantId a = TenantId.of(SeedData.uniqueTenant("IE_A"));
        TenantId b = TenantId.of(SeedData.uniqueTenant("IE_B"));
        assertThat(in(a, events::maxAppliedSeq)).isEqualTo(OptionalLong.empty());

        in(a, () -> {
            events.markApplied(applied(a, E1, 1));
            events.markFailed(failed(a, E2, 2, IngestErrorCode.PROJECTOR_ERROR.format("seq 2 x")));
            return null;
        });
        assertThat(in(a, () -> events.find(E1))).contains(applied(a, E1, 1));
        assertThat(in(a, () -> events.find(E2)).orElseThrow().status()).isEqualTo(IngestEventStatus.FAILED);
        assertThat(in(a, events::maxAppliedSeq)).isEqualTo(OptionalLong.of(1));

        in(a, () -> {
            events.markFailed(failed(a, E2, 2, IngestErrorCode.PROJECTOR_ERROR.format("seq 2 again")));
            events.markApplied(applied(a, E2, 2));
            return null;
        });
        assertThat(in(a, () -> events.find(E2))).contains(applied(a, E2, 2));
        assertThat(in(a, events::maxAppliedSeq)).isEqualTo(OptionalLong.of(2));

        assertThatThrownBy(() -> in(a, () -> {
            events.markApplied(applied(a, E1, 1));
            return null;
        })).hasMessageContaining("already APPLIED");
        assertThatThrownBy(() -> in(a, () -> {
            events.markFailed(failed(a, E1, 1, "SCHEMA_VIOLATION: x"));
            return null;
        })).hasMessageContaining("not overwritten");
        assertThat(in(a, () -> events.find(E1))).contains(applied(a, E1, 1));

        assertThat(in(b, () -> events.find(E1))).isEmpty();
        assertThatThrownBy(() -> in(b, () -> {
            events.markApplied(applied(a, E1, 1));
            return null;
        })).hasMessageContaining("not the bound tenant");
    }

    @Test
    void payloadHashMustBeLowerHex() throws Exception {
        TenantId a = TenantId.of(SeedData.uniqueTenant("IE_H"));
        assertThatThrownBy(() -> DB.seed(a.value(), c -> SeedData.exec(c, "INSERT INTO ingest_event (tenant_id, event_id, seq, event_type, "
                + "received_at, status, payload_hash) VALUES (?, '" + E1 + "', 1, 'X', now(), 'APPLIED', '" + "Z".repeat(64) + "')", a.value())))
                .hasMessageContaining("23514");
    }

    @Test
    void theLockIsHeldByOneTransactionPerTenantUntilItEnds() throws Exception {
        TenantId a = TenantId.of(SeedData.uniqueTenant("IL_A"));
        TenantId b = TenantId.of(SeedData.uniqueTenant("IL_B"));
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Boolean> holder = CompletableFuture.supplyAsync(() -> {
            try {
                return in(a, () -> {
                    boolean got = locks.tryLock();
                    held.countDown();
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return got;
                });
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(in(a, locks::tryLock)).as("same tenant while held").isFalse();
        assertThat(in(b, locks::tryLock)).as("another tenant").isTrue();
        release.countDown();
        assertThat(holder.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(in(a, locks::tryLock)).as("released at commit").isTrue();
    }
}
