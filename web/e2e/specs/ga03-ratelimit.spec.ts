// GA03 — 레이트 리밋 2/분(포털 Phase 5 ⑨ 시나리오 4): 연타 셋째가 429 — "잠시 후 다시 시도해 주세요(n초)"(Retry-After 그대로) + 마지막 결과 유지.
import { tenantOf } from '../support/env';
import { expect, test } from '../support/fixtures';
import { newContractSample } from '../support/samples';

const SIM = '2027-02-15T00:30:00Z';

test('셋째 요청이 429, 마지막 결과는 그대로', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA03');
  await portal.open('/simulations', t, 'agent', SIM);
  const form = page.getByTestId('form-new');
  await form.getByTestId('field-productKey').locator('input').fill('WHOLE-LIFE-20Y');
  await form.getByTestId('field-monthlyPremium').locator('input').fill('150000');
  await form.getByTestId('field-contractDate').locator('input').fill('2026-08-01');
  const snapshot = `룰 스냅샷 ${newContractSample('new-limit-exceeded').ruleSnapshotId}`;
  for (let i = 0; i < 2; i++) {
    const answered = page.waitForResponse((r) => r.url().endsWith('/api/v1/me/simulations/new-contract'));
    await page.getByTestId('run-new').click();
    expect((await answered).status()).toBe(200);
    await expect(page.getByTestId('sim-rule-snapshot')).toHaveText(snapshot);
  }
  const third = page.waitForResponse((r) => r.url().endsWith('/api/v1/me/simulations/new-contract'));
  await page.getByTestId('run-new').click();
  const r = await third;
  expect(r.status()).toBe(429);
  const retryAfter = r.headers()['retry-after'] ?? '';
  expect(retryAfter).toMatch(/^[1-9][0-9]?$/);
  await expect(page.getByTestId('sim-error')).toHaveText(`잠시 후 다시 시도해 주세요(${retryAfter}초)`);
  await expect(page.getByTestId('sim-rule-snapshot')).toHaveText(snapshot);
  await portal.check('ga03 rate limited');
});
