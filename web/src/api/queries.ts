// 서버 상태(TanStack Query — 지시문 ①): 엔드포인트마다 훅 하나. 화면은 응답을 그대로 그리고 계산하지 않는다.
import { MutationCache, QueryCache, QueryClient, useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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
export type DeferralsResponse = Schemas['DeferralsResponse'];
export type SimNewContractRequest = Schemas['SimNewContractRequest'];
export type SimPolicyEventRequest = Schemas['SimPolicyEventRequest'];
export type SimNewContractResponse = Schemas['SimNewContractResponse'];
export type SimPolicyEventResponse = Schemas['SimPolicyEventResponse'];
export type ProblemCode = Schemas['Problem']['code'];
export type Notification = Schemas['Notification'];
export type NotificationType = Notification['type'];
export type InquiryCategory = Schemas['InquiryCategory'];
export type InquiryStatus = Schemas['InquiryStatus'];
export type InquiryRequest = Schemas['InquiryRequest'];

/** 라인 목록은 그 달 전체를 받는다(계획 Q12 — 접기는 같은 달 안에서). API 페이지 크기 상한. */
const LINE_PAGE_SIZE = 200;

/** 401이면 토큰을 버리고 캐시를 비운다(다음 토큰은 다른 사람일 수 있다) — App이 다시 로그인 화면으로 바뀐다. 조회·시뮬레이션(변이) 둘 다. */
export function createQueryClient(): QueryClient {
  const onError = (error: Error) => {
    if (error instanceof ApiError && error.status === 401) {
      dropToken();
      client.clear();
    }
  };
  const client: QueryClient = new QueryClient({
    queryCache: new QueryCache({ onError }),
    mutationCache: new MutationCache({ onError }),
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

export function useMonth(ym: string, enabled = true) {
  return useQuery({ queryKey: ['month', ym], queryFn: () => unwrap(api.GET('/api/v1/me/earnings/{ym}', { params: { path: { ym } } })), enabled });
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

/** 분급 타임라인(1.1.0): 지급월 범위 + 계약 하나(선택) + 시나리오(RETENTION이면 p — 화면이 계약 pattern으로 미리 막는다). */
export interface DeferralQuery {
  from: string;
  to: string;
  policyKey?: string;
  retention?: string;
}

export function useDeferrals(q: DeferralQuery, enabled = true) {
  const query = {
    from: q.from,
    to: q.to,
    ...(q.policyKey === undefined ? {} : { policyKey: q.policyKey }),
    ...(q.retention === undefined ? {} : { scenario: 'RETENTION' as const, p: q.retention }),
  };
  return useQuery({
    queryKey: ['deferrals', q.from, q.to, q.policyKey ?? '', q.retention ?? ''],
    queryFn: () => unwrap(api.GET('/api/v1/me/deferrals', { params: { query } })),
    enabled,
  });
}

/** 시뮬레이션 거부(포털 고정 바이트의 code — 422 셋·429·502·503 구분용)와 429의 Retry-After 문자열(숫자로 바꾸지 않는다). */
export class SimulationError extends ApiError {
  constructor(
    status: number,
    readonly code: ProblemCode | undefined,
    readonly retryAfter: string | undefined,
  ) {
    super(status);
    this.name = 'SimulationError';
  }
}

async function simulate<T>(call: Promise<{ data?: T; error?: { code: ProblemCode }; response: Response }>): Promise<T> {
  const { data, error, response } = await call;
  if (!response.ok || data === undefined) {
    throw new SimulationError(response.status, error?.code, response.headers.get('Retry-After') ?? undefined);
  }
  return data;
}

/** 시뮬레이션은 변이다 — 재시도 없음(엔진 재시도 0 — 지시문 ⑬), 결과는 메모리에만(캐시 키 없음). */
export function useNewContractSimulation() {
  return useMutation({
    mutationFn: (body: SimNewContractRequest) => simulate(api.POST('/api/v1/me/simulations/new-contract', { body })),
    retry: false,
  });
}

export function usePolicyEventSimulation() {
  return useMutation({
    mutationFn: (body: SimPolicyEventRequest) => simulate(api.POST('/api/v1/me/simulations/policy-event', { body })),
    retry: false,
  });
}

export function useLimit(ledgerId: string) {
  return useQuery({ queryKey: ['limit', ledgerId], queryFn: () => unwrap(api.GET('/api/v1/me/limits/{ledgerId}', { params: { path: { ledgerId } } })) });
}

// ---------------------------------------------------------------- 알림함·이의제기(포털 Phase 6, 계약 1.2.0)

/** 알림 한 페이지 크기(알림함 "더 보기"). 홈은 최근 셋. */
export const NOTIFICATION_PAGE_SIZE = 20;

/** 홈의 최근 알림(size=3 한 콜 — 안읽음 수는 응답의 unreadCount). */
export function useRecentNotifications() {
  return useQuery({
    queryKey: ['notifications', 'recent'],
    queryFn: () => unwrap(api.GET('/api/v1/me/notifications', { params: { query: { size: 3 } } })),
  });
}

/** 알림함 — 최신순, "더 보기"가 다음 페이지. */
export function useNotificationPages() {
  return useInfiniteQuery({
    queryKey: ['notifications', 'pages'],
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      unwrap(api.GET('/api/v1/me/notifications', { params: { query: { page: pageParam, size: NOTIFICATION_PAGE_SIZE } } })),
    getNextPageParam: (last) => ((last.page + 1) * last.size < last.total ? last.page + 1 : undefined),
  });
}

/** 읽음(멱등) — 끝나면 알림 캐시를 버린다(안읽음 수·굵기가 바뀐다). */
export function useMarkRead() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => unwrap(api.PATCH('/api/v1/me/notifications/{id}/read', { params: { path: { id } } })),
    retry: false,
    onSettled: () => client.invalidateQueries({ queryKey: ['notifications'] }),
  });
}

export function useInquiries() {
  return useQuery({ queryKey: ['inquiries'], queryFn: () => unwrap(api.GET('/api/v1/me/inquiries', { params: { query: { size: 100 } } })) });
}

export function useInquiry(id: string) {
  return useQuery({ queryKey: ['inquiry', id], queryFn: () => unwrap(api.GET('/api/v1/me/inquiries/{id}', { params: { path: { id } } })) });
}

/** 문의 접수 거부 — 429의 Retry-After는 문자열 그대로(숫자로 바꾸지 않는다). */
export class InquiryError extends ApiError {
  constructor(
    status: number,
    readonly retryAfter: string | undefined,
  ) {
    super(status);
    this.name = 'InquiryError';
  }
}

/** 접수는 변이 — 재시도 없음(한 번 누름 = 한 행). 끝나면 목록을 버린다. */
export function useCreateInquiry() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (body: InquiryRequest) => {
      const { data, response } = await api.POST('/api/v1/me/inquiries', { body });
      if (!response.ok || data === undefined) throw new InquiryError(response.status, response.headers.get('Retry-After') ?? undefined);
      return data;
    },
    retry: false,
    onSuccess: () => client.invalidateQueries({ queryKey: ['inquiries'] }),
  });
}
