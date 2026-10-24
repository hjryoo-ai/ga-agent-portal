package com.ga.portal.query;

import com.ga.portal.domain.access.ApiAuthorization;
import com.ga.portal.domain.access.Caller;
import com.ga.portal.domain.access.DenyReason;
import com.ga.portal.domain.access.InvalidRequest;
import com.ga.portal.domain.access.NotFound;
import com.ga.portal.domain.access.UseCaseEntry;
import com.ga.platform.core.money.Ratio;
import com.ga.platform.core.time.Ym;
import com.ga.portal.domain.view.AgentRow;
import com.ga.portal.domain.view.DeferralRow;
import com.ga.portal.domain.view.HistoryRow;
import com.ga.portal.domain.view.LedgerRow;
import com.ga.portal.domain.view.DueMonth;
import com.ga.portal.domain.view.MonthDetail;
import com.ga.portal.domain.view.MonthRow;
import com.ga.portal.query.api.AsOf;
import com.ga.portal.query.api.DeferralsResponse;
import com.ga.portal.query.api.EarningsResponse;
import com.ga.portal.query.api.Ledger;
import com.ga.portal.query.api.LimitDetailResponse;
import com.ga.portal.query.api.LimitsResponse;
import com.ga.portal.query.api.Line;
import com.ga.portal.query.api.LineDetailResponse;
import com.ga.portal.query.api.LinesResponse;
import com.ga.portal.query.api.MeResponse;
import com.ga.portal.query.api.MonthResponse;
import com.ga.portal.query.api.MonthSummary;
import com.ga.portal.query.port.AgentQueryStore;
import com.ga.portal.domain.view.LedgerItem;
import com.ga.portal.domain.view.LineDetail;
import com.ga.portal.domain.view.LineItem;
import com.ga.portal.domain.view.LinePage;
import com.ga.portal.query.port.PolicyKeys;
import com.ga.portal.query.port.ReadTransaction;
import com.ga.portal.domain.view.SyncSnapshot;
import com.ga.portal.domain.rule.LimitStage;
import com.ga.portal.domain.rule.PolicyMask;
import com.ga.portal.query.port.SyncStatusStore;
import com.ga.portal.query.rule.Etags;
import com.ga.portal.query.rule.ForecastScenario;
import com.ga.portal.query.rule.PayoutDay;
import com.ga.portal.query.rule.SyncStatusRule;
import com.ga.portal.query.rule.SyncStatusRule.SyncStatus;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 설계사 본인 조회(지시문 D-5, 계획 ⑤) — 엔드포인트마다 진입점 하나({@link UseCaseEntry}). 진입점은 첫 줄에서 역할을 확인하고({@link ApiAuthorization}),
 * 한 읽기 전용 트랜잭션 안에서 테넌트 설정·커서 한 문장 + 본문 문장들을 읽는다. 모든 조회 키는 {@code (호출자의 agentId, 키)} — 남의 키는 "없다"(404 MISSING).
 *
 * <p>입력 규칙: 경로 변수(월·라인·원장)의 형식이 틀리면 404(존재 여부를 형식 오류로 흘리지 않는다), 쿼리 파라미터가 틀리면 400 한 가지(Q11).
 * 응답에 증권번호 원문은 없다 — {@code policyMasked}·{@code policyKey}(D-7·Q5). 금액은 뷰의 값 그대로이고 여기서 만드는 숫자는 {@code remaining}(SQL 차감)·
 * 단계(정수 bp 비교)·{@code utilization}·{@code bar}(표시 문자열)와 분급 시나리오의 {@code expected}({@link ForecastScenario} — 포털의 유일한 금액 곱셈,
 * 1.1.0)뿐이다.
 */
public final class MeQueries {

