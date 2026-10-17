// 라이선스 검사(포털 Phase 4 계획 Q13 — 넓힘): 설치된 모든 패키지(개발용 포함)의 이름·판·라이선스를 THIRD-PARTY.md와 대조한다.
//   check: ① 설치된 패키지 = 목록(빠진 것·남은 것 모두 실패) ② 라이선스가 허용 목록 밖이면 실패 — 단 DEV_ONLY(닫힌 이름 목록)는 개발 도구 전용 예외
//          ③ 번들에 실제로 묶인 패키지(build/web/bundle-modules.json)는 예외 없이 허용 목록 안 ④ 쓰이지 않는 DEV_ONLY 예외가 남으면 실패.
//   write: THIRD-PARTY.md를 다시 쓴다(의존성을 바꾼 커밋에서만).
// 플랫폼 바이너리(@rolldown/binding-*, lightningcss-*)는 이름을 `<platform>`으로 맞춰 운영체제마다 같은 목록이 되게 하고, fsevents(macOS 전용)는
// 설치되지 않아도 남은 항목으로 치지 않는다.
import { readdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const web = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const LIST = resolve(web, 'THIRD-PARTY.md');
const ALLOWED = new Set(['MIT', 'ISC', 'Apache-2.0', 'BSD-2-Clause', 'BSD-3-Clause', '0BSD', 'BlueOak-1.0.0', 'MIT-0']);
/** 개발 도구 전용 예외(이름 → 라이선스). 번들에 들어가면 실패한다. */
const DEV_ONLY = new Map([
  ['lightningcss', 'MPL-2.0'],
  ['lightningcss-<platform>', 'MPL-2.0'],
  ['mdn-data', 'CC0-1.0'],
  ['argparse', 'Python-2.0'],
  ['type-fest', '(MIT OR CC0-1.0)'],
  ['caniuse-lite', 'CC-BY-4.0'],
  // E2E 접근성 검사(Playwright + axe — 지시문 ⑦). 번들 밖.
  ['axe-core', 'MPL-2.0'],
  ['@axe-core/playwright', 'MPL-2.0'],
]);
const PLATFORM_OPTIONAL = new Set(['fsevents']);

const normalize = (name) =>
  name.replace(/^@rolldown\/binding-.+$/, '@rolldown/binding-<platform>').replace(/^lightningcss-(?!.*<).+$/, 'lightningcss-<platform>');

async function installed() {
  const store = resolve(web, 'node_modules/.pnpm');
  const out = new Map();
  for (const entry of await readdir(store)) {
    const nm = join(store, entry, 'node_modules');
    let names;
    try {
      names = await readdir(nm);
    } catch {
      continue;
    }
    for (const n of names) {
      const dirs = n.startsWith('@') ? (await readdir(join(nm, n))).map((s) => `${n}/${s}`) : [n];
      for (const d of dirs) {
        let pkg;
        try {
          pkg = JSON.parse(await readFile(join(nm, d, 'package.json'), 'utf8'));
        } catch {
          continue;
        }
        if (!pkg.name || !pkg.version) continue;
        let license = pkg.license ?? pkg.licenses;
        if (Array.isArray(license)) license = license.map((l) => (typeof l === 'string' ? l : l.type)).join(' OR ');
        else if (license && typeof license === 'object') license = license.type;
        out.set(`${normalize(pkg.name)}@${pkg.version}`, { name: normalize(pkg.name), version: pkg.version, license: String(license ?? 'UNKNOWN') });
      }
    }
  }
  return out;
}

async function bundled() {
  const chunks = JSON.parse(await readFile(resolve(web, 'build/web/bundle-modules.json'), 'utf8'));
  const names = new Set();
  for (const c of Object.values(chunks)) {
    for (const m of c.modules) {
      const match = m.match(/node_modules\/\.pnpm\/[^/]+\/node_modules\/((?:@[^/]+\/)?[^/]+)\//);
      if (match) names.add(normalize(match[1]));
    }
  }
  return names;
}

const mode = process.argv[2] ?? 'check';
const pkgs = [...(await installed()).values()].sort((a, b) => (a.name === b.name ? a.version.localeCompare(b.version) : a.name.localeCompare(b.name)));
const inBundle = await bundled();
const row = (p) => `| \`${p.name}\` | ${p.version} | ${p.license} | ${inBundle.has(p.name) ? '예' : ''} |`;
const header = [
  '# web — 서드파티 패키지',
  '',
  '설치되는 모든 패키지(개발용 포함). `scripts/licenses.mjs`가 설치본과 이 표를 대조한다 — 의존성을 바꾼 커밋에서 `node scripts/licenses.mjs write`로 다시 쓴다.',
  '허용 라이선스: ' + [...ALLOWED].join(', ') + '. 개발 도구 전용 예외(번들 밖): ' + [...DEV_ONLY].map(([n, l]) => `\`${n}\` ${l}`).join(', ') + '.',
  '플랫폼 바이너리는 `<platform>`으로 적는다. "번들"은 배포 산출물에 실제로 묶인 패키지다.',
  '',
  '| 패키지 | 판 | 라이선스 | 번들 |',
  '|---|---|---|---|',
];
if (mode === 'write') {
  await writeFile(LIST, header.concat(pkgs.map(row)).join('\n') + '\n');
  console.log(`wrote THIRD-PARTY.md (${pkgs.length} packages, ${inBundle.size} bundled)`);
  process.exit(0);
}

const failures = [];
const listed = new Set((await readFile(LIST, 'utf8')).split('\n').filter((l) => l.startsWith('| `')));
const expected = new Set(pkgs.map(row));
for (const r of expected) if (!listed.has(r)) failures.push(`not in THIRD-PARTY.md: ${r}`);
for (const r of listed) {
  if (expected.has(r)) continue;
  const name = r.split('`')[1];
  if (!PLATFORM_OPTIONAL.has(name)) failures.push(`stale THIRD-PARTY.md row: ${r}`);
}
const usedExceptions = new Set();
for (const p of pkgs) {
  if (ALLOWED.has(p.license)) continue;
  if (DEV_ONLY.get(p.name) === p.license && !inBundle.has(p.name)) {
    usedExceptions.add(p.name);
    continue;
  }
  failures.push(`${p.name}@${p.version}: license ${p.license} is not allowed${inBundle.has(p.name) ? ' in the bundle' : ''}`);
}
for (const n of DEV_ONLY.keys()) if (!usedExceptions.has(n)) failures.push(`unused DEV_ONLY exception: ${n}`);
if (inBundle.size === 0) failures.push('no bundled packages found — the bundle check would be vacuous');
if (failures.length > 0) {
  for (const f of failures) console.error(f);
  process.exit(1);
}
console.log(`licenses ok (${pkgs.length} packages, ${inBundle.size} bundled, ${usedExceptions.size} dev-only exceptions)`);
