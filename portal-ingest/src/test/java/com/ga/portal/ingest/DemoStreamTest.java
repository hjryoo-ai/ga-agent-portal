package com.ga.portal.ingest;

import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.NotificationType;
import com.ga.portal.projection.Envelope;
import com.ga.portal.projection.ProjectionResult;
import com.ga.portal.projection.ViewProjector;
import com.ga.portal.projection.notify.Candidate;
import com.ga.portal.projection.notify.NotificationRules;
import com.ga.portal.projection.notify.Thresholds;
import com.ga.portal.projection.testing.Envelopes;
import com.ga.portal.projection.testing.GoldenViews;
import com.ga.portal.projection.testing.InMemoryViewStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포털 Phase 8 ⓪c(지시문 C2, 계획 P-8·Q12): 데모 테넌트 스트림(엔진 {@code DemoContractTest}가 낸 사본 — 17,856건) 전체를 포털의 검증·투영·알림 규칙에
 * 돌린다(인메모리 — 같은 스트림의 DB 적재·전수 불변식·대사 규칙은 {@code DemoStreamIT}).
 * <ul>
 *   <li>모든 줄이 {@link EnvelopeValidator}를 통과하고 seq가 1부터 빈틈없다. 프로젝터는 예외 0, 이벤트 출처 DRIFT 0(엔진 스트림은 자기 합과 맞는다).</li>
 *   <li>두 번 투영하면 같은 뷰(결정론). 뷰는 요약 골든({@code golden-views/demo/summary.jsonl} — 표마다 행 수·SHA-256)과 손 검산 설계사 둘의 전 행.</li>
 *   <li>알림 규칙은 전 스트림에서 예외 0, 종류별 수 고정, 후보 어디에도 증권번호 원문 없음.</li>
 *   <li>엔진 케이스 표({@code cases.json})의 seq가 그 계약·설계사의 이벤트를 가리킨다.</li>
 * </ul>
 */
class DemoStreamTest {

    static final TenantId T = TenantId.of("DEMO");
    static final Thresholds DEFAULT = new Thresholds(8_000, 9_500);
    /** 손 검산 설계사(보고서): A-2001(한도 80% 케이스), A-2009(HELD 분급·채권 이월 케이스). */
    static final Set<String> HAND_CHECKED = Set.of("A-2001", "A-2009");

    record Run(Map<String, List<String>> views, Map<NotificationType, Integer> notifications, List<Candidate> candidates, long events, long drifts) {
    }

    private static Run once;

    static Run run() {
        EnvelopeValidator validator = new EnvelopeValidator(ContractSchemas.get());
        InMemoryViewStore store = new InMemoryViewStore();
        ViewProjector projector = new ViewProjector(store);
        Map<NotificationType, Integer> counts = new EnumMap<>(NotificationType.class);
        List<Candidate> all = new ArrayList<>();
        long seq = 0;
        long drifts = 0;
        for (ObjectNode node : Envelopes.nodes(Envelopes.demo())) {
            EnvelopeVerdict verdict = validator.validate(node);
            assertThat(verdict).as("seq %s", node.path("seq")).isInstanceOf(EnvelopeVerdict.Valid.class);
            Envelope e = ((EnvelopeVerdict.Valid) verdict).envelope();
            assertThat(e.seq()).as("gapless").isEqualTo(++seq);
            ProjectionResult r = projector.apply(T, e);
            if (r.drift()) {
                drifts++;
            }
            r.change().ifPresent(c -> NotificationRules.candidates(e, c, () -> DEFAULT).forEach(n -> {
                counts.merge(n.type(), 1, Integer::sum);
                all.add(n);
            }));
        }
        return new Run(GoldenViews.of(store), counts, all, seq, drifts);
    }

    @BeforeAll
    static void project() {
        once = run();
    }

