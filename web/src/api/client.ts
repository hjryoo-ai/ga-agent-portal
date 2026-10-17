// 생성 클라이언트(포털 Phase 4 C1): 화면의 모든 API 호출이 이 하나를 거친다. 같은 출처(/api — 기준 주소는 그 문서의 출처), CORS 없음.
// 토큰은 메모리에서 꺼내 Authorization 헤더에만 싣는다(주소·본문·저장소 0). 304는 브라우저 HTTP 캐시가 처리한다(Phase 3 ETag).
import createClient from 'openapi-fetch';
import { currentToken } from '../boot/token';
import type { paths } from '../gen/portal-api';

export const api = createClient<paths>({ baseUrl: window.location.origin });

api.use({
  onRequest({ request }) {
    const token = currentToken();
    if (token !== null) {
      request.headers.set('Authorization', `Bearer ${token}`);
    }
    return request;
  },
});

/** API가 성공이 아닌 상태로 답했다(본문은 같은 바이트의 problem — 사유를 싣지 않는다). */
export class ApiError extends Error {
  constructor(readonly status: number) {
    super(`api responded ${String(status)}`);
    this.name = 'ApiError';
  }
}

/** openapi-fetch 결과 → 본문, 아니면 ApiError(상태). */
export async function unwrap<T>(call: Promise<{ data?: T; response: Response }>): Promise<T> {
  const { data, response } = await call;
  if (!response.ok || data === undefined) {
    throw new ApiError(response.status);
  }
  return data;
}
