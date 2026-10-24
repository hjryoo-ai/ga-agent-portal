package com.ga.portal.inbox.port;

import com.ga.platform.core.tenant.AgentId;
import com.ga.portal.domain.InquiryRecord;
import com.ga.portal.domain.InquiryStatus;
import com.ga.portal.domain.notify.NotificationRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * {@code notification}·{@code inquiry}(포털 Phase 6 ⑥) — 바인딩된 테넌트의 {@link InboxTransactions} 안에서만 부른다. 모든 문장은
 * {@code tenant_id = :tenantId AND agent_id = :agentId}(설정 읽기 제외 — {@code TenantPredicateScanTest}).
 */
public interface InboxStore {

    /** 한 페이지(정렬 {@code created_at DESC, notification_id DESC})와 필터에 맞는 전체 수·그 설계사의 안읽음 수. */
    NotificationPage notifications(AgentId agent, boolean unreadOnly, int page, int size);

    /** {@code read_at = coalesce(read_at, now)} — 그 설계사의 행이 아니면 비어 있다. */
    Optional<NotificationRecord> markRead(AgentId agent, UUID id, Instant now);

    /** 그 설계사의 라인인지(문의의 {@code calcId}). */
    boolean ownsLine(AgentId agent, String calcId);

    /** 그 설계사의 문의 접수 직렬화(트랜잭션 끝까지 — 리밋 판정과 넣기가 한 덩어리). */
    void lockInquiries(AgentId agent);

    /** 테넌트 {@code params.inquiry_rate_per_day}(형식이 맞을 때만 — 아니면 비어 있고 앱 기본값). */
    OptionalInt inquiryRatePerDay();

    /** {@code created_at > since}인 그 설계사의 문의 수와 가장 이른 시각. */
    Window inquiryWindow(AgentId agent, Instant since);

    void insertInquiry(InquiryRecord row);

    InquiryPage inquiries(AgentId agent, Optional<InquiryStatus> status, int page, int size);

    Optional<InquiryRecord> inquiry(AgentId agent, UUID id);

    record NotificationPage(List<NotificationRecord> items, long total, long unread) {
        public NotificationPage {
            items = List.copyOf(items);
        }
    }

    record InquiryPage(List<InquiryRecord> items, long total) {
        public InquiryPage {
            items = List.copyOf(items);
        }
    }

    record Window(long count, Optional<Instant> oldest) {
    }
}
