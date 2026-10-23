package com.ga.portal.app.web;

import com.ga.portal.infra.persistence.AuditAccessRepository;
import com.ga.portal.domain.access.Caller;
import com.ga.portal.domain.access.DenyReason;
import com.ga.portal.domain.access.NotFound;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 시험 전용 {@code /api} 진입점(3단계 — 조회 유스케이스는 4단계): 보안 체인·{@code Caller} 해석·거부 바이트·감사를 실제 조회와 떼어 본다. {@code @TestComponent}라
 * 컴포넌트 스캔에 잡히지 않고 {@link ApiIntegrationTest}의 {@code @Import}로만 들어온다.
 */
@TestComponent
@RestController
class ApiProbe {

    private final AuditAccessRepository audits;
    private final TransactionTemplate transactions;

    ApiProbe(AuditAccessRepository audits, PlatformTransactionManager transactionManager) {
        this.audits = audits;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** 호출자 = 테넌트|설계사|역할(전부 identity_link에서). */
    @GetMapping("/api/v1/test/whoami")
    String whoami(Caller caller) {
        return caller.tenant().value() + "|" + caller.agentId().value() + "|" + caller.role().name();
    }

    /** 본 요청 트랜잭션 안에서 표식 행을 쓰고 {@code NotFound(MISSING)} — 롤백돼 표식은 사라지고 거부 감사는 남아야 한다(C9). */
    @GetMapping("/api/v1/test/missing")
    String missing(Caller caller) {
        return transactions.execute(status -> {
            audits.record("probe", "PROBE_WORK", "probe", Instant.now());
            throw new NotFound(DenyReason.MISSING);
        });
    }

    @GetMapping("/api/v1/test/n/{n}")
    String pathVariable(Caller caller, @PathVariable("n") int n) {
        return Integer.toString(n);
    }

    @GetMapping("/api/v1/test/page")
    String queryParameter(Caller caller, @RequestParam("size") int size) {
        return Integer.toString(size);
    }
}
