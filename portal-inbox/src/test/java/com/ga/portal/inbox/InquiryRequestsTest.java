package com.ga.portal.inbox;

import com.ga.portal.domain.InquiryCategory;
import com.ga.portal.domain.access.InvalidRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 포털 Phase 6 ⑥·C7: 문의 본문은 형식(JSON·4 KiB)·계약 스키마(닫힌 분류·라인 분류의 {@code calcId}·1~1,000 코드 포인트·공백만 금지·모르는 키 금지)·저장할 수 있는
 * 글자만 통과한다. 거부는 한 가지이고, 예외 메시지에 입력이 없다(본문 격리).
 */
class InquiryRequestsTest {

    static final Optional<String> JSON = Optional.of("application/json");
    static final String SENTINEL = "문의센티널-가나다-0420";

    static byte[] b(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void aLineInquiryAndAnOtherInquiryPass() {
        InquiryRequests.Request line = InquiryRequests.read(JSON, b("{\"category\":\"AMOUNT\",\"calcId\":\"CALC-1\",\"message\":\"금액이 달라요\"}"));
        assertThat(line.category()).isEqualTo(InquiryCategory.AMOUNT);
        assertThat(line.calcId()).contains("CALC-1");
        assertThat(line.message()).isEqualTo("금액이 달라요");
        InquiryRequests.Request other = InquiryRequests.read(Optional.of("application/json; charset=UTF-8"), b("{\"category\":\"OTHER\",\"message\":\"x\"}"));
        assertThat(other.calcId()).isEmpty();
        assertThat(InquiryRequests.read(JSON, b("{\"category\":\"OTHER\",\"calcId\":\"C1\",\"message\":\"x\"}")).calcId()).contains("C1");
    }

    @Test
    void theLimitIsAThousandCodePointsNotUtf16Units() {
        String emoji = "😀";    // U+1F600 — UTF-16 두 단위
        assertThat(InquiryRequests.read(JSON, b("{\"category\":\"OTHER\",\"message\":\"" + emoji.repeat(1000) + "\"}")).message()).hasSize(2000);
        assertThatThrownBy(() -> InquiryRequests.read(JSON, b("{\"category\":\"OTHER\",\"message\":\"" + emoji.repeat(1001) + "\"}")))
                .isInstanceOf(InvalidRequest.class);
        assertThat(InquiryRequests.read(JSON, b("{\"category\":\"OTHER\",\"message\":\"" + "가".repeat(1000) + "\"}")).message()).hasSize(1000);
        assertThatThrownBy(() -> InquiryRequests.read(JSON, b("{\"category\":\"OTHER\",\"message\":\"" + "가".repeat(1001) + "\"}")))
                .isInstanceOf(InvalidRequest.class);
    }

    @Test
    void theBodyIsBoundedAtFourKibibytes() {
        String head = "{\"category\":\"OTHER\",\"message\":\"";
        String tail = "\"}";
        String fits = head + "a".repeat(InquiryRequests.MAX_BODY_BYTES - head.length() - tail.length()) + tail;
        assertThat(b(fits)).hasSize(4096);
        // 4,096바이트 본문은 형식상 받지만 본문이 1,000자를 넘어 스키마가 거부한다 — 상한 + 1은 읽기 전에 거부
        assertThatThrownBy(() -> InquiryRequests.read(JSON, b(fits))).isInstanceOf(InvalidRequest.class);
        assertThatThrownBy(() -> InquiryRequests.read(JSON, b(fits + " "))).isInstanceOf(InvalidRequest.class);
        String spaced = "{\"category\":\"OTHER\",\"message\":\"m\"" + " ".repeat(4096 - 33 - 1) + "}";
        assertThat(b(spaced)).hasSize(4096);
        assertThat(InquiryRequests.read(JSON, b(spaced)).message()).isEqualTo("m");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"category\":\"AMOUNT\",\"message\":\"m\"}",                         // 라인 분류에 calcId 없음
            "{\"category\":\"LINE\",\"calcId\":\"C1\",\"message\":\"m\"}",         // 닫힌 분류 밖
            "{\"category\":\"OTHER\",\"message\":\"\"}",                           // 빈 본문
            "{\"category\":\"OTHER\",\"message\":\"   \\n\\t \"}",                 // 공백만
            "{\"category\":\"OTHER\"}",                                            // 본문 없음
            "{\"category\":\"OTHER\",\"message\":\"m\",\"agentId\":\"A-2002\"}",   // 모르는 키(설계사는 호출자에서)
            "{\"category\":\"OTHER\",\"message\":\"m\",\"message\":\"n\"}",        // 중복 키
            "{\"category\":\"OTHER\",\"calcId\":\"\",\"message\":\"m\"}",          // 빈 라인 키
            "{\"category\":\"OTHER\",\"message\":\"a\\u0000b\"}",                  // 저장할 수 없는 U+0000
            "{\"category\":\"OTHER\",\"message\":\"a\\ud800b\"}",                  // 짝 없는 서로게이트
            "{\"category\":\"OTHER\",\"message\":42}",                             // 문자열이 아니다
            "[{\"category\":\"OTHER\",\"message\":\"m\"}]",                        // 객체가 아니다
            "{\"category\":\"OTHER\",\"message\":\"m\"",                           // 깨진 JSON
    })
    void everythingElseIsOneRejection(String json) {
        assertThatThrownBy(() -> InquiryRequests.read(JSON, b(json))).isInstanceOf(InvalidRequest.class).hasMessage("body");
    }

    @Test
    void theContentTypeMustBeJson() {
        byte[] ok = b("{\"category\":\"OTHER\",\"message\":\"m\"}");
        assertThatThrownBy(() -> InquiryRequests.read(Optional.empty(), ok)).isInstanceOf(InvalidRequest.class);
        assertThatThrownBy(() -> InquiryRequests.read(Optional.of("text/plain"), ok)).isInstanceOf(InvalidRequest.class);
        assertThatThrownBy(() -> InquiryRequests.read(Optional.of("application/json; charset=latin1"), ok)).isInstanceOf(InvalidRequest.class);
        assertThatThrownBy(() -> InquiryRequests.read(JSON, new byte[0])).isInstanceOf(InvalidRequest.class);
    }

    /** C7: 깨진 본문·스키마 위반 본문의 거부에 입력 조각이 실리지 않는다(예외 메시지·원인 모두). */
    @Test
    void aRejectionCarriesNoPartOfTheMessage() {
        for (String json : new String[] {"{\"category\":\"OTHER\",\"message\":\"" + SENTINEL + "\"", "{\"category\":\"NOPE\",\"message\":\"" + SENTINEL + "\"}",
                "{\"category\":\"OTHER\",\"message\":\"" + SENTINEL + "\" " + SENTINEL + "}"}) {
            InvalidRequest e = catchThrowableOfType(InvalidRequest.class, () -> InquiryRequests.read(JSON, b(json)));
            assertThat(e.getMessage()).isEqualTo("body");
            assertThat(e.getCause()).isNull();
            assertThat(e.toString()).doesNotContain(SENTINEL);
        }
        assertThat(InquiryRequests.read(JSON, b("{\"category\":\"OTHER\",\"message\":\"" + SENTINEL + "\"}")).toString()).doesNotContain(SENTINEL);
    }
}
