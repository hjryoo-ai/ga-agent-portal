// 알림 문장(포털 Phase 6 계획 Q1): 서버에는 문장이 없다 — type별 템플릿(messages.ko.json notifications.<TYPE>)에 params를 채운다. 금액 자리는 없다.
// 월(closeYm·dueYm·ym)은 기존 월 형식, 날짜(occurredOn)는 그대로(YYYY-MM-DD), 가린 증권번호는 그대로. ID(calcId·ledgerId·inquiryId)는 문장에 쓰지 않는다.
import type { Notification } from '../api/queries';
import { fill, m } from '../messages';
import { ymParts } from '../time/kst';

const MONTH_KEYS = new Set(['closeYm', 'dueYm', 'ym']);

export function notificationText(n: Notification): string {
  const values: Record<string, string> = {};
  for (const [key, value] of Object.entries(n.params)) {
    values[key] = MONTH_KEYS.has(key) ? fill(m.fill.ym, ymParts(value)) : value;
  }
  return fill(m.notifications[n.type], values);
}
