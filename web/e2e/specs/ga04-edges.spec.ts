// GA04 — 포털 손 스트림(web/e2e/streams/edges.jsonl): DRIFT "확인 중" + 흐림(§6.6), 같은 달 취소 짝 접힘·다른 달 배지 + 링크(Q6), CUT 삭감(보조 문구 없음).
import { advance, streamLength, tenantOf } from '../support/env';
import { expect, test } from '../support/fixtures';

const AUG = '2026-08-20T03:00:00Z';

test('DRIFT 달, 취소 짝, CUT', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA04');
  await advance(t, streamLength(t));
  await portal.open('/', t, 'agent', AUG);
  const card = page.getByTestId('month-card');
  await expect(card.getByTestId('month-drift')).toHaveText('확인 중');
  const amounts = card.locator('.amount');
  await expect(amounts.first()).toBeVisible();
  for (const a of await amounts.all()) await expect(a).toHaveClass(/amount--uncertain/);
  await portal.check('ga04 home drift');

  await portal.open('/statements/202608', t, 'agent', AUG);
  const lines = page.getByTestId('lines');
  await expect(lines.getByTestId('line-pair')).toHaveCount(1);
  await expect(lines.getByTestId('line-pair').locator('summary')).toHaveText('취소됨 취소된 원 라인 보기');
  const cut = lines.locator('[data-calc-id="EDG-C2"]');
  await expect(cut.getByTestId('limit-cut')).toHaveText('한도 초과 삭감 30,000원확정');
  await expect(cut.getByTestId('limit-cut-defer-note')).toHaveCount(0);
  await expect(cut.getByTestId('cross-month')).toHaveText('취소됨 → 2026년 9월');
  await portal.check('ga04 statement 202608');

  await cut.getByTestId('cross-month').click();
  await expect(page).toHaveURL(/\/statements\/202609$/);
  await expect(page.locator('[data-calc-id="EDG-C4"]').getByTestId('cross-month')).toHaveText('취소 ← 2026년 8월');
  await portal.check('ga04 statement 202609');
});
