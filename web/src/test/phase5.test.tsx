// 포털 Phase 5 화면 시험(C10): 타임라인(막대 = API 문자열, 표·라벨, HELD 범례, 시나리오 토글·p 규칙, 기간·계약 필터), 시뮬레이션(고정 문구, 모든 금액
// "시뮬레이션" 라벨, 룰 스냅샷·요청 번호·시각, 문자열 그대로의 요청 본문, 규칙 파일로 미리 막기, 422·429·503·502 — 마지막 결과 유지, 보험사 입력란,
// 변경 유형 다섯, 탭 키보드), 홈(다음 달 분급·전월), 한도 상세 → 해약 영향 인계. 금액에는 늘 라벨이 있다.
import { QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createQueryClient, type Schemas } from '../api/queries';
import { bootToken, dropToken } from '../boot/token';
import { HomeScreen } from '../screens/HomeScreen';
import { LimitScreen } from '../screens/LimitScreen';
import { SimulationScreen } from '../screens/SimulationScreen';
import { TimelineScreen } from '../screens/TimelineScreen';
import { asOf, ledger, month, won } from './fixtures';

interface Reply {
  status: number;
  body: unknown;
  headers?: Record<string, string>;
}

const net = vi.hoisted(() => {
  const gets = new Map<string, unknown>();
  const posts = new Map<string, Reply[]>();
  const calls: { method: string; url: string; body: string }[] = [];
  globalThis.fetch = async (input: RequestInfo | URL) => {
    const request = input instanceof Request ? input : new Request(String(input));
    const url = new URL(request.url, 'http://portal.test');
    const path = url.pathname + url.search;
    const body = request.method === 'POST' ? await request.text() : '';
    calls.push({ method: request.method, url: path, body });
    const problem = (status: number, code: string, headers: Record<string, string> = {}) =>
      new Response(JSON.stringify({ code, message: 'x' }), { status, headers: { 'Content-Type': 'application/problem+json', ...headers } });
    if (request.method === 'POST') {
      const reply = posts.get(url.pathname)?.shift();
      if (reply === undefined) return problem(404, 'NOT_FOUND');
      const type = reply.status === 200 ? 'application/json' : 'application/problem+json';
      return new Response(JSON.stringify(reply.body), { status: reply.status, headers: { 'Content-Type': type, ...reply.headers } });
    }
    const found = gets.get(path) ?? gets.get(url.pathname);
    return found === undefined ? problem(404, 'NOT_FOUND') : new Response(JSON.stringify(found), { status: 200, headers: { 'Content-Type': 'application/json' } });
  };
  return { gets, posts, calls };
});

const NEW = '/api/v1/me/simulations/new-contract';
const EVENT = '/api/v1/me/simulations/policy-event';
const KEY = '0123456789abcdef';
const SIM_ID = '5f0e9a52-3c1d-4f7b-9d4e-2a6b8c0d1e2f';

beforeEach(() => {
  bootToken({ hash: '#token=a.b.c', pathname: '/', search: '' }, { replaceState: () => undefined });
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2027-02-15T00:30:00Z')); // 2027-02-15 09:30 KST(엔진 샘플의 시계)
});

afterEach(() => {
  cleanup();
  net.gets.clear();
  net.posts.clear();
  net.calls.length = 0;
  dropToken();
  vi.useRealTimers();
});

