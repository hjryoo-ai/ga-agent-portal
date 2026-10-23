package com.ga.portal.app.web;

import com.ga.portal.domain.access.Caller;
import com.ga.portal.sim.SimRequests;
import com.ga.portal.sim.SimulationUseCases;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * 시뮬레이션 API(포털 Phase 5, 계약 1.1.0) — 얇게: 호출자·{@code Content-Type}·본문 바이트를 진입점 하나에 넘기고 결과 바이트를 그대로 낸다. 결과는 요청마다 새로
 * 만들어지므로 ETag가 없고 {@code Cache-Control: no-store}. 쿠키가 없고(Bearer 머리만) 같은 출처라 CSRF 토큰을 두지 않는다(설계서 §9 v1.8).
 * 본문은 상한+1바이트까지만 읽는다 — 상한을 넘는 본문을 다 받아 놓고 거부하지 않는다(넘친 1바이트로 진입점이 400을 낸다).
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
final class SimulationController {

    private final SimulationUseCases simulations;

    SimulationController(SimulationUseCases simulations) {
        this.simulations = simulations;
    }

    @PostMapping("/api/v1/me/simulations/new-contract")
    ResponseEntity<byte[]> newContract(Caller caller, @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                       InputStream body) throws IOException {
        return ok(simulations.newContract(caller, Optional.ofNullable(contentType), bounded(body)));
    }

    @PostMapping("/api/v1/me/simulations/policy-event")
    ResponseEntity<byte[]> policyEvent(Caller caller, @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                       InputStream body) throws IOException {
        return ok(simulations.policyEvent(caller, Optional.ofNullable(contentType), bounded(body)));
    }

    static byte[] bounded(InputStream body) throws IOException {
        return body.readNBytes(SimRequests.MAX_BODY_BYTES + 1);
    }

    private static ResponseEntity<byte[]> ok(byte[] body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }
}
