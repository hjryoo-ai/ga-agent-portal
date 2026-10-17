import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import { bootToken } from './boot/token';
import './styles.css';

// React보다 먼저: 프래그먼트의 토큰을 메모리로 옮기고 주소에서 지운다(계획 ⑦).
bootToken(window.location, window.history);

const root = document.getElementById('root');
if (root === null) {
  throw new Error('#root is missing');
}
createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
