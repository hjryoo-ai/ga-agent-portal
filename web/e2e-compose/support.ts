// compose E2E 공용(포털 Phase 8 지시문 ⑥, 계획 ⑤·P-19·P-21·조건 B): compose 스택(docker/compose.yaml — 프로파일 demo)을 상대로 한다.
// 비밀(액세스·refresh 토큰, code, state)은 시험 중 잡아 build/compose-e2e/.state/secrets(600)에만 적는다 — run.mjs가 산출물 전체를 그것으로 훑는다.
// 센티널(P-19)은 스트림에서 읽는다: 데모 스트림의 증권번호 전부. 데모 비밀번호는 README의 허구값(계획 Q7 — README 한 곳).
import AxeBuilder from '@axe-core/playwright';
import { expect, type BrowserContext, type Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { appendFileSync, mkdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

export const repo = resolve(import.meta.dirname, '../..');
export const out = resolve(repo, 'web/build/compose-e2e');
const state = resolve(out, '.state');
export const BASE = 'http://localhost:8080';
export const ISSUER = 'http://localhost:8180/realms/demo';
export const ISSUER_ORIGIN = 'http://localhost:8180';
export const JAEGER = 'http://localhost:16686';
export const TENANT = 'DEMO';
const COMPOSE = ['compose', '-f', resolve(repo, 'docker/compose.yaml'), '--profile', 'demo'];

/** README의 데모 비밀번호 줄(`데모 비밀번호: \`…\``). */
export function demoPassword(): string {
  const m = /데모 비밀번호: `([^`]+)`/.exec(readFileSync(resolve(repo, 'README.md'), 'utf8'));
  if (m?.[1] === undefined) throw new Error('README has no demo password line');
  return m[1];
}

let policies: string[] | null = null;
/** 데모 스트림의 증권번호 원문 전부(어느 응답·DOM·주소·콘솔·저장소에도 없어야 한다). */
export function policyNumbers(): string[] {
  if (policies === null) {
    const set = new Set<string>();
    for (const line of readFileSync(resolve(repo, 'contracts/shared/ga-commission-engine/samples/demo/demo_ga.jsonl'), 'utf8').split('\n')) {
      if (line === '') continue;
      const p = (JSON.parse(line) as { payload: { policyNo?: unknown } }).payload.policyNo;
      if (typeof p === 'string') set.add(p);
    }
    policies = [...set];
  }
  return policies;
}

const secrets: string[] = [];
/** 잡은 비밀(토큰·code·state) — 메모리 목록 + 상태 파일(600). */
export function recordSecret(value: string): void {
  if (value === '' || secrets.includes(value)) return;
  secrets.push(value);
  mkdirSync(state, { recursive: true, mode: 0o700 });
  appendFileSync(resolve(state, 'secrets'), `${value}\n`, { mode: 0o600 });
}
export function knownSecrets(): readonly string[] {
  return secrets;
}

export function leaks(text: string): string[] {
  return [
    ...policyNumbers().filter((p) => text.includes(p)).map((p) => `policy ${p.slice(0, 5)}…`),
    ...secrets.filter((s) => text.includes(s)).map((s) => `secret ${s.slice(0, 3)}…`),
  ];
}

/** 시험 밖 API 호출용 토큰(k6 클라이언트 — Direct Access Grants, 데모 렐름 전용). */
export async function directGrant(username: string): Promise<string> {
  const r = await fetch(`${ISSUER}/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'password', client_id: 'k6', username, password: demoPassword(), scope: 'openid' }),
  });
  if (!r.ok) throw new Error(`direct grant for ${username} answered ${String(r.status)}`);
  const token = ((await r.json()) as { access_token: string }).access_token;
  recordSecret(token);
  return token;
}

export async function api(path: string, token: string): Promise<{ status: number; body: string }> {
  const r = await fetch(BASE + path, { headers: { Authorization: `Bearer ${token}` } });
  return { status: r.status, body: await r.text() };
}

/** 포털 운영 CLI(compose exec — 행위자 cli:portal). */
export function cli(...args: string[]): { code: number; out: string; ms: number } {
  const started = Date.now();
  try {
    const stdout = execFileSync('docker', [...COMPOSE, 'exec', '-T', 'portal', 'java', '-jar', '/app/portal.jar', '--spring.profiles.active=cli', ...args], {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
      timeout: 600_000,
    });
    return { code: 0, out: stdout, ms: Date.now() - started };
  } catch (e) {
    const err = e as { status?: number; stdout?: string };
    return { code: err.status ?? 1, out: err.stdout ?? '', ms: Date.now() - started };
  }
}

export interface Watch {
  console: string[];
  addresses: string[];
  requests: string[];
  external: string[];
  bodies: { url: string; text: string }[];
}

