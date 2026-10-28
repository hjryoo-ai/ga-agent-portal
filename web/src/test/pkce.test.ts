// PKCE 로그인(포털 Phase 8 지시문 ③, 계획 P-9·조건 B, C7): RFC 7636 벡터, 디스커버리 끝점 출처, 채널 규약(state 불일치·두 번째 메시지),
// 콜백의 주소 정리, 교환·회전·실패, 로그아웃, 그리고 저장소 5종 + window.name 0. 브라우저 기능은 Deps로 바꿔 끼운다(실제 IdP 없음 — compose E2E가 본다).
import { webcrypto } from 'node:crypto';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  CALLBACK_PATH,
  LoginError,
  base64url,
  challengeOf,
  channelReceiver,
  discover,
  finishCallback,
  logout,
  randomValue,
  refreshNow,
  startLogin,
  type Deps,
} from '../auth/oidc';
import { currentToken, dropToken, tokenSource } from '../boot/token';

const ISSUER = 'http://localhost:8180/realms/demo';
const CFG = { issuer: ISSUER, clientId: 'portal-web' };
const DISCOVERY = {
  issuer: ISSUER,
  authorization_endpoint: `${ISSUER}/protocol/openid-connect/auth`,
  token_endpoint: `${ISSUER}/protocol/openid-connect/token`,
  end_session_endpoint: `${ISSUER}/protocol/openid-connect/logout`,
};
const jwt = (n: number) => `eyJhbGciOiJSUzI1NiJ9.eyJuIjo${String(n)}fQ.c2ln${String(n)}`;

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

interface FakeChannel {
  name: string;
  closed: boolean;
  onmessage: ((ev: MessageEvent) => void) | null;
  close: () => void;
  deliver: (data: unknown) => void;
}

/** 시험용 브라우저: 기록하는 fetch, 결정적 난수, 실제 SHA-256, 가짜 팝업·채널·타이머. */
function fakeBrowser(tokenResponses: Response[] = []) {
  const calls: { url: string; body: string }[] = [];
  const channels: FakeChannel[] = [];
  const timers: { run: () => void; ms: number; cleared: boolean }[] = [];
  const popup = { closed: false, location: { href: '' }, close: vi.fn() };
  let counter = 0;
  const deps: Deps = {
    http: (input, init) => {
      const url = input instanceof Request ? input.url : String(input);
      calls.push({ url, body: init?.body instanceof URLSearchParams ? init.body.toString() : '' });
      if (url.endsWith('/.well-known/openid-configuration')) return Promise.resolve(json(DISCOVERY));
      if (url === DISCOVERY.end_session_endpoint) return Promise.resolve(new Response(null, { status: 204 }));
      return Promise.resolve(tokenResponses.shift() ?? json({ error: 'invalid_grant' }, 400));
    },
    random: (bytes) => {
      counter++;
      bytes.fill(counter);
      return bytes;
    },
    digest: (data) => webcrypto.subtle.digest('SHA-256', data),
    openPopup: () => popup as unknown as Window,
    channel: (name) => {
      const c: FakeChannel = {
        name,
        closed: false,
        onmessage: null,
        close() {
          c.closed = true;
        },
        deliver(data) {
          if (!c.closed) c.onmessage?.({ data } as MessageEvent);
        },
      };
      channels.push(c);
      return c as unknown as BroadcastChannel;
    },
    origin: 'http://localhost:8080',
    setTimer: (run, ms) => {
      timers.push({ run, ms, cleared: false });
      return (timers.length - 1) as unknown as ReturnType<typeof setTimeout>;
    },
    clearTimer: (t) => {
      const timer = timers[t as unknown as number];
      if (timer !== undefined) timer.cleared = true;
    },
  };
  return { deps, calls, channels, timers, popup };
}

/** 팝업이 (새) 인가 주소로 보내질 때까지 — 디스커버리·SHA-256이 비동기다. */
async function navigated(popup: { location: { href: string } }, before = ''): Promise<void> {
  await vi.waitFor(() => {
    expect(popup.location.href).not.toBe(before);
  });
}

/** 팝업 주소의 state로 콜백 메시지를 보낸다. */
function stateOf(popupHref: string): string {
  return new URL(popupHref).searchParams.get('state') ?? '';
}

afterEach(() => {
  dropToken();
});

describe('PKCE values', () => {
  it('the S256 challenge of the RFC 7636 appendix B verifier', async () => {
    expect(await challengeOf('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk', { digest: (d) => webcrypto.subtle.digest('SHA-256', d) })).toBe(
      'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM',
    );
  });

  it('verifier and state are 43 base64url characters from 32 random bytes', () => {
    const v = randomValue({ random: (b) => webcrypto.getRandomValues(b) });
    expect(v).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(base64url(new Uint8Array([251, 255]))).toBe('-_8');
  });
});

