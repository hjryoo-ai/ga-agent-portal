package com.ga.portal.ingest;

import com.ga.portal.domain.IngestErrorCode;
import com.ga.portal.projection.Envelope;

import java.util.Optional;

/**
 * 받은 이벤트 하나의 수신 검증 결과(설계서 §6.1 독 이벤트 분류 1~4). 키(eventId·seq)를 믿을 수 있는지가 기록 여부를 가른다.
 */
public sealed interface EnvelopeVerdict {

    /** 스키마를 전부 통과했다. */
    record Valid(Envelope envelope) implements EnvelopeVerdict {
    }

    /** 멈춰야 한다. {@code envelope}은 핵심 Envelope를 통과했을 때만 있다(그때만 행을 남길 수 있다). */
    record Rejected(IngestErrorCode code, String detail, Optional<Envelope> envelope) implements EnvelopeVerdict {
    }
}
