package com.ga.portal.app.web;

import com.ga.portal.domain.access.Caller;
import com.ga.portal.query.MeQueries;
import com.ga.portal.query.api.DeferralsResponse;
import com.ga.portal.query.api.EarningsResponse;
import com.ga.portal.query.api.LimitDetailResponse;
import com.ga.portal.query.api.LimitsResponse;
import com.ga.portal.query.api.LineDetailResponse;
import com.ga.portal.query.api.LinesResponse;
import com.ga.portal.query.api.MeResponse;
import com.ga.portal.query.api.MonthResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * {@code /api/v1/me/**}(지시문 D-5) — 얇다: 메서드마다 {@link Caller}(identity_link에서 — 경로·쿼리에 {@code agentId}는 없다)를 받아 {@code @UseCaseEntry}
 * 진입점 하나를 부르고, 결과를 조건부 응답으로. 인가·검증·조회는 유스케이스가 한다(계획 승인 Q8). 정본 계약 {@code contracts/api/v1/portal-api.openapi.yaml}.
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
final class MeController {

    private final MeQueries queries;

    MeController(MeQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/api/v1/me")
    ResponseEntity<MeResponse> me(Caller caller, @RequestHeader(name = EtagSupport.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return EtagSupport.respond(queries.me(caller), ifNoneMatch);
    }

    @GetMapping("/api/v1/me/earnings")
    ResponseEntity<EarningsResponse> earnings(Caller caller, @RequestParam(name = "from", required = false) String from,
                                              @RequestParam(name = "to", required = false) String to,
                                              @RequestHeader(name = EtagSupport.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return EtagSupport.respond(queries.earnings(caller, from, to), ifNoneMatch);
    }

    @GetMapping("/api/v1/me/earnings/{ym}")
    ResponseEntity<MonthResponse> month(Caller caller, @PathVariable("ym") String ym,
                                        @RequestHeader(name = EtagSupport.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return EtagSupport.respond(queries.month(caller, ym), ifNoneMatch);
    }

    @GetMapping("/api/v1/me/earnings/{ym}/lines")
    ResponseEntity<LinesResponse> lines(Caller caller, @PathVariable("ym") String ym, @RequestParam(name = "page", defaultValue = "0") int page,
                                        @RequestParam(name = "size", defaultValue = "50") int size,
                                        @RequestParam(name = "commType", required = false) String commType,
                                        @RequestParam(name = "policyKey", required = false) String policyKey,
                                        @RequestHeader(name = EtagSupport.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return EtagSupport.respond(queries.lines(caller, ym, page, size, Optional.ofNullable(commType), Optional.ofNullable(policyKey)), ifNoneMatch);
    }

    @GetMapping("/api/v1/me/earnings/lines/{calcId}")
    ResponseEntity<LineDetailResponse> line(Caller caller, @PathVariable("calcId") String calcId,
                                            @RequestHeader(name = EtagSupport.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return EtagSupport.respond(queries.line(caller, calcId), ifNoneMatch);
    }

    @GetMapping("/api/v1/me/limits")
    ResponseEntity<LimitsResponse> limits(Caller caller, @RequestParam(name = "status", defaultValue = "ALL") String status,
                                          @RequestHeader(name = EtagSupport.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return EtagSupport.respond(queries.limits(caller, status), ifNoneMatch);
    }

    @GetMapping("/api/v1/me/limits/{ledgerId}")
    ResponseEntity<LimitDetailResponse> limit(Caller caller, @PathVariable("ledgerId") String ledgerId,
                                              @RequestHeader(name = EtagSupport.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return EtagSupport.respond(queries.limit(caller, ledgerId), ifNoneMatch);
    }

    @GetMapping("/api/v1/me/deferrals")
    ResponseEntity<DeferralsResponse> deferrals(Caller caller, @RequestParam(name = "from", required = false) String from,
                                                @RequestParam(name = "to", required = false) String to,
                                                @RequestHeader(name = EtagSupport.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return EtagSupport.respond(queries.deferrals(caller, from, to), ifNoneMatch);
    }
}
