// 토큰 주입(포털 Phase 4 계획 ⑦ — Q1 ⓒ URL 프래그먼트 + 메모리). 웹뷰 호스트가 /#token=<JWT>로 연다.
// React보다 먼저 읽고 바로 주소에서 지운다(history.replaceState) — 프래그먼트는 요청에 실리지 않아 서버 로그·Referer에 남지 않고, 지운 뒤에는
// 주소·이력에도 없다. 토큰은 이 모듈의 변수(메모리)에만 있다 — 브라우저 저장소·쿠키 0(ESLint가 막는다). 새로고침하면 사라진다(호스트가 다시 넣는다).
// 서명은 서버가 본다 — 여기서는 JWT 세 조각 모양만 확인한다.
const JWT_SHAPE = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;

let token: string | null = null;
const listeners = new Set<() => void>();

/** 주소의 프래그먼트에서 토큰을 꺼내고 프래그먼트를 지운다. 프래그먼트가 있으면 토큰이 아니어도 지운다. */
export function bootToken(location: Pick<Location, 'hash' | 'pathname' | 'search'>, history: Pick<History, 'replaceState'>): void {
  if (location.hash === '' || location.hash === '#') {
    return;
  }
  const params = new URLSearchParams(location.hash.slice(1));
  const candidate = params.get('token');
  history.replaceState(null, '', location.pathname + location.search);
  token = candidate !== null && JWT_SHAPE.test(candidate) ? candidate : null;
  notify();
}

export function currentToken(): string | null {
  return token;
}

/** 401을 받으면 버린다 — 다시 넣는 것은 웹뷰 호스트다. */
export function dropToken(): void {
  if (token !== null) {
    token = null;
    notify();
  }
}

export function subscribeToken(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function notify(): void {
  for (const l of listeners) {
    l();
  }
}
