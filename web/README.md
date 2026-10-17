# web — 설계사 화면 (포털 Phase 4)

React 19 + TypeScript 5.9(strict) + Vite 8 모바일 웹. 빌드 산출물(`build/web/dist`)은 `processResources`로 `portal-web/`에 들어가 `portal-app` 부트 jar가 API와 같은 출처로 서빙한다. 정본 규약은 저장소 루트 `CLAUDE.md` "화면 규약"과 설계서 §6.2·§8·§9·§10.

## 명령

Node 24·pnpm 12는 Gradle(node-gradle)이 받는다. Gradle을 거치는 것이 정본이다 — 직접 `pnpm`을 쓸 때도 같은 판이어야 한다(`engineStrict`).

| Gradle | pnpm | 하는 일 |
|---|---|---|
| `:web:clientCheck` | `client:check` | 생성 클라이언트 `src/gen/portal-api.ts`가 계약(`contracts/api/v1/portal-api.openapi.yaml`)과 `contracts-client.lock.json`의 해시에 맞는지 |
| `:web:clientLock` | `client:lock` | 계약이 바뀌었을 때 생성물·잠금 파일을 다시 쓴다(같은 커밋에) |
| `:web:webTypecheck` · `:web:webLint` · `:web:webTest` | `typecheck` · `lint` · `test` | tsc, ESLint(`eslint/plugin.js`의 규칙 넷 포함, 경고 0), Vitest |
| `:web:webBuild` | `build` | Vite 빌드 → 산출물 스캔(`scripts/scan-dist.mjs`) → 번들 상한(`scripts/bundle-size.mjs`, 초기 로드 gzip ≤ 127 KiB) |
| `:web:webLicenses` | `licenses` | 의존성 라이선스를 `THIRD-PARTY.md`와 대조 |
| `:web:webAudit` | `audit:record` | `pnpm audit` 결과를 `build/web/audit.json`으로(CI 아티팩트) |
| `:web:e2eInstall` · `:web:e2e` | `e2e:install` · `e2e` | Playwright 브라우저 설치, 화면 E2E(Docker 필요 — 아래) |
| `:web:e2eServe` | `e2e:serve` | 로컬에서 화면 보기(루트 README "화면") |

`./gradlew build`는 E2E를 뺀 전부를 돈다. E2E는 CI 잡 `web-e2e`.

## 구조

- `src/boot/token.ts` — `#token=` 프래그먼트 → 메모리, 주소에서 지움.
- `src/api/` — `client.ts`(유일한 `openapi-fetch` 사용처), `queries.ts`(TanStack Query 훅, 401 → 토큰 버림·캐시 비움).
- `src/money/won.ts` — 불투명 `Won`을 숫자로 다루는 유일한 파일(`formatWon`·`isZero`·`present`). `Amount.tsx` — 금액은 라벨과 함께만.
- `src/labels/labels.ts` — 상태 라벨(`StatusLabel`)과 세후 16칸 표. 문구는 `src/messages.ko.json`.
- `src/screens/` — 홈·명세·라인·한도 목록·한도 상세. `src/ui/` — 동기화 칩, 상태 화면, 월 카드, 한도 바 등.
- `src/routes.ts` — 경로 닫힌 목록(서버 `ScreenConfiguration.SCREEN_PATHS`와 같아야 한다).

## E2E

`e2e/run.mjs`가 Java 하네스(`WebE2eHarness` — PostgreSQL Testcontainers, 시험용 발급자, 테넌트별 피드 스텁)와 부트 jar를 띄우고, Playwright 세 프로젝트(모바일 Chromium·WebKit, 데스크톱)를 돌린 뒤 내린다. 각 프로젝트는 테넌트 한 벌(GA01~GA04 × A/B/C)을 쓰고 피드 머리를 단계별로 올려 실 인제스트로 상태 전이를 본다. 스트림은 엔진·계약 사본 바이트 그대로이거나 손 스트림(`e2e/streams/`, 허구 — 그 README).

비밀(토큰)은 `build/e2e/.state`(700)의 파일에만 있다. 끝나면 `build/e2e` 전체를 토큰·센티널로 스캔한다. 트레이스·스크린샷·비디오는 끈다(토큰이 실릴 수 있다).
