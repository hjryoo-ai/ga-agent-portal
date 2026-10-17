package com.ga.portal.app.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.IngestErrorCode;
import com.ga.portal.domain.IngestState;
import com.ga.portal.infra.persistence.AuditAccessRepository;
import com.ga.portal.infra.testing.SeedData;
import com.ga.portal.ingest.apply.IngestMetrics;
import com.ga.portal.projection.RecordingProjector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * C10(지시문 D-8): {@code resume}은 마지막 APPLIED seq보다 큰 n을 거부하고, 성공·거부·pause·status 모두 {@code audit_access}에 같은 트랜잭션으로
 * 남는다. 실제 PostgreSQL(portal_app·RLS).
 */
class IngestCliIT {

    private static final String ACTOR = "cli:ops-kim";

    private final IngestRig rig = new IngestRig();
    private final AuditAccessRepository audit = new AuditAccessRepository(rig.gateway);

    /** 명령 조립 — 주체 {@code cli:ops-kim}을 그 테넌트의 OPERATOR로 등록한다(Phase 3 D-9). */
    private IngestCommands commands(TenantId only) {
        IngestRig.operator(only, ACTOR);
        return unlinked(List.of(only));
    }

    private IngestCommands unlinked(List<TenantId> tenants) {
        return new IngestCommands(rig.transactions, rig.store, rig.lock, audit, () -> tenants,
                rig.rebuilder(IngestRig.feed("dup.jsonl"), new RecordingProjector(), 200, 50_000), IngestMetrics.NONE, IngestRig.CLOCK, rig.links);
    }

    private List<AuditAccessRepository.AuditRow> audits(TenantId t) {
        return rig.transactions.inTransaction(t, () -> audit.recent(50)).reversed();
    }

    @Test
    void c10ResumeIsBoundedByTheLastAppliedSeqAndEveryCommandIsAudited() {
        TenantId t = TenantId.of(SeedData.uniqueTenant("CLI"));
        IngestCommands cli = commands(t);

        assertThat(cli.status(Optional.of(t), ACTOR).lines()).containsExactly(t.value() + " no cursor");
        assertThat(cli.resume(t, 0, ACTOR).exitCode()).as("no cursor").isEqualTo(2);

        rig.worker(IngestRig.feed("gap.jsonl"), new RecordingProjector(), 200).runOnce(t);
        assertThat(rig.cursor(t).state()).isEqualTo(IngestState.FAILED);

        IngestCommands.Outcome beyond = cli.resume(t, 5, ACTOR);
        assertThat(beyond.exitCode()).isEqualTo(2);
        assertThat(beyond.lines().getFirst()).contains("beyond the last applied seq 4").contains("rebuild");
        assertThat(rig.cursor(t).state()).as("rejected resume changes nothing").isEqualTo(IngestState.FAILED);

        IngestCommands.Outcome ok = cli.resume(t, 2, ACTOR);
        assertThat(ok.exitCode()).isZero();
        assertThat(rig.cursor(t).state()).isEqualTo(IngestState.RUNNING);
        assertThat(rig.cursor(t).lastSeq()).isEqualTo(2);
        assertThat(rig.cursor(t).error()).isEmpty();

        assertThat(cli.resume(t, 2, ACTOR).exitCode()).as("resume while RUNNING").isEqualTo(2);
        assertThat(cli.pause(t, ACTOR).exitCode()).isZero();
        assertThat(rig.cursor(t).state()).isEqualTo(IngestState.PAUSED);
        assertThat(rig.cursor(t).errorCode()).contains(IngestErrorCode.OPERATOR_PAUSE);
        assertThat(cli.pause(t, ACTOR).exitCode()).as("pause while PAUSED").isEqualTo(2);
        assertThat(cli.status(Optional.empty(), ACTOR).lines()).singleElement().satisfies(l ->
                assertThat(l).startsWith(t.value() + " PAUSED last_seq=2 applied_max=4").contains("error=OPERATOR_PAUSE: operator"));

        assertThat(audits(t)).allSatisfy(a -> {
            assertThat(a.actorSubject()).isEqualTo(ACTOR);
            assertThat(a.at()).isEqualTo(IngestRig.CLOCK.instant());
        });
        assertThat(audits(t)).extracting(AuditAccessRepository.AuditRow::action, AuditAccessRepository.AuditRow::target).containsExactly(
                tuple(IngestCommands.STATUS, "cursor:" + t.value() + "@-"),
                tuple(IngestCommands.RESUME_REJECTED, "cursor:" + t.value() + "@0"),
                tuple(IngestCommands.RESUME_REJECTED, "cursor:" + t.value() + "@5"),
                tuple(IngestCommands.RESUME, "cursor:" + t.value() + "@2"),
                tuple(IngestCommands.RESUME_REJECTED, "cursor:" + t.value() + "@2"),
                tuple(IngestCommands.PAUSE, "cursor:" + t.value() + "@2"),
                tuple(IngestCommands.PAUSE_REJECTED, "cursor:" + t.value() + "@2"),
                tuple(IngestCommands.STATUS, "cursor:" + t.value() + "@2"));
    }

