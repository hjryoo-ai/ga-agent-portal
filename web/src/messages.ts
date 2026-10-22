// 화면 문구는 전부 이 데이터에서 온다(Phase 3 수용 심사 Q1 — 상태 라벨은 데이터, 한국어 하나).
import messages from './messages.ko.json';

export type Messages = typeof messages;
export const m: Messages = messages;

/** 문구의 {이름} 자리를 채운다(값은 이미 형식화된 문자열). */
export function fill(template: string, values: Record<string, string>): string {
  return template.replace(/\{(\w+)\}/g, (whole, name: string) => values[name] ?? whole);
}
