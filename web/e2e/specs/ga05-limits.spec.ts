// GA05 — 포털 손 스트림(web/e2e/streams/ga05-limits.jsonl, 포털 Phase 6 계획 ⑨·Q26): 원장 하나(GA5-LEDGER-1, 한도 1,000,000)가 OK → 85% WARN(5) → 환수 음수 전기로
// 65% OK(7) → 다시 85% WARN(9) → 90%(11 — 같은 단계, 알림 0) → 재산정 900,000(12 — 단계는 FULL이 되지만 LIMIT_REBASE 하나). 문의 한도 3(params.inquiry_rate_per_day).
//  E3 재상향은 새 알림(응답 id가 다르다) · E4 재구축(실 CLI) 뒤 알림 응답이 바이트로 같다(읽음 포함) · E6 넷째 문의 429.
import { advance, me, rebuild, streamLength, tenantOf } from '../support/env';
import { expect, test } from '../support/fixtures';
import { openInbox, sorted, type NotificationPage } from '../support/inbox';

const OCT = '2026-10-24T03:00:00Z';

test.describe.configure({ mode: 'serial' });

test('한 원장의 상향·하향·재상향은 알림 둘, 같은 단계는 0, 재산정은 LIMIT_REBASE', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA05');
  await advance(t, 5);
  expect((await openInbox(page, portal, t, OCT)).types).toEqual(['LIMIT_WARN']);
  await advance(t, 7);
  expect(sorted((await openInbox(page, portal, t, OCT)).types), 'clawback down to OK — no limit notification').toEqual(['CLAWBACK', 'LIMIT_WARN']);
  await advance(t, 9);
  const risen = await openInbox(page, portal, t, OCT);
  expect(sorted(risen.types)).toEqual(['CLAWBACK', 'LIMIT_WARN', 'LIMIT_WARN']);
  const warnIds = risen.response.items.filter((n) => n.type === 'LIMIT_WARN').map((n) => n.id);
  expect(new Set(warnIds).size, 'the second rise is a new notification').toBe(2);
  await expect(page.getByTestId('notification').filter({ hasText: '계약 *********0001의 한도 사용이 주의 단계에 들어섰습니다' })).toHaveCount(2);
  await portal.check('ga05 inbox risen again');

  await advance(t, 11);
  expect((await openInbox(page, portal, t, OCT)).response.total, 'same stage — none').toBe(3);
  await advance(t, streamLength(t));
  const rebased = await openInbox(page, portal, t, OCT);
  expect(sorted(rebased.types)).toEqual(['CLAWBACK', 'LIMIT_REBASE', 'LIMIT_WARN', 'LIMIT_WARN']);
  await expect(page.getByTestId('notification').filter({ hasText: '계약 *********0001의 한도가 다시 산정되었습니다' })).toHaveCount(1);
  await portal.check('ga05 inbox rebased');
});

test('재구축(실 CLI) 뒤 알림은 수·항목·읽음 그대로', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA05');
  await advance(t, streamLength(t));
  await openInbox(page, portal, t, OCT);
  await page.getByTestId('notification').filter({ hasText: '다시 산정되었습니다' }).click();
  await expect(page).toHaveURL(/\/limits\/GA5-LEDGER-1$/);
  await expect.poll(async () => (await me<NotificationPage>(t, 'notifications?size=100')).unreadCount).toBe(3);
  const before = await me<NotificationPage>(t, 'notifications?size=100');

  expect(await rebuild(t), 'ingest rebuild exit code').toBe(0);
  expect(await me<{ asOf: { seq: number } }>(t, '').then((r) => r.asOf.seq)).toBe(streamLength(t));
  const after = await me<NotificationPage>(t, 'notifications?size=100');
  expect(after).toEqual(before);
  expect(after.items.filter((n) => n.readAt !== undefined).map((n) => n.type)).toEqual(['LIMIT_REBASE']);
  const inbox = await openInbox(page, portal, t, OCT);
  expect(inbox.response.items.map((n) => n.id)).toEqual(before.items.slice(0, 20).map((n) => n.id));
  await expect(page.locator('.notice--unread')).toHaveCount(3);
  await portal.check('ga05 inbox after rebuild');
});

test('문의 한도 3 — 넷째는 429, 입력은 그대로', async ({ page, portal }, info) => {
  const t = tenantOf(info.project.name, 'GA05');
  for (let i = 1; i <= 3; i++) {
    await portal.open('/inquiries/new', t, 'agent', OCT);
    await page.getByTestId('inquiry-message').fill(`한도 재산정 기준을 알고 싶습니다 (${String(i)})`);
    const created = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/v1/me/inquiries' && r.request().method() === 'POST');
    await page.getByTestId('inquiry-submit').click();
    expect((await created).status()).toBe(201);
    await expect(page).toHaveURL(/\/inquiries\/[0-9a-f-]{36}$/);
    await expect(page.getByTestId('inquiry-status')).toHaveText('접수됨');
  }
  await portal.open('/inquiries/new', t, 'agent', OCT);
  const text = '한도 재산정 기준을 알고 싶습니다 (4)';
  await page.getByTestId('inquiry-message').fill(text);
  const fourth = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/v1/me/inquiries' && r.request().method() === 'POST');
  await page.getByTestId('inquiry-submit').click();
  const r = await fourth;
  expect(r.status()).toBe(429);
  const retryAfter = r.headers()['retry-after'] ?? '';
  expect(retryAfter).toMatch(/^[1-9][0-9]*$/);
  await expect(page.getByTestId('inquiry-error')).toHaveText(`오늘 문의 한도를 넘었습니다 — ${retryAfter}초 뒤 다시 시도해 주세요`);
  await expect(page.getByTestId('inquiry-message')).toHaveValue(text);
  await expect(page).toHaveURL(/\/inquiries\/new$/);
  await portal.check('ga05 inquiry rate limited');

  await portal.open('/inquiries', t, 'agent', OCT);
  await expect(page.getByTestId('inquiries').locator('li')).toHaveCount(3);
  await portal.check('ga05 inquiries');
});
