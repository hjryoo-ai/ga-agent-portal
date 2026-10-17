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

    @BeforeAll
    static void cursor() {
        DB.seed(T2.value(), c -> SeedData.exec(c, """
                INSERT INTO ingest_cursor (tenant_id, last_seq, state, updated_at, caught_up_at)
                VALUES (?, 40, 'RUNNING', TIMESTAMPTZ '2026-10-17 00:00:00+00', ?)
                ON CONFLICT (tenant_id) DO UPDATE SET caught_up_at = EXCLUDED.caught_up_at
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
