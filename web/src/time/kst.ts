// 날짜(포털 Phase 4 계획 ⑥ — 금액이 아니다): "이번 달"·D-n·월 범위·시각 표시. 시간대는 늘 Asia/Seoul로 명시한다(브라우저 시간대와 무관 — E2E는 UTC로 돈다).
// 기준 시각은 호출자가 넘긴다(시험은 고정 시각, 화면은 Date.now()).
const ZONE = 'Asia/Seoul';
const PARTS = new Intl.DateTimeFormat('en-CA', {
  timeZone: ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

interface Parts {
  year: string;
  month: string;
  day: string;
  hour: string;
  minute: string;
}

function parts(epochMs: number): Parts {
  const p: Record<string, string> = {};
  for (const x of PARTS.formatToParts(epochMs)) {
    p[x.type] = x.value;
  }
  return { year: p['year'] ?? '', month: p['month'] ?? '', day: p['day'] ?? '', hour: p['hour'] ?? '', minute: p['minute'] ?? '' };
}

/** KST 달력의 그 시각이 속한 월(YYYYMM). */
export function ymAt(epochMs: number): string {
  const p = parts(epochMs);
  return p.year + p.month;
}

/** 월 이동(YYYYMM ± n개월). */
export function ymShift(ym: string, months: number): string {
  const index = Number.parseInt(ym.slice(0, 4), 10) * 12 + Number.parseInt(ym.slice(4, 6), 10) - 1 + months;
  const year = Math.floor(index / 12);
  const month = (index % 12) + 1;
  return `${String(year)}${String(month).padStart(2, '0')}`;
}

/** 월의 연·월 숫자(문구 채우기용). */
export function ymParts(ym: string): { y: string; m: string } {
  return { y: ym.slice(0, 4), m: String(Number.parseInt(ym.slice(4, 6), 10)) };
}

/** 시각(ISO 8601) → KST "MM-DD HH:mm". */
export function formatAt(instant: string): string {
  const p = parts(Date.parse(instant));
  return `${p.month}-${p.day} ${p.hour}:${p.minute}`;
}

/** 시각(ISO 8601) → KST 날짜 "YYYY-MM-DD". */
export function dateAt(instant: string): string {
  const p = parts(Date.parse(instant));
  return `${p.year}-${p.month}-${p.day}`;
}

/** KST 오늘에서 그 날(YYYY-MM-DD)까지 남은 날 수(지난 날은 음수). */
export function daysUntil(date: string, epochMs: number): number {
  const today = parts(epochMs);
  const from = Date.UTC(Number(today.year), Number(today.month) - 1, Number(today.day));
  const to = Date.UTC(Number(date.slice(0, 4)), Number(date.slice(5, 7)) - 1, Number(date.slice(8, 10)));
  return Math.round((to - from) / 86_400_000);
}
