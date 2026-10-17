package com.ga.portal.app.web;

import com.ga.portal.projection.testing.Envelopes;
import com.ga.portal.projection.testing.GoldenViews;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 실 엔진 E2E 판정(지시문 D-16) — {@code e2e/engine-feed/run.sh}가 부른다({@code ./gradlew :portal-app:e2eCheck --args=...}, 정규 빌드 밖).
 * ① 포털 DB의 GA01 뷰 9표 = 골든 뷰 {@code a1}(둘째 판 — {@code ProjectionGoldenIT}와 같은 정규형) ② {@code GET /api/v1/me/earnings/202608}(시험 발급기 토큰)이
 * 200이고 계약({@code portal-api.openapi.yaml})을 통과하며 증권번호 원문이 없다. 결과를 RESULT.md에 날짜·커밋·해시와 함께 쓴다(토큰·subject·비밀 없음).
 *
 * <p>인자: {@code <stateDir> <jdbcUrl> <portalBaseUrl> <resultFile> <engineCommit> <portalCommit>}. DB는 {@code portal_app}(로컬 기본 자격 증명 —
 * {@code docker/postgres/init-roles.sql})으로 테넌트를 설정해 읽는다.
 */
public final class EngineFeedE2eCheck {

    static final String TENANT = "GA01";
    private static final Set<String> TIMESTAMPS = Set.of("estimate_as_of", "confirmed_at", "paid_at", "occurred_at", "cleared_at", "run_at");

    private EngineFeedE2eCheck() {
    }

    public static void main(String[] args) throws Exception {
        Path state = Path.of(args[0]);
        String jdbcUrl = args[1];
        String portal = args[2];
        Path result = Path.of(args[3]);
        String engineCommit = args[4];
        String portalCommit = args[5];
        List<String> report = new ArrayList<>();
        boolean ok = true;

        // ① 뷰 9표 = 골든 a1
        Map<String, List<String>> actual = views(jdbcUrl);
        Map<String, String> tableResults = new LinkedHashMap<>();
        for (String table : GoldenViews.TABLES) {
            List<String> expected = Files.readAllLines(GoldenViews.dir("a1").resolve(table + ".jsonl"), StandardCharsets.UTF_8).stream()
                    .filter(l -> !l.isBlank()).map(l -> GoldenViews.canonical(Envelopes.JSON.readTree(l))).sorted().toList();
            boolean same = expected.equals(actual.get(table));
            ok &= same;
            tableResults.put(table, (same ? "OK" : "DIFF") + " (" + actual.get(table).size() + " rows, golden " + expected.size() + ")");
        }

        // ② 조회 API 한 번 — 계약·증권번호
        String token = Files.readString(state.resolve("token"), StandardCharsets.UTF_8).strip();
        HttpResponse<byte[]> r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(portal + "/api/v1/me/earnings/202608"))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        String body = new String(r.body(), StandardCharsets.UTF_8);
        String contract;
        try {
            ApiContract.check("/api/v1/me/earnings/202608", r);
            contract = r.statusCode() == 200 ? "OK" : "status " + r.statusCode();
        } catch (AssertionError e) {
            contract = "VIOLATION " + e.getMessage().lines().findFirst().orElse("");
        }
        boolean apiOk = r.statusCode() == 200 && contract.equals("OK") && !body.contains("POL-2026-0001");
        ok &= apiOk;

        report.add("# 실 엔진 E2E 결과 (e2e/engine-feed)");
        report.add("");
        report.add("- 일시(UTC): " + OffsetDateTime.now(ZoneOffset.UTC));
        report.add("- 판정: **" + (ok ? "PASS" : "FAIL") + "**");
        report.add("- 엔진 커밋: `" + engineCommit + "`, 포털 커밋: `" + portalCommit + "`");
        report.add("- A-1 스트림(계약 사본) SHA-256: `" + sha256(Files.readAllBytes(Envelopes.a1())) + "`");
        report.add("");
        report.add("## 뷰 9표 = 골든 `a1`");
        report.add("");
        report.add("| 표 | 결과 |");
        report.add("|---|---|");
        tableResults.forEach((t, v) -> report.add("| `" + t + "` | " + v + " |"));
        report.add("");
        report.add("## `GET /api/v1/me/earnings/202608`");
        report.add("");
        report.add("- 상태 " + r.statusCode() + ", 계약 " + contract + ", 증권번호 원문 " + (body.contains("POL-2026-0001") ? "있음" : "없음"));
        report.add("- 응답 본문 SHA-256: `" + sha256(r.body()) + "` (" + r.body().length + " bytes), ETag `" + r.headers().firstValue("etag").orElse("-") + "`");
        Files.write(result, report, StandardCharsets.UTF_8);
        System.out.println("[e2e] " + (ok ? "PASS" : "FAIL") + " — " + result);
        if (!ok) {
            System.exit(1);
        }
    }

    static Map<String, List<String>> views(String jdbcUrl) throws Exception {
        Map<String, List<String>> out = new LinkedHashMap<>();
        try (Connection c = DriverManager.getConnection(jdbcUrl, "portal_app", "app_local_only")) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT);
                ps.execute();
            }
            for (String table : GoldenViews.TABLES) {
                List<ObjectNode> rows = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement("SELECT (to_jsonb(v) - 'tenant_id')::text FROM " + table + " v WHERE tenant_id = ?")) {
                    ps.setString(1, TENANT);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            ObjectNode row = (ObjectNode) Envelopes.JSON.readTree(rs.getString(1));
                            for (String col : TIMESTAMPS) {
                                if (row.has(col) && !row.path(col).isNull()) {
                                    row.put(col, OffsetDateTime.parse(row.path(col).asString()).toInstant().toString());
                                }
                            }
                            rows.add(row);
                        }
                    }
                }
                out.put(table, GoldenViews.lines(rows));
            }
            c.rollback();
        }
        return out;
    }

    static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
