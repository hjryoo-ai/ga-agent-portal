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

/**
 * 길이는 유니코드 코드 포인트(JSON Schema minLength·maxLength와 서버 char_length와 같은 단위 — UTF-16 length는 이모지에서 갈린다, Phase 6 계획 Q27).
 * 서로게이트 짝을 하나로 센다. 자소 묶음(그래핌)이 아니라 코드 포인트를 세는 것이 의도다 — 계약이 그 단위다.
 */
export function codePoints(value: string): number {
  let n = 0;
  for (let i = 0; i < value.length; i++) {
    const unit = value.charCodeAt(i);
    // 높은 서로게이트 뒤에 낮은 서로게이트가 오면 둘이 한 코드 포인트
    if (unit >= 0xd800 && unit <= 0xdbff && i + 1 < value.length) {
      const next = value.charCodeAt(i + 1);
      if (next >= 0xdc00 && next <= 0xdfff) i++;
    }
    n++;
  }
  return n;
}

function fits(c: Constraint, value: string): boolean {
  if (c.minLength !== undefined && codePoints(value) < c.minLength) return false;
  if (c.maxLength !== undefined && codePoints(value) > c.maxLength) return false;
  if (c.enum !== undefined && !c.enum.includes(value)) return false;
  return c.pattern === undefined || new RegExp(c.pattern, 'u').test(value);
}

/** 그 요청의 필드(빈 문자열 = 입력 없음)를 검사한다 → 문제 있는 필드만. 조건부 필수는 규칙 파일의 when. */
export function check(name: RequestName, values: Readonly<Record<string, string>>): Record<string, FieldProblem> {
  const request = rules.requests[name];
  const required = new Set<string>(request.required);
  for (const w of request.when) {
    if (w.in.includes(values[w.field] ?? '')) for (const f of w.required) required.add(f);
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

/** 그 필드의 최대 길이(코드 포인트 — 계약 maxLength). 문의 작성의 남은 글자 수(포털 Phase 6). */
export function maxLength(name: RequestName, field: string): number {
  const fields: Record<string, Constraint> = rules.requests[name].fields;
  const max = fields[field]?.maxLength;
  if (max === undefined) throw new Error(`no maxLength for ${name}.${field}`);
  return max;
}

/** 타임라인 유지율 p(쿼리) — 계약 pattern. */
export function validRetention(p: string): boolean {
  return fits(rules.queries['listDeferrals.p'], p);
}

/** 계약이 허용하는 변경 유형(열거) — 화면은 이 중 PAYMENT_OVERDUE를 내지 않는다(엔진이 시뮬레이션하지 않는다 — 지시문 ⑬). */
export const CONTRACT_EVENT_TYPES: readonly string[] = rules.requests.SimPolicyEventRequest.fields.eventType.enum;
