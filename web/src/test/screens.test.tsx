// 화면 컴포넌트 시험(포털 Phase 4 C3·C5): 생성 클라이언트가 부르는 fetch를 계약 모양의 허구 응답으로 바꾸고 실제 화면을 그린다.
// DRIFT 흐림·"확인 중", 정정만 있는 달(Q3), DEFER_AFTER_FY 문구(Q4), 라인 접기(Q6 — 같은 달 접힘·다른 달 배지 + 링크), 세후 표시(Q2),
// 그리고 그려진 모든 금액에 라벨이 있다(C3).
import { QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createQueryClient } from '../api/queries';
import { bootToken, dropToken } from '../boot/token';
import { HomeScreen } from '../screens/HomeScreen';
import { LimitScreen } from '../screens/LimitScreen';
import { LineScreen } from '../screens/LineScreen';
import { LimitsScreen } from '../screens/LimitsScreen';
import { StatementScreen } from '../screens/StatementScreen';
import { MonthCard } from '../ui/MonthCard';
import { asOf, ledger, line, month, won } from './fixtures';

const routes = vi.hoisted(() => {
  const table = new Map<string, unknown>();
  globalThis.fetch = (input: RequestInfo | URL) => {
    const url = new URL(input instanceof Request ? input.url : String(input), 'http://portal.test');
    const body = table.get(url.pathname + url.search) ?? table.get(url.pathname);
    return Promise.resolve(
      body === undefined
        ? new Response('{"code":"NOT_FOUND","message":"not found"}', { status: 404, headers: { 'Content-Type': 'application/problem+json' } })
        : new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } }),
    );
  };
  return table;
});

beforeEach(() => {
  bootToken({ hash: '#token=a.b.c', pathname: '/', search: '' }, { replaceState: () => undefined });
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-08-20T03:00:00Z'));
});

afterEach(() => {
  cleanup();
  routes.clear();
  dropToken();
  vi.useRealTimers();
});

