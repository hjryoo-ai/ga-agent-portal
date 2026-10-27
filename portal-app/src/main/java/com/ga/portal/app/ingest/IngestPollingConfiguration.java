package com.ga.portal.app.ingest;

import com.ga.platform.spring.jdbc.TenantDirectoryReader;
import com.ga.portal.infra.persistence.TenantRepository;
import com.ga.portal.ingest.apply.IngestApplier;
import com.ga.portal.ingest.apply.IngestMetrics;
import com.ga.portal.ingest.apply.IngestStore;
import com.ga.portal.ingest.apply.IngestWorker;
import com.ga.portal.ingest.apply.PollSchedule;
import com.ga.portal.ingest.apply.TenantTransactions;
import com.ga.portal.ingest.feed.FeedClient;
import com.ga.portal.ingest.feed.HttpFeedClient;
import com.ga.portal.ingest.feed.ServiceTokenSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;

/**
 * 폴링 조립({@code portal.ingest.enabled=true}): 테넌트 목록은 플랫폼 {@link TenantDirectoryReader}(전용 롤 {@code portal_directory} —
 * {@code tenant.tenant_id}만), 피드는 테넌트의 {@code engine_base_url}로 HTTP, 토큰은 파일, 틱은 {@code portal.ingest.tick}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "portal.ingest", name = "enabled", havingValue = "true")
@EnableScheduling
public class IngestPollingConfiguration {

    @Bean
    FeedClient feedClient(IngestProperties props, TenantTransactions transactions, TenantRepository tenants, ServiceTokenSource tokens) {
        return httpFeed(props, transactions, tenants, tokens);
    }

    /** 폴링과 재구축(CLI)이 같은 피드 조립을 쓴다. */
    public static FeedClient httpFeed(IngestProperties props, TenantTransactions transactions, TenantRepository tenants, ServiceTokenSource tokens) {
        return new HttpFeedClient(HttpClient.newBuilder().connectTimeout(props.httpTimeout()).build(),
                tenant -> URI.create(transactions.inTransaction(tenant, tenants::current).orElseThrow().engineBaseUrl()),
                tokens, props.httpTimeout());
    }

    @Bean
    IngestWorker ingestWorker(FeedClient feed, IngestStore store, TenantTransactions transactions, IngestApplier applier,
                              IngestMetrics metrics, Clock clock, IngestProperties props) {
        return new IngestWorker(feed, store, transactions, applier, metrics, clock, props.limit());
    }

    @Bean
    IngestScheduler ingestScheduler(IngestProperties props, @Value("${spring.datasource.url}") String jdbcUrl, TenantTransactions transactions,
                                    TenantRepository tenants, IngestWorker worker, Clock clock) {
        TenantDirectoryReader reader = new TenantDirectoryReader(jdbcUrl, props.directoryUsername(), props.directoryPassword());
        return new IngestScheduler(reader::allTenants, transactions, tenants, worker, new PollSchedule(props.defaultPollInterval()),
                props.defaultPollInterval(), clock);
    }

    @Bean
    Ticker ingestTicker(IngestScheduler scheduler) {
        return new Ticker(scheduler);
    }

    /** 고정 지연 틱 — 한 틱이 끝난 뒤 다음 틱. */
    static final class Ticker {
        private final IngestScheduler scheduler;

        Ticker(IngestScheduler scheduler) {
            this.scheduler = scheduler;
        }

        @Scheduled(fixedDelayString = "${portal.ingest.tick}", initialDelayString = "${portal.ingest.tick}")
        void tick() {
            scheduler.tick();
        }
    }
}
