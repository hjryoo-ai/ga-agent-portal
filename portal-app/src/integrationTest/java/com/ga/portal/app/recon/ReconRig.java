package com.ga.portal.app.recon;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.app.ingest.IngestCommands;
import com.ga.portal.app.ingest.IngestRigAccess;
import com.ga.portal.app.ingest.JdbcViewReset;
import com.ga.portal.app.ingest.SpringTenantTransactions;
import com.ga.portal.domain.ReconTrigger;
import com.ga.portal.infra.persistence.AuditAccessRepository;
import com.ga.portal.infra.persistence.EarningsMonthRepository;
import com.ga.portal.infra.persistence.IngestCursorRepository;
import com.ga.portal.infra.persistence.NotificationRepository;
import com.ga.portal.infra.persistence.ReconLockRepository;
import com.ga.portal.infra.persistence.ReconResultRepository;
import com.ga.portal.infra.persistence.ReconRunRepository;
import com.ga.portal.infra.persistence.TenantRepository;
import com.ga.portal.infra.persistence.ViewResetRepository;
import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.infra.testing.SeedData;
import com.ga.portal.ingest.ContractSchemas;
import com.ga.portal.ingest.EnvelopeValidator;
import com.ga.portal.ingest.apply.IngestMetrics;
import com.ga.portal.ingest.apply.Rebuilder;
import com.ga.portal.ingest.feed.FeedClient;
import com.ga.portal.ingest.feed.ServiceTokenSource;
import com.ga.portal.ingest.testing.StubFeedServer;
import com.ga.portal.projection.testing.Envelopes;
import com.ga.portal.recon.ReconRunner;
import com.ga.portal.recon.engine.HttpStatementClient;
import com.ga.portal.recon.port.ReconRebuild;
import com.ga.portal.recon.testing.StubStatementEngine;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.node.ObjectNode;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * 대사 IT 조립(포털 Phase 7): 실제 PostgreSQL(portal_app·TenantSessionBinder·RLS)·앱 어댑터 그대로({@link JdbcReconStore}·{@link TransactionalReconLock}·
 * {@link RebuilderReconRebuild}·{@link HttpReconEngine}). 엔진 명세는 엔진 샘플 사본 스텁을 HTTP로({@link StubStatementEngine} — {@link StubFeedServer}의
 * 같은 토큰 검사), 피드는 그 테넌트의 파일 피드(인제스트·재구축·피드 머리가 같은 스트림). 시계는 바꿀 수 있다 — 인제스트는 {@code IngestRig}의 고정 시계
 * (2026-10-17 02:00Z = 11:00 KST)로 따라잡고 회차도 같은 시각에서 시작한다.
 */
final class ReconRig implements AutoCloseable {

    static final PostgresHarness DB = IngestRigAccess.DB;
    static final String TOKEN = "recon-it-token";
    static final String OPERATOR = "cli:ops-kim";
    static final Path SAMPLES = StubStatementEngine.samplesDir(Path.of(System.getProperty("ga.repoRoot")));

    /** 바꿀 수 있는 시계(회차·재구축·재대사). */
    static final class MovingClock extends Clock {
        volatile Instant now;

        MovingClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    final IngestRigAccess ingest = new IngestRigAccess();
    final MovingClock clock = new MovingClock(IngestRigAccess.CLOCK.instant());
    final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    final TenantId tenant;
    final List<ObjectNode> stream;
    final StubStatementEngine stub;
    final StubFeedServer server;
    final FeedClient feed;
    final ReconRunRepository runs;
    final ReconResultRepository results;
    private final List<String> rebuildActors = new ArrayList<>();
    private UnaryOperator<ReconRebuild> rebuildWrapper = UnaryOperator.identity();

    /** 새 테넌트 하나: 엔진 상태 {@code engineState}의 스텁, 스트림 {@code stream}, 테넌트 params {@code params}. 스트림은 아직 적용하지 않는다. */
    ReconRig(String prefix, String engineState, List<ObjectNode> stream, String params) {
        this.tenant = TenantId.of(SeedData.uniqueTenant(prefix));
        this.stream = List.copyOf(stream);
        this.stub = StubStatementEngine.of(SAMPLES, engineState);
        this.server = new StubFeedServer(tenant.value(), TOKEN).route(StubStatementEngine.PATH_PREFIX, stub);
        this.feed = IngestRigAccess.feed(tenant, this.stream);
        IngestRigAccess.seedTenant(tenant, server.baseUrl().toString(), params);
        IngestRigAccess.operator(tenant, OPERATOR);
        this.runs = new ReconRunRepository(ingest.gateway());
        this.results = new ReconResultRepository(ingest.gateway());
    }

