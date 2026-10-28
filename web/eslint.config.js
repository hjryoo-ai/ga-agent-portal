// 화면 코드 린트(포털 Phase 4 계획 ④-b): any 0, 금액 연산 0, 수기 네트워크 0, 브라우저 저장소 0, 서비스 워커 0, 콘솔 0, 한글 리터럴은 messages.ko.json만.
// 네트워크는 생성 클라이언트(openapi-fetch)뿐이고 그것을 만드는 곳은 src/api/client.ts 하나다(no-restricted-imports). 예외는 닫힌 두 파일뿐이다(마지막 두 블록):
// src/api/client.ts(openapi-fetch) · src/auth/oidc.ts(포털 Phase 8 — 발급자 출처로의 디스커버리·토큰·갱신·종료 fetch, 계획 P-11·돌이킬 수 없는 결정 7).
// 저장소 금지는 두 파일에서도 그대로다. lintRules.test.ts가 "예외는 정확히 두 파일"과 "다른 파일의 fetch는 여전히 실패"를 본다.
import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
import tseslint from 'typescript-eslint';
import portal from './eslint/plugin.js';

const NETWORK = 'Network calls go through the generated contract client only (Phase 4 C1).';
const STORAGE = 'Browser storage is forbidden — the token and API data stay in memory (Phase 4 ④·C10).';
const restrictedGlobals = [
  ...['fetch', 'XMLHttpRequest', 'WebSocket', 'EventSource'].map((name) => ({ name, message: NETWORK })),
  ...['localStorage', 'sessionStorage', 'indexedDB', 'caches', 'cookieStore'].map((name) => ({ name, message: STORAGE })),
];
const restrictedProperties = [
  ...['window', 'globalThis', 'self'].flatMap((object) => [
    ...['fetch', 'XMLHttpRequest', 'WebSocket', 'EventSource'].map((property) => ({ object, property, message: NETWORK })),
    ...['localStorage', 'sessionStorage', 'indexedDB', 'caches', 'cookieStore'].map((property) => ({ object, property, message: STORAGE })),
  ]),
  { object: 'navigator', property: 'sendBeacon', message: NETWORK },
  { object: 'navigator', property: 'serviceWorker', message: 'No service worker — responses would be cached outside memory (plan P-6).' },
  { object: 'document', property: 'cookie', message: STORAGE },
];
const clientOnly = { name: 'openapi-fetch', message: 'The API client is created only in src/api/client.ts (Phase 4 C1).' };
/** 네트워크 예외 파일(닫힌 목록 — lintRules.test.ts). */
export const NETWORK_EXCEPTIONS = ['src/api/client.ts', 'src/auth/oidc.ts'];

export default tseslint.config(
  { ignores: ['build/**', 'node_modules/**', 'src/gen/**', '.gradle/**', 'test-results/**', 'playwright-report/**'] },
  js.configs.recommended,
  ...tseslint.configs.strictTypeChecked,
  {
    languageOptions: { parserOptions: { projectService: true, tsconfigRootDir: import.meta.dirname } },
    rules: {
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/restrict-template-expressions': ['error', { allowNumber: true }],
    },
  },
  {
    files: ['eslint.config.js', 'eslint/**/*.js', 'scripts/**/*.mjs', 'e2e/**/*.mjs'],
    extends: [tseslint.configs.disableTypeChecked],
    languageOptions: { globals: globals.node },
  },
  {
    // E2E(Node 쪽 Playwright 시험): 브라우저 금지 규칙 밖 — 하네스 제어·토큰 파일은 Node에서 읽는다. 금액 규칙은 화면 코드(src)의 것이다.
    files: ['e2e/**/*.ts', 'playwright.config.ts'],
    languageOptions: { globals: globals.node },
  },
  {
    files: ['src/**/*.{ts,tsx}'],
    languageOptions: { globals: globals.browser },
    plugins: { 'react-hooks': reactHooks, portal },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'portal/no-money-arithmetic': 'error',
      'no-console': 'error',
    },
  },
  {
    // 배포 코드(시험 밖): 한글 리터럴·네트워크·저장소 금지. 시험 원천은 저장소가 비었는지 직접 들여다보고 화면 문구를 단언한다 — 배포 번들에 들지 않는다(scan-dist).
    files: ['src/**/*.{ts,tsx}'],
    ignores: ['src/test/**', 'src/**/*.test.{ts,tsx}'],
    rules: {
      'portal/no-hangul-literal': 'error',
      'no-restricted-globals': ['error', ...restrictedGlobals],
      'no-restricted-properties': ['error', ...restrictedProperties],
      'no-restricted-imports': ['error', { paths: [clientOnly], patterns: [{ group: ['node:*'], message: 'Browser code.' }] }],
    },
  },
  {
    // 생성 클라이언트를 만드는 한 파일만 openapi-fetch를 가져온다(위 금지를 이 파일에서만 덮는다).
    files: [NETWORK_EXCEPTIONS[0]],
    rules: { 'no-restricted-imports': ['error', { patterns: [{ group: ['node:*'], message: 'Browser code.' }] }] },
  },
  {
    // PKCE(포털 Phase 8): 발급자 출처로의 fetch만 이 파일에서 연다 — 저장소·XMLHttpRequest·WebSocket·EventSource·sendBeacon 금지는 그대로.
    files: [NETWORK_EXCEPTIONS[1]],
    rules: {
      'no-restricted-globals': ['error', ...restrictedGlobals.filter((g) => g.name !== 'fetch')],
      'no-restricted-properties': ['error', ...restrictedProperties.filter((p) => p.property !== 'fetch')],
    },
  },
);
