package com.ga.portal.domain.view;


import java.util.List;
import java.util.Objects;

/** 한도 원장 하나 + 남은 한도(SQL의 정수 차감 {@code limit_amount − accum_paid}) + 열린 플래그 유형(정렬, 같은 문장의 집계). */
public record LedgerItem(LedgerRow ledger, long remaining, List<String> openFlags) {
    public LedgerItem {
        Objects.requireNonNull(ledger, "ledger");
        openFlags = List.copyOf(openFlags);
    }
}
