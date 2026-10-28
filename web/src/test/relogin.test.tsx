// 다시 로그인 화면(포털 Phase 8 계획 Q2·C7): 이 출처에 로그인 설정이 없으면(GET /auth/config 404) 지금의 웹뷰 문구·"다시 시도" 그대로,
// 있으면 "로그인" 버튼(PKCE 팝업). PKCE 세션이 API 401을 받으면 토큰·refresh를 버리고 로그인 버튼으로 돌아온다. 로그아웃 버튼은 PKCE 세션에만.
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import messages from '../messages.ko.json';

const net = vi.hoisted(() => {
  const state = { config: null as null | { issuer: string; clientId: string }, me: 401, calls: [] as string[] };
  globalThis.fetch = (input: RequestInfo | URL) => {
    const url = new URL(input instanceof Request ? input.url : String(input), 'http://portal.test');
    state.calls.push(url.pathname);
    if (url.pathname === '/auth/config') {
      return Promise.resolve(
        state.config === null
          ? new Response('{"code":"NOT_FOUND","message":"not found"}', { status: 404, headers: { 'Content-Type': 'application/problem+json' } })
          : new Response(JSON.stringify(state.config), { status: 200, headers: { 'Content-Type': 'application/json' } }),
      );
    }
    return Promise.resolve(
      new Response('{"code":"UNAUTHENTICATED","message":"authentication required"}', { status: state.me, headers: { 'Content-Type': 'application/problem+json' } }),
    );
  };
  return state;
});

beforeEach(() => {
  vi.resetModules();
  net.calls.length = 0;
});

afterEach(() => {
  cleanup();
});

describe('ReloginScreen', () => {
  it('without a login configuration keeps the webview wording and the retry button', async () => {
    net.config = null;
    const { ReloginScreen } = await import('../ui/States');
    render(<ReloginScreen />);
    const relogin = await screen.findByTestId('screen-relogin');
    expect(relogin.textContent).toContain(messages.auth.reloginTitle);
    expect(screen.getByRole('button').textContent).toBe(messages.auth.retry);
    expect(screen.queryByText(messages.auth.login)).toBeNull();
  });

  it('with a login configuration offers the login button, and a blocked popup says so', async () => {
    net.config = { issuer: 'http://localhost:8180/realms/demo', clientId: 'portal-web' };
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    const { ReloginScreen } = await import('../ui/States');
    render(<ReloginScreen />);
    const relogin = await screen.findByTestId('screen-relogin');
    expect(relogin.textContent).toContain(messages.auth.loginTitle);
    fireEvent.click(screen.getByRole('button', { name: messages.auth.login }));
    expect(open).toHaveBeenCalledOnce();
    await waitFor(() => {
      expect(relogin.textContent).toContain(messages.auth.popupBlocked);
    });
    expect(net.calls.filter((c) => c === '/auth/config')).toHaveLength(1);
    open.mockRestore();
  });
});

describe('App', () => {
  it('a PKCE session that gets a 401 drops the token and shows the login button again', async () => {
    net.config = { issuer: 'http://localhost:8180/realms/demo', clientId: 'portal-web' };
    net.me = 401;
    const token = await import('../boot/token');
    const { App } = await import('../app/App');
    token.setToken('eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4In0.c2ln', 'pkce');
    render(<App />);
    await screen.findByRole('button', { name: messages.auth.login });
    expect(token.currentToken()).toBeNull();
    expect(token.tokenSource()).toBeNull();
    expect(net.calls).toContain('/api/v1/me');
  });
});
