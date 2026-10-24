// 알림함 읽기(포털 Phase 6 계획 ⑨): 알림함 화면을 열어 항목의 type(data-type)을 읽는다 — 화면에 보이는 것이 증거다. 응답(id)은 같은 탐색에서 잡는다.
import type { Page } from '@playwright/test';
import { expect, type Portal } from './fixtures';

export interface NotificationItem {
  id: string;
  type: string;
  readAt?: string;
}

export interface NotificationPage {
  items: NotificationItem[];
  total: number;
  unreadCount: number;
}

/** 알림함을 열고 (화면의 type 목록 — 최신순, 그 화면이 받은 첫 페이지 응답)을 돌려준다. */
export async function openInbox(page: Page, portal: Portal, tenant: string, at: string): Promise<{ types: string[]; response: NotificationPage }> {
  const answered = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/v1/me/notifications' && new URL(r.url()).searchParams.get('size') === '20');
  await portal.open('/notifications', tenant, 'agent', at);
  const response = (await (await answered).json()) as NotificationPage;
  const list = page.getByTestId('notifications').or(page.getByTestId('notifications-empty'));
  await expect(list).toBeVisible();
  await expect(page.getByTestId('notification')).toHaveCount(response.items.length);
  const types = await page.getByTestId('notification').evaluateAll((els) => els.map((e) => e.getAttribute('data-type') ?? ''));
  expect(types, 'the screen shows the response in order').toEqual(response.items.map((n) => n.type));
  return { types, response };
}

export const sorted = (types: string[]) => [...types].sort();
