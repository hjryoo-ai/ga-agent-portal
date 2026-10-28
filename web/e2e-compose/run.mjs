// compose E2E 실행기(포털 Phase 8 지시문 ⑥·C5·C6, 계획 ⑤): ./gradlew :web:composeE2e(CI 잡 compose-e2e)가 부른다. 이미지는 그 앞 단계가 만든다.
//  ① `docker compose --profile demo up -d` — 기동 시간: 포털 준비(healthy)·등록(seed 종료 0)·첫 동기화 끝(설계사 /me의 asOf.seq = 스트림 길이)
//  ② Playwright(playwright.compose.config.ts — 데스크톱 Chromium 하나) ③ compose 설정 출력·전 컨테이너 로그를 산출물로
//  ④ 산출물 전체를 잡은 비밀(토큰·code·state)과 센티널(데모 증권번호 전부)로 훑는다. 내리기는 CI 단계가 비밀 스캔 뒤에 한다(볼륨의 비밀 파일이 필요하다).
// 비밀은 build/compose-e2e/.state(700)의 파일에만 — 인자·환경 변수·출력에 0.
import { execFileSync, spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const web = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repo = resolve(web, '..');
const out = resolve(web, 'build/compose-e2e');
const state = join(out, '.state');
const COMPOSE = ['compose', '-f', resolve(repo, 'docker/compose.yaml'), '--profile', 'demo'];
const BASE = 'http://localhost:8080';
const ISSUER = 'http://localhost:8180/realms/demo';
const STREAM = resolve(repo, 'contracts/shared/ga-commission-engine/samples/demo/demo_ga.jsonl');

const docker = (...args) => execFileSync('docker', [...COMPOSE, ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 256 * 1024 * 1024 });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function until(what, ms, check) {
  const end = Date.now() + ms;
  for (;;) {
    if (await check()) return;
    if (Date.now() > end) throw new Error(`timed out waiting for ${what}`);
    await sleep(1000);
  }
}
function services() {
  return docker('ps', '-a', '--format', 'json')
    .split('\n')
    .filter((l) => l.trim() !== '')
    .flatMap((l) => {
      const v = JSON.parse(l);
      return Array.isArray(v) ? v : [v];
    });
}
const service = (name) => services().find((s) => s.Service === name);

rmSync(out, { recursive: true, force: true });
mkdirSync(state, { recursive: true, mode: 0o700 });
mkdirSync(join(out, 'logs'), { recursive: true });

const password = /데모 비밀번호: `([^`]+)`/.exec(readFileSync(resolve(repo, 'README.md'), 'utf8'))?.[1];
const streamLength = readFileSync(STREAM, 'utf8').split('\n').filter((l) => l !== '').length;
const timings = {};
let status = 1;
try {
  if (password === undefined) throw new Error('README has no demo password line');
  const t0 = Date.now();
  docker('up', '-d');
  await until('the portal to be healthy', 600_000, () => {
    const p = service('portal');
    if (p !== undefined && p.State === 'exited') throw new Error('the portal container exited — see logs/compose.log');
    return p?.Health === 'healthy';
  });
  timings.portalHealthyMs = Date.now() - t0;
  await until('the seed to finish', 120_000, () => {
    const s = service('seed');
    if (s?.State === 'exited' && s.ExitCode !== 0) throw new Error(`seed exited with ${String(s.ExitCode)}`);
    return s?.State === 'exited' && s.ExitCode === 0;
  });
  timings.seededMs = Date.now() - t0;
  let token = '';
  await until('the first sync of the demo stream', 600_000, async () => {
    try {
      if (token === '') {
        const r = await fetch(`${ISSUER}/protocol/openid-connect/token`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: new URLSearchParams({ grant_type: 'password', client_id: 'k6', username: 'a-2001', password, scope: 'openid' }),
        });
        if (!r.ok) return false;
        token = (await r.json()).access_token;
        writeFileSync(join(state, 'secrets'), `${token}\n`, { mode: 0o600, flag: 'a' });
      }
      const me = await fetch(`${BASE}/api/v1/me`, { headers: { Authorization: `Bearer ${token}` } });
      if (me.status === 401) token = '';
      return me.ok && (await me.json()).asOf.seq >= streamLength;
    } catch {
      return false;
    }
  });
  timings.syncedMs = Date.now() - t0;
  const pw = spawnSync(resolve(web, 'node_modules/.bin/playwright'), ['test', '--config', resolve(web, 'playwright.compose.config.ts')], {
    cwd: web,
    stdio: 'inherit',
    env: { ...process.env, TZ: 'UTC' },
  });
  status = pw.status ?? 1;
} catch (e) {
  console.error(String(e));
} finally {
  try {
    writeFileSync(join(out, 'compose-config.yaml'), docker('config'));
    writeFileSync(join(out, 'logs', 'compose.log'), docker('logs', '--no-color', '--timestamps'));
    writeFileSync(join(out, 'services.json'), JSON.stringify(services().map((s) => ({ service: s.Service, image: s.Image, state: s.State, health: s.Health, exit: s.ExitCode })), null, 2));
  } catch (e) {
    console.error(`could not collect compose output: ${String(e)}`);
    status = 1;
  }
}

// 산출물 스캔(C6·조건 B): 잡은 비밀과 데모 증권번호 전부가 build/compose-e2e(.state 밖)의 어느 파일에도 없어야 한다.
const secrets = existsSync(join(state, 'secrets')) ? readFileSync(join(state, 'secrets'), 'utf8').split('\n').filter((l) => l !== '') : [];
const policies = [...new Set(readFileSync(STREAM, 'utf8').split('\n').filter((l) => l !== '').map((l) => JSON.parse(l).payload.policyNo).filter((p) => typeof p === 'string'))];
const forbidden = [...new Set([...secrets, ...policies])];
const hits = [];
const walk = (d) => {
  for (const name of readdirSync(d)) {
    const p = join(d, name);
    if (p === state) continue;
    if (statSync(p).isDirectory()) walk(p);
    else {
      const text = readFileSync(p, 'utf8');
      for (const f of forbidden) if (text.includes(f)) hits.push(`${p.slice(out.length + 1)}: ${f.slice(0, 4)}…`);
    }
  }
};
walk(out);
const summary = { streamLength, timings, secretsCaptured: secrets.length, policySentinels: policies.length, artifactHits: hits.length };
writeFileSync(join(out, 'summary.json'), JSON.stringify(summary, null, 2));
console.log(`compose E2E summary: ${JSON.stringify(summary)}`);
if (hits.length > 0) {
  console.error(`compose E2E artifacts contain secrets or sentinels:\n${hits.join('\n')}`);
  status = 1;
}
if (secrets.length < 5) {
  console.error('compose E2E: fewer than five secrets were captured — the leak checks would be vacuous');
  status = 1;
}
rmSync(state, { recursive: true, force: true });
process.exit(status);
