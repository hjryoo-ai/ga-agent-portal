package com.ga.portal.recon.rule;

/** 설계사×달 대사 한 건의 판정(지표 {@code recon_checks_total{tenant, result}}의 값 집합 — {@code docs/지표.md}). */
public enum CheckResult {
    MATCH,
    DRIFT,
    INCONCLUSIVE
}
