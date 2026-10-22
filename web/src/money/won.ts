// 금액(포털 Phase 4 계획 ④, C2·C3). 계약의 Won·NonNegativeWon은 이 불투명 타입으로 생성된다(scripts/client-lock.mjs).
// 숫자가 아니므로 화면 코드의 산술(+ - * /)과 JSX 직접 렌더는 컴파일 오류다. 런타임 값은 JSON 정수 그대로다(변환 비용 없음).
// 이 파일이 숫자로 되돌리는 유일한 곳이다(ESLint portal/no-money-arithmetic의 닫힌 예외) — 하는 일은 정수 형식화, 0 판정, 선택 필드의 유무뿐이다.
declare const wonBrand: unique symbol;

/** 정수 원(엔진·API 값). 계산·비교·변환하지 않는다 — 형식화는 Amount 컴포넌트만. */
export interface Won {
  readonly [wonBrand]: true;
}

const FORMAT = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 });

/** 천 단위 구분 정수 문자열(부호는 Intl이 붙인다). Amount 컴포넌트 밖에서 부르지 않는다. */
export function formatWon(value: Won): string {
  return FORMAT.format(value as unknown as number);
}

/** 값이 0인가 — 0이 아닌 줄만 보일 때(설계서 §6.3 삭감·이연, 정정만 있는 달). 크기 비교는 없다. */
export function isZero(value: Won): boolean {
  return (value as unknown as number) === 0;
}

/** 선택 금액 필드가 응답에 있는가(API는 값이 없으면 필드를 뺀다 — null 없음). 0도 "있음"이다(참거짓 판정을 쓰면 0이 빠진다). */
export function present(value: Won | undefined): value is Won {
  return value !== undefined;
}
