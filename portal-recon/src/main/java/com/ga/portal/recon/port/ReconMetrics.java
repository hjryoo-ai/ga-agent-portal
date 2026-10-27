package com.ga.portal.recon.port;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.NotificationType;
import com.ga.portal.domain.ReconRunResult;
import com.ga.portal.recon.rule.CheckResult;
import com.ga.portal.recon.rule.InconclusiveReason;

/** 대사 지표({@code docs/지표.md} — 태그 집합 고정). */
public interface ReconMetrics {

    void check(TenantId tenant, CheckResult result);

    void inconclusive(TenantId tenant, InconclusiveReason reason);

    void run(TenantId tenant, ReconRunResult result);

    void unresolved(TenantId tenant, int count);

    void notificationsDerived(TenantId tenant, NotificationType type, int inserted);
}
