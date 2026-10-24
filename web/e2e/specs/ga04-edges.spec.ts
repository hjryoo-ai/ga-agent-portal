// GA04 — 포털 손 스트림(web/e2e/streams/edges.jsonl): DRIFT "확인 중" + 흐림(§6.6), 같은 달 취소 짝 접힘·다른 달 배지 + 링크(Q6), CUT 삭감(보조 문구 없음).
// 포털 Phase 6 E2(계획 ⑨): 분급 보류·취소 알림, DRIFT 달(202608)의 확정 알림 0 — 202701은 있다(설계서 §6.5 DD-18).
import { advance, streamLength, tenantOf } from '../support/env';
import { expect, test } from '../support/fixtures';
import { openInbox, sorted } from '../support/inbox';

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

test('알림: 분급 보류·취소, DRIFT 달은 확정 알림 없음', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA04');
  await advance(t, streamLength(t));
  const { types } = await openInbox(page, portal, t, AUG);
  expect(sorted(types)).toEqual(['DEFER_CANCELLED', 'DEFER_HELD', 'LIMIT_CUT', 'LIMIT_FULL', 'LIMIT_WARN', 'STMT_CONFIRMED']);
  const items = page.getByTestId('notification');
  await expect(items.filter({ hasText: '2027년 1월 명세가 확정되었습니다' })).toHaveCount(1);
  await expect(items.filter({ hasText: '2026년 8월 명세' })).toHaveCount(0);
  await expect(items.filter({ hasText: '계약 *********0002의 2027년 9월 분급이 보류되었습니다' })).toHaveCount(1);
  await expect(items.filter({ hasText: '계약 *********0002의 2030년 1월 분급이 취소되었습니다' })).toHaveCount(1);
  await portal.check('ga04 inbox');

  // 링크가 화면 경로(/lines/:calcId)에 맞으면 읽음 뒤 그리로 간다(승인 A)
  await items.filter({ hasText: '분급이 보류되었습니다' }).click();
  await expect(page).toHaveURL(/\/lines\/EDG-C5$/);
  await expect(page.getByTestId('line-detail')).toBeVisible();
  const after = await openInbox(page, portal, t, AUG);
  expect(after.response.items.filter((n) => n.readAt !== undefined).map((n) => n.type)).toEqual(['DEFER_HELD']);
  await expect(page.locator('.notice--unread')).toHaveCount(5);
});
