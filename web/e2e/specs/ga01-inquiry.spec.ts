// GA01 — 이의제기 왕복(포털 Phase 6 계획 ⑨ E5·E7): 라인 상세 → 문의 작성(본문에 문의 본문 센티널 셋 — 허구, pii-sentinels.properties inquiry) → 상세 →
// 하네스가 실 CLI로 답변(inquiry answer — 파일로만) → 홈 안읽음 +1 → INQUIRY_ANSWERED 알림 → 상세의 답변 → 읽음.
// 본문은 작성·상세 화면의 DOM과 상세 응답에만 있어도 된다 — 고정 장치가 그 밖(다른 화면 DOM·다른 응답·콘솔·주소·요청 주소)을 전부 보고, run.mjs가 산출물을 본다.
// 같은 프로젝트의 ga01-a1이 먼저 돈다(파일 이름 순 — 머리가 끝까지 열려 있다).
import { ANSWER, advance, answer, me, streamLength, tenantOf } from '../support/env';
import { expect, test } from '../support/fixtures';
import type { NotificationPage } from '../support/inbox';
import { inquirySentinels } from '../support/sentinels.mjs';

const FEB = '2027-02-20T03:00:00Z';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

test('라인 문의 → CLI 답변 → 알림 → 상세의 답변', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA01');
  await advance(t, streamLength(t));
  const [name, phone, policy] = inquirySentinels();
  const body = `고객 ${name ?? ''} 님(${phone ?? ''}) 계약 ${policy ?? ''}의 삭감 금액이 맞는지 확인 부탁드립니다`;

  await portal.open('/lines/2', t, 'agent', FEB);
  await page.getByTestId('entry-inquiry').click();
  await expect(page).toHaveURL(/\/inquiries\/new\?calcId=2$/);
  await expect(page.getByTestId('inquiry-notice')).toBeVisible();
  await page.getByTestId('inquiry-category').getByRole('button', { name: '한도' }).click();
  await page.getByTestId('inquiry-message').fill(body);
  await expect(page.getByTestId('inquiry-remaining')).toHaveText(`${String(1000 - Array.from(body).length)}자 남음`);
  await portal.check('ga01 inquiry compose');
  const created = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/v1/me/inquiries' && r.request().method() === 'POST');
  await page.getByTestId('inquiry-submit').click();
  const response = await created;
  expect(response.status()).toBe(201);
  const id = ((await response.json()) as { id: string }).id;
  expect(id).toMatch(UUID);
  await expect(page).toHaveURL(new RegExp(`/inquiries/${id}$`));
  await expect(page.getByTestId('inquiry-body')).toHaveText(body);
  await expect(page.getByTestId('inquiry-status')).toHaveText('접수됨');
  await portal.check('ga01 inquiry detail open');
  // 상세에서 앱 안 이동(다시 불러오기 없이)으로 나간 화면에도 본문이 남지 않는다 — 문서 제목·남은 노드 등(주입 S14)
  await page.getByTestId('inquiry-line').click();
  await expect(page).toHaveURL(/\/lines\/2$/);
  await expect(page.getByTestId('line-detail')).toBeVisible();
  await portal.check('ga01 line after the inquiry detail');

  // 목록에는 본문이 없다(계약 — 상세에만)
  await portal.open('/inquiries', t, 'agent', FEB);
  await expect(page.getByTestId('inquiries').locator('li')).toHaveCount(1);
  await portal.check('ga01 inquiries');

  const unreadBefore = (await me<NotificationPage>(t, 'notifications?size=1')).unreadCount;
  expect(await answer(t, id), 'inquiry answer exit code').toBe(0);

  await portal.open('/', t, 'agent', FEB);
  const recent = page.getByTestId('recent-notifications');
  await expect(recent.getByTestId('unread-count')).toHaveText(`안읽음 ${String(unreadBefore + 1)}건`);
  const answered = recent.locator('[data-testid="notification"][data-type="INQUIRY_ANSWERED"]');
  await expect(answered).toHaveText(/^안읽음 문의에 답변이 등록되었습니다/);
  await portal.check('ga01 home answered');
  await answered.click();
  await expect(page).toHaveURL(new RegExp(`/inquiries/${id}$`));
  await expect(page.getByTestId('inquiry-status')).toHaveText('답변 완료');
  await expect(page.getByTestId('inquiry-answer')).toContainText(ANSWER);
  await expect(page.getByTestId('inquiry-body')).toHaveText(body);
  await expect.poll(async () => (await me<NotificationPage>(t, 'notifications?size=1')).unreadCount).toBe(unreadBefore);
  await portal.check('ga01 inquiry detail answered');
  await page.getByTestId('inquiry-line').click();
  await expect(page).toHaveURL(/\/lines\/2$/);
  await portal.check('ga01 line after the answered detail');
});
