// 상태 라벨 표(포털 Phase 4 계획 ⑤ — Phase 3 수용 심사 Q1·Q2): 문구는 messages.ko.json의 데이터, 여기서는 "어느 상태에 어느 문구"만 정한다.
// 금액 컴포넌트(Amount)는 이 파일이 만든 StatusLabel 없이는 그릴 수 없다(C3) — 라벨을 만드는 함수는 이 파일의 export 함수뿐이다.
import type { MonthStatus, NetBasis, Schemas } from '../api/queries';
import { fill, m } from '../messages';

declare const statusLabelBrand: unique symbol;

/** 금액 옆에 붙는 상태 라벨. 이 모듈의 함수로만 만든다. */
export type StatusLabel = string & { readonly [statusLabelBrand]: true };

const label = (text: string): StatusLabel => text as StatusLabel;

/** 월 금액(세전·환수 상계)과 그 달 라인의 라벨 — 월 상태. 정정은 adjustmentLabel(원천세는 netView의 라벨). */
export function monthLabel(status: MonthStatus): StatusLabel {
  return label(m.status.month[status]);
}

/** 정정 금액(월 정정 합·byType.ADJUSTMENT·ADJUSTMENT 라인) — 월 상태와 무관하다. 엔진 E1-2: 정정은 마감·지급 런이 읽지 않는다
 *  (StatementConfirmed.adjustment에는 있고 PayoutCompleted에는 없다) — PAID 달의 정정도 지급된 것이 아니다(Phase 4 수용 심사 A1).
 *  엔진이 정정을 지급에 넣는 날 문구만 바뀐다. */
export function adjustmentLabel(): StatusLabel {
  return label(m.status.adjustment);
}

/** 라인 금액의 라벨: 정정 라인은 adjustmentLabel, 나머지는 그 달의 상태. commType은 열린 값이다. */
export function lineLabel(commType: string, status: MonthStatus): StatusLabel {
  return commType === 'ADJUSTMENT' ? adjustmentLabel() : monthLabel(status);
}

/** 원장 금액(한도액·누적·잔여·삭감) — 사실 값. */
export function ledgerLabel(): StatusLabel {
  return label(m.status.ledger);
}

/** 계약 사실(월납 보험료) — 엔진이 계약 이벤트로 준 값. */
export function contractLabel(): StatusLabel {
  return label(m.status.contract);
}

/** 분급 항목 금액 — 그 항목의 상태(예정·이번 달 지급 대상·보류·취소). */
export function deferralLabel(status: Schemas['LineDetailResponse']['deferrals'][number]['status']): StatusLabel {
  return label(m.enums.deferral[status]);
}

/** 환수 채권 금액 — 채권 상태(미상계·상계 중·종결·대손). */
export function receivableLabel(status: NonNullable<Schemas['LineDetailResponse']['receivable']>['status']): StatusLabel {
  return label(m.enums.receivable[status]);
}

/** 분급 예정(타임라인 월 합계 — 모두 유지 시나리오·가정 전 값, 홈 다음 달 분급). 분급 항목 상태 라벨("예정")과 다르다 — 월 합계는 예정·이번 달
 *  지급 대상의 합이다(포털 Phase 5 계획 ⑧ 라벨 표). */
export function forecastLabel(): StatusLabel {
  return label(m.status.forecast);
}

/** 유지율 가정을 적용한 월 합계(RETENTION의 months[].expected)만 — p는 응답의 값 그대로(Q23). 원값(가정 전·보류·항목)에는 붙이지 않는다. */
export function retentionLabel(p: number): StatusLabel {
  return label(fill(m.status.retention, { p: String(p) }));
}

/** 시뮬레이션 결과의 모든 금액 — 엔진 계산값이지만 확정·지급 사실이 아니다. 월 상태·정정·원장·분급 라벨 어느 것과도 다르다(C10). */
export function simulationLabel(): StatusLabel {
  return label(m.status.simulation);
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
