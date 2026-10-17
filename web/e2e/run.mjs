// 화면 E2E(포털 Phase 4 계획 ⑨): ./gradlew :web:e2e가 부른다. 순서:
//  ① Java 하네스(WebE2eHarness — PostgreSQL·발급기·피드 스텁·등록, 상태 디렉터리에 파일로만) ② 포털 bootJar(인제스트 켬, 같은 출처로 화면 서빙)
//  ③ Playwright(세 프로젝트) ④ 내리기(실패해도) ⑤ build/e2e 산출물(로그·보고서) 전체를 토큰·센티널로 스캔.
// --serve(로컬 실행 — ./gradlew :web:e2eServe): ③ 대신 모든 테넌트의 머리를 끝까지 열고, 설계사 로그인 주소(…/#token=…)를 .state/login-urls.txt(600)에만
// 쓴 뒤 Ctrl-C까지 기다린다. 주소(토큰)는 출력하지 않는다 — 파일 경로만.
// 비밀(토큰·서비스 토큰·policyKey 비밀)은 build/e2e/.state(700)의 파일에만 — 명령 인자·환경 변수·출력에 싣지 않는다. 환경 변수로 넘기는 것은 경로뿐.
import { spawn, spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, openSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:net';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const web = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repo = resolve(web, '..');
const out = resolve(web, 'build/e2e');
const state = join(out, '.state');
const logs = join(out, 'logs');
const need = (name) => {
  const v = process.env[name];
  if (!v) throw new Error(`${name} is not set — run through ./gradlew :web:e2e`);
  return v;
};
const jar = need('PORTAL_E2E_JAR');
const java = need('PORTAL_E2E_JAVA');
const classpath = readFileSync(need('PORTAL_E2E_HARNESS_CP'), 'utf8').trim();
export const PROJECTS = { 'mobile-android': 'A', 'mobile-ios': 'B', desktop: 'C' };

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function until(what, ms, check) {
  const end = Date.now() + ms;
  for (;;) {
    if (await check()) return;
    if (Date.now() > end) throw new Error(`timed out waiting for ${what}`);
    await sleep(500);
  }
}
function freePort() {
  return new Promise((ok, fail) => {
    const s = createServer();
    s.listen(0, '127.0.0.1', () => {
      const { port } = s.address();
      s.close(() => ok(port));
    });
    s.on('error', fail);
  });
}

rmSync(out, { recursive: true, force: true });
mkdirSync(state, { recursive: true, mode: 0o700 });
mkdirSync(logs, { recursive: true });

const children = [];
function start(name, args, env = {}) {
  const log = openSync(join(logs, `${name}.log`), 'w');
  const child = spawn(java, args, { cwd: repo, env: { ...process.env, ...env, TZ: 'UTC' }, stdio: ['ignore', log, log] });
  children.push(child);
  return child;
}
async function stopAll() {
  for (const c of children.reverse()) {
    if (c.exitCode === null) {
      c.kill('SIGTERM');
      await Promise.race([new Promise((r) => c.once('exit', r)), sleep(20_000)]);
      if (c.exitCode === null) c.kill('SIGKILL');
    }
  }
}

let status;
try {
  const harness = start('harness', ['-Dfile.encoding=UTF-8', `-Dga.repoRoot=${repo}`, '-cp', classpath, 'com.ga.portal.app.web.WebE2eHarness', state, Object.values(PROJECTS).join(',')]);
  await until('the harness (PostgreSQL, issuers, feeds)', 240_000, () => {
    if (harness.exitCode !== null) throw new Error(`harness exited with ${harness.exitCode} — see build/e2e/logs/harness.log`);
    return existsSync(join(state, 'ready'));
  });
  const env = JSON.parse(readFileSync(join(state, 'env.json'), 'utf8'));
  const port = await freePort();
  const baseUrl = `http://127.0.0.1:${port}`;
  const portal = start('portal', ['-Dfile.encoding=UTF-8', '-jar', jar, `--server.port=${port}`], {
    PORTAL_DB_URL: env.dbUrl,
    PORTAL_INGEST_ENABLED: 'true',
    PORTAL_ENGINE_TOKEN_DIR: env.engineTokenDir,
    PORTAL_POLICY_KEY_SECRET_FILE: env.policyKeyFile,
  });
  await until('the portal', 180_000, async () => {
    if (portal.exitCode !== null) throw new Error(`portal exited with ${portal.exitCode} — see build/e2e/logs/portal.log`);
    try {
      return (await fetch(`${baseUrl}/actuator/health`)).ok;
    } catch {
      return false;
    }
  });
  writeFileSync(join(out, 'run.json'), JSON.stringify({ baseUrl, controlUrl: env.controlUrl, tokenDir: env.tokenDir, streamLength: env.streamLength, projects: PROJECTS }, null, 2));
  if (process.argv.includes('--serve')) {
    const lines = [];
    for (const [tenant, length] of Object.entries(env.streamLength)) {
      if (!tenant.endsWith('C')) continue; // 한 벌이면 된다(데스크톱 벌)
      const r = await fetch(`${env.controlUrl}/head/${tenant}/${String(length)}`, { method: 'POST' });
      if (r.status !== 204) throw new Error(`could not open the feed of ${tenant}`);
      lines.push(`${tenant}\t${baseUrl}/#token=${readFileSync(join(env.tokenDir, `${tenant}-agent.jwt`), 'utf8').trim()}`);
    }
    writeFileSync(join(state, 'login-urls.txt'), lines.join('\n') + '\n', { mode: 0o600 });
    console.log(`portal ${baseUrl} — open a login URL from ${join(state, 'login-urls.txt')} (tokens expire in 3 h). Ctrl-C stops everything.`);
    await new Promise((ok) => {
      process.once('SIGINT', ok);
      process.once('SIGTERM', ok);
    });
    status = 0;
  } else {
    // 주입 시험에서 범위를 좁힐 때만(예 "--project=desktop ga03") — 비밀이 아닌 Playwright 인자. CI는 쓰지 않는다(전체).
    const narrow = (process.env.PORTAL_E2E_PLAYWRIGHT_ARGS ?? '').split(' ').filter((a) => a !== '');
    const pw = spawnSync(resolve(web, 'node_modules/.bin/playwright'), ['test', '--config', resolve(web, 'playwright.config.ts'), ...narrow], { cwd: web, stdio: 'inherit', env: { ...process.env, TZ: 'UTC' } });
    status = pw.status ?? 1;
  }
} catch (e) {
  console.error(String(e));
  status = 1;
} finally {
  await stopAll();
}

// ⑤ 산출물 스캔: 토큰 값과 센티널(스트림의 증권번호 원문·이웃 설계사 ID — 허구)이 로그·보고서에 한 번도 없어야 한다. .state(비밀 자체)는 빼고 본다.
const secrets = existsSync(join(state, 'tokens')) ? readdirSync(join(state, 'tokens')).map((f) => readFileSync(join(state, 'tokens', f), 'utf8').trim()) : [];
const { sentinels } = await import('./support/sentinels.mjs');
const forbidden = [...secrets, ...sentinels()];
const hits = [];
const walk = (d) => {
  for (const name of readdirSync(d)) {
    const p = join(d, name);
    if (p === state) continue;
    if (statSync(p).isDirectory()) walk(p);
    else {
      const text = readFileSync(p, 'latin1');
      const utf8 = readFileSync(p, 'utf8');
      for (const f of forbidden) if (utf8.includes(f) || text.includes(f)) hits.push(`${p.slice(out.length + 1)}: ${f.slice(0, 3)}…`);
    }
  }
};
walk(out);
writeFileSync(join(out, 'artifact-scan.json'), JSON.stringify({ forbidden: forbidden.length, tokens: secrets.length, hits }, null, 2));
if (hits.length > 0) {
  console.error(`E2E artifacts contain secrets or sentinels:\n${hits.join('\n')}`);
  status = 1;
}
rmSync(state, { recursive: true, force: true });
process.exit(status);
