package com.ga.portal.app.recon;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.trace.Tracing;
import com.ga.portal.infra.persistence.TenantRepository;
import com.ga.portal.ingest.apply.TenantTransactions;
import com.ga.portal.ingest.feed.FeedClient;
import com.ga.portal.ingest.feed.FeedException;
import com.ga.portal.ingest.feed.ServiceTokenSource;
import com.ga.portal.recon.engine.HttpStatementClient;
import com.ga.portal.recon.port.ReconEngine;
import com.ga.portal.recon.port.ServiceToken;
import com.ga.portal.recon.rule.EngineAnswer;
import com.ga.portal.recon.rule.ReconMode;

import java.net.URI;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * {@link ReconEngine}: 명세 조회는 {@link HttpStatementClient}(테넌트의 {@code engine_base_url}, 인제스트와 같은 토큰 파일), 피드 머리는 인제스트와 같은 피드
 * 클라이언트로 {@code after = last_seq, limit 1}. 재시도 0, 예외를 던지지 않는다(닿지 못하면 {@code ENGINE_UNAVAILABLE}·빈 머리).
 */
final class HttpReconEngine implements ReconEngine {

    private final HttpStatementClient statements;
    private final FeedClient feed;
    private final TenantTransactions transactions;
    private final TenantRepository tenants;
    private final ServiceTokenSource tokens;
    private final Tracing tracing;

    HttpReconEngine(HttpStatementClient statements, FeedClient feed, TenantTransactions transactions, TenantRepository tenants, ServiceTokenSource tokens,
                    Tracing tracing) {
        this.statements = statements;
        this.feed = feed;
        this.transactions = transactions;
        this.tenants = tenants;
        this.tokens = tokens;
        this.tracing = tracing;
    }

    @Override
    public EngineAnswer statement(TenantId tenant, String agentId, String ym, ReconMode mode) {
        // 스팬 engine.statement — 방식과 답의 종류만(설계사·달·금액은 싣지 않는다)
        Tracing.Span span = tracing.start("engine.statement").kind("mode", mode).kind("agent_id", agentId);   // 주입 S11
        try {
            EngineAnswer answer = fetch(tenant, agentId, ym, mode);
            span.kind("outcome", switch (answer) {
                case EngineAnswer.Confirmed c -> "CONFIRMED";
                case EngineAnswer.Estimated e -> "ESTIMATED";
                case EngineAnswer.Failed f -> f.reason().name();
            });
            return answer;
        } finally {
            span.close();
        }
    }

    private EngineAnswer fetch(TenantId tenant, String agentId, String ym, ReconMode mode) {
        Optional<String> base = transactions.inTransaction(tenant, tenants::current).map(t -> t.engineBaseUrl());
        Optional<ServiceToken> token = tokens.tokenFor(tenant).map(t -> new ServiceToken(t.value()));
        if (base.isEmpty() || token.isEmpty()) {
            return EngineAnswer.unavailable();
        }
        return statements.fetch(URI.create(base.get()), token.get(), agentId, ym, mode);
    }

    @Override
    public OptionalLong feedHead(TenantId tenant, long afterSeq) {
        try {
            return OptionalLong.of(feed.fetch(tenant, afterSeq, 1).headSeq());
        } catch (FeedException | IllegalArgumentException e) {
            return OptionalLong.empty();
        }
    }
}
