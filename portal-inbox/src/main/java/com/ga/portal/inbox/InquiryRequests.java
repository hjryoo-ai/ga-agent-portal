package com.ga.portal.inbox;

import com.ga.portal.domain.InquiryCategory;
import com.ga.portal.domain.access.InvalidRequest;
import com.networknt.schema.Schema;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Locale;
import java.util.Optional;

/**
 * 문의 요청 본문(포털 Phase 6 ⑥ — 계약 {@code InquiryRequest}). 순서: 형식({@code application/json}, 4 KiB 이하, JSON 객체 하나 — 중복 키 거부) → 계약 스키마
 * (닫힌 분류, 라인 분류는 {@code calcId} 필수, 본문 1~1,000 코드 포인트·공백만 금지) → 저장할 수 없는 글자(U+0000·짝 없는 서로게이트). 어느 단계든 400 한
 * 가지({@link InvalidRequest}, 사유·입력값 없음).
 *
 * <p><b>본문 격리</b>(C7): 파서가 오류 위치에 입력 조각을 싣지 않게 한다({@link StreamReadFeature#INCLUDE_SOURCE_IN_LOCATION} 끔), 예외는 규칙 이름만 갖는다 — 본문은
 * 로그·예외 메시지로 나가지 않는다.
 */
public final class InquiryRequests {

    public static final int MAX_BODY_BYTES = 4096;
    public static final int MAX_MESSAGE_CODE_POINTS = 1000;
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .build();

    private InquiryRequests() {
    }

    /** 검사를 지난 요청. */
    public record Request(InquiryCategory category, Optional<String> calcId, String message) {

        @Override
        public String toString() {
            return "Request[category=" + category + "]";
        }
    }

    public static Request read(Optional<String> contentType, byte[] body) {
        if (!isJson(contentType) || body == null || body.length == 0 || body.length > MAX_BODY_BYTES) {
            throw new InvalidRequest("body");
        }
        JsonNode node;
        try {
            node = JSON.readTree(body);
        } catch (JacksonException e) {
            throw new InvalidRequest("body");
        }
        Schema schema = InboxContracts.get().schema("InquiryRequest");
        if (node == null || !node.isObject() || !schema.validate(node).isEmpty()) {
            throw new InvalidRequest("body");
        }
        String message = node.path("message").asString();
        if (message.codePointCount(0, message.length()) > MAX_MESSAGE_CODE_POINTS || !storable(message)) {
            throw new InvalidRequest("body");
        }
        Optional<String> calcId = node.has("calcId") ? Optional.of(node.path("calcId").asString()) : Optional.empty();
        if (calcId.isPresent() && !storable(calcId.get())) {
            throw new InvalidRequest("body");
        }
        return new Request(InquiryCategory.valueOf(node.path("category").asString()), calcId, message);
    }

    /** PostgreSQL {@code text}(UTF-8)에 넣을 수 있는 글자만 — U+0000과 짝 없는 서로게이트는 아니다. */
    static boolean storable(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u0000') {
                return false;
            }
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) {
                    return false;
                }
                i++;
            } else if (Character.isLowSurrogate(c)) {
                return false;
            }
        }
        return true;
    }

    static boolean isJson(Optional<String> contentType) {
        if (contentType.isEmpty()) {
            return false;
        }
        String[] parts = contentType.get().split(";", -1);
        if (!parts[0].strip().toLowerCase(Locale.ROOT).equals("application/json")) {
            return false;
        }
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].strip().toLowerCase(Locale.ROOT).replace(" ", "").equals("charset=utf-8")) {
                return false;
            }
        }
        return true;
    }
}
