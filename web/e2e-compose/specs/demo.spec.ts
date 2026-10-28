// compose E2E(포털 Phase 8 지시문 ⑥·C5~C9, 계획 ⑤·Q4·P-21·P-24·조건 B): README 계정으로 로그인(PKCE 팝업) → 다섯 화면(홈·명세·한도·타임라인·
// 시뮬레이션) 데이터 → 알림함·문의 접수 → 대사 `recon run`·`status`(compose exec) → 운영자 `ingest rebuild` 전후 응답 동일 → Jaeger 스팬 키 → 로그아웃.
// 데모 스트림에는 엔진이 낼 수 없는 불일치가 없다 — 대사는 MATCH(OK)뿐이고 DRIFT 감지·복구·표식 유지의 증거는 하네스 E2E ⓐ~ⓔ′다(계획 Q4).
// 화면 상태 표(보고서 — 스냅샷이 아니라 화면이 보인 문구·행 수)는 build/compose-e2e/screens.json.
import { expect, test, type Page } from '@playwright/test';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import {
  BASE,
  JAEGER,
  TENANT,
  api,
  check,
  cli,
  directGrant,
  finalChecks,
  knownSecrets,
  leaks,
  login,
  out,
  policyNumbers,
  repo,
  watch,
} from '../support';

const AGENT = 'a-2001';
interface Sample {
  name: string;
  request: Record<string, string | number>;
  response: { ruleSnapshotId: string };
}
const samples = JSON.parse(readFileSync(resolve(repo, 'contracts/shared/ga-commission-engine/samples/demo/simulations.json'), 'utf8')) as Sample[];
const sample = (name: string): Sample => {
  const s = samples.find((x) => x.name === name);
  if (s === undefined) throw new Error(`no sample ${name}`);
  return s;
};
const screens: Record<string, Record<string, string | number>> = {};

/**
 * 화면 안 이동(라우터 — 문서를 다시 읽지 않는다). PKCE 토큰은 메모리에만 있어 `page.goto`(문서 새로 읽기)는 세션을 버린다 — 설계대로다(저장소 0).
 * 그래서 로그인 뒤 이동은 사용자의 링크 이동처럼 history 항목을 더하고 라우터에 알린다.
 */
async function go(page: Page, path: string): Promise<void> {
  await page.evaluate((p) => {
    history.pushState(null, '', p);
    dispatchEvent(new PopStateEvent('popstate'));
  }, path);
  await expect(page).toHaveURL(BASE + path);
}

