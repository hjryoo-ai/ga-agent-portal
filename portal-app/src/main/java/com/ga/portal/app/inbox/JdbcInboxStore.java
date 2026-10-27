package com.ga.portal.app.inbox;

import com.ga.platform.core.tenant.AgentId;
import com.ga.portal.domain.InquiryRecord;
import com.ga.portal.domain.InquiryStatus;
import com.ga.portal.domain.NotificationType;
import com.ga.portal.domain.notify.NotificationRecord;
import com.ga.portal.inbox.port.InboxStore;
import com.ga.portal.infra.persistence.InquiryRepository;
import com.ga.portal.infra.persistence.NotificationRepository;
import com.ga.portal.infra.persistence.TenantRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * {@link InboxStore}의 저장소 어댑터. 설정은 테넌트 행의 {@code params.inquiry_rate_per_day} — 1~9자리 양의 정수일 때만(아니면 비어 있고 앱 기본값). 알림의
 * {@code params}는 저장 원문을 그 종류의 모양으로 읽는다({@link NotificationParamsJson} — 맞지 않으면 500, 행은 그대로).
 */
final class JdbcInboxStore implements InboxStore {

    private static final Pattern RATE = Pattern.compile("[1-9][0-9]{0,8}");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final NotificationRepository notifications;
    private final InquiryRepository inquiries;
    private final TenantRepository tenants;

    JdbcInboxStore(NotificationRepository notifications, InquiryRepository inquiries, TenantRepository tenants) {
        this.notifications = notifications;
        this.inquiries = inquiries;
        this.tenants = tenants;
    }

    static NotificationRecord record(NotificationRepository.Stored s) {
        NotificationType type = NotificationType.valueOf(s.type());
        return new NotificationRecord(s.id(), s.agentId(), type, s.dedupKey(), NotificationParamsJson.read(type, s.paramsJson()), s.link(), s.createdAt(),
                s.readAt());
    }

    @Override
    public NotificationPage notifications(AgentId agent, boolean unreadOnly, int page, int size) {
        NotificationRepository.Counts counts = notifications.counts(agent.value(), unreadOnly);
        return new NotificationPage(notifications.page(agent.value(), unreadOnly, page, size).stream().map(JdbcInboxStore::record).toList(),
                counts.total(), counts.unread());
    }

    @Override
    public Optional<NotificationRecord> markRead(AgentId agent, UUID id, Instant now) {
        return notifications.markRead(agent.value(), id, now).map(JdbcInboxStore::record);
    }

    @Override
    public boolean ownsLine(AgentId agent, String calcId) {
        return inquiries.ownsLine(agent.value(), calcId);
    }

    @Override
    public void lockInquiries(AgentId agent) {
        inquiries.lock(agent.value());
    }

    @Override
    public OptionalInt inquiryRatePerDay() {
        var tenant = tenants.current().orElseThrow(() -> new IllegalStateException("no tenant row for the bound tenant"));
        return rate(tenant.paramsJson());
    }

    static OptionalInt rate(String params) {
        try {
            JsonNode node = JSON.readTree(params == null ? "{}" : params).path("inquiry_rate_per_day");
            String text = node.isIntegralNumber() || node.isString() ? node.asString() : "";
            return RATE.matcher(text).matches() ? OptionalInt.of(Integer.parseInt(text)) : OptionalInt.empty();
        } catch (JacksonException e) {
            return OptionalInt.empty();
        }
    }

    @Override
    public Window inquiryWindow(AgentId agent, Instant since) {
        InquiryRepository.Window w = inquiries.window(agent.value(), since);
        return new Window(w.count(), w.oldest());
    }

    @Override
    public void insertInquiry(InquiryRecord row) {
        inquiries.insert(row);
    }

    @Override
    public InquiryPage inquiries(AgentId agent, Optional<InquiryStatus> status, int page, int size) {
        return new InquiryPage(inquiries.page(agent.value(), status, page, size), inquiries.count(agent.value(), status));
    }

    @Override
    public Optional<InquiryRecord> inquiry(AgentId agent, UUID id) {
        return inquiries.find(agent.value(), id);
    }
}
