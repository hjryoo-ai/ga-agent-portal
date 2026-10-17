// 계약 → 화면 클라이언트 타입(포털 Phase 4 계획 ③·④-a, C1). 생성물 src/gen/portal-api.ts와 잠금 파일 contracts-client.lock.json을 **둘 다 커밋**한다.
//   check: 다시 생성해 대조한다 — ① 같은 실행에서 두 번 생성한 바이트가 다르면 실패(비결정) ② 계약 SHA-256이 잠금과 다르면 실패(계약이 바뀌었는데 잠금 그대로)
//          ③ 생성물이 커밋본과 바이트가 다르면 실패(손 편집·생성기 변화) ④ 잠금의 생성물 SHA-256이 커밋본과 다르면 실패 ⑤ 생성기 판이 다르면 실패.
//   lock:  생성물과 잠금 파일을 다시 쓴다 — 계약을 바꾼 커밋에서만(./gradlew :web:clientLock).
// 금액(계약의 Won·NonNegativeWon)은 숫자가 아니라 불투명 타입으로 만든다(src/money/won.ts) — 화면 코드에서 산술·직접 렌더가 컴파일 오류가 된다(계획 Q9).
import openapiTS, { astToString } from 'openapi-typescript';
import ts from 'typescript';
import { createHash } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const web = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repo = resolve(web, '..');
const LOCK = resolve(web, 'contracts-client.lock.json');
const CONTRACT = 'contracts/api/v1/portal-api.openapi.yaml';
const OUTPUT = 'src/gen/portal-api.ts';
const MONEY_SCHEMAS = new Set(['#/components/schemas/Won', '#/components/schemas/NonNegativeWon']);

const HEADER = [
  '// 생성물 — 손으로 고치지 않는다. 계약 contracts/api/v1/portal-api.openapi.yaml → scripts/client-lock.mjs(./gradlew :web:clientLock).',
  '// 금액 스키마(Won·NonNegativeWon)는 불투명 타입 Won이다(src/money/won.ts).',
  "import type { Won } from '../money/won';",
  '',
  '',
].join('\n');

const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex');

async function generate(contractPath) {
  const seen = new Set();
  const ast = await openapiTS(pathToFileURL(contractPath), {
    alphabetize: true,
    transform(_schema, metadata) {
      if (MONEY_SCHEMAS.has(metadata.path)) {
        seen.add(metadata.path);
        return ts.factory.createTypeReferenceNode('Won');
      }
      return undefined;
    },
  });
  if (seen.size !== MONEY_SCHEMAS.size) {
    throw new Error(`money schemas not found in the contract: ${[...MONEY_SCHEMAS].filter((s) => !seen.has(s)).join(', ')}`);
  }
  return HEADER + astToString(ast);
}

const mode = process.argv[2];
if (mode !== 'check' && mode !== 'lock') {
  console.error('usage: client-lock.mjs check|lock');
  process.exit(2);
}

const generatorPkg = JSON.parse(await readFile(resolve(web, 'node_modules/openapi-typescript/package.json'), 'utf8'));
const generator = `openapi-typescript@${generatorPkg.version}`;
const contractPath = resolve(repo, CONTRACT);
const contractBytes = await readFile(contractPath);
const first = await generate(contractPath);
const second = await generate(contractPath);
if (first !== second) {
  console.error('two generations in one run differ — generation is not deterministic');
  process.exit(1);
}
const entry = { generator, contract: CONTRACT, contractSha256: sha256(contractBytes), output: OUTPUT, outputSha256: sha256(Buffer.from(first, 'utf8')) };

if (mode === 'lock') {
  await writeFile(resolve(web, OUTPUT), first);
  await writeFile(LOCK, JSON.stringify(entry, null, 2) + '\n');
  console.log(`wrote ${OUTPUT} and ${relative(web, LOCK)} (${generator})`);
  process.exit(0);
}

const failures = [];
let locked;
try {
  locked = JSON.parse(await readFile(LOCK, 'utf8'));
} catch {
  console.error('contracts-client.lock.json is missing — run ./gradlew :web:clientLock');
  process.exit(1);
}
let committed = '';
try {
  committed = await readFile(resolve(web, OUTPUT), 'utf8');
} catch {
  failures.push(`${OUTPUT} is missing — run ./gradlew :web:clientLock`);
}
if (locked.generator !== generator) failures.push(`generator changed: locked ${locked.generator}, installed ${generator}`);
if (locked.contract !== CONTRACT) failures.push(`contract path changed: locked ${locked.contract}`);
if (locked.contractSha256 !== entry.contractSha256) {
  failures.push(`contract changed (${CONTRACT}) but the lock file was not updated — run ./gradlew :web:clientLock in the commit that changes the contract`);
}
if (locked.outputSha256 !== sha256(Buffer.from(committed, 'utf8'))) failures.push(`${OUTPUT} does not match the lock file (edited by hand?)`);
if (committed !== first) failures.push(`${OUTPUT} differs from a fresh generation`);
if (failures.length > 0) {
  for (const f of failures) console.error(f);
  process.exit(1);
}
console.log(`client lock ok (${generator}, contract ${entry.contractSha256.slice(0, 12)})`);
