// 계약 선택지(포털 Phase 5 — 타임라인 계약 필터·해약 영향의 계약): 한도 카드(원장)의 계약, 같은 키는 한 번. 이름은 상품명(없으면 상품 키 — CR-4)과
// 가린 증권번호. 값은 policyKey(주소에 넣지 않는다 — 쿼리·요청 본문만).
import type { Ledger } from '../api/queries';

export function policyOptions(ledgers: readonly Ledger[]): { key: string; name: string }[] {
  const seen = new Set<string>();
  return ledgers.flatMap((l) => {
    if (seen.has(l.policyKey)) return [];
    seen.add(l.policyKey);
    const product = l.productName ?? l.productKey;
    return [{ key: l.policyKey, name: product === undefined ? l.policyMasked : `${product} · ${l.policyMasked}` }];
  });
}
