package com.ga.portal.app.recon;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.app.cli.OperatorAuthorization;
import com.ga.portal.app.ingest.IngestCommands;
import com.ga.portal.domain.CursorRecord;
import com.ga.portal.domain.ReconRunResult;
import com.ga.portal.domain.ReconTrigger;
import com.ga.portal.infra.persistence.AuditAccessRepository;
import com.ga.portal.infra.persistence.IdentityLinkRepository;
import com.ga.portal.infra.persistence.IngestCursorRepository;
import com.ga.portal.infra.persistence.InquiryOperatorRepository;
import com.ga.portal.infra.persistence.ReconResultRepository;
import com.ga.portal.infra.persistence.ReconRunRepository;
import com.ga.portal.ingest.apply.TenantTransactions;
import com.ga.portal.recon.Episodes;
import com.ga.portal.recon.RunRecord;
import com.ga.portal.recon.ReconRunner;
import com.ga.portal.recon.port.ReconEngine;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 운영 명령 둘(포털 Phase 7 — 설계서 §6.6·§10, 계획 Q7·Q12·⑤): 인가는 다른 명령과 같다(그 테넌트의 {@code OPERATOR} 링크 — {@link OperatorAuthorization},
 * 거부는 {@code AUTHZ_DENIED}로 남는다).
 * <ul>
 *   <li>{@code recon run --tenant=T [--ym=YYYYMM,…]} — 최대 6개, 없으면 창. 감사 {@code RECON_RUN}(target {@code recon:<T>}). 출력은 회차 수·결과 한 줄
 *       (설계사·달 없음). 종료 0(회차 끝 — DRIFT·판정 불가도), 1(재구축 실패), 2(거부·다른 회차가 도는 중).</li>
 *   <li>{@code status --tenant=T|--all} — 테넌트마다 한 줄: 커서 상태·{@code last_seq}·lag(seq·초)·{@code caught_up_at}·미해결 DRIFT 수·가장 오래된 미해결의
 *       나이(일)·마지막 회차 시각·결과·마지막 재구축 결과·열린 문의 수·가장 오래된 열린 문의의 나이(시간). 설계사·달·증권번호·금액 없음 — 미해결 달은 나이만
 *       (승인 조건 B). 시각은 초까지. 감사 {@code STATUS}(target {@code cli:status}). 하나라도 거부면 종료 2(Phase 3 Q10).</li>
 * </ul>
 */
public final class ReconCommands {

    public static final String RUN = "RECON_RUN";
    public static final String STATUS = "STATUS";
    public static final int MAX_MONTHS = 6;
    private static final Pattern YM = Pattern.compile("^[0-9]{4}(0[1-9]|1[0-2])$");

    private final TenantTransactions transactions;
    private final OperatorAuthorization authorization;
    private final AuditAccessRepository audit;
    private final ReconService service;
    private final ReconEngine engine;
    private final IngestCursorRepository cursors;
    private final ReconResultRepository results;
    private final ReconRunRepository runs;
    private final InquiryOperatorRepository inquiries;
    private final Supplier<List<TenantId>> directory;
    private final Clock clock;

    public ReconCommands(TenantTransactions transactions, IdentityLinkRepository links, AuditAccessRepository audit, ReconService service,
                         ReconEngine engine, IngestCursorRepository cursors, ReconResultRepository results, ReconRunRepository runs,
                         InquiryOperatorRepository inquiries, Supplier<List<TenantId>> directory, Clock clock) {
        this.transactions = transactions;
        this.authorization = new OperatorAuthorization(links, audit, clock);
        this.audit = audit;
        this.service = service;
        this.engine = engine;
        this.cursors = cursors;
        this.results = results;
        this.runs = runs;
        this.inquiries = inquiries;
        this.directory = directory;
        this.clock = clock;
    }

    /** {@code --ym} 값 → 달 목록(쉼표, 중복 없음, 1~6개, yyyyMM). 형식 밖이면 {@link IllegalArgumentException}(사용법 64). */
    public static List<String> months(Optional<String> option) {
        if (option.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String ym : option.get().split(",", -1)) {
            if (!YM.matcher(ym).matches() || out.contains(ym)) {
                throw new IllegalArgumentException("--ym");
            }
            out.add(ym);
        }
        if (out.isEmpty() || out.size() > MAX_MONTHS) {
            throw new IllegalArgumentException("--ym");
        }
        return List.copyOf(out);
    }

