package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.app.inbox.NotificationParamsJson;
import com.ga.portal.domain.notify.NotificationRecord;
import com.ga.portal.domain.view.SyncSnapshot;
import com.ga.portal.infra.persistence.NotificationRepository;
import com.ga.portal.infra.persistence.SyncStatusRepository;
import com.ga.portal.projection.Envelope;
import com.ga.portal.projection.NotificationDeriver;
import com.ga.portal.projection.ProjectionResult;
import com.ga.portal.projection.notify.Candidate;
import com.ga.portal.projection.notify.NotificationRules;
import com.ga.portal.projection.notify.Thresholds;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 알림 파생(설계서 §6.5 v1.9, 포털 Phase 6 계획 ④): 적용기가 프로젝터 다음 <b>같은 트랜잭션</b>에서 부른다(RM-4 — 커서를 따로 읽는 파생기는 없다). 프로젝터의
 * {@code Change}에 {@link NotificationRules}를 돌리고 후보마다 한 행을 넣는다 — 같은 {@code (설계사, dedup_key)}가 있으면 아무것도 하지 않는다(재적용·재시작에도
 * 같은 행 하나). 키는 이름 기반 UUID, 시각은 적용 시계(엔진 {@code occurredAt}이 아니라 포털이 안 시각 — Q29). 임계치는 전기일 때만 읽는다(테넌트 설정의 bp —
 * 조회 카드와 같은 문장). 재구축({@code Rebuilder})은 이 클래스를 부르지 않는다(§6.7 — ArchUnit이 전이 의존 0을 본다). 예외는 그 이벤트의 {@code APPLY_ERROR}다
 * (뷰·알림·{@code ingest_event}가 함께 롤백 — Q16).
 */
final class JdbcNotificationDeriver implements NotificationDeriver {

    private final NotificationRepository notifications;
    private final SyncStatusRepository settings;
    private final Clock clock;

    JdbcNotificationDeriver(NotificationRepository notifications, SyncStatusRepository settings, Clock clock) {
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void derive(TenantId tenant, Envelope envelope, ProjectionResult result) {
        result.change().ifPresent(change -> {
            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);   // timestamptz 정밀도 — 재시작 비교가 바이트로 같게
            for (Candidate c : NotificationRules.candidates(envelope, change, this::thresholds)) {
                notifications.insertIfAbsent(new NotificationRepository.Fresh(NotificationRecord.id(tenant.value(), c.agentId(), c.dedupKey()),
                        c.agentId(), c.type().name(), c.severity().name(), c.dedupKey(), c.link(), NotificationParamsJson.write(c.params()), now));
            }
        });
    }

    private Thresholds thresholds() {
        SyncSnapshot s = settings.snapshot();
        return new Thresholds(s.limitWarnBp(), s.limitCritBp());
    }
}
