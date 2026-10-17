// GA01 — 엔진 A-1 둘째 판(+ 같은 테넌트 이웃 설계사). 지시문 ⑦ 시나리오: 상태 전이(머리를 올리며) → 홈 → 명세 → 라인 상세 → 한도 → 상세, 동기화 지연.
// 한 프로젝트 안에서는 순서대로(머리는 되돌리지 않는다).
import { advance, failFeed, streamLength, tenantOf } from '../support/env';
import { expect, test } from '../support/fixtures';

const AUG = '2026-08-20T03:00:00Z'; // KST 08-20 — "이번 달" 202608
const FEB = '2027-02-20T03:00:00Z'; // KST 2027-02-20 — 202702

test.describe.configure({ mode: 'serial' });

test('202608: 예상 → 확정(세후는 지급 시 확정) → 지급완료, 한도 소진 카드', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA01');
  await advance(t, 5);
  await portal.open('/', t, 'agent', AUG);
  const card = page.getByTestId('month-card');
  await expect(card.getByTestId('month-status')).toHaveText('예상');
  await expect(card.getByTestId('month-big')).toHaveText('세전1,800,000원예상세후는 야간 산출 후 표시');
  await expect(page.getByTestId('attention-limits')).toHaveText('한도 주의 1건');
  await portal.check('ga01 home provisional');

  await page.getByRole('link', { name: '한도 현황 보기' }).click();
  const ledger = page.getByTestId('ledger-card');
  await expect(ledger.getByTestId('stage')).toHaveText('소진');
  await expect(ledger.getByRole('img')).toHaveAccessibleName('소진율 100.0%');
  await portal.check('ga01 limits full');

  await advance(t, 6);
  await portal.open('/', t, 'agent', AUG);
  await expect(card.getByTestId('month-status')).toHaveText('확정');
  await expect(card.getByTestId('month-big')).toHaveText('세전1,800,000원확정세후는 지급 시 확정');

  await advance(t, 7);
  await portal.open('/', t, 'agent', AUG);
  await expect(card.getByTestId('month-status')).toHaveText('지급완료');
  await expect(card.getByTestId('month-big')).toHaveText('실수령1,740,600원지급완료');
  await expect(card.getByTestId('row-tax')).toHaveText('원천세59,400원지급완료');
  await expect(card.getByTestId('paid-on')).toHaveText('지급일 2026-09-10');
  await portal.check('ga01 home paid');
});

test('202702: 환수 상계와 잠정 명세(예상, 순액 0) — 예상 이월은 보이지 않는다', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA01');
  await advance(t, 11);
  await portal.open('/', t, 'agent', FEB);
  const card = page.getByTestId('month-card');
  await expect(card.getByTestId('month-status')).toHaveText('예상');
  await expect(card.getByTestId('month-big')).toHaveText('실수령0원예상');
  await expect(card.getByTestId('row-clawback')).toHaveText('환수 상계-720,000원예상');
  await expect(card.getByTestId('estimate-as-of')).toHaveText('기준 02-16 02:10');
  await expect(page.getByText('720,000원', { exact: false })).toHaveCount(1); // 환수 상계 한 곳 — 이월 채권 720,000은 없다(Q5)
  await expect(page.getByTestId('attention-limits')).toHaveText('한도 주의 0건');
  await portal.check('ga01 home estimated');
});

test('홈 → 명세 → 라인 상세 → 한도 → 상세 (이웃 설계사 데이터 0)', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA01');
  await advance(t, streamLength(t));
  await portal.open('/', t, 'agent', AUG);
  await expect(page.getByTestId('sync-chip')).toHaveText(/^기준 \d{2}-\d{2} \d{2}:\d{2}$/);
  await page.getByRole('link', { name: '월별 명세 보기' }).click();
  await expect(page).toHaveURL(/\/statements\/202608$/);
  await expect(page.getByTestId('by-type-FY_COMM')).toHaveText('초년도945,000원지급완료');
  await expect(page.getByTestId('by-type-INCENTIVE')).toHaveText('시책855,000원지급완료');
  await expect(page.getByTestId('lines').getByTestId('line')).toHaveCount(2);
  await portal.check('ga01 statement');

  await page.getByTestId('line').nth(1).getByRole('link').click();
  await expect(page).toHaveURL(/\/lines\/2$/);
  await expect(page.getByTestId('line-limit').getByTestId('limit-cut')).toContainText('한도 초과 삭감 145,000원지급완료');
  await expect(page.getByTestId('limit-cut-defer-note')).toHaveText('(사규: 초년도 후 이연 — 현재 이연 스케줄 없음)');
  await expect(page.getByTestId('policy-masked')).toHaveText('*********0001');
  await portal.check('ga01 line');

  await page.getByRole('link', { name: '이 계약의 한도 보기' }).click();
  await expect(page).toHaveURL(/\/limits\/1$/);
  await expect(page.getByTestId('limit-formula')).toHaveText('월납 150,000원원장 기준 × 12배 → 한도 1,800,000원원장 기준');
  await expect(page.getByTestId('flag-CANCELLED')).toHaveText('해약');
  await expect(page.getByTestId('history-item')).toHaveCount(3);
  await expect(page.getByTestId('limit-summary')).toContainText('한도 초과 삭감 145,000원원장 기준(사규: 초년도 후 이연 — 현재 이연 스케줄 없음)');
  await portal.check('ga01 limit detail');

  await page.getByRole('link', { name: '한도 현황' }).click();
  await expect(page.getByTestId('limits-empty')).toBeVisible(); // 주의 필터: 60%는 정상
  await page.getByTestId('filter-all').click();
  await expect(page.getByTestId('ledger-card')).toHaveCount(1);
  await portal.check('ga01 limits all');
});

test('동기화 지연 칩 — 피드가 응답하지 않으면 기준 시각과 함께', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA01');
  await failFeed(t, true);
  try {
    await expect
      .poll(
        async () => {
          await portal.open('/', t, 'agent', AUG);
          return page.getByTestId('sync-chip').textContent();
        },
        { timeout: 60_000, intervals: [3_000] },
      )
      .toMatch(/^동기화 지연 — 기준 \d{2}-\d{2} \d{2}:\d{2}$/);
    await portal.check('ga01 sync delayed');
  } finally {
    await failFeed(t, false);
  }
});
