// 날짜(포털 Phase 4 계획 Q16): 브라우저 시간대와 무관하게 KST. "이번 달"은 KST 달력 월.
import { describe, expect, it } from 'vitest';
import { dateAt, daysUntil, formatAt, ymAt, ymParts, ymShift } from '../time/kst';

describe('kst', () => {
  it('this month is the KST calendar month', () => {
    expect(ymAt(Date.parse('2027-02-28T15:30:00Z'))).toBe('202703'); // KST 03-01 00:30
    expect(ymAt(Date.parse('2027-02-28T14:59:00Z'))).toBe('202702'); // KST 02-28 23:59
  });

  it('shifts months across years', () => {
    expect(ymShift('202702', -35)).toBe('202403'); // 202403..202702 = 36개월(명세 월 목록의 폭)
    expect(ymShift('202612', 1)).toBe('202701');
    expect(ymShift('202701', -1)).toBe('202612');
  });

  it('formats instants and dates in KST', () => {
    expect(formatAt('2026-09-22T01:15:41Z')).toBe('09-22 10:15');
    expect(formatAt('2026-09-10T10:00:00+09:00')).toBe('09-10 10:00');
    expect(dateAt('2026-09-09T16:00:00Z')).toBe('2026-09-10');
    expect(ymParts('202608')).toEqual({ y: '2026', m: '8' });
  });

  it('counts days to a date from the KST day', () => {
    const now = Date.parse('2026-09-30T15:30:00Z'); // KST 10-01 00:30
    expect(daysUntil('2026-10-10', now)).toBe(9);
    expect(daysUntil('2026-10-01', now)).toBe(0);
    expect(daysUntil('2026-09-30', now)).toBe(-1);
  });
});
