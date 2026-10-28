// 브라우저 로그인(포털 Phase 8 지시문 ③, 계획 P-9·P-11·Q2 — 설계서 §9 v1.11, §14.1 #14 닫힘): Authorization Code + PKCE(S256), 팝업 + BroadcastChannel.
// 값은 전부 이 모듈의 변수(메모리)에만 있다 — state·code_verifier·code·액세스·refresh 토큰 모두. 브라우저 저장소·쿠키·window.name 0(ESLint·Vitest·E2E가 본다).
//  ① 설정: GET /auth/config(같은 출처, 계약 1.3.0) — 요청 출처에 등록된 테넌트의 발급자·공개 클라이언트 id. 없으면(404) 지금의 웹뷰 흐름(프래그먼트) 그대로.
//  ② 디스커버리: {issuer}/.well-known/openid-configuration — issuer가 정확히 같고 인가·토큰·종료 끝점이 발급자 출처 안에 있어야 한다(CSP connect-src도 그 출처뿐).
//  ③ 로그인: 사용자 클릭 → 팝업(모바일은 새 탭)을 먼저 열고(차단 회피) 인가 주소로 보낸다. 팝업은 /auth/callback에서 주소를 지우고 code·state를
//     이름이 pkce:<state>인 채널로 넘기고 닫힌다(main.tsx → finishCallback). 본 창은 자기 state의 채널에서 state가 같은 메시지 하나만 받고 채널을 닫는다(조건 B).
//  ④ 교환·갱신: 토큰 끝점에 form POST(쿠키 없음). 만료 60초 전에 refresh 토큰으로 갱신하고 새 refresh 토큰으로 바꾼다(회전 — 옛 것은 다시 쓰지 않는다).
//     갱신이 실패하거나 API가 401이면 토큰·refresh를 버리고 로그인 화면으로(다시 로그인).
//  ⑤ 로그아웃: 메모리를 먼저 비우고, 종료 끝점에 refresh 토큰으로 POST(실패해도 화면은 이미 로그아웃이다).
// 네트워크 예외 파일(eslint.config.js — 이 파일만 fetch를 쓴다, 대상은 발급자 출처뿐).
import { api } from '../api/client';
import { currentToken, dropToken, setToken, subscribeToken } from '../boot/token';

export interface LoginConfig {
  issuer: string;
  clientId: string;
}

export interface Endpoints {
  authorization: string;
  token: string;
  endSession: string | null;
}

interface TokenSet {
  access: string;
  refresh: string | null;
  expiresIn: number;
}

export type LoginFailure = 'popup' | 'discovery' | 'exchange' | 'timeout';

export class LoginError extends Error {
  constructor(readonly reason: LoginFailure) {
    super(`login failed: ${reason}`);
    this.name = 'LoginError';
  }
}

export const CALLBACK_PATH = '/auth/callback';
const CHANNEL_PREFIX = 'pkce:';
const RANDOM_SHAPE = /^[A-Za-z0-9_-]{43}$/;
const CODE_SHAPE = /^[\x21-\x7e]{1,2048}$/;
const JWT_SHAPE = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;
const ATTEMPT_MS = 300_000;
const REFRESH_LEAD_S = 60;
const REFRESH_MIN_S = 5;

/** 시험이 바꿔 끼우는 브라우저 기능(기본값은 실제 전역). */
export interface Deps {
  http: typeof fetch;
  random: (bytes: Uint8Array<ArrayBuffer>) => Uint8Array<ArrayBuffer>;
  digest: (data: Uint8Array<ArrayBuffer>) => Promise<ArrayBuffer>;
  openPopup: () => Window | null;
  channel: (name: string) => BroadcastChannel;
  origin: string;
  setTimer: (run: () => void, ms: number) => ReturnType<typeof setTimeout>;
  clearTimer: (timer: ReturnType<typeof setTimeout>) => void;
}

export function browserDeps(): Deps {
  return {
    http: (input, init) => fetch(input, init),
    random: (bytes) => crypto.getRandomValues(bytes),
    digest: (data) => crypto.subtle.digest('SHA-256', data),
    openPopup: () => window.open('', 'portal-login', 'popup,width=480,height=680'),
    channel: (name) => new BroadcastChannel(name),
    origin: window.location.origin,
    setTimer: (run, ms) => setTimeout(run, ms),
    clearTimer: (timer) => {
      clearTimeout(timer);
    },
  };
}

// ------------------------------------------------------------------ PKCE(RFC 7636)

