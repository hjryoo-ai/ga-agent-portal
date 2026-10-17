# 포털 Phase 4 계획 — 화면 1차 (ga-agent-portal)

> 이 계획은 지시문 `docs/phase-04-지시문.md` v1.0(2026-10-17)에 대한 것이다. 브랜치는 `work/phase-4`(`main` = `3d6aa13`, Phase 3 병합)이고, 첫 커밋은 `9ba6e39`(Phase 3 수용 심사 보관·설계서 v1.6.1·F6 지표)다. **이 계획이 승인되기 전에는 화면 코드를 쓰지 않는다.**
>
> 읽은 것:
> - 지시문 전문, Phase 3 수용 심사 §3(Q1~Q6), 설계서 v1.6.1 §0·§1.3·§2.3·§6.2~§6.4·§6.6·§7·§8·§9·§10·§12·§13.
> - `contracts/api/v1/portal-api.openapi.yaml` 1.0.0 전문.
> - `ApiSecurityConfiguration`(체인 둘), `MeQueries`(한도 필터), `StubFeedServer`·`TestIssuerMain`, 골든 스트림 README와 엔진 스트림 일곱.
> - 참고 구현 ga-disclosure Phase 7: `disclosure-web/` — `build.gradle.kts`, `vite.config.ts`, `eslint.config.js`, `scripts/client-lock.mjs`·`scan-dist.mjs`, `playwright.config.ts`, `e2e/support/fixtures.ts`, `DemoWebConfiguration`, Phase 7 계획·수용 심사.
> - npm 레지스트리·nodejs.org·Gradle 플러그인 포털(2026-10-17 조회).

## 0. 먼저 결정이 필요한 것 (요약)

지시문 §3 질문(Q1~Q8)에 더해 계획이 고른 것(Q9~Q16)도 승인 대상이다.

| # | 질문 | 권장 | 대안 |
|---|---|---|---|
| Q1 | 토큰 주입 방식 | **ⓒ URL 프래그먼트 `/#token=<JWT>` + 메모리**. 첫 스크립트가 React보다 먼저 읽고 `history.replaceState`로 지운다. 독립 브라우저는 Phase 8 Keycloak PKCE로 미룬다. 지금은 개발용 붙여 넣기 화면을 두지 않는다(⑦) | ⓐ 인라인 스크립트라 CSP `script-src 'self'`와 맞지 않는다. ⓑ `postMessage`는 앱마다 다리 코드가 필요하고 출처 검사도 추가해야 한다 |
| Q2 | `frame-ancestors` | **`'none'` + `X-Frame-Options: DENY`**. 네이티브 웹뷰(Android WebView·WKWebView)는 프레임이 아니라 최상위 문서이므로 이 지시어가 적용되지 않는다 | GA 앱이 `iframe`으로 임베드하면 그 출처를 테넌트 설정으로 열어야 한다(v2) |
| Q3 | 번들 상한 | **측정값 + 10%(KiB 올림)**. 대상은 초기 로드(HTML + 진입 JS + 정적 import + CSS)의 gzip 합이다. 예상은 120~140 KiB이고, 5단계에서 측정해 확정한다. ga-disclosure에는 JS 번들 상한이 없고 폰트 바이트 상한만 있다(P-1) | 고정값(예: 150 KiB)을 먼저 정한다 |
| Q4 | 전월 대비·다음 달 분급 예정액 | **둘 다 Phase 4 홈에 보이지 않는다.**<br>• 전월 대비: 화면은 차감도 증감 방향(비교)도 하지 않는다. **CR-1**으로 지난달 **원래 값**(`previous{closeYm, status, gross, net?, netBasis?}`)을 요청하고, 차이는 화면이 내지 않는다.<br>• 다음 달 분급: API에 이미 있다(`/me/deferrals` `months[].expected` — P-8). 다만 HELD 문구와 "분급 확정 스케줄만 반영" 고정 문구가 Phase 5 타임라인의 규약이므로 Phase 5에서 함께 보인다 | 전월 대비를 서버 차감 필드(`deltaNet`)로 받는다. 이 경우 설계서 §0의 "합산·비율만"을 넓혀야 한다 |
| Q5 | 다크 모드·국제화 | **범위 밖 — 확인.** 한국어 `messages.ko.json` 하나만 둔다. `<meta name="color-scheme" content="light">`로 웹뷰의 강제 다크(알고리즘 반전)를 끈다 | — |
| Q6 | 같은 출처 서빙 | **`portal-app`의 Spring 정적 자원 처리기 + 화면 체인**(⑧). `web` 모듈 jar의 `classpath:/portal-web/`. 인증 필터는 `/api` 체인에만 둔다. 별도 정적 서버는 Phase 8 인그레스에서 다시 본다 | 별도 nginx — 단일 아티팩트(⑧)가 깨진다 |
| Q7 | 알림 자리 | **자리만 둔다: "알림함 — 준비 중"(비활성 진입점).** "알림 없음" 같은 빈 상태 문구는 쓰지 않는다. 알림이 있는지 모르는데 없다고 말하게 된다 | 영역을 숨긴다(Phase 6에 배치가 바뀐다) |
| Q8 | E2E 시드 | **실 인제스트 경로.** 하네스(Java)가 일회용 PostgreSQL, 시험 발급기, 테넌트별 `StubFeedServer`를 띄운다. 스트림 파일을 단계별로 열고(머리 seq 제어), 포털 bootJar가 그 피드를 실제로 폴링한다. 대기 조건은 `/api/v1/me`의 `asOf.seq`(⑨). `QuerySeed`(SQL 직접)는 쓰지 않는다 | `QuerySeed` 재사용 — 빠르지만 인제스트·프로젝션을 건너뛰어 "스트림을 단계별로"가 아니다 |
| Q9 | 금액 타입을 화면에 한정하는 방법(C2·C3) | **금액은 숫자가 아니다**:<br>• 생성기 변환 훅으로 계약의 `Won`·`NonNegativeWon`을 불투명 브랜드 타입(`{ readonly __won: unique symbol }`)으로 만든다. 그러면 TS가 `+ - * /`와 JSX 직접 렌더를 컴파일 오류로 만든다.<br>• ESLint 커스텀 규칙이 그 타입의 산술·비교·`Number()`·`Math.*`·템플릿 문자열·`String()`을 막는다.<br>• 번들 스캔은 금액 속성 이름(계약에서 생성한 닫힌 목록) 옆의 연산자를 막는다.<br>• 형식화는 `src/money/`(닫힌 허용 목록 — `Amount` 컴포넌트, `isZero`)만 한다(④) | 브랜드를 `number & {…}`로 둔다 — TS가 산술을 막지 못해 ESLint만 남는다 |
| Q10 | Playwright 브라우저 | **모바일 Android = Chromium(Pixel 7), 모바일 iOS = WebKit(iPhone 15), 데스크톱 = Chromium.** iOS GA 앱의 웹뷰는 WKWebView다 | 셋 다 Chromium(iPhone은 뷰포트만) — 빠르지만 iOS 엔진을 보지 못한다 |
| Q11 | E2E 테넌트·스트림 | 테넌트 넷(⑨ 표):<br>• GA01: A-1 둘째 판 + 이웃 설계사<br>• GA02: 엔진 `estimate_cycle_01_기본` — 한 달의 네 단계 전이<br>• GA03: 엔진 E1 cycle — 정정만 있는 달·채권<br>• GA04: 포털 손 스트림 — 같은 달·다른 달 취소 짝, DRIFT, `CUT` 라인<br>A-1에는 한 달의 PROVISIONAL → ESTIMATED → CONFIRMED → PAID가 없다(P-2) | A-1 하나 — 전이·Q3·Q6·DRIFT를 E2E로 못 보인다 |
| Q12 | 라인 목록과 접기(Q6) | **그 달 전체를 받는다**(`size=200`, `total`까지 페이지 반복). 접기는 같은 달 안에서만 하고, 화면 페이지는 클라이언트에서 나눈다. 짝이 그 달에 없으면 다른 달이다. 그 달을 알려고 짝 라인 상세를 한 번 부른다(캐시). 개선은 CR-2 | API 페이지 그대로 — 짝이 다른 페이지에 있으면 같은 달인데 "다른 달"로 보인다 |
| Q13 | 라이선스 검사 | **넣는다(넓힘).** ga-disclosure와 같은 허용 목록(MIT·Apache-2.0·BSD·ISC·0BSD·BlueOak)과 `web/THIRD-PARTY.md` 대조. 지시문에는 없다 | 넣지 않는다 |
| Q14 | E2E CI 잡 | **새 잡 `web-e2e`**(PR·`main`, Docker 필요, Docker가 없으면 실패). `build`의 `check`에는 넣지 않는다(브라우저 설치·실행 시간) | `check`에 포함 |
| Q15 | TypeScript 판 | **5.9.3**. 최신 7.0.2는 `typescript-eslint` 8.71.2(피어 `<6.1.0`)와 `openapi-typescript` 7.13.0(피어 `^5.x`) 밖이다. 6.0.3도 후자의 피어 밖이다(ga-disclosure와 같은 판단) | 7.0.2 + 피어 무시 — 린트가 공식 지원 밖이 된다 |
| Q16 | "이번 달"의 뜻 | 브라우저 시계의 **KST 달력 월**(`Intl.DateTimeFormat` `timeZone: 'Asia/Seoul'`)이다. 그 달 월 행이 없으면(404) "이번 달 내역이 아직 없습니다"를 보이고 명세 월 목록으로 연결한다. 지급일 기준 "이번 달 받을 돈"(전월 마감분)으로 바꾸지 않는다(§7 예시가 마감월 기준이다) | 최신 월 행을 홈에 보인다 — "이번 달"이 아니게 된다 |

