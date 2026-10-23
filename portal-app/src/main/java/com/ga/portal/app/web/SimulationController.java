package com.ga.portal.app.web;

import com.ga.portal.domain.access.Caller;
import com.ga.portal.sim.SimulationUseCases;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 시뮬레이션 API(포털 Phase 5, 계약 1.1.0) — 얇게: 호출자·{@code Content-Type}·본문 바이트를 진입점 하나에 넘기고 결과 바이트를 그대로 낸다. 결과는 요청마다 새로
 * 만들어지므로 ETag가 없고 {@code Cache-Control: no-store}. 쿠키가 없고(Bearer 머리만) 같은 출처라 CSRF 토큰을 두지 않는다(설계서 §9 v1.8).
 */
@RestController
final class SimulationController {

    private final SimulationUseCases simulations;

    SimulationController(SimulationUseCases simulations) {
        this.simulations = simulations;
    }

    @PostMapping("/api/v1/me/simulations/new-contract")
    ResponseEntity<byte[]> newContract(Caller caller, @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                       @RequestBody(required = false) byte[] body) {
        return ok(simulations.newContract(caller, Optional.ofNullable(contentType), body == null ? new byte[0] : body));
    }

    @PostMapping("/api/v1/me/simulations/policy-event")
    ResponseEntity<byte[]> policyEvent(Caller caller, @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                       @RequestBody(required = false) byte[] body) {
        return ok(simulations.policyEvent(caller, Optional.ofNullable(contentType), body == null ? new byte[0] : body));
    }

    private static ResponseEntity<byte[]> ok(byte[] body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }
}
