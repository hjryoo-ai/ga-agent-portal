package com.ga.portal.app.inbox;

import com.ga.portal.domain.NotificationType;
import com.ga.portal.domain.notify.NotificationParams;
import com.ga.portal.domain.notify.NotificationShapes;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code notification.params}(JSONB, V7) ↔ 모양 레코드(포털 Phase 6 계획 ③). 쓰기는 레코드 성분 그대로(금액 없음), 읽기는 그 종류의 모양으로만 — 모르는 키·빠진 키·
 * 형식 밖 값은 예외(조용히 버리지 않는다 — 계약이 {@code additionalProperties: false}).
 */
public final class NotificationParamsJson {

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .build();

    private NotificationParamsJson() {
    }

    public static String write(NotificationParams params) {
        return JSON.writeValueAsString(params);
    }

    public static NotificationParams read(NotificationType type, String json) {
        try {
            return JSON.readValue(json, NotificationShapes.paramsType(type));
        } catch (JacksonException | IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException("notification params do not fit " + type + ": " + e.getClass().getSimpleName());
        }
    }
}
