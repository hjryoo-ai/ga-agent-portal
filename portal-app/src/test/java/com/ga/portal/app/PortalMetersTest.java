package com.ga.portal.app;

import com.ga.platform.core.tenant.TenantId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포털 Phase 7(docs/지표.md): 함께 쓰는 지표는 커밋된 사실만 센다 — 롤백된 트랜잭션의 계수 0, 넣지 않은 알림(같은 키) 0, 적체 게이지는 마지막 값.
 */
class PortalMetersTest {

    private static final TenantId T = TenantId.of("GA01");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final PortalMeters meters = new PortalMeters(Optional.of(registry));

    private double count(String name, String key, String value) {
        var c = registry.find(name).tag("tenant", T.value()).tag(key, value).counter();
        return c == null ? 0 : c.count();
    }

    @Test
    void countsHappenOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            PortalMeters.afterCommit(() -> meters.inquiry(T, "ANSWERED"));
            assertThat(count("inquiries_total", "action", "ANSWERED")).as("not yet committed").isZero();
            List<TransactionSynchronization> pending = TransactionSynchronizationManager.getSynchronizations();
            pending.forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertThat(count("inquiries_total", "action", "ANSWERED")).isEqualTo(1.0);
    }

    @Test
    void aRolledBackTransactionCountsNothing() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            PortalMeters.afterCommit(() -> meters.inquiry(T, "CLOSED"));
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertThat(count("inquiries_total", "action", "CLOSED")).isZero();
    }

    @Test
    void onlyInsertedNotificationsAreCounted() {
        meters.notificationsDerived(T, "PAYOUT", 0);
        assertThat(registry.find("notifications_derived_total").counters()).as("ON CONFLICT — no meter at all").isEmpty();
        meters.notificationsDerived(T, "PAYOUT", 2);
        meters.notificationsDerived(T, "PAYOUT", 1);
        assertThat(count("notifications_derived_total", "type", "PAYOUT")).isEqualTo(3.0);
    }

    @Test
    void theBacklogGaugesHoldTheLastValues() {
        meters.inquiryBacklog(T, 3, 7200);
        meters.inquiryBacklog(T, 0, 0);
        assertThat(registry.find("inquiry_open_total").tag("tenant", T.value()).gauge().value()).isZero();
        meters.inquiryBacklog(T, 2, 60);
        assertThat(registry.find("inquiry_open_total").tag("tenant", T.value()).gauges()).hasSize(1);
        assertThat(registry.find("inquiry_open_total").tag("tenant", T.value()).gauge().value()).isEqualTo(2.0);
        assertThat(registry.find("inquiry_open_oldest_age_seconds").tag("tenant", T.value()).gauge().value()).isEqualTo(60.0);
    }

    @Test
    void withoutARegistryNothingHappens() {
        PortalMeters.NONE.inquiry(T, "CREATED");
        PortalMeters.NONE.inquiryBacklog(T, 1, 1);
        PortalMeters.NONE.notificationsDerived(T, "PAYOUT", 1);
        assertThat(registry.getMeters()).isEmpty();
    }
}
