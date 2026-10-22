// D-n 문구(지급예정일·초년도 종료) — 날짜 차이(금액 아님).
import { fill, m } from '../messages';
import { daysUntil } from './kst';

export function dDay(date: string, nowMs: number): string {
  const n = daysUntil(date, nowMs);
  if (n === 0) return m.fill.dDayToday;
  return fill(n > 0 ? m.fill.dDay : m.fill.dDayPast, { n: String(Math.abs(n)) });
}
