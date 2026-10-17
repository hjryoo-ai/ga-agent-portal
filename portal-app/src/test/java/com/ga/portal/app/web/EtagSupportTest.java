package com.ga.portal.app.web;

import com.ga.portal.query.QueryResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** C7: If-None-Match — 약한 비교, 목록, {@code *}; 304는 본문 없이 같은 ETag. */
class EtagSupportTest {

    static final String TAG = "W/\"11.O.0a1b2c3d\"";

    @Test
    void weakComparisonListsAndStar() {
        assertThat(EtagSupport.matches(TAG, TAG)).isTrue();
        assertThat(EtagSupport.matches("\"11.O.0a1b2c3d\"", TAG)).isTrue();
        assertThat(EtagSupport.matches("W/\"1.O.00000000\" ,  " + TAG, TAG)).isTrue();
        assertThat(EtagSupport.matches("*", TAG)).isTrue();
        assertThat(EtagSupport.matches("W/\"11.D.0a1b2c3d\"", TAG)).isFalse();
        assertThat(EtagSupport.matches(null, TAG)).isFalse();
        assertThat(EtagSupport.matches(" ", TAG)).isFalse();
    }

    @Test
    void notModifiedHasNoBodyAndTheSameTag() {
        var notModified = EtagSupport.respond(new QueryResult<>("body", TAG), TAG);
        assertThat(notModified.getStatusCode().value()).isEqualTo(304);
        assertThat(notModified.getBody()).isNull();
        assertThat(notModified.getHeaders().getETag()).isEqualTo(TAG);
        var ok = EtagSupport.respond(new QueryResult<>("body", TAG), null);
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat(ok.getBody()).isEqualTo("body");
        assertThat(ok.getHeaders().getETag()).isEqualTo(TAG);
    }
}
