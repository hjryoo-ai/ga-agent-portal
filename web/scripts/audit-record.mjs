// 취약점 기록(포털 Phase 4 지시문 ①·C12 — "기록, 올림은 없음"): pnpm audit --json 결과를 build/web/audit.json에 쓰고 심각도별 개수를 출력한다.
// 결과(취약점 있음)로는 실패하지 않는다. pnpm audit 자체를 돌리지 못하면(레지스트리 응답 없음 등) 실패한다 — 기록이 없는 것을 성공으로 치지 않는다.
import { spawnSync } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const web = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const pnpm = process.env.npm_execpath;
if (!pnpm) {
  console.error('npm_execpath is not set — run through `pnpm run audit:record`');
  process.exit(2);
}
const viaNode = /\.[cm]?js$/.test(pnpm);
const r = spawnSync(viaNode ? process.execPath : pnpm, [...(viaNode ? [pnpm] : []), 'audit', '--json'], { cwd: web, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
let report;
try {
  report = JSON.parse(r.stdout);
} catch {
  console.error(`pnpm audit did not produce JSON (exit ${r.status}): ${(r.stderr || r.stdout).slice(0, 500)}`);
  process.exit(1);
}
await mkdir(resolve(web, 'build/web'), { recursive: true });
await writeFile(resolve(web, 'build/web/audit.json'), JSON.stringify(report, null, 2) + '\n');
const v = report.metadata?.vulnerabilities ?? {};
console.log(`pnpm audit recorded: ${JSON.stringify(v)} (dependencies ${report.metadata?.totalDependencies ?? '?'}) — build/web/audit.json`);
