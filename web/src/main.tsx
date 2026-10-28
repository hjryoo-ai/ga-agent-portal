import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import { finishCallback, postToChannel } from './auth/oidc';
import { bootToken } from './boot/token';
import { CallbackScreen } from './ui/States';
import './styles.css';

// React보다 먼저: PKCE 팝업의 콜백이면 주소를 지우고 본 창에 넘긴 뒤 닫는다(포털 Phase 8 — code·state는 그리지 않는다).
// 아니면 프래그먼트의 토큰을 메모리로 옮기고 주소에서 지운다(계획 ⑦).
const callback = finishCallback(window.location, window.history, postToChannel, () => {
  window.close();
});
if (!callback) bootToken(window.location, window.history);

const root = document.getElementById('root');
if (root === null) {
  throw new Error('#root is missing');
}
createRoot(root).render(<StrictMode>{callback ? <CallbackScreen /> : <App />}</StrictMode>);
