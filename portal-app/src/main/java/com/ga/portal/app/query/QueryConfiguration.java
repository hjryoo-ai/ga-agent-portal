package com.ga.portal.app.query;

import com.ga.portal.infra.persistence.AgentQueryRepository;
import com.ga.portal.infra.persistence.SyncStatusRepository;
import com.ga.portal.query.MeQueries;
import com.ga.portal.query.port.ReadTransaction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * 조회 유스케이스 조립(포털 Phase 3 계획 ⑦). 웹 애플리케이션에서만 — 운영 명령 프로파일에는 조회 API가 없다. 읽기 트랜잭션은 플랫폼
 * {@code TenantSessionBinder}의 읽기 전용 트랜잭션(바인딩된 테넌트 — {@code TenantBindingFilter}).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(QueryProperties.class)
public class QueryConfiguration {

    @Bean
    MeQueries meQueries(AgentQueryRepository agents, SyncStatusRepository sync, PlatformTransactionManager transactionManager, Clock clock,
                        QueryProperties props) {
        TransactionTemplate read = new TransactionTemplate(transactionManager);
        read.setReadOnly(true);
        ReadTransaction transaction = new ReadTransaction() {
            @Override
            public <T> T read(java.util.function.Supplier<T> work) {
                return read.execute(status -> work.get());
            }
        };
        return new MeQueries(new JdbcAgentQueryStore(agents), sync::snapshot, HmacPolicyKeys.fromFile(props.policyKeySecretFile()), transaction,
                clock, props.defaultSyncDelaySeconds());
    }
}
