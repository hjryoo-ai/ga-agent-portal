package com.ga.portal.ingest.feed;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.IngestErrorCode;

import java.util.Optional;

/**
 * 피드 호출 실패. 메시지에 서비스 토큰·응답 본문을 넣지 않는다(평문 금지 — J10). {@link #stopsWith()}가 비어 있으면 일시적 실패라
 * 커서·상태를 그대로 두고 다음 주기에 다시 한다(설계서 §6.1 — 네트워크 오류·5xx).
 */
public abstract sealed class FeedException extends RuntimeException {

    FeedException(String message) {
        super(message);
    }

    FeedException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 테넌트를 멈출 사유(일시적이면 비어 있다). */
    public abstract Optional<IngestErrorCode> stopsWith();

    /** 401 — 서비스 토큰이 없거나 틀렸다(공급자가 거부). */
    public static final class Unauthorized extends FeedException {
        public Unauthorized(TenantId tenant) {
            super("feed refused the service token of tenant " + tenant.value() + " (401)");
        }

        @Override
        public Optional<IngestErrorCode> stopsWith() {
            return Optional.of(IngestErrorCode.FEED_UNAUTHORIZED);
        }
    }

    /** 테넌트의 서비스 토큰이 없다(폴링하지 않는다). */
    public static final class TokenMissing extends FeedException {
        public TokenMissing(TenantId tenant) {
            super("no service token for tenant " + tenant.value());
        }

        @Override
        public Optional<IngestErrorCode> stopsWith() {
            return Optional.of(IngestErrorCode.TOKEN_MISSING);
        }
    }

    /** 422 {@code AFTER_BEYOND_HEAD} — afterSeq가 공급자의 headSeq보다 크다(복원된 DB 등 다른 발행자). */
    public static final class AfterBeyondHead extends FeedException {
        public AfterBeyondHead(long afterSeq) {
            super("afterSeq " + afterSeq + " is beyond the publisher's head (422 AFTER_BEYOND_HEAD)");
        }

        @Override
        public Optional<IngestErrorCode> stopsWith() {
            return Optional.of(IngestErrorCode.AFTER_BEYOND_HEAD);
        }
    }

    /** 응답의 tenantId가 요청한 테넌트가 아니다 — 아무것도 적용하지 않는다(C9). */
    public static final class TenantMismatch extends FeedException {
        public TenantMismatch(TenantId requested, String responded) {
            super("feed answered for tenant " + responded + ", requested " + requested.value());
        }

        @Override
        public Optional<IngestErrorCode> stopsWith() {
            return Optional.of(IngestErrorCode.TENANT_MISMATCH);
        }
    }

    /** 닿지 못했거나(연결·시간 초과·5xx) 응답이 계약 모양이 아니다 — 일시적 실패. */
    public static final class Unavailable extends FeedException {
        public Unavailable(String message) {
            super(message);
        }

        public Unavailable(String message, Throwable cause) {
            super(message, cause);
        }

        @Override
        public Optional<IngestErrorCode> stopsWith() {
            return Optional.empty();
        }
    }
}
