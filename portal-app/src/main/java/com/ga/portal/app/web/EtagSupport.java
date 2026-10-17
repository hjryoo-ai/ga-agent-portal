package com.ga.portal.app.web;

import com.ga.portal.query.QueryResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;

/**
 * 조건부 조회(지시문 D-8, 계획 ⑤ Q4): 유스케이스가 결과를 냈다는 것 = 소유 확인을 지났다는 것 — 그 뒤에만 {@code If-None-Match}를 비교한다(남의 자원은 304가
 * 아니라 404). 약한 비교({@code W/} 무시), {@code *}는 일치. 304는 본문 없이 같은 {@code ETag}·{@code Cache-Control}(보안 체인이 {@code private, no-cache}).
 */
final class EtagSupport {

    static final String IF_NONE_MATCH = HttpHeaders.IF_NONE_MATCH;

    private EtagSupport() {
    }

    static <T> ResponseEntity<T> respond(QueryResult<T> result, String ifNoneMatch) {
        if (matches(ifNoneMatch, result.etag())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(result.etag()).build();
        }
        return ResponseEntity.ok().eTag(result.etag()).body(result.body());
    }

    static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        String ours = opaque(etag);
        return Arrays.stream(ifNoneMatch.split(",")).map(String::trim).anyMatch(t -> t.equals("*") || opaque(t).equals(ours));
    }

    private static String opaque(String tag) {
        return tag.startsWith("W/") ? tag.substring(2) : tag;
    }
}
