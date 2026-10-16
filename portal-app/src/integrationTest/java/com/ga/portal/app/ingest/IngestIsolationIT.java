package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.IngestErrorCode;
import com.ga.portal.domain.IngestState;
import com.ga.portal.infra.testing.SeedData;
import com.ga.portal.ingest.apply.IngestWorker;
import com.ga.portal.ingest.apply.PollSchedule;
import com.ga.portal.ingest.feed.FileFeedAdapter;
import com.ga.portal.projection.RecordingProjector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C5 테넌트 격리: 같은 틱에서 T1은 독 이벤트로 FAILED, T2는 정상 전진. T1이 멈춘 뒤의 틱에서도 T2는 새 이벤트를 계속 적용하고 T1은 폴링되지 않는다.
 * 스케줄러(앱)·워커·실제 PostgreSQL. 테넌트 목록은 고정(이 시험의 두 테넌트 — 전용 롤 읽기는 TenantDirectoryIT).
 */
class IngestIsolationIT {

    @TempDir
    Path dir;

    @Test
    void c5APoisonedTenantDoesNotStopTheOtherTenant() throws IOException {
        IngestRig rig = new IngestRig();
        TenantId t1 = TenantId.of(SeedData.uniqueTenant("ISO_1"));
        TenantId t2 = TenantId.of(SeedData.uniqueTenant("ISO_2"));
        IngestRig.seedTenant(t1, "https://engine-t1.invalid", "{}");
        IngestRig.seedTenant(t2, "https://engine-t2.invalid", "{}");

        // T2의 피드는 처음엔 1~9만, 두 번째 틱 전에 1~17로 늘어난다(엔진이 계속 발행하는 것처럼)
        Path t2Feed = dir.resolve("t2.jsonl");
        writeFirstLines(IngestRig.streamFor(t2, "a1-golden.jsonl"), t2Feed, 1 + 9);
        Map<TenantId, Path> feeds = Map.of(t1, IngestRig.streamFor(t1, "poison-schema.jsonl"), t2, t2Feed);
        RecordingProjector p = new RecordingProjector();
        IngestWorker worker = rig.worker(new FileFeedAdapter(feeds::get), p, 200);
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-17T03:00:00Z"));
        Clock ticking = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        IngestScheduler scheduler = new IngestScheduler(() -> List.of(t1, t2), rig.transactions, rig.tenants, worker,
                new PollSchedule(Duration.ofSeconds(10)), Duration.ofSeconds(10), ticking);

        List<IngestWorker.Round> firstTick = scheduler.tick();
        assertThat(firstTick).hasSize(2);
        assertThat(rig.cursor(t1).state()).isEqualTo(IngestState.FAILED);
        assertThat(rig.cursor(t1).errorCode()).contains(IngestErrorCode.SCHEMA_VIOLATION);
        assertThat(rig.cursor(t2).state()).isEqualTo(IngestState.RUNNING);
        assertThat(rig.cursor(t2).lastSeq()).isEqualTo(9);

        Files.copy(IngestRig.streamFor(t2, "a1-golden.jsonl"), t2Feed, StandardCopyOption.REPLACE_EXISTING);
        now.set(now.get().plusSeconds(10));
        List<IngestWorker.Round> secondTick = scheduler.tick();
        assertThat(secondTick).extracting(IngestWorker.Round::tenant).containsExactly(t1, t2);
        assertThat(secondTick.getFirst().polled()).as("FAILED T1 is not polled").isFalse();
        assertThat(rig.cursor(t2).lastSeq()).isEqualTo(17);
        assertThat(p.applied(t2)).hasSize(17);
        assertThat(p.applied(t1)).hasSize(4);
        assertThat(rig.cursor(t1).lastSeq()).isEqualTo(4);
    }

    private static void writeFirstLines(Path from, Path to, int lines) throws IOException {
        List<String> all = Files.readAllLines(from, StandardCharsets.UTF_8);
        Files.write(to, all.subList(0, lines), StandardCharsets.UTF_8);
    }
}
