package com.ga.portal.inbox.api;

import com.ga.portal.domain.notify.NotificationParams;

import java.time.Instant;
import java.util.Optional;

/**
 * 알림 하나(계약 {@code Notification} — {@code oneOf} 변이 여섯, {@code type}이 고른다). 금액·증권번호 원문·설계사·dedup 키가 없다(C5). 계약 상태 변이에는
 * {@code link}가 없다(계획 Q19).
 */
public sealed interface Notification {

    String id();

    String type();

    record Ledger(String id, String type, String severity, Instant createdAt, Optional<Instant> readAt, Optional<String> link,
                  NotificationParams.Ledger params) implements Notification {
    }

    record Line(String id, String type, String severity, Instant createdAt, Optional<Instant> readAt, Optional<String> link,
                NotificationParams.Line params) implements Notification {
    }

    record Deferral(String id, String type, String severity, Instant createdAt, Optional<Instant> readAt, Optional<String> link,
                    NotificationParams.Deferral params) implements Notification {
    }

    record Policy(String id, String type, String severity, Instant createdAt, Optional<Instant> readAt,
                  NotificationParams.Policy params) implements Notification {
    }

    record Month(String id, String type, String severity, Instant createdAt, Optional<Instant> readAt, Optional<String> link,
                 NotificationParams.Month params) implements Notification {
    }

    record Inquiry(String id, String type, String severity, Instant createdAt, Optional<Instant> readAt, Optional<String> link,
                   NotificationParams.Inquiry params) implements Notification {
    }
}
