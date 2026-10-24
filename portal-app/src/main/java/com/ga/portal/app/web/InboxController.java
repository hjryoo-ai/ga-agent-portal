package com.ga.portal.app.web;

import com.ga.portal.domain.access.Caller;
import com.ga.portal.inbox.InboxUseCases;
import com.ga.portal.inbox.InquiryRequests;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * 알림함·이의제기 API(포털 Phase 6, 계약 1.2.0) — 얇게: 호출자와 입력을 진입점 하나에 넘긴다. GET 셋은 본문 ETag({@link BodyEtags})·{@code private, no-cache},
 * 쓰기 둘은 {@code no-store}. 쿠키가 없고(Bearer 머리만) 같은 출처라 CSRF 토큰·{@code Origin} 검사를 두지 않는다(설계서 §9 v1.9). 문의 본문은 상한+1바이트까지만
 * 읽는다(넘친 1바이트로 진입점이 400을 낸다).
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
final class InboxController {

    private final InboxUseCases inbox;
    private final JsonMapper json;

    InboxController(InboxUseCases inbox, JsonMapper json) {
        this.inbox = inbox;
        this.json = json;
    }

    @GetMapping("/api/v1/me/notifications")
    ResponseEntity<byte[]> notifications(Caller caller, @RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly,
                                         @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "20") int size,
                                         @RequestHeader(name = EtagSupport.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return BodyEtags.respond(json, inbox.notifications(caller, unreadOnly, page, size), ifNoneMatch);
    }

    @PatchMapping("/api/v1/me/notifications/{id}/read")
    ResponseEntity<byte[]> markRead(Caller caller, @PathVariable("id") String id) {
        return BodyEtags.noStore(json, HttpStatus.OK, inbox.markRead(caller, id));
    }

    @GetMapping("/api/v1/me/inquiries")
    ResponseEntity<byte[]> inquiries(Caller caller, @RequestParam(name = "status", defaultValue = "ALL") String status,
                                     @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "20") int size,
                                     @RequestHeader(name = EtagSupport.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return BodyEtags.respond(json, inbox.inquiries(caller, status, page, size), ifNoneMatch);
    }

    @PostMapping("/api/v1/me/inquiries")
    ResponseEntity<byte[]> createInquiry(Caller caller, @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                  InputStream body) throws IOException {
        return BodyEtags.noStore(json, HttpStatus.CREATED,
                inbox.createInquiry(caller, Optional.ofNullable(contentType), body.readNBytes(InquiryRequests.MAX_BODY_BYTES + 1)));
    }

    @GetMapping("/api/v1/me/inquiries/{id}")
    ResponseEntity<byte[]> inquiry(Caller caller, @PathVariable("id") String id,
                                   @RequestHeader(name = EtagSupport.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return BodyEtags.respond(json, inbox.inquiry(caller, id), ifNoneMatch);
    }
}
