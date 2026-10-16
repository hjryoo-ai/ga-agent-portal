package com.ga.portal.app.ingest;

import com.ga.platform.spring.jdbc.TenantDirectoryReader;
import com.ga.portal.infra.persistence.AuditAccessRepository;
import com.ga.portal.ingest.apply.IngestLock;
import com.ga.portal.ingest.apply.IngestStore;
import com.ga.portal.ingest.apply.TenantTransactions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

/** 운영 명령 조립({@code cli} 프로파일 — 웹·폴링 없음, {@code application-cli.yaml}). 테넌트 목록은 스케줄러와 같은 전용 롤. */
@Configuration(proxyBeanMethods = false)
@Profile("cli")
public class IngestCliConfiguration {

    @Bean
    IngestCommands ingestCommands(TenantTransactions transactions, IngestStore store, IngestLock lock, AuditAccessRepository audit,
                                  IngestProperties props, @Value("${spring.datasource.url}") String jdbcUrl, Clock clock) {
        TenantDirectoryReader reader = new TenantDirectoryReader(jdbcUrl, props.directoryUsername(), props.directoryPassword());
        return new IngestCommands(transactions, store, lock, audit, reader::allTenants, clock);
    }

    @Bean
    IngestCliRunner ingestCliRunner(IngestCommands commands) {
        return new IngestCliRunner(commands, System.out, System.getProperty("user.name"));
    }
}
