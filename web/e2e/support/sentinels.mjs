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