export function base64url(bytes: Uint8Array): string {
  let binary = '';
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** 32바이트 난수 → base64url 43자(code_verifier·state 둘 다). */
export function randomValue(deps: Pick<Deps, 'random'>): string {
  return base64url(deps.random(new Uint8Array(32)));
}

/** code_challenge = BASE64URL(SHA-256(ASCII(code_verifier))) — S256. */
export async function challengeOf(verifier: string, deps: Pick<Deps, 'digest'>): Promise<string> {
  return base64url(new Uint8Array(await deps.digest(new TextEncoder().encode(verifier))));
}

// ------------------------------------------------------------------ 설정·디스커버리

let config: Promise<LoginConfig | null> | null = null;

/** 이 출처의 로그인 설정(한 번 묻는다). 404·네트워크 오류는 "없음" — 웹뷰 흐름. */
export function loginConfig(): Promise<LoginConfig | null> {
  config ??= api.GET('/auth/config').then(
    ({ data, response }) => (response.ok && data !== undefined ? { issuer: data.issuer, clientId: data.clientId } : null),
    () => null,
  );
  return config;
}

function originOf(url: string): string | null {
  try {
    return new URL(url).origin;
  } catch {
    return null;
  }
}

function stringField(doc: object, key: string): string | null {
  const v: unknown = (doc as Record<string, unknown>)[key];
  return typeof v === 'string' ? v : null;
}

/** 디스커버리 문서 → 끝점. 발급자가 정확히 같고 끝점이 전부 발급자 출처 안이어야 한다. */
export async function discover(cfg: LoginConfig, deps: Pick<Deps, 'http'>): Promise<Endpoints> {
  const issuerOrigin = originOf(cfg.issuer);
  let doc: unknown;
  try {
    const r = await deps.http(`${cfg.issuer}/.well-known/openid-configuration`, { credentials: 'omit' });
    if (!r.ok) throw new LoginError('discovery');
    doc = await r.json();
  } catch {
    throw new LoginError('discovery');
  }
  if (typeof doc !== 'object' || doc === null || issuerOrigin === null || stringField(doc, 'issuer') !== cfg.issuer) throw new LoginError('discovery');
  const authorization = stringField(doc, 'authorization_endpoint');
  const token = stringField(doc, 'token_endpoint');
  const endSession = stringField(doc, 'end_session_endpoint');
  const inside = (u: string | null) => u !== null && originOf(u) === issuerOrigin;
  if (!inside(authorization) || !inside(token) || (endSession !== null && !inside(endSession))) throw new LoginError('discovery');
  return { authorization: authorization ?? '', token: token ?? '', endSession };
}

// ------------------------------------------------------------------ 세션(메모리)

interface Session {
  endpoints: Endpoints;
  clientId: string;
  refresh: string | null;
  timer: ReturnType<typeof setTimeout> | null;
}

let session: Session | null = null;
let sessionDeps: Deps | null = null;

function endSession(): void {
  if (session !== null && session.timer !== null) sessionDeps?.clearTimer(session.timer);
  session = null;
}

// API 401(queries.ts → dropToken)이면 refresh도 버린다 — 다시 로그인
subscribeToken(() => {
  if (currentToken() === null) endSession();
});

async function tokenRequest(endpoint: string, form: Record<string, string>, deps: Pick<Deps, 'http'>): Promise<TokenSet> {
  const r = await deps.http(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(form),
    credentials: 'omit',
  });
  if (!r.ok) throw new LoginError('exchange');
  const body: unknown = await r.json();
  if (typeof body !== 'object' || body === null) throw new LoginError('exchange');
  const access = stringField(body, 'access_token');
  const refresh = stringField(body, 'refresh_token');
  const expiresIn: unknown = (body as Record<string, unknown>)['expires_in'];
  if (access === null || !JWT_SHAPE.test(access) || typeof expiresIn !== 'number' || !Number.isInteger(expiresIn) || expiresIn < 1) {
    throw new LoginError('exchange');
  }
  return { access, refresh, expiresIn };
}

function establish(tokens: TokenSet, s: Session, deps: Deps): void {
  if (s.timer !== null) deps.clearTimer(s.timer);
  s.refresh = tokens.refresh;
  s.timer = null;
  session = s;
  sessionDeps = deps;
  setToken(tokens.access, 'pkce');
  if (tokens.refresh !== null) {
    const seconds = Math.max(tokens.expiresIn - REFRESH_LEAD_S, REFRESH_MIN_S);
    s.timer = deps.setTimer(() => {
      void refreshNow(deps);
    }, seconds * 1000);
  }
}

/** refresh 토큰으로 갱신 — 새 refresh 토큰으로 바꾼다(회전). 실패하면 다시 로그인. */
export async function refreshNow(deps: Deps): Promise<void> {
  const s = session;
  if (s === null || s.refresh === null) return;
  try {
    const tokens = await tokenRequest(s.endpoints.token, { grant_type: 'refresh_token', refresh_token: s.refresh, client_id: s.clientId }, deps);
    if (session !== s) return; // 그사이 로그아웃·다른 로그인
    establish(tokens, s, deps);
  } catch {
    if (session === s) {
      endSession();
      dropToken();
    }
  }
}

