package com.ga.portal.ingest;

import com.ga.portal.domain.IngestErrorCode;
import com.ga.portal.projection.Envelope;
import com.networknt.schema.Error;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 수신 검증(설계서 §6.1 독 이벤트 분류 1~4, 판정 순서 그대로): ① 핵심 Envelope(ga-disclosure 공유 사본) ② version이 지원 최대보다
 * 크면 멈춤(PAUSED) ③ type ↔ aggregate.kind 짝 ④ 포털 Envelope·payload 스키마. 포털 {@code EventSchemaTest}와 같은 스키마·같은 IRI 해석.
 *
 * <p>detail에는 JSON 위치와 스키마 키워드만 쓴다 — 검증기 메시지는 값을 담을 수 있어 쓰지 않는다(평문 금지).
 */
public final class EnvelopeValidator {

    private final ContractSchemas schemas;

    public EnvelopeValidator(ContractSchemas schemas) {
        this.schemas = schemas;
    }

    public EnvelopeVerdict validate(JsonNode node) {
        List<Error> core = schemas.core().validate(node);
        if (!core.isEmpty()) {
            return new EnvelopeVerdict.Rejected(IngestErrorCode.SCHEMA_VIOLATION, "envelope core " + describe(core), Optional.empty());
        }
        Envelope envelope = EnvelopeReader.read(node);
        if (envelope.version() > schemas.maxVersion()) {
            return new EnvelopeVerdict.Rejected(IngestErrorCode.UNSUPPORTED_VERSION,
                    keys(envelope) + " version " + envelope.version() + " > " + schemas.maxVersion(), Optional.empty());
        }
        String expectedKind = schemas.kindByType().get(envelope.type());
        if (expectedKind != null && !expectedKind.equals(envelope.aggregate().kind())) {
            return new EnvelopeVerdict.Rejected(IngestErrorCode.TYPE_KIND_MISMATCH,
                    keys(envelope) + " type " + envelope.type() + " requires kind " + expectedKind + ", got " + envelope.aggregate().kind(),
                    Optional.of(envelope));
        }
        List<Error> portal = schemas.envelope().validate(node);
        if (!portal.isEmpty()) {
            return new EnvelopeVerdict.Rejected(IngestErrorCode.SCHEMA_VIOLATION, keys(envelope) + " " + describe(portal), Optional.of(envelope));
        }
        return new EnvelopeVerdict.Valid(envelope);
    }

    static String keys(Envelope e) {
        return "seq " + e.seq() + " eventId " + e.eventId();
    }

    /** 위치·키워드만(값 없음), 최대 5개. */
    static String describe(List<Error> errors) {
        String shown = errors.stream().limit(5)
                .map(e -> e.getInstanceLocation() + " " + e.getKeyword())
                .collect(Collectors.joining(", "));
        return "[" + shown + (errors.size() > 5 ? ", +" + (errors.size() - 5) : "") + "]";
    }
}