test('로그인 → 다섯 화면 데이터 → 알림함·문의 → 로그아웃(IdP 세션도 끝)', async ({ page, context }) => {
  const w = await watch(context, page);
  await page.goto(`${BASE}/`);
  await check(page, 'relogin');
  await login(page, AGENT);
  expect(knownSecrets().length, 'code, state and tokens were captured').toBeGreaterThanOrEqual(4);

  // ① 홈 — 이번 달(실제 시계의 KST 달 — 데모 창 2026-03~2028-02 안)
  await expect(page.getByTestId('month-card')).toBeVisible();
  await expect(page.getByTestId('sync-chip')).toHaveText(/^기준 /);
  screens['home'] = {
    chip: await page.getByTestId('sync-chip').innerText(),
    monthStatus: await page.getByTestId('month-status').innerText(),
    recentNotifications: await page.getByTestId('recent-notifications').locator('[data-testid="notification"]').count(),
  };
  await check(page, 'home');

  // ② 월별 명세(확정 달)
  await go(page, '/statements/202608');
  await expect(page.getByTestId('lines').getByTestId('line').first()).toBeVisible();
  screens['statement 202608'] = {
    lines: await page.getByTestId('lines').getByTestId('line').count(),
    status: await page.getByTestId('month-status').innerText(),
  };
  await check(page, 'statement');

  // ③ 한도
  await go(page, '/limits');
  await expect(page.getByTestId('ledger-card').first()).toBeVisible();
  screens['limits'] = { cards: await page.getByTestId('ledger-card').count() };
  await check(page, 'limits');

  // ④ 분급 타임라인
  await go(page, '/deferrals');
  // 데모 스트림의 첫 분급 도래는 2028-01(체결 2027-01 + 12개월) — 이번 달부터 12개월(기본)에는 없을 수 있어 24개월을 고른다(README 데모 절)
  await page.getByTestId('period-24').click();
  await expect(page.getByTestId('timeline-month').first()).toBeVisible();
  screens['timeline'] = { months: await page.getByTestId('timeline-month').count(), items: await page.getByTestId('timeline-item').count() };
  await check(page, 'timeline');

  // ⑤ 시뮬레이션 — 엔진 데모 샘플 요청 그대로 입력(P-24: 화면에 미리 채우는 코드 없음 — README 표의 값)
  const nc = sample('A-2001-new-contract');
  await go(page, '/simulations');
  const form = page.getByTestId('form-new');
  await form.getByTestId('field-productKey').locator('input').fill(String(nc.request['productKey']));
  await form.getByTestId('field-monthlyPremium').locator('input').fill(String(nc.request['monthlyPremium']));
  await form.getByTestId('field-contractDate').locator('input').fill(String(nc.request['contractDate']));
  await page.getByTestId('run-new').click();
  await expect(page.getByTestId('sim-result-new').getByTestId('sim-rule-snapshot')).toHaveText(`룰 스냅샷 ${nc.response.ruleSnapshotId}`);
  const ev = sample('A-2001-cancel');
  await page.getByTestId('tab-event').click();
  const eventForm = page.getByTestId('form-event');
  const policySuffix = String(ev.request['policyNo']).slice(-4);
  await eventForm.getByTestId('field-policyKey').getByRole('button', { name: new RegExp(`${policySuffix}$`) }).click();
  await eventForm.getByTestId('field-asOfDate').locator('input').fill(String(ev.request['asOfDate']));
  await eventForm.getByTestId('field-elapsedInstallments').locator('input').fill(String(ev.request['elapsedInstallments']));
  await page.getByTestId('run-event').click();
  await expect(page.getByTestId('sim-result-event').getByTestId('sim-rule-snapshot')).toHaveText(`룰 스냅샷 ${ev.response.ruleSnapshotId}`);
  screens['simulation'] = { newContract: nc.response.ruleSnapshotId, cancel: ev.response.ruleSnapshotId };
  await check(page, 'simulation');

  // 알림함
  await go(page, '/notifications');
  await expect(page.getByTestId('notifications').locator('[data-testid="notification"]').first()).toBeVisible();
  screens['notifications'] = { items: await page.getByTestId('notifications').locator('[data-testid="notification"]').count() };
  await check(page, 'notifications');

  // 문의 접수(기타 — 라인 없이)
  await go(page, '/inquiries/new');
  await page.getByTestId('inquiry-message').fill('데모 문의 — 지난달 명세의 정정 항목을 확인 부탁드립니다');
  const created = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/v1/me/inquiries' && r.request().method() === 'POST');
  await page.getByTestId('inquiry-submit').click();
  expect((await created).status()).toBe(201);
  await expect(page.getByTestId('inquiry-status')).toHaveText('접수됨');
  screens['inquiry'] = { status: await page.getByTestId('inquiry-status').innerText() };
  await check(page, 'inquiry');

  // 로그아웃 → 로그인 화면, 다시 누르면 IdP가 자격 증명을 다시 묻는다(종료 끝점이 세션을 끝냈다)
  await page.getByRole('button', { name: '로그아웃' }).click();
  await expect(page.getByRole('button', { name: '로그인', exact: true })).toBeVisible();
  await check(page, 'after logout');
  const popupEvent = page.waitForEvent('popup');
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  const popup = await popupEvent;
  await expect(popup.locator('#username')).toBeVisible();
  await popup.close();

  finalChecks(w);
  mkdirSync(out, { recursive: true });
  writeFileSync(resolve(out, 'screens.json'), JSON.stringify(screens, null, 2));
});

