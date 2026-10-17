package com.ga.portal.query.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 한도 원장 카드. {@code limitMultiple}은 계약 원문 문자열, {@code remaining} = 한도 − 누적(정수 차감), {@code utilization}은 표시 문자열(한도 0이면 생략),
 * {@code stage}는 정수 bp 비교(D-13). {@code flags}는 열린 계약 플래그 유형.
 */
public record Ledger(String ledgerId, String policyMasked, String policyKey, LocalDate contractDate, LocalDate fyStart, LocalDate fyEnd,
                     long monthlyPremium, String limitMultiple, long limitAmount, long accumPaid, long remaining, Optional<String> utilization,
                     String stage, long cutTotal, long deferredTotal, List<String> flags) {
}
