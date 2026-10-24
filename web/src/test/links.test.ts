// @vitest-environment node
// 알림 link 대조(포털 Phase 6 계획 승인 A — 주입 S15): 맞는 넷(닫힌 화면 경로 넷)만 이동 경로가 되고, 밖 출처 URL·`//`·모르는 경로와 그 밖의 모양은 null(읽음만).
import { describe, expect, it } from 'vitest';
import { LINKABLE, screenPath } from '../notify/links';
import { ROUTE_PATHS } from '../routes';

describe('screenPath', () => {
  it('the four linkable screens are route paths', () => {
    expect(LINKABLE.map((l) => l.path)).toEqual(['/lines/:calcId', '/limits/:ledgerId', '/statements/:ym', '/inquiries/:id']);
    for (const l of LINKABLE) expect(ROUTE_PATHS).toContain(l.path);
  });

  it.each([
    '/lines/C-1%2F2',
    '/limits/EDG-LEDGER-1',
    '/statements/202609',
    '/inquiries/00000000-0000-4000-8000-000000000602',
  ])('follows %s', (link) => {
    expect(screenPath(link)).toBe(link);
  });

  it.each([
    'https://evil.example/lines/1',
    '//evil.example/lines/1',
    '/admin/1',
  ])('refuses %s (approval A)', (link) => {
    expect(screenPath(link)).toBeNull();
  });

  it.each([
    undefined,
    '',
    '/',
    'lines/1',
    '/lines/',
    '/lines/1/2',
    '/lines/1?x=1',
    '/lines/1#x',
    '/lines/..',
    '/lines/a\\b',
    '/statements/2026-09',
    '/inquiries/new',
    '/inquiries/not-a-uuid',
    'javascript:alert(1)',
    '/LINES/1',
  ])('refuses the other shapes: %s', (link) => {
    expect(screenPath(link)).toBeNull();
  });
});
