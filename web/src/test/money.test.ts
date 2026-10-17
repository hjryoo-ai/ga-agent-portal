// 금액 형식화(포털 Phase 4 계획 ④-a): 정수 천 단위 구분, 부호는 Intl, 0 판정만.
import { describe, expect, it } from 'vitest';
import { formatWon, isZero, present, type Won } from '../money/won';

const won = (n: number) => n as unknown as Won;

describe('won', () => {
  it('formats integers with thousands separators and a sign', () => {
    expect(formatWon(won(1740600))).toBe('1,740,600');
    expect(formatWon(won(-720000))).toBe('-720,000');
    expect(formatWon(won(0))).toBe('0');
  });

  it('isZero only answers zero', () => {
    expect(isZero(won(0))).toBe(true);
    expect(isZero(won(-1))).toBe(false);
    expect(isZero(won(145000))).toBe(false);
  });

  it('present treats 0 as present and only undefined as absent', () => {
    expect(present(won(0))).toBe(true);
    expect(present(won(-720000))).toBe(true);
    expect(present(undefined)).toBe(false);
  });
});
