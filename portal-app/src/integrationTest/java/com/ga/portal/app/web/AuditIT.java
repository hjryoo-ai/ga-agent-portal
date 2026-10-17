package com.ga.portal.app.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C9: 404 거부는 감사 행(action {@code AUTHZ_DENIED}, target = 경로 템플릿, detail = 사유 코드)을 <b>별도 트랜잭션</b>으로 남긴다 — 본 요청 트랜잭션이 롤백돼도 남는다.
 * 401은 감사 행이 없고 지표만(발급자가 등록된 경우만 그 테넌트 태그). 로그에는 subject 해시만.
 */
@ExtendWith(OutputCaptureExtension.class)
class AuditIT extends ApiIntegrationTest {

    static final String WHOAMI = "/api/v1/test/whoami";

    static List<AuditLine> since(List<AuditLine> all, int from) {
        return all.subList(from, all.size());
    }

    @Test
    void eachNotFoundReasonIsAuditedWithTheRouteTemplateAndAClosedCode() {
        int from = audits(T1).size();
        assertThat(get(WHOAMI, ISSUER_1.token(SUB_NONE, AUD_1)).statusCode()).isEqualTo(404);
        assertThat(get(WHOAMI, ISSUER_1.token(SUB_R, AUD_1)).statusCode()).isEqualTo(404);
        assertThat(get(WHOAMI, ISSUER_1.token(SUB_O, AUD_1)).statusCode()).isEqualTo(404);
        assertThat(get("/api/v1/test/n/1", ISSUER_1.token(SUB_R, AUD_1)).statusCode()).isEqualTo(404);
        assertThat(since(audits(T1), from)).containsExactly(
                new AuditLine(SUB_NONE, "AUTHZ_DENIED", "GET /api/v1/test/whoami", "NO_LINK"),
                new AuditLine(SUB_R, "AUTHZ_DENIED", "GET /api/v1/test/whoami", "REVOKED"),
                new AuditLine(SUB_O, "AUTHZ_DENIED", "GET /api/v1/test/whoami", "ROLE"),
                new AuditLine(SUB_R, "AUTHZ_DENIED", "GET /api/v1/test/n/{n}", "REVOKED"));
    }

    @Test
    void theDenialAuditSurvivesTheRollbackOfTheRequestTransaction() {
        int from = audits(T1).size();
        assertThat(get("/api/v1/test/missing", ISSUER_1.token(SUB_A, AUD_1)).statusCode()).isEqualTo(404);
        List<AuditLine> added = since(audits(T1), from);
        assertThat(added).containsExactly(new AuditLine(SUB_A, "AUTHZ_DENIED", "GET /api/v1/test/missing", "MISSING"));
        assertThat(added).as("the request transaction's own write rolled back").noneMatch(l -> l.action().equals("PROBE_WORK"));
        assertThat(counter("api_authz_denied_total", T1.value(), "MISSING")).isPositive();
    }

    @Test
    void unauthenticatedRequestsWriteNoAuditRowAndTagTheTenantOnlyForARegisteredIssuer() {
        int from1 = audits(T1).size();
        double expired = counter("api_authn_rejected_total", T1.value(), "EXPIRED");
        double unknown = counter("api_authn_rejected_total", ApiMetrics.NO_TENANT, "ISSUER_UNKNOWN");
        Instant now = Instant.now();
        assertThat(get(WHOAMI, ISSUER_1.token(SUB_A, AUD_1, c -> c.issueTime(Date.from(now.minus(Duration.ofHours(2))))
                .expirationTime(Date.from(now.minus(Duration.ofHours(1)))))).statusCode()).isEqualTo(401);
        assertThat(get(WHOAMI, STRANGER.token(SUB_A, AUD_1, c -> c.claim("tenant_id", T1.value()))).statusCode()).isEqualTo(401);
        assertThat(audits(T1)).hasSize(from1);
        assertThat(counter("api_authn_rejected_total", T1.value(), "EXPIRED")).isEqualTo(expired + 1);
        assertThat(counter("api_authn_rejected_total", ApiMetrics.NO_TENANT, "ISSUER_UNKNOWN")).isEqualTo(unknown + 1);
        assertThat(meters.find("api_authn_rejected_total").tag("reason", "ISSUER_UNKNOWN").counters())
                .as("an unregistered issuer never produces a tenant tag").allMatch(c -> ApiMetrics.NO_TENANT.equals(c.getId().getTag("tenant")));
    }

    @Test
    void theDenialLogCarriesTheSubjectHashNotTheSubject(CapturedOutput output) {
        assertThat(get(WHOAMI, ISSUER_1.token(SUB_NONE, AUD_1)).statusCode()).isEqualTo(404);
        assertThat(output.getAll()).contains("API_AUTHZ_DENIED tenant=" + T1.value() + " reason=NO_LINK subject#=" + LogIds.subject(SUB_NONE))
                .doesNotContain(SUB_NONE);
    }
}
