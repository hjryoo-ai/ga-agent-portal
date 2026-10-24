package com.ga.portal.app.web;

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
 * C5(포털 Phase 5 ③): 설계사당 직전 60초 행 수 ≥ 테넌트 한도(여기 3)면 429 {@code RATE_LIMITED} + {@code Retry-After}(정수 초) — 행·감사·엔진 호출 없음,
 * 지표 {@code api_rate_limited_total{tenant, route="simulations"}} 1. 동시에 N+5개를 보내도 통과는 정확히 N(설계사 잠금이 창 판정과 행 넣기를 한 덩어리로 묶는다) — 동시 시험은
 * 자기 테넌트({@code BURST})에서 샘플의 설계사로 보내 통과 N이 전부 샘플 응답 200이다(수용 심사 F10 — 전에는 샘플 밖 설계사라 503).
 */
class RateLimitIT extends SimIntegrationTest {

    static final int LIMIT = 3;

    String rl(String subject) {
        return ISSUER_RL.token(subject, AUD_RL);
    }

    @Test
    void theFourthRequestInAMinuteIsRejectedWithoutARow() {
        String a = rl(SUB_A1);
        double metric = rateLimitedMetric(RL);
        int audits = audits(RL).size();
        for (int i = 0; i < LIMIT; i++) {
            assertThat(post(NEW, a, S1).statusCode()).as("request %d", i + 1).isEqualTo(200);
        }
        int calls = ENGINE_RL.calls();
        HttpResponse<byte[]> limited = post(NEW, a, S1);
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.body()).isEqualTo(SimulationRejectionIT.RATE_LIMITED);
        assertThat(limited.headers().allValues("retry-after")).singleElement().satisfies(v -> {
            assertThat(v).matches("[1-9][0-9]?");
            assertThat(Integer.parseInt(v)).isBetween(1, 60);
        });
        assertThat(rows(RL).stream().filter(r -> r.agentId().equals(AGENT_A1))).hasSize(LIMIT);
        assertThat(ENGINE_RL.calls()).as("no engine call").isEqualTo(calls);
        assertThat(audits(RL)).as("no audit row").hasSize(audits);
        assertThat(rateLimitedMetric(RL) - metric).isEqualTo(1.0);
    }

    @Test
    void concurrentRequestsAdmitExactlyTheLimit() throws Exception {
        String b = ISSUER_BURST.token(SUB_BURST, AUD_BURST);
        int threads = LIMIT + 5;
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
                    return post(NEW, b, S1).statusCode();
                }, pool));
            }
            start.countDown();
            List<Integer> statuses = results.stream().map(CompletableFuture::join).toList();
            long limited = statuses.stream().filter(s -> s == 429).count();
            System.out.println("RATE_LIMIT_CONCURRENCY threads=" + threads + " limit=" + LIMIT + " admitted=" + (threads - limited) + " limited=" + limited
                    + " statuses=" + statuses);
            assertThat(threads - limited).as("admitted (a row each)").isEqualTo(LIMIT);
            assertThat(statuses.stream().filter(s -> s == 200)).as("every admitted request is the sample's answer").hasSize(LIMIT);
            assertThat(rows(BURST)).extracting(SimRow::status).containsExactly("SUCCEEDED", "SUCCEEDED", "SUCCEEDED");
            assertThat(ENGINE_BURST.calls()).as("one engine call per admitted request").isEqualTo(LIMIT);
        } finally {
            pool.shutdownNow();
        }
    }
}
