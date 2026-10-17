package com.ga.portal.app.web;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.infra.testing.SeedData;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C4: 테넌트는 토큰 발급자({@code iss})가 정하고, 설계사·역할은 {@code identity_link}에서만 — 토큰의 {@code agent_id}·{@code roles} 클레임은 아무것도 바꾸지
 * 않는다. {@code tenant_id} 클레임은 교차 검사(다르면 401). 발급자 표는 요청마다 읽는다(해제가 즉시 효력). 등록된 발급자의 메타데이터는 한 번만 받는다.
 */
class TenantBindingIT extends ApiIntegrationTest {

    static final String WHOAMI = "/api/v1/test/whoami";

    static String text(HttpResponse<byte[]> r) {
        return new String(r.body(), StandardCharsets.UTF_8);
    }

    @Test
    void theIssuerChoosesTheTenantAndTheLinkChoosesTheAgent() {
        HttpResponse<byte[]> one = get(WHOAMI, ISSUER_1.token(SUB_A, AUD_1));
        assertThat(one.statusCode()).isEqualTo(200);
        assertThat(text(one)).isEqualTo(T1.value() + "|" + AGENT_A + "|AGENT");
        // 같은 subject 문자열 — 다른 발급자면 다른 테넌트, 그 테넌트의 링크
        HttpResponse<byte[]> two = get(WHOAMI, ISSUER_2.token(SUB_A, AUD_2));
        assertThat(two.statusCode()).isEqualTo(200);
        assertThat(text(two)).isEqualTo(T2.value() + "|" + AGENT_A2 + "|AGENT");
    }

    @Test
    void agentAndRoleClaimsInTheTokenChangeNothing() {
        HttpResponse<byte[]> r = get(WHOAMI, ISSUER_1.token(SUB_A, AUD_1, c -> c
                .claim("agent_id", AGENT_B).claim("roles", List.of("MANAGER", "OPERATOR")).claim("org_path", "/HQ9")
                .claim("tenant_id", T1.value())));
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(text(r)).isEqualTo(T1.value() + "|" + AGENT_A + "|AGENT");
        // 링크의 역할이 MANAGER면 Caller도 MANAGER(조회 진입점의 거부는 4단계 — AuthzIT)
        assertThat(text(get(WHOAMI, ISSUER_1.token(SUB_M, AUD_1, c -> c.claim("roles", List.of("AGENT"))))))
                .isEqualTo(T1.value() + "|" + AGENT_M + "|MANAGER");
    }

    @Test
    void aTenantClaimMustMatchTheIssuersTenantAndAnotherIssuersAudienceIsRejected() {
        assertThat(get(WHOAMI, ISSUER_1.token(SUB_A, AUD_1, c -> c.claim("tenant_id", T2.value()))).statusCode()).isEqualTo(401);
        assertThat(get(WHOAMI, ISSUER_2.token(SUB_A, AUD_1)).statusCode()).as("issuer 2 with issuer 1's audience").isEqualTo(401);
        assertThat(get(WHOAMI, STRANGER.token(SUB_A, AUD_1, c -> c.claim("tenant_id", T1.value()))).statusCode())
                .as("an unregistered issuer cannot name a tenant").isEqualTo(401);
    }

    @Test
    void unregisteringAnIssuerTakesEffectOnTheNextRequest() {
        TestIssuer issuer = new TestIssuer("ph3-t3");
        TenantId t3 = TenantId.of(SeedData.uniqueTenant("PH3C"));
        seedTenant(t3, issuer.issuer(), "portal-ph3-t3");
        link(t3, SUB_A, AGENT_A, "AGENT", "ACTIVE");
        try {
            assertThat(get(WHOAMI, issuer.token(SUB_A, "portal-ph3-t3")).statusCode()).isEqualTo(200);
            DB.seed(t3.value(), c -> SeedData.exec(c, "UPDATE tenant SET oidc_issuer = NULL, oidc_audience = NULL WHERE tenant_id = ?", t3.value()));
            assertThat(get(WHOAMI, issuer.token(SUB_A, "portal-ph3-t3")).statusCode()).isEqualTo(401);
        } finally {
            issuer.close();
        }
    }

    @Test
    void anIssuersMetadataIsFetchedOnceAndOnlyForRegisteredIssuers() {
        for (int i = 0; i < 3; i++) {
            assertThat(get(WHOAMI, ISSUER_1.token(SUB_B, AUD_1)).statusCode()).isEqualTo(200);
        }
        assertThat(get(WHOAMI, STRANGER.token(SUB_A, AUD_1)).statusCode()).isEqualTo(401);
        assertThat(ISSUER_1.metadataRequests()).isEqualTo(1);
        assertThat(STRANGER.metadataRequests()).as("no request to an unregistered issuer").isZero();
        assertThat(STRANGER.jwksRequests()).isZero();
    }
}
