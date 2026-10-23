// GA01 — 시뮬레이션(포털 Phase 5 ⑨ 시나리오 1·2·5 + S8 API 요청). 엔진은 표준 샘플 스텁(JCS 대조 — 입력이 샘플 요청과 같아야 답한다).
// 브라우저 시계는 엔진 샘플의 시계(2027-02-15 09:30 KST) — 기준일·체결일 기본값이 그 날이다. 기대값은 샘플 응답에서 읽는다.
import { advance, baseUrl, simEngine, streamLength, tenantOf, token } from '../support/env';
import { expect, test } from '../support/fixtures';
import { newContractSample, policyEventSample, wonText } from '../support/samples';
import type { Page } from '@playwright/test';

const SIM = '2027-02-15T00:30:00Z';

test.describe.configure({ mode: 'serial' });

async function runNewContract(page: Page, contractDate: string) {
  const form = page.getByTestId('form-new');
  await form.getByTestId('field-productKey').locator('input').fill('WHOLE-LIFE-20Y');
  await form.getByTestId('field-monthlyPremium').locator('input').fill('150000');
  await form.getByTestId('field-contractDate').locator('input').fill(contractDate);
  await page.getByTestId('run-new').click();
}

async function expectNewContract(page: Page, name: string) {
  const s = newContractSample(name);
  const result = page.getByTestId('sim-result-new');
  await expect(result.getByTestId('sim-rule-snapshot')).toHaveText(`룰 스냅샷 ${s.ruleSnapshotId}`);
  await expect(result.getByTestId('sim-first-year')).toHaveText(`첫해 지급분${wonText(s.deferral.firstYearPayout)}시뮬레이션`);
  await expect(result.getByTestId('sim-installments').locator('tr')).toHaveCount(s.fyCommissionByInstallment.length);
  if (s.limitAmount === undefined) {
    await expect(result.getByTestId('sim-limit-amount')).toHaveCount(0);
    await expect(result.getByTestId('sim-no-limit')).toHaveText('한도 원장 대상 아님');
  } else {
    await expect(result.getByTestId('sim-limit-amount')).toHaveText(`한도액${wonText(s.limitAmount)}시뮬레이션`);
  }
  await expect(result.getByTestId('sim-schedule').locator('tr')).toHaveCount(s.deferral.schedule.length);
  await expect(result.getByTestId('sim-generated-at')).toHaveText('엔진 산출 시각 02-15 09:30');
  await expect(result.getByTestId('sim-requested-at')).toHaveText(/^요청 시각 \d{2}-\d{2} \d{2}:\d{2}$/);
  await expect(result.getByTestId('sim-id')).toHaveText(/^요청 번호 [0-9a-f-]{36}$/);
  for (const label of await result.locator('.amount__label').allTextContents()) expect(label).toBe('시뮬레이션');
}

test('신계약 S1·S2·S3 — 엔진 샘플 값 그대로, 모든 금액 "시뮬레이션", 고정 문구', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA01');
  await advance(t, streamLength(t));
  await portal.open('/', t, 'agent', SIM);
  await page.getByTestId('entry-simulation').click();
  await expect(page).toHaveURL(/\/simulations$/);
  await expect(page.getByTestId('simulation-note')).toHaveText('설계사 참고용 · 비확정 · 고객 제시 불가');
  await expect(page.getByTestId('form-new').getByTestId('field-contractDate').locator('input')).toHaveValue('2027-02-15');
  for (const [name, date] of [
    ['new-limit-exceeded', '2026-08-01'],
    ['new-curve', '2027-01-15'],
    ['new-no-limit', '2026-06-30'],
  ] as const) {
    await runNewContract(page, date);
    await expectNewContract(page, name);
    await portal.check(`ga01 simulation ${name}`);
  }
});