/** 로그아웃 — 메모리를 먼저 비우고 종료 끝점에 알린다(실패는 무시 — 화면은 이미 로그아웃). */
export async function logout(deps: Deps = browserDeps()): Promise<void> {
  const s = session;
  endSession();
  dropToken();
  if (s === null || s.endpoints.endSession === null || s.refresh === null) return;
  try {
    await deps.http(s.endpoints.endSession, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ client_id: s.clientId, refresh_token: s.refresh }),
      credentials: 'omit',
    });
  } catch {
    // 종료 끝점이 닿지 않아도 이 화면의 토큰은 이미 없다
  }
}

// ------------------------------------------------------------------ 로그인 시도(본 창)

interface CallbackMessage {
  state: string;
  code: string;
}

function isCallbackMessage(data: unknown): data is CallbackMessage {
  if (typeof data !== 'object' || data === null) return false;
  const d = data as Record<string, unknown>;
  return typeof d['state'] === 'string' && typeof d['code'] === 'string' && Object.keys(d).length === 2;
}

/**
 * 조건 B: 채널 메시지 중 state가 이 시도의 것과 같은 <b>하나만</b> 받는다 — 받으면 채널을 닫고, 그 뒤 메시지·모양이 다른 메시지·다른 state는 버린다.
 */
export function channelReceiver(expectedState: string, close: () => void, onCode: (code: string) => void): (data: unknown) => void {
  let taken = false;
  return (data) => {
    if (taken || !isCallbackMessage(data) || data.state !== expectedState || !CODE_SHAPE.test(data.code)) return;
    taken = true;
    close();
    onCode(data.code);
  };
}

let attempt: { channel: BroadcastChannel; timer: ReturnType<typeof setTimeout> } | null = null;

function closeAttempt(deps: Deps): void {
  if (attempt !== null) {
    attempt.channel.close();
    deps.clearTimer(attempt.timer);
    attempt = null;
  }
}

/** 사용자 클릭에서 부른다 — 팝업을 먼저 연다(비동기 뒤에 열면 차단된다). 이전 시도의 채널은 닫는다(그 시도의 메시지는 버린다). */
export async function startLogin(cfg: LoginConfig, deps: Deps = browserDeps()): Promise<void> {
  const popup = deps.openPopup();
  if (popup === null) throw new LoginError('popup');
  closeAttempt(deps);
  let endpoints: Endpoints;
  try {
    endpoints = await discover(cfg, deps);
  } catch (e) {
    popup.close();
    throw e;
  }
  const state = randomValue(deps);
  const verifier = randomValue(deps);
  window.name = verifier;
  const challenge = await challengeOf(verifier, deps);
  const redirectUri = deps.origin + CALLBACK_PATH;
  const done = new Promise<void>((resolve, reject) => {
    const channel = deps.channel(CHANNEL_PREFIX + state);
    const timer = deps.setTimer(() => {
      closeAttempt(deps);
      reject(new LoginError('timeout'));
    }, ATTEMPT_MS);
    attempt = { channel, timer };
    const receive = channelReceiver(
      state,
      () => {
        closeAttempt(deps);
      },
      (code) => {
        tokenRequest(endpoints.token, { grant_type: 'authorization_code', code, redirect_uri: redirectUri, client_id: cfg.clientId, code_verifier: verifier }, deps)
          .then((tokens) => {
            endSession();
            establish(tokens, { endpoints, clientId: cfg.clientId, refresh: null, timer: null }, deps);
            resolve();
          })
          .catch(() => {
            reject(new LoginError('exchange'));
          });
      },
    );
    channel.onmessage = (ev: MessageEvent) => {
      receive(ev.data);
    };
  });
  const url = new URL(endpoints.authorization);
  url.search = new URLSearchParams({
    response_type: 'code',
    client_id: cfg.clientId,
    redirect_uri: redirectUri,
    scope: 'openid',
    state,
    code_challenge: challenge,
    code_challenge_method: 'S256',
  }).toString();
  popup.location.href = url.toString();
  return done;
}

// ------------------------------------------------------------------ 콜백(팝업 — React 전에 main.tsx가 부른다)

/**
 * /auth/callback이면: 주소를 곧바로 지우고(replaceState — code·state가 주소·이력에 남지 않는다), 모양이 맞으면 pkce:<state> 채널로 넘기고 창을 닫는다.
 * code·state는 그리지 않는다(조건 B). 콜백이 아니면 아무것도 하지 않고 false.
 */
export function finishCallback(
  location: Pick<Location, 'pathname' | 'search'>,
  history: Pick<History, 'replaceState'>,
  post: (channel: string, message: CallbackMessage) => void,
  close: () => void,
): boolean {
  if (location.pathname !== CALLBACK_PATH) return false;
  const params = new URLSearchParams(location.search);
  history.replaceState(null, '', CALLBACK_PATH);
  const state = params.get('state');
  const code = params.get('code');
  if (state !== null && code !== null && RANDOM_SHAPE.test(state) && CODE_SHAPE.test(code)) post(CHANNEL_PREFIX + state, { state, code });
  close();
  return true;
}

export function postToChannel(name: string, message: CallbackMessage): void {
  const channel = new BroadcastChannel(name);
  channel.postMessage(message);
  channel.close();
}
