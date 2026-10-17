package com.ga.portal.app.ingest;

import com.ga.portal.infra.persistence.IngestCursorRepository;
import com.ga.portal.infra.persistence.IngestEventRepository;
import com.ga.portal.infra.persistence.IngestLockRepository;
import com.ga.portal.ingest.ContractSchemas;
import com.ga.portal.ingest.EnvelopeValidator;
import com.ga.portal.ingest.apply.IngestApplier;
import com.ga.portal.ingest.apply.IngestLock;
import com.ga.portal.ingest.apply.IngestMetrics;
import com.ga.portal.ingest.apply.IngestStore;
import com.ga.portal.ingest.apply.TenantTransactions;
import com.ga.portal.infra.persistence.AgentProfileRepository;
import com.ga.portal.infra.persistence.ClawbackItemRepository;
import com.ga.portal.infra.persistence.DeferralItemRepository;
import com.ga.portal.infra.persistence.EarningsLineRepository;
import com.ga.portal.infra.persistence.EarningsMonthRepository;
import com.ga.portal.infra.persistence.PolicyFlagRepository;
import com.ga.portal.infra.persistence.PolicyLimitRepository;
import com.ga.portal.infra.persistence.ReconResultRepository;
import com.ga.portal.projection.NoopNotificationDeriver;
import com.ga.portal.projection.NotificationDeriver;
import com.ga.portal.projection.Projector;
import com.ga.portal.projection.ViewProjector;
import com.ga.portal.projection.view.ViewStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

/**
 * 인제스트 적용 부품의 조립(Phase 1). 폴링(피드·스케줄·테넌트 목록)은 {@link IngestPollingConfiguration}이 {@code portal.ingest.enabled}일 때만.
 * 프로젝터는 Phase 1의 기록 프로젝터 — Phase 2가 뷰 프로젝터로 바꾼다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IngestProperties.class)
public class IngestConfiguration {

    @Bean
    IngestStore ingestStore(IngestCursorRepository cursors, IngestEventRepository events) {
        return new JdbcIngestStore(cursors, events);
    }

    @Bean
    TenantTransactions tenantTransactions(PlatformTransactionManager transactionManager) {
        return new SpringTenantTransactions(transactionManager);
    }

    @Bean
    IngestLock ingestLock(IngestLockRepository locks) {
        return new JdbcIngestLock(locks);
    }

    @Bean
    IngestMetrics ingestMetrics(ObjectProvider<MeterRegistry> registry) {
        MeterRegistry r = registry.getIfAvailable();
        return r == null ? IngestMetrics.NONE : new MicrometerIngestMetrics(r);
    }

    @Bean
    ViewStore viewStore(AgentProfileRepository agents, EarningsLineRepository lines, EarningsMonthRepository months, PolicyLimitRepository limits,
                        DeferralItemRepository deferrals, ClawbackItemRepository receivables, PolicyFlagRepository flags, ReconResultRepository recon) {
        return new JdbcViewStore(agents, lines, months, limits, deferrals, receivables, flags, recon);
    }

    /** Phase 2: 뷰 프로젝터(Phase 1의 기록 프로젝터는 시험 픽스처로 옮겼다). */
    @Bean
    Projector projector(ViewStore store) {
        return new ViewProjector(store);
    }

    /** 알림 파생 — Phase 6까지 no-op(지시문 D-15). */
    @Bean
    NotificationDeriver notificationDeriver() {
        return new NoopNotificationDeriver();
    }

    @Bean
    EnvelopeValidator envelopeValidator() {
        return new EnvelopeValidator(ContractSchemas.get());
    }

    @Bean
    IngestApplier ingestApplier(EnvelopeValidator validator, IngestStore store, TenantTransactions transactions, IngestLock lock,
                                Projector projector, NotificationDeriver notifications, Clock clock) {
        return new IngestApplier(validator, store, transactions, lock, projector, notifications, clock);
    }
}
