package com.ga.portal.infra.identity;

import com.ga.platform.spring.identity.IdentityResolver;
import com.ga.portal.domain.IdentityLinkRecord;
import com.ga.portal.domain.LinkStatus;
import com.ga.portal.infra.persistence.IdentityLinkRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * 플랫폼 {@link IdentityResolver}의 포털 구현(지시문 D-2): 바인딩된 테넌트의 {@code identity_link} PK 조회 1회, 캐시 없음. 설계사·역할·조직은 이 행에서만
 * 온다 — 토큰의 {@code roles}·{@code agent_id} 클레임은 어디서도 읽지 않는다.
 *
 * <p>{@link #resolve}는 플랫폼 계약대로 "쓸 수 있는 신원"만(ACTIVE이고 설계사가 있는 행). 거부 사유를 가려야 하는 호출자(조회 API의 {@code Caller} 해석 —
 * 링크 없음·REVOKED·OPERATOR를 감사 사유로 나눈다)는 같은 조회의 원 행 {@link #link}를 쓴다. 어느 쪽이든 문장은 하나다.
 */
@Component
public class IdentityLinkResolver implements IdentityResolver {

    private final IdentityLinkRepository links;

    public IdentityLinkResolver(IdentityLinkRepository links) {
        this.links = links;
    }

    /** 원 행(상태·역할 그대로). 바인딩된 테넌트의 트랜잭션 안에서 부른다. */
    public Optional<IdentityLinkRecord> link(String subject) {
        return links.find(subject);
    }

    @Override
    public Optional<ResolvedIdentity> resolve(String oidcSubject) {
        return link(oidcSubject)
                .filter(r -> r.status() == LinkStatus.ACTIVE)
                .flatMap(r -> r.agentId().map(agent -> new ResolvedIdentity(agent, Set.of(r.role().name()), r.orgPath().orElseThrow())));
    }
}