test('콜백 문서는 code·state를 그리지 않고 주소에서 바로 지운다(조건 B·주입 S16)', async ({ page, context }) => {
  const w = await watch(context, page);
  const canary = 'c0de-canary-7Hq2';
  const state = 'S'.repeat(43);
  await page.goto(`${BASE}/auth/callback?code=${canary}&state=${state}`);
  await expect(page.getByTestId('screen-callback')).toBeVisible();
  expect(await page.evaluate(() => location.pathname + location.search)).toBe('/auth/callback');
  const dom = await page.content();
  expect(dom.includes(canary), 'code in the callback DOM').toBe(false);
  expect(dom.includes(state), 'state in the callback DOM').toBe(false);
  expect(w.console.filter((c) => c.includes(canary) || c.includes(state)), 'console').toEqual([]);
});

test('운영: recon run(MATCH — OK)·status·운영자 재구축 전후 다섯 화면 응답 동일', async () => {
  const token = await directGrant(AGENT);
  const paths = ['/api/v1/me', '/api/v1/me/earnings?from=202603&to=202802', '/api/v1/me/earnings/202608', '/api/v1/me/limits', '/api/v1/me/deferrals?from=202801&to=202812'];
  const before = await Promise.all(paths.map((p) => api(p, token)));
  for (const r of before) expect(r.status).toBe(200);

  const recon = cli('recon', 'run', `--tenant=${TENANT}`, '--ym=202608,202609');
  expect(recon.code, recon.out).toBe(0);
  const line = new RegExp(`^${TENANT} recon OK checked=(\\d+) matched=(\\d+) drift_new=0 `, 'm').exec(recon.out);
  expect(line, recon.out).not.toBeNull();
  expect(Number(line?.[1])).toBeGreaterThan(0);
  expect(line?.[2], 'every checked month matched').toBe(line?.[1]);
  const status = cli('status', `--tenant=${TENANT}`);
  expect(status.code, status.out).toBe(0);
  expect(status.out).toContain('drift_unresolved=0');

  const rebuild = cli('ingest', 'rebuild', `--tenant=${TENANT}`);
  expect(rebuild.code, rebuild.out).toBe(0);
  const after = await Promise.all(paths.map((p) => api(p, token)));
  for (const [i, r] of after.entries()) expect(r.body, paths[i]).toBe(before[i]?.body);
  expect([...before, ...after].flatMap((r) => leaks(r.body))).toEqual([]);
  mkdirSync(out, { recursive: true });
  writeFileSync(
    resolve(out, 'operations.json'),
    JSON.stringify({ recon: recon.out.trim(), reconMs: recon.ms, status: status.out.trim().split('\n').length, rebuild: rebuild.out.trim(), rebuildMs: rebuild.ms }, null, 2),
  );
});

/** Jaeger 2.x 질의 API v3(OTLP JSON — `/api/v3/traces`). 내부 API `/api/traces`는 2.22에 없다(첫 CI: 404). 스트리밍 응답은 줄마다 JSON 하나. */
interface OtlpAttribute {
  key: string;
  value: Record<string, unknown>;
}
interface OtlpSpan {
  name: string;
  attributes?: OtlpAttribute[];
}
interface ResourceSpans {
  resource?: { attributes?: OtlpAttribute[] };
  scopeSpans?: { spans?: OtlpSpan[] }[];
}

