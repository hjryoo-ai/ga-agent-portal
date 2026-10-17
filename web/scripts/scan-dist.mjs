// Vite 산출물 검사(webBuild 단계, 포털 Phase 4 계획 ③·④-c, C2·C8):
//  ① HTML: 인라인 스크립트·인라인 스타일·style 속성·on* 이벤트 속성 0, src·href는 /assets/만, 외부 URL 0
//  ② CSS: url()은 /assets/만, @import 0
//  ③ 소스맵 0, 시험 원천이 묶인 청크 0
//  ④ 금액 연산 정규식 — 앱 청크(src/ 모듈이 든 청크)에서 계약 금액 속성(생성물에서 읽은 닫힌 목록) 옆의 산술·비교 연산자, Math.*·Number() 인자 0.
//     TS(불투명 Won)와 ESLint(no-money-arithmetic)를 형변환으로 우회한 연산을 잡는 마지막 층이다. 라이브러리 청크는 보지 않는다(속성 이름이 겹친다).
import { readdir, readFile } from 'node:fs/promises';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const web = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dist = resolve(web, 'build/web/dist');
const failures = [];

async function walk(dir) {
  const out = [];
  for (const e of await readdir(dir, { withFileTypes: true })) {
    const p = join(dir, e.name);
    if (e.isDirectory()) out.push(...(await walk(p)));
    else out.push(p);
  }
  return out;
}

const files = await walk(dist);
const rel = (f) => relative(dist, f).split('\\').join('/');
if (!files.some((f) => rel(f) === 'index.html')) failures.push('missing index.html');

for (const f of files) {
  const name = rel(f);
  if (name.endsWith('.map')) failures.push(`${name}: source map in the output`);
  if (name.endsWith('.html')) {
    const text = await readFile(f, 'utf8');
    for (const m of text.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)) {
      if (!/\bsrc=/.test(m[1] ?? '') || (m[2] ?? '').trim() !== '') failures.push(`${name}: inline script`);
    }
    if (/<style\b/i.test(text)) failures.push(`${name}: inline <style>`);
    if (/\sstyle\s*=/i.test(text)) failures.push(`${name}: style attribute`);
    if (/\son[a-z]+\s*=/i.test(text)) failures.push(`${name}: event handler attribute`);
    if (/(https?:)?\/\/[a-z0-9.-]+\.[a-z]{2,}/i.test(text.replace(/<!--[\s\S]*?-->/g, ''))) failures.push(`${name}: external URL`);
    for (const m of text.matchAll(/\b(?:src|href)="([^"]*)"/g)) {
      if (!(m[1] ?? '').startsWith('/assets/')) failures.push(`${name}: reference outside /assets/: ${m[1]}`);
    }
  }
  if (name.endsWith('.css')) {
    const text = await readFile(f, 'utf8');
    if (/@import/i.test(text)) failures.push(`${name}: @import`);
    for (const m of text.matchAll(/url\(\s*['"]?([^'")]+)/g)) {
      if (!(m[1] ?? '').startsWith('/assets/')) failures.push(`${name}: url() outside /assets/: ${m[1]}`);
    }
  }
}

// ④ 금액 속성 이름 = 생성물에서 금액 스키마를 참조하는 속성(계약이 바뀌면 목록도 바뀐다)
const generated = await readFile(resolve(web, 'src/gen/portal-api.ts'), 'utf8');
const moneyProps = [...new Set([...generated.matchAll(/(\w+)\??: components\["schemas"\]\["(?:NonNegative)?Won"\]/g)].map((m) => m[1]))].sort();
if (moneyProps.length < 10) failures.push(`only ${moneyProps.length} money properties found in the generated client — the scan would be vacuous`);
const names = moneyProps.join('|');
// 산술·비교·같음 연산자. 화살표 `=>`의 `>`는 연산자가 아니다(`e=>e.gross`는 금액을 넘길 뿐이다).
const OP = '(?:\\*\\*|[-+*/%]|(?<![=!<>])[<>]=?|[=!]==?)';
const patterns = [
  new RegExp(`\\.(${names})\\b\\s*${OP}`, 'g'),
  new RegExp(`${OP}\\s*[\\w$]+(?:\\.[\\w$]+)*\\.(${names})\\b`, 'g'),
  new RegExp(`Math\\.\\w+\\([^)]*\\.(${names})\\b`, 'g'),
  new RegExp(`(?:Number|parseInt|parseFloat|BigInt)\\([^)]*\\.(${names})\\b`, 'g'),
];
const chunks = JSON.parse(await readFile(resolve(web, 'build/web/bundle-modules.json'), 'utf8'));
const tests = Object.values(chunks).flatMap((c) => c.modules).filter((m) => /\.test\.tsx?$|^src\/test\//.test(m));
for (const m of tests) failures.push(`test source in a bundle: ${m}`);
const appChunks = Object.entries(chunks).filter(([, c]) => c.modules.some((m) => m.startsWith('src/'))).map(([file]) => file);
if (appChunks.length === 0) failures.push('no app chunk — the money scan would be vacuous');
for (const file of appChunks) {
  const vendorInside = chunks[file].modules.filter((m) => m.includes('node_modules'));
  if (vendorInside.length > 0) failures.push(`${file}: app chunk also bundles library modules (${vendorInside[0]} …) — split them so the scan stays on app code`);
  const text = await readFile(resolve(dist, file), 'utf8');
  for (const p of patterns) {
    for (const m of text.matchAll(p)) failures.push(`${file}: money arithmetic near .${m[1]}: ${m[0].slice(0, 60)}`);
  }
}

if (failures.length > 0) {
  for (const f of failures) console.error(f);
  process.exit(1);
}
console.log(`dist scan ok (${files.length} files, ${appChunks.length} app chunks, ${moneyProps.length} money properties)`);
