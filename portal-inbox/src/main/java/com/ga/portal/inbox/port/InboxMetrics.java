package com.ga.portal.inbox.port;

import com.ga.platform.core.tenant.TenantId;

/** 문의 지표(설계서 §10 v1.9): {@code api_rate_limited_total{tenant, route="inquiries"}} — 429 하나에 1. 설계사 태그·본문 없음. */
public interface InboxMetrics {

    void inquiryRateLimited(TenantId tenant);
}
