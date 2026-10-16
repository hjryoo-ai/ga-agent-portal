package com.ga.portal.ingest.apply;

import com.ga.portal.domain.CursorRecord;
import com.ga.portal.domain.IngestEventRecord;
import com.ga.portal.domain.IngestState;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 인제스트 저장 포트(설계서 §5 {@code ingest_cursor}·{@code ingest_event}). 모든 메서드는 {@link TenantTransactions}가 연 트랜잭션 안에서,
 * 바인딩된 테넌트({@code TenantContext})에 대해 불린다 — 테넌트 인자가 없다. 구현: {@code portal-app}의 어댑터(인프라 저장소 위임),
 * 시험 픽스처의 인메모리.
 */
public interface IngestStore {

    Optional<CursorRecord> cursor();

    /** 커서가 없으면 {@code RUNNING}·{@code last_seq 0}으로 만든다(두 워커가 동시에 불러도 하나). 있으면 그대로 돌려준다. */
    CursorRecord ensureCursor(Instant now);

    void saveCursor(long lastSeq, Optional<String> lastEventId, IngestState state, Optional<String> error, Instant now);

    Optional<IngestEventRecord> event(String eventId);

    /** 새 행을 넣거나 같은 eventId의 {@code FAILED} 행을 {@code APPLIED}로 바꾼다. */
    void markApplied(IngestEventRecord record);

    /** 새 {@code FAILED} 행을 넣거나 기존 {@code FAILED} 행을 갱신한다. {@code APPLIED} 행은 건드리지 않는다. */
    void markFailed(IngestEventRecord record);

    /** 마지막 {@code APPLIED} seq(운영 명령 {@code resume}의 상한). */
    OptionalLong maxAppliedSeq();
}