    public IngestCommands.Outcome run(TenantId tenant, List<String> months, String actorSubject) {
        boolean allowed = transactions.inTransaction(tenant, () -> {
            if (authorization.deny(actorSubject, "recon run").isPresent()) {
                return false;
            }
            audit.record(actorSubject, RUN, "recon:" + tenant.value(), clock.instant());
            return true;
        });
        if (!allowed) {
            return new IngestCommands.Outcome(2, List.of(tenant.value() + " not authorized"));
        }
        ReconRunner.Outcome outcome = service.run(tenant, ReconTrigger.CLI, actorSubject, months.isEmpty() ? service.window() : months);
        return switch (outcome) {
            case ReconRunner.Outcome.Locked l -> new IngestCommands.Outcome(2, List.of(tenant.value() + " another recon run is in progress — try again"));
            case ReconRunner.Outcome.Done d -> new IngestCommands.Outcome(d.run().result() == ReconRunResult.FAILED ? 1 : 0, List.of(line(tenant, d.run())));
        };
    }

    static String line(TenantId tenant, RunRecord r) {
        return tenant.value() + " recon " + r.result() + " checked=" + r.checked() + " matched=" + r.matched() + " drift_new=" + r.driftNew()
                + " drift_unresolved=" + r.driftUnresolved() + " inconclusive=" + r.inconclusive() + " engine_calls=" + r.engineCalls()
                + r.rebuildOutcome().map(o -> " rebuild=" + o).orElse("");
    }

    public IngestCommands.Outcome status(Optional<TenantId> tenant, String actorSubject) {
        List<TenantId> tenants = tenant.map(List::of).orElseGet(directory);
        List<String> lines = new ArrayList<>();
        boolean denied = false;
        for (TenantId t : tenants) {
            Optional<StatusFacts> facts = transactions.inTransaction(t, () -> {
                if (authorization.deny(actorSubject, "status").isPresent()) {
                    return Optional.<StatusFacts>empty();
                }
                audit.record(actorSubject, STATUS, "cli:status", clock.instant());
                Map<?, Episodes.Episode> unresolved = Episodes.unresolved(results.driftAndRepaired(), runs.rebuilds());
                Optional<Instant> oldest = unresolved.values().stream().map(Episodes.Episode::firstDriftAt).min(Instant::compareTo);
                return Optional.of(new StatusFacts(cursors.find(), cursors.caughtUpAt(), unresolved.size(), oldest, runs.last(), runs.lastRebuild(),
                        inquiries.openSummary()));
            });
            denied |= facts.isEmpty();
            lines.add(facts.map(f -> statusLine(t, f, f.cursor().map(c -> engine.feedHead(t, c.lastSeq())).orElse(OptionalLong.empty()), clock.instant()))
                    .orElse(t.value() + " not authorized"));
        }
        return new IngestCommands.Outcome(denied ? 2 : 0, List.copyOf(lines));
    }

    /** {@code status} 한 줄의 재료 — 수·상태·시각만(설계사·달·금액 없음). */
    public record StatusFacts(Optional<CursorRecord> cursor, Optional<Instant> caughtUpAt, int unresolved, Optional<Instant> oldestDrift,
                              Optional<ReconRunRepository.Run> lastRun, Optional<ReconRunRepository.Run> lastRebuild,
                              InquiryOperatorRepository.OpenSummary inquiries) {
    }

    /** {@code status} 한 줄(승인 조건 B — 달은 나이로만, 시각은 초까지). {@code head}는 피드 머리(못 읽으면 비어 있다 — {@code lag_seq=-}). */
    public static String statusLine(TenantId t, StatusFacts f, OptionalLong head, Instant now) {
        StringBuilder b = new StringBuilder(t.value());
        if (f.cursor().isPresent()) {
            CursorRecord c = f.cursor().get();
            b.append(" cursor=").append(c.state()).append(" last_seq=").append(c.lastSeq())
                    .append(" lag_seq=").append(head.isPresent() ? String.valueOf(Math.max(0, head.getAsLong() - c.lastSeq())) : "-");
        } else {
            b.append(" cursor=- last_seq=- lag_seq=-");
        }
        b.append(" caught_up_at=").append(f.caughtUpAt().map(ReconCommands::seconds).orElse("-"))
                .append(" lag_s=").append(f.caughtUpAt().map(a -> String.valueOf(Duration.between(a, now).toSeconds())).orElse("-"))
                .append(" drift_unresolved=").append(f.unresolved())
                .append(" drift_oldest_days=").append(f.oldestDrift().map(a -> String.valueOf(Duration.between(a, now).toDays())).orElse("-"))
                .append(" last_run=").append(f.lastRun().map(r -> seconds(r.runAt()) + "/" + r.result()).orElse("-"))
                .append(" last_rebuild=").append(f.lastRebuild().flatMap(ReconRunRepository.Run::rebuildOutcome).map(Enum::name).orElse("-"))
                .append(" inquiries_open=").append(f.inquiries().count())
                .append(" inquiry_oldest_hours=").append(f.inquiries().oldest().map(a -> String.valueOf(Duration.between(a, now).toHours())).orElse("-"));
        return b.toString();
    }

    private static String seconds(Instant at) {
        return at.truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
