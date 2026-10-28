package com.ga.portal.app.sim;

import com.ga.portal.app.trace.TracedSimulationClient;
import com.ga.portal.domain.trace.Tracing;
import com.ga.portal.domain.view.SyncSnapshot;
import com.ga.portal.infra.persistence.AgentQueryRepository;
import com.ga.portal.infra.persistence.SimulationLockRepository;
import com.ga.portal.infra.persistence.SimulationLogRepository;
import com.ga.portal.infra.persistence.SyncStatusRepository;
import com.ga.portal.infra.persistence.TenantRepository;
import com.ga.portal.ingest.feed.ServiceTokenSource;
import com.ga.portal.query.port.PolicyKeys;
import com.ga.portal.query.rule.SyncStatusRule;
import com.ga.portal.sim.HttpEngineSimulationClient;
import com.ga.portal.sim.SimulationUseCases;
import com.ga.portal.sim.port.EngineCredentials;
import com.ga.portal.sim.port.PolicyResolver;
import com.ga.portal.sim.port.SimFacts;
import com.ga.portal.sim.port.SimTransactions;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * 시뮬레이션 조립(포털 Phase 5 ④) — 웹 애플리케이션에서만. 트랜잭션은 바인딩된 테넌트의 읽기·쓰기(플랫폼 {@code TenantSessionBinder}), 토큰은 인제스트와 같은
 * 파일 빈({@code ServiceTokenSource} — {@code IngestConfiguration}이 인제스트 꺼짐과 무관하게 만든다, 계획 P-9), {@code policyKey}는 조회와 같은 HMAC 빈,
 * {@code asOf}·{@code dataStatus}는 조회와 같은 규칙({@link SyncStatusRule}).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(SimProperties.class)
public class SimConfiguration {

    @Bean
    SimulationUseCases simulationUseCases(SimulationLogRepository logs, SimulationLockRepository locks, TenantRepository tenants,
                                          AgentQueryRepository agents, PolicyKeys policyKeys, SyncStatusRepository sync, ServiceTokenSource tokens,
                                          PlatformTransactionManager transactionManager, ObjectProvider<MeterRegistry> registry, Clock clock,
                                          SimProperties props, @Value("${portal.query.default-sync-delay-seconds}") long defaultSyncDelaySeconds,
                                          Tracing tracing) {
        TransactionTemplate write = new TransactionTemplate(transactionManager);
        TransactionTemplate read = new TransactionTemplate(transactionManager);
        read.setReadOnly(true);
        SimTransactions transactions = new SimTransactions() {
            @Override
            public <T> T write(java.util.function.Supplier<T> work) {
                return write.execute(status -> work.get());
            }
        };
        PolicyResolver policies = (tenant, agent, key) -> read.execute(status -> agents.policies(agent.value()).stream()
                .filter(p -> policyKeys.key(tenant, p).equals(key)).findFirst());
        EngineCredentials credentials = tenant -> tokens.tokenFor(tenant).map(t -> new EngineCredentials.EngineToken(t.value()));
        SimFacts facts = () -> {
            SyncSnapshot s = sync.snapshot();
            long delay = s.configuredDelaySeconds().orElse(defaultSyncDelaySeconds);
            SyncStatusRule.SyncStatus status = SyncStatusRule.evaluate(s.cursor().map(SyncSnapshot.Cursor::state),
                    s.cursor().flatMap(SyncSnapshot.Cursor::caughtUpAt), delay, clock.instant());
            return new SimFacts.Facts(s.cursor().map(SyncSnapshot.Cursor::lastSeq).orElse(0L), s.cursor().map(SyncSnapshot.Cursor::updatedAt),
                    status.name());
        };
        HttpClient http = HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build();
        return new SimulationUseCases(new JdbcSimulationLogStore(logs, locks, tenants), transactions, policies,
                new TracedSimulationClient(new HttpEngineSimulationClient(http, props.readTimeout()), tracing), credentials, facts, new MicrometerSimMetrics(Optional.ofNullable(registry.getIfAvailable())),
                clock, UUID::randomUUID, props.defaultRatePerMinute());
    }
}
