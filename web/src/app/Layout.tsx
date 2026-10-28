// 공통 틀(포털 Phase 4 계획 ⑥): /me가 관문이다 — /me 404만 "등록되지 않은 사용자"(같은 404 바이트라 다른 화면의 404는 "없음"으로 읽는다).
// 상단 동기화 칩, 하단 탭(홈·월별 명세·한도 현황). 포털 Phase 8: PKCE로 로그인한 세션에만 상단 로그아웃 버튼(웹뷰 세션은 호스트가 정한다).
import { useSyncExternalStore } from 'react';
import { NavLink, Outlet } from 'react-router';
import { ApiError } from '../api/client';
import { logout } from '../auth/oidc';
import { subscribeToken, tokenSource } from '../boot/token';
import { useMe } from '../api/queries';
import { m } from '../messages';
import { Loading, ServerErrorScreen, UnregisteredScreen } from '../ui/States';
import { SyncChip } from '../ui/SyncChip';

export function Layout() {
  const me = useMe();
  const source = useSyncExternalStore(subscribeToken, tokenSource);
  if (me.isPending) return <Loading />;
  if (me.isError) {
    if (me.error instanceof ApiError && me.error.status === 404) return <UnregisteredScreen />;
    if (me.error instanceof ApiError && me.error.status === 401) return null; // App이 다시 로그인 화면으로 바꾼다
    return (
      <ServerErrorScreen
        retry={() => {
          void me.refetch();
        }}
      />
    );
  }
  return (
    <div className="frame">
      <header className="topbar">
        <p className="topbar__title">{m.app.title}</p>
        <SyncChip />
        {source === 'pkce' ? (
          <button
            type="button"
            className="link-button"
            onClick={() => {
              void logout();
            }}
          >
            {m.auth.logout}
          </button>
        ) : null}
      </header>
      <main className="content">
        <Outlet />
      </main>
      <nav className="tabbar" aria-label={m.app.title}>
        <NavLink to="/" end className="tabbar__item">
          {m.nav.home}
        </NavLink>
        <NavLink to="/statements" className="tabbar__item">
          {m.nav.statements}
        </NavLink>
        <NavLink to="/limits" className="tabbar__item">
          {m.nav.limits}
        </NavLink>
      </nav>
    </div>
  );
}
