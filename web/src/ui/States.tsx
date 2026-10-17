// 상태 화면(포털 Phase 4 계획 ⑥·⑦ — 지시문 ④): 다시 로그인(401·토큰 없음), 등록되지 않은 사용자(/me 404), 잠시 후 다시(5xx·네트워크), 불러오는 중.
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

/** 포털에는 로그인 화면도 IdP 주소도 없다 — 웹뷰 호스트가 새 토큰과 함께 다시 연다. */
export function ReloginScreen() {
  return (
    <Message
      testId="screen-relogin"
      title={m.auth.reloginTitle}
      body={m.auth.reloginBody}
      action={{
        text: m.auth.retry,
        run: () => {
          // TODO(confirm#14): "다시 시도"가 호스트에 닿는 방법은 미정 — 기본 가정은 새로고침(호스트가 토큰을 다시 넣는다, 설계서 §14.1 #14).
          window.location.reload();
        },
      }}
    />
  );
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
