package com.ga.portal.projection.notify;

import com.ga.platform.core.testing.SeededCases;
import com.ga.portal.domain.DataStatus;
import com.ga.portal.domain.DeferralStatus;
import com.ga.portal.domain.MonthStatus;
import com.ga.portal.domain.NotificationType;
import com.ga.portal.domain.Severity;
import com.ga.portal.domain.notify.NotificationParams;
import com.ga.portal.domain.rule.LimitStage;
import com.ga.portal.domain.view.DeferralRow;
import com.ga.portal.domain.view.LedgerRow;
import com.ga.portal.domain.view.LineRow;
import com.ga.portal.domain.view.MonthRow;
import com.ga.portal.projection.Change;
import com.ga.portal.projection.Envelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.node.JsonNodeFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C2·C5(포털 Phase 6 계획 ③): 파생 규칙 표의 칸마다 맞는 경우·안 맞는 경우, 한도 단계 OK·WARN·CRIT·FULL 16쌍 전수(오를 때만 후 단계 하나), 상향 → 하향 → 재상향은
 * 키 둘, 시드 고정 생성기로 만든 임의 전기열에서 "후보 수 = 상향 횟수"(단계는 규칙과 다른 식 — 경계 금액을 먼저 구해 비교하는 신탁 — 으로 센다). 모든 후보의
 * {@code params}·키·경로에 증권번호 원문이 없다.
 */
class NotificationRulesTest {

    static final Thresholds T = new Thresholds(8_000, 9_500);
    static final String POLICY = "POL-RULES-77123";
    /** 끝 4자만(PolicyMask). */
    static final String MASKED = "***********7123";
    static final long LIMIT = 1_000_000;
    static final List<Candidate> SEEN = new ArrayList<>();

    static Envelope env(String type, long seq, String eventId) {
        return new Envelope(eventId, seq, type, 1, "2026-10-20T00:00:00Z", new Envelope.Aggregate("X", "x"), JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.objectNode());
    }

    static Envelope env(String type, long seq) {
        return env(type, seq, "00000000-0000-4000-8000-" + String.format("%012d", seq));
    }

    static LedgerRow ledger(long limit, long accum) {
        return new LedgerRow("L-1", "A-1", POLICY, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-01"), LocalDate.parse("2027-07-31"), 50_000,
                "20", limit, accum, 0, 0, 1, "LIMIT_RULE:1");
    }

    static LineRow line(String commType, long amount, long cut) {
        return new LineRow("C-9", "A-1", POLICY, Optional.empty(), commType, amount, "202610", Optional.empty(), cut,
                cut > 0 ? Optional.of("CUT") : Optional.empty(), "{}", "{}", 9);
    }

    static DeferralRow deferral(DeferralStatus status) {
        return new DeferralRow("S-1", "A-1", POLICY, "C-1", "202611", 10_000, "INFORCE", status, "CURVE", "CV1", Optional.empty(), 3);
    }

    static MonthRow month(DataStatus data) {
        return MonthRow.first("A-1", "202609", MonthStatus.CONFIRMED, 4).withDataStatus(data);
    }

    static List<Candidate> run(Envelope e, Change c) {
        List<Candidate> out = NotificationRules.candidates(e, c, () -> T);
        SEEN.addAll(out);
        return out;
    }

    /** 단계마다 그 단계의 누적(한도 1,000,000, 경고 80%, 위험 95%). */
    static long accumAt(LimitStage s) {
        return switch (s) {
            case OK -> 100_000;
            case WARN -> 800_000;
            case CRIT -> 950_000;
            case FULL -> 1_000_000;
        };
    }

    static Stream<Arguments> stagePairs() {
        List<Arguments> out = new ArrayList<>();
        for (LimitStage before : LimitStage.values()) {
            for (LimitStage after : LimitStage.values()) {
                out.add(Arguments.of(before, after));
            }
        }
        return out.stream();
    }