const CSP_MARK = 'CSP-VIOLATION';

/** 본 창 감시: 콘솔·주소·요청·같은 출처 응답 본문. 외부 출처 = 발급자 출처만 허용(조건 — CSP connect-src와 같다). */
export async function watch(context: BrowserContext, page: Page): Promise<Watch> {
  const w: Watch = { console: [], addresses: [], requests: [], external: [], bodies: [] };
  await context.addInitScript((mark) => {
    document.addEventListener('securitypolicyviolation', (ev) => {
      console.log(`${mark} ${ev.violatedDirective} ${ev.blockedURI}`);
    });
  }, CSP_MARK);
  page.on('console', (m) => w.console.push(m.text()));
  page.on('pageerror', (e) => w.console.push(String(e)));
  page.on('framenavigated', (f) => {
    if (f === page.mainFrame()) w.addresses.push(f.url());
  });
  page.on('request', (r) => {
    w.requests.push(r.url());
    if (!r.url().startsWith(BASE) && !r.url().startsWith('data:') && !r.url().startsWith('blob:')) w.external.push(r.url());
  });
  page.on('response', (r) => {
    if (r.url().startsWith(BASE) && r.request().resourceType() === 'fetch') {
      void r
        .text()
        .then((text) => w.bodies.push({ url: r.url(), text }))
        .catch(() => undefined);
    }
  });
  return w;
}

export async function storage(page: Page) {
  return page.evaluate(async () => ({
    local: localStorage.length,
    session: sessionStorage.length,
    cookie: document.cookie,
    indexedDb: (await indexedDB.databases()).length,
    caches: (await caches.keys()).length,
    windowName: window.name,
  }));
}

/** 지금 화면: DOM 누출 0, 저장소 6종 0, axe(WCAG 2.0/2.1 A·AA) 위반 0. */
export async function check(page: Page, label: string): Promise<void> {
  expect(leaks(await page.content()), `DOM ${label}`).toEqual([]);
  expect(await storage(page), `storage ${label}`).toEqual({ local: 0, session: 0, cookie: '', indexedDb: 0, caches: 0, windowName: '' });
  const axe = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(axe.violations.map((v) => v.id), `axe ${label}`).toEqual([]);
}

/** 끝 검사: 주소·요청 주소·콘솔·같은 출처 응답에 누출 0, CSP 위반 0, 외부 출처는 발급자뿐. */
export function finalChecks(w: Watch): void {
  expect(w.addresses.flatMap(leaks), 'addresses').toEqual([]);
  expect(w.requests.flatMap(leaks), 'request addresses').toEqual([]);
  expect(w.console.flatMap(leaks), 'console').toEqual([]);
  expect(w.bodies.flatMap((b) => leaks(b.text)), 'portal response bodies').toEqual([]);
  expect(w.console.filter((c) => c.startsWith(CSP_MARK) || /Content Security Policy|Refused to/i.test(c)), 'CSP').toEqual([]);
  expect(w.external.filter((u) => !u.startsWith(`${ISSUER_ORIGIN}/`)), 'external origins other than the issuer').toEqual([]);
}

/**
 * "로그인" → 팝업(Keycloak 로그인 화면) → 자격 증명 → /auth/callback → 팝업이 닫히고 본 창이 홈. 팝업이 지나간 주소에서 code·state를, 본 창의 토큰
 * 응답에서 토큰을 잡아 비밀 목록에 넣는다(조건 B — 그것들이 어디에도 남지 않는지 보려고).
 */
export async function login(page: Page, username: string): Promise<void> {
  await page.goto(`${BASE}/`);
  const button = page.getByRole('button', { name: '로그인', exact: true });
  await expect(button).toBeVisible();
  page.on('response', (r) => {
    if (r.url() === `${ISSUER}/protocol/openid-connect/token` && r.ok()) {
      void r
        .json()
        .then((t: Record<string, unknown>) => {
          for (const k of ['access_token', 'refresh_token', 'id_token']) if (typeof t[k] === 'string') recordSecret(t[k]);
        })
        .catch(() => undefined);
    }
  });
  const popupEvent = page.waitForEvent('popup');
  await button.click();
  const popup = await popupEvent;
  popup.on('request', (r) => {
    const u = new URL(r.url());
    if (u.origin === BASE && u.pathname === '/auth/callback') {
      for (const k of ['code', 'state']) {
        const v = u.searchParams.get(k);
        if (v !== null) recordSecret(v);
      }
    }
  });
  await popup.locator('#username').fill(username);
  await popup.locator('#password').fill(demoPassword());
  const closed = popup.waitForEvent('close');
  await popup.locator('#kc-login').click();
  await closed;
  await expect(page.getByTestId('sync-chip')).toBeVisible();
}
