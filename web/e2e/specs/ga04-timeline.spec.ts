// GA04 — 분급 타임라인·홈(포털 Phase 5 ⑨ 시나리오 6·7). 손 스트림(edges.jsonl seq 11~24): 계약 POL-EDGE-0002의 분급 여섯(2027-03 ~ 2034-01 —
// 84개월 경계 안팎), 2027-09 보류(HELD), 2030-01 취소(CANCELLED), 2027-01 확정·2027-02 잠정 달(전월). 테넌트가 유지율 시나리오를 켰다.
import { advance, streamLength, tenantOf } from '../support/env';
import { expect, test } from '../support/fixtures';
import type { Locator } from '@playwright/test';

const SIM = '2027-02-15T00:30:00Z';

const cssVar = (bar: Locator, name: string) => bar.evaluate((el, n) => (el as HTMLElement).style.getPropertyValue(n), name);

test.describe.configure({ mode: 'serial' });

test('12개월·84개월, 보류 범례, 막대 = API 문자열, 계약 필터', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA04');
  await advance(t, streamLength(t));
  await portal.open('/', t, 'agent', SIM);
  await page.getByTestId('entry-timeline').click();
  await expect(page).toHaveURL(/\/deferrals$/);
  await expect(page.getByTestId('timeline-note')).toHaveText('분급 확정 스케줄만 반영 · 참고치 · 비확정');
  await expect(page.getByTestId('held-legend')).toHaveText('보류: 지급 조건 미충족으로 멈춘 분급 — 예정 합계에 넣지 않음');
  const rows = page.getByTestId('timeline-month');
  await expect(rows).toHaveCount(2);
  await expect(rows.nth(0)).toHaveText('2027년 3월40,000원분급 예정0원보류');
  await expect(rows.nth(1)).toHaveText('2027년 9월0원분급 예정40,000원보류');
  const bars = page.getByTestId('timeline-bar');
  expect([await cssVar(bars.nth(0), '--bar-expected'), await cssVar(bars.nth(0), '--bar-held')]).toEqual(['100.0%', '0.0%']);
  expect([await cssVar(bars.nth(1), '--bar-expected'), await cssVar(bars.nth(1), '--bar-held')]).toEqual(['0.0%', '100.0%']);
  await expect(bars.nth(1)).toHaveAccessibleName('2027년 9월 0원분급 예정 40,000원보류');
  await expect(page.getByTestId('timeline-item')).toHaveText([/예정/, /보류/]);
  await portal.check('ga04 timeline 12');

  await page.getByTestId('period-84').click();
  await expect(rows.locator('th')).toHaveText(['2027년 3월', '2027년 9월', '2028년 2월', '2030년 1월', '2033년 12월']);
  await expect(page.getByTestId('timeline-item').filter({ hasText: '취소' })).toHaveCount(1);
  await expect(page.getByText('2034년 1월')).toHaveCount(0);
  await portal.check('ga04 timeline 84');

  await page.getByTestId('timeline-policy').selectOption({ label: 'TERM-10Y · *********0001' });
  await expect(page.getByTestId('timeline-empty')).toHaveText('이 기간에 분급 예정이 없습니다');
  await page.getByTestId('timeline-policy').selectOption({ label: 'TERM-10Y · *********0002' });
  await expect(rows).toHaveCount(5);
});

test('유지율 30% — 가정이 적용된 합계만 가정 라벨, 가정 전·보류는 원값', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA04');
  await portal.open('/deferrals', t, 'agent', SIM);
  await page.getByTestId('scenario-toggle').click();
  await page.getByTestId('scenario-p').fill('0');
  await expect(page.getByTestId('scenario-p-error')).toHaveText('1~100 사이의 정수를 입력해 주세요');
  await page.getByTestId('scenario-p').fill('30');
  const first = page.getByTestId('timeline-month').nth(0);
  await expect(first.getByTestId('month-expected')).toHaveText('12,000원참고(유지율 30% 가정)');
  await expect(first.getByTestId('month-expected-base')).toHaveText('40,000원분급 예정');
  await expect(page.getByTestId('timeline-month').nth(1).getByTestId('month-held')).toHaveText('40,000원보류');
  const bar = page.getByTestId('timeline-bar').nth(0);
  expect(await cssVar(bar, '--bar-expected')).toBe('30.0%');
  await portal.check('ga04 timeline retention');
});

test('홈: 다음 달 분급(분급 예정), 전월(그 달의 라벨 — 차감 없음)', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA04');
  await portal.open('/', t, 'agent', SIM);
  await expect(page.getByTestId('next-month')).toContainText('다음 달 분급 · 2027년 3월');
  await expect(page.getByTestId('next-month-amount')).toHaveText('40,000원분급 예정');
  await expect(page.getByTestId('previous')).toHaveText('전월 · 2027년 1월300,000원확정');
  await portal.check('ga04 home next month and previous');
});
