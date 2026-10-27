// 계약 → 화면 클라이언트 타입(포털 Phase 4 계획 ③·④-a, C1). 생성물 src/gen/portal-api.ts와 잠금 파일 contracts-client.lock.json을 **둘 다 커밋**한다.
//   check: 다시 생성해 대조한다 — ① 같은 실행에서 두 번 생성한 바이트가 다르면 실패(비결정) ② 계약 SHA-256이 잠금과 다르면 실패(계약이 바뀌었는데 잠금 그대로)
//          ③ 생성물이 커밋본과 바이트가 다르면 실패(손 편집·생성기 변화) ④ 잠금의 생성물 SHA-256이 커밋본과 다르면 실패 ⑤ 생성기 판이 다르면 실패.
//   lock:  생성물과 잠금 파일을 다시 쓴다 — 계약을 바꾼 커밋에서만(./gradlew :web:clientLock).
// 입력 규칙(포털 Phase 5 계획 Q12): 시뮬레이션 요청 본문·타임라인 p의 pattern·길이·필수·enum을 계약에서 뽑아 src/gen/portal-api.rules.json으로 쓴다 —
// 화면은 그 파일로 미리 막고, 규칙을 손으로 두 벌 적지 않는다. 같은 생성 한 번(transform)에서 모으고, 잠금이 그 바이트도 고정한다.
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
const RULES = 'src/gen/portal-api.rules.json';
const REQUEST_SCHEMAS = ['SimNewContractRequest', 'SimPolicyEventRequest', 'InquiryRequest'];
const QUERY_RULES = { 'listDeferrals.p': '#/paths/~1api~1v1~1me~1deferrals/get/parameters/query/p' };
const CONSTRAINTS = ['pattern', 'minLength', 'maxLength', 'enum'];
const MONEY_SCHEMAS = new Set(['#/components/schemas/Won', '#/components/schemas/NonNegativeWon']);

const HEADER = [
  '// 생성물 — 손으로 고치지 않는다. 계약 contracts/api/v1/portal-api.openapi.yaml → scripts/client-lock.mjs(./gradlew :web:clientLock).',
  '// 금액 스키마(Won·NonNegativeWon)는 불투명 타입 Won이다(src/money/won.ts).',
  "import type { Won } from '../money/won';",
  '',
  '',
].join('\n');

const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex');

/** 스키마에서 입력 제약만(키 순서 고정). */
function constraints(schema) {
  const out = {};
  for (const k of CONSTRAINTS) {
    if (schema[k] !== undefined) out[k] = schema[k];
  }
  return out;
}

/** transform이 본 스키마(경로 → 스키마)에서 규칙 파일을 만든다. 참조는 components/schemas 하나만 따라간다(계약이 그렇게 쓴다). */
function rules(seenSchemas) {
  const component = (ref) => {
    const s = seenSchemas.get(ref);
    if (s === undefined) throw new Error(`rule source not seen: ${ref}`);
    return s;
  };
  const requests = {};
  for (const name of REQUEST_SCHEMAS) {
    const schema = component(`#/components/schemas/${name}`);
    const fields = {};
    for (const field of Object.keys(schema.properties).sort()) {
      const prop = schema.properties[field];
      const c = constraints(prop.$ref === undefined ? prop : component(prop.$ref));
      if (Object.keys(c).length === 0) throw new Error(`${name}.${field} has no input constraint`);
      fields[field] = c;
    }
    // if/then(그 필드가 그 값들 중 하나면 더 필수) — 계약이 쓰는 모양 둘만 읽는다: if.properties.<필드>.const(Phase 5) 또는 .enum(Phase 6 문의 분류), then.required.
    // 규칙 파일에는 한 모양 {field, in, required}로 쓴다.
    const when = [];
    if (schema.if !== undefined) {
      const [[field, cond] = []] = Object.entries(schema.if.properties ?? {});
      const values = cond?.const !== undefined ? [cond.const] : cond?.enum;
      if (field === undefined || values === undefined || schema.then?.required === undefined) throw new Error(`${name}: unsupported if/then`);
      when.push({ field, in: [...values].sort(), required: [...schema.then.required].sort() });
    }
    requests[name] = { required: [...schema.required].sort(), when, fields };
  }
  const queries = {};
  for (const [name, path] of Object.entries(QUERY_RULES)) {
    queries[name] = constraints(component(path));
  }
  return JSON.stringify({ contract: CONTRACT, requests, queries }, null, 2) + '\n';
}

async function generate(contractPath) {
  const seen = new Set();
  const seenSchemas = new Map();
  const ast = await openapiTS(pathToFileURL(contractPath), {
    alphabetize: true,
    transform(schema, metadata) {
      if (!seenSchemas.has(metadata.path)) seenSchemas.set(metadata.path, schema);
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
  return { client: HEADER + astToString(ast), rules: rules(seenSchemas) };
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
if (first.client !== second.client || first.rules !== second.rules) {
  console.error('two generations in one run differ — generation is not deterministic');
  process.exit(1);
}
const entry = {
  generator,
  contract: CONTRACT,
  contractSha256: sha256(contractBytes),
  output: OUTPUT,
  outputSha256: sha256(Buffer.from(first.client, 'utf8')),
  rules: RULES,
  rulesSha256: sha256(Buffer.from(first.rules, 'utf8')),
};

if (mode === 'lock') {
  await writeFile(resolve(web, OUTPUT), first.client);
  await writeFile(resolve(web, RULES), first.rules);
  await writeFile(LOCK, JSON.stringify(entry, null, 2) + '\n');
  console.log(`wrote ${OUTPUT}, ${RULES} and ${relative(web, LOCK)} (${generator})`);
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
let committedRules = '';
try {
  committedRules = await readFile(resolve(web, RULES), 'utf8');
} catch {
  failures.push(`${RULES} is missing — run ./gradlew :web:clientLock`);
}
if (locked.outputSha256 !== sha256(Buffer.from(committed, 'utf8'))) failures.push(`${OUTPUT} does not match the lock file (edited by hand?)`);
if (committed !== first.client) failures.push(`${OUTPUT} differs from a fresh generation`);
if (locked.rules !== RULES) failures.push(`rules path changed: locked ${String(locked.rules)}`);
if (locked.rulesSha256 !== sha256(Buffer.from(committedRules, 'utf8'))) failures.push(`${RULES} does not match the lock file (edited by hand?)`);
if (committedRules !== first.rules) failures.push(`${RULES} differs from a fresh generation`);
if (failures.length > 0) {
  for (const f of failures) console.error(f);
  process.exit(1);
}
console.log(`client lock ok (${generator}, contract ${entry.contractSha256.slice(0, 12)})`);
