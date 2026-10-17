// 생성 클라이언트(포털 Phase 4 C1): 화면의 모든 API 호출이 이 하나를 거친다. 같은 출처(/api) — baseUrl 없음, CORS 없음.
import createClient from 'openapi-fetch';
import type { paths } from '../gen/portal-api';

export const api = createClient<paths>();
