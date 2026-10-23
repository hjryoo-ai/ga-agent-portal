// GA02 — 보험사 둘 엔진(포털 Phase 5 ⑨ 시나리오 3): 422 INSURER_AMBIGUOUS(S11) → 보험사 입력란이 열린다 → SAMLIFE로 다시(S11b).
import { tenantOf } from '../support/env';
import { expect, test } from '../support/fixtures';
import { newContractSample, wonText } from '../support/samples';

const SIM = '2027-02-15T00:30:00Z';

test('INSURER_AMBIGUOUS → 보험사 코드 → 결과', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA02');
  await portal.open('/simulations', t, 'agent', SIM);
  const form = page.getByTestId('form-new');
  await expect(form.getByTestId('field-insurerCd')).toHaveCount(0);
  await form.getByTestId('field-productKey').locator('input').fill('WHOLE-LIFE-20Y');
  await form.getByTestId('field-monthlyPremium').locator('input').fill('150000');
  await form.getByTestId('field-contractDate').locator('input').fill('2026-08-01');
  await page.getByTestId('run-new').click();
  await expect(page.getByTestId('sim-error')).toHaveText('같은 상품 키를 가진 보험사가 둘 이상입니다 — 보험사 코드를 입력해 주세요');
  await expect(page.getByTestId('sim-result-new')).toHaveCount(0);
  await portal.check('ga02 insurer needed');

  await form.getByTestId('field-insurerCd').locator('input').fill('SAMLIFE');
  await page.getByTestId('run-new').click();
  const s = newContractSample('new-ambiguous-samlife');
  const result = page.getByTestId('sim-result-new');
  await expect(result.getByTestId('sim-rule-snapshot')).toHaveText(`룰 스냅샷 ${s.ruleSnapshotId}`);
  await expect(result.getByTestId('sim-first-year')).toHaveText(`첫해 지급분${wonText(s.deferral.firstYearPayout)}시뮬레이션`);
  await expect(page.getByTestId('sim-error')).toHaveCount(0);
  await portal.check('ga02 insurer resolved');
});
