package com.ga.portal.query.rule;

import com.ga.platform.core.time.Ym;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code payoutExpectedOn}(지시문 D-12, 계획 Q12): 테넌트 {@code payout_day_rule}이 {@code NEXT_MONTH_D{n}}이면 마감월 다음 달 n일. 영업일 보정 없음
 * (§14.1 #2 미결정). 형식이 다르거나 n이 그 달 일수보다 크면 <b>생략</b>하고 사유를 돌려준다(호출자가 경고로 남긴다) — 정하지 않은 영업 규칙(말일로 당김 등)을
 * 지어내지 않는다. 날짜 산술은 금액이 아니다.
 */
public final class PayoutDay {

    /** 생략 사유(경고 로그용 닫힌 코드). */
    public enum Omission { RULE_FORMAT, DAY_BEYOND_MONTH }

    public record Result(Optional<LocalDate> date, Optional<Omission> omitted) {
    }

    private static final Pattern NEXT_MONTH = Pattern.compile("NEXT_MONTH_D([1-9][0-9]?)");

    private PayoutDay() {
    }

    public static Result expected(String rule, Ym closeYm) {
        Matcher m = NEXT_MONTH.matcher(rule == null ? "" : rule);
        if (!m.matches()) {
            return new Result(Optional.empty(), Optional.of(Omission.RULE_FORMAT));
        }
        int day = Integer.parseInt(m.group(1));
        Ym next = closeYm.next();
        YearMonth month = YearMonth.of(next.year(), next.month());
        if (day > month.lengthOfMonth()) {
            return new Result(Optional.empty(), Optional.of(Omission.DAY_BEYOND_MONTH));
        }
        return new Result(Optional.of(month.atDay(day)), Optional.empty());
    }
}
