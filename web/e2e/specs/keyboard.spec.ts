// 키보드 전 경로(지시문 ⑤·C7) — 데스크톱 프로젝트만(playwright.config.ts — 모바일 프로젝트의 testIgnore): Tab·Enter만으로 홈 → 명세 → 한도 현황 → 전체 → 상세,
// 포커스는 늘 보인다(:focus-visible 외곽선의 계산된 스타일). GA01이 끝까지 적용된 뒤(같은 프로젝트의 ga01-a1이 먼저 돈다 — 파일 이름 순).
import { tenantOf } from '../support/env';
import { expect, test } from '../support/fixtures';

const AUG = '2026-08-20T03:00:00Z';

test('키보드만으로 홈 → 명세 → 한도', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA01');
  await portal.open('/', t, 'agent', AUG);
  await expect(page.getByTestId('month-card')).toBeVisible();
  const focusOn = async (name: string) => {
    for (let i = 0; i < 40; i++) {
      await page.keyboard.press('Tab');
      const focused = await page.evaluate(() => ({ text: document.activeElement?.textContent.trim() ?? '', outline: getComputedStyle(document.activeElement ?? document.body).outlineStyle }));
      if (focused.text === name) {
        expect(focused.outline, `visible focus on ${name}`).not.toBe('none');
        return;
      }
    }
    throw new Error(`could not reach "${name}" with Tab`);
  };
  await focusOn('월별 명세 보기');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/statements\/202608$/);
  await focusOn('한도 현황');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/limits$/);
  await focusOn('전체');
  await page.keyboard.press('Enter');
  await focusOn('상세 보기');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/limits\/1$/);
});
