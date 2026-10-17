package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.IngestState;
import com.ga.portal.infra.testing.SeedData;
import com.ga.portal.projection.RecordingProjector;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 계획 승인 Q3(DB 쪽): {@code caught_up_at}을 쓰는 문장이 커서 {@code RUNNING}·{@code last_seq = headSeq}를 문장 안에서 본다 —
 * 적용 트랜잭션과 같은 행이라 행 잠금이 순서를 정한다. 유휴 회차는 {@code updated_at}을 바꾸지 않고 {@code caught_up_at}만 바꾼다.
 */
class CaughtUpIT {

    private final IngestRig rig = new IngestRig();

    @Test
    void theStatementChecksTheStateAndTheSeqItself() {
        TenantId t = TenantId.of(SeedData.uniqueTenant("CAUGHT"));
        rig.worker(IngestRig.feed("a1-golden.jsonl"), new RecordingProjector(), 200).runOnce(t);
        assertThat(rig.transactions.inTransaction(t, rig.store::caughtUpAt)).contains(IngestRig.CLOCK.instant());
        var updatedAt = rig.cursor(t).updatedAt();

        assertThat(rig.transactions.inTransaction(t, () -> rig.store.markCaughtUp(16, IngestRig.CLOCK.instant()))).as("behind the head").isFalse();
        rig.transactions.inTransaction(t, () -> {
            var c = rig.store.cursor().orElseThrow();
            rig.store.saveCursor(c.lastSeq(), c.lastEventId(), IngestState.PAUSED, Optional.empty(), c.updatedAt());
            return null;
        });
        assertThat(rig.transactions.inTransaction(t, () -> rig.store.markCaughtUp(17, IngestRig.CLOCK.instant()))).as("paused").isFalse();
        assertThat(rig.cursor(t).updatedAt()).isEqualTo(updatedAt);
    }
}
