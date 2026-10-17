// 서버 상태(TanStack Query — 지시문 ①): 엔드포인트마다 훅 하나. 화면은 응답을 그대로 그리고 계산하지 않는다.
import { QueryCache, QueryClient, useQuery } from '@tanstack/react-query';
import { dropToken } from '../boot/token';
import type { components } from '../gen/portal-api';
import { ApiError, api, unwrap } from './client';

export type Schemas = components['schemas'];
export type MonthStatus = Schemas['MonthStatus'];
export type NetBasis = Schemas['NetBasis'];
export type MonthDataStatus = Schemas['MonthDataStatus'];
export type Line = Schemas['Line'];
export type Ledger = Schemas['Ledger'];
export type MonthResponse = Schemas['MonthResponse'];
export type LineDetailResponse = Schemas['LineDetailResponse'];
export type LimitDetailResponse = Schemas['LimitDetailResponse'];

/** 라인 목록은 그 달 전체를 받는다(계획 Q12 — 접기는 같은 달 안에서). API 페이지 크기 상한. */
const LINE_PAGE_SIZE = 200;

/** 401이면 토큰을 버리고 캐시를 비운다(다음 토큰은 다른 사람일 수 있다) — App이 다시 로그인 화면으로 바뀐다. */
export function createQueryClient(): QueryClient {
  const client: QueryClient = new QueryClient({
    queryCache: new QueryCache({
      onError(error) {
        if (error instanceof ApiError && error.status === 401) {
          dropToken();
          client.clear();
        }
      },
    }),
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        // 401·404·400은 다시 물어도 같다 — 재시도 없음. 5xx·네트워크 오류만 한 번.
        retry: (failures, error) => !(error instanceof ApiError && error.status < 500) && failures < 1,
        refetchOnWindowFocus: false,
      },
    },
  });
  return client;
}

export function useMe() {
  return useQuery({ queryKey: ['me'], queryFn: () => unwrap(api.GET('/api/v1/me')) });
}

export function useEarnings(from: string, to: string) {
  return useQuery({
    queryKey: ['earnings', from, to],
    queryFn: () => unwrap(api.GET('/api/v1/me/earnings', { params: { query: { from, to } } })),
  });
}

export function useMonth(ym: string) {
  return useQuery({ queryKey: ['month', ym], queryFn: () => unwrap(api.GET('/api/v1/me/earnings/{ym}', { params: { path: { ym } } })) });
}

/** 그 달의 라인 전부(페이지를 total까지 이어 받는다) + 마지막 응답의 asOf·dataStatus. */
export function useMonthLines(ym: string) {
  return useQuery({
    queryKey: ['lines', ym],
    queryFn: async () => {
      const items: Line[] = [];
      for (let page = 0; ; page++) {
        const r = await unwrap(api.GET('/api/v1/me/earnings/{ym}/lines', { params: { path: { ym }, query: { page, size: LINE_PAGE_SIZE } } }));
        items.push(...r.items);
        if (r.items.length === 0 || items.length >= r.total) {
          return { asOf: r.asOf, dataStatus: r.dataStatus, closeYm: r.closeYm, total: r.total, items };
        }
      }
    },
  });
}

export function useLine(calcId: string, enabled = true) {
  return useQuery({
    queryKey: ['line', calcId],
    queryFn: () => unwrap(api.GET('/api/v1/me/earnings/lines/{calcId}', { params: { path: { calcId } } })),
    enabled,
  });
}

export function useLimits() {
  return useQuery({ queryKey: ['limits'], queryFn: () => unwrap(api.GET('/api/v1/me/limits', { params: { query: { status: 'ALL' } } })) });
}

export function useLimit(ledgerId: string) {
  return useQuery({ queryKey: ['limit', ledgerId], queryFn: () => unwrap(api.GET('/api/v1/me/limits/{ledgerId}', { params: { path: { ledgerId } } })) });
}
