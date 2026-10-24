package com.ga.portal.app.inbox;

import com.ga.portal.inbox.InboxUseCases;
import com.ga.portal.inbox.port.InboxTransactions;
import com.ga.portal.infra.persistence.InquiryRepository;
import com.ga.portal.infra.persistence.NotificationRepository;
import com.ga.portal.infra.persistence.TenantRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 알림함·이의제기 조립(포털 Phase 6 ⑥) — 웹 애플리케이션에서만. 트랜잭션은 바인딩된 테넌트의 읽기 전용(목록·상세)과 읽기·쓰기(읽음·접수). 문의 한도 기본값은
 * {@code portal.inbox.default-inquiries-per-day}(기본 20 — 테넌트 {@code params.inquiry_rate_per_day}가 없을 때).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InboxConfiguration {

    @Bean
    InboxUseCases inboxUseCases(NotificationRepository notifications, InquiryRepository inquiries, TenantRepository tenants,
                                PlatformTransactionManager transactionManager, Clock clock, ObjectProvider<MeterRegistry> registry,
                                @Value("${portal.inbox.default-inquiries-per-day:20}") int defaultInquiriesPerDay) {
        TransactionTemplate write = new TransactionTemplate(transactionManager);
        TransactionTemplate read = new TransactionTemplate(transactionManager);
        read.setReadOnly(true);
        InboxTransactions transactions = new InboxTransactions() {
            @Override
            public <T> T read(Supplier<T> work) {
                return read.execute(status -> work.get());
            }

            @Override
            public <T> T write(Supplier<T> work) {
                return write.execute(status -> work.get());
            }
        };
        return new InboxUseCases(new JdbcInboxStore(notifications, inquiries, tenants), transactions,
                new MicrometerInboxMetrics(Optional.ofNullable(registry.getIfAvailable())), clock, UUID::randomUUID, defaultInquiriesPerDay);
    }
}