    static final int MAX_RANGE_MONTHS = 36;
    /** 분급 타임라인의 범위 상한(1.1.0 — 기간 토글 12/24/48/84개월). 다른 범위 API는 36 그대로. */
    static final int MAX_DEFERRAL_RANGE_MONTHS = 84;
    static final String ALL_INFORCE = "ALL_INFORCE";
    static final String RETENTION = "RETENTION";
    static final int MAX_PAGE_SIZE = 200;
    static final int MAX_PAGE = 1_000_000;
    static final int MAX_KEY_LENGTH = 128;
    private static final Pattern COMM_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");
    private static final Pattern POLICY_KEY = Pattern.compile("[0-9a-f]{16}");
    private static final Pattern YM = Pattern.compile("[0-9]{6}");
    private static final Pattern PERCENT = Pattern.compile("[1-9][0-9]?|100");
    private static final List<String> STAGE_FILTERS = List.of("ALL", "WARN", "CRIT", "FULL");
    private static final System.Logger LOG = System.getLogger(MeQueries.class.getName());

    private final AgentQueryStore store;
    private final SyncStatusStore sync;
    private final PolicyKeys policyKeys;
    private final ReadTransaction transaction;
    private final Clock clock;
    private final long defaultSyncDelaySeconds;

    public MeQueries(AgentQueryStore store, SyncStatusStore sync, PolicyKeys policyKeys, ReadTransaction transaction, Clock clock,
                     long defaultSyncDelaySeconds) {
        this.store = Objects.requireNonNull(store, "store");
        this.sync = Objects.requireNonNull(sync, "sync");
        this.policyKeys = Objects.requireNonNull(policyKeys, "policyKeys");
        this.transaction = Objects.requireNonNull(transaction, "transaction");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (defaultSyncDelaySeconds < 1) {
            throw new IllegalArgumentException("default sync delay must be positive");
        }
        this.defaultSyncDelaySeconds = defaultSyncDelaySeconds;
    }

    // ------------------------------------------------------------------ 진입점

    @UseCaseEntry(ApiAuthorization.ME)
    public QueryResult<MeResponse> me(Caller caller) {
        ApiAuthorization.require(caller, ApiAuthorization.ME);
        return transaction.read(() -> {
            Context c = context();
            Optional<AgentRow> profile = store.profile(caller.agentId());
            SyncSnapshot s = c.snapshot;
            MeResponse body = new MeResponse(c.asOf(), c.status.name(), caller.agentId().value(), caller.role().name(),
                    profile.map(AgentRow::name), profile.flatMap(AgentRow::grade), profile.map(AgentRow::orgPath),
                    profile.map(p -> p.status().name()), Optional.of(new MeResponse.Features(s.forecastScenarioEnabled())),
                    new MeResponse.Tenant(s.tenantName(), s.payoutDayRule(), s.limitWarnBp(), s.limitCritBp()));
            return new QueryResult<>(body, c.etag(s.tenantName(), s.payoutDayRule(), Long.toString(s.limitWarnBp()), Long.toString(s.limitCritBp()),
                    Boolean.toString(s.forecastScenarioEnabled())));
        });
    }

    @UseCaseEntry(ApiAuthorization.EARNINGS)
    public QueryResult<EarningsResponse> earnings(Caller caller, String from, String to) {
        ApiAuthorization.require(caller, ApiAuthorization.EARNINGS);
        Ym[] range = range(from, to);
        return transaction.read(() -> {
            Context c = context();
            List<MonthSummary> items = store.months(caller.agentId(), range[0].value(), range[1].value()).stream().map(MeQueries::summary).toList();
            return new QueryResult<>(new EarningsResponse(c.asOf(), c.status.name(), range[0].value(), range[1].value(), items), c.etag());
        });
    }

