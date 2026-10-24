package com.ga.portal.app.web;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C7(포털 Phase 6 ⑥ — API 쪽, CLI는 {@code InquiryCliIT}): 문의 본문은 <b>작성자의 상세 응답에만</b> 나온다. 센티널 본문(센티널 파일 {@code inbox})을 넣고 —
 * 접수 응답·목록·알림·감사 {@code detail}·지표 태그·로그 캡처에 0, 상세에만 원문. 본문 상한: 4,096바이트는 받고 4,097바이트는 400, 1,000 코드 포인트(이모지 —
 * UTF-16 길이 2,000)는 받고 1,001은 400. 깨진 JSON·중복 키·형식 오류·<b>따옴표 없는 토큰</b>에 센티널을 실어도 400 고정 바이트이고 로그에 0 — 마지막 것은 파서 문구가
 * 입력 조각(토큰 전체)을 싣는 유일한 모양이라, 거부 경로가 예외 문구를 로그에 남기면(주입 S8) 여기서 걸린다.
 */
@ExtendWith(OutputCaptureExtension.class)
class InquiryIsolationIT extends InboxIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String EMOJI = "\uD83D\uDE00";   // U+1F600 — UTF-8 4바이트, UTF-16 2

    static String text(HttpResponse<byte[]> r) {
        return new String(r.body(), StandardCharsets.UTF_8);
    }

    /** 정확히 {@code bytes}바이트가 되도록 JSON 뒤에 공백을 붙인다(공백은 JSON 값 밖 — 본문은 그대로). */
    static byte[] padded(String json, int bytes) {
        byte[] raw = json.getBytes(StandardCharsets.UTF_8);
        assertThat(raw.length).isLessThanOrEqualTo(bytes);
        byte[] out = new byte[bytes];
        System.arraycopy(raw, 0, out, 0, raw.length);
        java.util.Arrays.fill(out, raw.length, bytes, (byte) ' ');
        return out;
    }

    @Test
    void theBodyAppearsOnlyInTheAuthorsDetail(CapturedOutput output) {
        List<String> sentinels = bodySentinels();
        assertThat(sentinels).hasSizeGreaterThanOrEqualTo(3);
        String token = t6(SUB_6I);
        String body = "금액 확인 부탁드립니다 " + sentinels.get(0);
        int auditsBefore = audits(T6).size();

        HttpResponse<byte[]> created = post(INQUIRIES, token, inquiryJson("AMOUNT", CALC_6I, body));
        assertThat(created.statusCode()).isEqualTo(201);
        String id = JSON.readTree(created.body()).path("id").asString();
        List<String> outside = new ArrayList<>();
        outside.add(text(created));
        outside.add(text(get(INQUIRIES, token)));
        outside.add(text(get(INQUIRIES + "?status=OPEN", token)));
        outside.add(text(get(NOTIFICATIONS, token)));
        // 남의 상세는 404 — 본문 없음
        HttpResponse<byte[]> foreign = get(INQUIRIES + "/" + id, t6(SUB_6A));
        assertThat(foreign.statusCode()).isEqualTo(404);
        outside.add(text(foreign));
        for (String s : outside) {
            assertThat(s).as("a response outside the detail").doesNotContain(sentinels.get(0));
        }

        HttpResponse<byte[]> detail = get(INQUIRIES + "/" + id, token);
        JsonNode d = JSON.readTree(detail.body());
        assertThat(d.path("message").asString()).as("the detail is the one place").isEqualTo(body);

        assertThat(audits(T6).subList(auditsBefore, audits(T6).size())).extracting(AuditLine::detail, AuditLine::target)
                .allSatisfy(t -> assertThat(t.toList().toString()).doesNotContain(sentinels.get(0)));
        for (Meter m : meters.getMeters()) {
            for (Tag tag : m.getId().getTags()) {
                assertThat(tag.getValue()).as("metric %s", m.getId().getName()).doesNotContain(sentinels.get(0));
            }
        }
        assertThat(output.getAll()).doesNotContain(sentinels.get(0));
    }

    @Test
    void theSizeAndLengthLimitsCountBytesAndCodePoints() {
        String token = t6(SUB_6I);
        long before = inquiryRows(T6).stream().filter(r -> r.agentId().equals(AGENT_6I)).count();
        String thousand = EMOJI.repeat(1000);
        assertThat(thousand.length()).as("UTF-16 length is twice the code points").isEqualTo(2000);
        String json = inquiryJson("OTHER", null, thousand);
        HttpResponse<byte[]> atLimit = postBytes(INQUIRIES, token, "application/json", padded(json, 4096));
        assertThat(atLimit.statusCode()).as("4,096 bytes, 1,000 code points").isEqualTo(201);
        HttpResponse<byte[]> overBytes = postBytes(INQUIRIES, token, "application/json", padded(json, 4097));
        assertThat(overBytes.statusCode()).as("4,097 bytes").isEqualTo(400);
        assertThat(overBytes.body()).isEqualTo(UniformRejectionIT.INVALID_REQUEST);
        HttpResponse<byte[]> overLength = post(INQUIRIES, token, inquiryJson("OTHER", null, EMOJI.repeat(1001)));
        assertThat(overLength.statusCode()).as("1,001 code points").isEqualTo(400);
        assertThat(overLength.body()).isEqualTo(UniformRejectionIT.INVALID_REQUEST);
        assertThat(inquiryRows(T6).stream().filter(r -> r.agentId().equals(AGENT_6I)).count()).isEqualTo(before + 1);
        String stored = inquiryRows(T6).stream().filter(r -> r.agentId().equals(AGENT_6I) && r.category().equals("OTHER")).findFirst().orElseThrow().message();
        assertThat(stored.codePointCount(0, stored.length())).isEqualTo(1000);
    }

    @Test
    void rejectedBodiesCarryingASentinelLeaveNoTrace(CapturedOutput output) {
        List<String> sentinels = bodySentinels();
        String token = t6(SUB_6I);
        String s1 = sentinels.get(1);
        String s2 = sentinels.get(2);
        String bare = bareBodySentinel();
        long before = inquiryRows(T6).size();
        List<byte[]> bodies = List.of(
                ("{\"category\":\"OTHER\",\"message\":\"" + s1).getBytes(StandardCharsets.UTF_8),
                ("{\"category\":\"OTHER\",\"message\":\"" + s1 + "\",\"message\":\"" + s2 + "\"}").getBytes(StandardCharsets.UTF_8),
                ("{\"category\":\"" + s2 + "\",\"message\":\"" + s1 + "\"}").getBytes(StandardCharsets.UTF_8),
                ("{\"category\":\"OTHER\",\"message\":\"" + s1 + "\",\"" + s2 + "\":1}").getBytes(StandardCharsets.UTF_8),
                ("{\"category\":\"OTHER\",\"message\":\"" + s1 + "\\u0000\"}").getBytes(StandardCharsets.UTF_8),
                ("{\"category\":\"OTHER\",\"message\":\"" + s1 + "\\ud800\"}").getBytes(StandardCharsets.UTF_8),
                ("{\"category\":\"OTHER\",\"message\":" + bare + "}").getBytes(StandardCharsets.UTF_8));
        for (byte[] b : bodies) {
            HttpResponse<byte[]> r = postBytes(INQUIRIES, token, "application/json", b);
            assertThat(r.statusCode()).isEqualTo(400);
            assertThat(r.body()).isEqualTo(UniformRejectionIT.INVALID_REQUEST);
        }
        assertThat(inquiryRows(T6)).hasSize((int) before);
        assertThat(output.getAll()).doesNotContain(s1).doesNotContain(s2).doesNotContain(bare);
    }
}
