// 화면이 내는 변경 유형(포털 Phase 5): 계약 열거에서 PAYMENT_OVERDUE를 뺀 다섯 — 엔진이 시뮬레이션하지 않는다(지시문 ⑬, 엔진 422
// UNSUPPORTED_EVENT_TYPE은 API 요청으로만 보인다). 문구 키·계약 열거와의 관계는 messages.test가 본다.
import type { components } from '../gen/portal-api';

export const EVENT_TYPES = ['CANCEL', 'LAPSE', 'WITHDRAW', 'REDUCE', 'REINSTATE'] as const satisfies readonly components['schemas']['SimPolicyEventRequest']['eventType'][];
