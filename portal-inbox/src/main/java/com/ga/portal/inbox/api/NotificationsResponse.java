package com.ga.portal.inbox.api;

import java.util.List;

/** 알림 한 페이지(계약 {@code NotificationsResponse}). {@code unreadCount}는 필터와 무관한 그 설계사의 안읽음 수 — 화면은 이 값을 그대로 보인다(C10). */
public record NotificationsResponse(List<Notification> items, int page, int size, long total, long unreadCount) {

    public NotificationsResponse {
        items = List.copyOf(items);
    }
}
