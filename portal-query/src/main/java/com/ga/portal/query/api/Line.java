package com.ga.portal.query.api;

import java.util.Optional;

/** 라인 한 줄 — 평평하게, 취소 짝은 {@code reversalOf}·{@code reversedBy}로(접기는 화면 — Q7). 증권번호는 {@code policyMasked}·{@code policyKey}로만. */
public record Line(String calcId, String closeYm, String commType, long amount, long limitCutAmt, Optional<String> limitCutPolicy,
                   Optional<String> productKey, Optional<String> productName, Optional<String> insuredMasked, String policyMasked, String policyKey,
                   Optional<String> reversalOf, Optional<String> reversedBy) {
}
