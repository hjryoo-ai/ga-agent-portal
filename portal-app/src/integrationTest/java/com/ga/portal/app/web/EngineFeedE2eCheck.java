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
 * 200이고 계약({@code portal-api.openapi.yaml})을 통과하며 증권번호 원문이 없다 ③ (포털 Phase 5 ⑨) 시뮬레이션 한 쌍 — 신계약(엔진 샘플 S1의 요청)과
 * 해약 영향(S5 — 그 설계사의 원장 {@code policyKey}): 실 엔진의 답이 엔진 계약 사본 스키마를 통과해야 포털이 200을 내고, 포털 응답이 계약을 통과하며,
 * {@code simulation_log}에 그 둘이 {@code SUCCEEDED}로 남는다 ④ (포털 Phase 6 ⑨) GA01의 {@code notification} 행 수 &gt; 0 — 알림은 피드 적용
 * 트랜잭션에서 자연히 생긴다(종류별 수만 적는다 — 키·매개변수는 읽지 않는다) ⑤ (포털 Phase 7 계획 ⑦) {@code run.sh}가 부른 실 CLI
 * {@code recon run --tenant=GA01 --ym=202608,202702}의 종료 코드 0, 마지막 CLI 회차 행 = 검사 2·일치 2·새 DRIFT 0·미해결 0·판정 불가 0·{@code OK}(202608
 * CONFIRMED 셋 비교, 202702 ESTIMATE 둘 비교), {@code recon_result} 0행. 결과를 RESULT.md에 날짜·커밋·해시와 함께 쓴다(토큰·subject·비밀 없음).
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
            ApiContract.check("GET", "/api/v1/me/earnings/202608", r);
            contract = r.statusCode() == 200 ? "OK" : "status " + r.statusCode();
        } catch (AssertionError e) {
            contract = "VIOLATION " + e.getMessage().lines().findFirst().orElse("");
        }
        boolean apiOk = r.statusCode() == 200 && contract.equals("OK") && !body.contains("POL-2026-0001");
        ok &= apiOk;

        // ③ 시뮬레이션 한 쌍(실 엔진) — 포털 요청은 문자열 숫자, policyKey는 그 설계사의 원장에서
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<byte[]> limits = http.send(HttpRequest.newBuilder(URI.create(portal + "/api/v1/me/limits?status=ALL"))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        String policyKey = Envelopes.JSON.readTree(limits.body()).path("items").path(0).path("policyKey").asString("");
        Map<String, String> simulations = new LinkedHashMap<>();
        simulations.put("/api/v1/me/simulations/new-contract",
                "{\"productKey\":\"WHOLE-LIFE-20Y\",\"monthlyPremium\":\"150000\",\"contractDate\":\"2026-08-01\"}");
        simulations.put("/api/v1/me/simulations/policy-event",
                "{\"policyKey\":\"" + policyKey + "\",\"eventType\":\"CANCEL\",\"asOfDate\":\"2027-02-15\",\"elapsedInstallments\":\"7\"}");
        Map<String, String> simResults = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : simulations.entrySet()) {
            HttpResponse<byte[]> s = http.send(HttpRequest.newBuilder(URI.create(portal + e.getKey())).header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(e.getValue())).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            String simContract;
            try {
                ApiContract.check("POST", e.getKey(), s);
                simContract = s.statusCode() == 200 ? "OK" : "status " + s.statusCode();
            } catch (AssertionError err) {
                simContract = "VIOLATION " + err.getMessage().lines().findFirst().orElse("");
            }
            boolean simOk = s.statusCode() == 200 && simContract.equals("OK") && !new String(s.body(), StandardCharsets.UTF_8).contains("POL-2026-0001");
            ok &= simOk;
            simResults.put(e.getKey(), "상태 " + s.statusCode() + ", 계약 " + simContract + ", 본문 SHA-256 `" + sha256(s.body()) + "` (" + s.body().length + " bytes)");
        }
        List<String> logRows = simulationLog(jdbcUrl);
        boolean logOk = logRows.equals(List.of("NEW_CONTRACT SUCCEEDED 200", "POLICY_EVENT SUCCEEDED 200"));
        ok &= logOk;

        // ④ 알림 행(포털 Phase 6) — 수만
        Map<String, Integer> notifications = notificationCounts(jdbcUrl);
        int notificationRows = notifications.values().stream().mapToInt(Integer::intValue).sum();
        boolean notificationsOk = notificationRows > 0;
        ok &= notificationsOk;

        // ⑤ 대사(포털 Phase 7) — 실 CLI의 종료 코드(run.sh가 상태 디렉터리에 쓴다), 회차 행의 수, 결과 행 수
        String reconExit = Files.readString(state.resolve("recon.exit"), StandardCharsets.UTF_8).strip();
        List<String> recon = recon(jdbcUrl);
        boolean reconOk = reconExit.equals("0") && recon.equals(List.of("CLI 2 2 0 0 0 OK", "recon_result 0"));
        ok &= reconOk;

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
        report.add("");
        report.add("## 시뮬레이션 한 쌍(실 엔진 — 포털 Phase 5)");
        report.add("");
        simResults.forEach((path, v) -> report.add("- `POST " + path + "`: " + v));
        report.add("- `simulation_log`(GA01, 시각 순): " + String.join(" · ", logRows) + " — " + (logOk ? "OK" : "기대 NEW_CONTRACT·POLICY_EVENT SUCCEEDED 200 둘"));
        report.add("");
        report.add("## 알림(포털 Phase 6)");
        report.add("");
        StringBuilder byType = new StringBuilder();
        notifications.forEach((type, n) -> byType.append(byType.isEmpty() ? "" : " · ").append(type).append(' ').append(n));
        report.add("- `notification`(GA01) 행 수 " + notificationRows + (byType.isEmpty() ? "" : " (" + byType + ")") + " — "
                + (notificationsOk ? "OK" : "기대 1행 이상"));
        report.add("");
        report.add("## 대사(포털 Phase 7 — `recon run --tenant=GA01 --ym=202608,202702`)");
        report.add("");
        report.add("- 종료 코드 " + reconExit + ", 마지막 CLI 회차(검사·일치·새 DRIFT·미해결·판정 불가·결과) `" + String.join("` · `", recon) + "` — "
                + (reconOk ? "OK" : "기대 종료 0, CLI 2 2 0 0 0 OK, recon_result 0"));
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

    /** GA01의 시뮬레이션 로그 행 — 종류·상태·엔진 상태(본문·키는 읽지 않는다). */
    static List<String> simulationLog(String jdbcUrl) throws Exception {
        List<String> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(jdbcUrl, "portal_app", "app_local_only")) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT);
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT kind, status, coalesce(engine_status::text, '-') FROM simulation_log WHERE tenant_id = ? ORDER BY created_at, sim_id")) {
                ps.setString(1, TENANT);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(rs.getString(1) + " " + rs.getString(2) + " " + rs.getString(3));
                    }
                }
            }
            c.rollback();
        }
        return out;
    }

    /** GA01의 알림 행 수(종류별) — 종류 열만 읽는다. */
    static Map<String, Integer> notificationCounts(String jdbcUrl) throws Exception {
        Map<String, Integer> out = new LinkedHashMap<>();
        try (Connection c = DriverManager.getConnection(jdbcUrl, "portal_app", "app_local_only")) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT);
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT type, count(*) FROM notification WHERE tenant_id = ? GROUP BY type ORDER BY type")) {
                ps.setString(1, TENANT);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.put(rs.getString(1), rs.getInt(2));
                    }
                }
            }
            c.rollback();
        }
        return out;
    }

    /** GA01의 마지막 CLI 회차(수와 결과만)와 {@code recon_result} 행 수. */
    static List<String> recon(String jdbcUrl) throws Exception {
        List<String> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(jdbcUrl, "portal_app", "app_local_only")) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, TENANT);
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT concat_ws(' ', trigger, checked, matched, drift_new, drift_unresolved, inconclusive, result) FROM recon_run
                     WHERE tenant_id = ? AND trigger = 'CLI' ORDER BY run_at DESC LIMIT 1
                    """)) {
                ps.setString(1, TENANT);
                try (ResultSet rs = ps.executeQuery()) {
                    out.add(rs.next() ? rs.getString(1) : "no CLI run");
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM recon_result WHERE tenant_id = ?")) {
                ps.setString(1, TENANT);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    out.add("recon_result " + rs.getInt(1));
                }
            }
            c.rollback();
        }
        return out;
    }

    static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