## ① 확인한 사실 (지시문·참고 구현·계약과 다른 곳)

| # | 지시문(또는 설계서) 문구 | 사실 | 계획 |
|---|---|---|---|
| P-1 | "참고 구현: ga-disclosure Phase 7(`web/` — … 생성 클라이언트 해시 고정, … 번들 상한)" | 참고 구현과 지시문은 다섯 곳에서 다르다(아래 표). | 지시문을 따르고, 다른 점은 보고서에 표로 남긴다 |
| P-2 | ⑦ "시드 테넌트(A-1 둘째 판 …) … 상태 전이 표시(PROVISIONAL → ESTIMATED → CONFIRMED → PAID — 스트림을 단계별로)" | A-1에서는 202608이 PROVISIONAL(seq 2~5) → CONFIRMED(6, 세후 없음) → PAID(7)로 가고 ESTIMATED를 거치지 않는다. 202702는 PROVISIONAL(9) → ESTIMATED(11)에서 끝난다. 한 달이 네 단계를 다 거치는 스트림은 엔진 `estimate_cycle_01_기본`(seq 3 → 4 → 5 → 6, CONFIRMED는 세후 예상)뿐이다 | A-1(GA01)과 `estimate_cycle_01`(GA02)을 둘 다 단계별로 넣는다(Q11). Q2의 세 조합(세후 예상·세후 없음·지급)이 전부 E2E에 나온다 |
| P-3 | ③ "`monthDataStatus DRIFT` → '확인 중'" | 계약 열거는 `OK`·`DRIFT`·`REBUILDING`이다(설계서 v1.5는 `REBUILDING` 미사용이라고 적었다) | `REBUILDING`도 "확인 중" + 흐리게. 열거 전수 `switch`(누락은 컴파일 오류) |
| P-4 | 설계서 §10·Phase 3 "홈 세 콜 … `/limits?status=WARN`", §8 "한도 WARN 이상 N건" | `status=WARN`은 **정확히 WARN**만 돌려준다(`MeQueries` — `card.stage().equals(status)`). A-1 카드는 `FULL`이라 빠진다 | 홈은 `status=ALL`로 받고, 화면에서 `stage !== 'OK'`인 개수와 플래그 개수를 센다(문자열 비교·개수 — 금액 연산 아님). `QueryStatementIT`에 `ALL`의 문장 수를 더한다(서버 시험만, API 변경 없음) |
| P-5 | 설계서 §12 Phase 4 "홈·월별 명세·한도 현황·**알림함**" | 지시문 ②는 알림함을 Phase 6으로 미룬다 | 지시문을 따른다. 설계서 §12를 고친다(⑫) |
| P-6 | 설계서 §3.3·§11 "React PWA" | 지시문에는 서비스 워커가 없고, §2.2는 오프라인 캐시를 v1에서 뺐다. 서비스 워커 캐시에는 응답(개인 데이터)이 남는다 | 서비스 워커·매니페스트 없음. ESLint로 `navigator.serviceWorker`를 금지한다. 설계서 §11 "PWA"를 "모바일 웹(PWA는 v2)"으로 고친다 |
| P-7 | 지시문 Q4 "다음 달 분급 예정액 … API가 주지 않는다" | `/me/deferrals?from&to`의 `months[].expected`(Σ SCHEDULED + RELEASED, SQL 정수 합)가 있다 | Q4 — Phase 5에서 타임라인과 함께 |
| P-8 | ⑦ "한도 카드 1(삭감 145,000)" | A-1 seq 3의 정책은 `DEFER_AFTER_FY`라 원장에서는 `deferredTotal` = 145,000, `cutTotal` = 0이다(설계서 §6.3 분류) | 화면은 두 값 모두 "한도 초과 삭감"으로 쓰고, `DEFER_AFTER_FY` 쪽에 보조 문구를 붙인다(심사 Q4). 둘을 더하지 않는다 — 0이 아닌 줄만 보인다(`isZero`) |
| P-9 | C2 "`Number()` 변환 0, 포맷은 `Intl.NumberFormat` 정수만" | `Intl.NumberFormat.format`은 `number`를 받는다. 불투명 타입(Q9)을 숫자로 넘기는 곳이 한 곳은 필요하다 | 그 변환은 `src/money/won.ts` 한 파일에만 둔다(닫힌 허용 목록 — ESLint 규칙의 유일한 예외, 폐기 항목이 남으면 실패) |
| P-10 | 설계서 §8 한도 상세 "산식(월납 × 배수, **규정 기준일**)" | `Ledger`에 규정 기준일(룰 버전·기준일)이 없다 | 산식은 "월납 {monthlyPremium} × {limitMultiple}배 → 한도 {limitAmount}"으로 쓴다. 표시 문자열일 뿐 계산하지 않는다. 기준일은 **CR-3** |
| P-11 | 설계서 §8 "소진 바(%)" | `utilization`은 문자열 `"60.0%"`다(한도 0이면 없다) | 문자열을 숫자로 바꾸지 않는다. CSS 사용자 속성 `--util: 60.0%`(CSSOM 설정 — CSP `style-src 'self'`와 맞는다)로 너비를 정하고, 접근성은 `role="img"` + `aria-label`(문자열 그대로) |
| P-12 | ③ "모든 금액 옆에 상태 라벨" | 한도 원장 금액(한도액·누적·잔여)과 라인 금액에는 월 상태가 없다 | 라벨 근거는 세 종류다(④ `AmountBasis`). 월 금액은 월 상태, 라인 금액은 그 라인이 속한 달의 상태, 원장 금액은 "원장 기준"(사실 값 표식). 근거 없는 금액은 타입 오류다 |

