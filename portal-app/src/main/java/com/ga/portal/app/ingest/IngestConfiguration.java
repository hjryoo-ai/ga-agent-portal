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
import com.ga.portal.infra.persistence.NotificationRepository;
import com.ga.portal.infra.persistence.SyncStatusRepository;
import com.ga.portal.projection.NotificationDeriver;
import com.ga.portal.projection.Projector;
import com.ga.portal.projection.ViewProjector;
import com.ga.portal.projection.view.ViewStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.ga.portal.ingest.feed.ServiceTokenSource;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

/**
 * 인제스트 적용 부품의 조립(Phase 1). 폴링(피드·스케줄·테넌트 목록)은 {@link IngestPollingConfiguration}이 {@code portal.ingest.enabled}일 때만.
 * 프로젝터는 Phase 1의 기록 프로젝터 — Phase 2가 뷰 프로젝터로 바꾼다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IngestProperties.class)
public class IngestConfiguration {

    /**
     * 테넌트별 엔진 서비스 토큰 파일({@code portal.ingest.token-dir}) — 폴링과 시뮬레이션(포털 Phase 5, 계획 Q2·P-9)이 같은 빈을 쓴다. 폴링이 꺼져 있어도 있다
     * (Phase 4까지는 {@code IngestPollingConfiguration}에만 있었다 — 인제스트 없이 시뮬레이션만 켜면 토큰이 없었다).
     */
    @Bean
    ServiceTokenSource serviceTokenSource(IngestProperties props) {
        return new FileServiceTokenSource(props.tokenDir());
    }

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

    /** 알림 파생(포털 Phase 6 — 적용 트랜잭션 안, 프로젝터 다음). no-op 구현은 시험 픽스처에만 있다. */
    @Bean
    NotificationDeriver notificationDeriver(NotificationRepository notifications, SyncStatusRepository settings, Clock clock) {
        return new JdbcNotificationDeriver(notifications, settings, clock);
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