    @UseCaseEntry(ApiAuthorization.MONTH)
    public QueryResult<MonthResponse> month(Caller caller, String ym) {
        ApiAuthorization.require(caller, ApiAuthorization.MONTH);
        Ym closeYm = pathYm(ym);
        return transaction.read(() -> {
            Context c = context();
            MonthDetail detail = store.monthDetail(caller.agentId(), closeYm.value(), closeYm.prev().value())
                    .orElseThrow(() -> new NotFound(DenyReason.MISSING));
            MonthRow m = detail.row();
            PayoutDay.Result payout = PayoutDay.expected(c.snapshot.payoutDayRule(), closeYm);
            payout.omitted().ifPresent(reason -> LOG.log(System.Logger.Level.WARNING,
                    "PAYOUT_DAY_OMITTED tenant=" + caller.tenant().value() + " reason=" + reason + " ym=" + closeYm.value()));
            MonthResponse body = new MonthResponse(c.asOf(), c.status.name(), m.closeYm(), m.status().name(), m.dataStatus().name(), m.gross(),
                    m.clawbackOffset(), m.adjustment(), m.withholdingTax(), m.net(), m.netBasis().map(Enum::name),
                    store.byType(caller.agentId(), closeYm.value()), m.estimateAsOf(), m.estimateBasisSeq(), m.confirmedAt(), m.receivableOffset(),
                    m.carriedReceivable(), m.payable(), m.runSeq(), m.paidAmount(), m.paidAt(), payout.date(),
                    detail.previous().map(pm -> new MonthResponse.Previous(pm.closeYm(), pm.status().name(), pm.gross(), pm.net(),
                            pm.netBasis().map(Enum::name))));
            return new QueryResult<>(body, c.etag(c.snapshot.payoutDayRule()));
        });
    }

    @UseCaseEntry(ApiAuthorization.LINES)
    public QueryResult<LinesResponse> lines(Caller caller, String ym, int page, int size, Optional<String> commType, Optional<String> policyKey) {
        ApiAuthorization.require(caller, ApiAuthorization.LINES);
        Ym closeYm = pathYm(ym);
        if (page < 0 || page > MAX_PAGE || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequest("page/size");
        }
        if (commType.isPresent() && !COMM_TYPE.matcher(commType.get()).matches()) {
            throw new InvalidRequest("commType");
        }
        if (policyKey.isPresent() && !POLICY_KEY.matcher(policyKey.get()).matches()) {
            throw new InvalidRequest("policyKey");
        }
        return transaction.read(() -> {
            Context c = context();
            // 월이 없으면(남의 월 포함) 404 — 빈 목록이 아니다(월 상세와 같은 뜻)
            store.month(caller.agentId(), closeYm.value()).orElseThrow(() -> new NotFound(DenyReason.MISSING));
            Optional<String> policyNo = Optional.empty();
            boolean unknownKey = false;
            if (policyKey.isPresent()) {
                // 키 → 원문은 요청 안에서 그 설계사의 그 달 증권번호 집합으로만(SQL에 키는 없다 — Q5)
                policyNo = store.linePolicies(caller.agentId(), closeYm.value()).stream()
                        .filter(p -> policyKeys.key(caller.tenant(), p).equals(policyKey.get())).findFirst();
                unknownKey = policyNo.isEmpty();
            }
            LinePage result = unknownKey ? new LinePage(List.of(), 0)
                    : store.lines(caller.agentId(), closeYm.value(), commType, policyNo, page, size);
            LinesResponse body = new LinesResponse(c.asOf(), c.status.name(), closeYm.value(), page, size, result.total(),
                    result.items().stream().map(l -> line(caller, l)).toList());
            return new QueryResult<>(body, c.pageEtag(page, size, List.of(commType.orElse(""), policyKey.orElse(""))));
        });
    }

    @UseCaseEntry(ApiAuthorization.LINE)
    public QueryResult<LineDetailResponse> line(Caller caller, String calcId) {
        ApiAuthorization.require(caller, ApiAuthorization.LINE);
        String key = pathKey(calcId);
        return transaction.read(() -> {
            Context c = context();
            LineDetail d = store.line(caller.agentId(), key).orElseThrow(() -> new NotFound(DenyReason.MISSING));
            List<LineDetailResponse.Deferral> deferrals = store.deferralsOfLine(caller.agentId(), key).stream()
                    .map(r -> new LineDetailResponse.Deferral(r.scheduleId(), r.dueYm(), r.amount(), r.status().name())).toList();
            Optional<LineDetailResponse.Receivable> receivable = store.receivableOfLine(caller.agentId(), key)
                    .map(r -> new LineDetailResponse.Receivable(r.receivableId(), r.amount(), r.remaining(), r.status().name()));
            LineDetailResponse body = new LineDetailResponse(c.asOf(), c.status.name(), line(caller, d.line()),
                    new LineDetailResponse.Basis(d.rate(), d.payoutRate(), d.installmentNo(), d.ruleVersionIds()),
                    new LineDetailResponse.PolicySummary(d.contractDate(), d.monthlyPremium()), deferrals, receivable,
                    store.ledgerIdOfPolicy(caller.agentId(), d.line().policyNo()));
            return new QueryResult<>(body, c.etag());
        });
    }

