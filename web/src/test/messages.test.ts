// @vitest-environment node
// 문구 ↔ 계약 열거 양방향(포털 Phase 4 계획 ⑤): 계약(생성 클라이언트)의 모든 열거 값에 문구가 있고, 문구 쪽에만 있는 키(폐기)도 실패.
// commType은 계약에서 열린 값이라 알려진 목록을 이벤트 계약 설명문(CommissionCalculated.commType)에서 읽는다.
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import messages from '../messages.ko.json';
import { EVENT_TYPES } from '../sim/eventTypes';
import { CONTRACT_EVENT_TYPES } from '../sim/rules';

const web = resolve(import.meta.dirname, '../..');
const generated = readFileSync(resolve(web, 'src/gen/portal-api.ts'), 'utf8');

/** 생성물에서 `이름: "A" | "B"`·`이름?: …`·`이름: ("A" | "B")[]`의 값들. 같은 이름이 여러 번이면 합친다. */
function unionOf(name: string): string[] {
  const values = new Set<string>();
  for (const match of generated.matchAll(new RegExp(`\\b${name}\\??: \\(?((?:"[A-Z_]+"(?: \\| )?)+)`, 'g'))) {
    for (const v of (match[1] ?? '').matchAll(/"([A-Z_]+)"/g)) values.add(v[1] ?? '');
  }
  return [...values].sort();
}

/** 생성물에서 어떤 스키마 블록(`이름: {` … 짝 닫는 괄호) 안의 `status: "…"` 값들. */
function statusInside(block: string): string[] {
  const start = generated.search(new RegExp(`\\b${block}\\??: \\{`));
  expect(start, block).toBeGreaterThan(-1);
  let depth = 0;
  let end = start;
  for (let i = generated.indexOf('{', start); i < generated.length; i++) {
    if (generated[i] === '{') depth++;
    if (generated[i] === '}') depth--;
    if (depth === 0) {
      end = i;
      break;
    }
  }
  const text = generated.slice(start, end);
  return [...new Set([...text.matchAll(/status: ((?:"[A-Z_]+"(?: \| )?)+)/g)].flatMap((x) => [...(x[1] ?? '').matchAll(/"([A-Z_]+)"/g)].map((v) => v[1] ?? '')))].sort();
}

const keys = (o: object) => Object.keys(o).sort();

describe('messages ↔ contract enums', () => {
  it.each([
    ['MonthStatus', messages.status.month],
    ['MonthDataStatus', messages.status.monthData],
    ['stage', messages.enums.stage],
    ['flags', messages.enums.flag],
    ['statusReason', messages.enums.statusReason],
    ['reason', messages.enums.deferralReason],
    ['kind', messages.enums.historyKind],
  ] as const)('%s', (name, labels) => {
    const values = unionOf(name);
    expect(values.length, `${name} values in the generated client`).toBeGreaterThan(1);
    expect(keys(labels)).toEqual(values);
  });

  it('deferral and receivable statuses', () => {
    expect(keys(messages.enums.deferral)).toEqual(statusInside('DeferralsResponse'));
    expect(keys(messages.enums.receivable)).toEqual(statusInside('receivable'));
  });

  it('commType names equal the event contract list', () => {
    const schema = JSON.parse(readFileSync(resolve(web, '../contracts/events/v1/payloads/CommissionCalculated.v1.schema.json'), 'utf8')) as {
      properties: { commType: { description: string } };
    };
    // "열린 값(FY_COMM/RENEWAL/…/ADJUSTMENT/...)" — 괄호 안의 이름들
    const inParens = /\(([A-Z_/]+)\/\.\.\.\)/.exec(schema.properties.commType.description)?.[1] ?? '';
    const list = inParens.split('/').filter((v) => v !== '').sort();
    expect(list).toContain('FY_COMM');
    expect(keys(messages.enums.commType)).toEqual(list);
  });

  it('every message is a non-empty string except the OK month-data badge', () => {
    const walk = (o: object, path: string): string[] =>
      Object.entries(o).flatMap(([k, v]) => (typeof v === 'string' ? (v === '' ? [`${path}.${k}`] : []) : walk(v as object, `${path}.${k}`)));
    expect(walk(messages, 'm')).toEqual(['m.status.monthData.OK']);
  });

  // Phase 5: 화면의 변경 유형 = 계약 열거 − PAYMENT_OVERDUE(엔진이 시뮬레이션하지 않는다 — 지시문 ⑬), 문구도 그 다섯.
  it('event types: the contract enum without PAYMENT_OVERDUE, each with a message', () => {
    expect([...CONTRACT_EVENT_TYPES].sort()).toEqual(unionOf('eventType'));
    expect([...EVENT_TYPES].sort()).toEqual(CONTRACT_EVENT_TYPES.filter((t) => t !== 'PAYMENT_OVERDUE').sort());
    expect(keys(messages.enums.eventType)).toEqual([...EVENT_TYPES].sort());
  });
});

