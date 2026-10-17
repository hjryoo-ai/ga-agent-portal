package com.ga.portal.ingest.apply;

import com.ga.platform.canonical.CanonicalizationException;
import com.ga.portal.domain.IngestErrorCode;
import com.ga.portal.domain.IngestEventRecord;
import com.ga.portal.domain.IngestEventStatus;
import com.ga.portal.ingest.EnvelopeValidator;
import com.ga.portal.ingest.EnvelopeVerdict;
import com.ga.portal.ingest.PayloadHasher;
import com.ga.portal.projection.Envelope;
import tools.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * 이벤트 하나의 수신 판정(설계서 §6.1 독 이벤트 분류 표의 순서) — {@link IngestApplier}와 {@link Rebuilder}가 같은 규칙을 쓴다(Phase 2 계획 ⑥):
 * 수신 검증 → 해시 → 기존 {@code APPLIED} 행 대조(해시·seq) → seq 규칙. 쓰기는 하지 않는다 — 호출자가 판정을 보고 쓴다.
 */
final class EventCheck {

    /** 판정. */
    sealed interface Verdict {
        /** 처음 보는(또는 {@code FAILED}만 있는) 이벤트, seq = 기대값. */
        record Fresh(Envelope envelope, String hash) implements Verdict {
        }

        /** 이미 {@code APPLIED}로 같은 해시·seq — seq 규칙 전에 판정한다(적용기는 건너뛰고, 재생은 다시 투영한다). */
        record Known(Envelope envelope, String hash) implements Verdict {
        }

        /** 독. {@code recordable}이 있으면 키를 믿을 수 있어 {@code FAILED} 행을 남길 수 있다. */
        record Poison(IngestErrorCode code, String detail, Optional<Recordable> recordable) implements Verdict {
        }
    }

    /** {@code FAILED} 행을 지을 재료. */
    record Recordable(Envelope envelope, String hash) {
    }

    private EventCheck() {
    }

    static Verdict check(EnvelopeValidator validator, IngestStore store, JsonNode event, long expected) {
        EnvelopeVerdict verdict = validator.validate(event);
        if (verdict instanceof EnvelopeVerdict.Rejected r) {
            Optional<Recordable> row = r.code() == IngestErrorCode.UNSUPPORTED_VERSION
                    ? Optional.empty()
                    : r.envelope().flatMap(e -> hashOf(event).map(h -> new Recordable(e, h)));
            return new Verdict.Poison(r.code(), r.detail(), row);
        }
        Envelope envelope = ((EnvelopeVerdict.Valid) verdict).envelope();
        String keys = keys(envelope);
        Optional<String> hashed = hashOf(event);
        if (hashed.isEmpty()) {
            return new Verdict.Poison(IngestErrorCode.SCHEMA_VIOLATION, keys + " not canonicalizable", Optional.empty());
        }
        String hash = hashed.get();

        Optional<IngestEventRecord> existing = store.event(envelope.eventId());
        if (existing.isPresent() && existing.get().status() == IngestEventStatus.APPLIED) {
            IngestEventRecord known = existing.get();
            if (!known.payloadHash().equals(hash)) {
                return new Verdict.Poison(IngestErrorCode.HASH_MISMATCH, keys + " differs from the applied event", Optional.empty());
            }
            if (known.seq() != envelope.seq()) {
                return new Verdict.Poison(IngestErrorCode.EVENT_SEQ_CONFLICT, keys + " was applied at seq " + known.seq(), Optional.empty());
            }
            return new Verdict.Known(envelope, hash);
        }
        if (envelope.seq() < expected) {
            return new Verdict.Poison(IngestErrorCode.PAST_SEQ_NEW_EVENT, keys + " is new but expected seq " + expected,
                    Optional.of(new Recordable(envelope, hash)));
        }
        if (envelope.seq() > expected) {
            return new Verdict.Poison(IngestErrorCode.SEQ_GAP, "expected seq " + expected + ", got " + keys, Optional.empty());
        }
        return new Verdict.Fresh(envelope, hash);
    }

    static String keys(Envelope e) {
        return "seq " + e.seq() + " eventId " + e.eventId();
    }

    private static Optional<String> hashOf(JsonNode event) {
        try {
            return Optional.of(PayloadHasher.hash(event));
        } catch (CanonicalizationException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
