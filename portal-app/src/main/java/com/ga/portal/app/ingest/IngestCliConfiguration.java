package com.ga.portal.app.ingest;

import com.ga.platform.spring.jdbc.TenantDirectoryReader;
import com.ga.portal.infra.persistence.AuditAccessRepository;
import com.ga.portal.infra.persistence.TenantRepository;
import com.ga.portal.infra.persistence.ViewResetRepository;
import com.ga.portal.ingest.EnvelopeValidator;
import com.ga.portal.ingest.apply.IngestLock;
import com.ga.portal.ingest.apply.IngestMetrics;
import com.ga.portal.ingest.apply.IngestStore;
import com.ga.portal.ingest.apply.Rebuilder;
import com.ga.portal.ingest.apply.TenantTransactions;
import com.ga.portal.ingest.feed.FeedClient;
import com.ga.portal.projection.Projector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

/** 운영 명령 조립({@code cli} 프로파일 — 웹·폴링 없음, {@code application-cli.yaml}). 테넌트 목록은 스케줄러와 같은 전용 롤. */
@Configuration(proxyBeanMethods = false)
@Profile("cli")
public class IngestCliConfiguration {

    /** 재구축의 피드 — 폴링과 같은 조립(토큰 파일·테넌트의 {@code engine_base_url}). */
    @Bean
    Rebuilder rebuilder(EnvelopeValidator validator, IngestStore store, TenantTransactions transactions, IngestLock lock, Projector projector,
                        ViewResetRepository views, TenantRepository tenants, IngestProperties props, Clock clock) {
        FeedClient feed = IngestPollingConfiguration.httpFeed(props, transactions, tenants, new FileServiceTokenSource(props.tokenDir()));
        return new Rebuilder(validator, store, transactions, lock, projector, new JdbcViewReset(views), feed, clock, props.limit(),
                props.rebuildMaxEvents());
    }

    @Bean
    IngestCommands ingestCommands(TenantTransactions transactions, IngestStore store, IngestLock lock, AuditAccessRepository audit,
                                  IngestProperties props, @Value("${spring.datasource.url}") String jdbcUrl, Rebuilder rebuilder,
                                  IngestMetrics metrics, Clock clock) {
        TenantDirectoryReader reader = new TenantDirectoryReader(jdbcUrl, props.directoryUsername(), props.directoryPassword());
        return new IngestCommands(transactions, store, lock, audit, reader::allTenants, rebuilder, metrics, clock);
    }

    @Bean
    IngestCliRunner ingestCliRunner(IngestCommands commands) {
        return new IngestCliRunner(commands, System.out, System.getProperty("user.name"));
    }
}
