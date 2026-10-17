package com.ga.portal.query;

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
    final MeQueries queries = new MeQueries(store, () -> snapshot, (tenant, policyNo) -> key(policyNo), new ReadTransaction(),
            Clock.fixed(NOW, ZoneOffset.UTC), 300);

    /** 가짜 키(16자 16진, 결정적). */
    static String key(String policyNo) {
        return String.format("%016x", policyNo.hashCode() & 0xffffffffL);
    }

    static SyncSnapshot snapshot(Optional<Instant> caughtUp) {
        return new SyncSnapshot("시험 GA", "NEXT_MONTH_D10", 8000, 9500, Optional.empty(),
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

        FakeStore() {
            ledgers.put("1", item("1", A, POLICY_A, 2_250_000, 1_800_000));
            ledgers.put("2", item("2", B, POLICY_B, 2_250_000, 0));
            ledgers.put("3", item("3", A, "POL-ZERO-0003", 0, 0));
            lines.add(line("1", A, POLICY_A, "FY_COMM", 945_000));
            lines.add(line("2", A, "POL-2026-0002", "INCENTIVE", 855_000));
            lines.add(line("9", B, POLICY_B, "FY_COMM", 1));
        }

        static LedgerItem item(String id, AgentId agent, String policy, long limit, long accum) {
            return new LedgerItem(new LedgerRow(id, agent.value(), policy, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2027, 7, 31),
                    150_000, "15", limit, accum, 0, 0, 1, "LIMIT_RULE:1"), limit - accum, List.of());
        }

        static LineItem line(String calcId, AgentId agent, String policy, String type, long amount) {
            return new LineItem(calcId, "202608", type, amount, 0, Optional.empty(), Optional.of("P-" + agent.value()), Optional.empty(), Optional.empty(),
                    policy, Optional.empty(), Optional.empty(), Long.parseLong(calcId));
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
        public List<DeferralRow> deferrals(AgentId agent, String fromYm, String toYm) {
            calls.add("deferrals");
            return List.of();
        }

        @Override
        public List<DueMonth> dueMonths(AgentId agent, String fromYm, String toYm) {
            calls.add("dueMonths");
            return List.of();
        }
    }

    static List<Function<MeQueries, Object>> everyEntry(Caller c) {
        return List.of(q -> q.me(c), q -> q.earnings(c, "202601", "202612"), q -> q.month(c, "202608"),
                q -> q.lines(c, "202608", 0, 50, Optional.empty(), Optional.empty()), q -> q.line(c, "1"), q -> q.limits(c, "ALL"),
                q -> q.limit(c, "1"), q -> q.deferrals(c, "202601", "202612"));
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
                q -> q.earnings(a, "2026-1", "202612"), q -> q.deferrals(a, "202601", null),
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
}