describe('Phase 5 wording', () => {
  const all = (o: object): string[] => Object.values(o).flatMap((v) => (typeof v === 'string' ? [v] : all(v as object)));

  // 보류는 "다시 받을 수 있는" 돈처럼 말하지 않는다(계획 ⑧ — 지급 조건 미충족으로 멈춘 분급)
  it('no message promises that held deferrals come back', () => {
    for (const word of ['다시 받', '돌려받', '회복', '추후 지급', '나중에 지급', '받을 수 있']) {
      expect(all(messages).filter((t) => t.includes(word)), word).toEqual([]);
    }
  });

  // 해약 영향의 엔진 값은 그 달의 세전·환수 상계 합계다 — "실수령"이 아니다(Q24). 시뮬레이션 문구 어디에도 없다.
  it('simulation wording never says 실수령, and the fixed notes are as planned', () => {
    expect(all(messages.simulation).filter((t) => t.includes('실수령'))).toEqual([]);
    expect(messages.simulation.note).toBe('설계사 참고용 · 비확정 · 고객 제시 불가');
    expect(messages.timeline.note).toBe('분급 확정 스케줄만 반영 · 참고치 · 비확정');
  });
});

// 포털 Phase 6(계획 ⑧·C10): 알림 문장은 화면 템플릿 — 종류 열넷 ↔ 템플릿 키 양방향, 템플릿의 자리 ⊆ 그 변이의 params 키(금액 자리 없음), 문의 분류·상태 라벨 ↔ 계약 열거.
describe('Phase 6 notification templates and inquiry labels', () => {
  /** 생성물의 알림 변이 블록마다 (type 값들, params 키들). */
  function variants(): { types: string[]; params: string[] }[] {
    const out: { types: string[]; params: string[] }[] = [];
    for (const match of generated.matchAll(/\bNotification(Ledger|Line|Deferral|Policy|Month|Inquiry): \{/g)) {
      let depth = 0;
      let end = match.index;
      for (let i = generated.indexOf('{', match.index); i < generated.length; i++) {
        if (generated[i] === '{') depth++;
        if (generated[i] === '}') depth--;
        if (depth === 0) {
          end = i;
          break;
        }
      }
      const block = generated.slice(match.index, end);
      const types = [...(/type: ((?:"[A-Z_]+"(?: \| )?)+);/.exec(block)?.[1] ?? '').matchAll(/"([A-Z_]+)"/g)].map((v) => v[1] ?? '');
      const paramsBlock = /params: \{([^}]*)\}/.exec(block)?.[1] ?? '';
      const params = [...paramsBlock.matchAll(/^\s*(\w+)\??:/gm)].map((v) => v[1] ?? '');
      out.push({ types, params });
    }
    return out;
  }

  it('one template per notification type, both ways', () => {
    const v = variants();
    expect(v).toHaveLength(6);
    const types = v.flatMap((x) => x.types).sort();
    expect(types).toHaveLength(14);
    expect(keys(messages.notifications)).toEqual(types);
  });

  it("each template's placeholders are keys of its variant's params, and no template talks money", () => {
    for (const { types, params } of variants()) {
      expect(params.length, types.join()).toBeGreaterThan(0);
      for (const type of types) {
        const template = (messages.notifications as Record<string, string>)[type] ?? '';
        const holes = [...template.matchAll(/\{(\w+)\}/g)].map((x) => x[1] ?? '');
        expect(holes.filter((h) => !params.includes(h)), type).toEqual([]);
        expect(template, type).not.toMatch(/원|금액|\{amount\}|\{value\}/);
      }
    }
  });

  it('inquiry categories and statuses equal the contract enums', () => {
    expect(keys(messages.inquiry.categories)).toEqual(unionOf('InquiryCategory'));
    expect(keys(messages.inquiry.statuses)).toEqual(unionOf('InquiryStatus'));
    expect(messages.inquiry.notice).toBe('고객 이름·연락처·증권번호를 쓰지 마세요');
  });
});
