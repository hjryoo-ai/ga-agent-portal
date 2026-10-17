package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.platform.spring.jdbc.TenantSessionBinder;
import com.ga.portal.domain.CursorRecord;
import com.ga.portal.domain.IngestEventRecord;
import com.ga.portal.infra.persistence.IngestCursorRepository;
import com.ga.portal.infra.persistence.IngestEventRepository;
import com.ga.portal.infra.persistence.IngestLockRepository;
import com.ga.portal.infra.persistence.TenantRepository;
import com.ga.portal.infra.persistence.ViewResetRepository;
import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.ingest.ContractSchemas;
import com.ga.portal.ingest.EnvelopeValidator;
import com.ga.portal.ingest.apply.IngestApplier;
import com.ga.portal.ingest.apply.IngestLock;
import com.ga.portal.ingest.apply.IngestMetrics;
import com.ga.portal.ingest.apply.IngestWorker;
import com.ga.portal.ingest.apply.Rebuilder;
import com.ga.portal.ingest.feed.FeedClient;
import com.ga.portal.projection.Projector;

import com.ga.portal.ingest.feed.FileFeedAdapter;
import com.ga.portal.ingest.testing.Samples;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인제스트 IT 조립: 실제 PostgreSQL(portal_app·TenantSessionBinder·RLS)과 앱 어댑터, 고정 시계. 같은 DB를 쓰는 워커를 여러 개 만들 수 있다.
 */
final class IngestRig {

    static final PostgresHarness DB = PostgresHarness.get();
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T02:00:00Z"), ZoneOffset.UTC);

    final TenantSessionBinder binder = new TenantSessionBinder(DB.appDataSource());
    final TenantJdbcGateway gateway = new TenantJdbcGateway(DB.appDataSource());
    final SpringTenantTransactions transactions = new SpringTenantTransactions(binder);
    final JdbcIngestStore store = new JdbcIngestStore(new IngestCursorRepository(gateway), new IngestEventRepository(gateway));
    final IngestLock lock = new JdbcIngestLock(new IngestLockRepository(gateway));
    final TenantRepository tenants = new TenantRepository(gateway);

    IngestApplier applier(Projector projector) {
        return applier(projector, lock);
    }

    IngestApplier applier(Projector projector, IngestLock lock) {
        return new IngestApplier(new EnvelopeValidator(ContractSchemas.get()), store, transactions, lock, projector, CLOCK);
    }

    IngestWorker worker(FeedClient feed, Projector projector, int limit) {
        return new IngestWorker(feed, store, transactions, applier(projector), IngestMetrics.NONE, CLOCK, limit);
    }

    IngestWorker worker(FeedClient feed, IngestApplier applier, int limit) {
        return new IngestWorker(feed, store, transactions, applier, IngestMetrics.NONE, CLOCK, limit);
    }

    /** 재구축기 — 뷰 삭제·불변식은 실제 저장소, 피드·프로젝터·페이지 크기·상한은 시험이 고른다. */
    Rebuilder rebuilder(FeedClient feed, Projector projector, int pageLimit, long maxEvents) {
        return new Rebuilder(new EnvelopeValidator(ContractSchemas.get()), store, transactions, lock, projector,
                new JdbcViewReset(new ViewResetRepository(gateway)), feed, CLOCK, pageLimit, maxEvents);
    }

    /**
     * 시험 스트림의 테넌트별 사본 — 머리의 {@code tenantId}만 그 테넌트로 바꾼다(스트림은 GA01로 쓰여 있고 IT 테넌트는 고유 이름이다).
     * 나머지 줄은 바이트 그대로.
     */
    static Path streamFor(TenantId t, String stream) {
        try {
            List<String> lines = Files.readAllLines(Samples.stream(stream), StandardCharsets.UTF_8);
            ObjectNode header = (ObjectNode) Samples.JSON.readTree(lines.getFirst());
            header.put("tenantId", t.value());
            List<String> out = new ArrayList<>(lines);
            out.set(0, Samples.JSON.writeValueAsString(header));
            Path dir = Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"), "portal-ingest-it"));
            Path file = dir.resolve(t.value() + "-" + stream);
            Files.write(file, out, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 테넌트마다 그 테넌트의 사본을 읽는 파일 피드. */
    static FileFeedAdapter feed(String stream) {
        Map<TenantId, Path> copies = new ConcurrentHashMap<>();
        return new FileFeedAdapter(t -> copies.computeIfAbsent(t, x -> streamFor(x, stream)));
    }

    CursorRecord cursor(TenantId t) {
        return transactions.inTransaction(t, store::cursor).orElseThrow();
    }

    /** 테넌트의 ingest_event 행(seq 순) — 테넌트 컬럼 없이 비교할 수 있게 문자열로. */
    List<String> eventRows(TenantId t) {
        return DB.asApp(t.value(), c -> {
            List<String> rows = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT event_id, seq, event_type, received_at, status, coalesce(error, ''), payload_hash
                      FROM ingest_event WHERE tenant_id = ? ORDER BY seq, event_id
                    """)) {
                ps.setString(1, t.value());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(String.join("|", rs.getString(1), rs.getString(2), rs.getString(3),
                                rs.getObject(4, java.time.OffsetDateTime.class).toInstant().toString(), rs.getString(5), rs.getString(6), rs.getString(7)));
                    }
                }
            }
            return rows;
        });
    }

    List<IngestEventRecord> events(TenantId t, String status) {
        return eventRows(t).stream().filter(r -> r.split("\\|", -1)[4].equals(status))
                .map(r -> {
                    String[] f = r.split("\\|", -1);
                    return transactions.inTransaction(t, () -> store.event(f[0])).orElseThrow();
                }).toList();
    }

    /** {@code tenant} 행(ACTIVE, 엔진 주소) — 마이그레이터로 테넌트 설정 후. */
    static void seedTenant(TenantId t, String engineBaseUrl, String params) {
        DB.seed(t.value(), c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO tenant (tenant_id, name, engine_base_url, status, payout_day_rule, params)
                    VALUES (?, ?, ?, 'ACTIVE', 'NEXT_MONTH_D10', ?::jsonb)
                    """)) {
                ps.setString(1, t.value());
                ps.setString(2, "시험 GA " + t.value());
                ps.setString(3, engineBaseUrl);
                ps.setString(4, params);
                ps.executeUpdate();
            }
        });
    }
}
