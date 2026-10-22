// GA03 — 엔진 E1 cycle: 정정만 있는 확정 달(202611)은 "정정 N원 — 지급 반영은 정산팀 확인"(Phase 3 수용 Q3 — 단정하지 않는다).
// 정정 금액의 라벨은 월 상태가 아니라 "지급 반영 미정"(Phase 4 수용 심사 A1 — 엔진 E1-2).
import { advance, streamLength, tenantOf } from '../support/env';
import { expect, test } from '../support/fixtures';

test('정정만 있는 달', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA03');
  await advance(t, streamLength(t));
  await portal.open('/', t, 'agent', '2026-11-20T03:00:00Z');
  const card = page.getByTestId('month-card');
  await expect(card.getByTestId('month-status')).toHaveText('확정');
  await expect(card.getByTestId('row-adjustment').locator('.amount__label')).toHaveText('지급 반영 미정');
  await expect(card.getByTestId('adjustment-only')).toHaveText('정정 -45,000원지급 반영 미정 — 지급 반영은 정산팀 확인');
  await portal.check('ga03 adjustment only');
});
