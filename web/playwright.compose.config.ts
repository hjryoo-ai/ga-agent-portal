// compose E2E(포털 Phase 8 지시문 ⑥, 계획 ⑤): e2e-compose/run.mjs가 compose 스택을 띄운 뒤 이 설정으로 돈다. 프로젝트 하나(데스크톱 Chromium —
// 세 프로젝트는 하네스 E2E가 덮는다). 직렬, 트레이스·스크린샷·비디오·HTML 보고서 끔(실패 산출물에 토큰이 실릴 수 있다 — run.mjs가 산출물을 훑는다).
// 시간대는 UTC가 아니라 실제 시계 그대로 — 데모 데이터의 "이번 달"은 실제 날짜다(설계서 §14.2 — 스트림 재생성).
import { defineConfig, devices } from '@playwright/test';
import { resolve } from 'node:path';

const out = resolve(import.meta.dirname, 'build/compose-e2e');

export default defineConfig({
  testDir: 'e2e-compose/specs',
  outputDir: resolve(out, 'test-results'),
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: true,
  timeout: 600_000,
  expect: { timeout: 30_000 },
  reporter: [['list'], ['junit', { outputFile: resolve(out, 'junit.xml') }]],
  use: { trace: 'off', screenshot: 'off', video: 'off', locale: 'ko-KR', timezoneId: 'Asia/Seoul', actionTimeout: 30_000 },
  projects: [{ name: 'compose', use: { ...devices['Desktop Chrome'] } }],
});
