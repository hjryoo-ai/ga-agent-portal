package com.ga.portal.app.web;

import com.ga.portal.infra.testing.SeedData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C7: 약한 ETag·304·{@code Cache-Control: private, no-cache}({@code no-store} 없음). 304는 본문 없이 같은 ETag·캐시 헤더. 남의 자원은 ETag가 맞아도 304가 아니라
 * 404(소유 확인 뒤 비교). 같은 {@code seq}에서 지연 상태가 바뀌거나(Q4) 그 응답이 읽는 설정이 바뀌면 ETag가 바뀐다 — 읽지 않는 설정은 ETag를 바꾸지 않는다.
 * T2(설계사 A2)에서 돈다 — 커서를 이 시험이 쓴다.
 */
class CachingIT extends ApiIntegrationTest {

    /**
     * T2의 커서 전체를 이 시험이 정한다 — 같은 JVM의 폴링 시험({@code IngestPollingIT})이 그 사이 모든 테넌트를 돌며 T2의 커서를 {@code seq 0}으로 만들어 둘 수
     * 있다(전 컬럼 덮어쓰기 — 일부만 고치면 순서에 따라 결과가 달라진다).
     */
    @BeforeAll
    static void cursor() {
        DB.seed(T2.value(), c -> SeedData.exec(c, """
                INSERT INTO ingest_cursor (tenant_id, last_seq, state, updated_at, caught_up_at, error)
                VALUES (?, 40, 'RUNNING', TIMESTAMPTZ '2026-10-17 00:00:00+00', ?, NULL)
                ON CONFLICT (tenant_id) DO UPDATE SET last_seq = EXCLUDED.last_seq, last_event_id = NULL, state = EXCLUDED.state,
                    updated_at = EXCLUDED.updated_at, caught_up_at = EXCLUDED.caught_up_at, error = NULL
                """, T2.value(), OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)));
    }

    static void caughtUpAt(Instant at) {
        DB.seed(T2.value(), c -> SeedData.exec(c, "UPDATE ingest_cursor SET caught_up_at = ? WHERE tenant_id = ?",
                OffsetDateTime.ofInstant(at, ZoneOffset.UTC), T2.value()));
    }

    static void tenantSetting(String sql) {
        DB.seed(T2.value(), c -> SeedData.exec(c, sql + " WHERE tenant_id = ?", T2.value()));
    }

    HttpResponse<byte[]> asA2(String path, String ifNoneMatch) {
        String token = ISSUER_2.token(SUB_A, AUD_2);
        return get(path, b -> {
            b.header("Authorization", "Bearer " + token);
            if (ifNoneMatch != null) {
                b.header("If-None-Match", ifNoneMatch);
            }
            return b;
        });
    }

    static String etag(HttpResponse<?> r) {
        return r.headers().firstValue("etag").orElseThrow();
    }

    @Test
    void aMatchingIfNoneMatchGets304WithTheSameHeadersAndNoBody() {
        caughtUpAt(Instant.now());
        for (String path : List.of("/api/v1/me", "/api/v1/me/earnings?from=202601&to=202612", "/api/v1/me/earnings/202608",
                "/api/v1/me/earnings/202608/lines?size=2", "/api/v1/me/earnings/lines/" + QuerySeed.calc(AGENT_A2, 1), "/api/v1/me/limits",
                "/api/v1/me/limits/" + QuerySeed.ledger(AGENT_A2, 1), "/api/v1/me/deferrals?from=202601&to=202612")) {
            HttpResponse<byte[]> first = asA2(path, null);
            assertThat(first.statusCode()).as(path).isEqualTo(200);
            assertThat(first.headers().allValues("cache-control")).as(path).containsExactly("private, no-cache");
            assertThat(String.join(",", first.headers().allValues("cache-control")) + first.headers().allValues("pragma")).doesNotContain("no-store");
            String tag = etag(first);
            assertThat(tag).as(path).startsWith("W/\"40.O.");
            HttpResponse<byte[]> again = asA2(path, tag);
            assertThat(again.statusCode()).as(path).isEqualTo(304);
            assertThat(again.body()).isEmpty();
            assertThat(etag(again)).isEqualTo(tag);
            assertThat(again.headers().allValues("cache-control")).containsExactly("private, no-cache");
            assertThat(asA2(path, tag.substring(2)).statusCode()).as("weak comparison ignores W/").isEqualTo(304);
            assertThat(asA2(path, "W/\"0.O.00000000\", " + tag).statusCode()).as("a list containing the tag").isEqualTo(304);
            assertThat(asA2(path, "*").statusCode()).isEqualTo(304);
            assertThat(asA2(path, "W/\"0.O.00000000\"").statusCode()).isEqualTo(200);
        }
    }

    @Test
    void anotherAgentsResourceIs404EvenWithAMatchingTag() {
        caughtUpAt(Instant.now());
        String own = etag(asA2("/api/v1/me/limits/" + QuerySeed.ledger(AGENT_A2, 1), null));
        HttpResponse<byte[]> others = asA2("/api/v1/me/limits/" + QuerySeed.ledger(AGENT_A, 1), own);
        assertThat(others.statusCode()).isEqualTo(404);
        assertThat(others.body()).isEqualTo(UniformRejectionIT.NOT_FOUND);
        assertThat(asA2("/api/v1/me/limits/NO-SUCH", "*").statusCode()).as("* does not reveal existence").isEqualTo(404);
    }

    @Test
    void aDelaySwitchAtTheSameSeqChangesTheTagAndTheStatus() {
        caughtUpAt(Instant.now());
        HttpResponse<byte[]> fresh = asA2("/api/v1/me", null);
        assertThat(new String(fresh.body())).contains("\"dataStatus\":\"OK\"");
        caughtUpAt(Instant.now().minusSeconds(3600));
        HttpResponse<byte[]> late = asA2("/api/v1/me", etag(fresh));
        assertThat(late.statusCode()).as("the cached OK must not be revalidated after the switch").isEqualTo(200);
        assertThat(new String(late.body())).contains("\"dataStatus\":\"SYNC_DELAYED\"");
        assertThat(etag(late)).isNotEqualTo(etag(fresh)).startsWith("W/\"40.D.");
        tenantSetting("UPDATE tenant SET params = params || '{\"sync_delay_seconds\": 7200}'::jsonb");
        try {
            HttpResponse<byte[]> tolerant = asA2("/api/v1/me", null);
            assertThat(new String(tolerant.body())).as("the tenant's own delay").contains("\"dataStatus\":\"OK\"");
            assertThat(etag(tolerant)).isNotEqualTo(etag(fresh)).as("the delay seconds are part of cfg");
        } finally {
            tenantSetting("UPDATE tenant SET params = params - 'sync_delay_seconds'");
        }
    }

    /** C8: 커서가 RUNNING이 아니면 방금 따라잡았어도 SYNC_DELAYED(재구축 중의 옛 행도 "갱신 중"이다), 월의 대사 상태는 따로. */
    @Test
    void everyNonRunningCursorStateIsSyncDelayedAndTheMonthStatusIsSeparate() {
        caughtUpAt(Instant.now());
        try {
            for (String state : List.of("FAILED", "PAUSED", "REBUILDING")) {
                tenantCursorState(state);
                String body = new String(asA2("/api/v1/me/earnings/202609", null).body());
                assertThat(body).as(state).contains("\"dataStatus\":\"SYNC_DELAYED\"").contains("\"monthDataStatus\":\"DRIFT\"");
            }
            tenantCursorState("RUNNING");
            assertThat(new String(asA2("/api/v1/me/earnings/202609", null).body())).contains("\"dataStatus\":\"OK\"")
                    .contains("\"monthDataStatus\":\"DRIFT\"");
        } finally {
            tenantCursorState("RUNNING");
        }
    }

    /**
     * 포털 Phase 7(E2E ⓑ가 찾음 — 보고서 F): 대사는 {@code seq}를 움직이지 않고 달의 {@code data_status}를 바꾼다, 재구축도 같은 {@code seq}로 뷰를 다시 쓴다. ETag가
     * 그 표식(마지막 대사 회차·커서 갱신 시각)을 보지 않으면 옛 태그로 다시 물은 브라우저가 304를 받아 DRIFT 달을 "정상"으로 그대로 보인다.
     */
    @Test
    void aReconRunOrARebuildAtTheSameSeqChangesTheTag() {
        caughtUpAt(Instant.now());
        String path = "/api/v1/me/earnings/202608";
        HttpResponse<byte[]> before = asA2(path, null);
        assertThat(new String(before.body())).contains("\"monthDataStatus\":\"OK\"");
        DB.seed(T2.value(), c -> {
            SeedData.exec(c, "UPDATE earnings_month SET data_status = 'DRIFT' WHERE tenant_id = ? AND agent_id = ? AND close_ym = '202608'", T2.value(), AGENT_A2);
            SeedData.exec(c, """
                    INSERT INTO recon_run (tenant_id, run_at, actor, trigger, checked, matched, drift_new, drift_unresolved, inconclusive, engine_calls, result)
                    VALUES (?, now(), 'cli:caching-it', 'CLI', 1, 0, 1, 1, 0, 1, 'DRIFT')
                    """, T2.value());
        });
        try {
            HttpResponse<byte[]> drift = asA2(path, etag(before));
            assertThat(drift.statusCode()).as("the old tag must not revalidate the old body").isEqualTo(200);
            assertThat(new String(drift.body())).contains("\"monthDataStatus\":\"DRIFT\"");
            assertThat(etag(drift)).isNotEqualTo(etag(before)).startsWith("W/\"40.O.");
            assertThat(asA2(path, etag(drift)).statusCode()).as("nothing changed since").isEqualTo(304);

            DB.seed(T2.value(), c -> {
                SeedData.exec(c, "UPDATE earnings_month SET data_status = 'OK' WHERE tenant_id = ? AND agent_id = ? AND close_ym = '202608'", T2.value(), AGENT_A2);
                SeedData.exec(c, "UPDATE ingest_cursor SET updated_at = updated_at + interval '1 minute' WHERE tenant_id = ?", T2.value());
            });
            HttpResponse<byte[]> rebuilt = asA2(path, etag(drift));
            assertThat(rebuilt.statusCode()).as("a rebuild at the same seq changes the tag too").isEqualTo(200);
            assertThat(new String(rebuilt.body())).contains("\"monthDataStatus\":\"OK\"");
        } finally {
            DB.seed(T2.value(), c -> {
                SeedData.exec(c, "UPDATE earnings_month SET data_status = 'OK' WHERE tenant_id = ? AND agent_id = ? AND close_ym = '202608'", T2.value(), AGENT_A2);
                SeedData.exec(c, "DELETE FROM recon_run WHERE tenant_id = ? AND actor = 'cli:caching-it'", T2.value());
            });
        }
    }

    static void tenantCursorState(String state) {
        DB.seed(T2.value(), c -> SeedData.exec(c, "UPDATE ingest_cursor SET state = ? WHERE tenant_id = ?", state, T2.value()));
    }

    @Test
    void onlyTheSettingsAResponseReadsChangeItsTag() {
        caughtUpAt(Instant.now());
        String month = etag(asA2("/api/v1/me/earnings/202608", null));
        String list = etag(asA2("/api/v1/me/earnings?from=202601&to=202612", null));
        String limits = etag(asA2("/api/v1/me/limits", null));
        tenantSetting("UPDATE tenant SET payout_day_rule = 'NEXT_MONTH_D25'");
        try {
            HttpResponse<byte[]> m = asA2("/api/v1/me/earnings/202608", month);
            assertThat(m.statusCode()).isEqualTo(200);
            assertThat(new String(m.body())).contains("\"payoutExpectedOn\":\"2026-09-25\"");
            assertThat(asA2("/api/v1/me/earnings?from=202601&to=202612", list).statusCode()).as("the list does not read the payout rule").isEqualTo(304);
            assertThat(asA2("/api/v1/me/limits", limits).statusCode()).isEqualTo(304);
        } finally {
            tenantSetting("UPDATE tenant SET payout_day_rule = 'NEXT_MONTH_D10'");
        }
        tenantSetting("UPDATE tenant SET limit_warn_pct = 70");
        try {
            assertThat(asA2("/api/v1/me/limits", limits).statusCode()).as("thresholds are read by limits").isEqualTo(200);
            assertThat(asA2("/api/v1/me/earnings/202608", month).statusCode()).isEqualTo(304);
        } finally {
            tenantSetting("UPDATE tenant SET limit_warn_pct = 80");
        }
    }
}
