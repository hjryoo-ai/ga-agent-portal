// 상태 라벨 표(포털 Phase 4 계획 ⑤ — Phase 3 수용 심사 Q1·Q2): 문구는 messages.ko.json의 데이터, 여기서는 "어느 상태에 어느 문구"만 정한다.
// 금액 컴포넌트(Amount)는 이 파일이 만든 StatusLabel 없이는 그릴 수 없다(C3) — 라벨을 만드는 함수는 아래 넷뿐이다.
import type { MonthStatus, NetBasis } from '../api/queries';
import { m } from '../messages';

declare const statusLabelBrand: unique symbol;

/** 금액 옆에 붙는 상태 라벨. 이 모듈의 함수로만 만든다. */
export type StatusLabel = string & { readonly [statusLabelBrand]: true };

const label = (text: string): StatusLabel => text as StatusLabel;

/** 월 금액(세전·환수 상계·정정·원천세)과 그 달 라인의 라벨 — 월 상태. */
export function monthLabel(status: MonthStatus): StatusLabel {
  return label(m.status.month[status]);
}

/** 원장 금액(한도액·누적·잔여·삭감) — 사실 값. */
export function ledgerLabel(): StatusLabel {
  return label(m.status.ledger);
}

/** 지급 런 확정값(지급액·채권 상계·이월 채권) — PAID 달에만 보인다. */
export function payoutLabel(): StatusLabel {
  return label(m.status.month.PAID);
}

/** 세후(실수령) 표시: 보이면 그 라벨, 아니면 숨기고 안내 문구(Phase 3 수용 심사 Q2, 설계서 §6.2). 계약 열거 4 × (3 + 없음) = 16칸. */
export type NetView = { kind: 'show'; label: StatusLabel } | { kind: 'hide'; note: string };

export function netView(status: MonthStatus, netBasis: NetBasis | undefined): NetView {
  switch (status) {
    case 'PROVISIONAL':
      return netBasis === undefined ? { kind: 'hide', note: m.status.net.pendingNightly } : unavailable();
    case 'ESTIMATED':
      return netBasis === 'ESTIMATE' ? { kind: 'show', label: label(m.status.month.ESTIMATED) } : unavailable();
    case 'CONFIRMED':
      switch (netBasis) {
        case undefined:
          return { kind: 'hide', note: m.status.net.pendingPayout };
        case 'ESTIMATE':
          return { kind: 'show', label: label(m.status.month.ESTIMATED) };
        case 'CONFIRM':
          return { kind: 'show', label: label(m.status.month.CONFIRMED) };
        case 'PAYOUT':
          return unavailable();
      }
      return unavailable();
    case 'PAID':
      return netBasis === 'PAYOUT' ? { kind: 'show', label: label(m.status.month.PAID) } : unavailable();
  }
}

/** 계약상 오지 않는 조합 — 세후를 보이지 않는 쪽으로 실패한다. */
function unavailable(): NetView {
  return { kind: 'hide', note: m.status.net.unavailable };
}
