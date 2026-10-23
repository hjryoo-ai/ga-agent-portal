// 엔진 시뮬레이션 샘플 사본(contracts/shared/ga-commission-engine/samples/simulations — 스텁이 내는 바이트 그대로)에서 기대값을 읽는다.
// 화면 기대 문자열은 샘플 값 → 화면 형식(ko-KR 천 단위 + "원")이고, 손으로 숫자를 다시 적지 않는다(포털 Phase 5 ⑨).
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const dir = resolve(import.meta.dirname, '../../../contracts/shared/ga-commission-engine/samples/simulations');

export interface NewContractSample {
  limitAmount?: number;
  projectedAccum?: number;
  fyCommissionByInstallment: { no: number; amount: number }[];
  incentives: { code: string; amount: number }[];
  deferral: { firstYearPayout: number; schedule: { dueYm: string; amount: number }[] };
  ruleSnapshotId: string;
  generatedAt: string;
}

export interface PolicyEventSample {
  clawbackAmount: number;
  offsetAgainstMonth: { closeYm: string; before: number; after: number };
  receivableRemaining: number;
  cancelledDeferrals: { dueYm: string; amount: number }[];
  ruleSnapshotId: string;
}

const read = (name: string): unknown => JSON.parse(readFileSync(resolve(dir, `${name}.response.json`), 'utf8'));

export const newContractSample = (name: string) => read(name) as NewContractSample;
export const policyEventSample = (name: string) => read(name) as PolicyEventSample;

const FORMAT = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 });

/** 화면의 금액 글자(Amount의 값 부분). */
export const wonText = (n: number) => `${FORMAT.format(n)}원`;
