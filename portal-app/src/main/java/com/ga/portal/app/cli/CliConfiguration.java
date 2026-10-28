package com.ga.portal.app.cli;

import com.ga.platform.spring.jdbc.TenantDirectoryReader;
import com.ga.portal.app.PortalMeters;
import com.ga.portal.app.ingest.IngestCommands;
import com.ga.portal.app.ingest.IngestProperties;
import com.ga.portal.app.recon.ReconCommands;
import com.ga.portal.app.recon.ReconService;
import com.ga.portal.infra.persistence.AuditAccessRepository;
import com.ga.portal.infra.persistence.IdentityLinkRepository;
import com.ga.portal.infra.persistence.IngestCursorRepository;
import com.ga.portal.infra.persistence.InquiryOperatorRepository;
import com.ga.portal.infra.persistence.NotificationRepository;
import com.ga.portal.infra.persistence.ReconResultRepository;
import com.ga.portal.infra.persistence.ReconRunRepository;
import com.ga.portal.ingest.apply.TenantTransactions;
import com.ga.portal.recon.port.ReconEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

/** 운영 CLI 조립({@code cli} 프로파일 — 포털 Phase 6 Q28): 실행기 하나({@link CliRunner})가 {@code ingest}·{@code inquiry} 두 묶음을 받는다. */
@Configuration(proxyBeanMethods = false)
@Profile("cli")
public class CliConfiguration {

    @Bean
    InquiryCommands inquiryCommands(TenantTransactions transactions, InquiryOperatorRepository inquiries, NotificationRepository notifications,
                                    AuditAccessRepository audit, IdentityLinkRepository links, PortalMeters meters, Clock clock) {
        return new InquiryCommands(transactions, inquiries, notifications, audit, links, meters, clock);
    }

    /** 대사·운영 상태(포털 Phase 7). 전 테넌트 {@code status}의 목록은 인제스트 명령과 같은 전용 롤 리더. */
    @Bean
    ReconCommands reconCommands(TenantTransactions transactions, IdentityLinkRepository links, AuditAccessRepository audit, ReconService service,
                                ReconEngine engine, IngestCursorRepository cursors, ReconResultRepository results, ReconRunRepository runs,
                                InquiryOperatorRepository inquiries, IngestProperties props, @Value("${spring.datasource.url}") String jdbcUrl,
                                Clock clock) {
        TenantDirectoryReader reader = new TenantDirectoryReader(jdbcUrl, props.directoryUsername(), props.directoryPassword());
        return new ReconCommands(transactions, links, audit, service, engine, cursors, results, runs, inquiries, reader::allTenants, clock);
    }

    @Bean
    CliRunner cliRunner(IngestCommands ingest, InquiryCommands inquiry, ReconCommands recon) {
        return new CliRunner(ingest, inquiry, recon, System.out, System.getProperty("user.name"));
    }
}
