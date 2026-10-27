package com.ga.portal.query;

import com.ga.portal.domain.access.Caller;
import com.ga.portal.domain.access.DenyReason;
import com.ga.portal.domain.access.InvalidRequest;
import com.ga.portal.domain.access.NotFound;
import com.ga.platform.core.tenant.AgentId;
import com.ga.platform.core.tenant.TenantId;
import com.ga.portal.domain.DataStatus;
import com.ga.portal.domain.IngestState;
import com.ga.portal.domain.MonthStatus;
import com.ga.portal.domain.Role;
import com.ga.portal.domain.view.AgentRow;
import com.ga.portal.domain.view.DeferralRow;
import com.ga.portal.domain.view.HistoryRow;
import com.ga.portal.domain.view.LedgerRow;
import com.ga.portal.domain.view.MonthRow;
import com.ga.portal.domain.view.ReceivableRow;
import com.ga.portal.domain.DeferralStatus;
import com.ga.portal.domain.NetBasis;
import com.ga.portal.domain.view.MonthDetail;
import com.ga.portal.query.api.DeferralsResponse;
import com.ga.portal.query.api.Ledger;
import com.ga.portal.query.api.LinesResponse;
import com.ga.portal.query.api.LimitsResponse;
import com.ga.portal.query.port.AgentQueryStore;
import com.ga.portal.domain.view.DueMonth;
import com.ga.portal.domain.view.LedgerItem;
import com.ga.portal.domain.view.LineDetail;
import com.ga.portal.domain.view.LineItem;
import com.ga.portal.domain.view.LinePage;
import com.ga.portal.domain.view.SyncSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 유스케이스 단위(포트는 메모리 가짜): 역할 검사가 저장소보다 먼저, 남의 키는 MISSING, 경로 형식 → 404·쿼리 형식 → 400, {@code policyKey} 필터는 그 설계사의
 * 증권번호 집합으로만 풀린다, 응답에 증권번호 원문 없음, 한도 0의 {@code utilization} 생략, 지연 전환은 ETag를 바꾼다.
 */
class MeQueriesTest {

    static final TenantId T = TenantId.of("GA01");
    static final AgentId A = AgentId.of("A-1001");
    static final AgentId B = AgentId.of("B-2002");
    static final Instant NOW = Instant.parse("2026-10-17T03:00:00Z");
    static final String POLICY_A = "POL-2026-0001";
    static final String POLICY_B = "POL-9999-7777";

    final FakeStore store = new FakeStore();
    SyncSnapshot snapshot = snapshot(Optional.of(NOW.minusSeconds(10)));
    Optional<String> reconMark = Optional.empty();
    final MeQueries queries = new MeQueries(store, () -> snapshot, () -> reconMark, (tenant, policyNo) -> key(policyNo), new ReadTransaction(),
            Clock.fixed(NOW, ZoneOffset.UTC), 300);

    /** 가짜 키(16자 16진, 결정적). */
    static String key(String policyNo) {
        return String.format("%016x", policyNo.hashCode() & 0xffffffffL);
    }

    static SyncSnapshot snapshot(Optional<Instant> caughtUp) {
        return snapshot(caughtUp, false);
    }

    static SyncSnapshot snapshot(Optional<Instant> caughtUp, boolean forecast) {
        return new SyncSnapshot("시험 GA", "NEXT_MONTH_D10", 8000, 9500, Optional.empty(), forecast,
                Optional.of(new SyncSnapshot.Cursor(11, NOW.minusSeconds(3600), IngestState.RUNNING, caughtUp)));
    }

    static Caller caller(AgentId agent, Role role) {
        return new Caller(T, agent, role);
    }

    static final class ReadTransaction implements com.ga.portal.query.port.ReadTransaction {
        int opened;

        @Override
        public <R> R read(java.util.function.Supplier<R> work) {
            opened++;
            return work.get();
        }
    }

