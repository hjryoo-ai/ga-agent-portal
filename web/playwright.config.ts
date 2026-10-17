// 화면 E2E(포털 Phase 4 계획 ⑨ — Q10): ./gradlew :web:e2e → e2e/run.mjs가 하네스·포털을 띄우고 이 설정으로 돌린 뒤 내린다.
// 프로젝트 셋(모바일 Android = Chromium, 모바일 iOS = WebKit, 데스크톱 = Chromium)은 각자 테넌트 한 벌을 쓴다(run.json projects — 머리를 따로 올린다).
// 서버 하나를 같이 쓰므로 직렬(worker 1). 브라우저 시간대는 UTC — 화면이 KST를 명시하는지 본다. 트레이스·스크린샷·비디오·HTML 보고서 끔
// (실패 산출물에 토큰이 실릴 수 있다 — run.mjs가 build/e2e를 토큰·센티널로 스캔한다). 스냅샷 시험 없음(C11).
import { defineConfig, devices } from '@playwright/test';
import { resolve } from 'node:path';

const out = resolve(import.meta.dirname, 'build/e2e');

export default defineConfig({
  testDir: 'e2e/specs',
  outputDir: resolve(out, 'test-results'),
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: true,
  timeout: 180_000,
  expect: { timeout: 30_000 },
  reporter: [['list'], ['junit', { outputFile: resolve(out, 'junit.xml') }]],
  use: { trace: 'off', screenshot: 'off', video: 'off', locale: 'ko-KR', timezoneId: 'UTC', actionTimeout: 20_000 },
  projects: [
    // 키보드 경로는 데스크톱만 — 모바일 프로젝트에서는 건너뛰는 시험으로 두지 않고 아예 넣지 않는다
    { name: 'mobile-android', use: { ...devices['Pixel 7'] }, testIgnore: ['keyboard.spec.ts'] },
    { name: 'mobile-ios', use: { ...devices['iPhone 15'] }, testIgnore: ['keyboard.spec.ts'] },
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
  ],
});