    /** 워커가 테넌트 잠금을 쥔 동안 pause·resume은 거부되고 아무것도 바꾸지 않는다(커서는 잠금을 쥔 뒤에만 읽는다). */
    @Test
    void commandsAreRefusedWhileAWorkerHoldsTheTenant() throws Exception {
        TenantId t = TenantId.of(SeedData.uniqueTenant("CLI_L"));
        rig.worker(IngestRig.feed("dup.jsonl"), new RecordingProjector(), 200).runOnce(t);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Boolean> worker = CompletableFuture.supplyAsync(() -> rig.transactions.inTransaction(t, () -> {
            boolean got = rig.lock.tryLock();
            held.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return got;
        }));
        assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            IngestCommands.Outcome paused = commands(t).pause(t, ACTOR);
            assertThat(paused.exitCode()).isEqualTo(2);
            assertThat(paused.lines().getFirst()).contains("being processed by a worker");
        } finally {
            release.countDown();
        }
        assertThat(worker.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(rig.cursor(t).state()).isEqualTo(IngestState.RUNNING);
        assertThat(audits(t)).extracting(AuditAccessRepository.AuditRow::action, AuditAccessRepository.AuditRow::target)
                .containsExactly(tuple(IngestCommands.PAUSE_REJECTED, "cursor:" + t.value() + "@-"));
    }

    @Test
    void aResumedTenantContinuesFromTheChosenSeq() {
        TenantId t = TenantId.of(SeedData.uniqueTenant("CLI_R"));
        RecordingProjector p = new RecordingProjector();
        rig.worker(IngestRig.feed("poison-schema.jsonl"), p, 200).runOnce(t);
        assertThat(rig.cursor(t).lastSeq()).isEqualTo(4);
        assertThat(commands(t).resume(t, 4, ACTOR).exitCode()).isZero();
        assertThat(rig.cursor(t).lastEventId()).as("kept when n is the cursor seq").isPresent();
        // 같은 독 스트림이면 같은 자리에서 다시 멈춘다 — 그 FAILED 행은 갱신된다
        rig.worker(IngestRig.feed("poison-schema.jsonl"), p, 200).runOnce(t);
        assertThat(rig.cursor(t).errorCode()).contains(IngestErrorCode.SCHEMA_VIOLATION);
        assertThat(rig.events(t, "FAILED")).singleElement().satisfies(f -> assertThat(f.seq()).isEqualTo(5));
        assertThat(p.applied(t)).hasSize(4);
    }

    // ------------------------------------------------------------------ Phase 3 C10: OS 사용자 → 그 테넌트의 OPERATOR 링크