    @UseCaseEntry(ApiAuthorization.LIMITS)
    public QueryResult<LimitsResponse> limits(Caller caller, String status) {
        ApiAuthorization.require(caller, ApiAuthorization.LIMITS);
        if (!STAGE_FILTERS.contains(status)) {
            throw new InvalidRequest("status");
        }
        return transaction.read(() -> {
            Context c = context();
            List<Ledger> items = new ArrayList<>();
            for (LedgerItem l : store.ledgers(caller.agentId())) {
                Ledger card = ledger(caller, l, c.snapshot);
                if (status.equals("ALL") || card.stage().equals(status)) {
                    items.add(card);
                }
            }
            return new QueryResult<>(new LimitsResponse(c.asOf(), c.status.name(), status, items), c.etag(thresholds(c.snapshot)));
        });
    }

    @UseCaseEntry(ApiAuthorization.LIMIT)
    public QueryResult<LimitDetailResponse> limit(Caller caller, String ledgerId) {
        ApiAuthorization.require(caller, ApiAuthorization.LIMIT);
        String key = pathKey(ledgerId);
        return transaction.read(() -> {
            Context c = context();
            LedgerItem l = store.ledger(caller.agentId(), key).orElseThrow(() -> new NotFound(DenyReason.MISSING));
            List<LimitDetailResponse.History> history = store.history(caller.agentId(), key).stream().map(MeQueries::history).toList();
            return new QueryResult<>(new LimitDetailResponse(c.asOf(), c.status.name(), ledger(caller, l, c.snapshot), history),
                    c.etag(thresholds(c.snapshot)));
        });
    }

    /**
     * 분급 타임라인(1.1.0): 범위 84개월까지, {@code scenario}(기본 {@code ALL_INFORCE})·{@code p}(RETENTION일 때만 필수 1~100)·{@code policyKey}(그 설계사의 분급
     * 항목 증권번호 집합에서 푼다 — 모르는 키는 빈 결과). RETENTION은 테넌트가 켰을 때만(아니면 400). RETENTION이면 월 {@code expected}만
     * {@link ForecastScenario}로 바꾸고 적용 전 값을 {@code expectedBase}로 함께 싣는다 — {@code held}·항목은 원값. 막대({@code bar})는 응답에 실린 값 기준
     * (월마다 {@code expected + held}의 최댓값 대비, 최댓값 ≤ 0이면 생략).
     */
    @UseCaseEntry(ApiAuthorization.DEFERRALS)
    public QueryResult<DeferralsResponse> deferrals(Caller caller, String from, String to, Optional<String> scenario, Optional<String> p,
                                                    Optional<String> policyKey) {
        ApiAuthorization.require(caller, ApiAuthorization.DEFERRALS);
        Ym[] range = range(from, to, MAX_DEFERRAL_RANGE_MONTHS);
        String chosen = scenario.orElse(ALL_INFORCE);
        if (!chosen.equals(ALL_INFORCE) && !chosen.equals(RETENTION)) {
            throw new InvalidRequest("scenario");
        }
        if (chosen.equals(RETENTION) != p.isPresent() || p.isPresent() && !PERCENT.matcher(p.get()).matches()) {
            throw new InvalidRequest("p");
        }
        if (policyKey.isPresent() && !POLICY_KEY.matcher(policyKey.get()).matches()) {
            throw new InvalidRequest("policyKey");
        }
        Optional<Integer> percent = p.map(Integer::valueOf);
        return transaction.read(() -> {
            Context c = context();
            if (chosen.equals(RETENTION) && !c.snapshot.forecastScenarioEnabled()) {
                throw new InvalidRequest("scenario");
            }
            Optional<String> policyNo = Optional.empty();
            boolean unknownKey = false;
            if (policyKey.isPresent()) {
                // 키 → 원문은 요청 안에서 그 설계사의 분급 항목 증권번호 집합으로만(SQL에 키는 없다 — /lines와 같은 규약)
                policyNo = store.deferralPolicies(caller.agentId()).stream()
                        .filter(n -> policyKeys.key(caller.tenant(), n).equals(policyKey.get())).findFirst();
                unknownKey = policyNo.isEmpty();
            }
            List<DueMonth> sums = unknownKey ? List.of() : store.dueMonths(caller.agentId(), range[0].value(), range[1].value(), policyNo);
            List<DeferralsResponse.Item> items = unknownKey ? List.of()
                    : store.deferrals(caller.agentId(), range[0].value(), range[1].value(), policyNo).stream().map(r -> deferral(caller, r)).toList();
            DeferralsResponse body = new DeferralsResponse(c.asOf(), c.status.name(), range[0].value(), range[1].value(), Optional.of(chosen), percent,
                    timeline(sums, percent), items);
            return new QueryResult<>(body, c.etag(Boolean.toString(c.snapshot.forecastScenarioEnabled())));
        });
    }

