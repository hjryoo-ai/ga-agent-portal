// 번들 크기(포털 Phase 4 계획 ③·Q3, C9): Vite 매니페스트로 초기 로드(index.html + 진입 청크 + 정적 import 닫힘 + 그 CSS)와
// 경로별 지연 청크(동적 import마다 그 정적 닫힘에서 초기 로드에 이미 있는 것을 뺀 것)의 gzip(-9) 크기를 build/web/bundle-size.json에 쓴다.
// 초기 로드가 상한을 넘으면 실패한다.
import { readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { gzipSync } from 'node:zlib';

// 상한(바이트, gzip — 계획 Q3): 5단계 측정값 118,135 B(화면 다섯·React 19·라우터·TanStack Query·openapi-fetch) + 10%를 KiB로 올림 = 127 KiB.
// 늘려야 하면 측정표와 함께 계획·보고서에서 다시 정한다(조용히 올리지 않는다).
const INITIAL_GZIP_CAP = 127 * 1024;
// 지연 청크여야 하는 화면(닫힌 목록 — 포털 Phase 5 계획 ⑧ "두 경로는 지연 청크"): 각 항목은 동적 진입점이고 초기 로드의 정적 닫힘에 없어야 한다.
// 목록 밖의 동적 진입점이 생기거나 목록의 화면이 정적으로 import되면 실패한다.
const LAZY_SCREENS = [
  'screens/StatementScreen.tsx',
  'screens/LineScreen.tsx',
  'screens/LimitsScreen.tsx',
  'screens/LimitScreen.tsx',
  'screens/TimelineScreen.tsx',
  'screens/SimulationScreen.tsx',
];

const web = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dist = resolve(web, 'build/web/dist');
const manifest = JSON.parse(await readFile(resolve(dist, '.vite/manifest.json'), 'utf8'));
const gz = async (file) => gzipSync(await readFile(resolve(dist, file)), { level: 9 }).length;

function closure(keys) {
  const seen = new Set();
  const stack = [...keys];
  while (stack.length > 0) {
    const k = stack.pop();
    if (seen.has(k)) continue;
    seen.add(k);
    stack.push(...(manifest[k]?.imports ?? []));
  }
  return seen;
}
const filesOf = (keys) => new Set([...keys].flatMap((k) => [manifest[k].file, ...(manifest[k].css ?? [])]));

const entryKey = Object.keys(manifest).find((k) => manifest[k].isEntry);
if (!entryKey) throw new Error('no entry in the Vite manifest');
const initialKeys = closure([entryKey]);
const initialFiles = ['index.html', ...filesOf(initialKeys)];
let initial = 0;
const parts = {};
for (const f of initialFiles) {
  parts[f] = await gz(f);
  initial += parts[f];
}
const routes = {};
for (const k of Object.keys(manifest).filter((k) => manifest[k].isDynamicEntry)) {
  const own = [...filesOf(closure([k]))].filter((f) => !initialFiles.includes(f));
  let size = 0;
  for (const f of own) size += await gz(f);
  routes[k] = { files: own, gzipBytes: size };
}
const lazyProblems = [];
for (const k of LAZY_SCREENS) {
  if (!manifest[k]?.isDynamicEntry) lazyProblems.push(`${k} is not a lazy route chunk`);
  if (initialKeys.has(k)) lazyProblems.push(`${k} is in the initial load (imported statically)`);
}
for (const k of Object.keys(routes)) {
  if (!LAZY_SCREENS.includes(k)) lazyProblems.push(`${k} is a lazy chunk outside the closed list`);
}
const report = { initialGzipBytes: initial, capGzipBytes: INITIAL_GZIP_CAP, initial: parts, routes };
await writeFile(resolve(web, 'build/web/bundle-size.json'), JSON.stringify(report, null, 2) + '\n');
if (lazyProblems.length > 0) {
  for (const x of lazyProblems) console.error(x);
  process.exit(1);
}
if (initial > INITIAL_GZIP_CAP) {
  console.error(`initial load ${initial} B gzip exceeds the cap ${INITIAL_GZIP_CAP} B`);
  process.exit(1);
}
console.log(`bundle size ok: initial ${initial} B gzip (cap ${INITIAL_GZIP_CAP}), ${Object.keys(routes).length} lazy routes`);
