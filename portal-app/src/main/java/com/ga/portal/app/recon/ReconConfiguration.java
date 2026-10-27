package com.ga.portal.app.recon;

import com.ga.platform.spring.jdbc.TenantDirectoryReader;
import com.ga.portal.app.PortalMeters;
import com.ga.portal.app.ingest.IngestPollingConfiguration;
import com.ga.portal.app.ingest.IngestProperties;
import com.ga.portal.app.ingest.JdbcViewReset;
import com.ga.portal.app.ingest.SpringTenantTransactions;
import com.ga.portal.app.trace.TracedFeedClient;
import com.ga.portal.domain.trace.Tracing;
import com.ga.portal.infra.persistence.AuditAccessRepository;
import com.ga.portal.infra.persistence.EarningsMonthRepository;
import com.ga.portal.infra.persistence.IngestCursorRepository;
import com.ga.portal.infra.persistence.InquiryOperatorRepository;
import com.ga.portal.infra.persistence.NotificationRepository;
import com.ga.portal.infra.persistence.ReconLockRepository;
import com.ga.portal.infra.persistence.ReconResultRepository;
import com.ga.portal.infra.persistence.ReconRunRepository;
import com.ga.portal.infra.persistence.TenantRepository;
import com.ga.portal.infra.persistence.ViewResetRepository;
import com.ga.portal.ingest.EnvelopeValidator;
import com.ga.portal.ingest.apply.IngestLock;
import com.ga.portal.ingest.apply.IngestMetrics;
import com.ga.portal.ingest.apply.IngestStore;
import com.ga.portal.ingest.apply.Rebuilder;
import com.ga.portal.ingest.feed.FeedClient;
import com.ga.portal.ingest.feed.ServiceTokenSource;
import com.ga.portal.projection.Projector;
import com.ga.portal.recon.ReconRunner;
import com.ga.portal.recon.engine.HttpStatementClient;
import com.ga.portal.recon.port.ReconEngine;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Optional;

/**
 * 대사 조립(포털 Phase 7) — 서버와 CLI 둘 다. 재구축기는 대사 전용 인스턴스다: 트랜잭션이 늘 새 트랜잭션(회차 잠금의 바깥 트랜잭션에 합류하지 않는다 — P7-4),
 * 피드·뷰 초기화·프로젝터·검증기는 인제스트와 같은 부품.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReconProperties.class)
public class ReconConfiguration {

    /** 엔진 명세·피드 머리(대사와 운영 {@code status}가 같은 빈). 피드 클라이언트는 테넌트 주소를 새 트랜잭션으로 읽는다. */
    @Bean
    ReconEngine reconEngine(ReconProperties props, IngestProperties ingest, PlatformTransactionManager transactionManager, TenantRepository tenants,
                            ServiceTokenSource tokens, Tracing tracing) {
        SpringTenantTransactions fresh = SpringTenantTransactions.requiresNew(transactionManager);
        HttpStatementClient statements = new HttpStatementClient(HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build(),
                props.readTimeout());
        return new HttpReconEngine(statements, new TracedFeedClient(IngestPollingConfiguration.httpFeed(ingest, fresh, tenants, tokens), tracing), fresh,
                tenants, tokens, tracing);
    }

    @Bean
    ReconService reconService(ReconProperties props, IngestProperties ingest, @Value("${spring.datasource.url}") String jdbcUrl,
                              PlatformTransactionManager transactionManager, TenantRepository tenants, IngestCursorRepository cursors,
                              EarningsMonthRepository months, ReconResultRepository results, ReconRunRepository runs,
                              NotificationRepository notifications, ReconLockRepository locks, AuditAccessRepository audit, ServiceTokenSource tokens,
                              EnvelopeValidator validator, IngestStore store, IngestLock ingestLock, Projector projector, ViewResetRepository views,
                              IngestMetrics ingestMetrics, ReconEngine engine, InquiryOperatorRepository inquiries, PortalMeters meters,
                              ObjectProvider<MeterRegistry> registry, Tracing tracing, Clock clock) {
        SpringTenantTransactions fresh = SpringTenantTransactions.requiresNew(transactionManager);
        FeedClient feed = new TracedFeedClient(IngestPollingConfiguration.httpFeed(ingest, fresh, tenants, tokens), tracing);
        Rebuilder rebuilder = new Rebuilder(validator, store, fresh, ingestLock, projector, new JdbcViewReset(views), feed, clock, ingest.limit(),
                ingest.rebuildMaxEvents());
        ReconRunner runner = new ReconRunner(
                new JdbcReconStore(transactionManager, tenants, cursors, months, results, runs, notifications, props.defaults(), clock),
                engine,
                new TransactionalReconLock(transactionManager, locks),
                new RebuilderReconRebuild(rebuilder, audit, ingestMetrics, clock),
                new MicrometerReconMetrics(Optional.ofNullable(registry.getIfAvailable()), meters),
                interval -> sleep(interval.toMillis()),
                clock,
                tracing);
        TenantDirectoryReader reader = new TenantDirectoryReader(jdbcUrl, ingest.directoryUsername(), ingest.directoryPassword());
        return new ReconService(runner, reader::allTenants, fresh, tenants, inquiries, meters, clock);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
