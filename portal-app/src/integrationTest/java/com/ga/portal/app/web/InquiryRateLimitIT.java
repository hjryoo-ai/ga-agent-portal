package com.ga.portal.app.web;

import com.ga.platform.core.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C8(포털 Phase 6 ⑥): 설계사당 직전 24시간 문의 행 수 ≥ 테넌트 한도(여기 {@link #RATE} — {@code params.inquiry_rate_per_day})면 429 {@code RATE_LIMITED}
 * ("too many inquiries") + {@code Retry-After}(정수 초, 1~86,400) — 행·감사 0, 지표 {@code api_rate_limited_total{tenant, route="inquiries"}} 1. 동시에 N+5개를
 * 보내도 통과는 정확히 N(설계사 권고 잠금이 창 판정과 행 넣기를 한 덩어리로 묶는다 — 동시 시험은 빈 창으로 시작하는 자기 테넌트에서).
 */
class InquiryRateLimitIT extends InboxIntegrationTest {

    static final String BODY = inquiryJson("OTHER", null, "한도 시험 문의");

    double metric(TenantId t) {
        var c = meters.find("api_rate_limited_total").tag("tenant", t.value()).tag("route", "inquiries").counter();
        return c == null ? 0 : c.count();
    }

    @Test
    void theRequestAfterTheDailyLimitIsRejectedWithoutARow() {
        String token = ISSUER_6R.token(SUB_6R, AUD_6R);
        double before = metric(T6R);
        int audits = audits(T6R).size();
        for (int i = 0; i < RATE; i++) {
            assertThat(post(INQUIRIES, token, BODY).statusCode()).as("request %d", i + 1).isEqualTo(201);
        }
        HttpResponse<byte[]> limited = post(INQUIRIES, token, BODY);
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.body()).isEqualTo(UniformRejectionIT.INQUIRY_RATE_LIMITED);
        assertThat(limited.headers().allValues("retry-after")).singleElement().satisfies(v -> {
            assertThat(v).matches("[1-9][0-9]{0,4}");
            assertThat(Integer.parseInt(v)).isBetween(86_000, 86_400);
        });
        assertThat(headers(limited)).containsEntry("content-type", List.of("application/problem+json"));
        assertThat(inquiryRows(T6R)).hasSize(RATE).allSatisfy(r -> assertThat(r.status()).isEqualTo("OPEN"));
        assertThat(audits(T6R)).as("no audit row").hasSize(audits);
        assertThat(metric(T6R) - before).isEqualTo(1.0);
        // 한도는 접수만 막는다 — 목록·상세는 그대로
        assertThat(get(INQUIRIES, token).statusCode()).isEqualTo(200);
        assertThat(get(INQUIRIES + "/" + inquiryRows(T6R).getFirst().id(), token).statusCode()).isEqualTo(200);
    }

    @Test
    void concurrentRequestsAdmitExactlyTheLimit() throws Exception {
        String token = ISSUER_6B.token(SUB_6U, AUD_6B);
        int threads = RATE + 5;
        double before = metric(T6B);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                results.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return post(INQUIRIES, token, BODY).statusCode();
                }, pool));
            }
            start.countDown();
            List<Integer> statuses = results.stream().map(CompletableFuture::join).toList();
            long admitted = statuses.stream().filter(s -> s == 201).count();
            long limited = statuses.stream().filter(s -> s == 429).count();
            System.out.println("INQUIRY_RATE_LIMIT_CONCURRENCY threads=" + threads + " limit=" + RATE + " admitted=" + admitted + " limited=" + limited
                    + " statuses=" + statuses);
            assertThat(admitted).as("admitted (a row each)").isEqualTo(RATE);
            assertThat(limited).isEqualTo(threads - RATE);
            assertThat(inquiryRows(T6B)).hasSize(RATE);
            assertThat(metric(T6B) - before).isEqualTo((double) (threads - RATE));
        } finally {
            pool.shutdownNow();
        }
    }
}
