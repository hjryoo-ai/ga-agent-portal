package com.ga.portal.domain;

import com.ga.platform.core.tenant.AgentId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V5 {@code identity_link}의 CHECK 둘(OPERATOR만 설계사 없음·OPERATOR만 조직 없음)을 기록 타입도 강제한다(Phase 3 계획 승인 Q2). */
class IdentityLinkRecordTest {

    private static final Instant AT = Instant.parse("2026-10-17T00:00:00Z");

    @Test
    void agentsAndManagersHaveAnAgentAndAnOrgPathOperatorsHaveNeither() {
        assertThat(new IdentityLinkRecord("sub", Optional.of(AgentId.of("A-1001")), Role.AGENT, LinkStatus.ACTIVE, Optional.of("/HQ1"), AT).role())
                .isEqualTo(Role.AGENT);
        assertThat(new IdentityLinkRecord("cli:ops", Optional.empty(), Role.OPERATOR, LinkStatus.ACTIVE, Optional.empty(), AT).agentId()).isEmpty();
        assertThatThrownBy(() -> new IdentityLinkRecord("cli:ops", Optional.of(AgentId.of("A-1001")), Role.OPERATOR, LinkStatus.ACTIVE,
                Optional.empty(), AT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdentityLinkRecord("sub", Optional.empty(), Role.AGENT, LinkStatus.ACTIVE, Optional.of("/HQ1"), AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdentityLinkRecord("sub", Optional.of(AgentId.of("A-1001")), Role.MANAGER, LinkStatus.ACTIVE, Optional.empty(), AT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