function show(path: string, pattern: string, element: ReactNode, extra: { path: string; element: ReactNode }[] = []) {
  return render(
    <QueryClientProvider client={createQueryClient()}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path={pattern} element={element} />
          {extra.map((r) => (
            <Route key={r.path} path={r.path} element={r.element} />
          ))}
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function labelsOf(container: HTMLElement): string[] {
  const amounts = [...container.querySelectorAll('.amount')];
  expect(amounts.length).toBeGreaterThan(0);
  return amounts.map((a) => a.querySelector('.amount__label')?.textContent ?? '');
}

const me = (forecastScenario: boolean) => ({ asOf, dataStatus: 'OK', agentId: 'A-1', agentName: 'x', role: 'AGENT', features: { forecastScenario } });
const limits = { asOf, dataStatus: 'OK', status: 'ALL', items: [ledger({ productKey: 'WHOLE-LIFE-20Y', productName: '종신20', insuredMasked: '김*수' })] };

function deferrals(over: Partial<Schemas['DeferralsResponse']> = {}): Schemas['DeferralsResponse'] {
  return {
    asOf,
    dataStatus: 'OK',
    from: '202702',
    to: '202801',
    scenario: 'ALL_INFORCE',
    months: [
      { dueYm: '202703', expected: won(40000), held: won(0), bar: { expected: '40.0%', held: '0.0%' } },
      { dueYm: '202709', expected: won(60000), held: won(40000), bar: { expected: '60.0%', held: '40.0%' } },
    ],
    items: [
      { scheduleId: 'D1', sourceCalcId: 'C1', policyKey: KEY, policyMasked: '*********0001', dueYm: '202703', amount: won(40000), reason: 'CURVE', status: 'SCHEDULED', payCondition: 'INFORCE' },
      { scheduleId: 'D2', sourceCalcId: 'C1', policyKey: KEY, policyMasked: '*********0001', dueYm: '202709', amount: won(40000), reason: 'CURVE', status: 'HELD', statusReason: 'POLICY_NOT_INFORCE', payCondition: 'INFORCE' },
      { scheduleId: 'D3', sourceCalcId: 'C1', policyKey: KEY, policyMasked: '*********0001', dueYm: '202709', amount: won(60000), reason: 'LIMIT_DEFER', status: 'SCHEDULED', payCondition: 'INFORCE' },
    ],
    ...over,
  };
}

const newResult = {
  asOf,
  dataStatus: 'OK',
  simId: SIM_ID,
  requestedAt: '2027-02-15T00:30:01.123456Z',
  generatedAt: '2027-02-15T00:30:01Z',
  ruleSnapshotId: 'RS-2027-02',
  fyCommissionByInstallment: [
    { no: 1, amount: won(945000) },
    { no: 2, amount: won(0) },
  ],
  limitAmount: won(1800000),
  projectedAccum: won(1800000),
  incentives: [{ code: 'INC-01', amount: won(855000), limitIncluded: true, limitCutAmt: won(145000) }],
  deferral: { firstYearPayout: won(1800000), schedule: [{ dueYm: '202708', amount: won(145000) }] },
};

const eventResult = {
  asOf,
  dataStatus: 'OK',
  simId: SIM_ID,
  requestedAt: '2027-02-15T00:30:01.123456Z',
  ruleSnapshotId: 'RS-2027-02',
  clawbackAmount: won(-720000),
  offsetAgainstMonth: { closeYm: '202702', before: won(1800000), after: won(1080000) },
  cancelledDeferrals: [{ scheduleId: 'D1', dueYm: '202703', amount: won(40000) }],
  receivableRemaining: won(0),
};

const posted = (path: string) => net.calls.filter((c) => c.method === 'POST' && c.url === path).map((c) => JSON.parse(c.body) as Record<string, unknown>);

describe('timeline', () => {
  it('bars carry the API strings, the table labels each amount, HELD has its legend and the fixed note is there', async () => {
    net.gets.set('/api/v1/me', me(false));
    net.gets.set('/api/v1/me/limits?status=ALL', limits);
    net.gets.set('/api/v1/me/deferrals?from=202702&to=202801', deferrals());
    const { container } = show('/deferrals', '/deferrals', <TimelineScreen />);
    const bars = await screen.findAllByTestId('timeline-bar');
    expect(screen.getByTestId('timeline-note').textContent).toBe('분급 확정 스케줄만 반영 · 참고치 · 비확정');
    expect(screen.getByTestId('held-legend').textContent).toBe('보류: 지급 조건 미충족으로 멈춘 분급 — 예정 합계에 넣지 않음');
    expect(bars.map((b) => [b.style.getPropertyValue('--bar-expected'), b.style.getPropertyValue('--bar-held')])).toEqual([
      ['40.0%', '0.0%'],
      ['60.0%', '40.0%'],
    ]);
    // 막대의 이름 = 표의 월·예정·보류 칸(금액은 Amount로만)
    const ids = (bars[1]?.getAttribute('aria-labelledby') ?? '').split(' ');
    expect(ids.map((id) => document.getElementById(id)?.textContent)).toEqual(['2027년 9월', '60,000원분급 예정', '40,000원보류']);
    const rows = screen.getAllByTestId('timeline-month');
    expect(rows.map((r) => r.getAttribute('data-ym'))).toEqual(['202703', '202709']);
    expect(screen.queryByTestId('month-expected-base')).toBeNull();
    expect(screen.queryByTestId('timeline-scenario')).toBeNull();
    const items = within(screen.getByTestId('timeline-items'));
    expect(items.getAllByTestId('timeline-item').map((i) => i.querySelector('.amount__label')?.textContent)).toEqual(['예정', '보류', '예정']);
    expect(new Set(labelsOf(container))).toEqual(new Set(['분급 예정', '보류', '예정']));
  });

  it('retention: the toggle exists only when the tenant enabled it; only applied totals take the assumption label; p is checked by the contract rule', async () => {
    net.gets.set('/api/v1/me', me(true));
    net.gets.set('/api/v1/me/limits?status=ALL', limits);
    net.gets.set('/api/v1/me/deferrals?from=202702&to=202801', deferrals());
    net.gets.set(
      '/api/v1/me/deferrals?from=202702&to=202801&scenario=RETENTION&p=30',
      deferrals({
        scenario: 'RETENTION',
        p: 30,
        months: [
          { dueYm: '202703', expected: won(12000), expectedBase: won(40000), held: won(0), bar: { expected: '12.0%', held: '0.0%' } },
          { dueYm: '202709', expected: won(18000), expectedBase: won(60000), held: won(40000), bar: { expected: '18.0%', held: '40.0%' } },
        ],
      }),
    );
    show('/deferrals', '/deferrals', <TimelineScreen />);
    await screen.findAllByTestId('timeline-bar');
    fireEvent.click(await screen.findByTestId('scenario-toggle'));
    const p = screen.getByTestId('scenario-p');
    for (const bad of ['0', '101', '3.5', '030', '']) {
      fireEvent.change(p, { target: { value: bad } });
      expect(screen.getByTestId('scenario-p-error').textContent).toBe('1~100 사이의 정수를 입력해 주세요');
    }
    expect(net.calls.some((c) => c.url.includes('scenario='))).toBe(false);
    fireEvent.change(p, { target: { value: '30' } });
    await waitFor(() => {
      expect(screen.getAllByTestId('month-expected-base')).toHaveLength(2);
    });
    const row = screen.getAllByTestId('timeline-month')[1];
    expect(row?.querySelector('[data-testid="month-expected"] .amount')?.textContent).toBe('18,000원참고(유지율 30% 가정)');
    expect(row?.querySelector('[data-testid="month-expected-base"] .amount')?.textContent).toBe('60,000원분급 예정');
    expect(row?.querySelector('[data-testid="month-held"] .amount')?.textContent).toBe('40,000원보류');
    expect(within(screen.getByTestId('timeline-items')).getAllByTestId('timeline-item')[1]?.querySelector('.amount__label')?.textContent).toBe('보류');
  });

  it('period and contract filter go to the query (84 months, policyKey), never into the address', async () => {
    net.gets.set('/api/v1/me', me(false));
    net.gets.set('/api/v1/me/limits?status=ALL', limits);
    net.gets.set('/api/v1/me/deferrals?from=202702&to=202801', deferrals());
    net.gets.set('/api/v1/me/deferrals?from=202702&to=203401', deferrals({ to: '203401' }));
    net.gets.set(`/api/v1/me/deferrals?from=202702&to=203401&policyKey=${KEY}`, deferrals({ to: '203401', months: [], items: [] }));
    show('/deferrals', '/deferrals', <TimelineScreen />);
    await screen.findAllByTestId('timeline-bar');
    fireEvent.click(screen.getByTestId('period-84'));
    await waitFor(() => {
      expect(net.calls.map((c) => c.url)).toContain('/api/v1/me/deferrals?from=202702&to=203401');
    });
    const group = screen.getByTestId('timeline-policy');
    await waitFor(() => {
      expect(within(group).getAllByRole('button').map((o) => o.textContent)).toEqual(['전체 계약', '종신20 · *********0001']);
    });
    expect(within(group).getByRole('button', { name: '전체 계약' }).getAttribute('aria-pressed')).toBe('true');
    fireEvent.click(within(group).getByRole('button', { name: '종신20 · *********0001' }));
    expect((await screen.findByTestId('timeline-empty')).textContent).toBe('이 기간에 분급 예정이 없습니다');
  });
});

describe('simulation', () => {
  async function fillNew(values: Record<string, string>) {
    const form = await screen.findByTestId('form-new');
    for (const [name, value] of Object.entries(values)) {
      const input = within(form).getByTestId(`field-${name}`).querySelector('input');
      if (input === null) throw new Error(name);
      fireEvent.change(input, { target: { value } });
    }
    fireEvent.submit(form);
  }

  it('new contract: strings sent as typed, every amount labelled 시뮬레이션, fixed note, rule snapshot, request id and both times', async () => {
    net.gets.set('/api/v1/me/limits?status=ALL', limits);
    net.posts.set(NEW, [{ status: 200, body: newResult }]);
    const { container } = show('/simulations', '/simulations', <SimulationScreen />);
    expect(screen.getByTestId('simulation-note').textContent).toBe('설계사 참고용 · 비확정 · 고객 제시 불가');
    expect(within(screen.getByTestId('form-new')).getByTestId('field-contractDate').querySelector('input')?.value).toBe('2027-02-15');
    await fillNew({ productKey: 'WHOLE-LIFE-20Y', monthlyPremium: '150000' });
    const result = await screen.findByTestId('sim-result-new');
    expect(posted(NEW)).toEqual([{ productKey: 'WHOLE-LIFE-20Y', monthlyPremium: '150000', contractDate: '2027-02-15' }]);
    expect(within(result).getByTestId('sim-note').textContent).toBe('설계사 참고용 · 비확정 · 고객 제시 불가');
    expect(within(result).getByTestId('sim-rule-snapshot').textContent).toBe('룰 스냅샷 RS-2027-02');
    expect(within(result).getByTestId('sim-id').textContent).toBe(`요청 번호 ${SIM_ID}`);
    expect(within(result).getByTestId('sim-requested-at').textContent).toBe('요청 시각 02-15 09:30');
    expect(within(result).getByTestId('sim-generated-at').textContent).toBe('엔진 산출 시각 02-15 09:30');
    expect(within(result).getByTestId('sim-limit-amount').textContent).toContain('1,800,000원');
    expect(within(result).getByTestId('sim-projected-accum').textContent).toContain('1,800,000원');
    expect(result.textContent).not.toContain('%');
    expect(new Set(labelsOf(container))).toEqual(new Set(['시뮬레이션']));
  });

  it('the contract rules stop bad input before any request', async () => {
    net.gets.set('/api/v1/me/limits?status=ALL', limits);
    show('/simulations', '/simulations', <SimulationScreen />);
    await fillNew({ productKey: '', monthlyPremium: '150,000', paymentTermMonths: '0', contractDate: '2027-13-01' });
    expect(screen.getByTestId('error-productKey').textContent).toBe('상품을(를) 입력해 주세요');
    expect(screen.getByTestId('error-monthlyPremium').textContent).toBe('월납 보험료(원) 형식을 확인해 주세요');
    expect(screen.getByTestId('error-paymentTermMonths')).toBeTruthy();
    expect(screen.getByTestId('error-contractDate')).toBeTruthy();
    expect(posted(NEW)).toEqual([]);
  });

  it('422 INSURER_AMBIGUOUS opens the insurer field; the retry carries insurerCd', async () => {
    net.gets.set('/api/v1/me/limits?status=ALL', limits);
    net.posts.set(NEW, [
      { status: 422, body: { code: 'INSURER_AMBIGUOUS', message: 'insurer must be chosen' } },
      { status: 200, body: newResult },
    ]);
    show('/simulations', '/simulations', <SimulationScreen />);
    expect(screen.queryByTestId('field-insurerCd')).toBeNull();
    await fillNew({ productKey: 'WHOLE-LIFE-20Y', monthlyPremium: '150000' });
    expect((await screen.findByTestId('sim-error')).textContent).toBe('같은 상품 키를 가진 보험사가 둘 이상입니다 — 보험사 코드를 입력해 주세요');
    await fillNew({ insurerCd: 'SAMLIFE' });
    await screen.findByTestId('sim-result-new');
    expect(posted(NEW)[1]).toEqual({ productKey: 'WHOLE-LIFE-20Y', monthlyPremium: '150000', contractDate: '2027-02-15', insurerCd: 'SAMLIFE' });
  });

  it('429, 503, 502 and other 422s keep the last result and say why (Retry-After as given)', async () => {
    net.gets.set('/api/v1/me/limits?status=ALL', limits);
    net.posts.set(NEW, [
      { status: 200, body: newResult },
      { status: 429, body: { code: 'RATE_LIMITED', message: 'too many simulations' }, headers: { 'Retry-After': '17' } },
      { status: 503, body: { code: 'ENGINE_UNAVAILABLE', message: 'engine unavailable' } },
      { status: 502, body: { code: 'ENGINE_CONTRACT', message: 'engine answer not usable' } },
      { status: 422, body: { code: 'ENGINE_REJECTED', message: 'rejected by the engine' } },
    ]);
    show('/simulations', '/simulations', <SimulationScreen />);
    await fillNew({ productKey: 'WHOLE-LIFE-20Y', monthlyPremium: '150000' });
    await screen.findByTestId('sim-result-new');
    const expected = [
      ['429', '잠시 후 다시 시도해 주세요(17초)'],
      ['503', '시뮬레이션을 지금 할 수 없습니다 — 조회는 가능'],
      ['502', '일시적 오류 — 운영팀 확인 중'],
      ['422', '엔진이 이 요청을 계산하지 않았습니다 — 입력을 확인해 주세요'],
    ];
    for (const [status, text] of expected) {
      fireEvent.submit(screen.getByTestId('form-new'));
      await waitFor(() => {
        expect(screen.getByTestId('sim-error').getAttribute('data-status')).toBe(status);
      });
      expect(screen.getByTestId('sim-error').textContent).toBe(text);
      expect(screen.getByTestId('sim-id').textContent).toBe(`요청 번호 ${SIM_ID}`);
    }
    expect(posted(NEW)).toHaveLength(5);
  });

  it('policy event: five event types (no PAYMENT_OVERDUE), REDUCE needs the new premium, before and after side by side without a difference', async () => {
    net.gets.set('/api/v1/me/limits?status=ALL', limits);
    net.posts.set(EVENT, [{ status: 200, body: eventResult }]);
    const { container } = show('/simulations', '/simulations', <SimulationScreen />);
    fireEvent.click(screen.getByTestId('tab-event'));
    const form = screen.getByTestId('form-event');
    const types = within(within(form).getByTestId('field-eventType')).getAllByRole('button');
    expect(types.map((o) => o.getAttribute('data-value'))).toEqual(['CANCEL', 'LAPSE', 'WITHDRAW', 'REDUCE', 'REINSTATE']);
    expect(types.map((o) => o.textContent)).toEqual(['해약', '실효', '청약 철회', '감액', '부활']);
    const policy = within(form).getByTestId('field-policyKey');
    // 고르지 않고 보내면 필수 오류
    fireEvent.submit(form);
    expect(screen.getByTestId('error-policyKey').textContent).toBe('계약을(를) 입력해 주세요');
    await waitFor(() => {
      expect(within(policy).getAllByRole('button')).toHaveLength(1);
    });
    fireEvent.click(within(policy).getByRole('button'));
    const pick = (value: string) => {
      const button = types.find((b) => b.getAttribute('data-value') === value);
      if (button === undefined) throw new Error(value);
      fireEvent.click(button);
    };
    pick('REDUCE');
    fireEvent.submit(form);
    expect(screen.getByTestId('error-newMonthlyPremium').textContent).toBe('감액 뒤 월납(원)을(를) 입력해 주세요');
    expect(posted(EVENT)).toEqual([]);
    pick('CANCEL');
    fireEvent.change(within(form).getByTestId('field-elapsedInstallments').querySelector('input') as HTMLInputElement, { target: { value: '7' } });
    fireEvent.submit(form);
    const result = await screen.findByTestId('sim-result-event');
    expect(posted(EVENT)).toEqual([{ policyKey: KEY, eventType: 'CANCEL', asOfDate: '2027-02-15', elapsedInstallments: '7' }]);
    expect(within(result).getByTestId('sim-offset-title').textContent).toBe('2027년 2월 세전·환수 상계 합계');
    expect(within(result).getByTestId('sim-offset-before').textContent).toBe('변경 전1,800,000원시뮬레이션');
    expect(within(result).getByTestId('sim-offset-after').textContent).toBe('변경 후1,080,000원시뮬레이션');
    expect(within(result).getByTestId('sim-offset').querySelectorAll('.amount')).toHaveLength(2);
    expect(result.textContent).not.toContain('실수령');
    expect(within(result).queryByTestId('sim-generated-at')).toBeNull();
    expect(new Set(labelsOf(container))).toEqual(new Set(['시뮬레이션']));
  });

  it('tabs: arrow keys move the selection and the focus', () => {
    show('/simulations', '/simulations', <SimulationScreen />);
    const first = screen.getByTestId('tab-new');
    expect(first.getAttribute('aria-selected')).toBe('true');
    first.focus();
    fireEvent.keyDown(first, { key: 'ArrowRight' });
    expect(screen.getByTestId('tab-event').getAttribute('aria-selected')).toBe('true');
    expect(document.activeElement).toBe(screen.getByTestId('tab-event'));
    expect(document.getElementById('sim-panel-new')?.hidden).toBe(true);
  });
});

describe('hand-off and home', () => {
  it('limit detail → "이 계약 해약 시 영향" opens the event tab with that contract, once, without the key in the address', async () => {
    net.gets.set('/api/v1/me/limits?status=ALL', limits);
    net.gets.set('/api/v1/me/limits/1', { asOf, dataStatus: 'OK', ledger: ledger({ productName: '종신20', insuredMasked: '김*수' }), history: [] });
    show('/limits/1', '/limits/:ledgerId', <LimitScreen />, [{ path: '/simulations', element: <SimulationScreen /> }]);
    expect((await screen.findByTestId('ledger-product')).textContent).toBe('종신20 · 피보험자 김*수');
    fireEvent.click(screen.getByTestId('entry-impact'));
    expect((await screen.findByTestId('tab-event')).getAttribute('aria-selected')).toBe('true');
    await waitFor(() => {
      expect(within(screen.getByTestId('form-event')).getByTestId('field-policyKey').querySelector('[aria-pressed="true"]')?.getAttribute('data-value')).toBe(KEY);
    });
    expect(net.calls.every((c) => !c.url.startsWith('/simulations'))).toBe(true);
    cleanup();
    // 두 번째 진입은 빈 폼(한 번 읽으면 비운다)
    show('/simulations', '/simulations', <SimulationScreen />);
    expect(screen.getByTestId('tab-new').getAttribute('aria-selected')).toBe('true');
    expect(within(screen.getByTestId('form-event')).getByTestId('field-policyKey').querySelector('[aria-pressed="true"]')).toBeNull();
  });

  it('home: next month deferral with the forecast label, last month next to this one with its own label', async () => {
    net.gets.set(
      '/api/v1/me/earnings/202702',
      month({ closeYm: '202702', status: 'CONFIRMED', previous: { closeYm: '202701', status: 'PAID', gross: won(1500000), netBasis: 'PAYOUT', net: won(1450500) } }),
    );
    net.gets.set('/api/v1/me/deferrals?from=202703&to=202703', deferrals({ from: '202703', to: '202703', months: [{ dueYm: '202703', expected: won(40000), held: won(0) }], items: [] }));
    net.gets.set('/api/v1/me/limits?status=ALL', limits);
    show('/', '/', <HomeScreen />);
    expect((await screen.findByTestId('next-month-amount')).textContent).toBe('40,000원분급 예정');
    const previous = await screen.findByTestId('previous');
    expect(previous.textContent).toBe('전월 · 2027년 1월1,500,000원지급완료 실수령 1,450,500원지급완료');
  });

  it('home: no next-month row (or zero) says so; no previous month says so', async () => {
    net.gets.set('/api/v1/me/earnings/202702', month({ closeYm: '202702' }));
    net.gets.set('/api/v1/me/deferrals?from=202703&to=202703', deferrals({ from: '202703', to: '202703', months: [], items: [] }));
    net.gets.set('/api/v1/me/limits?status=ALL', limits);
    show('/', '/', <HomeScreen />);
    expect((await screen.findByTestId('next-month-none')).textContent).toBe('다음 달 예정 없음');
    expect((await screen.findByTestId('previous-none')).textContent).toBe('전월 내역 없음');
  });
});
