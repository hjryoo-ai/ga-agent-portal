// 라인 접기(Phase 3 수용 심사 Q6 — 계획 Q12): 같은 달 안의 취소 짝만 접는다. 짝(reversalOf/reversedBy)이 그 달 목록에 없으면 다른 달의 짝이다 —
// 접지 않고 배지 + 상대 달 링크. 상대 달은 라인 응답의 reversalOfYm·reversedByYm(1.1.0 CR-2 — 추가 호출 없음)이고, 없으면 링크 없는 배지.
// 순서는 API 정렬(source_seq, calc_id) 그대로, 취소 라인 자리에 묶음을 둔다.
import type { Line } from '../api/queries';

export type Row =
  | { kind: 'line'; line: Line; crossMonth?: { role: 'original' | 'reversal'; partnerYm: string | undefined } }
  | { kind: 'pair'; reversal: Line; original: Line };

export function foldLines(lines: readonly Line[]): Row[] {
  const byId = new Map(lines.map((l) => [l.calcId, l]));
  const folded = new Set<string>();
  for (const l of lines) {
    if (l.reversalOf !== undefined && byId.has(l.reversalOf)) folded.add(l.reversalOf);
  }
  const rows: Row[] = [];
  for (const l of lines) {
    if (folded.has(l.calcId)) continue;
    const original = l.reversalOf === undefined ? undefined : byId.get(l.reversalOf);
    if (original !== undefined) {
      rows.push({ kind: 'pair', reversal: l, original });
    } else if (l.reversalOf !== undefined) {
      rows.push({ kind: 'line', line: l, crossMonth: { role: 'reversal', partnerYm: l.reversalOfYm } });
    } else if (l.reversedBy !== undefined && !byId.has(l.reversedBy)) {
      rows.push({ kind: 'line', line: l, crossMonth: { role: 'original', partnerYm: l.reversedByYm } });
    } else {
      rows.push({ kind: 'line', line: l });
    }
  }
  return rows;
}
