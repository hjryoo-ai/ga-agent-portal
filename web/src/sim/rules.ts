// 입력 규칙(포털 Phase 5 계획 Q12): 계약에서 생성한 src/gen/portal-api.rules.json(client:lock이 쓰고 잠금이 해시를 고정)으로 보내기 전에 막는다.
// 규칙을 여기 손으로 다시 적지 않는다 — pattern·길이·필수·조건부 필수(if/then) 모두 그 파일의 데이터다. 값은 문자열 그대로 보낸다(숫자로 바꾸지 않는다).
import rules from '../gen/portal-api.rules.json';

export type RequestName = keyof typeof rules.requests;
export type FieldProblem = 'required' | 'invalid';

interface Constraint {
  pattern?: string;
  minLength?: number;
  maxLength?: number;
  enum?: string[];
}

function fits(c: Constraint, value: string): boolean {
  if (c.minLength !== undefined && value.length < c.minLength) return false;
  if (c.maxLength !== undefined && value.length > c.maxLength) return false;
  if (c.enum !== undefined && !c.enum.includes(value)) return false;
  return c.pattern === undefined || new RegExp(c.pattern, 'u').test(value);
}

/** 그 요청의 필드(빈 문자열 = 입력 없음)를 검사한다 → 문제 있는 필드만. 조건부 필수는 규칙 파일의 when. */
export function check(name: RequestName, values: Readonly<Record<string, string>>): Record<string, FieldProblem> {
  const request = rules.requests[name];
  const required = new Set<string>(request.required);
  for (const w of request.when) {
    if (values[w.field] === w.equals) for (const f of w.required) required.add(f);
  }
  const fields: Record<string, Constraint> = request.fields;
  const problems: Record<string, FieldProblem> = {};
  for (const [field, constraint] of Object.entries(fields)) {
    const value = values[field] ?? '';
    if (value === '') {
      if (required.has(field)) problems[field] = 'required';
    } else if (!fits(constraint, value)) {
      problems[field] = 'invalid';
    }
  }
  return problems;
}

/** 요청 본문: 입력이 있는 필드만(빈 선택 필드는 보내지 않는다 — 엔진 기본값). */
export function body(name: RequestName, values: Readonly<Record<string, string>>): Record<string, string> {
  const fields: Record<string, Constraint> = rules.requests[name].fields;
  return Object.fromEntries(Object.keys(fields).flatMap((f) => (values[f] === undefined || values[f] === '' ? [] : [[f, values[f]]])));
}

/** 타임라인 유지율 p(쿼리) — 계약 pattern. */
export function validRetention(p: string): boolean {
  return fits(rules.queries['listDeferrals.p'], p);
}

/** 계약이 허용하는 변경 유형(열거) — 화면은 이 중 PAYMENT_OVERDUE를 내지 않는다(엔진이 시뮬레이션하지 않는다 — 지시문 ⑬). */
export const CONTRACT_EVENT_TYPES: readonly string[] = rules.requests.SimPolicyEventRequest.fields.eventType.enum;
