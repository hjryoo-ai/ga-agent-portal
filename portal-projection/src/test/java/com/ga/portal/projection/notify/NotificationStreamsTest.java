package com.ga.portal.projection.notify;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.NotificationType;
import com.ga.portal.projection.Envelope;
import com.ga.portal.projection.ProjectionResult;
import com.ga.portal.projection.ViewProjector;
import com.ga.portal.projection.testing.Envelopes;
import com.ga.portal.projection.testing.InMemoryViewStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C2(포털 Phase 6 계획 P-9): 실제 프로젝터({@link ViewProjector} + 인메모리 뷰)가 돌려준 {@code Change}에 규칙을 돌린 결과가 계획의 손 계산과 같다 — A-1(계약 샘플,
 * GA01)과 화면 E2E 손 스트림 {@code edges}(GA04)·{@code ga05-limits}(GA05 — 같은 원장의 상향·하향·재상향과 재산정, 계획 Q26). 임계치는 테넌트 기본(80%·95%). 증권번호 원문은 후보 어디에도 없다(스트림의 증권번호 전부와 대조).
 */
class NotificationStreamsTest {

    static final TenantId T = TenantId.of("GA01");
    static final Thresholds DEFAULT = new Thresholds(8_000, 9_500);

    record Derived(long seq, NotificationType type, String dedupKey) {
    }

    static List<Derived> derive(Path stream, List<Candidate> all) {
        InMemoryViewStore store = new InMemoryViewStore();
        ViewProjector projector = new ViewProjector(store);
        List<Derived> out = new ArrayList<>();
        for (Envelope e : Envelopes.read(stream)) {
            ProjectionResult r = projector.apply(T, e);
            r.change().ifPresent(c -> NotificationRules.candidates(e, c, () -> DEFAULT).forEach(n -> {
                out.add(new Derived(e.seq(), n.type(), n.dedupKey()));
                all.add(n);
            }));
        }
        return out;
    }

    static Set<String> policyNumbers(Path stream) {
        Set<String> out = new TreeSet<>();
        for (Envelope e : Envelopes.read(stream)) {
            String p = e.payload().path("policyNo").asString("");
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    static void assertNoPolicyNumber(List<Candidate> all, Set<String> policies) {
        assertThat(policies).isNotEmpty();
        for (Candidate c : all) {
            for (String p : policies) {
                assertThat(c.dedupKey() + "|" + c.params() + "|" + c.link().orElse("")).doesNotContain(p);
            }
        }
    }

    @Test
    void a1DerivesTheHandCountedNotifications() {
        List<Candidate> all = new ArrayList<>();
        assertThat(derive(Envelopes.a1(), all)).extracting(Derived::seq, Derived::type).containsExactly(
                org.assertj.core.groups.Tuple.tuple(3L, NotificationType.LIMIT_CUT),
                org.assertj.core.groups.Tuple.tuple(5L, NotificationType.LIMIT_FULL),
                org.assertj.core.groups.Tuple.tuple(6L, NotificationType.STMT_CONFIRMED),
                org.assertj.core.groups.Tuple.tuple(7L, NotificationType.PAYOUT),
                org.assertj.core.groups.Tuple.tuple(8L, NotificationType.POLICY_CANCEL),
                org.assertj.core.groups.Tuple.tuple(9L, NotificationType.CLAWBACK));
        assertNoPolicyNumber(all, policyNumbers(Envelopes.a1()));
    }

    @Test
    void theEdgesStreamDerivesTheHandCountedNotificationsAndNoneForTheDriftedMonth() {
        Path edges = Envelopes.repoRoot().resolve("web/e2e/streams/edges.jsonl");
        List<Candidate> all = new ArrayList<>();
        List<Derived> derived = derive(edges, all);
        assertThat(derived).extracting(Derived::seq, Derived::type).containsExactly(
                org.assertj.core.groups.Tuple.tuple(4L, NotificationType.LIMIT_CUT),
                org.assertj.core.groups.Tuple.tuple(5L, NotificationType.LIMIT_FULL),
                org.assertj.core.groups.Tuple.tuple(14L, NotificationType.STMT_CONFIRMED),
                org.assertj.core.groups.Tuple.tuple(16L, NotificationType.LIMIT_WARN),
                org.assertj.core.groups.Tuple.tuple(23L, NotificationType.DEFER_HELD),
                org.assertj.core.groups.Tuple.tuple(24L, NotificationType.DEFER_CANCELLED));
        assertThat(derived).extracting(Derived::dedupKey).doesNotHaveDuplicates().noneMatch(k -> k.contains("202608"));
        assertNoPolicyNumber(all, policyNumbers(edges));
    }

    @Test
    void ga05RisesFallsAndRisesAgainOnOneLedgerThenRebases() {
        Path ga05 = Envelopes.repoRoot().resolve("web/e2e/streams/ga05-limits.jsonl");
        List<Candidate> all = new ArrayList<>();
        List<Derived> derived = derive(ga05, all);
        // 5: 85% → WARN, 6: 환수 라인, 7: 65% → OK(하향 — 없음), 9: 85% → WARN(재상향 — 새 키), 11: 90%(같은 단계 — 없음),
        // 12: 한도 1,000,000 → 900,000(남은 0 — FULL이 되지만 재산정은 LIMIT_REBASE 하나뿐)
        assertThat(derived).extracting(Derived::seq, Derived::type, Derived::dedupKey).containsExactly(
                org.assertj.core.groups.Tuple.tuple(5L, NotificationType.LIMIT_WARN, "LIMIT_WARN:GA5-LEDGER-1:5"),
                org.assertj.core.groups.Tuple.tuple(6L, NotificationType.CLAWBACK, "CLAWBACK:GA5-C3"),
                org.assertj.core.groups.Tuple.tuple(9L, NotificationType.LIMIT_WARN, "LIMIT_WARN:GA5-LEDGER-1:9"),
                org.assertj.core.groups.Tuple.tuple(12L, NotificationType.LIMIT_REBASE, "LIMIT_REBASE:GA5-LEDGER-1:12"));
        assertNoPolicyNumber(all, policyNumbers(ga05));
    }
}
