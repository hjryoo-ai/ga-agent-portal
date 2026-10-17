package com.ga.portal.query;

import java.util.Objects;

/** 응답 본문과 그 약한 ETag(계획 ⑤ Q4). 앱은 소유 확인(= 이 결과가 나왔다는 것) 뒤에만 {@code If-None-Match}를 비교한다. */
public record QueryResult<T>(T body, String etag) {
    public QueryResult {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(etag, "etag");
    }
}
