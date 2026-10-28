// 상태 화면(포털 Phase 4 계획 ⑥·⑦ — 지시문 ④): 다시 로그인(401·토큰 없음), 등록되지 않은 사용자(/me 404), 잠시 후 다시(5xx·네트워크), 불러오는 중.
// 포털 Phase 8: 이 출처에 로그인 설정이 있으면(GET /auth/config) 다시 로그인 화면이 "로그인" 버튼(PKCE 팝업)이 된다. 없으면 지금의 웹뷰 문구 그대로.
import { useEffect, useState } from 'react';
import { LoginError, loginConfig, startLogin, type LoginConfig } from '../auth/oidc';
import { m } from '../messages';

function Message({ title, body, testId, action }: { title: string; body: string; testId: string; action?: { text: string; run: () => void } }) {
  return (
    <section className="message" data-testid={testId}>
      <h1>{title}</h1>
      <p>{body}</p>
      {action === undefined ? null : (
        <button type="button" className="button" onClick={action.run}>
          {action.text}
        </button>
      )}
    </section>
  );
}

/**
 * 로그인 설정이 없는 출처(웹뷰): 포털에는 로그인 화면도 IdP 주소도 없다 — 웹뷰 호스트가 새 토큰과 함께 다시 연다("다시 시도"는 새로고침 — 호스트가 가로채
 * 프래그먼트를 붙인다, §14.1 #14 v1.11 확정). 설정이 있는 출처(브라우저): "로그인" 버튼이 PKCE 팝업을 연다.
 */
export function ReloginScreen() {
  const [config, setConfig] = useState<LoginConfig | null | undefined>(undefined);
  const [failure, setFailure] = useState<string | null>(null);
  useEffect(() => {
    let live = true;
    void loginConfig().then((c) => {
      if (live) setConfig(c);
    });
    return () => {
      live = false;
    };
  }, []);
  if (config === undefined) return <Loading />;
  if (config === null) {
    return (
      <Message
        testId="screen-relogin"
        title={m.auth.reloginTitle}
        body={m.auth.reloginBody}
        action={{
          text: m.auth.retry,
          run: () => {
            window.location.reload();
          },
        }}
      />
    );
  }
  return (
    <Message
      testId="screen-relogin"
      title={m.auth.loginTitle}
      body={failure ?? m.auth.loginBody}
      action={{
        text: m.auth.login,
        run: () => {
          setFailure(null);
          startLogin(config).catch((e: unknown) => {
            setFailure(e instanceof LoginError && e.reason === 'popup' ? m.auth.popupBlocked : m.auth.loginFailed);
          });
        },
      }}
    />
  );
}

/** PKCE 팝업의 콜백 문서 — code·state는 그리지 않는다(조건 B). */
export function CallbackScreen() {
  return <Message testId="screen-callback" title={m.auth.callbackTitle} body={m.auth.callbackBody} />;
}

export function UnregisteredScreen() {
  return <Message testId="screen-unregistered" title={m.auth.unregisteredTitle} body={m.auth.unregisteredBody} />;
}

export function ServerErrorScreen({ retry }: { retry: () => void }) {
  return <Message testId="screen-server-error" title={m.auth.serverErrorTitle} body={m.auth.serverErrorBody} action={{ text: m.auth.retry, run: retry }} />;
}

export function Loading() {
  return (
    <p className="loading" role="status">
      {m.app.loading}
    </p>
  );
}
