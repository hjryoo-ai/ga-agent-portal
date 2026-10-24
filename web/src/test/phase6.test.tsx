// 포털 Phase 6 화면 시험(C10): 알림 문장(템플릿 — 금액 없음), 안읽음 표식(굵게·점·숨은 글자)과 홈의 안읽음 수, 누르면 읽음(PATCH) → 닫힌 경로 넷이면 이동·
// 아니면 읽음만(승인 A), 알림함 "더 보기", 문의 작성(분류 — 라인 없으면 "기타"만, 고정 문구, 남은 글자 수 = 코드 포인트, 넘으면 보내지 않음, 보내면 상세로,
// 429 — Retry-After 그대로·입력 유지), 상세(본문·답변·라인 링크), 라인·월 명세의 문의 진입점(준비 중 0).
import { QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createQueryClient, type Notification } from '../api/queries';
import { bootToken, dropToken } from '../boot/token';
import messages from '../messages.ko.json';
import { HomeScreen } from '../screens/HomeScreen';
import { InquiryNewScreen } from '../screens/InquiryNewScreen';
import { InquiryScreen } from '../screens/InquiryScreen';
import { NotificationsScreen } from '../screens/NotificationsScreen';
import { asOf, ledger } from './fixtures';

interface Reply {
  status: number;
  body: unknown;
  headers?: Record<string, string>;
}

const net = vi.hoisted(() => {
  const gets = new Map<string, unknown>();
  const posts = new Map<string, Reply[]>();
  const calls: { method: string; url: string; body: string }[] = [];
  globalThis.fetch = async (input: RequestInfo | URL) => {
    const request = input instanceof Request ? input : new Request(String(input));
    const url = new URL(request.url, 'http://portal.test');
    const path = url.pathname + url.search;
    const body = request.method === 'POST' ? await request.text() : '';
    calls.push({ method: request.method, url: path, body });
    const json = (status: number, payload: unknown, headers: Record<string, string> = {}) =>
      new Response(JSON.stringify(payload), {
        status,
        headers: { 'Content-Type': status < 300 ? 'application/json' : 'application/problem+json', ...headers },
      });
    if (request.method === 'POST' || request.method === 'PATCH') {
      const reply = posts.get(`${request.method} ${url.pathname}`)?.shift();
      return reply === undefined ? json(404, { code: 'NOT_FOUND', message: 'not found' }) : json(reply.status, reply.body, reply.headers);
    }
    const found = gets.get(path) ?? gets.get(url.pathname);
    return found === undefined ? json(404, { code: 'NOT_FOUND', message: 'not found' }) : json(200, found);
  };
  return { gets, posts, calls };
});

const ID_1 = '00000000-0000-4000-8000-000000000a01';
const ID_2 = '00000000-0000-4000-8000-000000000a02';
const ID_3 = '00000000-0000-4000-8000-000000000a03';
const INQ = '00000000-0000-4000-8000-000000000b01';

const cut: Notification = {
  id: ID_1,
  type: 'LIMIT_CUT',
  severity: 'WARN',
  createdAt: '2026-10-20T02:00:00Z',
  link: '/lines/C-9',
  params: { calcId: 'C-9', policyMasked: '*********0001', closeYm: '202610' },
};
const cancel: Notification = {
  id: ID_2,
  type: 'POLICY_CANCEL',
  severity: 'WARN',
  createdAt: '2026-10-20T01:00:00Z',
  params: { policyMasked: '*********0001', occurredOn: '2026-10-19' },
};
const payout: Notification = {
  id: ID_3,
  type: 'PAYOUT',
  severity: 'INFO',
  createdAt: '2026-10-19T01:00:00Z',
  readAt: '2026-10-19T02:00:00Z',
  link: '/statements/202609',
  params: { ym: '202609' },
};

function page(items: Notification[], over: { page?: number; size?: number; total?: number; unreadCount?: number } = {}) {
  return { items, page: over.page ?? 0, size: over.size ?? 3, total: over.total ?? items.length, unreadCount: over.unreadCount ?? items.filter((n) => n.readAt === undefined).length };
}

beforeEach(() => {
  bootToken({ hash: '#token=a.b.c', pathname: '/', search: '' }, { replaceState: () => undefined });
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-10-21T00:30:00Z'));
  net.gets.set('/api/v1/me/limits?status=ALL', { asOf, dataStatus: 'OK', status: 'ALL', items: [ledger()] });
  net.gets.set('/api/v1/me/deferrals?from=202611&to=202611', { asOf, dataStatus: 'OK', from: '202611', to: '202611', scenario: 'ALL_INFORCE', months: [], items: [] });
});

afterEach(() => {
  cleanup();
  net.gets.clear();
  net.posts.clear();
  net.calls.length = 0;
  dropToken();
  vi.useRealTimers();
});

/** 지금 주소를 보이는 칸 — 이동했는지 본다. */
function Where() {
  const location = useLocation();
  return <p data-testid="where">{location.pathname + location.search}</p>;
}

