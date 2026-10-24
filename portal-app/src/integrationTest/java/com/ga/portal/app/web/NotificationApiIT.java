package com.ga.portal.app.web;

import com.ga.portal.domain.NotificationType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C6(포털 Phase 6 ⑥ — 알림): 본인 것만, 최신순, 변이마다 {@code params}·{@code link}가 계약 모양(응답마다 {@link ApiContract}가 검증), {@code dedupKey}·
 * {@code agentId} 없음. 읽음은 멱등({@code readAt}은 처음 값). 남의·없는·형식이 틀린 id는 같은 404 + {@code AUTHZ_DENIED MISSING}, 남의 행은 그대로.
 * {@code MANAGER}·운영자 링크는 404 {@code ROLE}. 목록은 본문 ETag로 304, 읽으면 ETag가 바뀐다.
 */
class NotificationApiIT extends InboxIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final byte[] NOT_FOUND = UniformRejectionIT.NOT_FOUND;

    @Test
    void anAgentSeesOnlyItsOwnNotificationsNewestFirstInTheContractShape() {
        HttpResponse<byte[]> r = get(NOTIFICATIONS, t6(SUB_6A));
        assertThat(r.statusCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(r.body());
        List<String> expected = new ArrayList<>();
        for (int i = A_NOTIFICATIONS.size() - 1; i >= 0; i--) {
            expected.add(A_NOTIFICATIONS.get(i).id(T6, AGENT_6A));
        }
        List<String> ids = new ArrayList<>();
        body.path("items").forEach(n -> ids.add(n.path("id").asString()));
        assertThat(ids).isEqualTo(expected);
        assertThat(body.path("total").asLong()).isEqualTo(A_NOTIFICATIONS.size());
        long unread = 0;
        for (JsonNode n : body.path("items")) {
            Seeded seeded = A_NOTIFICATIONS.stream().filter(s -> s.id(T6, AGENT_6A).equals(n.path("id").asString())).findFirst().orElseThrow();
            assertThat(n.path("type").asString()).isEqualTo(seeded.type().name());
            assertThat(n.path("severity").asString()).isEqualTo(seeded.type().severity().name());
            assertThat(JSON.readTree(seeded.params())).as("params as stored").isEqualTo(n.path("params"));
            if (seeded.link() == null) {
                assertThat(n.has("link")).as("%s has no link", seeded.type()).isFalse();
            } else {
                assertThat(n.path("link").asString()).isEqualTo(seeded.link());
            }
            assertThat(n.has("dedupKey") || n.has("agentId") || n.has("title") || n.has("body")).as("no server-side key or sentence").isFalse();
            if (!n.has("readAt")) {
                unread++;
            }
        }
        assertThat(body.path("unreadCount").asLong()).isEqualTo(unread);
        String text = new String(r.body(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(text).doesNotContain(AGENT_6A, POLICY_6A, B_NOTIFICATION.id(T6, AGENT_6B), CALC_6B);
        assertThat(text).doesNotContain(A_NOTIFICATIONS.getFirst().dedupKey());

        // B는 자기 것 하나만
        JsonNode b = JSON.readTree(get(NOTIFICATIONS, t6(SUB_6B)).body());
        assertThat(b.path("items")).singleElement().satisfies(n -> assertThat(n.path("id").asString()).isEqualTo(B_NOTIFICATION.id(T6, AGENT_6B)));
        assertThat(b.path("total").asLong()).isEqualTo(1);
    }

    @Test
    void pagingAndTheUnreadFilterKeepTheTotalsHonest() {
        JsonNode p1 = JSON.readTree(get(NOTIFICATIONS + "?size=2&page=1", t6(SUB_6A)).body());
        assertThat(p1.path("page").asInt()).isEqualTo(1);
        assertThat(p1.path("size").asInt()).isEqualTo(2);
        assertThat(p1.path("items")).hasSize(2);
        assertThat(p1.path("items").path(0).path("id").asString()).isEqualTo(A_NOTIFICATIONS.get(3).id(T6, AGENT_6A));
        JsonNode beyond = JSON.readTree(get(NOTIFICATIONS + "?size=100&page=5", t6(SUB_6A)).body());
        assertThat(beyond.path("items")).isEmpty();
        assertThat(beyond.path("total").asLong()).isEqualTo(A_NOTIFICATIONS.size());
        JsonNode unread = JSON.readTree(get(NOTIFICATIONS + "?unreadOnly=true&size=100", t6(SUB_6A)).body());
        assertThat(unread.path("total").asLong()).isEqualTo(unread.path("unreadCount").asLong()).isEqualTo(unread.path("items").size());
        unread.path("items").forEach(n -> assertThat(n.has("readAt")).isFalse());
        for (String bad : List.of("?size=0", "?size=101", "?page=-1", "?unreadOnly=maybe", "?size=x")) {
            HttpResponse<byte[]> r = get(NOTIFICATIONS + bad, t6(SUB_6A));
            assertThat(r.statusCode()).as(bad).isEqualTo(400);
            assertThat(r.body()).as(bad).isEqualTo(UniformRejectionIT.INVALID_REQUEST);
        }
    }

    @Test
    void markingReadIsIdempotentAndChangesTheListEtag() {
        Seeded mine = new Seeded(NotificationType.LIMIT_CUT, "LIMIT_CUT:" + CALC_6I, "/lines/" + CALC_6I,
                "{\"calcId\":\"" + CALC_6I + "\",\"policyMasked\":\"*************1-01\",\"closeYm\":\"202610\"}", "2026-10-21 00:00:00+00");
        DB.seed(T6.value(), c -> notification(c, AGENT_6I, mine));
        String id = mine.id(T6, AGENT_6I);
        String token = t6(SUB_6I);
        HttpResponse<byte[]> before = get(NOTIFICATIONS, token);
        String etag = before.headers().firstValue("etag").orElseThrow();
        HttpResponse<byte[]> notModified = get(NOTIFICATIONS, b -> b.header("Authorization", "Bearer " + token).header("If-None-Match", etag));
        assertThat(notModified.statusCode()).isEqualTo(304);
        long unreadBefore = JSON.readTree(before.body()).path("unreadCount").asLong();

        HttpResponse<byte[]> first = patch(NOTIFICATIONS + "/" + id + "/read", token);
        assertThat(first.statusCode()).isEqualTo(200);
        JsonNode n1 = JSON.readTree(first.body());
        assertThat(n1.path("id").asString()).isEqualTo(id);
        String readAt = n1.path("readAt").asString();
        assertThat(readAt).isNotEmpty();
        String stored = readAt(id);
        HttpResponse<byte[]> second = patch(NOTIFICATIONS + "/" + id + "/read", token);
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(second.body()).path("readAt").asString()).as("the first read time stays").isEqualTo(readAt);
        assertThat(readAt(id)).isEqualTo(stored);

        HttpResponse<byte[]> after = get(NOTIFICATIONS, b -> b.header("Authorization", "Bearer " + token).header("If-None-Match", etag));
        assertThat(after.statusCode()).as("reading changes the body, so the old ETag no longer matches").isEqualTo(200);
        assertThat(JSON.readTree(after.body()).path("unreadCount").asLong()).isEqualTo(unreadBefore - 1);
    }

    @Test
    void anotherAgentsMissingOrMalformedIdIsTheSame404AndLeavesTheRowUnread() {
        String others = A_NOTIFICATIONS.get(2).id(T6, AGENT_6A);
        int from = audits(T6).size();
        HttpResponse<byte[]> foreign = patch(NOTIFICATIONS + "/" + others + "/read", t6(SUB_6B));
        HttpResponse<byte[]> missing = patch(NOTIFICATIONS + "/00000000-0000-4000-8000-0000000006ff/read", t6(SUB_6B));
        HttpResponse<byte[]> malformed = patch(NOTIFICATIONS + "/not-a-uuid/read", t6(SUB_6B));
        HttpResponse<byte[]> upper = patch(NOTIFICATIONS + "/" + others.toUpperCase(java.util.Locale.ROOT) + "/read", t6(SUB_6B));
        for (HttpResponse<byte[]> r : List.of(foreign, missing, malformed, upper)) {
            assertThat(r.statusCode()).isEqualTo(404);
            assertThat(r.body()).isEqualTo(NOT_FOUND);
            assertThat(headers(r)).isEqualTo(headers(foreign));
        }
        assertThat(readAt(others)).as("B cannot read A's notification").isNull();
        assertThat(audits(T6).subList(from, audits(T6).size())).hasSize(4)
                .allSatisfy(l -> assertThat(l).isEqualTo(new AuditLine(SUB_6B, "AUTHZ_DENIED", "PATCH /api/v1/me/notifications/{id}/read", "MISSING")));
    }

    @Test
    void managerAndOperatorLinksAreDeniedOnBothEndpoints() {
        int from = audits(T6).size();
        String id = A_NOTIFICATIONS.getFirst().id(T6, AGENT_6A);
        for (String subject : List.of(SUB_6M, SUB_6O)) {
            HttpResponse<byte[]> list = get(NOTIFICATIONS, t6(subject));
            HttpResponse<byte[]> read = patch(NOTIFICATIONS + "/" + id + "/read", t6(subject));
            assertThat(List.of(list, read)).allSatisfy(r -> {
                assertThat(r.statusCode()).isEqualTo(404);
                assertThat(r.body()).isEqualTo(NOT_FOUND);
            });
        }
        assertThat(readAt(id)).isNull();
        assertThat(audits(T6).subList(from, audits(T6).size())).extracting(AuditLine::detail).containsExactly("ROLE", "ROLE", "ROLE", "ROLE");
    }
}