    @ParameterizedTest
    @MethodSource("stagePairs")
    void aPostingNotifiesOnlyWhenTheStageRisesAndThenOnlyTheNewStage(LimitStage before, LimitStage after) {
        LedgerRow b = ledger(LIMIT, accumAt(before));
        LedgerRow a = b.posted(accumAt(after), 2);
        assertThat(NotificationRules.stage(b, T)).isEqualTo(before);
        assertThat(NotificationRules.stage(a, T)).isEqualTo(after);
        List<Candidate> out = run(env("LimitLedgerPosted", 41), new Change.LedgerPosted(b, a));
        if (after.compareTo(before) <= 0) {
            assertThat(out).isEmpty();
            return;
        }
        NotificationType type = NotificationType.valueOf("LIMIT_" + after.name());
        assertThat(out).singleElement().satisfies(c -> {
            assertThat(c.type()).isEqualTo(type);
            assertThat(c.dedupKey()).isEqualTo("LIMIT_" + after.name() + ":L-1:41");
            assertThat(c.agentId()).isEqualTo("A-1");
            assertThat(c.params()).isEqualTo(new NotificationParams.Ledger("L-1", MASKED));
            assertThat(c.link()).contains("/limits/L-1");
        });
        assertThat(type.severity()).isEqualTo(switch (after) {
            case WARN -> Severity.INFO;
            case CRIT -> Severity.WARN;
            default -> Severity.CRIT;
        });
    }

    @Test
    void riseFallRiseIsTwoKeysAndTheThresholdsAreReadOnlyForPostings() {
        LedgerRow ok = ledger(LIMIT, 100_000);
        LedgerRow warn = ok.posted(850_000, 2);
        LedgerRow back = warn.posted(700_000, 3);
        LedgerRow again = back.posted(810_000, 4);
        List<String> keys = new ArrayList<>();
        keys.addAll(run(env("LimitLedgerPosted", 10), new Change.LedgerPosted(ok, warn)).stream().map(Candidate::dedupKey).toList());
        keys.addAll(run(env("LimitLedgerPosted", 11), new Change.LedgerPosted(warn, back)).stream().map(Candidate::dedupKey).toList());
        keys.addAll(run(env("LimitLedgerPosted", 12), new Change.LedgerPosted(back, again)).stream().map(Candidate::dedupKey).toList());
        assertThat(keys).containsExactly("LIMIT_WARN:L-1:10", "LIMIT_WARN:L-1:12");
        // 전기 밖에서는 임계치를 읽지 않는다
        assertThat(NotificationRules.candidates(env("PayoutCompleted", 5), new Change.MonthClosed(month(DataStatus.OK)), () -> {
            throw new AssertionError("thresholds read");
        })).hasSize(1);
    }

    @Test
    void aRebaseIsAlwaysOneRebaseEvenWhenTheStageRises() {
        LedgerRow before = ledger(LIMIT, 900_000);
        LedgerRow after = before.rebased(900_000, 30_000);
        assertThat(NotificationRules.stage(after, T)).isEqualTo(LimitStage.FULL);
        assertThat(run(env("LimitLedgerRebased", 7), new Change.LedgerRebased(before, after))).singleElement().satisfies(c -> {
            assertThat(c.type()).isEqualTo(NotificationType.LIMIT_REBASE);
            assertThat(c.dedupKey()).isEqualTo("LIMIT_REBASE:L-1:7");
            assertThat(c.severity()).isEqualTo(Severity.INFO);
        });
        LedgerRow up = before.rebased(2_000_000, 100_000);
        assertThat(run(env("LimitLedgerRebased", 8), new Change.LedgerRebased(before, up))).extracting(Candidate::dedupKey)
                .containsExactly("LIMIT_REBASE:L-1:8");
    }

