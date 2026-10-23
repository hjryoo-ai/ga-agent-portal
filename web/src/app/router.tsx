// 화면 경로(src/routes.ts의 닫힌 목록과 1:1 — 빠진 경로는 타입 오류). 홈은 초기 번들, 나머지는 경로별 지연 청크(번들 크기 표 — 계획 ③).
import { createBrowserRouter, type RouteObject } from 'react-router';
import { ROUTE_PATHS, type RoutePath } from '../routes';
import { HomeScreen } from '../screens/HomeScreen';
import { Layout } from './Layout';

type Child = Omit<RouteObject, 'path' | 'index' | 'children'>;

const screens: Record<RoutePath, Child> = {
  '/': { element: <HomeScreen /> },
  '/statements': { lazy: () => import('../screens/StatementScreen').then((s) => ({ Component: s.StatementScreen })) },
  '/statements/:ym': { lazy: () => import('../screens/StatementScreen').then((s) => ({ Component: s.StatementScreen })) },
  '/lines/:calcId': { lazy: () => import('../screens/LineScreen').then((s) => ({ Component: s.LineScreen })) },
  '/limits': { lazy: () => import('../screens/LimitsScreen').then((s) => ({ Component: s.LimitsScreen })) },
  '/limits/:ledgerId': { lazy: () => import('../screens/LimitScreen').then((s) => ({ Component: s.LimitScreen })) },
  '/deferrals': { lazy: () => import('../screens/TimelineScreen').then((s) => ({ Component: s.TimelineScreen })) },
  '/simulations': { lazy: () => import('../screens/SimulationScreen').then((s) => ({ Component: s.SimulationScreen })) },
};

export function createRouter() {
  const children: RouteObject[] = ROUTE_PATHS.map((path) => (path === '/' ? { index: true, ...screens[path] } : { path: path.slice(1), ...screens[path] }));
  return createBrowserRouter([{ path: '/', element: <Layout />, children }]);
}
