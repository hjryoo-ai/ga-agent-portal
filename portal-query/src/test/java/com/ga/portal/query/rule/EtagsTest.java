package com.ga.portal.query.rule;

import com.ga.portal.query.rule.SyncStatusRule.SyncStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** C7·Q4: 약한 ETag = seq·동기화 한 글자·읽은 설정 지문(+ 페이지·필터) — 어느 하나가 바뀌면 ETag가 바뀐다. */
class EtagsTest {

    @Test
    void shape() {
        assertThat(Etags.of(11, SyncStatus.OK, List.of("300"))).matches("W/\"11\\.O\\.[0-9a-f]{8}\"");
        assertThat(Etags.ofPage(11, SyncStatus.OK, List.of("300"), 0, 50, List.of("", ""))).matches("W/\"11\\.O\\.[0-9a-f]{8}\\.0\\.50\\.[0-9a-f]{8}\"");
    }

    @Test
    void eachInputChangesTheTag() {
        String base = Etags.of(11, SyncStatus.OK, List.of("NEXT_MONTH_D10", "300"));
        assertThat(Etags.of(12, SyncStatus.OK, List.of("NEXT_MONTH_D10", "300"))).isNotEqualTo(base);
        assertThat(Etags.of(11, SyncStatus.SYNC_DELAYED, List.of("NEXT_MONTH_D10", "300"))).isNotEqualTo(base);
        assertThat(Etags.of(11, SyncStatus.OK, List.of("NEXT_MONTH_D25", "300"))).isNotEqualTo(base);
        assertThat(Etags.of(11, SyncStatus.OK, List.of("NEXT_MONTH_D10", "60"))).isNotEqualTo(base);
        // 구분자가 있어 값의 경계를 옮겨도 같은 지문이 나오지 않는다
        assertThat(Etags.hash8(List.of("ab", "c"))).isNotEqualTo(Etags.hash8(List.of("a", "bc")));
        String page = Etags.ofPage(11, SyncStatus.OK, List.of("300"), 0, 50, List.of("FY_COMM", ""));
        assertThat(Etags.ofPage(11, SyncStatus.OK, List.of("300"), 1, 50, List.of("FY_COMM", ""))).isNotEqualTo(page);
        assertThat(Etags.ofPage(11, SyncStatus.OK, List.of("300"), 0, 20, List.of("FY_COMM", ""))).isNotEqualTo(page);
        assertThat(Etags.ofPage(11, SyncStatus.OK, List.of("300"), 0, 50, List.of("", "FY_COMM"))).isNotEqualTo(page);
    }
}
