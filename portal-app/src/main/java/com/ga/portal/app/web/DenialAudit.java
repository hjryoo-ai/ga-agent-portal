package com.ga.portal.app.web;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.infra.persistence.AuditAccessRepository;
import com.ga.portal.domain.access.DenyReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;

/**
 * 404 거부의 감사(지시문 D-3, 계획 승인 Q6): {@code audit_access} action {@code AUTHZ_DENIED}, {@code target} = 경로 템플릿(값 없음), {@code detail} = 닫힌 사유 코드.
 * <b>별도 트랜잭션</b>이라 본 요청이 롤백돼도 남는다. 그리고 지표 {@code api_authz_denied_total}과 로그(식별자는 해시 — D-14).
 */
final class DenialAudit {

    static final String ACTION = "AUTHZ_DENIED";
    private static final Logger LOG = LoggerFactory.getLogger(DenialAudit.class);

    private final AuditAccessRepository audits;
    private final ApiTransactions transactions;
    private final ApiMetrics metrics;
    private final Clock clock;

    DenialAudit(AuditAccessRepository audits, ApiTransactions transactions, ApiMetrics metrics, Clock clock) {
        this.audits = audits;
        this.transactions = transactions;
        this.metrics = metrics;
        this.clock = clock;
    }

    void record(TenantId tenant, String subject, DenyReason reason, String route) {
        transactions.separate(() -> audits.recordDenial(subject, ACTION, route, reason.name(), clock.instant()));
        metrics.authzDenied(tenant, reason);
        LOG.info("API_AUTHZ_DENIED tenant={} reason={} subject#={} route={}", tenant.value(), reason.name(), LogIds.subject(subject), route);
    }
}
