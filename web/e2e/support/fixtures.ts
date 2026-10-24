// E2E 고정 장치(포털 Phase 4 계획 ⑨ 검사 표 — C6·C7·C8·C10). 모든 시험에 걸린다:
//  - 누출: 단계마다(그리고 끝에) DOM 전체·모든 응답 본문·콘솔·주소 이력에 센티널(증권번호 원문·이웃 설계사 ID)과 토큰이 0.
//    토큰은 요청의 Authorization 헤더에만 있어도 된다(주입 경로인 첫 주소의 프래그먼트는 open()이 연 것 — 그 뒤 주소에는 0).
//  - 저장소: localStorage·sessionStorage·쿠키·IndexedDB·Cache Storage가 전부 비어 있다(센티널 대조보다 강하다).
//  - CSP: securitypolicyviolation(초기화 스크립트가 표식 줄로 콘솔에 올림)과 브라우저의 CSP 콘솔 오류 = 0, 외부 출처 요청 0.
//  - 접근성(check()): axe WCAG 2.0/2.1 A·AA 위반 0, 보이는 대화형 요소 44px 이상, 움직임 감소에서 전환·애니메이션 0.
//  - 동적 센티널(포털 Phase 5 계획 Q20): 시뮬레이션 응답의 simId — 화면(DOM)에는 보이도록 설계됐지만 주소·요청 주소·콘솔·저장소에는 0.
//  - 문의 본문 센티널(포털 Phase 6 계획 ⑨ E7·P-22): 작성 화면(/inquiries/new)·상세 화면(/inquiries/<id>)의 DOM과 상세 응답(GET /api/v1/me/inquiries/<id>)에서만
//    허용 — 다른 화면 DOM·다른 응답·콘솔·주소·요청 주소에는 0(저장소는 위에서 비어 있다). 허용된 응답에 실제로 실린 수(inquiryBodies)도 요약에 남긴다(공허 방지).
import AxeBuilder from '@axe-core/playwright';
import { test as base, expect, type Page } from '@playwright/test';
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { inquirySentinels, sentinels } from './sentinels.mjs';
import { allTokens, baseUrl, token, type TokenKind } from './env';

const out = resolve(import.meta.dirname, '../../build/e2e');
const FORBIDDEN = sentinels();
const BODY = inquirySentinels();
const UUID = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}';
const BODY_SCREEN = new RegExp(`^/inquiries/(new|${UUID})$`);
const BODY_RESPONSE = new RegExp(`^/api/v1/me/inquiries/${UUID}$`);
const TOKENS = allTokens();
const CSP_MARK = 'CSP-VIOLATION';

interface Watch {
  console: string[];
  bodies: { url: string; method: string; text: string }[];
  urls: string[];
  requestUrls: string[];
  external: string[];
  doms: number;
  axe: { label: string; violations: number; passes: number; incomplete: number }[];
  opened: Set<string>;
}

export interface Portal {
  /** 웹뷰처럼 연다 — /경로#token=<토큰>, 브라우저 시계를 KST 기준 시각에 고정. */
  open(path: string, tenant: string, kind?: TokenKind | 'none' | 'garbage', at?: string): Promise<void>;
  /** 지금 화면을 검사한다: DOM 누출 + axe + 44px + 움직임 감소. */
  check(label: string): Promise<void>;
}

function leaks(text: string, allowToken = false, allowBody = false): string[] {
  const hits = FORBIDDEN.filter((s) => text.includes(s)).map((s) => `sentinel ${s.slice(0, 3)}…`);
  if (!allowToken) hits.push(...TOKENS.filter((t) => text.includes(t)).map(() => 'token'));
  if (!allowBody) hits.push(...BODY.filter((s) => text.includes(s)).map((s) => `inquiry body ${s.slice(0, 2)}…`));
  return hits;
}

/** 본문이 보여도 되는 화면인가(작성·상세) — 주소의 경로만 본다. */
async function bodyScreen(page: Page): Promise<boolean> {
  return BODY_SCREEN.test(await page.evaluate(() => location.pathname));
}