P-1 상세 — 참고 구현과 지시문이 다른 곳:

| 항목 | ga-disclosure Phase 7 | 지시문 | 계획 |
|---|---|---|---|
| 모듈 경로 | `disclosure-web/` | `web/` | 지시문 |
| 패키지 관리자 | npm(`package-lock.json`, `npm ci`) | pnpm(`--frozen-lockfile`) | 지시문 |
| 생성 클라이언트 | 커밋하지 않는다. 잠금 파일(계약·생성물 SHA-256)만 커밋 | 생성물을 커밋 + 해시 고정 | 지시문. 생성물과 잠금 파일 둘 다 커밋, 빌드가 다시 생성해 바이트를 대조한다(③) |
| 서버 상태 캐시 | 없음(요청 → 응답 → 그리기) | TanStack Query 류 | 지시문 |
| 취약점 기록 | `npm audit` 대신 OSV 조회 | `pnpm audit` 기록 | 지시문(기록만, 올림 없음) |
| JS 번들 상한 | 없음(폰트 바이트 상한 4,335,043만 — Phase 8) | gzip 초기 로드 상한 | Q3 |

## ② 스택·버전 (2026-10-17 레지스트리 조회 — 1단계에서 다시 확인해 고정)

| 영역 | 선택 | 판 | 비고 |
|---|---|---|---|
| 런타임 | Node LTS(Krypton) | 24.22.0 (2026-10-14) | node-gradle이 내려받아 고정. ga-disclosure는 24.21.0 |
| Gradle 플러그인 | `com.github.node-gradle.node` | 7.1.0 | ga-disclosure와 같은 판. pnpm 태스크 지원 |
| 패키지 관리자 | pnpm | 12.14.0 | `--frozen-lockfile`, `.npmrc`: `save-exact`·`engine-strict`·`strict-peer-dependencies`, 의존성 설치 스크립트 0 |
| 화면 | react·react-dom | 19.3.1 | |
| 라우터 | react-router(라이브러리 모드) | 8.4.0 | ga-disclosure와 같다. 피어 react ≥ 19.2.7 |
| 서버 상태 | @tanstack/react-query | 5.104.3 | |
| 클라이언트 | openapi-fetch / openapi-typescript | 0.17.0 / 7.13.0 | 생성기 피어 `typescript ^5.x` |
| 언어 | typescript | 5.9.3 | Q15 |
| 빌드 | vite / @vitejs/plugin-react | 8.3.4 / 6.1.2 | Vite 8 엔진 Node ≥ 22.12 |
| 시험 | vitest / jsdom / @testing-library/react | 5.0.5 / 30.1.3 / 16.3.3 | |
| E2E | @playwright/test / @axe-core/playwright | 1.64.1 / 4.14.0 | Chromium·WebKit(Q10) |
| 린트 | eslint / typescript-eslint | 10.13.0 / 8.71.2 | 커스텀 규칙은 저장소 안 로컬 플러그인(④) |

차트 라이브러리·폰트·아이콘 패키지는 없다. 아이콘은 인라인 SVG 컴포넌트(외부 자원 0)로 둔다.

## ③ 모듈·빌드 배선

- **Gradle 하위 프로젝트 `:web`**(`web/build.gradle.kts`):
  - node-gradle(pnpm)으로 감싼다.
  - 산출물은 `web` jar의 `classpath:/portal-web/` 아래로 들어간다. `portal-app`이 `runtimeOnly(project(":web"))` — bootJar 하나(⑧ 단일 아티팩트).
  - 모든 태스크가 원천·계약·락·설정 파일을 입력으로 선언한다(`verifyTestInputs` 확장 — CLAUDE.md "소스셋 밖 파일을 읽는 시험").
