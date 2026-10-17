package com.ga.portal.query.rule;

import com.ga.platform.core.time.Ym;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** C12·Q12: {@code NEXT_MONTH_D{n}}만, n이 그 달에 없으면 생략(말일로 당기지 않는다), 형식이 다르면 생략. */
class PayoutDayTest {

    @Test
    void nextMonthDayN() {
        assertThat(PayoutDay.expected("NEXT_MONTH_D10", Ym.parse("202608")).date()).contains(LocalDate.of(2026, 9, 10));
        assertThat(PayoutDay.expected("NEXT_MONTH_D28", Ym.parse("202701")).date()).contains(LocalDate.of(2027, 2, 28));
        assertThat(PayoutDay.expected("NEXT_MONTH_D1", Ym.parse("202612")).date()).as("year rolls").contains(LocalDate.of(2027, 1, 1));
        assertThat(PayoutDay.expected("NEXT_MONTH_D29", Ym.parse("202801")).date()).as("leap February").contains(LocalDate.of(2028, 2, 29));
    }

    @Test
    void aDayBeyondTheMonthIsOmittedNotClamped() {
        PayoutDay.Result r = PayoutDay.expected("NEXT_MONTH_D31", Ym.parse("202701"));
        assertThat(r.date()).isEmpty();
        assertThat(r.omitted()).contains(PayoutDay.Omission.DAY_BEYOND_MONTH);
        assertThat(PayoutDay.expected("NEXT_MONTH_D29", Ym.parse("202701")).date()).isEmpty();
        assertThat(PayoutDay.expected("NEXT_MONTH_D31", Ym.parse("202607")).date()).contains(LocalDate.of(2026, 8, 31));
    }

    @Test
    void otherFormatsAreOmitted() {
        for (String rule : new String[] {"NEXT_MONTH_D0", "NEXT_MONTH_D100", "NEXT_MONTH_D010", "SAME_MONTH_D25", "NEXT_BUSINESS_DAY", "", "next_month_d10"}) {
            assertThat(PayoutDay.expected(rule, Ym.parse("202608"))).as(rule)
                    .isEqualTo(new PayoutDay.Result(Optional.empty(), Optional.of(PayoutDay.Omission.RULE_FORMAT)));
        }
    }
}
