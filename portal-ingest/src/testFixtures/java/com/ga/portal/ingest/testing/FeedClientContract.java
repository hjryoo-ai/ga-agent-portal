package com.ga.portal.ingest.testing;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.IngestErrorCode;
import com.ga.portal.ingest.feed.FeedClient;
import com.ga.portal.ingest.feed.FeedException;
import com.ga.portal.ingest.feed.FeedPage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 피드 클라이언트 계약 스위트(Phase 1 지시문 D-7, 계획 ⑦ F1~F8) — {@code HttpFeedClient}와 {@code FileFeedAdapter}가 같은 시험을 상속한다.
 * 클라이언트는 이벤트 본문을 판정하지 않는다: 순서·갭·중복은 그대로 돌려주고, 응답 모양과 응답 테넌트만 본다.
 */
public abstract class FeedClientContract {

    protected static final TenantId T = TenantId.of("GA01");

    /** {@code respondedTenant}로 응답하고 {@code events}를 (그 순서로) 싣는 공급자 위의 클라이언트. */
    protected abstract FeedClient client(String respondedTenant, List<? extends JsonNode> events);

    /** 응답이 계약 {@code FeedResponse} 모양이 아닌(tenantId 없음) 공급자 위의 클라이언트. */
    protected abstract FeedClient malformedClient();

    protected static List<ObjectNode> seqs(long... seqs) {
        ObjectNode base = Samples.seq(1);
        return LongStream.of(seqs).mapToObj(s -> Samples.withSeq(base, s)).toList();
    }

    private static List<Long> seqsOf(FeedPage page) {
        return page.events().stream().map(e -> e.path("seq").asLong()).toList();
    }

    @Test
    void f1ReturnsEventsInOrderWithGapsUntouched() {
        FeedPage page = client("GA01", seqs(1, 2, 4, 5)).fetch(T, 0);
        assertThat(seqsOf(page)).containsExactly(1L, 2L, 4L, 5L);
        assertThat(page.tenantId()).isEqualTo(T);
        assertThat(page.schemaVersion()).isEqualTo(1);
        assertThat(page.events().getFirst().toString()).as("event passed through unchanged").isEqualTo(Samples.withSeq(Samples.seq(1), 1).toString());
    }

    @Test
    void f2NextSeqIsTheLastReturnedSeqOrAfterSeqWhenEmpty() {
        FeedClient c = client("GA01", seqs(1, 2, 3));
        assertThat(c.fetch(T, 1).nextSeq()).isEqualTo(3);
        FeedPage empty = c.fetch(T, 3);
        assertThat(empty.events()).isEmpty();
        assertThat(empty.nextSeq()).isEqualTo(3);
    }

    @Test
    void f3HeadSeqIsThePublishersMaximum() {
        assertThat(client("GA01", seqs(1, 2, 3, 9)).fetch(T, 0, 2).headSeq()).isEqualTo(9);
    }

    @Test
    void f4LimitBoundsDefaultAndRejection() {
        FeedClient c = client("GA01", seqs(LongStream.rangeClosed(1, 250).toArray()));
        assertThat(c.fetch(T, 0).events()).hasSize(FeedClient.DEFAULT_LIMIT);
        assertThat(seqsOf(c.fetch(T, 0, 1))).containsExactly(1L);
        assertThat(c.fetch(T, 0, 1000).events()).hasSize(250);
        assertThat(c.fetch(T, 0, 2).nextSeq()).isEqualTo(2);
        assertThatThrownBy(() -> c.fetch(T, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> c.fetch(T, 0, 1001)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> c.fetch(T, -1, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void f5AnotherTenantsFeedIsRefused() {
        assertThatThrownBy(() -> client("GA02", seqs(1, 2)).fetch(T, 0))
                .isInstanceOfSatisfying(FeedException.TenantMismatch.class,
                        e -> assertThat(e.stopsWith()).contains(IngestErrorCode.TENANT_MISMATCH))
                .hasMessageContaining("GA02").hasMessageContaining("GA01");
    }

    @Test
    void f6AfterSeqBeyondHeadIsAfterBeyondHead() {
        assertThatThrownBy(() -> client("GA01", seqs(1, 2)).fetch(T, 3))
                .isInstanceOfSatisfying(FeedException.AfterBeyondHead.class,
                        e -> assertThat(e.stopsWith()).contains(IngestErrorCode.AFTER_BEYOND_HEAD));
    }

    @Test
    void f7AnEmptyPublisherAnswersAnEmptyPage() {
        FeedPage page = client("GA01", List.of()).fetch(T, 0);
        assertThat(page.events()).isEmpty();
        assertThat(page.nextSeq()).isZero();
        assertThat(page.headSeq()).isZero();
    }

    @Test
    void f8AResponseThatIsNotAFeedResponseIsUnavailable() {
        assertThatThrownBy(() -> malformedClient().fetch(T, 0))
                .isInstanceOfSatisfying(FeedException.Unavailable.class, e -> assertThat(e.stopsWith()).isEmpty());
    }
}