- **태스크**:
  - `clientCheck`: 계약 → `web/src/gen/portal-api.ts`(**커밋**). `contracts-client.lock.json`에 계약 SHA-256·생성물 SHA-256·생성기 판을 둔다.
    - 빌드가 다시 생성해 커밋본과 바이트를 대조하고, 같은 실행에서 두 번 생성해 결정성도 본다.
    - 계약이 1바이트 바뀌면 잠금 불일치로 실패한다(C1).
    - 갱신은 계약을 바꾼 커밋에서 `clientLock`으로만 한다.
  - `webTypecheck`(`tsc --noEmit`), `webLint`, `webTest`(Vitest, JUnit XML), `webLicenses`(Q13), `webBuild`(`vite build` → `scan-dist` → `bundle-size`).
  - `check`는 앞의 다섯을 포함한다. `build`는 `webBuild`를 거쳐 jar에 넣는다.
  - `webAudit`: `pnpm audit --json` → `build/web/audit.json`. 기록만 하고 결과로 실패하지 않으며, CI 아티팩트에 싣는다.
- **산출물 검사(`scan-dist`)**:
  1. HTML: 인라인 스크립트·인라인 스타일·`on*` 속성 0, 참조는 `/assets/`만, 외부 URL 0.
  2. CSS: `url()`은 `/assets/`만, `@import` 0.
  3. 소스맵 0, 시험 원천 0.
  4. **금액 연산 정규식**(④-c).
- **번들 크기(`bundle-size`)**: Vite 매니페스트로 진입점의 정적 import 닫힘을 따라가 gzip(-9) 크기를 더한다. 초기 로드·경로별 지연 청크를 `build/web/bundle-size.json`에 쓰고, 상한(Q3)을 넘으면 실패한다.
- **검증 메타데이터**: node-gradle 플러그인과 그 의존성, Node 배포본이 새로 들어온다. 빈 `GRADLE_USER_HOME`에서 락·`verification-metadata.xml`을 다시 만들고, diff(추가만, 제거 0)를 보고서에 싣는다.
  - Node 배포본 저장소는 `settings.gradle.kts`의 `exclusiveContent`(nodejs.org dist, ivy)다. 프로젝트 저장소 금지 규칙을 지킨다.
- **평문 스캔**: `scanPlaintextLeaks`의 대상에 `web/build/web/dist/**`와 E2E 산출물(`web/build/e2e/**`)을 더한다.

## ④ 금액 규약의 강제 (C2·C3)

**a. 타입.**
- `clientCheck`의 생성 단계는 `openapi-typescript`의 `transform` 훅을 쓴다. 대상은 `$ref`가 `#/components/schemas/Won`·`NonNegativeWon`인 스키마다.
  - 이 둘을 `import("../money/won").Won`으로 바꾼다.
  - `Won`은 `{ readonly [wonBrand]: true }`인 불투명 타입이다. 런타임 값은 JSON 숫자 그대로라 변환 비용이 없다.
- 그 결과:
  - `gross + adjustment`는 TS2365 컴파일 오류가 된다.
  - JSX `{m.gross}`도 `ReactNode`가 아니라서 컴파일 오류가 된다.
  - 금액을 화면에 내는 길은 `<Amount>`뿐이다.

**b. ESLint 커스텀 규칙 `portal/no-money-arithmetic`**(로컬 플러그인 `web/eslint/`, 타입 정보 사용):
- 피연산자 중 하나라도 `Won`이면 다음을 막는다.
  - 산술(`+ - * / % **`, 단항 `- +`, `++ --`, 복합 대입)
  - 비교(`< > <= >= == === != !==`)
  - `Math.*` 인자
  - `Number()`·`parseInt`·`parseFloat`·`BigInt`·`String()`
  - 템플릿 문자열 보간
  - `Array.prototype.sort` 비교 함수 안의 `Won`
- 예외는 `src/money/won.ts` 하나다(닫힌 목록 — 파일이 없거나 쓰이지 않으면 규칙 시험이 실패한다).
- 같은 플러그인에 다른 규칙이 셋 있다.
  - `portal/no-raw-network`: `fetch`·`XMLHttpRequest`·`WebSocket`·`EventSource`·`sendBeacon`·`serviceWorker`를 막는다. `openapi-fetch` import는 `src/api/client.ts`만 할 수 있다.
  - `portal/no-browser-storage`: `localStorage`·`sessionStorage`·`indexedDB`·`caches`·`document.cookie`·`cookieStore`를 막는다.
  - `portal/no-hangul-literal`: 한글 문자열 리터럴은 `messages.ko.json` 밖에서 쓸 수 없다(상태 라벨 = 데이터, Q1).
- 이 규칙들은 RuleTester 시험으로 검증하고, 위반·통과 예시를 둘 다 둔다.

**c. 번들 스캔.**
- 생성기가 계약에서 `Won` 속성 이름의 닫힌 목록을 만든다: `gross`·`net`·`amount`·`clawbackOffset`·`adjustment`·`withholdingTax`·`paidAmount`·`payable`·`receivableOffset`·`carriedReceivable`·`limitAmount`·`accumPaid`·`remaining`·`cutTotal`·`deferredTotal`·`limitCutAmt`·`monthlyPremium`·`expected`·`held`·`accumAfter`·`limitAfter`.
- 앱 청크(라이브러리 청크는 `manualChunks`로 분리)에서 다음 정규식을 찾는다.
  - `\.(이름)\s*[-+*/%<>]`
  - `[-+*/%<>]\s*[\w$]+\.(이름)\b`
  - `Math\.\w+\([^)]*\.(이름)\b`
  - `Number\([^)]*\.(이름)\b`
- 0건이어야 한다. TS·ESLint를 형변환으로 우회한 산술(`(m.gross as unknown as number) + 1`)은 이 단계가 잡는다(W6).

**d. `<Amount>`(C3).**
- props는 `{ value: Won; basis: AmountBasis }`이고 둘 다 필수다.
- `AmountBasis`는 판별 유니온이다.
  - `{ kind: 'month'; status; netBasis?; part: 'gross' | 'net' | … }`
  - `{ kind: 'line'; monthStatus }`
  - `{ kind: 'ledger' }`
  - `{ kind: 'payout' }`
