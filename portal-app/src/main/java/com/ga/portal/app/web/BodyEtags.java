package com.ga.portal.app.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 알림·문의 GET의 조건부 응답(포털 Phase 6 계획 Q22): 약한 ETag {@code W/"<본문 SHA-256 앞 16자>"} — 읽음·답변·종료는 피드 위치({@code seq})와 무관하게 바뀌므로
 * 뷰 ETag의 재료를 쓰지 않고 응답 바이트에서 만든다. 소유 확인(유스케이스가 결과를 냈다) 뒤에만 비교한다({@link EtagSupport}와 같은 규약 — 남의 자원은 404).
 */
final class BodyEtags {

    private BodyEtags() {
    }

    static String etag(byte[] body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
            return "W/\"" + HexFormat.of().formatHex(digest, 0, 8) + "\"";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static ResponseEntity<byte[]> respond(JsonMapper json, Object body, String ifNoneMatch) {
        byte[] bytes = json.writeValueAsBytes(body);
        String etag = etag(bytes);
        if (EtagSupport.matches(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).contentType(MediaType.APPLICATION_JSON).body(bytes);
    }

    static ResponseEntity<byte[]> noStore(JsonMapper json, HttpStatus status, Object body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(json.writeValueAsBytes(body));
    }
}