    /** 월 합 → 응답 월: 시나리오 적용(있으면)과 막대. 막대의 분모는 응답에 실린 {@code expected + held}의 최댓값. */
    private static List<DeferralsResponse.Month> timeline(List<DueMonth> sums, Optional<Integer> percent) {
        List<long[]> shown = sums.stream()
                .map(m -> new long[] {percent.map(pp -> ForecastScenario.apply(m.expected(), pp)).orElse(m.expected()), m.held()}).toList();
        long max = shown.stream().mapToLong(v -> Math.addExact(v[0], v[1])).max().orElse(0L);
        List<DeferralsResponse.Month> out = new ArrayList<>();
        for (int i = 0; i < sums.size(); i++) {
            DueMonth m = sums.get(i);
            long[] v = shown.get(i);
            Optional<DeferralsResponse.Bar> bar = max > 0
                    ? Optional.of(new DeferralsResponse.Bar(Ratio.of(v[0], max).toPercentString(), Ratio.of(v[1], max).toPercentString()))
                    : Optional.empty();
            out.add(new DeferralsResponse.Month(m.dueYm(), v[0], m.held(), percent.map(pp -> m.expected()), bar));
        }
        return out;
    }

    // ------------------------------------------------------------------ 공통

    /** 요청 하나의 동기화 문맥: 설정·커서 한 문장 → 상태·asOf·ETag 재료. */
    private final class Context {
        final SyncSnapshot snapshot;
        final long delaySeconds;
        final SyncStatus status;

        Context(SyncSnapshot snapshot) {
            this.snapshot = snapshot;
            this.delaySeconds = snapshot.configuredDelaySeconds().orElse(defaultSyncDelaySeconds);
            this.status = SyncStatusRule.evaluate(snapshot.cursor().map(SyncSnapshot.Cursor::state),
                    snapshot.cursor().flatMap(SyncSnapshot.Cursor::caughtUpAt), delaySeconds, clock.instant());
        }

        AsOf asOf() {
            return new AsOf(seq(), snapshot.cursor().map(SyncSnapshot.Cursor::updatedAt));
        }

        long seq() {
            return snapshot.cursor().map(SyncSnapshot.Cursor::lastSeq).orElse(0L);
        }

        /** {@code cfg} 입력 = 그 요청이 읽은 설정 + 지연 초(모든 요청이 읽는다 — 계획 ⑤ 표). */
        String etag(String... configRead) {
            return Etags.of(seq(), status, config(configRead));
        }