- 렌더 결과는 항상 `금액 + 라벨`이다. 라벨 문자열은 `messages.ko.json`에서 온다(⑤).
- 형식은 `Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 })`에 `원`을 붙인다.
- 부호는 `Intl`이 표시한다. 화면은 부호를 판단하지 않는다.

## ⑤ 상태 라벨 표 (초안 — `messages.ko.json`, 최종판은 보고서)

**월 금액**(`status × netBasis` 16칸 — 계약 열거 4 × (3 + 없음). Vitest 전수, C4):

| status | netBasis | 세전(gross) 라벨 | 세후(net) 표시 |
|---|---|---|---|
| PROVISIONAL | 없음 | 예상 | 숨김 — "세후는 야간 산출 후 표시"(§6.2) |
| ESTIMATED | ESTIMATE | 예상 · 기준 {estimateAsOf} | 예상 |
| CONFIRMED | ESTIMATE | 확정 | **예상**(심사 Q2 — 잠정 명세 값) |
| CONFIRMED | 없음 | 확정 | 숨김 — "세후는 지급 시 확정"(Q2) |
| CONFIRMED | CONFIRM | 확정 | 확정 |
| PAID | PAYOUT | 지급완료 · {paidAt 날짜} | 지급완료(`paidAmount`) |
| 그 밖의 9칸(예: PROVISIONAL + PAYOUT) | | 상태 라벨 그대로 | 숨김 — 계약상 오지 않는 조합. 세후를 보이지 않는 쪽으로 실패한다 |

**그 밖의 라벨**:

| 대상 | 규칙 |
|---|---|
| `monthDataStatus` | `DRIFT`·`REBUILDING` → "확인 중" 배지 + 그 달 금액 흐리게. AA 대비 4.5:1은 유지한다(색만 연하게, 점선 밑줄) |
| `dataStatus` 칩 | `OK` → "기준 {MM-DD HH:mm}". `SYNC_DELAYED` → "동기화 지연 — 기준 {asOf.at}". `asOf.at`이 없으면(seq 0) "동기화 전". 화면의 여러 응답은 **나쁜 쪽**으로 합친다: 하나라도 지연이면 지연, `asOf`는 가장 작은 seq(정수 비교 — 금액 아님) |
| 정정만 있는 달(Q3) | 조건: `CONFIRMED` + `byType` 비어 있음 + `adjustment` 0 아님(`isZero`). 문구: "정정 {adjustment} — 지급 반영은 정산팀 확인". `<Amount basis=month>` |
| 라인 한도 판정 | `limitCutAmt` 0 아님이면 "한도 초과 삭감 {limitCutAmt}". `limitCutPolicy = DEFER_AFTER_FY`이면 보조 문구 "(사규: 초년도 후 이연 — 현재 이연 스케줄 없음)"을 붙인다(Q4). `CUT`·그 밖이면 보조 문구 없음 |
| 원장 | `stage`: OK "정상" · WARN "주의" · CRIT "경고" · FULL "소진".<br>`flags`: CANCELLED "해약" · LAPSED "실효" · OVERDUE "납입 지연" · REDUCED "감액".<br>`cutTotal`·`deferredTotal`: 0이 아닌 것만 "한도 초과 삭감", `deferredTotal` 쪽에만 Q4 보조 문구.<br>`history.kind`: POST "전기" · REBASE "재산정" |
| 라인 상세 분급·채권 | 분급 SCHEDULED "예정" · RELEASED "이번 달 지급 대상" · HELD "보류" · CANCELLED "취소"(§6.2).<br>채권 OPEN "미상계" · OFFSET "상계 중" · CLOSED "종결" · WRITTEN_OFF "대손".<br>HELD에 "다시 지급" 뉘앙스의 문구를 쓰지 않는다(§6.4 — 문구 확정은 Phase 5) |
| 취소 짝(Q6) | 같은 달: 원본 "취소됨"으로 접고 음수 라인과 한 묶음.<br>다른 달: 원본 쪽 "취소됨 → {ym}"과 취소 쪽 "취소 ← {ym}" 배지 + 상대 달 링크 |
| 수수료 유형 | `commType` 값별 이름(FY_COMM "초년도", INCENTIVE "시책", CLAWBACK "환수", RENEWAL "유지", DEFERRED "분급", ADJUSTMENT "정정"). 모르는 값은 코드 그대로 보이고 문구 누락 시험이 잡는다 |

`messages.ko.json`과 계약의 열거는 **양방향**으로 대조한다(Vitest). 계약의 모든 열거 값에 라벨이 있어야 하고, 라벨 쪽에만 있는 키(폐기)도 실패다. `commType`은 계약에서 열거가 아니므로, 알려진 목록을 설계서 §4.2에서 읽어 대조한다.

## ⑥ 화면·경로·데이터

| 경로 | 화면 | 호출 | 비고 |
|---|---|---|---|
| `/` | 홈 | `/me` · `/me/earnings/{이번 달}` · `/me/limits?status=ALL` | `/me`가 관문이다 — `/me` 404만 "등록되지 않은 사용자"다(같은 404 바이트라 다른 404는 "없음"으로 읽는다). 이번 달 카드는 실수령 큰 숫자(세후가 숨김이면 세전) · 세전 · 환수 상계 · 원천세 · 지급예정일 D-n(`payoutExpectedOn`, 없으면 줄 생략) · 상태 라벨. 주의 계약 N건(`stage ≠ OK`) · 납입 지연/실효 M건(플래그) → `/limits`. 비활성 진입점: 분급 타임라인 · 시뮬레이션 · 알림함 · 문의 |
| `/statements`, `/statements/:ym` | 월별 명세 | `/me/earnings?from={ym−35}&to={ym}`(월 목록) · `/me/earnings/{ym}` · `/me/earnings/{ym}/lines?size=200&page=…` | 월 선택은 행이 있는 달만 보인다. 유형별 합계(`byType`) → 라인 목록(접기 Q6·Q12, 클라이언트 페이지 20). 지급 런 값(`receivableOffset`·`carriedReceivable`·`payable`)은 **PAID 달에만** 지급완료 라벨로 보인다. 예상 이월은 미표시(Q5) |
| `/lines/:calcId` | 라인 상세 | `/me/earnings/lines/{calcId}` | 근거(`rate`·`payoutRate`·`installmentNo` — 있을 때만, 문자열 그대로), 한도 판정, 계약 요약(가린 증권번호, 상품명 또는 `productKey`, 피보험자 가림), 분급·채권, 원장 링크. 문의 버튼은 비활성 |
| `/limits` | 한도 현황 | `/me/limits?status=ALL` | 필터 주의/전체는 화면 필터다(문자열 비교). 카드: 상품(없으면 `productKey`) · 피보험자 · 체결일 · 소진 바(P-11) · 잔여 · 초년도 종료 D-n · 플래그 배지 |
| `/limits/:ledgerId` | 한도 상세 | `/me/limits/{ledgerId}` | 산식(P-10) · 타임라인(`history`, `sourceSeq` 순 그대로) · 삭감/이연 · 플래그. "해약 시 영향" 버튼은 비활성(Phase 5) |

