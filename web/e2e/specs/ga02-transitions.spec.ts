// GA02 — 엔진 estimate_cycle_01_기본: 한 달(202608)의 네 단계 PROVISIONAL → ESTIMATED → CONFIRMED(세후 예상 — Phase 3 수용 Q2) → PAID(지시문 ⑦·C4).
import { advance, tenantOf } from '../support/env';
import { expect, test } from '../support/fixtures';

const AUG = '2026-08-20T03:00:00Z';

test('네 단계 전이와 세후 표시', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA02');
  const card = page.getByTestId('month-card');
  const steps: [number, string, string][] = [
    [3, '예상', '세전1,890,000원예상세후는 야간 산출 후 표시'],
    [4, '예상', '실수령1,827,630원예상'],
    [5, '확정', '실수령1,827,630원예상'],
    [6, '지급완료', '실수령1,827,630원지급완료'],
  ];
  for (const [seq, status, big] of steps) {
    await advance(t, seq);
    await portal.open('/', t, 'agent', AUG);
    await expect(card.getByTestId('month-status')).toHaveText(status);
    await expect(card.getByTestId('month-big')).toHaveText(big);
    if (seq === 5) await expect(card.getByTestId('row-gross')).toHaveText('세전1,890,000원확정');
    await portal.check(`ga02 seq ${String(seq)}`);
  }
});
