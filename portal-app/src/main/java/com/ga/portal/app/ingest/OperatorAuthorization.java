package com.ga.portal.app.ingest;

import com.ga.portal.domain.IdentityLinkRecord;
import com.ga.portal.domain.LinkStatus;
import com.ga.portal.domain.Role;
import com.ga.portal.infra.persistence.AuditAccessRepository;
import com.ga.portal.infra.persistence.IdentityLinkRepository;
import com.ga.portal.domain.access.DenyReason;

import java.time.Clock;
import java.util.Optional;

/**
 * 운영 명령의 인가(포털 Phase 3 지시문 D-9, 계획 승인 Q10): 실행한 OS 사용자 {@code cli:<user>}가 <b>그 테넌트의</b> {@code identity_link}에 {@code OPERATOR}·
 * {@code ACTIVE}로 등록돼 있어야 한다(등록·해제는 운영 SQL — {@code docs/운영.md}). 없으면 거부하고 그 테넌트에 감사 {@code AUTHZ_DENIED}(detail = 닫힌 사유
 * 코드 {@code NO_LINK}·{@code REVOKED}·{@code ROLE}, target = 명령). 바인딩된 테넌트의 트랜잭션 안에서 부른다 — 거부 감사는 그 트랜잭션과 함께 커밋된다
 * (명령은 아무것도 바꾸지 않았다).
 */
final class OperatorAuthorization {

    static final String ACTION = "AUTHZ_DENIED";

    private final IdentityLinkRepository links;
    private final AuditAccessRepository audit;
    private final Clock clock;

    OperatorAuthorization(IdentityLinkRepository links, AuditAccessRepository audit, Clock clock) {
        this.links = links;
        this.audit = audit;
        this.clock = clock;
    }

    /** 거부 사유(감사까지 남긴 뒤) 또는 빈 값(허용). */
    Optional<DenyReason> deny(String subject, String command) {
        Optional<IdentityLinkRecord> link = links.find(subject);
        Optional<DenyReason> reason;
        if (link.isEmpty()) {
            reason = Optional.of(DenyReason.NO_LINK);
        } else if (link.get().status() != LinkStatus.ACTIVE) {
            reason = Optional.of(DenyReason.REVOKED);
        } else if (link.get().role() != Role.OPERATOR) {
            reason = Optional.of(DenyReason.ROLE);
        } else {
            reason = Optional.empty();
        }
        reason.ifPresent(r -> audit.recordDenial(subject, ACTION, "cli:ingest " + command, r.name(), clock.instant()));
        return reason;
    }
}