- **날짜 계산**(D-n, 이번 달, `ym−35`): `src/time/` 한 모듈이 맡는다. 금액이 아니라 날짜다. 기준 시각은 브라우저 시계, 시간대는 `Asia/Seoul`로 명시한다.
- **캐시**(TanStack Query):
  - 401·404·400에서는 재시도하지 않는다. 그 밖의 5xx·네트워크 오류는 1회 재시도한다.
  - `staleTime`은 30초다. 304는 브라우저 HTTP 캐시가 처리한다(Phase 3 ETag — 화면 코드 없음).
- **오류 화면**: 401 → "다시 로그인"(⑦). `/me` 404 → "등록되지 않은 사용자 — 소속 GA에 문의". 500 → "잠시 후 다시"(마지막으로 받은 값은 그대로 보인다 — §3.4).

## ⑦ 인증·토큰 (Q1)

- **부트스트랩** `src/boot/token.ts`(React보다 먼저 실행):
  1. `location.hash`에서 `token=` 하나를 읽는다. 형식이 JWT 세 조각이 아니면 버린다. 서명 검증은 서버가 한다.
  2. 바로 `history.replaceState(null, '', pathname + search)`로 프래그먼트를 지운다. 프래그먼트는 요청에 실리지 않으므로 서버 로그·Referer에도 남지 않는다. `Referrer-Policy: no-referrer`는 이중 방어다.
  3. 토큰은 모듈 클로저(메모리)에만 둔다. `openapi-fetch` 미들웨어가 `Authorization: Bearer`로 붙인다.
- 토큰이 없거나 401을 받으면 메모리를 비우고 "다시 로그인" 화면을 보인다. 문구: "로그인이 만료되었습니다 — 앱에서 다시 열어 주세요".
  - 포털에는 로그인 화면도, IdP 주소도 없다. 웹뷰 호스트가 새 토큰과 함께 다시 연다.
  - 버튼은 "다시 시도"(`location.reload()` — 호스트가 프래그먼트를 다시 넣는 경로) 하나다.
- **새로고침하면 토큰이 사라진다**(메모리만). 웹뷰는 호스트가 다시 넣는다. 개발 브라우저는 `.state/` 파일의 로그인 URL을 다시 연다(⑨ 로컬 실행).

## ⑧ 서빙·헤더 (Q2·Q6)

- **체인 배치**: `portal-app` `web.WebAssetsConfiguration`이 화면 체인(`@Order(5)` — `/api` 체인 1과 나머지 denyAll 10 사이)을 둔다.
- **경로 판정**: `PathPatternRequestMatcher`(MVC와 같은 파서)로만 한다. `GET`만 받는다.
  - `/`, `/statements`, `/statements/*`, `/lines/*`, `/limits`, `/limits/*` → `index.html`(`Cache-Control: no-store`)
  - `/assets/*` → 해시 이름 자원(`public, max-age=31536000, immutable`)
  - 나머지는 지금처럼 같은 404다.
- 화면 응답은 `RouterFunction`(`@RestController` 아님)이 낸다. `ApiLayerRulesTest`·`AuthzMatrixTest`의 대상(`/api`)이 아니다.
- **화면 응답 헤더**:
  - `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`
  - `Referrer-Policy: no-referrer`
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - 세션·쿠키·CSRF 토큰은 없다.
- **API 응답 헤더**는 Phase 3 그대로다(시험이 바뀌지 않음을 단언한다).
- **시험 `WebAssetsIT`**(통합): 헤더 집합 전수, SPA 경로 목록 ↔ 화면 라우터 경로 목록 양방향 대조(`web/src/routes.ts`를 입력으로 선언), 경로 탈출·다른 메서드 404, `/api`에 CSP가 없음.

## ⑨ E2E 하네스·시나리오 (Q8·Q10·Q11)

**하네스.**
- `./gradlew :web:e2e` → `web/e2e/run.mjs`가 다음을 차례로 띄운다.
  1. Java 하네스 `:portal-app:webE2eHarness`(통합 시험 소스셋 JavaExec — Phase 3 `TestIssuerMain`과 같은 자리). 내용:
     - Testcontainers PostgreSQL(`postgres-image` digest, `init-roles.sql`, 볼륨 없음)
     - 시험 발급기 넷(테넌트별 `iss`)
     - 테넌트별 `StubFeedServer`
     - 등록 SQL 파일(테넌트·`identity_link` — 허구 subject)
     - 스트림 파일을 계약 스키마로 검증(아니면 시작 실패)
     - `localhost` 제어 포트(`POST /control/{tenant}/head/{seq}`, `/control/{tenant}/fail-on|off`)
  2. 포털 bootJar(`PORTAL_INGEST_ENABLED=true`, 폴링 5초, `sync_delay_seconds` 10)
  3. Playwright
  4. 내리기(실패해도 내린다). Docker가 없으면 하네스가 실패한다 — 스킵 없음.
- **비밀**: 토큰·subject는 `web/build/e2e/.state/`(권한 600)에만 둔다. 명령 인자·환경 변수·출력에 싣지 않는다.
- **대기**: 단계마다 머리를 올린 뒤 `/api/v1/me`의 `asOf.seq`가 그 seq가 될 때까지 기다린다(하네스 쪽 HTTP — 브라우저 밖).

