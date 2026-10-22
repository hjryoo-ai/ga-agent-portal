// 앱 뿌리(포털 Phase 4 계획 ⑦): 토큰이 없으면(처음부터 없거나 401로 버렸으면) 다시 로그인 화면. 있으면 라우터.
import { QueryClientProvider } from '@tanstack/react-query';
import { useState, useSyncExternalStore } from 'react';
import { RouterProvider } from 'react-router/dom';
import { createQueryClient } from '../api/queries';
import { currentToken, subscribeToken } from '../boot/token';
import { ReloginScreen } from '../ui/States';
import { createRouter } from './router';

export function App() {
  const token = useSyncExternalStore(subscribeToken, currentToken);
  const [client] = useState(createQueryClient);
  const [router] = useState(createRouter);
  if (token === null) return <ReloginScreen />;
  return (
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  );
}