describe('discovery', () => {
  const with_ = (doc: object) => ({ http: () => Promise.resolve(json(doc)) });

  it('accepts endpoints on the issuer origin', async () => {
    expect(await discover(CFG, with_(DISCOVERY))).toEqual({
      authorization: DISCOVERY.authorization_endpoint,
      token: DISCOVERY.token_endpoint,
      endSession: DISCOVERY.end_session_endpoint,
    });
  });

  it('rejects another issuer or an endpoint on another origin', async () => {
    await expect(discover(CFG, with_({ ...DISCOVERY, issuer: `${ISSUER}/` }))).rejects.toThrow(LoginError);
    await expect(discover(CFG, with_({ ...DISCOVERY, token_endpoint: 'https://evil.example/token' }))).rejects.toThrow(LoginError);
    await expect(discover(CFG, with_({ ...DISCOVERY, end_session_endpoint: 'http://localhost:8181/logout' }))).rejects.toThrow(LoginError);
    await expect(discover(CFG, { http: () => Promise.resolve(json({}, 500)) })).rejects.toThrow(LoginError);
  });
});

describe('channel (condition B)', () => {
  it('channelRejectsAForeignState', () => {
    const close = vi.fn();
    const onCode = vi.fn();
    const receive = channelReceiver('S'.repeat(43), close, onCode);
    receive({ state: 'T'.repeat(43), code: 'abc' });
    receive({ state: 'S'.repeat(43), code: 'abc', extra: 1 });
    receive('S');
    expect(onCode).not.toHaveBeenCalled();
    expect(close).not.toHaveBeenCalled();
    receive({ state: 'S'.repeat(43), code: 'abc' });
    expect(onCode).toHaveBeenCalledExactlyOnceWith('abc');
  });

  it('channelIgnoresASecondMessage', () => {
    const close = vi.fn();
    const onCode = vi.fn();
    const receive = channelReceiver('S'.repeat(43), close, onCode);
    receive({ state: 'S'.repeat(43), code: 'first' });
    receive({ state: 'S'.repeat(43), code: 'second' });
    expect(onCode).toHaveBeenCalledExactlyOnceWith('first');
    expect(close).toHaveBeenCalledOnce();
  });

  it('a new attempt closes the previous channel — its messages are dropped', async () => {
    const b = fakeBrowser([json({ access_token: jwt(1), refresh_token: 'r1', expires_in: 300 })]);
    const first = startLogin(CFG, b.deps);
    first.catch(() => undefined);
    await navigated(b.popup);
    const firstHref = b.popup.location.href;
    const firstState = stateOf(firstHref);
    const second = startLogin(CFG, b.deps);
    await navigated(b.popup, firstHref);
    expect(b.channels[0]?.closed).toBe(true);
    b.channels[0]?.deliver({ state: firstState, code: 'old' });
    expect(b.calls.filter((c) => c.url === DISCOVERY.token_endpoint)).toHaveLength(0);
    b.channels[1]?.deliver({ state: stateOf(b.popup.location.href), code: 'new' });
    await second;
    expect(b.calls.find((c) => c.url === DISCOVERY.token_endpoint)?.body).toContain('code=new');
  });
});

describe('callback page', () => {
  it('callbackClearsTheAddress before handing over, and hands over only well-formed values', () => {
    const order: string[] = [];
    const post = vi.fn(() => order.push('post'));
    const close = vi.fn(() => order.push('close'));
    const replaceState = vi.fn(() => order.push('replace'));
    const state = 'A'.repeat(43);
    expect(finishCallback({ pathname: CALLBACK_PATH, search: `?state=${state}&code=c0de.x&session_state=1` }, { replaceState }, post, close)).toBe(true);
    expect(replaceState).toHaveBeenCalledExactlyOnceWith(null, '', CALLBACK_PATH);
    expect(post).toHaveBeenCalledExactlyOnceWith(`pkce:${state}`, { state, code: 'c0de.x' });
    expect(order).toEqual(['replace', 'post', 'close']);

    post.mockClear();
    expect(finishCallback({ pathname: CALLBACK_PATH, search: '?error=access_denied&state=short' }, { replaceState }, post, close)).toBe(true);
    expect(post).not.toHaveBeenCalled();
    expect(finishCallback({ pathname: '/statements', search: '?code=x' }, { replaceState }, post, close)).toBe(false);
  });
});