async function jaegerSpans(): Promise<ResourceSpans[]> {
  const now = Date.now();
  const q = new URLSearchParams({
    'query.service_name': 'ga-agent-portal',
    'query.start_time_min': new Date(now - 2 * 3600_000).toISOString(),
    'query.start_time_max': new Date(now + 60_000).toISOString(),
    'query.search_depth': '1500',
  });
  const r = await fetch(`${JAEGER}/api/v3/traces?${q.toString()}`);
  if (!r.ok) throw new Error(`jaeger answered ${String(r.status)} (${r.headers.get('content-type') ?? '-'})`);
  const text = await r.text();
  return text
    .split('\n')
    .filter((l) => l.trim() !== '')
    .flatMap((l) => {
      const v = JSON.parse(l) as { result?: { resourceSpans?: ResourceSpans[] }; resourceSpans?: ResourceSpans[]; error?: unknown };
      if (v.error !== undefined) throw new Error('jaeger answered an error object');
      return v.result?.resourceSpans ?? v.resourceSpans ?? [];
    });
}

/** docs/지표.md §4 — 앱 스팬 표, 프레임워크 스팬 표, 수집 경로가 더하는 키(닫힌 두 목록). */
function catalog() {
  const doc = readFileSync(resolve(repo, 'docs/지표.md'), 'utf8');
  const app = new Map<string, Set<string>>();
  for (const m of doc.matchAll(/^\| `([a-z.]+)` \| [^|]* \| ([^|]*) \|$/gm)) {
    app.set(m[1] ?? '', new Set([...(m[2] ?? '').matchAll(/`([a-z._]+)`/g)].map((k) => k[1] ?? '')));
  }
  const block = (name: string) => {
    const b = new RegExp('```' + name + '\\n([\\s\\S]*?)\\n```').exec(doc);
    if (b?.[1] === undefined) throw new Error(`no ${name} block in docs/지표.md`);
    return new Set(b[1].split('\n').map((l) => l.trim()).filter((l) => l !== ''));
  };
  return { app, http: block('framework-span-keys'), span: block('collector-span-keys'), process: block('collector-process-keys') };
}

test('Jaeger: 스팬 이름·키가 닫힌 표 안, 값에 누출 0(P-21)', async () => {
  const { app, http, span, process } = catalog();
  expect(app.size).toBeGreaterThanOrEqual(11);
  let traces: ResourceSpans[] = [];
  const spansOf = (rs: ResourceSpans) => (rs.scopeSpans ?? []).flatMap((ss) => ss.spans ?? []);
  await expect(async () => {
    traces = await jaegerSpans();
    const names = new Set(traces.flatMap((rs) => spansOf(rs).map((s) => s.name)));
    expect(names.has('ingest.apply') && names.has('recon.run') && [...names].some((n) => n.startsWith('http '))).toBe(true);
  }).toPass({ timeout: 60_000 });
  const problems: string[] = [];
  const seen = { spans: 0, names: new Set<string>(), keys: new Set<string>(), processKeys: new Set<string>() };
  for (const rs of traces) {
    for (const s of spansOf(rs)) {
      seen.spans++;
      seen.names.add(s.name);
      const own = app.get(s.name) ?? (/^http [a-z]+ /.test(s.name) ? http : null);
      if (own === null) problems.push(`span outside the table: ${s.name}`);
      for (const a of s.attributes ?? []) {
        seen.keys.add(a.key);
        if (!(own?.has(a.key) ?? false) && !span.has(a.key)) problems.push(`${s.name}: key ${a.key}`);
        const v = JSON.stringify(a.value);
        if (leaks(v).length > 0 || /A-20[0-9]{2}/.test(v)) problems.push(`${s.name}: value of ${a.key} leaks`);
      }
    }
    for (const a of rs.resource?.attributes ?? []) {
      seen.processKeys.add(a.key);
      if (!process.has(a.key)) problems.push(`resource key ${a.key}`);
    }
  }
  mkdirSync(out, { recursive: true });
  writeFileSync(
    resolve(out, 'jaeger.json'),
    JSON.stringify({ resourceSpans: traces.length, spans: seen.spans, names: [...seen.names].sort(), keys: [...seen.keys].sort(), processKeys: [...seen.processKeys].sort() }, null, 2),
  );
  expect([...new Set(problems)]).toEqual([]);
  expect(policyNumbers()).toHaveLength(432);
});