    @Test
    void everyEventPassesTheValidatorAndTheProjectorWithoutDrift() {
        assertThat(once.events()).isEqualTo(17_856);
        assertThat(once.drifts()).as("the engine's statements equal its own lines").isZero();
        assertThat(once.views().get("recon_result")).isEmpty();
        assertThat(once.views().get("earnings_month")).isNotEmpty().noneMatch(r -> r.contains("\"data_status\":\"DRIFT\""));
        Set<String> agents = once.views().get("earnings_month").stream().map(r -> Envelopes.JSON.readTree(r).path("agent_id").asString())
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        assertThat(agents).as("30 demo agents").hasSize(30);
    }

    @Test
    void theProjectionIsDeterministic() {
        assertThat(run().views()).isEqualTo(once.views());
    }

    /** 계획 Q12: 요약 골든 + 손 검산 설계사 둘의 전 행({@code -Pgolden.update=true}로 쓰고 보고서 손 검산과 대조했다). */
    @Test
    void theViewsMatchTheGoldenSummaryAndTheHandCheckedAgents() {
        GoldenViews.assertSummary("demo", once.views(), HAND_CHECKED);
    }

    @Test
    void theNotificationRulesRunOverTheWholeStream() {
        // 종류별 수(규칙의 결과 — 첫 실행에서 고정, 보고서 표). 확정·지급은 StatementConfirmed·PayoutCompleted 686건씩과 같다
        assertThat(once.notifications()).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(Map.entry(NotificationType.LIMIT_WARN, 17),
                Map.entry(NotificationType.LIMIT_CRIT, 4), Map.entry(NotificationType.LIMIT_FULL, 2), Map.entry(NotificationType.LIMIT_REBASE, 5),
                Map.entry(NotificationType.LIMIT_CUT, 53), Map.entry(NotificationType.CLAWBACK, 32), Map.entry(NotificationType.DEFER_HELD, 1),
                Map.entry(NotificationType.DEFER_CANCELLED, 213), Map.entry(NotificationType.POLICY_CANCEL, 32),
                Map.entry(NotificationType.STMT_CONFIRMED, 686), Map.entry(NotificationType.PAYOUT, 686)));
        Set<String> policies = new TreeSet<>();
        for (ObjectNode node : Envelopes.nodes(Envelopes.demo())) {
            String p = node.path("payload").path("policyNo").asString("");
            if (!p.isEmpty()) {
                policies.add(p);
            }
        }
        assertThat(policies).hasSizeGreaterThan(400);
        for (Candidate c : once.candidates()) {
            String text = c.dedupKey() + "|" + c.params() + "|" + c.link().orElse("");
            for (String p : policies) {
                assertThat(text).doesNotContain(p);
            }
        }
    }

    /** 엔진 케이스 표(부록 B 필수 케이스 13): seq가 스트림 안에 있고, 계약이 있는 케이스는 그 증권번호·설계사의 이벤트다. */
    @Test
    void theEngineCaseTablePointsAtTheStream() {
        Path cases = Envelopes.repoRoot().resolve("contracts/shared/ga-commission-engine/samples/demo/cases.json");
        List<ObjectNode> events = Envelopes.nodes(Envelopes.demo());
        JsonNode table = Envelopes.JSON.readTree(cases.toFile());
        assertThat(table).hasSize(13);
        for (JsonNode c : table) {
            for (JsonNode s : c.path("seqs")) {
                JsonNode e = events.get((int) s.asLong() - 1);
                assertThat(e.path("seq").asLong()).isEqualTo(s.asLong());
                String policy = c.path("policyNo").asString();
                if (!policy.isEmpty() && e.path("payload").has("policyNo")) {
                    assertThat(e.path("payload").path("policyNo").asString()).as(c.path("case").asString()).isEqualTo(policy);
                }
                if (e.path("payload").has("agentId")) {
                    assertThat(e.path("payload").path("agentId").asString()).as(c.path("case").asString()).isEqualTo(c.path("agentId").asString());
                }
            }
        }
    }
}
