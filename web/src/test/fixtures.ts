// 화면 시험 픽스처: 계약 모양의 응답(허구 값). 금액은 정수를 Won으로 바꿔 싣는다(런타임 값은 JSON 정수 그대로).
import type { Schemas } from '../api/queries';
import type { Won } from '../money/won';

export const won = (n: number) => n as unknown as Won;
const asOf = { seq: 11, at: '2027-02-20T00:30:00Z' };

export function month(over: Partial<Schemas['MonthResponse']> = {}): Schemas['MonthResponse'] {
  return {
    asOf,
    dataStatus: 'OK',
    closeYm: '202608',
    status: 'PROVISIONAL',
    monthDataStatus: 'OK',
    gross: won(1800000),
    clawbackOffset: won(0),
    adjustment: won(0),
    byType: { FY_COMM: won(945000), INCENTIVE: won(855000) },
    ...over,
  };
}

export function line(calcId: string, over: Partial<Schemas['Line']> = {}): Schemas['Line'] {
  return {
    calcId,
    closeYm: '202608',
    commType: 'FY_COMM',
    amount: won(945000),
    limitCutAmt: won(0),
    policyMasked: '*********0001',
    policyKey: '0123456789abcdef',
    ...over,
  };
}

export function ledger(over: Partial<Schemas['Ledger']> = {}): Schemas['Ledger'] {
  return {
    ledgerId: '1',
    policyMasked: '*********0001',
    policyKey: '0123456789abcdef',
    contractDate: '2026-08-01',
    fyStart: '2026-08-01',
    fyEnd: '2027-07-31',
    monthlyPremium: won(150000),
    limitMultiple: '12',
    limitAmount: won(1800000),
    accumPaid: won(1080000),
    remaining: won(720000),
    utilization: '60.0%',
    stage: 'OK',
    cutTotal: won(0),
    deferredTotal: won(145000),
    flags: ['CANCELLED'],
    ...over,
  };
}

export { asOf };