        String pageEtag(int page, int size, List<String> filter) {
            return Etags.ofPage(seq(), status, config(), page, size, filter);
        }

        private List<String> config(String... configRead) {
            List<String> all = new ArrayList<>(List.of(configRead));
            all.add(Long.toString(delaySeconds));
            return all;
        }
    }

    private Context context() {
        return new Context(sync.snapshot());
    }

    private static String[] thresholds(SyncSnapshot s) {
        return new String[] {Long.toString(s.limitWarnBp()), Long.toString(s.limitCritBp())};
    }

    private static Ym[] range(String from, String to) {
        return range(from, to, MAX_RANGE_MONTHS);
    }

    private static Ym[] range(String from, String to, int maxMonths) {
        if (from == null || to == null || !YM.matcher(from).matches() || !YM.matcher(to).matches()) {
            throw new InvalidRequest("from/to");
        }
        Ym f;
        Ym t;
        try {
            f = Ym.parse(from);
            t = Ym.parse(to);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequest("from/to");
        }
        if (f.compareTo(t) > 0 || Ym.range(f, t).size() > maxMonths) {
            throw new InvalidRequest("range");
        }
        return new Ym[] {f, t};
    }

    private static Ym pathYm(String ym) {
        try {
            return Ym.parse(ym);
        } catch (IllegalArgumentException e) {
            throw new NotFound(DenyReason.MISSING);
        }
    }

    private static String pathKey(String key) {
        if (key == null || key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
            throw new NotFound(DenyReason.MISSING);
        }
        return key;
    }

    private static MonthSummary summary(MonthRow m) {
        return new MonthSummary(m.closeYm(), m.status().name(), m.dataStatus().name(), m.gross(), m.clawbackOffset(), m.adjustment(),
                m.withholdingTax(), m.net(), m.netBasis().map(Enum::name), m.paidAmount(), m.paidAt());
    }

    private Line line(Caller caller, LineItem l) {
        return new Line(l.calcId(), l.closeYm(), l.commType(), l.amount(), l.limitCutAmt(), l.limitCutPolicy(), l.productKey(), l.productName(),
                l.insuredMasked(), PolicyMask.mask(l.policyNo()), policyKeys.key(caller.tenant(), l.policyNo()), l.reversalOf(), l.reversedBy(),
                l.reversalOfYm(), l.reversedByYm());
    }

    private Ledger ledger(Caller caller, LedgerItem item, SyncSnapshot s) {
        LedgerRow r = item.ledger();
        LimitStage stage = LimitStage.of(r.accumPaid(), r.limitAmount(), item.remaining(), s.limitWarnBp(), s.limitCritBp());
        Optional<String> utilization = r.limitAmount() > 0 ? Optional.of(Ratio.of(r.accumPaid(), r.limitAmount()).toPercentString()) : Optional.empty();
        return new Ledger(r.ledgerId(), PolicyMask.mask(r.policyNo()), policyKeys.key(caller.tenant(), r.policyNo()), r.contractDate(), r.fyStart(),
                r.fyEnd(), r.monthlyPremium(), r.limitMultiple(), r.limitAmount(), r.accumPaid(), item.remaining(), utilization, stage.name(),
                r.cutTotal(), r.deferredTotal(), item.openFlags(), Optional.of(r.ruleVersionId()), item.productKey(), item.productName(),
                item.insuredMasked());
    }

    private static LimitDetailResponse.History history(HistoryRow h) {
        return new LimitDetailResponse.History(h.sourceSeq(), h.kind(), h.postingSeq(), h.calcId(), h.amount(), h.accumAfter(), h.limitAfter(),
                h.reason(), h.occurredAt());
    }

    private DeferralsResponse.Item deferral(Caller caller, DeferralRow r) {
        return new DeferralsResponse.Item(r.scheduleId(), r.dueYm(), r.amount(), r.status().name(), r.statusReason(), r.reason(), r.payCondition(),
                PolicyMask.mask(r.policyNo()), policyKeys.key(caller.tenant(), r.policyNo()), r.sourceCalcId());
    }
}