**테넌트·스트림**(Q11):

| 테넌트 | 스트림 | 쓰는 곳 |
|---|---|---|
| GA01 | A-1 둘째 판(계약 사본 바이트 그대로, seq 1~11) + 이웃 설계사 `A-2002`의 손 스트림(seq 12~, 증권번호 센티널 — 허구) | 홈·명세·라인·한도(삭감 145,000, 해약 플래그). 전이: 머리 5(202608 예상) → 6(확정, 세후 숨김) → 7(지급완료) → 11(202702 예상·순액 0). 같은 테넌트 이웃의 누출 검사 |
| GA02 | 엔진 `estimate_cycle_01_기본`(바이트 그대로) | 한 달의 네 단계: 머리 3 PROVISIONAL → 4 ESTIMATED → 5 CONFIRMED(세후 예상 — Q2) → 6 PAID |
| GA03 | 엔진 E1 `cycle_마감_지급_채권_정정`(바이트 그대로) | 정정만 있는 달 202611(Q3), 채권 OPEN → CLOSED, 음수 순액 |
| GA04 | 포털 손 스트림 `web/e2e/streams/edges.jsonl`(계약 검증 통과, 엔진이 내지 않는 형태 — 출처를 README에 적는다) | 같은 달 취소 짝(접힘), 다른 달 취소 짝(배지·링크), `CUT` 라인, 확정 합계 불일치 → DRIFT(§6.6 — 독 아님) |

**시나리오**(프로젝트 셋: `mobile-android`·`mobile-ios`·`desktop`, `locale: ko-KR`, **`timezoneId: 'UTC'`** — 화면이 KST를 명시하는지 보인다):
- 브라우저 시계는 `page.clock`으로 KST 2026-08-20 / 2027-02-20에 고정한다.
- 홈 → 명세 → 라인 상세 → 한도 → 상세(GA01).
- 전이(GA01·GA02, 머리를 올리며 같은 화면을 다시 본다).
- Q3(GA03), Q6·DRIFT·CUT(GA04).
- `SYNC_DELAYED`(GA01 피드 실패 + 머리 전진 → 10초 뒤 칩).
- 401(만료 토큰·형식 틀린 토큰·토큰 없음), 404(등록되지 않은 subject).
- 단언은 구조·텍스트로만 한다. 스냅샷은 없다(C11).

**검사**(모든 시나리오에 고정 장치로 건다):

| 검사 | 방법 |
|---|---|
| 누출(C6) | 센티널 = 네 스트림의 모든 `policyNo` 원문, 이웃·다른 테넌트의 `agentId`·`calcId`·`ledgerId`·subject, 그리고 토큰 값.<br>대상:<br>• 단계마다 `document.documentElement.outerHTML`<br>• 모든 응답 본문<br>• 콘솔·`pageerror`<br>• 모든 프레임의 URL 이력<br>저장소(`localStorage`·`sessionStorage`·쿠키·IndexedDB 목록·Cache Storage 키)는 **비어 있어야** 한다(센티널 대조보다 강하다).<br>토큰은 요청의 `Authorization` 헤더에만 있어도 된다. `run.mjs`가 끝에 `build/e2e/**`도 센티널·토큰으로 스캔한다 |
| CSP(C8) | 초기화 스크립트의 `securitypolicyviolation` 수집 + 콘솔의 CSP 오류 = 0. 외부 출처 요청 0 |
| axe(C7) | 화면·상태마다 `wcag2a`·`wcag2aa`·`wcag21a`·`wcag21aa` 위반 0. 예외 목록은 비어 있게 시작하고, 쓰이지 않는 예외가 남으면 실패 |
| 터치 44px(C7) | 보이는 모든 대화형 요소(`a[href]`·`button`·`[role=button]`·`select`·`input`)의 `getBoundingClientRect` 폭·높이 ≥ 44 |
| 키보드(C7) | 데스크톱에서 Tab/Shift+Tab/Enter만으로 홈 → 명세 → 라인 → 한도 → 상세를 지난다. 포커스는 늘 보여야 한다(`:focus-visible` 외곽선의 계산된 스타일) |
| 움직임(C7) | `reducedMotion: 'reduce'`에서 모든 요소의 계산된 `transition-duration`·`animation-name`이 0s·none |
| 토큰 저장 0(C10) | 위 저장소 빈 상태 + 부팅 직후 `location.href`에 `token` 0 + `history.length` 이력의 URL에 0 |

## ⑩ 완료 기준 → 시험·주입

주입은 ID 단위로 하나씩 넣어 실패를 확인하고 되돌린다. 각 커밋 본문에 기록한다.

| C | 증명 | 주입(실패해야 한다) |
|---|---|---|
| C1 | `clientCheck`(잠금·결정성), ESLint `no-raw-network` | **W1** 계약 1바이트 변경 / **W2** `src`에 `fetch(` / **W3** `openapi-fetch`를 `src/api` 밖에서 import / **W4** 생성물 손 편집 |
| C2 | TS 불투명 `Won`, ESLint `no-money-arithmetic`(RuleTester + 실제 린트), `scan-dist` 정규식 | **W5** 홈에서 `gross + adjustment`(tsc) / **W6** `Number(m.gross)`(ESLint) / **W7** `m.net > 0`(ESLint) / **W8** 형변환 우회 산술(번들 스캔) |
| C3 | `<Amount>` 필수 `basis`, JSX 직접 렌더 불가 | **W9** `basis` 없는 `<Amount>`(tsc) / **W10** `{m.gross}` 직접 렌더(tsc) |
| C4 | Vitest 16칸 표 + 라벨↔계약 열거 양방향, E2E 전이 GA01·GA02 | **W11** `messages.ko.json`에서 라벨 하나 삭제 / **W12** CONFIRMED+ESTIMATE를 "확정"으로 |
| C5 | 컴포넌트 시험(DRIFT·REBUILDING 흐림·배지, 칩, Q3, Q4, Q6) + E2E GA03·GA04 | **W13** 흐림 클래스 제거 / **W14** 다른 달 짝을 접기 |
| C6 | 누출 고정 장치(⑨) | **W15** 라인 상세에 센티널 증권번호 상수 표시 / **W16** 토큰을 `sessionStorage`에 쓰기(ESLint 끈 줄로 — E2E가 잡는지) |
| C7 | axe·44px·키보드·움직임 | **W17** 대비 위반 색 / **W18** 버튼 높이 36px / **W19** 내비게이션에 `tabindex=-1` / **W20** 감소 설정을 무시하는 전환 |
| C8 | CSP 수집, `scan-dist`, `WebAssetsIT` | **W21** `index.html`에 인라인 `onclick`(번들 스캔·CSP 둘 다) / **W22** CSP에서 `frame-ancestors` 삭제(`WebAssetsIT`) |
| C9 | `bundle-size` | **W23** 큰 라이브러리(예: 전체 locale 데이터) import |
| C10 | E2E 401·404 + 저장 0 | **W24** 401 화면 대신 빈 화면 |
| C11 | 세 프로젝트 × 전 시나리오 | (C1~C10 주입이 세 프로젝트에서 실패) |
| C12 | `./gradlew build` → bootJar 안 `portal-web/index.html`(시험), Phase 0~3 시험 무손상, CI(`build`·`no-docker`·`web-e2e`), 평문 스캔 확장, `pnpm audit` 기록, 락 | **W25** 웹 산출물에 센티널 문자열(평문 스캔) / **W26** `pnpm-lock.yaml`과 다른 `package.json`(`--frozen-lockfile` 실패) |

