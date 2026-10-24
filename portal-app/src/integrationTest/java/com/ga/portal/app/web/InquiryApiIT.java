package com.ga.portal.app.web;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C6(포털 Phase 6 ⑥ — 문의): 접수 201({@code OPEN}, 행 하나, {@code createdAt} = 행), 목록·상세는 본인 것만, 남의·없는·형식이 틀린 id는 같은 404 +
 * {@code AUTHZ_DENIED MISSING}. {@code calcId}는 그 설계사의 라인이어야 한다(남의·없는 라인 → 404, 행 0). {@code status} 필터, 목록 항목에 본문 없음
 * (계약 — Q23). {@code MANAGER}·운영자는 다섯 진입점 전부 404 {@code ROLE}. 요청 형식·스키마 오류는 400 한 가지(행 0).
 */
class InquiryApiIT extends InboxIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final byte[] NOT_FOUND = UniformRejectionIT.NOT_FOUND;
    static final byte[] INVALID = UniformRejectionIT.INVALID_REQUEST;

    static long rows(String agent) {
        return inquiryRows(T6).stream().filter(r -> r.agentId().equals(agent)).count();
    }

    @Test
    void anInquiryIsCreatedOpenAndReadBackOnlyByItsAuthor() {
        long before = rows(AGENT_6A);
        HttpResponse<byte[]> created = post(INQUIRIES, t6(SUB_6A), inquiryJson("AMOUNT", CALC_6A, "이번 달 금액이 예상과 달라요"));
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode c = JSON.readTree(created.body());
        String id = c.path("id").asString();
        assertThat(c.path("status").asString()).isEqualTo("OPEN");
        assertThat(c.path("category").asString()).isEqualTo("AMOUNT");
        assertThat(c.path("calcId").asString()).isEqualTo(CALC_6A);
        assertThat(c.has("message")).as("the created response carries no body").isFalse();
        assertThat(Instant.parse(c.path("createdAt").asString())).isBeforeOrEqualTo(Instant.now());
        assertThat(rows(AGENT_6A)).isEqualTo(before + 1);
        assertThat(inquiryRows(T6)).filteredOn(r -> r.id().equals(id)).singleElement().satisfies(r -> {
            assertThat(r.status()).isEqualTo("OPEN");
            assertThat(r.calcId()).isEqualTo(CALC_6A);
            assertThat(r.message()).isEqualTo("이번 달 금액이 예상과 달라요");
        });

        HttpResponse<byte[]> detail = get(INQUIRIES + "/" + id, t6(SUB_6A));
        assertThat(detail.statusCode()).isEqualTo(200);
        JsonNode d = JSON.readTree(detail.body());
        assertThat(d.path("message").asString()).isEqualTo("이번 달 금액이 예상과 달라요");
        assertThat(d.path("createdAt").asString()).isEqualTo(c.path("createdAt").asString());
        assertThat(d.has("answer") || d.has("answeredAt") || d.has("answeredBy") || d.has("agentId")).isFalse();

        JsonNode list = JSON.readTree(get(INQUIRIES + "?size=100", t6(SUB_6A)).body());
        assertThat(list.path("items")).anySatisfy(i -> assertThat(i.path("id").asString()).isEqualTo(id));
        list.path("items").forEach(i -> assertThat(i.has("message") || i.has("answer")).as("no body in the list").isFalse());

        // B에게는 없다 — 상세 404, 목록에 없음
        int from = audits(T6).size();
        HttpResponse<byte[]> foreign = get(INQUIRIES + "/" + id, t6(SUB_6B));
        HttpResponse<byte[]> missing = get(INQUIRIES + "/00000000-0000-4000-8000-0000000006fe", t6(SUB_6B));
        HttpResponse<byte[]> malformed = get(INQUIRIES + "/x", t6(SUB_6B));
        for (HttpResponse<byte[]> r : List.of(foreign, missing, malformed)) {
            assertThat(r.statusCode()).isEqualTo(404);
            assertThat(r.body()).isEqualTo(NOT_FOUND);
            assertThat(headers(r)).isEqualTo(headers(foreign));
        }
        assertThat(audits(T6).subList(from, audits(T6).size())).hasSize(3)
                .allSatisfy(l -> assertThat(l).isEqualTo(new AuditLine(SUB_6B, "AUTHZ_DENIED", "GET /api/v1/me/inquiries/{id}", "MISSING")));
        JsonNode bList = JSON.readTree(get(INQUIRIES + "?size=100", t6(SUB_6B)).body());
        bList.path("items").forEach(i -> assertThat(i.path("id").asString()).isNotEqualTo(id));
    }

    @Test
    void theDetailAndListAnswerConditionalRequests() {
        HttpResponse<byte[]> created = post(INQUIRIES, t6(SUB_6A), inquiryJson("OTHER", null, "기타 문의"));
        String id = JSON.readTree(created.body()).path("id").asString();
        HttpResponse<byte[]> detail = get(INQUIRIES + "/" + id, t6(SUB_6A));
        String etag = detail.headers().firstValue("etag").orElseThrow();
        assertThat(get(INQUIRIES + "/" + id, b -> b.header("Authorization", "Bearer " + t6(SUB_6A)).header("If-None-Match", etag)).statusCode())
                .isEqualTo(304);
        // 남의 문의에는 ETag가 맞아도 304가 아니라 404(소유 확인 뒤에만 비교)
        assertThat(get(INQUIRIES + "/" + id, b -> b.header("Authorization", "Bearer " + t6(SUB_6B)).header("If-None-Match", etag)).statusCode())
                .isEqualTo(404);
        HttpResponse<byte[]> list = get(INQUIRIES, t6(SUB_6A));
        String listEtag = list.headers().firstValue("etag").orElseThrow();
        assertThat(get(INQUIRIES, b -> b.header("Authorization", "Bearer " + t6(SUB_6A)).header("If-None-Match", listEtag)).statusCode()).isEqualTo(304);
    }

    @Test
    void theCalcIdMustBeTheAuthorsOwnLine() {
        long before = rows(AGENT_6A);
        int from = audits(T6).size();
        HttpResponse<byte[]> others = post(INQUIRIES, t6(SUB_6A), inquiryJson("LIMIT", CALC_6B, "남의 라인"));
        HttpResponse<byte[]> none = post(INQUIRIES, t6(SUB_6A), inquiryJson("LIMIT", "NO-SUCH-CALC", "없는 라인"));
        for (HttpResponse<byte[]> r : List.of(others, none)) {
            assertThat(r.statusCode()).isEqualTo(404);
            assertThat(r.body()).isEqualTo(NOT_FOUND);
        }
        assertThat(rows(AGENT_6A)).isEqualTo(before);
        assertThat(audits(T6).subList(from, audits(T6).size())).hasSize(2)
                .allSatisfy(l -> assertThat(l).isEqualTo(new AuditLine(SUB_6A, "AUTHZ_DENIED", "POST /api/v1/me/inquiries", "MISSING")));
    }

    @Test
    void theStatusFilterIsClosed() {
        post(INQUIRIES, t6(SUB_6A), inquiryJson("CLAWBACK", CALC_6A, "환수 문의"));
        DB.seed(T6.value(), c -> com.ga.portal.infra.testing.SeedData.exec(c, """
                INSERT INTO inquiry (tenant_id, inquiry_id, agent_id, calc_id, category, message, status, created_at, answered_at, answer, answered_by)
                VALUES (?, '00000000-0000-4000-8000-000000000611', ?, ?, 'DEFERRAL', '분급 문의', 'ANSWERED', TIMESTAMPTZ '2026-10-01 00:00:00+00',
                        TIMESTAMPTZ '2026-10-02 00:00:00+00', '확인했습니다', 'cli:ph6-ops')
                ON CONFLICT DO NOTHING
                """, T6.value(), AGENT_6A, CALC_6A));
        for (String status : List.of("OPEN", "ANSWERED", "CLOSED")) {
            JsonNode body = JSON.readTree(get(INQUIRIES + "?size=100&status=" + status, t6(SUB_6A)).body());
            body.path("items").forEach(i -> assertThat(i.path("status").asString()).as(status).isEqualTo(status));
            assertThat(body.path("total").asLong()).isEqualTo(body.path("items").size());
        }
        JsonNode answered = JSON.readTree(get(INQUIRIES + "?status=ANSWERED", t6(SUB_6A)).body());
        assertThat(answered.path("items")).anySatisfy(i -> {
            assertThat(i.path("id").asString()).isEqualTo("00000000-0000-4000-8000-000000000611");
            assertThat(i.path("answeredAt").asString()).isEqualTo("2026-10-02T00:00:00Z");
        });
        JsonNode detail = JSON.readTree(get(INQUIRIES + "/00000000-0000-4000-8000-000000000611", t6(SUB_6A)).body());
        assertThat(detail.path("answer").asString()).isEqualTo("확인했습니다");
        assertThat(new String(get(INQUIRIES + "/00000000-0000-4000-8000-000000000611", t6(SUB_6A)).body(), StandardCharsets.UTF_8))
                .as("who answered stays inside").doesNotContain("cli:ph6-ops").doesNotContain("answeredBy");
        long all = JSON.readTree(get(INQUIRIES + "?status=ALL&size=100", t6(SUB_6A)).body()).path("total").asLong();
        long dflt = JSON.readTree(get(INQUIRIES + "?size=100", t6(SUB_6A)).body()).path("total").asLong();
        assertThat(all).isEqualTo(dflt).isGreaterThanOrEqualTo(2);
        // 빈 값(?status=)은 Spring의 defaultValue(ALL)가 받는다 — /limits?status=와 같다. 값이 있으면 닫힌 넷 밖은 400
        for (String bad : List.of("?status=open", "?status=DONE", "?status=%20", "?page=-1", "?size=101")) {
            HttpResponse<byte[]> r = get(INQUIRIES + bad, t6(SUB_6A));
            assertThat(r.statusCode()).as(bad).isEqualTo(400);
            assertThat(r.body()).as(bad).isEqualTo(INVALID);
        }
    }

    @Test
    void malformedRequestsAreTheOne400AndWriteNothing() {
        long before = rows(AGENT_6A);
        List<String> bodies = List.of(
                "", "{", "[]", "null",
                inquiryJson("AMOUNT", null, "라인 없이 금액 문의"),
                inquiryJson("NOPE", null, "모르는 분류"),
                inquiryJson("OTHER", null, ""),
                inquiryJson("OTHER", null, "   "),
                "{\"category\":\"OTHER\",\"message\":\"m\",\"extra\":1}",
                "{\"category\":\"OTHER\",\"message\":\"m\",\"message\":\"n\"}",
                "{\"category\":\"OTHER\",\"message\":7}",
                "{\"category\":\"OTHER\",\"calcId\":null,\"message\":\"m\"}");
        for (String body : bodies) {
            HttpResponse<byte[]> r = post(INQUIRIES, t6(SUB_6A), body);
            assertThat(r.statusCode()).as("body #%d", bodies.indexOf(body)).isEqualTo(400);
            assertThat(r.body()).isEqualTo(INVALID);
        }
        HttpResponse<byte[]> noType = postBytes(INQUIRIES, t6(SUB_6A), null, inquiryJson("OTHER", null, "m").getBytes(StandardCharsets.UTF_8));
        HttpResponse<byte[]> textType = postBytes(INQUIRIES, t6(SUB_6A), "text/plain", inquiryJson("OTHER", null, "m").getBytes(StandardCharsets.UTF_8));
        assertThat(List.of(noType, textType)).allSatisfy(r -> {
            assertThat(r.statusCode()).isEqualTo(400);
            assertThat(r.body()).isEqualTo(INVALID);
        });
        assertThat(rows(AGENT_6A)).isEqualTo(before);
    }

    @Test
    void managerAndOperatorAreDeniedAtEveryInquiryEndpoint() {
        int from = audits(T6).size();
        for (String subject : List.of(SUB_6M, SUB_6O)) {
            List<HttpResponse<byte[]>> rs = List.of(get(INQUIRIES, t6(subject)), get(INQUIRIES + "/00000000-0000-4000-8000-000000000611", t6(subject)),
                    post(INQUIRIES, t6(subject), inquiryJson("OTHER", null, "조직장 문의")));
            assertThat(rs).allSatisfy(r -> {
                assertThat(r.statusCode()).isEqualTo(404);
                assertThat(r.body()).isEqualTo(NOT_FOUND);
            });
        }
        assertThat(audits(T6).subList(from, audits(T6).size())).extracting(AuditLine::detail).containsOnly("ROLE").hasSize(6);
        assertThat(inquiryRows(T6)).noneMatch(r -> r.agentId().equals(AGENT_6M));
    }
}