    /** 메모리 가짜 — 모든 조회는 설계사로 걸러진다(실제 저장소의 {@code agent_id = :agentId}와 같은 뜻). 호출 수를 센다. */
    static final class FakeStore implements AgentQueryStore {
        final List<String> calls = new ArrayList<>();
        final Map<String, LedgerItem> ledgers = new TreeMap<>();
        final List<LineItem> lines = new ArrayList<>();
        /** 지급월별 원값 합(설계사 A의 것 — 계약별). */
        final List<DeferralRow> deferralRows = new ArrayList<>();

        FakeStore() {
            ledgers.put("1", item("1", A, POLICY_A, 2_250_000, 1_800_000));
            ledgers.put("2", item("2", B, POLICY_B, 2_250_000, 0));
            ledgers.put("3", item("3", A, "POL-ZERO-0003", 0, 0));
            lines.add(line("1", A, POLICY_A, "FY_COMM", 945_000));
            lines.add(line("2", A, "POL-2026-0002", "INCENTIVE", 855_000));
            lines.add(line("9", B, POLICY_B, "FY_COMM", 1));
            deferralRows.add(deferral("S1", A, POLICY_A, "202703", 300_001, DeferralStatus.SCHEDULED));
            deferralRows.add(deferral("S2", A, POLICY_A, "202703", 100_000, DeferralStatus.HELD));
            deferralRows.add(deferral("S3", A, "POL-2026-0002", "202704", 600_000, DeferralStatus.RELEASED));
            deferralRows.add(deferral("S4", A, "POL-2026-0002", "202705", 50_000, DeferralStatus.CANCELLED));
            deferralRows.add(deferral("S9", B, POLICY_B, "202703", 7, DeferralStatus.SCHEDULED));
        }

        static DeferralRow deferral(String id, AgentId agent, String policy, String dueYm, long amount, DeferralStatus status) {
            return new DeferralRow(id, agent.value(), policy, "1", dueYm, amount, "INFORCE", status, "CURVE", "C1", Optional.empty(), 1);
        }

        static LedgerItem item(String id, AgentId agent, String policy, long limit, long accum) {
            return new LedgerItem(new LedgerRow(id, agent.value(), policy, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2027, 7, 31),
                    150_000, "15", limit, accum, 0, 0, 1, "LIMIT_RULE:1"), limit - accum, List.of(), Optional.of("WHOLE-LIFE-20Y"),
                    Optional.of("종신20"), Optional.of("김*"));
        }

        static LineItem line(String calcId, AgentId agent, String policy, String type, long amount) {
            return new LineItem(calcId, "202608", type, amount, 0, Optional.empty(), Optional.of("P-" + agent.value()), Optional.empty(), Optional.empty(),
                    policy, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Long.parseLong(calcId));
        }

        private boolean owns(AgentId agent, LineItem l) {
            return l.productKey().orElseThrow().equals("P-" + agent.value());
        }

        @Override
        public Optional<AgentRow> profile(AgentId agent) {
            calls.add("profile");
            return Optional.empty();
        }

        @Override
        public List<MonthRow> months(AgentId agent, String fromYm, String toYm) {
            calls.add("months");
            return List.of();
        }

        @Override
        public Optional<MonthRow> month(AgentId agent, String closeYm) {
            calls.add("month");
            return agent.equals(A) && closeYm.equals("202608") ? Optional.of(MonthRow.first(A.value(), "202608", MonthStatus.CONFIRMED, 6)
                    .withDataStatus(DataStatus.OK)) : Optional.empty();
        }

        @Override
        public Optional<MonthDetail> monthDetail(AgentId agent, String closeYm, String previousYm) {
            calls.add("monthDetail");
            Optional<MonthRow> row = agent.equals(A) && closeYm.equals("202608")
                    ? Optional.of(MonthRow.first(A.value(), "202608", MonthStatus.CONFIRMED, 6).withDataStatus(DataStatus.OK)) : Optional.empty();
            return row.map(m -> new MonthDetail(m, previousYm.equals("202607")
                    ? Optional.of(new MonthDetail.Previous("202607", MonthStatus.PAID, 10, Optional.of(9L), Optional.of(NetBasis.PAYOUT)))
                    : Optional.empty()));
        }

        @Override
        public Map<String, Long> byType(AgentId agent, String closeYm) {
            calls.add("byType");
            return Map.of();
        }