describe('login, rotation and logout', () => {
  it('opens the popup at the authorization endpoint with S256 and exchanges the code once', async () => {
    const b = fakeBrowser([json({ access_token: jwt(1), refresh_token: 'r1', expires_in: 300, id_token: jwt(9) })]);
    const done = startLogin(CFG, b.deps);
    await navigated(b.popup);
    const url = new URL(b.popup.location.href);
    expect(url.origin + url.pathname).toBe(DISCOVERY.authorization_endpoint);
    expect(Object.fromEntries(url.searchParams)).toMatchObject({
      response_type: 'code',
      client_id: 'portal-web',
      redirect_uri: 'http://localhost:8080/auth/callback',
      scope: 'openid',
      code_challenge_method: 'S256',
    });
    expect(url.searchParams.get('code_challenge')).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(url.searchParams.has('code_verifier')).toBe(false);
    b.channels[0]?.deliver({ state: stateOf(b.popup.location.href), code: 'the-code' });
    await done;
    const exchange = new URLSearchParams(b.calls.find((c) => c.url === DISCOVERY.token_endpoint)?.body);
    expect(exchange.get('grant_type')).toBe('authorization_code');
    expect(exchange.get('code_verifier')).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(await challengeOf(exchange.get('code_verifier') ?? '', b.deps)).toBe(url.searchParams.get('code_challenge'));
    expect(currentToken()).toBe(jwt(1));
    expect(tokenSource()).toBe('pkce');
    expect(b.channels[0]?.closed).toBe(true);
    expect(b.timers.at(-1)?.ms).toBe(240_000);
  });

  it('rotationReplacesTheRefreshToken', async () => {
    const b = fakeBrowser([
      json({ access_token: jwt(1), refresh_token: 'r1', expires_in: 300 }),
      json({ access_token: jwt(2), refresh_token: 'r2', expires_in: 300 }),
      json({ access_token: jwt(3), refresh_token: 'r3', expires_in: 300 }),
    ]);
    const done = startLogin(CFG, b.deps);
    await navigated(b.popup);
    b.channels[0]?.deliver({ state: stateOf(b.popup.location.href), code: 'c' });
    await done;
    await refreshNow(b.deps);
    expect(currentToken()).toBe(jwt(2));
    await refreshNow(b.deps);
    expect(currentToken()).toBe(jwt(3));
    const refreshes = b.calls.filter((c) => c.body.includes('grant_type=refresh_token')).map((c) => new URLSearchParams(c.body).get('refresh_token'));
    expect(refreshes).toEqual(['r1', 'r2']);
  });

  it('a failed refresh drops the token (back to the login screen)', async () => {
    const b = fakeBrowser([json({ access_token: jwt(1), refresh_token: 'r1', expires_in: 300 })]);
    const done = startLogin(CFG, b.deps);
    await navigated(b.popup);
    b.channels[0]?.deliver({ state: stateOf(b.popup.location.href), code: 'c' });
    await done;
    await refreshNow(b.deps);
    expect(currentToken()).toBeNull();
    expect(tokenSource()).toBeNull();
  });

  it('a 401 (dropToken) also forgets the refresh token', async () => {
    const b = fakeBrowser([json({ access_token: jwt(1), refresh_token: 'r1', expires_in: 300 })]);
    const done = startLogin(CFG, b.deps);
    await navigated(b.popup);
    b.channels[0]?.deliver({ state: stateOf(b.popup.location.href), code: 'c' });
    await done;
    dropToken();
    await refreshNow(b.deps);
    expect(b.calls.filter((c) => c.body.includes('grant_type=refresh_token'))).toHaveLength(0);
    expect(b.timers.at(-1)?.cleared).toBe(true);
  });

  it('logout clears memory first, then posts the refresh token to the end-session endpoint', async () => {
    const b = fakeBrowser([json({ access_token: jwt(1), refresh_token: 'r1', expires_in: 300 })]);
    const done = startLogin(CFG, b.deps);
    await navigated(b.popup);
    b.channels[0]?.deliver({ state: stateOf(b.popup.location.href), code: 'c' });
    await done;
    await logout(b.deps);
    expect(currentToken()).toBeNull();
    const end = b.calls.find((c) => c.url === DISCOVERY.end_session_endpoint);
    expect(Object.fromEntries(new URLSearchParams(end?.body))).toEqual({ client_id: 'portal-web', refresh_token: 'r1' });
  });

  it('a failed exchange and a blocked popup are login errors', async () => {
    const b = fakeBrowser([json({ error: 'invalid_grant' }, 400)]);
    const done = startLogin(CFG, b.deps);
    await navigated(b.popup);
    b.channels[0]?.deliver({ state: stateOf(b.popup.location.href), code: 'c' });
    await expect(done).rejects.toMatchObject({ reason: 'exchange' });
    expect(currentToken()).toBeNull();
    await expect(startLogin(CFG, { ...b.deps, openPopup: () => null })).rejects.toMatchObject({ reason: 'popup' });
  });

  it('nothing lands in browser storage, cookies or window.name', async () => {
    const b = fakeBrowser([json({ access_token: jwt(1), refresh_token: 'r1', expires_in: 300 }), json({ access_token: jwt(2), refresh_token: 'r2', expires_in: 300 })]);
    const done = startLogin(CFG, b.deps);
    await navigated(b.popup);
    b.channels[0]?.deliver({ state: stateOf(b.popup.location.href), code: 'c' });
    await done;
    await refreshNow(b.deps);
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    expect(document.cookie).toBe('');
    expect(window.name).toBe('');
    expect('caches' in globalThis ? await caches.keys() : []).toEqual([]);
    const idb = (globalThis as { indexedDB?: IDBFactory }).indexedDB;
    expect(idb === undefined ? [] : await idb.databases()).toEqual([]);
  });
});