function show(path: string, pattern: string, element: ReactNode) {
  const client = createQueryClient();
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path={pattern} element={element} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function everyAmountHasALabel(container: HTMLElement) {
  const amounts = [...container.querySelectorAll('.amount')];
  expect(amounts.length).toBeGreaterThan(0);
  for (const a of amounts) {
    expect(a.querySelector('.amount__label')?.textContent, a.textContent).not.toBe('');
  }
}

describe('MonthCard', () => {
  it('DRIFT and REBUILDING: "확인 중" badge and every amount dimmed', () => {
    for (const status of ['DRIFT', 'REBUILDING'] as const) {
      const { container } = render(<MonthCard month={month({ monthDataStatus: status })} heading="h" nowMs={Date.now()} />);
      expect(screen.getByTestId('month-drift').textContent).toBe('확인 중');
      const amounts = [...container.querySelectorAll('.amount')];
      expect(amounts.length).toBeGreaterThan(1);
      expect(amounts.every((a) => a.classList.contains('amount--uncertain'))).toBe(true);
      cleanup();
    }
  });

  it('OK months are not dimmed', () => {
    const { container } = render(<MonthCard month={month()} heading="h" nowMs={Date.now()} />);
    expect(screen.queryByTestId('month-drift')).toBeNull();
    expect(container.querySelector('.amount--uncertain')).toBeNull();
  });

  it('CONFIRMED + ESTIMATE shows the after-tax amount as 예상; CONFIRMED without a basis shows only pre-tax', () => {
    render(<MonthCard month={month({ status: 'CONFIRMED', netBasis: 'ESTIMATE', net: won(1740600), withholdingTax: won(59400) })} heading="h" nowMs={Date.now()} />);
    expect(screen.getByTestId('month-big').textContent).toBe('실수령1,740,600원예상');
    expect(screen.getByTestId('row-tax').textContent).toContain('59,400원예상');
    cleanup();
    render(<MonthCard month={month({ status: 'CONFIRMED' })} heading="h" nowMs={Date.now()} />);
    expect(screen.getByTestId('month-big').textContent).toBe('세전1,800,000원확정세후는 지급 시 확정');
    expect(screen.queryByTestId('row-tax')).toBeNull();
  });

  it('adjustment-only confirmed month: "정정 N원 — 지급 반영은 정산팀 확인" (no lines, or ADJUSTMENT lines only)', () => {
    for (const byType of [{}, { ADJUSTMENT: won(-45000) }]) {
      render(<MonthCard month={month({ status: 'CONFIRMED', gross: won(0), byType, adjustment: won(-45000) })} heading="h" nowMs={Date.now()} />);
      expect(screen.getByTestId('adjustment-only').textContent).toBe('정정 -45,000원확정 — 지급 반영은 정산팀 확인');
      cleanup();
    }
    render(<MonthCard month={month({ status: 'CONFIRMED', adjustment: won(-45000) })} heading="h" nowMs={Date.now()} />);
    expect(screen.queryByTestId('adjustment-only')).toBeNull();
  });

  it('PAID shows the payout day, not the expected day; payout expected shows D-n', () => {
    render(<MonthCard month={month({ status: 'PAID', netBasis: 'PAYOUT', net: won(1740600), paidAt: '2026-09-10T10:00:00+09:00', payoutExpectedOn: '2026-09-10' })} heading="h" nowMs={Date.now()} />);
    expect(screen.getByTestId('paid-on').textContent).toBe('지급일 2026-09-10');
    expect(screen.queryByTestId('payout-expected')).toBeNull();
    cleanup();
    render(<MonthCard month={month({ payoutExpectedOn: '2026-09-10' })} heading="h" nowMs={Date.parse('2026-08-31T15:00:00Z')} />);
    expect(screen.getByTestId('payout-expected').textContent).toBe('지급예정일 2026-09-10 · D-9');
  });
});

describe('screens', () => {
  it('home: this month card, attention counts, entry points disabled', async () => {
    routes.set('/api/v1/me/earnings/202608', month({ status: 'ESTIMATED', netBasis: 'ESTIMATE', net: won(1740600), estimateAsOf: '2026-08-19T17:10:00Z' }));
    routes.set('/api/v1/me/limits?status=ALL', { asOf, dataStatus: 'OK', status: 'ALL', items: [ledger({ stage: 'FULL' }), ledger({ ledgerId: '2', flags: ['OVERDUE'] })] });
    const { container } = show('/', '/', <HomeScreen />);
    await screen.findByTestId('month-card');
    expect(screen.getByTestId('estimate-as-of').textContent).toBe('기준 08-20 02:10');
    expect((await screen.findByTestId('attention-limits')).textContent).toBe('한도 주의 1건');
    expect(screen.getByTestId('attention-flags').textContent).toBe('납입 지연·실효 1건');
    for (const id of ['entry-timeline', 'entry-simulation', 'entry-notifications']) {
      expect(screen.getByTestId<HTMLButtonElement>(id).disabled).toBe(true);
    }
    everyAmountHasALabel(container);
  });

  it('home: no row for this month says so (404 is not "unregistered" here)', async () => {
    routes.set('/api/v1/me/limits?status=ALL', { asOf, dataStatus: 'OK', status: 'ALL', items: [] });
    show('/', '/', <HomeScreen />);
    expect((await screen.findByTestId('month-empty')).textContent).toContain('이번 달 내역이 아직 없습니다');
  });

  it('statement: same-month pair folded, cross-month partners badged with a link to the other month, DEFER_AFTER_FY wording', async () => {
    routes.set('/api/v1/me/earnings?from=202310&to=202608', { asOf, dataStatus: 'OK', from: '202310', to: '202608', items: [month({ closeYm: '202607' }), month()] });
    routes.set('/api/v1/me/earnings/202608', month({ status: 'CONFIRMED' }));
    routes.set('/api/v1/me/earnings/202608/lines?page=0&size=200', {
      asOf,
      dataStatus: 'OK',
      closeYm: '202608',
      page: 0,
      size: 200,
      total: 4,
      items: [
        line('1', { reversedBy: '3' }),
        line('2', { commType: 'INCENTIVE', amount: won(855000), limitCutAmt: won(145000), limitCutPolicy: 'DEFER_AFTER_FY', reversedBy: '9' }),
        line('3', { amount: won(-945000), reversalOf: '1' }),
        line('4', { amount: won(-100000), reversalOf: '8' }),
      ],
    });
    routes.set('/api/v1/me/earnings/lines/9', { asOf, dataStatus: 'OK', line: line('9', { closeYm: '202609', reversalOf: '2' }), basis: { ruleVersionIds: [] }, policySummary: {}, deferrals: [] });
    routes.set('/api/v1/me/earnings/lines/8', { asOf, dataStatus: 'OK', line: line('8', { closeYm: '202607', reversedBy: '4' }), basis: { ruleVersionIds: [] }, policySummary: {}, deferrals: [] });
    const { container } = show('/statements/202608', '/statements/:ym', <StatementScreen />);
    const lines = await screen.findByTestId('lines');
    const pair = within(lines).getByTestId('line-pair');
    expect(pair.querySelector('details')?.textContent).toContain('취소된 원 라인 보기');
    expect([...lines.querySelectorAll(':scope > [data-testid="line"]')].map((l) => l.getAttribute('data-calc-id'))).toEqual(['2', '4']);
    await waitFor(() => {
      expect(within(lines).getAllByTestId('cross-month')).toHaveLength(2);
    });
    const badges = within(lines).getAllByTestId('cross-month');
    expect(badges.map((b) => [b.textContent, b.getAttribute('href')])).toEqual([
      ['취소됨 → 2026년 9월', '/statements/202609'],
      ['취소 ← 2026년 7월', '/statements/202607'],
    ]);
    expect(within(lines).getByTestId('limit-cut-defer-note').textContent).toBe('(사규: 초년도 후 이연 — 현재 이연 스케줄 없음)');
    expect(within(lines).getAllByTestId('limit-cut')[0]?.textContent).toContain('한도 초과 삭감');
    everyAmountHasALabel(container);
  });

  it('line detail, limits and limit detail label every amount; CUT has no deferral note', async () => {
    routes.set('/api/v1/me/earnings/lines/2', {
      asOf,
      dataStatus: 'OK',
      line: line('2', { commType: 'INCENTIVE', limitCutAmt: won(145000), limitCutPolicy: 'CUT' }),
      basis: { rate: '7.0', payoutRate: '0.9', ruleVersionIds: ['1'] },
      policySummary: { contractDate: '2026-08-01', monthlyPremium: won(150000) },
      deferrals: [{ scheduleId: 'S1', dueYm: '202701', amount: won(10000), status: 'HELD' }],
      receivable: { receivableId: 'R1', amount: won(720000), remaining: won(720000), status: 'OPEN' },
      ledgerId: '1',
    });
    routes.set('/api/v1/me/earnings/202608', month({ status: 'PAID', netBasis: 'PAYOUT', net: won(1740600) }));
    const first = show('/lines/2', '/lines/:calcId', <LineScreen />);
    await screen.findByTestId('line-detail');
    expect(screen.getByTestId('policy-masked').textContent).toBe('*********0001');
    expect(screen.queryByTestId('limit-cut-defer-note')).toBeNull();
    expect(screen.getByTestId('line-deferrals').textContent).toContain('보류');
    everyAmountHasALabel(first.container);
    cleanup();

    routes.set('/api/v1/me/limits?status=ALL', { asOf, dataStatus: 'OK', status: 'ALL', items: [ledger({ stage: 'FULL' })] });
    const second = show('/limits', '/limits', <LimitsScreen />);
    const card = await screen.findByTestId('ledger-card');
    expect(within(card).getByTestId('stage').textContent).toBe('소진');
    expect(within(card).getByTestId('flag-CANCELLED').textContent).toBe('해약');
    expect(within(card).getByRole('img').getAttribute('aria-label')).toBe('소진율 60.0%');
    everyAmountHasALabel(second.container);
    cleanup();

    routes.set('/api/v1/me/limits/1', {
      asOf,
      dataStatus: 'OK',
      ledger: ledger(),
      history: [{ sourceSeq: 4, kind: 'POST', calcId: '1', amount: won(945000), accumAfter: won(945000), limitAfter: won(1800000), occurredAt: '2026-08-01T09:00:00+09:00' }],
    });
    const third = show('/limits/1', '/limits/:ledgerId', <LimitScreen />);
    expect((await screen.findByTestId('limit-formula')).textContent).toBe('월납 150,000원원장 기준 × 12배 → 한도 1,800,000원원장 기준');
    expect(screen.getByTestId('limit-summary').textContent).toContain('(사규: 초년도 후 이연 — 현재 이연 스케줄 없음)');
    await waitFor(() => {
      expect(screen.getAllByTestId('history-item')).toHaveLength(1);
    });
    everyAmountHasALabel(third.container);
  });
});