    static List<ObjectNode> a1() {
        return new ArrayList<>(Envelopes.nodes(Envelopes.a1()));
    }

    /** 스트림 전부를 운영 파생기와 함께 적용(커서 RUNNING·{@code caught_up_at} = 고정 시계). */
    ReconRig ingestAll() {
        ingest.derivingWorker(feed).runOnce(tenant);
        return this;
    }

    /** 재구축 포트를 감싼다(시험이 재구축 뒤에 엔진을 바꿀 때). */
    ReconRig wrapRebuild(UnaryOperator<ReconRebuild> wrapper) {
        this.rebuildWrapper = wrapper;
        return this;
    }

    static ReconProperties.Defaults defaults() {
        return new ReconProperties.Defaults(4, true, 900, 0, 500, 900);
    }

    /** 엔진 어댑터(운영 그대로 — 명세는 HTTP 스텁, 머리는 파일 피드). */
    HttpReconEngine engine() {
        SpringTenantTransactions fresh = SpringTenantTransactions.requiresNew(ingest.binder());
        ServiceTokenSource tokens = t -> Optional.of(new ServiceTokenSource.ServiceToken(TOKEN));
        return new HttpReconEngine(new HttpStatementClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), Duration.ofSeconds(2)),
                feed, fresh, new TenantRepository(ingest.gateway()), tokens);
    }

    ReconService service() {
        SpringTenantTransactions fresh = SpringTenantTransactions.requiresNew(ingest.binder());
        TenantRepository tenants = new TenantRepository(ingest.gateway());
        Rebuilder rebuilder = new Rebuilder(new EnvelopeValidator(ContractSchemas.get()), ingest.store(), fresh, ingest.lock(),
                IngestRigAccess.projector(ingest.gateway()), new JdbcViewReset(new ViewResetRepository(ingest.gateway())), feed,
                clock, 200, 50_000);
        ReconRebuild real = new RebuilderReconRebuild(rebuilder, new AuditAccessRepository(ingest.gateway()), IngestMetrics.NONE, clock);
        ReconRebuild recording = (t, actor) -> {
            rebuildActors.add(actor);
            clock.now = clock.now.plusSeconds(30);
            return real.rebuild(t, actor);
        };
        ReconRunner runner = new ReconRunner(
                new JdbcReconStore(ingest.binder(), tenants, new IngestCursorRepository(ingest.gateway()), new EarningsMonthRepository(ingest.gateway()),
                        results, runs, new NotificationRepository(ingest.gateway()), defaults(), clock),
                engine(),
                new TransactionalReconLock(ingest.binder(), new ReconLockRepository(ingest.gateway())),
                rebuildWrapper.apply(recording),
                new MicrometerReconMetrics(Optional.of(meters)),
                interval -> { },
                clock);
        return new ReconService(runner, () -> List.of(tenant), fresh, tenants);
    }

    ReconRunner.Outcome run(String... months) {
        return service().run(tenant, ReconTrigger.CLI, OPERATOR, List.of(months));
    }

    /** CLI 명령(실제 인가·감사) — {@code status}·{@code recon run}. 전 테넌트 목록은 이 테넌트 하나. */
    ReconCommands commands() {
        return commands(List.of(tenant));
    }

    ReconCommands commands(List<TenantId> directory) {
        return new ReconCommands(IngestRigAccess.transactions(ingest), new com.ga.portal.infra.persistence.IdentityLinkRepository(ingest.gateway()),
                new AuditAccessRepository(ingest.gateway()), service(), engine(), new IngestCursorRepository(ingest.gateway()), results, runs,
                new com.ga.portal.infra.persistence.InquiryOperatorRepository(ingest.gateway()), () -> directory, clock);
    }

    List<String> rebuildActors() {
        return List.copyOf(rebuildActors);
    }

    /** 테넌트 params를 바꾼다(마이그레이터 — 하네스 제어와 같은 뜻). */
    void params(String json) {
        DB.seed(tenant.value(), c -> SeedData.exec(c, "UPDATE tenant SET params = ?::jsonb WHERE tenant_id = ?", json, tenant.value()));
    }

    /**
     * 인위적 라인 누락(지시문 ⑤ — 하네스 {@code corrupt}와 같은 SQL): 그 라인 한 행을 지우고 그 달 합을 남은 라인으로 다시 쓴다(마이그레이터 — 운영 롤 직접 SQL).
     * 시험 코드의 재계산이지 포털 코드의 것이 아니다.
     */
    void corruptLine(String calcId) {
        DB.seed(tenant.value(), c -> {
            String[] key = new String[2];
            try (PreparedStatement ps = c.prepareStatement("SELECT agent_id, close_ym FROM earnings_line WHERE tenant_id = ? AND calc_id = ?")) {
                ps.setString(1, tenant.value());
                ps.setString(2, calcId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    key[0] = rs.getString(1);
                    key[1] = rs.getString(2);
                }
            }
            SeedData.exec(c, "DELETE FROM earnings_line WHERE tenant_id = ? AND calc_id = ?", tenant.value(), calcId);
            SeedData.exec(c, """
                    UPDATE earnings_month m
                       SET gross = (SELECT coalesce(sum(l.amount), 0) FROM earnings_line l WHERE l.tenant_id = m.tenant_id AND l.agent_id = m.agent_id
                                       AND l.close_ym = m.close_ym AND l.comm_type NOT IN ('CLAWBACK', 'ADJUSTMENT'))
                     WHERE m.tenant_id = ? AND m.agent_id = ? AND m.close_ym = ?
                    """, tenant.value(), key[0], key[1]);
        });
    }

    /** 한 열의 값들 — 그 테넌트 행만, 정렬 고정. */
    List<String> rows(String sql) {
        return DB.asApp(tenant.value(), c -> {
            List<String> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, tenant.value());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(rs.getString(1));
                    }
                }
            }
            return out;
        });
    }

    /** {@code recon_result} 행(테넌트 열 제외, 텍스트). */
    List<String> reconResults() {
        return rows("""
                SELECT concat_ws('|', agent_id, close_ym, run_at, portal_gross, portal_offset, portal_adjustment, coalesce(engine_gross::text, '-'),
                                 coalesce(engine_offset::text, '-'), coalesce(engine_adjustment::text, '-'), coalesce(engine_net::text, '-'), status, source,
                                 source_seq)
                  FROM recon_result WHERE tenant_id = ? ORDER BY agent_id, close_ym, run_at, source, source_seq
                """);
    }

    /** {@code recon_run} 행(테넌트 열 제외, 텍스트). */
    List<String> reconRuns() {
        return rows("""
                SELECT concat_ws('|', run_at, actor, trigger, checked, matched, drift_new, drift_unresolved, inconclusive, engine_calls, result,
                                 coalesce(rebuilt_at::text, '-'), coalesce(rebuild_outcome, '-'))
                  FROM recon_run WHERE tenant_id = ? ORDER BY run_at
                """);
    }

    String dataStatus(String agentId, String ym) {
        return rows("SELECT data_status FROM earnings_month WHERE tenant_id = ? AND agent_id = '" + agentId + "' AND close_ym = '" + ym + "'").getFirst();
    }

    double counter(String name, String... tags) {
        io.micrometer.core.instrument.Counter c = meters.find(name).tags(tags).counter();
        return c == null ? 0 : c.count();
    }

    @Override
    public void close() {
        server.close();
    }

    /** 감사 행 수(행위자·동작). */
    long audits(String actor, String action) {
        return DB.<Long>asApp(tenant.value(), c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM audit_access WHERE tenant_id = ? AND actor_subject = ? AND action = ?")) {
                ps.setString(1, tenant.value());
                ps.setString(2, actor);
                ps.setString(3, action);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        });
    }

    static String rebuildSucceeded() {
        return IngestCommands.REBUILD_SUCCEEDED;
    }
}