test('해약 영향 S5·S6 — 한도 상세에서 그 계약이 골라진 채로, 변경 전/후 나란히(차이 없음)', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA01');
  await portal.open('/limits/1', t, 'agent', SIM);
  await page.getByTestId('entry-impact').click();
  await expect(page).toHaveURL(/\/simulations$/);
  await expect(page.getByTestId('tab-event')).toHaveAttribute('aria-selected', 'true');
  const form = page.getByTestId('form-event');
  await expect(form.getByTestId('field-policyKey').locator('button[aria-pressed="true"]')).toHaveCount(1);
  await expect(form.getByTestId('field-eventType').getByRole('button')).toHaveText(['해약', '실효', '청약 철회', '감액', '부활']);
  await expect(form.getByTestId('field-eventType').getByRole('button', { name: '해약' })).toHaveAttribute('aria-pressed', 'true');
  for (const [name, elapsed] of [
    ['event-cancel-7', '7'],
    ['event-cancel-6', '6'],
  ] as const) {
    const s = policyEventSample(name);
    await form.getByTestId('field-elapsedInstallments').locator('input').fill(elapsed);
    await page.getByTestId('run-event').click();
    const result = page.getByTestId('sim-result-event');
    await expect(result.getByTestId('sim-clawback')).toHaveText(`환수${wonText(s.clawbackAmount)}시뮬레이션`);
    await expect(result.getByTestId('sim-offset-title')).toHaveText('2027년 2월 세전·환수 상계 합계');
    await expect(result.getByTestId('sim-offset-before')).toHaveText(`변경 전${wonText(s.offsetAgainstMonth.before)}시뮬레이션`);
    await expect(result.getByTestId('sim-offset-after')).toHaveText(`변경 후${wonText(s.offsetAgainstMonth.after)}시뮬레이션`);
    await expect(result.getByTestId('sim-receivable')).toHaveText(`채권 잔액${wonText(s.receivableRemaining)}시뮬레이션`);
    await expect(result.getByTestId('sim-rule-snapshot')).toHaveText(`룰 스냅샷 ${s.ruleSnapshotId}`);
    await expect(result).not.toContainText('실수령');
    await expect(result.getByTestId('sim-generated-at')).toHaveCount(0);
  }
  await portal.check('ga01 simulation policy event');
});

test('엔진이 내려가면 503 문구 + 마지막 결과 유지, 조회 화면은 그대로', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA01');
  await portal.open('/simulations', t, 'agent', SIM);
  await runNewContract(page, '2026-08-01');
  await expectNewContract(page, 'new-limit-exceeded');
  await simEngine(t, 'down');
  try {
    await page.getByTestId('run-new').click();
    await expect(page.getByTestId('sim-error')).toHaveText('시뮬레이션을 지금 할 수 없습니다 — 조회는 가능');
    await expect(page.getByTestId('sim-result-new').getByTestId('sim-rule-snapshot')).toHaveText(
      `룰 스냅샷 ${newContractSample('new-limit-exceeded').ruleSnapshotId}`,
    );
    await portal.check('ga01 simulation engine down');
    await page.getByRole('link', { name: '한도 현황' }).click();
    await page.getByTestId('filter-all').click();
    await expect(page.getByTestId('ledger-card')).toHaveCount(1);
  } finally {
    await simEngine(t, 'up');
  }
});

test('S8: PAYMENT_OVERDUE는 화면 선택지에 없고, API로 보내면 422 고정 바이트(엔진 message 없음)', async ({ request }, info) => {
  const t = tenantOf(info.project.name, 'GA01');
  const auth = { Authorization: `Bearer ${token(t, 'agent')}` };
  const limits = await request.get(`${baseUrl}/api/v1/me/limits?status=ALL`, { headers: auth });
  const { items } = (await limits.json()) as { items: { policyKey: string }[] };
  const policyKey = items[0]?.policyKey ?? '';
  expect(policyKey).toMatch(/^[0-9a-f]{16}$/);
  const r = await request.post(`${baseUrl}/api/v1/me/simulations/policy-event`, {
    headers: { ...auth, 'Content-Type': 'application/json' },
    data: JSON.stringify({ policyKey, eventType: 'PAYMENT_OVERDUE', asOfDate: '2027-02-15' }),
  });
  expect(r.status()).toBe(422);
  expect(await r.text()).toBe('{"code":"UNSUPPORTED_EVENT_TYPE","message":"event type not simulated"}');
});
