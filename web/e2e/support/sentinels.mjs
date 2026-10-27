// 누출 센티널(포털 Phase 4 C6): E2E 스트림 전부의 증권번호 원문과, 같은 테넌트 이웃 설계사의 ID·계산·원장 키(허구). 화면 어디에도 원문이 나오면 안 된다.
// 자기 설계사(A-1001)의 calcId·ledgerId는 화면 경로에 쓰이므로 센티널이 아니다.
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repo = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const STREAMS = [
  'contracts/events/v1/samples/a1/a1_portal_150000_e2.jsonl',
  'web/e2e/streams/ga01-neighbor.jsonl',
  'web/e2e/streams/edges.jsonl',
  'web/e2e/streams/ga05-limits.jsonl',
  'portal-projection/src/test/resources/golden-streams/engine-e2-estimate/estimate_cycle_01_기본.jsonl',
  'portal-projection/src/test/resources/golden-streams/engine-e1-cycle.jsonl',
];

export function sentinels() {
  const values = new Set();
  for (const file of STREAMS) {
    for (const line of readFileSync(resolve(repo, file), 'utf8').split('\n')) {
      if (line.trim() === '') continue;
      const p = JSON.parse(line).payload;
      if (typeof p.policyNo === 'string') values.add(p.policyNo);
      if (p.agentId !== undefined && p.agentId !== 'A-1001') {
        values.add(p.agentId);
        for (const k of ['calcId', 'ledgerId']) if (typeof p[k] === 'string') values.add(p[k]);
      }
    }
  }
  return [...values].sort();
}

// 문의 본문 센티널(포털 Phase 6 계획 ⑨ E7 — 허구, 이름 꼴·전화번호 꼴·증권번호 꼴): 원본은 pii-sentinels.properties의 inquiry 줄(Gradle 평문 스캔과 한 목록).
// 본문이 보여도 되는 곳은 작성 화면·상세 화면의 DOM과 그 상세 응답뿐 — 고정 장치(fixtures.ts)와 산출물 스캔(run.mjs)이 나머지 전부에서 0을 본다.
export function inquirySentinels() {
  const props = readFileSync(resolve(repo, 'portal-infra/src/testFixtures/resources/pii-sentinels.properties'), 'utf8');
  const line = props.split('\n').find((l) => l.startsWith('inquiry='));
  const values = (line ?? '').slice('inquiry='.length).split(',').map((v) => v.trim()).filter((v) => v !== '');
  if (values.length !== 3) throw new Error('pii-sentinels.properties: the inquiry line must have three sentinels');
  return values;
}
