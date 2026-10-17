package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.platform.spring.jdbc.TenantJdbcGateway;
import com.ga.portal.infra.persistence.AgentProfileRepository;
import com.ga.portal.infra.persistence.ClawbackItemRepository;
import com.ga.portal.infra.persistence.DeferralItemRepository;
import com.ga.portal.infra.persistence.EarningsLineRepository;
import com.ga.portal.infra.persistence.EarningsMonthRepository;
import com.ga.portal.infra.persistence.PolicyFlagRepository;
import com.ga.portal.infra.persistence.PolicyLimitRepository;
import com.ga.portal.infra.persistence.ReconResultRepository;
import com.ga.portal.infra.testing.PostgresHarness;
import com.ga.portal.ingest.feed.FileFeedAdapter;
import com.ga.portal.projection.ViewProjector;
import com.ga.portal.projection.testing.Envelopes;
import com.ga.portal.projection.testing.GoldenViews;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Phase 2 IT 조립: PostgreSQL 뷰 저장소의 {@link ViewProjector}, 골든 스트림의 파일 피드, DB 뷰의 골든 정규형. */
final class ViewRig {

    static final PostgresHarness DB = IngestRig.DB;
    private static final Set<String> TIMESTAMPS = Set.of("estimate_as_of", "confirmed_at", "paid_at", "occurred_at", "cleared_at", "run_at");

    static ViewProjector projector(TenantJdbcGateway gateway) {
        return new ViewProjector(new JdbcViewStore(new AgentProfileRepository(gateway), new EarningsLineRepository(gateway),
                new EarningsMonthRepository(gateway), new PolicyLimitRepository(gateway), new DeferralItemRepository(gateway),
                new ClawbackItemRepository(gateway), new PolicyFlagRepository(gateway), new ReconResultRepository(gateway)));
    }

    /** 골든 스트림(머리 줄 없음)을 그 테넌트의 파일 피드로 — 머리 {@code {tenantId, schemaVersion}}를 붙인 사본. */
    static FileFeedAdapter feed(TenantId t, List<ObjectNode> events) {
        try {
            List<String> lines = new ArrayList<>();
            lines.add("{\"tenantId\":\"" + t.value() + "\",\"schemaVersion\":1}");
            events.forEach(e -> lines.add(e.toString()));
            Path dir = Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"), "portal-projection-it"));
            Path file = dir.resolve(t.value() + ".jsonl");
            Files.write(file, lines, StandardCharsets.UTF_8);
            return new FileFeedAdapter(x -> file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static FileFeedAdapter feed(TenantId t, Path stream) {
        return feed(t, Envelopes.nodes(stream));
    }

    /** 그 테넌트의 뷰 9표를 골든 정규형으로({@link GoldenViews} — 시각은 UTC, {@code limit_multiple}은 V5부터 원문 TEXT, tenant_id 제외). */
    static Map<String, List<String>> views(TenantId t) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String table : GoldenViews.TABLES) {
            String select = "SELECT (to_jsonb(v) - 'tenant_id')::text FROM " + table + " v WHERE tenant_id = ?";   // limit_multiple은 V5부터 TEXT 원문
            List<ObjectNode> rows = DB.asApp(t.value(), c -> {
                List<ObjectNode> list = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(select)) {
                    ps.setString(1, t.value());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            ObjectNode row = (ObjectNode) Envelopes.JSON.readTree(rs.getString(1));
                            for (String col : TIMESTAMPS) {
                                if (row.has(col) && !row.path(col).isNull()) {
                                    row.put(col, OffsetDateTime.parse(row.path(col).asString()).toInstant().toString());
                                }
                            }
                            list.add(row);
                        }
                    }
                }
                return list;
            });
            out.put(table, GoldenViews.lines(rows));
        }
        return out;
    }
}
