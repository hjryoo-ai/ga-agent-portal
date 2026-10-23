package com.ga.portal.query.rule;

/**
 * 분급 타임라인의 유지율 시나리오(설계서 §6.4, 포털 Phase 5 ④) — <b>포털이 금액을 곱하는 유일한 자리</b>(CLAUDE.md 규칙 1의 예외 하나, 지시문 승인).
 * {@code RETENTION}이면 월 예정 합 {@code expected}를 {@code floor(expected × p / 100)}로 바꾼다. 보류({@code held})·항목·적용 전 값은 바꾸지 않는다.
 * 결과는 "참고(유지율 p% 가정)" 라벨로만 보인다 — 정산·명세 값이 아니다.
 *
 * <p>바이트코드의 곱셈·나눗셈은 {@code MultiplicationScanTest}의 닫힌 허용 목록에서 이 메서드({@link #apply})와 두 비금액 연산뿐이다.
 */
public final class ForecastScenario {

    public static final int MIN_P = 1;
    public static final int MAX_P = 100;

    private ForecastScenario() {
    }

    /** {@code floor(expected × p / 100)} — 정수, 넘치면 {@link ArithmeticException}. {@code p}는 1~100. */
    public static long apply(long expected, int p) {
        if (p < MIN_P || p > MAX_P) {
            throw new IllegalArgumentException("p must be 1..100");
        }
        return Math.floorDiv(Math.multiplyExact(expected, (long) p), 100L);
    }
}