function show(path: string, pattern: string, element: ReactNode) {
  return render(
    <QueryClientProvider client={createQueryClient()}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path={pattern} element={element} />
          <Route path="*" element={<Where />} />
        </Routes>
        <Where />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const where = () => screen.getAllByTestId('where').at(-1)?.textContent;
const patches = () => net.calls.filter((c) => c.method === 'PATCH').map((c) => c.url);

describe('notifications', () => {
  it('home: the recent three as template sentences, unread marked three ways, the unread count from the response', async () => {
    net.gets.set('/api/v1/me/notifications?size=3', page([cut, cancel, payout], { total: 9, unreadCount: 7 }));
    show('/', '/', <HomeScreen />);
    const items = await screen.findAllByTestId('notification');
    expect(items.map((i) => i.querySelector('.notice__text')?.textContent)).toEqual([
      '2026년 10월 계약 *********0001의 수수료 일부가 한도 초과로 삭감되었습니다',
      '계약 *********0001의 해약이 확인되었습니다(2026-10-19)',
      '2026년 9월 수수료 지급이 끝났습니다',
    ]);
    expect(screen.getByTestId('unread-count').textContent).toBe('안읽음 7건');
    const [first, , last] = items;
    expect(first?.className).toContain('notice--unread');
    expect(first?.querySelector('.notice__dot')).not.toBeNull();
    expect(first?.textContent).toContain('안읽음');
    expect(last?.className).not.toContain('notice--unread');
    expect(last?.textContent).not.toContain('안읽음');
    expect(screen.getByTestId('recent-notifications').textContent).not.toMatch(/[0-9],?[0-9]*원/);
  });

  it('home: no notification says so', async () => {
    net.gets.set('/api/v1/me/notifications?size=3', page([]));
    show('/', '/', <HomeScreen />);
    expect((await screen.findByTestId('notifications-none')).textContent).toBe('새 알림이 없습니다');
    expect(screen.queryByTestId('unread-count')).toBeNull();
  });

  it('pressing an unread notification marks it read, then follows its link', async () => {
    net.gets.set('/api/v1/me/notifications?size=3', page([cut]));
    net.posts.set(`PATCH /api/v1/me/notifications/${ID_1}/read`, [{ status: 200, body: { ...cut, readAt: '2026-10-21T00:30:00Z' } }]);
    show('/', '/', <HomeScreen />);
    fireEvent.click(await screen.findByTestId('notification'));
    await waitFor(() => {
      expect(where()).toBe('/lines/C-9');
    });
    expect(patches()).toEqual([`/api/v1/me/notifications/${ID_1}/read`]);
  });

  it('a notification without a link is only marked read; a read one with a link just goes', async () => {
    net.gets.set('/api/v1/me/notifications?page=0&size=20', page([cancel, payout], { size: 20 }));
    net.posts.set(`PATCH /api/v1/me/notifications/${ID_2}/read`, [{ status: 200, body: { ...cancel, readAt: '2026-10-21T00:30:00Z' } }]);
    show('/notifications', '/notifications', <NotificationsScreen />);
    const [policy, paid] = await screen.findAllByTestId('notification');
    fireEvent.click(policy as HTMLElement);
    await waitFor(() => {
      expect(patches()).toEqual([`/api/v1/me/notifications/${ID_2}/read`]);
    });
    expect(where()).toBe('/notifications');
    fireEvent.click(paid as HTMLElement);
    await waitFor(() => {
      expect(where()).toBe('/statements/202609');
    });
    expect(patches()).toHaveLength(1);
  });

  // 승인 A(주입 S15의 짝): 서버가 닫힌 넷 밖의 link를 보내도 화면은 그리로 가지 않는다 — 읽음만
  it('a link outside the four screen paths is not followed', async () => {
    const odd = { ...cut, link: '//evil.example/lines/C-9' } as Notification;
    net.gets.set('/api/v1/me/notifications?size=3', page([odd]));
    net.posts.set(`PATCH /api/v1/me/notifications/${ID_1}/read`, [{ status: 200, body: { ...odd, readAt: '2026-10-21T00:30:00Z' } }]);
    show('/', '/', <HomeScreen />);
    fireEvent.click(await screen.findByTestId('notification'));
    await waitFor(() => {
      expect(patches()).toHaveLength(1);
    });
    expect(where()).toBe('/');
  });

  it('the inbox shows newest first and "더 보기" fetches the next page; there is no "mark all read"', async () => {
    const many = Array.from({ length: 20 }, (_, i) => ({ ...payout, id: `00000000-0000-4000-8000-0000000001${String(i).padStart(2, '0')}` }));
    net.gets.set('/api/v1/me/notifications?page=0&size=20', page(many, { size: 20, total: 21 }));
    net.gets.set('/api/v1/me/notifications?page=1&size=20', page([cut], { page: 1, size: 20, total: 21 }));
    show('/notifications', '/notifications', <NotificationsScreen />);
    expect(await screen.findAllByTestId('notification')).toHaveLength(20);
    fireEvent.click(screen.getByTestId('notifications-more'));
    await waitFor(() => {
      expect(screen.getAllByTestId('notification')).toHaveLength(21);
    });
    expect(screen.queryByTestId('notifications-more')).toBeNull();
    expect(screen.queryByText(/모두 읽음/)).toBeNull();
  });
});

describe('inquiries', () => {
  it('without a line only "기타"; with a line all five; the fixed notice and the remaining count in code points', async () => {
    show('/inquiries/new', '/inquiries/new', <InquiryNewScreen />);
    const only = within(await screen.findByTestId('inquiry-category')).getAllByRole('button');
    expect(only.map((b) => b.textContent)).toEqual(['기타']);
    expect(screen.getByTestId('inquiry-notice').textContent).toBe('고객 이름·연락처·증권번호를 쓰지 마세요');
    expect(screen.getByTestId('inquiry-remaining').getAttribute('aria-live')).toBe('polite');
    fireEvent.change(screen.getByTestId('inquiry-message'), { target: { value: '😀😀가' } });
    expect(screen.getByTestId('inquiry-remaining').textContent).toBe('997자 남음');
    cleanup();
    show('/inquiries/new?calcId=C-9', '/inquiries/new', <InquiryNewScreen />);
    expect(within(await screen.findByTestId('inquiry-category')).getAllByRole('button').map((b) => b.textContent)).toEqual([
      '금액',
      '한도',
      '환수',
      '분급',
      '기타',
    ]);
  });

  it('over 1,000 code points nothing is sent; within it the body is sent as typed and the detail replaces the form', async () => {
    net.posts.set('POST /api/v1/me/inquiries', [
      { status: 201, body: { id: INQ, status: 'OPEN', category: 'AMOUNT', calcId: 'C-9', createdAt: '2026-10-21T00:30:00Z' } },
    ]);
    show('/inquiries/new?calcId=C-9', '/inquiries/new', <InquiryNewScreen />);
    const box = await screen.findByTestId('inquiry-message');
    fireEvent.change(box, { target: { value: '😀'.repeat(1001) } });
    expect(screen.getByTestId('inquiry-remaining').textContent).toBe('1자 넘었습니다');
    fireEvent.click(screen.getByTestId('inquiry-submit'));
    expect(net.calls.filter((c) => c.method === 'POST')).toHaveLength(0);
    fireEvent.change(box, { target: { value: '이번 달 금액 확인 부탁드립니다' } });
    fireEvent.click(screen.getByTestId('inquiry-submit'));
    await waitFor(() => {
      expect(where()).toBe(`/inquiries/${INQ}`);
    });
    expect(JSON.parse(net.calls.find((c) => c.method === 'POST')?.body ?? '{}')).toEqual({ category: 'AMOUNT', message: '이번 달 금액 확인 부탁드립니다', calcId: 'C-9' });
  });

  it('429 shows the Retry-After seconds as sent and keeps what was typed', async () => {
    net.posts.set('POST /api/v1/me/inquiries', [{ status: 429, body: { code: 'RATE_LIMITED', message: 'too many inquiries' }, headers: { 'Retry-After': '86321' } }]);
    show('/inquiries/new', '/inquiries/new', <InquiryNewScreen />);
    const box = await screen.findByTestId('inquiry-message');
    fireEvent.change(box, { target: { value: '기타 문의' } });
    fireEvent.click(screen.getByTestId('inquiry-submit'));
    expect((await screen.findByTestId('inquiry-error')).textContent).toBe('오늘 문의 한도를 넘었습니다 — 86321초 뒤 다시 시도해 주세요');
    expect((box as HTMLTextAreaElement).value).toBe('기타 문의');
    expect(where()).toBe('/inquiries/new');
  });

  it('the detail shows the body, the answer and a link to the line', async () => {
    net.gets.set(`/api/v1/me/inquiries/${INQ}`, {
      id: INQ,
      status: 'ANSWERED',
      category: 'AMOUNT',
      calcId: 'C-9',
      message: '첫 줄\n둘째 줄',
      createdAt: '2026-10-20T00:00:00Z',
      answeredAt: '2026-10-20T03:00:00Z',
      answer: '확인했습니다',
    });
    show(`/inquiries/${INQ}`, '/inquiries/:id', <InquiryScreen />);
    expect((await screen.findByTestId('inquiry-body')).textContent).toBe('첫 줄\n둘째 줄');
    expect(screen.getByTestId('inquiry-status').textContent).toBe('답변 완료');
    expect(screen.getByTestId('inquiry-answer').textContent).toContain('확인했습니다');
    expect(screen.getByTestId('inquiry-line').getAttribute('href')).toBe('/lines/C-9');
  });

  it('no "coming soon" is left anywhere', () => {
    const all = (o: object): string[] => Object.values(o).flatMap((v) => (typeof v === 'string' ? [v] : all(v as object)));
    expect(all(messages).filter((t) => t.includes('준비 중'))).toEqual([]);
    expect(Object.keys(messages.nav)).not.toContain('comingSoon');
  });
});