## ⑪ 단계 (단계마다 시험·주입·설계서 같은 커밋)

1. **골격**:
   - `web/` Gradle 하위 프로젝트, pnpm·락·`.npmrc`, tsconfig(ga-disclosure와 같은 엄격 옵션), 생성기 + `Won` 변환 + 잠금 파일, ESLint 로컬 플러그인 넷 + RuleTester, `scan-dist`, `bundle-size`(상한은 임시로 크게 두고 5단계에서 고정), 라이선스.
   - `portal-app` 화면 체인 + `WebAssetsIT`, 검증 메타데이터 재생성, 평문 스캔 확장.
   - 주입 W1~W8·W21·W22·W25·W26.
2. **틀**: 토큰 부트, API 클라이언트·캐시, 라우터, `<Amount>`, 라벨 표(`messages.ko.json`) + 양방향 대조, 동기화 칩, 401·404·500 화면, 비활성 진입점. 주입 W9~W12.
3. **화면**: 홈·명세(접기)·라인 상세·한도·한도 상세 + 컴포넌트 시험. 주입 W13·W14.
4. **E2E**: 하네스(Java) + `run.mjs` + 고정 장치 + 시나리오 + CI 잡 `web-e2e`. `QueryStatementIT`에 `status=ALL`. 주입 W15~W20·W23·W24.
5. **측정·문서**: 번들 상한 확정(측정표), 설계서 v1.7, `CLAUDE.md` "화면 규약" 절, README(로컬 실행), `web/README.md`, DECISIONS. 상한 주입 W23은 여기서 다시 확인한다.
6. **보고서** → 태그 `phase-4` → PR(병합은 수용 심사 뒤).

## ⑫ 설계서 v1.7·`CLAUDE.md`에서 고칠 곳

- **설계서**:
  - §3.3·§11: `web/` 스택과 판, pnpm, 생성 클라이언트 커밋 + 잠금, PWA → v2(P-6).
  - §6.2: 라벨 표 최종판(⑤). §6.3: `DEFER_AFTER_FY` 문구(심사 Q4).
  - §8:
    - 홈 줄 — 전월 대비·다음 달 분급은 Phase 5·CR-1(Q4), 알림 자리(Q7), "이번 달" 정의(Q16).
    - 한도 산식(P-10), 소진 바(P-11).
    - 라인 접기(Q6·Q12).
  - §9: 토큰 주입·메모리·화면 CSP와 헤더(Q1·Q2).
  - §10: 번들 상한 수치, 홈 콜 `status=ALL`(P-4).
  - §12: Phase 4 범위에서 알림함 제외(P-5). §13: 프론트 행(검사 목록).
- **`CLAUDE.md`**: "화면 규약" 절을 둔다.
  - 금액은 불투명 `Won` — 산술·비교·변환 금지, 형식화는 `src/money/`만.
  - 네트워크는 생성 클라이언트만, 브라우저 저장소 0, 한글 문구는 `messages.ko.json`만, 라벨 없는 금액 없음.
  - 인라인 스크립트·외부 출처 0, 생성물은 `clientLock`으로만.

## ⑬ 계약 요청 목록 (API 변경은 하지 않는다 — Phase 5 계약 개정 후보)

| # | 요청 | 왜 |
|---|---|---|
| CR-1 | `MonthResponse.previous`(선택): 지난달의 `closeYm`·`status`·`gross`·`net?`·`netBasis?` — **원래 값만, 차이 없음** | §8 "전월 대비" — 화면은 차감·비교를 하지 않는다. 두 금액을 나란히 라벨과 함께 보인다 |
| CR-2 | `Line.reversalOfYm`·`reversedByYm`(선택) | 다른 달 짝의 달을 알려고 라인 상세를 한 번 더 부르지 않게(Q12) |
| CR-3 | `Ledger.ruleVersionId`·규정 기준일(선택) | §8 한도 산식의 "규정 기준일"(P-10) |

## ⑭ 하지 않는 것

다음은 하지 않는다.
- 화면의 금액 연산·비교.
- 직접 `fetch`.
- 토큰·응답의 브라우저 저장소 저장.
- 서비스 워커.
- 분급 타임라인·시뮬레이션·알림함·이의제기 화면(진입점만).
- 다크 모드·다국어.
- 스냅샷 시험.
- 외부 CDN·폰트.
- API 변경(⑬).
- 화면 캡처 방지.
- 개발용 토큰 붙여 넣기 화면.
- ga-disclosure 코드 복사(규약만 같다).

## ⑮ 다음 Phase 질문 (보고서에서 다시)

Phase 5:
- 분급 타임라인 차트 라이브러리 1종(번들 상한 안에서 — 후보는 측정 뒤).
- `forecast_scenario_enabled` 토글의 화면 문구.
- 시뮬레이션 2탭의 입력 검증 위치(화면 vs API 400).
- `simulation_log` 레이트 리밋 초과 시 "마지막 결과 재표시"의 출처(로그 행).
- CR-1~3의 계약 개정 시점.
- HELD 문구.
