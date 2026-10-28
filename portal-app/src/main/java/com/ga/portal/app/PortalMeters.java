package com.ga.portal.app;

import com.ga.platform.core.tenant.TenantId;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 여러 경로가 같이 쓰는 지표(포털 Phase 7 — {@code docs/지표.md}, Phase 6 수용 Q3·Q4): 이름마다 태그 집합 하나를 이 클래스 한 곳에서.
 * <ul>
 *   <li>{@code notifications_derived_total{tenant, type}} — 파생기·CLI 답변·재대사가 <b>실제로 넣은</b> 행만({@code ON CONFLICT}로 0이면 세지 않는다)</li>
 *   <li>{@code inquiries_total{tenant, action}} — {@code CREATED}(설계사 API)·{@code ANSWERED}·{@code CLOSED}(운영 CLI), 커밋된 것만</li>
 *   <li>{@code inquiry_open_total{tenant}}·{@code inquiry_open_oldest_age_seconds{tenant}} — 열린 문의 적체(대사 틱이 갱신, 없으면 0)</li>
 * </ul>
 * 레지스트리가 없으면 아무것도 하지 않는다.
 */
public final class PortalMeters {

    public static final PortalMeters NONE = new PortalMeters(Optional.empty());

    private final Optional<MeterRegistry> registry;
    private final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    public PortalMeters(Optional<MeterRegistry> registry) {
        this.registry = registry;
    }

    public void notificationsDerived(TenantId tenant, String type, int inserted) {
        if (inserted > 0) {
            registry.ifPresent(r -> r.counter("notifications_derived_total", "tenant", tenant.value(), "type", type).increment(inserted));
        }
    }

    public void inquiry(TenantId tenant, String action) {
        registry.ifPresent(r -> r.counter("inquiries_total", "tenant", tenant.value(), "action", action).increment());
    }

    public void inquiryBacklog(TenantId tenant, long open, long oldestAgeSeconds) {
        registry.ifPresent(r -> {
            gauges.computeIfAbsent("inquiry_open_total|" + tenant.value(), k -> {
                AtomicLong v = new AtomicLong();
                Gauge.builder("inquiry_open_total", v, AtomicLong::get).tag("tenant", tenant.value()).register(r);
                return v;
            }).set(open);
            gauges.computeIfAbsent("inquiry_open_oldest_age_seconds|" + tenant.value(), k -> {
                AtomicLong v = new AtomicLong();
                Gauge.builder("inquiry_open_oldest_age_seconds", v, AtomicLong::get).tag("tenant", tenant.value()).register(r);
                return v;
            }).set(oldestAgeSeconds);
        });
    }

    /** 트랜잭션이 커밋되면 — 트랜잭션 밖이면 바로. 커밋된 사실만 세게 한다. */
    public static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
