package com.ga.portal.app.recon;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.app.ingest.IngestRigAccess;
import com.ga.portal.app.ingest.SpringTenantTransactions;
import com.ga.portal.infra.persistence.TenantRepository;
import com.ga.portal.infra.testing.SeedData;
import com.ga.portal.ingest.feed.HttpFeedClient;
import com.ga.portal.ingest.feed.ServiceTokenSource;
import com.ga.portal.ingest.testing.StubFeedServer;
import com.ga.portal.recon.engine.HttpStatementClient;
import com.ga.portal.recon.rule.EngineAnswer;
import com.ga.portal.recon.rule.InconclusiveReason;
import com.ga.portal.recon.rule.ReconMode;
import com.ga.portal.recon.testing.StubStatementEngine;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포털 Phase 7 C1·C7(계획 ④): 운영 엔진 어댑터({@link HttpReconEngine})가 테넌트의 {@code engine_base_url}·서비스 토큰으로 명세 스텁(엔진 샘플 사본)과 피드 머리를
 * 실제 HTTP로 읽는다. 토큰이 틀리면 401 → {@code ENGINE_UNAVAILABLE}(재시도 0), 피드가 실패하면 머리가 비어 있다, 샘플 밖 요청은 500.
 */
class ReconEngineIT {

    private final IngestRigAccess rig = new IngestRigAccess();

    private HttpReconEngine engine(String token, StubFeedServer server) {
        ServiceTokenSource tokens = t -> Optional.of(new ServiceTokenSource.ServiceToken(token));
        SpringTenantTransactions fresh = SpringTenantTransactions.requiresNew(rig.binder());
        TenantRepository tenants = new TenantRepository(rig.gateway());
        HttpFeedClient feed = new HttpFeedClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), t -> server.baseUrl(), tokens,
                Duration.ofSeconds(2));
        return new HttpReconEngine(new HttpStatementClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), Duration.ofSeconds(2)),
                feed, fresh, tenants, tokens);
    }

    @Test
    void theAdapterReadsStatementsAndTheFeedHeadOverHttp() {
        TenantId t = TenantId.of(SeedData.uniqueTenant("RCN_ENG"));
        StubStatementEngine stub = StubStatementEngine.of(ReconRig.SAMPLES, "a1");
        try (StubFeedServer server = new StubFeedServer(t.value(), ReconRig.TOKEN).route(StubStatementEngine.PATH_PREFIX, stub)) {
            server.events(ReconRig.a1());
            IngestRigAccess.seedTenant(t, server.baseUrl().toString(), "{}");
            HttpReconEngine engine = engine(ReconRig.TOKEN, server);
            assertThat(engine.statement(t, "A-1001", "202608", ReconMode.CONFIRMED)).isEqualTo(new EngineAnswer.Confirmed(1_800_000, 0, OptionalLong.of(0)));
            assertThat(engine.statement(t, "A-1001", "202702", ReconMode.ESTIMATE)).isEqualTo(new EngineAnswer.Estimated(10, 0, -720_000, 0));
            assertThat(engine.statement(t, "A-1001", "202702", ReconMode.CONFIRMED)).isEqualTo(new EngineAnswer.Failed(InconclusiveReason.NOT_CONFIRMED));
            assertThat(engine.statement(t, "A-1001", "202608", ReconMode.ESTIMATE)).isEqualTo(new EngineAnswer.Failed(InconclusiveReason.MONTH_CLOSED));
            assertThat(engine.statement(t, "A-9999-NONE", "202608", ReconMode.CONFIRMED)).isEqualTo(new EngineAnswer.Failed(InconclusiveReason.AGENT_NOT_FOUND));
            assertThat(engine.statement(t, "A-1001", "202609", ReconMode.CONFIRMED)).as("outside the samples — 500").isEqualTo(EngineAnswer.unavailable());
            assertThat(engine.feedHead(t, 10)).hasValue(11);

            HttpReconEngine wrongToken = engine("not-the-token", server);
            int before = stub.calls();
            assertThat(wrongToken.statement(t, "A-1001", "202608", ReconMode.CONFIRMED)).isEqualTo(EngineAnswer.unavailable());
            assertThat(stub.calls()).as("401 before the handler, no retry").isEqualTo(before);
            assertThat(wrongToken.feedHead(t, 10)).isEmpty();

            server.forceStatus(503);
            assertThat(engine.feedHead(t, 10)).isEmpty();
        }
    }

    @Test
    void aTenantWithoutAnEngineAddressIsUnavailable() {
        TenantId t = TenantId.of(SeedData.uniqueTenant("RCN_NOENG"));
        try (StubFeedServer server = new StubFeedServer(t.value(), ReconRig.TOKEN)) {
            assertThat(engine(ReconRig.TOKEN, server).statement(t, "A-1001", "202608", ReconMode.CONFIRMED)).isEqualTo(EngineAnswer.unavailable());
            URI unused = server.baseUrl();
            assertThat(unused).isNotNull();
        }
    }
}
