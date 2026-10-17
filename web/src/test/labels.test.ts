// 상태 라벨 표 전수(포털 Phase 4 C4 — Phase 3 수용 심사 Q2, 설계서 §6.2): 월 상태 4 × netBasis(3 + 없음) = 16칸. 기대값은 여기 따로 적는다
// (같은 코드로 기대값을 만들지 않는다).
import { describe, expect, it } from 'vitest';
import type { MonthStatus, NetBasis } from '../api/queries';
import { contractLabel, deferralLabel, ledgerLabel, monthLabel, netView, payoutLabel, receivableLabel } from '../labels/labels';

type Expected = { show: string } | { hide: string };
const NIGHTLY = '세후는 야간 산출 후 표시';
const AT_PAYOUT = '세후는 지급 시 확정';
const NONE = '세후 정보 없음';

const table: [MonthStatus, NetBasis | undefined, string, Expected][] = [
  ['PROVISIONAL', undefined, '예상', { hide: NIGHTLY }],
  ['PROVISIONAL', 'ESTIMATE', '예상', { hide: NONE }],
  ['PROVISIONAL', 'CONFIRM', '예상', { hide: NONE }],
  ['PROVISIONAL', 'PAYOUT', '예상', { hide: NONE }],
  ['ESTIMATED', undefined, '예상', { hide: NONE }],
  ['ESTIMATED', 'ESTIMATE', '예상', { show: '예상' }],
  ['ESTIMATED', 'CONFIRM', '예상', { hide: NONE }],
  ['ESTIMATED', 'PAYOUT', '예상', { hide: NONE }],
  ['CONFIRMED', undefined, '확정', { hide: AT_PAYOUT }],
  ['CONFIRMED', 'ESTIMATE', '확정', { show: '예상' }],
  ['CONFIRMED', 'CONFIRM', '확정', { show: '확정' }],
  ['CONFIRMED', 'PAYOUT', '확정', { hide: NONE }],
  ['PAID', undefined, '지급완료', { hide: NONE }],
  ['PAID', 'ESTIMATE', '지급완료', { hide: NONE }],
  ['PAID', 'CONFIRM', '지급완료', { hide: NONE }],
  ['PAID', 'PAYOUT', '지급완료', { show: '지급완료' }],
];

describe('status label table', () => {
  it('covers all 16 combinations', () => {
    expect(new Set(table.map(([s, n]) => `${s}/${n ?? '-'}`)).size).toBe(16);
  });

  it.each(table)('%s + %s → gross %s, net %j', (status, basis, gross, net) => {
    expect(monthLabel(status)).toBe(gross);
    const view = netView(status, basis);
    if ('show' in net) {
      expect(view).toEqual({ kind: 'show', label: net.show });
    } else {
      expect(view).toEqual({ kind: 'hide', note: net.hide });
    }
  });

  it('ledger, payout, contract, deferral and receivable amounts have their own labels', () => {
    expect(ledgerLabel()).toBe('원장 기준');
    expect(payoutLabel()).toBe('지급완료');
    expect(contractLabel()).toBe('계약 정보');
    expect(deferralLabel('HELD')).toBe('보류');
    expect(deferralLabel('RELEASED')).toBe('이번 달 지급 대상');
    expect(receivableLabel('OPEN')).toBe('미상계');
  });
});
