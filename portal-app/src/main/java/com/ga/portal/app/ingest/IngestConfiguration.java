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
import com.ga.portal.projection.Projector;
import com.ga.portal.projection.RecordingProjector;
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
    Projector projector() {
        return new RecordingProjector();
    }

    @Bean
    EnvelopeValidator envelopeValidator() {
        return new EnvelopeValidator(ContractSchemas.get());
    }

    @Bean
    IngestApplier ingestApplier(EnvelopeValidator validator, IngestStore store, TenantTransactions transactions, IngestLock lock,
                                Projector projector, Clock clock) {
        return new IngestApplier(validator, store, transactions, lock, projector, clock);
    }
}
