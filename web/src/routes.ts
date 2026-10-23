// 화면 경로(닫힌 목록 — 포털 Phase 4 계획 ⑥). 서버(portal-app ScreenConfiguration.SCREEN_PATHS)가 이 경로들에만 index.html을 낸다 —
// WebScreensIT가 두 목록을 양방향으로 대조한다(`:이름` ↔ `*`). 경로에는 증권번호·policyKey를 넣지 않는다(자기 calcId·ledgerId·월만 — 타임라인의 계약 필터·해약 영향 인계도 주소 밖, Phase 5).
export const ROUTE_PATHS = ['/', '/statements', '/statements/:ym', '/lines/:calcId', '/limits', '/limits/:ledgerId', '/deferrals', '/simulations'] as const;

export type RoutePath = (typeof ROUTE_PATHS)[number];
