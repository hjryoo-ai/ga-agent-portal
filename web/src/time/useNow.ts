// 화면의 기준 시각 — 화면이 처음 그려질 때 한 번 읽는다(렌더마다 바뀌지 않게). "이번 달"·D-n의 기준(E2E는 브라우저 시계를 고정한다).
import { useState } from 'react';

const readClock = (): number => Date.now();

export function useNow(): number {
  const [now] = useState(readClock);
  return now;
}