/** 본문이 실려도 되는 응답인가 — 상세 GET 하나. */
function bodyResponse(b: { url: string; method: string }): boolean {
  const u = new URL(b.url);
  return b.method === 'GET' && u.search === '' && BODY_RESPONSE.test(u.pathname);
}

async function storageIsEmpty(page: Page) {
  return page.evaluate(async () => ({
    local: localStorage.length,
    session: sessionStorage.length,
    cookie: document.cookie,
    indexedDb: (await indexedDB.databases()).length,
    caches: (await caches.keys()).length,
  }));
}

async function smallTargets(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const selector = 'a[href], button, [role="button"], select, input, textarea, summary';
    const small: string[] = [];
    for (const el of Array.from(document.querySelectorAll<HTMLElement>(selector))) {
      const r = el.getBoundingClientRect();
      const style = getComputedStyle(el);
      if (r.width === 0 || r.height === 0 || style.visibility === 'hidden' || el.closest('.visually-hidden') !== null) continue;
      if (r.width < 44 || r.height < 44) small.push(`${el.tagName.toLowerCase()} "${el.textContent.trim().slice(0, 20)}" ${String(Math.round(r.width))}x${String(Math.round(r.height))}`);
    }
    return small;
  });
}

async function motion(page: Page): Promise<string[]> {
  return page.evaluate(() =>
    Array.from(document.querySelectorAll<HTMLElement>('*'))
      .map((el) => ({ el, s: getComputedStyle(el) }))
      .filter(({ s }) => s.transitionDuration.split(',').some((d) => parseFloat(d) > 0) || (s.animationName !== 'none' && parseFloat(s.animationDuration) > 0))
      .map(({ el }) => el.tagName.toLowerCase() + (el.className ? `.${el.className}` : '')),
  );
}

