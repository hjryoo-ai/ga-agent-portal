// 포털 Phase 7 ⑤(계획 ⑦ — C11): 야간 대사와 자동 복구를 사용자가 보는 화면으로. 수량(행·호출·재구축 수)의 주 증거는 통합 시험(ReconRunIT·AutoRepairIT)이다.
// GA06 — A-1 둘째 판만(이웃 없음, 명세 스텁 a1, 자동 재구축 끔으로 시작): ⓐ 정상 → ⓑ 라인 누락 → DRIFT "확인 중" → 자동 켬 → 재구축·재대사 REPAIRED → 정상,
// 알림 추가 0 → ⓒ 뒤처짐(머리 11 + 피드 503) → DRIFT 0 → ⓓ 엔진 다운 → DRIFT 0. 한 프로젝트 안에서 순서대로.
// GA04 — 포털 손 스트림의 EVENT DRIFT(스트림 자체의 불일치): ⓔ 자동 끔 → "확인 중" 그대로·재구축 0, 켬 → 재구축 1·여전히 "확인 중", 한 번 더 → 재구축 0(시도함 — Q14).
import type { Page } from '@playwright/test';
import { advance, autoRebuild, corruptLine, failFeed, me, openHead, recon, reconRebuilds, statementEngine, streamLength, tenantOf } from '../support/env';
import { expect, test, type Portal } from '../support/fixtures';
import type { NotificationPage } from '../support/inbox';

const AUG = '2026-08-20T03:00:00Z'; // KST 08-20 — "이번 달" 202608
const MONTHS = ['202608', '202702'];

test.describe.configure({ mode: 'serial' });

/** 홈의 202608 카드: 지급완료, 세전 금액, DRIFT면 "확인 중" 배지 + 모든 금액 흐림, 아니면 둘 다 없음. */
async function august(page: Page, portal: Portal, tenant: string, label: string, gross: string, drift: boolean): Promise<void> {
  await portal.open('/', tenant, 'agent', AUG);
  const card = page.getByTestId('month-card');
  await expect(card.getByTestId('month-status')).toHaveText('지급완료');
  await expect(card.getByTestId('row-gross')).toContainText(gross);
  const amounts = card.locator('.amount');
  await expect(amounts.first()).toBeVisible();
  if (drift) {
    await expect(card.getByTestId('month-drift')).toHaveText('확인 중');
    for (const a of await amounts.all()) await expect(a).toHaveClass(/amount--uncertain/);
  } else {
    await expect(card.getByTestId('month-drift')).toHaveCount(0);
    for (const a of await amounts.all()) await expect(a).not.toHaveClass(/amount--uncertain/);
  }
  await portal.check(label);
}

/** 알림 첫 페이지(브라우저 밖 — 전후 비교). */
async function notifications(tenant: string): Promise<{ id: string; type: string }[]> {
  const page = await me<NotificationPage>(tenant, 'notifications?page=0&size=50');
  return page.items.map((n) => ({ id: n.id, type: n.type }));
}

test('ⓐ 정상: 대사 MATCH — 화면 그대로', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA06');
  await advance(t, 10);
  expect(await recon(t, MONTHS), 'recon run').toBe(0);
  await august(page, portal, t, 'ga06 a match', '1,800,000', false);
  expect(await reconRebuilds(t)).toBe(0);
});

test('ⓑ 라인 누락 → DRIFT "확인 중" → 자동 재구축·재대사 REPAIRED → 정상, 알림 추가 0', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA06');
  const before = await notifications(t);
  expect(before.map((n) => n.type)).toContain('STMT_CONFIRMED');
  expect(before.map((n) => n.type)).toContain('PAYOUT');

  await corruptLine(t, '1'); // 202608 FY 945,000 — 포털 합 855,000, 엔진 1,800,000
  await august(page, portal, t, 'ga06 b corrupted', '855,000', false);
  expect(await recon(t, MONTHS), 'recon run (auto rebuild off)').toBe(0);
  await august(page, portal, t, 'ga06 b drift', '855,000', true);
  expect(await reconRebuilds(t), 'auto rebuild off').toBe(0);

  await autoRebuild(t, true);
  expect(await recon(t, MONTHS), 'recon run (auto rebuild on)').toBe(0);
  expect(await reconRebuilds(t), 'one rebuild by recon:').toBe(1);
  await august(page, portal, t, 'ga06 b repaired', '1,800,000', false);
  expect(await notifications(t), 'same keys — no new notification (Phase 6 Q1)').toEqual(before);
});

test('ⓒ 뒤처짐: 머리 11 + 피드 503 → DRIFT 0, 재구축 0', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA06');
  const before = await notifications(t);
  await failFeed(t, true);
  try {
    await openHead(t, 11);
    expect(await recon(t, MONTHS), 'recon run').toBe(0);
    await august(page, portal, t, 'ga06 c lagging', '1,800,000', false);
    expect(await reconRebuilds(t)).toBe(1);
    expect(await notifications(t)).toEqual(before);
  } finally {
    await failFeed(t, false);
  }
});

test('ⓓ 엔진 다운 → DRIFT 0', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA06');
  await advance(t, 11);
  await statementEngine(t, 'down');
  try {
    expect(await recon(t, MONTHS), 'recon run').toBe(0);
    await august(page, portal, t, 'ga06 d engine down', '1,800,000', false);
    expect(await reconRebuilds(t)).toBe(1);
  } finally {
    await statementEngine(t, 'up');
  }
});

test('ⓔ 스트림 자체의 DRIFT(GA04): 끔 → 그대로, 켬 → 재구축 1·여전히 확인 중, 다시 → 재구축 0', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA04');
  await advance(t, streamLength(t));
  const card = page.getByTestId('month-card');
  const drift = async (label: string) => {
    await portal.open('/', t, 'agent', AUG);
    await expect(card.getByTestId('month-drift')).toHaveText('확인 중');
    await portal.check(label);
  };
  expect(await recon(t, ['202608']), 'recon run (auto rebuild off)').toBe(0);
  await drift('ga04 e off');
  expect(await reconRebuilds(t)).toBe(0);

  await autoRebuild(t, true);
  expect(await recon(t, ['202608']), 'recon run (auto rebuild on)').toBe(0);
  expect(await reconRebuilds(t), 'tried once').toBe(1);
  await drift('ga04 e rebuilt');

  expect(await recon(t, ['202608']), 'recon run again').toBe(0);
  expect(await reconRebuilds(t), 'tried — not rebuilt again (Q14)').toBe(1);
  await drift('ga04 e tried');
});