    @Test
    void c10AnOsUserWithoutAnActiveOperatorLinkIsRefusedAndAudited() {
        TenantId t = TenantId.of(SeedData.uniqueTenant("CLIAUTH"));
        IngestRig.link(t, "cli:ops-revoked", "OPERATOR", "REVOKED");
        IngestRig.link(t, "cli:ops-agent", "AGENT", "ACTIVE");
        TenantId other = TenantId.of(SeedData.uniqueTenant("CLIAUTH_O"));
        IngestRig.operator(other, "cli:ops-elsewhere");
        IngestCommands cli = unlinked(List.of(t));
        rig.worker(IngestRig.feed("gap.jsonl"), new RecordingProjector(), 200).runOnce(t);
        IngestState before = rig.cursor(t).state();

        for (String subject : List.of("cli:nobody", "cli:ops-revoked", "cli:ops-agent", "cli:ops-elsewhere")) {
            assertThat(cli.status(Optional.of(t), subject)).as(subject).isEqualTo(new IngestCommands.Outcome(2, List.of(t.value() + " not authorized")));
            assertThat(cli.resume(t, 2, subject).lines()).as(subject).containsExactly(t.value() + " not authorized");
            assertThat(cli.pause(t, subject).exitCode()).as(subject).isEqualTo(2);
            assertThat(cli.rebuild(t, subject).exitCode()).as(subject).isEqualTo(2);
        }
        assertThat(rig.cursor(t).state()).as("nothing ran").isEqualTo(before);
        assertThat(audits(t)).extracting(AuditAccessRepository.AuditRow::actorSubject, AuditAccessRepository.AuditRow::action,
                AuditAccessRepository.AuditRow::target, AuditAccessRepository.AuditRow::detail).containsExactly(
                tuple("cli:nobody", "AUTHZ_DENIED", "cli:ingest status", "NO_LINK"),
                tuple("cli:nobody", "AUTHZ_DENIED", "cli:ingest resume", "NO_LINK"),
                tuple("cli:nobody", "AUTHZ_DENIED", "cli:ingest pause", "NO_LINK"),
                tuple("cli:nobody", "AUTHZ_DENIED", "cli:ingest rebuild", "NO_LINK"),
                tuple("cli:ops-revoked", "AUTHZ_DENIED", "cli:ingest status", "REVOKED"),
                tuple("cli:ops-revoked", "AUTHZ_DENIED", "cli:ingest resume", "REVOKED"),
                tuple("cli:ops-revoked", "AUTHZ_DENIED", "cli:ingest pause", "REVOKED"),
                tuple("cli:ops-revoked", "AUTHZ_DENIED", "cli:ingest rebuild", "REVOKED"),
                tuple("cli:ops-agent", "AUTHZ_DENIED", "cli:ingest status", "ROLE"),
                tuple("cli:ops-agent", "AUTHZ_DENIED", "cli:ingest resume", "ROLE"),
                tuple("cli:ops-agent", "AUTHZ_DENIED", "cli:ingest pause", "ROLE"),
                tuple("cli:ops-agent", "AUTHZ_DENIED", "cli:ingest rebuild", "ROLE"),
                tuple("cli:ops-elsewhere", "AUTHZ_DENIED", "cli:ingest status", "NO_LINK"),
                tuple("cli:ops-elsewhere", "AUTHZ_DENIED", "cli:ingest resume", "NO_LINK"),
                tuple("cli:ops-elsewhere", "AUTHZ_DENIED", "cli:ingest pause", "NO_LINK"),
                tuple("cli:ops-elsewhere", "AUTHZ_DENIED", "cli:ingest rebuild", "NO_LINK"));
    }

    @Test
    void c10StatusOverSeveralTenantsReportsEachAndExitsTwoIfAnyIsRefused() {
        TenantId mine = TenantId.of(SeedData.uniqueTenant("CLIST_A"));
        TenantId notMine = TenantId.of(SeedData.uniqueTenant("CLIST_B"));
        IngestRig.operator(mine, "cli:ops-lee");
        IngestCommands cli = unlinked(List.of(mine, notMine));
        IngestCommands.Outcome all = cli.status(Optional.empty(), "cli:ops-lee");
        assertThat(all.exitCode()).isEqualTo(2);
        assertThat(all.lines()).containsExactly(mine.value() + " no cursor", notMine.value() + " not authorized");
        assertThat(audits(mine)).extracting(AuditAccessRepository.AuditRow::action).containsExactly(IngestCommands.STATUS);
        assertThat(audits(notMine)).extracting(AuditAccessRepository.AuditRow::action, AuditAccessRepository.AuditRow::detail)
                .containsExactly(tuple("AUTHZ_DENIED", "NO_LINK"));
        IngestRig.operator(notMine, "cli:ops-lee");
        assertThat(cli.status(Optional.empty(), "cli:ops-lee").exitCode()).as("registered in both").isZero();
    }
}