        @Override
        public LinePage lines(AgentId agent, String closeYm, Optional<String> commType, Optional<String> policyNo, int page, int size) {
            calls.add("lines");
            List<LineItem> mine = lines.stream().filter(l -> owns(agent, l))
                    .filter(l -> commType.map(c -> c.equals(l.commType())).orElse(true))
                    .filter(l -> policyNo.map(p -> p.equals(l.policyNo())).orElse(true)).toList();
            return new LinePage(mine, mine.size());
        }

        @Override
        public Set<String> linePolicies(AgentId agent, String closeYm) {
            calls.add("linePolicies");
            return lines.stream().filter(l -> owns(agent, l)).map(LineItem::policyNo).collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public Optional<LineDetail> line(AgentId agent, String calcId) {
            calls.add("line");
            return lines.stream().filter(l -> owns(agent, l) && l.calcId().equals(calcId)).findFirst()
                    .map(l -> new LineDetail(l, Optional.of("7"), Optional.empty(), Optional.empty(), List.of("R1"), Optional.of("2026-08-01"),
                            Optional.of(150_000L)));
        }

        @Override
        public List<DeferralRow> deferralsOfLine(AgentId agent, String calcId) {
            calls.add("deferralsOfLine");
            return List.of();
        }

        @Override
        public Optional<ReceivableRow> receivableOfLine(AgentId agent, String calcId) {
            calls.add("receivableOfLine");
            return Optional.empty();
        }

        @Override
        public Optional<String> ledgerIdOfPolicy(AgentId agent, String policyNo) {
            calls.add("ledgerIdOfPolicy");
            return ledgers.values().stream().filter(l -> l.ledger().agentId().equals(agent.value()) && l.ledger().policyNo().equals(policyNo))
                    .map(l -> l.ledger().ledgerId()).findFirst();
        }

        @Override
        public List<LedgerItem> ledgers(AgentId agent) {
            calls.add("ledgers");
            return ledgers.values().stream().filter(l -> l.ledger().agentId().equals(agent.value())).toList();
        }

        @Override
        public Optional<LedgerItem> ledger(AgentId agent, String ledgerId) {
            calls.add("ledger");
            return Optional.ofNullable(ledgers.get(ledgerId)).filter(l -> l.ledger().agentId().equals(agent.value()));
        }

        @Override
        public List<HistoryRow> history(AgentId agent, String ledgerId) {
            calls.add("history");
            return List.of();
        }

        @Override
        public List<DeferralRow> deferrals(AgentId agent, String fromYm, String toYm, Optional<String> policyNo) {
            calls.add("deferrals");
            return mine(agent, fromYm, toYm, policyNo).toList();
        }

        private java.util.stream.Stream<DeferralRow> mine(AgentId agent, String fromYm, String toYm, Optional<String> policyNo) {
            return deferralRows.stream().filter(r -> r.agentId().equals(agent.value()))
                    .filter(r -> r.dueYm().compareTo(fromYm) >= 0 && r.dueYm().compareTo(toYm) <= 0)
                    .filter(r -> policyNo.map(p -> p.equals(r.policyNo())).orElse(true));
        }

        @Override
        public List<DueMonth> dueMonths(AgentId agent, String fromYm, String toYm, Optional<String> policyNo) {
            calls.add("dueMonths");
            Map<String, long[]> sums = new TreeMap<>();
            mine(agent, fromYm, toYm, policyNo).forEach(r -> {
                long[] v = sums.computeIfAbsent(r.dueYm(), k -> new long[2]);
                switch (r.status()) {
                    case SCHEDULED, RELEASED -> v[0] += r.amount();
                    case HELD -> v[1] += r.amount();
                    default -> { }
                }
            });
            return sums.entrySet().stream().map(e -> new DueMonth(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
        }

        @Override
        public Set<String> deferralPolicies(AgentId agent) {
            calls.add("deferralPolicies");
            return deferralRows.stream().filter(r -> r.agentId().equals(agent.value())).map(DeferralRow::policyNo)
                    .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        }
    }

    static List<Function<MeQueries, Object>> everyEntry(Caller c) {
        return List.of(q -> q.me(c), q -> q.earnings(c, "202601", "202612"), q -> q.month(c, "202608"),
                q -> q.lines(c, "202608", 0, 50, Optional.empty(), Optional.empty()), q -> q.line(c, "1"), q -> q.limits(c, "ALL"),
                q -> q.limit(c, "1"), q -> deferrals(q, c, "202601", "202612"));
    }

    static QueryResult<DeferralsResponse> deferrals(MeQueries q, Caller c, String from, String to) {
        return q.deferrals(c, from, to, Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Test
    void aManagerIsDeniedAtEveryEntryBeforeAnyStoreCall() {
        for (Function<MeQueries, Object> entry : everyEntry(caller(A, Role.MANAGER))) {
            assertThatThrownBy(() -> entry.apply(queries)).isInstanceOfSatisfying(NotFound.class, e -> assertThat(e.reason()).isEqualTo(DenyReason.ROLE));
        }
        assertThat(store.calls).isEmpty();
        for (Function<MeQueries, Object> entry : everyEntry(caller(A, Role.AGENT))) {
            assertThat(entry.apply(queries)).isNotNull();
        }
    }

    @Test
    void anotherAgentsKeysAreMissing() {
        Caller a = caller(A, Role.AGENT);
        assertThatThrownBy(() -> queries.limit(a, "2")).isInstanceOfSatisfying(NotFound.class, e -> assertThat(e.reason()).isEqualTo(DenyReason.MISSING));
        assertThatThrownBy(() -> queries.line(a, "9")).isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> queries.month(caller(B, Role.AGENT), "202608")).isInstanceOf(NotFound.class);
        assertThat(queries.limits(a, "ALL").body().items()).extracting(Ledger::ledgerId).containsExactly("1", "3");
    }

    @Test
    void pathFormatErrorsAre404AndQueryFormatErrorsAre400() {
        Caller a = caller(A, Role.AGENT);
        for (String ym : new String[] {"2026-08", "202613", "abc", "20260"}) {
            assertThatThrownBy(() -> queries.month(a, ym)).as(ym).isInstanceOf(NotFound.class);
        }
        assertThatThrownBy(() -> queries.line(a, "x".repeat(129))).isInstanceOf(NotFound.class);
        List<Consumer<MeQueries>> bad = List.of(
                q -> q.earnings(a, null, "202612"), q -> q.earnings(a, "202612", "202601"), q -> q.earnings(a, "202601", "202901"),
                q -> q.earnings(a, "2026-1", "202612"), q -> deferrals(q, a, "202601", null), q -> deferrals(q, a, "202601", "203301"),
                q -> q.lines(a, "202608", -1, 50, Optional.empty(), Optional.empty()), q -> q.lines(a, "202608", 0, 0, Optional.empty(), Optional.empty()),
                q -> q.lines(a, "202608", 0, 201, Optional.empty(), Optional.empty()),
                q -> q.lines(a, "202608", 0, 50, Optional.of("fy_comm"), Optional.empty()),
                q -> q.lines(a, "202608", 0, 50, Optional.empty(), Optional.of("POL-2026-0001")), q -> q.limits(a, "OK"), q -> q.limits(a, "warn"));
        for (Consumer<MeQueries> call : bad) {
            assertThatThrownBy(() -> call.accept(queries)).isInstanceOf(InvalidRequest.class);
        }
        assertThat(queries.earnings(a, "202401", "202612").body().items()).as("36 months is allowed").isEmpty();
    }

    @Test
    void aPolicyKeyResolvesOnlyWithinTheCallersPolicies() {
        Caller a = caller(A, Role.AGENT);
        String keyA = queries.lines(a, "202608", 0, 50, Optional.empty(), Optional.empty()).body().items().getFirst().policyKey();
        LinesResponse filtered = queries.lines(a, "202608", 0, 50, Optional.empty(), Optional.of(keyA)).body();
        assertThat(filtered.items()).extracting(com.ga.portal.query.api.Line::calcId).containsExactly("1");
        // 남의 증권번호의 키는 이 설계사의 집합에서 풀리지 않는다 — 빈 목록(필터이지 자원이 아니다)
        store.calls.clear();
        LinesResponse othersKey = queries.lines(a, "202608", 0, 50, Optional.empty(), Optional.of(key(POLICY_B))).body();
        assertThat(othersKey.items()).isEmpty();
        assertThat(othersKey.total()).isZero();
        assertThat(store.calls).as("no line query for an unresolved key").doesNotContain("lines");
        LinesResponse unknown = queries.lines(a, "202608", 0, 50, Optional.empty(), Optional.of("0123456789abcdef")).body();
        assertThat(unknown.items()).isEmpty();
        assertThat(unknown.total()).isZero();
    }

    @Test
    void responsesCarryMaskedPoliciesAndKeysOnly() {
        Caller a = caller(A, Role.AGENT);
        List<Object> bodies = new ArrayList<>();
        for (Function<MeQueries, Object> entry : everyEntry(a)) {
            bodies.add(((QueryResult<?>) entry.apply(queries)).body());
        }
        assertThat(bodies.toString()).doesNotContain(POLICY_A).doesNotContain("POL-2026-0002").contains("*********0001");
    }

    @Test
    void utilizationIsOmittedForAZeroLimitAndTheStageFilterApplies() {
        Caller a = caller(A, Role.AGENT);
        LimitsResponse all = queries.limits(a, "ALL").body();
        assertThat(all.items()).filteredOn(l -> l.ledgerId().equals("1")).singleElement()
                .satisfies(l -> {
                    assertThat(l.utilization()).contains("80.0%");
                    assertThat(l.stage()).isEqualTo("WARN");
                    assertThat(l.remaining()).isEqualTo(450_000);
                });
        assertThat(all.items()).filteredOn(l -> l.ledgerId().equals("3")).singleElement()
                .satisfies(l -> assertThat(l.utilization()).isEmpty());
        assertThat(queries.limits(a, "WARN").body().items()).extracting(Ledger::ledgerId).containsExactly("1");
        assertThat(queries.limits(a, "CRIT").body().items()).isEmpty();
    }

    @Test
    void theDelaySwitchChangesTheEtagAndTheStatus() {
        Caller a = caller(A, Role.AGENT);
        QueryResult<?> fresh = queries.me(a);
        snapshot = snapshot(Optional.of(NOW.minusSeconds(301)));
        QueryResult<?> late = queries.me(a);
        assertThat(late.etag()).isNotEqualTo(fresh.etag());
        assertThat(((com.ga.portal.query.api.MeResponse) late.body()).dataStatus()).isEqualTo("SYNC_DELAYED");
        assertThat(((com.ga.portal.query.api.MeResponse) fresh.body()).dataStatus()).isEqualTo("OK");
    }

    /** 포털 Phase 7: 같은 {@code seq}에서 대사 회차(달의 {@code data_status})나 재구축(커서 갱신 시각)이 뷰를 바꾸면 ETag가 바뀐다 — 304가 옛 본문을 되살리지 않게. */
    @Test
    void aReconRunOrARebuildAtTheSameSeqChangesTheEtag() {
        Caller a = caller(A, Role.AGENT);
        String before = queries.me(a).etag();
        String prefix = before.substring(0, before.lastIndexOf('.'));
        SyncSnapshot s = snapshot;
        reconMark = Optional.of("2026-10-17T19:00:00Z|DRIFT|1|-");
        String afterRecon = queries.me(a).etag();
        assertThat(afterRecon).isNotEqualTo(before).startsWith(prefix);
        snapshot = new SyncSnapshot(s.tenantName(), s.payoutDayRule(), s.limitWarnBp(), s.limitCritBp(), s.configuredDelaySeconds(),
                s.forecastScenarioEnabled(), s.cursor().map(c -> new SyncSnapshot.Cursor(c.lastSeq(), c.updatedAt().plusSeconds(60), c.state(), c.caughtUpAt())));
        String afterRebuild = queries.me(a).etag();
        assertThat(afterRebuild).isNotEqualTo(afterRecon).startsWith(prefix);
        assertThat(queries.me(a).etag()).as("the same marks give the same tag").isEqualTo(afterRebuild);
        assertThat(queries.earnings(a, "202601", "202612").etag()).as("every response of the context").doesNotStartWith(before.substring(0, before.length() - 1));
    }

    // ------------------------------------------------------------------ 1.1.0 (포털 Phase 5 ⑦·C9, CR-1~4)

    @Test
    void theTimelineRangeIs84MonthsOnlyForDeferrals() {
        Caller a = caller(A, Role.AGENT);
        assertThat(deferrals(queries, a, "202701", "203312").body().months()).as("84 months").isNotEmpty();
        assertThatThrownBy(() -> deferrals(queries, a, "202701", "203401")).as("85 months").isInstanceOf(InvalidRequest.class);
        assertThatThrownBy(() -> queries.earnings(a, "202701", "203001")).as("other ranges stay at 36").isInstanceOf(InvalidRequest.class);
    }

    @Test
    void scenarioAndPercentAreValidatedAndRetentionNeedsTheTenantSwitch() {
        Caller a = caller(A, Role.AGENT);
        List<Consumer<MeQueries>> bad = List.of(
                q -> q.deferrals(a, "202701", "202712", Optional.of("RETENTION"), Optional.of("30"), Optional.empty()),   // 테넌트가 끔
                q -> q.deferrals(a, "202701", "202712", Optional.of("BEST"), Optional.empty(), Optional.empty()),
                q -> q.deferrals(a, "202701", "202712", Optional.of("ALL_INFORCE"), Optional.of("30"), Optional.empty()),
                q -> q.deferrals(a, "202701", "202712", Optional.empty(), Optional.of("30"), Optional.empty()),
                q -> q.deferrals(a, "202701", "202712", Optional.empty(), Optional.empty(), Optional.of("POL-2026-0001")));
        for (Consumer<MeQueries> call : bad) {
            assertThatThrownBy(() -> call.accept(queries)).isInstanceOf(InvalidRequest.class);
        }
        snapshot = snapshot(Optional.of(NOW.minusSeconds(10)), true);
        for (String p : new String[] {"0", "101", "05", "3.5", "", "-1"}) {
            assertThatThrownBy(() -> queries.deferrals(a, "202701", "202712", Optional.of("RETENTION"), Optional.of(p), Optional.empty()))
                    .as(p).isInstanceOf(InvalidRequest.class);
        }
        assertThatThrownBy(() -> queries.deferrals(a, "202701", "202712", Optional.of("RETENTION"), Optional.empty(), Optional.empty()))
                .isInstanceOf(InvalidRequest.class);
        assertThat(queries.deferrals(a, "202701", "202712", Optional.of("RETENTION"), Optional.of("100"), Optional.empty()).body().p()).contains(100);
    }

    @Test
    void retentionScalesOnlyTheMonthlyExpectedAndKeepsTheBaseHeldAndItems() {
        Caller a = caller(A, Role.AGENT);
        snapshot = snapshot(Optional.of(NOW.minusSeconds(10)), true);
        DeferralsResponse all = deferrals(queries, a, "202701", "202712").body();
        DeferralsResponse thirty = queries.deferrals(a, "202701", "202712", Optional.of("RETENTION"), Optional.of("30"), Optional.empty()).body();
        assertThat(all.scenario()).contains("ALL_INFORCE");
        assertThat(all.p()).isEmpty();
        assertThat(all.months()).allSatisfy(m -> assertThat(m.expectedBase()).isEmpty());
        assertThat(thirty.scenario()).contains("RETENTION");
        // 202703: 예정 300,001 × 30 / 100 = 90,000.3 → 90,000(내림), 보류 100,000 그대로
        assertThat(thirty.months()).extracting(DeferralsResponse.Month::dueYm, DeferralsResponse.Month::expected, DeferralsResponse.Month::held,
                        DeferralsResponse.Month::expectedBase)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("202703", 90_000L, 100_000L, Optional.of(300_001L)),
                        org.assertj.core.groups.Tuple.tuple("202704", 180_000L, 0L, Optional.of(600_000L)),
                        org.assertj.core.groups.Tuple.tuple("202705", 0L, 0L, Optional.of(0L)));   // 취소분만 있는 달도 행(SQL GROUP BY와 같다)
        assertThat(thirty.items()).isEqualTo(all.items());
        assertThat(thirty.items()).extracting(DeferralsResponse.Item::amount).containsExactly(300_001L, 100_000L, 600_000L, 50_000L);
        // 막대는 응답 값 기준: 최댓값 = 202704의 180,000(적용 후) — 202703 90,000 + 100,000 = 190,000이 더 크다
        assertThat(thirty.months().getFirst().bar()).contains(new DeferralsResponse.Bar("47.4%", "52.6%"));
        assertThat(all.months().getFirst().bar()).contains(new DeferralsResponse.Bar("50.0%", "16.7%"));   // max = 202704 600,000

        DeferralsResponse hundred = queries.deferrals(a, "202701", "202712", Optional.of("RETENTION"), Optional.of("100"), Optional.empty()).body();
        assertThat(hundred.months()).allSatisfy(m -> assertThat(m.expectedBase()).contains(m.expected()));
        assertThat(hundred.months()).extracting(DeferralsResponse.Month::expected, DeferralsResponse.Month::held, DeferralsResponse.Month::bar)
                .isEqualTo(all.months().stream().map(m -> org.assertj.core.groups.Tuple.tuple(m.expected(), m.held(), m.bar())).toList());
    }

    @Test
    void theBarIsOmittedWhenNothingIsPositive() {
        Caller a = caller(A, Role.AGENT);
        store.deferralRows.clear();
        store.deferralRows.add(FakeStore.deferral("N1", A, POLICY_A, "202703", 0, DeferralStatus.SCHEDULED));
        assertThat(deferrals(queries, a, "202701", "202712").body().months()).singleElement().satisfies(m -> assertThat(m.bar()).isEmpty());
        assertThat(deferrals(queries, a, "203001", "203012").body().months()).isEmpty();
    }

    @Test
    void aDeferralPolicyKeyResolvesOnlyWithinTheCallersDeferrals() {
        Caller a = caller(A, Role.AGENT);
        DeferralsResponse one = queries.deferrals(a, "202701", "202712", Optional.empty(), Optional.empty(), Optional.of(key(POLICY_A))).body();
        assertThat(one.months()).extracting(DeferralsResponse.Month::dueYm, DeferralsResponse.Month::expected)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("202703", 300_001L));
        assertThat(one.items()).extracting(DeferralsResponse.Item::scheduleId).containsExactly("S1", "S2");
        store.calls.clear();
        DeferralsResponse others = queries.deferrals(a, "202701", "202712", Optional.empty(), Optional.empty(), Optional.of(key(POLICY_B))).body();
        assertThat(others.months()).isEmpty();
        assertThat(others.items()).isEmpty();
        assertThat(store.calls).containsExactly("deferralPolicies");
    }

    @Test
    void cr1To4AndTheFeatureSwitchAreCarried() {
        Caller a = caller(A, Role.AGENT);
        assertThat(queries.month(a, "202608").body().previous())
                .contains(new com.ga.portal.query.api.MonthResponse.Previous("202607", "PAID", 10, Optional.of(9L), Optional.of("PAYOUT")));
        store.calls.clear();
        queries.month(a, "202608");
        assertThat(store.calls).as("one month statement (CR-1 is a join)").containsExactly("monthDetail", "byType");
        assertThat(queries.limits(a, "ALL").body().items()).first().satisfies(l -> {
            assertThat(l.ruleVersionId()).contains("LIMIT_RULE:1");
            assertThat(l.productKey()).contains("WHOLE-LIFE-20Y");
            assertThat(l.productName()).contains("종신20");
            assertThat(l.insuredMasked()).contains("김*");
        });
        assertThat(queries.me(a).body().features()).contains(new com.ga.portal.query.api.MeResponse.Features(false));
        QueryResult<?> off = queries.me(a);
        snapshot = snapshot(Optional.of(NOW.minusSeconds(10)), true);
        QueryResult<com.ga.portal.query.api.MeResponse> on = queries.me(a);
        assertThat(on.body().features()).contains(new com.ga.portal.query.api.MeResponse.Features(true));
        assertThat(on.etag()).as("the switch is part of the ETag").isNotEqualTo(off.etag());
    }
}