export const test = base.extend<{ portal: Portal; watch: Watch }>({
  watch: [
    async ({ page }, use, info) => {
      const w: Watch = { console: [], bodies: [], urls: [], requestUrls: [], external: [], doms: 0, axe: [], opened: new Set() };
      await page.addInitScript((mark) => {
        document.addEventListener('securitypolicyviolation', (ev) => {
          // 위반한 요소의 이름도 싣는다(어느 요소가 막혔는지 — Phase 5에서 WebKit <select>를 찾는 데 이것이 없었다)
          console.log(`${mark} ${ev.violatedDirective} ${ev.blockedURI} ${ev.target instanceof Element ? ev.target.tagName.toLowerCase() : '-'}`);
        });
      }, CSP_MARK);
      page.on('console', (m) => w.console.push(m.text()));
      page.on('pageerror', (e) => w.console.push(String(e)));
      page.on('framenavigated', (f) => {
        if (f === page.mainFrame() && f.url() !== 'about:blank') w.urls.push(f.url());
      });
      page.on('request', (r) => {
        w.requestUrls.push(r.url());
        if (!r.url().startsWith(baseUrl) && !r.url().startsWith('data:') && !r.url().startsWith('blob:')) w.external.push(r.url());
      });
      page.on('response', (r) => {
        if (r.url().startsWith(baseUrl) && r.request().resourceType() === 'fetch') {
          void r
            .text()
            .then((text) => w.bodies.push({ url: r.url(), method: r.request().method(), text }))
            .catch(() => undefined);
        }
      });
      await use(w);

      // 끝: 마지막 DOM, 응답·콘솔·주소, 저장소, CSP
      // 포털 문서를 연 페이지만(건너뛴 시험은 about:blank에 머문다 — 거기서는 저장소를 읽을 수 없다)
      if (!page.isClosed() && page.url().startsWith(baseUrl)) {
        w.doms++;
        expect(leaks(await page.content(), false, await bodyScreen(page)), 'DOM at the end').toEqual([]);
        expect(await storageIsEmpty(page), 'browser storage').toEqual({ local: 0, session: 0, cookie: '', indexedDb: 0, caches: 0 });
        expect(leaks(await page.evaluate(() => location.href)), 'current address').toEqual([]);
      }
      expect(w.bodies.flatMap((b) => leaks(b.text, false, bodyResponse(b))), 'API response bodies').toEqual([]);
      const inquiryBodies = w.bodies.filter((b) => bodyResponse(b) && BODY.some((s) => b.text.includes(s))).length;
      expect(w.console.flatMap((c) => leaks(c)), 'console').toEqual([]);
      const navigations = w.urls.filter((u) => !w.opened.has(u));
      expect(navigations.flatMap((u) => leaks(u)), 'addresses after boot').toEqual([]);
      expect(w.requestUrls.flatMap((u) => leaks(u)), 'request addresses').toEqual([]);
      const csp = w.console.filter((c) => c.startsWith(CSP_MARK) || /Content Security Policy|Refused to/i.test(c));
      expect(csp, 'CSP').toEqual([]);
      expect(w.external, 'external origins').toEqual([]);
      const simIds = w.bodies
        .filter((b) => b.url.includes('/api/v1/me/simulations/'))
        .flatMap((b) => [...b.text.matchAll(/"simId":"([0-9a-f-]{36})"/g)].map((x) => x[1] ?? ''));
      const simIdHits = (texts: string[]) => texts.filter((t) => simIds.some((id) => t.includes(id)));
      expect(simIdHits(navigations), 'simId in addresses').toEqual([]);
      expect(simIdHits(w.requestUrls), 'simId in request addresses').toEqual([]);
      expect(simIdHits(w.console), 'simId in the console').toEqual([]);
      if (!page.isClosed() && page.url().startsWith(baseUrl)) expect(simIdHits([page.url()]), 'simId in the current address').toEqual([]);
      // 요약은 시험마다 한 파일 — 이름은 testId(프로젝트·시험마다 유일, 제목의 한글을 지워 겹치는 일이 없다)
      const name = `${info.project.name}--${info.testId}.json`;
      const title = info.titlePath.slice(1).join(' › ');
      mkdirSync(resolve(out, 'leak-scan'), { recursive: true });
      writeFileSync(
        resolve(out, 'leak-scan', name),
        JSON.stringify(
          {
            project: info.project.name,
            title,
            responses: w.bodies.length,
            doms: w.doms,
            consoleLines: w.console.length,
            addresses: w.urls.length,
            requests: w.requestUrls.length,
            cspViolations: csp.length,
            externalRequests: w.external.length,
            sentinels: FORBIDDEN.length,
            tokens: TOKENS.length,
            simIds: simIds.length,
            inquiryBodySentinels: BODY.length,
            inquiryBodies,
          },
          null,
          2,
        ),
      );
      mkdirSync(resolve(out, 'axe'), { recursive: true });
      writeFileSync(resolve(out, 'axe', name), JSON.stringify({ project: info.project.name, title, states: w.axe }, null, 2));
    },
    { auto: true },
  ],
  portal: async ({ page, watch }, use) => {
    await use({
      async open(path, tenant, kind = 'agent', at) {
        if (at !== undefined) await page.clock.setFixedTime(new Date(at));
        const fragment = kind === 'none' ? '' : kind === 'garbage' ? '#token=not-a-jwt' : `#token=${token(tenant, kind)}`;
        const url = `${baseUrl}${path}${fragment}`;
        watch.opened.add(url);
        // 같은 문서의 프래그먼트만 바뀌면 다시 불러오지 않는다 — 빈 문서를 거쳐 새로 연다(웹뷰가 새로 여는 것과 같게)
        await page.goto('about:blank');
        await page.goto(url);
        await expect.poll(() => page.evaluate(() => location.hash)).toBe('');
      },
      async check(label) {
        watch.doms++;
        expect(leaks(await page.content(), false, await bodyScreen(page)), `DOM: ${label}`).toEqual([]);
        const axe = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
        watch.axe.push({ label, violations: axe.violations.length, passes: axe.passes.length, incomplete: axe.incomplete.length });
        expect(axe.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`), `axe: ${label}`).toEqual([]);
        expect(await smallTargets(page), `44px: ${label}`).toEqual([]);
        await page.emulateMedia({ reducedMotion: 'reduce' });
        expect(await motion(page), `reduced motion: ${label}`).toEqual([]);
        await page.emulateMedia({ reducedMotion: 'no-preference' });
      },
    });
  },
});

export { expect };
