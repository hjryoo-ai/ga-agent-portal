// 번들 크기(포털 Phase 4 계획 ③·Q3, C9): Vite 매니페스트로 초기 로드(index.html + 진입 청크 + 정적 import 닫힘 + 그 CSS)와
// 경로별 지연 청크(동적 import마다 그 정적 닫힘에서 초기 로드에 이미 있는 것을 뺀 것)의 gzip(-9) 크기를 build/web/bundle-size.json에 쓴다.
// 초기 로드가 상한을 넘으면 실패한다.
import { readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { gzipSync } from 'node:zlib';

// 상한(바이트, gzip). 5단계에서 측정값 + 10%(KiB 올림)로 고정한다 — 그 전까지는 골격용 임시 값.
const INITIAL_GZIP_CAP = 300 * 1024;

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
const report = { initialGzipBytes: initial, capGzipBytes: INITIAL_GZIP_CAP, initial: parts, routes };
await writeFile(resolve(web, 'build/web/bundle-size.json'), JSON.stringify(report, null, 2) + '\n');
if (initial > INITIAL_GZIP_CAP) {
  console.error(`initial load ${initial} B gzip exceeds the cap ${INITIAL_GZIP_CAP} B`);
  process.exit(1);
}
console.log(`bundle size ok: initial ${initial} B gzip (cap ${INITIAL_GZIP_CAP}), ${Object.keys(routes).length} lazy routes`);
