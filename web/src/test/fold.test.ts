// 라인 접기(Q6): 같은 달 짝은 한 묶음(원 라인 접힘), 다른 달 짝은 접지 않고 표시만.
import { describe, expect, it } from 'vitest';
import type { Line } from '../api/queries';
import type { Won } from '../money/won';
import { foldLines } from '../screens/fold';

const line = (calcId: string, extra: Partial<Line> = {}): Line => ({
  calcId,
  closeYm: '202608',
  commType: 'FY_COMM',
  amount: 1 as unknown as Won,
  limitCutAmt: 0 as unknown as Won,
  policyMasked: '*********0001',
  policyKey: '0123456789abcdef',
  ...extra,
});

describe('foldLines', () => {
  it('folds a same-month pair at the reversal position', () => {
    const rows = foldLines([line('1', { reversedBy: '3' }), line('2'), line('3', { reversalOf: '1' })]);
    expect(rows.map((r) => (r.kind === 'pair' ? `pair:${r.reversal.calcId}<${r.original.calcId}` : `line:${r.line.calcId}`))).toEqual(['line:2', 'pair:3<1']);
  });

  it('does not fold cross-month partners and marks both roles with the partner month from the line (CR-2 — no extra call)', () => {
    const original = line('1', { reversedBy: '9', reversedByYm: '202609' });
    const reversal = line('5', { reversalOf: '8', reversalOfYm: '202607' });
    expect(foldLines([original, reversal])).toEqual([
      { kind: 'line', line: original, crossMonth: { role: 'original', partnerYm: '202609' } },
      { kind: 'line', line: reversal, crossMonth: { role: 'reversal', partnerYm: '202607' } },
    ]);
  });

  it('a cross-month partner without its month (not yet projected) still gets a badge, without a month', () => {
    expect(foldLines([line('1', { reversedBy: '9' })])).toEqual([{ kind: 'line', line: line('1', { reversedBy: '9' }), crossMonth: { role: 'original', partnerYm: undefined } }]);
  });

  it('keeps plain lines in API order', () => {
    expect(foldLines([line('2'), line('1')]).map((r) => (r.kind === 'line' ? r.line.calcId : ''))).toEqual(['2', '1']);
  });
});
