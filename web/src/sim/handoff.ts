// 한도 상세 → 해약 영향 인계(포털 Phase 5 계획 Q6·P-13): 메모리 한 칸. 한 번 읽으면 비운다. 라우터 state·주소·저장소는 쓰지 않는다 —
// 브라우저가 history.state를 남겨 새로고침 뒤에도 계약 키가 살아나고, 주소에는 policyKey를 두지 않는다(routes.ts).
let pending: string | null = null;

/** 해약 영향을 볼 계약(그 원장의 policyKey). */
export function handOffPolicy(policyKey: string): void {
  pending = policyKey;
}

/** 인계된 계약 — 읽으면 비운다(두 번째 진입은 빈 폼). */
export function takePolicy(): string | null {
  const p = pending;
  pending = null;
  return p;
}
