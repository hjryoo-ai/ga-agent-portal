// 알림의 link 대조(포털 Phase 6 계획 승인 A — 규칙 8의 화면판): link는 서버 데이터다. 계약 pattern·V7 CHECK가 있어도 화면은 믿지 않고, 닫힌 화면 경로 넷
// (/lines/:calcId·/limits/:ledgerId·/statements/:ym·/inquiries/:id — ROUTE_PATHS의 항목)에 맞는 것만 라우터에 넘긴다. 맞지 않으면 null — link가 없는
// 알림과 같게 읽음만 한다. 같은 출처의 경로 문자열만: 스킴·`//`·역슬래시·쿼리·조각·점 조각·빈 조각은 전부 아니다.
import { matchPath } from 'react-router';
import type { RoutePath } from '../routes';

/** 알림이 가리킬 수 있는 화면(ROUTE_PATHS의 부분 — 타입이 목록 밖 경로를 막는다)과 그 조각의 모양. */
export const LINKABLE: readonly { path: RoutePath; param: RegExp }[] = [
  { path: '/lines/:calcId', param: /^[A-Za-z0-9._~%-]+$/ },
  { path: '/limits/:ledgerId', param: /^[A-Za-z0-9._~%-]+$/ },
  { path: '/statements/:ym', param: /^[0-9]{6}$/ },
  { path: '/inquiries/:id', param: /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/ },
];

const SHAPE = /^\/[a-z]+\/[^/]+$/;

/** 이동할 경로, 아니면 null. */
export function screenPath(link: string | undefined): string | null {
  if (link === undefined || !SHAPE.test(link)) return null;
  const raw = link.slice(link.indexOf('/', 1) + 1);
  if (raw === '.' || raw === '..') return null;
  for (const { path, param } of LINKABLE) {
    if (matchPath({ path, end: true }, link) !== null && param.test(raw)) return link;
  }
  return null;
}