    @Test
    void linesNotifyOnACutAndOnAClawbackSeparately() {
        assertThat(run(env("CommissionCalculated", 1), new Change.LineAdded(line("FY_COMM", 900_000, 0)))).isEmpty();
        assertThat(run(env("CommissionCalculated", 2), new Change.LineAdded(line("FY_COMM", 900_000, 120_000)))).singleElement().satisfies(c -> {
            assertThat(c.type()).isEqualTo(NotificationType.LIMIT_CUT);
            assertThat(c.dedupKey()).isEqualTo("LIMIT_CUT:C-9");
            assertThat(c.params()).isEqualTo(new NotificationParams.Line("C-9", MASKED, "202610"));
            assertThat(c.link()).contains("/lines/C-9");
        });
        assertThat(run(env("CommissionCalculated", 3), new Change.LineAdded(line("CLAWBACK", -300_000, 0)))).extracting(Candidate::type, Candidate::dedupKey)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(NotificationType.CLAWBACK, "CLAWBACK:C-9"));
        assertThat(run(env("CommissionCalculated", 4), new Change.LineAdded(line("CLAWBACK", -300_000, 5)))).extracting(Candidate::type)
                .containsExactly(NotificationType.LIMIT_CUT, NotificationType.CLAWBACK);
        for (String other : List.of("RENEWAL", "INCENTIVE", "OVERRIDE", "DEFERRED")) {
            assertThat(run(env("CommissionCalculated", 5), new Change.LineAdded(line(other, 1, 0)))).as(other).isEmpty();
        }
    }

    @Test
    void deferralsNotifyOnHeldAndCancelledOnlyAndLinkToTheSourceLine() {
        DeferralRow scheduled = deferral(DeferralStatus.SCHEDULED);
        assertThat(run(env("DeferralStatusChanged", 1), new Change.DeferralChanged(scheduled, scheduled.transitioned(DeferralStatus.RELEASED, "DUE_RELEASED", 1))))
                .isEmpty();
        assertThat(run(env("DeferralStatusChanged", 2), new Change.DeferralChanged(scheduled, scheduled.transitioned(DeferralStatus.HELD, "POLICY_NOT_INFORCE", 2))))
                .singleElement().satisfies(c -> {
                    assertThat(c.type()).isEqualTo(NotificationType.DEFER_HELD);
                    assertThat(c.dedupKey()).isEqualTo("DEFER_HELD:S-1");
                    assertThat(c.params()).isEqualTo(new NotificationParams.Deferral("C-1", MASKED, "202611"));
                    assertThat(c.link()).contains("/lines/C-1");
                });
        DeferralRow held = scheduled.transitioned(DeferralStatus.HELD, "POLICY_NOT_INFORCE", 2);
        assertThat(run(env("DeferralStatusChanged", 3), new Change.DeferralChanged(held, held.transitioned(DeferralStatus.CANCELLED, "POLICY_TERMINATED", 3))))
                .extracting(Candidate::type, Candidate::dedupKey)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(NotificationType.DEFER_CANCELLED, "DEFER_CANCELLED:S-1"));
    }

    @Test
    void policyEventsNotifyWithoutALinkAndReinstateIsSilent() {
        for (String type : List.of("PAYMENT_OVERDUE", "LAPSE", "CANCEL")) {
            Envelope e = env("PolicyEventObserved", 20, "00000000-0000-4000-8000-0000000000aa");
            assertThat(run(e, new Change.FlagObserved(type, POLICY, "A-1", LocalDate.parse("2026-10-19")))).singleElement().satisfies(c -> {
                assertThat(c.type()).isEqualTo(NotificationType.valueOf("POLICY_" + type));
                assertThat(c.dedupKey()).isEqualTo("POLICY_" + type + ":00000000-0000-4000-8000-0000000000aa");
                assertThat(c.params()).isEqualTo(new NotificationParams.Policy(MASKED, LocalDate.parse("2026-10-19")));
                assertThat(c.link()).isEmpty();
            });
        }
        assertThat(run(env("PolicyEventObserved", 21), new Change.FlagObserved("REINSTATE", POLICY, "A-1", LocalDate.parse("2026-10-20")))).isEmpty();
    }

    @Test
    void confirmedAndPaidMonthsNotifyUnlessDrifted() {
        assertThat(run(env("StatementConfirmed", 30), new Change.MonthClosed(month(DataStatus.OK)))).singleElement().satisfies(c -> {
            assertThat(c.type()).isEqualTo(NotificationType.STMT_CONFIRMED);
            assertThat(c.dedupKey()).isEqualTo("STMT_CONFIRMED:202609");
            assertThat(c.params()).isEqualTo(new NotificationParams.Month("202609"));
            assertThat(c.link()).contains("/statements/202609");
        });
        assertThat(run(env("PayoutCompleted", 31), new Change.MonthClosed(month(DataStatus.OK)))).extracting(Candidate::dedupKey)
                .containsExactly("PAYOUT:202609");
        assertThat(run(env("StatementConfirmed", 32), new Change.MonthClosed(month(DataStatus.DRIFT)))).isEmpty();
        assertThat(run(env("PayoutCompleted", 33), new Change.MonthClosed(month(DataStatus.DRIFT)))).isEmpty();
        assertThatThrownBy(() -> NotificationRules.candidates(env("StatementEstimated", 34), new Change.MonthClosed(month(DataStatus.OK)), () -> T))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anAnsweredInquiryIsOneInfoNotificationLinkedToTheInquiry() {
        Candidate c = NotificationRules.inquiryAnswered("A-1", "00000000-0000-4000-8000-0000000000c1");
        SEEN.add(c);
        assertThat(c.type()).isEqualTo(NotificationType.INQUIRY_ANSWERED);
        assertThat(c.severity()).isEqualTo(Severity.INFO);
        assertThat(c.dedupKey()).isEqualTo("INQUIRY:00000000-0000-4000-8000-0000000000c1");
        assertThat(c.params()).isEqualTo(new NotificationParams.Inquiry("00000000-0000-4000-8000-0000000000c1"));
        assertThat(c.link()).contains("/inquiries/00000000-0000-4000-8000-0000000000c1");
    }

    // ------------------------------------------------------------------ 임의 전기열(시드 고정)

    /** 신탁: 경계 금액(그 bp에 처음 닿는 누적)을 먼저 구한다 — 규칙의 곱셈 비교와 다른 식. */
    static LimitStage oracle(long limit, long accum) {
        if (accum >= limit) {
            return LimitStage.FULL;
        }
        long critAt = Math.ceilDiv(T.critBp() * limit, 10_000L);
        long warnAt = Math.ceilDiv(T.warnBp() * limit, 10_000L);
        return accum >= critAt ? LimitStage.CRIT : accum >= warnAt ? LimitStage.WARN : LimitStage.OK;
    }

    static Stream<Arguments> postingSequences() {
        return SeededCases.of(0x5EED_0B06L, r -> new Object[] {r.nextLong(1, 5_000_000), sequence(r)});
    }

    static long[] sequence(RandomGenerator r) {
        long[] amounts = new long[r.nextInt(1, 40)];
        for (int i = 0; i < amounts.length; i++) {
            amounts[i] = r.nextInt(5) == 0 ? -r.nextLong(0, 400_000) : r.nextLong(0, 400_000);
        }
        return amounts;
    }

    @ParameterizedTest
    @MethodSource("postingSequences")
    void theCandidateCountIsTheNumberOfRisesAndEveryKeyIsDistinct(long limit, long[] amounts) {
        LedgerRow row = ledger(limit, 0);
        int rises = 0;
        List<Candidate> all = new ArrayList<>();
        long seq = 100;
        for (long amount : amounts) {
            long accum = Math.clamp(row.accumPaid() + amount, 0, limit);
            if (accum == row.accumPaid()) {
                continue;
            }
            LedgerRow next = row.posted(accum, row.lastPostingSeq() + 1);
            LimitStage before = oracle(limit, row.accumPaid());
            LimitStage after = oracle(limit, accum);
            List<Candidate> out = run(env("LimitLedgerPosted", seq++), new Change.LedgerPosted(row, next));
            if (after.compareTo(before) > 0) {
                rises++;
                assertThat(out).singleElement().extracting(Candidate::type).isEqualTo(NotificationType.valueOf("LIMIT_" + after.name()));
            } else {
                assertThat(out).isEmpty();
            }
            all.addAll(out);
            row = next;
        }
        assertThat(all).hasSize(rises);
        Set<String> keys = new HashSet<>();
        all.forEach(c -> assertThat(keys.add(c.agentId() + "|" + c.dedupKey())).as("distinct key %s", c.dedupKey()).isTrue());
    }

    @Test
    void noCandidateCarriesThePolicyNumber() {
        // 다른 시험이 모은 후보 전부(같은 JVM — 순서가 보장되지 않아 여기서 표 전부를 한 번 더 돈다)
        aRebaseIsAlwaysOneRebaseEvenWhenTheStageRises();
        linesNotifyOnACutAndOnAClawbackSeparately();
        deferralsNotifyOnHeldAndCancelledOnlyAndLinkToTheSourceLine();
        policyEventsNotifyWithoutALinkAndReinstateIsSilent();
        confirmedAndPaidMonthsNotifyUnlessDrifted();
        aPostingNotifiesOnlyWhenTheStageRisesAndThenOnlyTheNewStage(LimitStage.OK, LimitStage.WARN);
        aPostingNotifiesOnlyWhenTheStageRisesAndThenOnlyTheNewStage(LimitStage.OK, LimitStage.CRIT);
        aPostingNotifiesOnlyWhenTheStageRisesAndThenOnlyTheNewStage(LimitStage.OK, LimitStage.FULL);
        assertThat(com.ga.portal.domain.rule.PolicyMask.mask(POLICY)).isEqualTo(MASKED);
        assertThat(SEEN).hasSizeGreaterThanOrEqualTo(15).allSatisfy(c -> {
            assertThat(c.dedupKey()).doesNotContain(POLICY);
            assertThat(c.params().toString()).doesNotContain(POLICY);
            assertThat(c.link().orElse("")).doesNotContain(POLICY);
        });
        anAnsweredInquiryIsOneInfoNotificationLinkedToTheInquiry();
        // 표의 종류 열넷 전부(문의 답변은 운영 CLI가 같은 규칙 클래스로 만든다 — InquiryCliIT)
        assertThat(SEEN).extracting(Candidate::type).contains(NotificationType.values());
    }
}
