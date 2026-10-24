/**
 * 알림함·이의제기 API의 유스케이스(포털 Phase 6 — 계약 1.2.0, 계획 ⑥). 진입점은 Phase 5의 접근 규약({@code domain.access}) 그대로. 쓰기는 읽음 처리와
 * 문의 접수 둘이고, 성공한 쓰기는 감사 행을 남기지 않는다 — 행({@code read_at}·{@code inquiry})이 기록이다(계획 Q6). 문의 본문은 상세 응답에만 나온다(Q23).
 */
package com.ga.portal.inbox;
