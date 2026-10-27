package com.ga.portal.app.cli;

import com.ga.portal.app.ingest.IngestCommands;
import com.ga.portal.infra.persistence.AuditAccessRepository;
import com.ga.portal.infra.persistence.IdentityLinkRepository;
import com.ga.portal.infra.persistence.InquiryOperatorRepository;
import com.ga.portal.infra.persistence.NotificationRepository;
import com.ga.portal.ingest.apply.TenantTransactions;
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
                                    AuditAccessRepository audit, IdentityLinkRepository links, Clock clock) {
        return new InquiryCommands(transactions, inquiries, notifications, audit, links, clock);
    }

    @Bean
    CliRunner cliRunner(IngestCommands ingest, InquiryCommands inquiry) {
        return new CliRunner(ingest, inquiry, System.out, System.getProperty("user.name"));
    }
}
