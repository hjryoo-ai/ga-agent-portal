# Phase 4 보고서 — ga-agent-portal 화면 1차 (지시문 v1.0)

> 지시문 `docs/phase-04-지시문.md` · 계획 `docs/phase-04-계획.md` · 승인 `docs/phase-04-계획승인.md`(2026-10-17, Q1~Q16 권장안, 조건 없음) ·
> 브랜치 `work/phase-4` · PR [#12](https://github.com/hjryoo-ai/ga-agent-portal/pull/12) · 태그 `phase-4`(이 보고서 커밋). **병합은 수용 심사 회신 뒤.**
>
> **2판**(2026-10-22, 태그 `phase-4.1`): 수용 심사 `docs/phase-04-수용심사.md`(조건부 수용) — 조건 A1(정정 금액의 라벨)·Q9 한 줄. 바뀐 곳은 §4.5 표·주입 W29·§7. `phase-4`는 1판(`5bed1f1`) 그대로.

## 1. 커밋

`main` = `e8fd66e`(영문 번역, #11) 위 `work/phase-4`(`main..phase-4`):

| 커밋 | 내용 | 주입 |
|---|---|---|
| `9ba6e39` | Phase 3 수용 심사 보관, 설계서 v1.6.1(F5·F9·§14.1 구현 위치 열·#12·#13), JWKS 장애 지표(F6), DE-19·RM-2·PQ-1 | W0 |
| `8d9962f` | 지시문·계획(Q1~Q16, P-1~12, 라벨 표, 경로, 하네스, 주입 W1~W26, CR-1~3) | — |
| `9230d87` | `main`(#11 영문 번역) 병합 — 설계서 충돌 해소 | — |
| `ff6fd3e` | 승인 보관 | — |
| `4f1f79a` | 1단계 — `web/` 골격, 생성 클라이언트 잠금, 불투명 `Won`, ESLint 규칙, 산출물 스캔, 번들 측정, 라이선스, 같은 출처 서빙 + CSP(`ScreenConfiguration`·`WebScreensIT`), 단일 아티팩트 | W1~W8·W21·W22·W25·W26 |
| `776a74c` | 2단계 — 토큰 부트, API 클라이언트·캐시, 경로, `<Amount value label>`, 라벨 표 16칸, 동기화 칩, 재로그인·미등록·오류 화면 | W9~W12 |
| `00a4f01` | 3단계 — 홈·명세(같은 달 접기)·라인·한도·한도 상세, 금액 규칙의 구조 인식(F3) | W13·W14 |
| `f0f9acf` | 4단계 — E2E 하네스(`WebE2eHarness`)·`run.mjs`·Playwright 세 프로젝트·누출·CSP·axe·44px·움직임·키보드, CI 잡 `web-e2e` | W15~W20·W21b·W24 |
| `5dadd3b` | 5단계 — 번들 상한 127 KiB, `:web:e2eServe`, 설계서 v1.7, `CLAUDE.md` 화면 규약, README·`web/README.md`, DECISIONS P4-1~11, `TODO(confirm#14)` | W23 |
| `c9e7f55` | E2E 요약을 시험마다 한 파일로 + 합계·개수 검사(F16) | W27 |
| `cb270cd` | E2E 센티널 전부를 평문 스캔 센티널에도 + 두 목록 대조(F17) | W28 |
| (이 커밋) | 보고서 | — |

단계마다 로컬 `./gradlew build`(4단계는 `:web:e2e` 포함)가 성공한 뒤 커밋했다(수는 각 커밋 본문).

```
web/            src/boot/token.ts · api/client.ts·queries.ts · money/won.ts·Amount.tsx · labels/labels.ts · messages.ko.json·messages.ts · routes.ts
                app/ App·Layout·router · screens/ Home·Statement·Line·Limits·Limit·fold.ts · ui/ SyncChip·States·EntryPoints·MonthCard·LimitCut·Utilization·fillNodes
                time/ kst·useNow·dday · gen/portal-api.ts(생성물 커밋) · contracts-client.lock.json · eslint/plugin.js(규칙 넷)
                scripts/ client-lock·scan-dist·bundle-size·licenses·audit-record · e2e/ run.mjs·support/·specs/ 6·streams/ 2 · THIRD-PARTY.md
portal-app      screen/ScreenConfiguration(화면 체인 order 5 — CSP·헤더) · verifyBootJarScreens · runtimeOnly(project(":web"))
                integrationTest/ WebScreensIT · web/WebE2eHarness · QueryStatementIT(status=ALL) · IssuerTenantResolver(jwksFailure — F6)
build           scanPlaintextLeaks(웹 산출물·Vitest·E2E 결과, 센티널 키 e2e) · CI 잡 web-e2e·pnpm audit 기록
```

## 2. 테스트 (CI 출처)

CI run [39035626322](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/39035626322)(`cb270cd`, build·no-docker·web-e2e 모두 success). 수는 그 run의 아티팩트(`test-reports` — 결과 XML 78개·Vitest junit, `web-e2e-reports` — junit·요약)를 내려받아 셌다. 기준선은 `main` = `e8fd66e`의 CI run 39028326501 아티팩트(2,655).

| 모듈 | 소스셋 | 건수 | `main` 대비 | 비고 |
|---|---|---:|---:|---|
| `portal-domain` | test | 2,084 | 0 | |
| `portal-projection` | test | 135 | 0 | |
| `portal-ingest` | test | 80 | 0 | |
| `portal-query` | test | 26 | 0 | |
| `portal-infra` | test | 10 | 0 | |
| `portal-infra` | integrationTest | 172 | 0 | |
| `portal-app` | test | 20 | 0 | `IssuerTenantResolverTest`에 JWKS 지표 단언(F6 — 건수 그대로) |
| `portal-app` | integrationTest | 69 | +5 | `WebScreensIT` 5. `QueryStatementIT`는 같은 메서드에 `status=ALL` 측정을 더함 |
| `portal-app` | archTest | 64 | 0 | |
| **Java 합계** | | **2,660** | **+5** | 실패 0, 스킵 0 |
| `web` | Vitest | 70 | +70 | `labels` 18·`lintRules` 14·`messages` 10·`screens` 9·`kst` 4·`token` 4·`fold` 3·`money` 3·`syncChip` 3·`amount` 2 |
| `web` | Playwright | 25 | +25 | 8 시나리오 × 3 프로젝트 + 키보드(데스크톱) — 프로젝트별 8·8·9, 스킵 0 |

- 같은 run의 빌드 검사: `printPlatformArtifacts: 13 files equal to the handoff §6 table, source=remote (Gradle cache)`, `verifyPlatformPins: 13 com.ga.platform:0.2.0 files pinned, equal to the handoff §6 table`, `verifyContractChecksums: contracts/CHECKSUMS and contracts/shared/ga-disclosure/CHECKSUMS match`, `scanPlaintextLeaks: 91 files, 62 forbidden strings, 0 hits`(웹 산출물·Vitest 결과 포함, 값 31 × 원문·16진 — F17로 30 → 31), `verifyTestInputs: 8 test tasks read repository files, all declared`, `verifyUpstreamContract` "envelope-core.schema.json == …ga-disclosure@db82d5d…", **`verifyBootJarScreens: portal-app-0.1.0.jar ⊃ web-0.1.0.jar ⊃ portal-web/index.html + 9 scripts`**, **`licenses ok (257 packages, 7 bundled, 8 dev-only exceptions)`**(linux — macOS 전용 `fsevents`가 빠져 표의 258보다 하나 적다), `:portal-domain`·`:portal-projection`·`:portal-ingest`·`:portal-query:verifySpringFree`, `files containing the token: 0`.
- `no-docker` 잡: "gradle exit code: 1", "result files: 10, with failures/errors: 10, skipped: 0"(Phase 3과 같다 — 웹 모듈은 Docker를 쓰지 않는다), "scanPlaintextLeaks: 10 files, 62 forbidden strings, 0 hits", "token occurrences: 0".
- `web-e2e` 잡: `summary.json`(§4.2·§4.3), `artifact-scan.json` = 금지 문자열 43(센티널) + 토큰 36, hits 0, "files containing the token: 0"(저장소 비밀).

## 3. 완료 기준 증거

| # | 기준 | 증거 | 주입 |
|---|---|---|---|
| C1 | 생성 클라이언트가 계약 해시에 고정, API 호출은 생성 클라이언트만 | `:web:clientCheck`(`scripts/client-lock.mjs` — 계약·생성물 SHA-256, 두 번 생성해 결정성), ESLint `no-restricted-globals`(`fetch`·XHR·WebSocket·EventSource)·`no-restricted-imports`(`openapi-fetch`는 `src/api/client.ts`만) — `lintRules.test.ts` › `reports fetch, browser storage and service worker`·`openapi-fetch is imported only in src/api/client.ts` | W1~W4 |
| C2 | 금액 연산 0 | TS 불투명 `Won`(산술·JSX 렌더 = 컴파일 오류), ESLint `portal/no-money-arithmetic`(이름 또는 구조 표식 — F3) — `lintRules.test.ts`(위반·허용 사례, 실제 `api.GET` 응답 타입 `moneyMapped`, 예외 목록 닫힘 `the exemption list is closed…`), `money.test.ts` 3, `scan-dist.mjs`(앱 청크의 금액 속성 21개 주변 연산자) | W5~W8 |
| C3 | 라벨 없는 금액은 타입 오류 | `<Amount value: Won, label: StatusLabel>` — `StatusLabel`은 `labels.ts`만 만든다. `amount.test.tsx` 2, `screens.test.tsx` › `line detail, limits and limit detail label every amount…`(렌더된 모든 `.amount`에 비지 않은 라벨) | W9·W10 |
| C4 | 라벨 표 16칸 + 전이 E2E | `labels.test.ts` › `covers all 16 combinations`(기대값을 따로 적은 표), `messages.test.ts`(계약 열거 ↔ 라벨 키 양방향), E2E `ga01-a1` › `202608: 예상 → 확정(세후는 지급 시 확정) → 지급완료…`·`ga02-transitions` › `네 단계 전이와 세후 표시`(PROVISIONAL → ESTIMATED → CONFIRMED(세후 예상) → PAID, 피드 머리를 단계별로) | W11·W12 |
| C5 | DRIFT 흐림·칩·Q3·Q4·Q6 | `screens.test.tsx` › `DRIFT and REBUILDING…`·`OK months are not dimmed`·`adjustment-only confirmed month…`·`statement: same-month pair folded, cross-month partners badged…, DEFER_AFTER_FY wording`, `syncChip.test.ts` 3, `fold.test.ts` 3; E2E `ga04-edges` › `DRIFT 달, 취소 짝, CUT`·`ga03-adjustment` › `정정만 있는 달`·`ga01-a1` › `동기화 지연 칩…` | W13·W14 |
| C6 | 누출 0(DOM·응답·콘솔·URL·저장소) | E2E 고정 장치(`e2e/support/fixtures.ts` — 모든 시험에 자동): 단계마다 DOM, 모든 응답 본문, 콘솔·`pageerror`, 주소·요청 URL, 저장소 다섯 종 — §4.3. 끝나면 `run.mjs`가 `build/e2e` 전체를 토큰·센티널로 스캔(§4.3) | W15·W16 |
| C7 | axe AA 0, 44px, 키보드, 움직임 | 고정 장치 `portal.check()`(axe `wcag2a/aa·wcag21a/aa` 위반 0, 보이는 상호작용 요소 ≥ 44px, 감소 설정에서 전환·애니메이션 0) — 모든 화면 상태마다(§4.2), `keyboard.spec.ts` › `키보드만으로 홈 → 명세 → 한도`(데스크톱, 보이는 포커스) | W17~W20 |
| C8 | CSP 위반 0, 인라인·`eval` 0, 헤더 집합 | 고정 장치(`securitypolicyviolation` + CSP 콘솔 오류 = 0, 외부 요청 0 — §4.4), `scan-dist.mjs`(인라인 스크립트·스타일·이벤트 속성, `/assets/` 밖 참조), `WebScreensIT` 5(화면 경로 전부 같은 문서·헤더 집합, `/assets` immutable, 순회·없는 자산 404, `/api` 헤더 불변, `SCREEN_PATHS` = `routes.ts` 양방향) | W21·W21b·W22 |
| C9 | 번들 상한 | `scripts/bundle-size.mjs`(`webBuild` 안 — 초기 로드 gzip ≤ 127 KiB), §4.1 | W23 |
| C10 | 401·404 화면, 토큰 저장 0 | E2E `auth` › `401과 404 화면`(토큰 없음·형식 오류·만료(서버 401) → 재로그인, 링크 없는 subject → 미등록), `token.test.ts` 4, 저장소 검사(고정 장치) | W24 |
| C11 | 세 뷰포트 전 시나리오, 스냅샷 없음 | `playwright.config.ts` 프로젝트 셋(Pixel 7/Chromium, iPhone 15/WebKit, Desktop Chrome) — 8 시나리오 × 3 + 키보드(데스크톱) = 25, 스킵 0. `toHaveScreenshot`·`toMatchSnapshot` 0(구조·텍스트 단언) | (C1~C10 E2E 주입) |
| C12 | 단일 아티팩트, Phase 0~3 무손상, CI, 평문 스캔, 주입, 락·audit | `verifyBootJarScreens`(부트 jar ⊃ 웹 jar ⊃ `portal-web/index.html`·스크립트, `.vite/`·맵 없음), §2, `scanPlaintextLeaks`(웹 산출물·Vitest·E2E 결과), `pnpm --frozen-lockfile`, `webLicenses`, `webAudit`(§4.8) | W25·W26 |

### 주입 기록 (ID 단위 30건)

각각 단독으로 넣고 해당 검사를 돌려 실패를 본 뒤 되돌렸다. 실패 문구는 단계 커밋 본문에 있다. E2E 주입은 `PORTAL_E2E_PLAYWRIGHT_ARGS`로 해당 명세만 돌렸다.

| ID | 기준 | 주입 | 실패한 곳 |
|---|---|---|---|
| W0 | (F6) | `jwksFailure` 호출 제거 | `IssuerTenantResolverTest` 1/10 |
| W1 | C1 | 계약 끝에 1바이트 | `clientCheck` "contract changed … lock file was not updated" |
| W2 | C1 | `App.tsx`에 `fetch(` | `webLint` `no-restricted-globals` |
| W3 | C1 | `App.tsx`에서 `openapi-fetch` import | `webLint` `no-restricted-imports` |
| W4 | C1 | 생성물 손 편집 | `clientCheck` "does not match the lock file" + "differs from a fresh generation" |
| W5 | C2 | 홈에서 `m.gross + m.adjustment` | `webTypecheck` TS2365 |
| W6 | C2 | `Number(m.gross)` | `webLint` `portal/no-money-arithmetic` |
| W7 | C2 | `m.net > m.gross` | `webLint` `portal/no-money-arithmetic`(있음 확인 `!== undefined`도 잡혀 `present()`로 옮겼다 — 규칙은 좁히지 않음) |
| W8 | C2 | `(m.gross as unknown as number) + 1`(eslint-disable) | `webBuild` 스캔 "money arithmetic near .gross: .gross+"(첫 실행에서 `=>`의 `>`를 잡은 거짓 양성 → 연산자 패턴 수정, 대조 빌드 통과) |
| W9 | C3 | 라벨 없는 `<Amount value={v} />` | `webTypecheck` TS2741 "Property 'label' is missing" |
| W10 | C3 | JSX에 `{s.gross}` | `webTypecheck` TS2322 "Type 'Won' is not assignable to type 'ReactNode'" |
| W11 | C4 | `messages.ko.json`에서 플래그 `REDUCED` 삭제 | `messages.test.ts` "flags" |
| W12 | C4 | CONFIRMED + ESTIMATE 세후를 "확정"으로 | `labels.test.ts` "CONFIRMED + ESTIMATE" |
| W13 | C5 | `Amount`의 흐림 클래스 제거 | `amount.test` "marks uncertain amounts", `screens` "DRIFT and REBUILDING" |
| W14 | C5 | 다른 달 짝을 같은 달처럼 접기 | `fold.test` "does not fold cross-month partners", 명세 화면 시험 |
| W15 | C6 | 라인 상세에 센티널 증권번호 상수 | E2E DOM 누출 검사 "sentinel POL…"(데스크톱 ga01) |
| W16 | C6 | 토큰을 `sessionStorage`에(ESLint 끈 줄) | E2E 저장소 검사 "session: 1" |
| W17 | C7 | `--muted: #a7adb3` | axe `color-contrast`(동기화 칩·상태 배지·캡션·라벨) |
| W18 | C7 | `.button`·`.link-button` 높이 36px | 44px 검사 `a "월별 명세 보기" 125x36` |
| W19 | C7 | "한도 현황" 탭에 `tabIndex -1` | 키보드 명세 "could not reach 한도 현황 with Tab" |
| W20 | C7 | 감소 설정 블록 없는 `.card` 전환 | 움직임 검사 |
| W21 | C8 | `index.html`에 `onclick` 속성 | `scan-dist` "event handler attribute" |
| W21b | C8 | `main.tsx`에 `new Function()` | E2E CSP "CSP-VIOLATION script-src eval"(인라인 처리기는 W21이 빌드에서 막아 브라우저까지 가지 않는다) |
| W22 | C8 | CSP에서 `frame-ancestors` 삭제 | `WebScreensIT` 2/5 |
| W23 | C9 | 진입 청크에 `react-dom/server` 정적 import | `webBuild` "initial load 178573 B gzip exceeds the cap 130048 B" |
| W24 | C10 | 재로그인 화면 대신 빈 화면 | E2E `auth` "screen-relogin" 없음 |
| W25 | C12 | `messages.ko.json`에 센티널 이름 | `scanPlaintextLeaks` "web/build/web/dist/assets/index-*.js: 허…" |
| W26 | C12 | 락 갱신 없이 `package.json`의 `openapi-fetch` 0.16.0 | `pnpmInstall` `ERR_PNPM_OUTDATED_LOCKFILE` |
| W27 | C6·C7(F16) | 요약 파일 이름을 프로젝트마다 상수로 | `run.mjs` "E2E summaries: 1 leak and 1 axe files for 4 passed tests", `:web:e2e` FAILED(데스크톱 ga01로 좁힘) |
| W28 | C6·C12(F17) | 더한 센티널을 다시 뺌 | `run.mjs` "1 E2E sentinel(s) missing from pii-sentinels.properties (key e2e)", `:web:e2e` FAILED |
| W29 | C4(2판 — A1) | `MonthCard` 정정 행의 라벨을 `monthLabel`(월 상태)로 되돌림 | `screens.test.tsx` "PAID month with ordinary lines and an adjustment…" `expected '지급완료' to be '지급 반영 미정'`, 73건 중 1 실패 |

## 4. 지시문 §5 추가 항목

### 4.1 번들 크기 (`build/web/bundle-size.json`, gzip 바이트)

CI run 39035626322의 `bundle-size.json` — 39033950394(`5dadd3b`)·로컬 측정과 바이트까지 같다.

| 초기 로드 | gzip B |
|---|---:|
| `index.html` | 375 |
| `assets/index-*.js`(진입) | 2,822 |
| `assets/index-*.css` | 1,683 |
| `assets/dday-*.js`(홈·공통 — 정적 import) | 4,663 |
| `assets/vendor-*.js`(React·react-dom·라우터·Query·openapi-fetch) | 108,238 |
| `assets/rolldown-runtime-*.js` | 368 |
| **합계** | **118,149** |

| 경로 청크(지연 로드) | 파일 | gzip B |
|---|---|---:|
| `/statements`, `/statements/:ym` | `StatementScreen`·`LimitCut` | 2,264 |
| `/lines/:calcId` | `LineScreen`·`LimitCut` | 1,476 |
| `/limits` | `LimitsScreen` | 978 |
| `/limits/:ledgerId` | `LimitScreen`·`LimitsScreen`·`LimitCut` | 2,278 |

상한 = 측정 118,149 B + 10%를 KiB로 올림 = **127 KiB(130,048 B)**(계획 Q3). 경로 청크는 상한 밖이고 표로만 남긴다. 측정 경과: 1단계 67,973 → 2단계 113,794(React·라우터·Query) → 3단계 118,135 → 5단계 118,149. W23(초기 178,573 B)이 상한에서 실패했다.

### 4.2 axe 결과 요약

CI run 39035626322 `web-e2e-reports/axe/`(시험마다 한 파일)·`summary.json`. 규칙 집합 `wcag2a`·`wcag2aa`·`wcag21a`·`wcag21aa`, `portal.check()`가 부를 때마다(화면 상태마다) 실행:

| 화면 상태 | 프로젝트별 | 통과 규칙 수 |
|---|---:|---:|
| GA01 홈 — 예상(PROVISIONAL)·예상(ESTIMATED)·지급완료 | 3 × 3 | 20 |
| GA01 동기화 지연 | 1 × 3 | 20 |
| GA01 명세·라인 | 2 × 3 | 20 |
| GA01 한도(FULL 필터·전체) | 2 × 3 | 21 |
| GA01 한도 상세 | 1 × 3 | 23 |
| GA02 머리 3·4·5·6(네 단계 전이) | 4 × 3 | 20 |
| GA03 정정만 있는 달 | 1 × 3 | 20 |
| GA04 홈 DRIFT | 1 × 3 | 20 |
| GA04 명세 202608(같은 달 짝)·202609(다른 달 짝) | 2 × 3 | 21·20 |
| 재로그인 — 토큰 없음·형식 오류·만료 | 3 × 3 | 8 |
| 미등록 사용자 | 1 × 3 | 6 |
| **합계** | **63**(21 × 3) | |

**위반 0, 미완(incomplete) 0** — 세 프로젝트 모두. 통과 규칙 수가 적은 상태(재로그인·미등록)는 제목·문단·버튼 하나뿐인 화면이다. 같은 상태에서 44px 검사와 움직임 감소 검사도 돌았다(실패 0). 키보드 시험(데스크톱)은 axe 상태를 따로 세지 않는다 — Tab/Enter 경로와 보이는 포커스를 단언한다.

### 4.3 누출 검사 범위

CI run 39035626322 `web-e2e-reports/leak-scan/`(시험마다 한 파일)의 합:

| 대상 | 수 | 무엇을 |
|---|---:|---|
| 시험 | 25 | 세 프로젝트 × 시나리오 + 키보드 — 시험마다 테넌트 사본이 다르다(A·B·C) |
| API 응답 본문 | 180 | 같은 출처 `fetch` 응답 전부(401·404 포함) |
| DOM 스냅샷 | 88 | `check()`마다 + 시험 끝 `page.content()` |
| 주소(주 프레임 이동) | 198 | 연 `#token` 주소를 뺀 나머지는 토큰·센티널 0 |
| 요청 URL | 574 | 문서·자산·API 전부 — 쿼리·경로에 토큰·센티널 0 |
| 콘솔·`pageerror` 줄 | 12 | `auth`(프로젝트마다 3)·`ga04`(1). 내용은 저장하지 않는다(누출 경로가 될 수 있다) — 센티널·토큰·CSP 필터는 0 |
| 저장소 | 시험마다 | `localStorage`·`sessionStorage`·쿠키·IndexedDB·Cache Storage 모두 비어 있음 |

센티널 = E2E 스트림 다섯의 증권번호 원문 전부 + 이웃 설계사의 `agentId`·`calcId`·`ledgerId`(`e2e/support/sentinels.mjs`가 스트림에서 뽑는다 — 허구) **7개**, 토큰 = 하네스가 만든 모든 테넌트 사본·종류의 토큰 **36개**. 시험 안 검사(DOM·응답·콘솔·주소·요청)는 7 + 36 전부를 본다. 끝의 산출물 스캔(`artifact-scan.json` "forbidden 43")은 같은 43개를 `build/e2e` 전체(로그·junit·요약 — `.state` 제외)에서 — hits 0. Gradle 평문 스캔(`pii-sentinels.properties`)에도 7개가 다 있다 — 손 스트림 5개와 엔진 스트림 증권번호 1개는 `e2e` 키, A-1 증권번호는 `identifiers`. 엔진 스트림의 그 하나는 보고서를 쓰며 두 목록을 대조하다 빠진 것을 찾았다(F17). 이제 `run.mjs`가 시작 전에 대조한다.

응답 수는 run마다 조금 다르다(직전 CI run 39034733427은 179, 로컬 183) — 화면 재조회 횟수가 시간에 달린 시험이 있다(예: 동기화 지연 시험은 칩 재조회를 기다린다 — 이 run에서 iOS 9, 다른 둘 12). 단언은 수가 아니라 0 hits다.

### 4.4 CSP 위반 0 증거

- **헤더**: `WebScreensIT` › 화면 경로 전부(`routes.ts`의 6) + `/assets/*`의 응답 헤더 집합을 비교 — `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`, `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`. `/api` 응답 헤더는 Phase 3 그대로(같은 시험).
- **브라우저**: 고정 장치가 `addInitScript`로 `securitypolicyviolation` 이벤트를 표식 붙은 콘솔 줄로 내보내고, 콘솔의 CSP 오류 문구("Content Security Policy"·"Refused to")와 함께 센다 — CI `summary.json` **`cspViolations: 0`**(25 시험, Chromium·WebKit), **`externalRequests: 0`**(요청 574개 모두 같은 출처·`data:`·`blob:`).
- **이 수집이 실제로 잡는다는 증거**: W21b(`new Function()`) → "CSP-VIOLATION script-src eval"로 실패.
- **산출물**: `scan-dist.mjs` — `index.html`에 인라인 `<script>`·`<style>`·이벤트 속성 0, 참조는 `/assets/`만, CSS의 `url()`도 `/assets/`만(W21이 잡음). 스타일은 CSS 파일뿐이고, 예외는 소진 바의 React `style` 속성 하나(`Utilization` — `--util` 사용자 속성). React는 이것을 CSSOM(`element.style`)으로 설정하므로 `style-src 'self'`에 걸리지 않는다 — HTML의 `style` 속성·`<style>`은 0.

### 4.5 상태 라벨 표 (최종 — 설계서 §6.2, `labels.ts`·`messages.ko.json`)

세후(실수령) — 계약 열거 `MonthStatus` 4 × `netBasis`(`ESTIMATE`·`CONFIRM`·`PAYOUT`·없음) = 16칸, `labels.test.ts`가 전수:

| 월 상태 | `netBasis` | 세후 | 근거 |
|---|---|---|---|
| PROVISIONAL | 없음 | 숨김 — "세후는 야간 산출 후 표시" | 잠정 명세 전 |
| ESTIMATED | ESTIMATE | **예상** + "기준 {estimateAsOf}" | |
| CONFIRMED | ESTIMATE | **예상**(세전은 "확정") | 심사 Q2 |
| CONFIRMED | 없음 | 숨김 — "세후는 지급 시 확정" | 심사 Q2 |
| CONFIRMED | CONFIRM | 확정 | |
| PAID | PAYOUT | **지급완료** + "{date} 지급" | |
| 그 밖의 10칸 | | 숨김 — "세후 정보 없음" | 계약상 오지 않는 조합 — 보이지 않는 쪽으로 실패 |

그 밖의 금액:

| 금액 | 라벨 |
|---|---|
| 세전·환수 상계·라인 금액(정정 라인 제외) | 월 상태 — 예상(PROVISIONAL·ESTIMATED)·확정·지급완료 |
| 정정(월 정정 합·`byType.ADJUSTMENT`·`ADJUSTMENT` 라인 — 목록·상세, 정정만 있는 달 문구 안의 금액) | **지급 반영 미정** — 월 상태와 무관(2판 — 수용 심사 A1, 엔진 E1-2) |
| 원천세(세후가 보일 때만) | 세후의 라벨(위 16칸 — 1판은 "월 상태"로 잘못 적었다, 코드는 1판부터 세후 라벨) |
| 지급 런 값(지급액·채권 상계·이월 채권) | 지급완료 — PAID 달에만 |
| 원장(한도액·누적·잔여·삭감·이연) | 원장 기준 |
| 월납 보험료 | 계약 정보 |
| 분급 항목 | 그 항목의 상태(예정·이번 달 지급 대상·보류·취소) |
| 환수 채권 | 채권 상태(미상계·상계 중·종결·대손) |
| `monthDataStatus` DRIFT·REBUILDING | 위 라벨 그대로 + "확인 중" 배지, 금액 흐리게(색 + 점선 밑줄, AA 유지) |

고정 문구: 정정만 있는 확정 달 "정정 {금액} — 지급 반영은 정산팀 확인"(Q3), `DEFER_AFTER_FY` "한도 초과 삭감" + "(사규: 초년도 후 이연 — 현재 이연 스케줄 없음)"(Q4), 동기화 "기준 {MM-DD HH:mm}" / "동기화 지연 — 기준 {MM-DD HH:mm}" / "동기화 전"(KST).

### 4.6 계약 요청 (API는 바꾸지 않았다 — 설계서 §7)

| # | 요청 | 화면에서 지금 |
|---|---|---|
| CR-1 | `MonthResponse.previous`(선택): 지난달의 `closeYm`·`status`·`gross`·`net?`·`netBasis?` — 원래 값만, 차이 없음 | 홈에 "전월 대비" 없음(심사 Q4·계획 Q4) |
| CR-2 | `Line.reversalOfYm`·`reversedByYm`(선택) | 다른 달 짝의 달을 알려고 짝의 라인 상세를 한 번 더 부른다(캐시) |
| CR-3 | `Ledger.ruleVersionId`·규정 기준일(선택) | 한도 산식에 규정 기준일 없음 |
| CR-4 | `Ledger.productName`·`productKey`·`insuredMasked`(선택) | 한도 카드에 상품명·피보험자 없음(마스킹 증권번호·체결일로 구분) — **계획 이후 추가**(P4-9) |

### 4.7 의존성·검증 메타데이터 diff (빈 `GRADLE_USER_HOME`에서 재생성)

| 단계 | 추가 | 제거 | 내용 |
|---|---:|---:|---|
| 1 | node-gradle 플러그인 7.1.0 + 플러그인 클래스패스 jackson 2.14.2(6 구성 요소), Node 24.22.0 darwin-arm64·linux-x64 tarball | 0 | tarball SHA-256은 nodejs.org `SHASUMS256.txt`와 같다(linux-x64는 그 파일에서 옮겼다 — CI가 받아 검증) |
| 2~5 | 0 | 0 | Gradle 의존성 변경 없음 |

npm 의존성은 `web/pnpm-lock.yaml`(정확한 판, 공개 1일 미만 판 금지 — eslint 10.12.0, P4-1). 런타임 번들 7종(`react`·`react-dom`·`scheduler`·`react-router`·`@tanstack/react-query`·`@tanstack/query-core`·`openapi-fetch` — `THIRD-PARTY.md` "번들" 열), 전체 258 패키지(macOS 기준 — linux CI 257, `fsevents`). 개발 전용 라이선스 예외 8종(이름으로 닫힘, 번들 금지 — axe-core·@axe-core/playwright MPL-2.0 포함).

### 4.8 `pnpm audit` 기록

CI run 39035626322의 `build/web/audit.json`(`pnpm audit --json`, 기록만 — 결과로 실패하지 않는다): 권고 0, 심각도별 info 0·low 0·moderate 0·high 0·critical 0. 의존성 297(런타임 11·개발 272·선택 41 — pnpm 집계). 올림 없음(지시문 ①).

### 4.9 실 엔진 E2E (Phase 3 Q12 ⓑ)

`e2e/engine-feed/run.sh`는 여전히 **미실행**(사용자 실행 — 병합 뒤 `main`에 `RESULT.md`). 이 Phase는 그 스크립트를 바꾸지 않았다. 화면 E2E는 별개로, 실 엔진 대신 엔진·계약 사본 스트림 바이트를 피드 스텁으로 넣는다(§3 C4·C11).

## 5. 계획과 다르게 한 것·해석·발견

| # | 무엇 | 처리 | 표시 |
|---|---|---|---|
| F1 | eslint 10.13.0 → **10.12.0**(P4-1) | `minimumReleaseAge` 1일에 걸렸다 — 예외를 두지 않고 판을 내렸다 | 기록 |
| F2 | 월 목록·라인 상세가 쓰는 `MonthResponse`·`Line`에 짝의 달이 없다 | 계획 Q12대로 짝 라인 상세를 부른다 — CR-2 | 기록 |
| F3 | **금액 규칙이 API 응답 타입을 보지 못했다** — `openapi-fetch`의 `data` 타입은 스키마의 매핑 사본이라 별칭 이름 `Won`이 사라진다. 3단계에서 번들 스캔이 `policySummary.monthlyPremium === undefined`를 잡았다(ESLint는 통과) | 규칙이 이름 **또는** 구조 표식(`unique symbol` 속성)으로 인식(P4-2). 실제 `api.GET` 타입 사례가 옛 규칙에서 실패·새 규칙에서 보고됨을 둘 다 확인. TS는 표식이 살아 있어 산술을 계속 막았다 — 빈틈은 비교·있음 확인이었다 | **발견·수정** |
| F4 | 형변환 우회(`as unknown as number`) | 규칙에 단언 금지를 더함(P4-4). 번들 스캔이 셋째 층(W8) | 수정 |
| F5 | 정정만 있는 달 조건 "`byType` 비어 있음" → "**키 ⊆ {ADJUSTMENT}**"(P4-5) | 엔진 `AdjustmentPosted`는 `ADJUSTMENT` 라인을 만든다(GA03 바이트). 계획 조건이면 실제 정정 달이 문구를 받지 못했다 | **확인 요청** |
| F6 | 생성 클라이언트 `baseUrl` = 문서 출처(P4-6) | 같은 출처라 결과가 같고, Vitest(Node `Request`)는 상대 URL을 거부한다 | 기록 |
| F7 | E2E 센티널을 `pii-sentinels.properties`의 새 키 `e2e`로(P4-7) | `identifiers`에 넣으면 `ResponseLeakScanIT`(시드 = 그 목록이라고 단언)가 깨진다 — Phase 3 시험은 그대로 | 기록 |
| F8 | 키보드 명세를 모바일 프로젝트에서 `testIgnore`(P4-8) | 건너뜀으로 두면 결과에 skipped가 남는다. 첫 시도(모바일에서 `test.skip`)는 `about:blank`에서 저장소를 읽다 실패했다 — 저장소 검사는 포털 출처 페이지만 보게 했다 | 기록 |
| F9 | 한도 카드에 상품명·피보험자 없음 → **CR-4**(P4-9) | 원장 응답에 없다 — 화면에서 만들지 않는다 | **계약 요청** |
| F10 | W23을 locale 데이터 대신 `react-dom/server`로(P4-10) | 의존성을 늘리면 락이 바뀐다. 첫 시도(axe-core import)는 해석 실패로 상한까지 가지 못해 바꿨다 | 기록 |
| F11 | 로컬 실행: `runTestIssuer` 재사용 대신 **`:web:e2eServe`**(P4-11) | 화면에는 발급자뿐 아니라 피드·인제스트된 뷰가 필요하다. E2E 하네스(같은 `TestIssuer`)를 C 사본으로 띄우고 로그인 URL을 파일(600)에만 쓴다 | 해석 |
| F12 | `verifyTestInputs`가 하네스의 `web/e2e/streams`를 잡았다 | 입력 선언 — 규칙이 일했다 | 기록 |
| F13 | `THIRD-PARTY.md`가 세 번 낡았다(남은 `node_modules`, "번들" 열, Playwright 추가) | `webLicenses`가 매번 잡아 표를 다시 썼다 | 기록 |
| F14 | 설계서의 "PWA" | 서비스 워커·매니페스트 없음(P-6) — 응답이 메모리 밖에 남는다. v2 | 해석 |
| F15 | 영문 번역(`README.en.md`·`docs/en/design.md`)은 `2649700`(설계서 v1.6) 그대로 | 각 파일 머리에 원본 커밋이 적혀 있다 — 이 Phase에서 갱신하지 않았다 | **확인 요청** |
| F16 | **E2E 요약 파일이 서로 덮어썼다** — CI run 39033950394 아티팩트에 시험 25건에 누출 요약 18개·axe 파일 21개(검사한 상태 63). 파일 이름이 제목에서 `\w` 밖 문자를 지워 한글 제목이 `_`로 겹쳤고, axe 파일 이름에는 프로젝트가 없었다. 검사 자체는 모든 시험에서 돌았다(실패하면 파일을 쓰기 전에 멈춘다) — 잃은 것은 요약뿐 | 이름 = `<프로젝트>--<testId>`, `run.mjs`가 통과한 시험마다 두 파일이 있는지 세고 `summary.json`에 합친다(`c9e7f55`, W27). 4단계 커밋 본문의 "18 tests, 108 responses, 65 DOM, 47 axe states"는 이 결함 때문에 모자란 수였다 — §4.2·§4.3이 정정한 수 | **발견·수정** |
| F17 | **평문 스캔 센티널에 E2E 센티널 하나가 없었다** — 엔진 스트림의 증권번호 하나가 E2E 검사(7개)에는 있고 `pii-sentinels.properties`(6개)에는 없었다. 웹 산출물·Vitest·Java 시험 출력에 그 값이 나와도 `scanPlaintextLeaks`는 몰랐다(E2E 산출물 스캔은 7개 전부 — 그쪽은 덮였다) | `e2e` 키에 더하고 `run.mjs`가 시작 전에 두 목록을 대조(`cb270cd`, W28). 평문 스캔 30 → 31 값(62 문자열 — 원문 + UTF-8 16진) | **발견·수정** |

도구 출력 중 지시문처럼 보이는 문장은 없었다(빌드·테스트 로그, pnpm·Playwright 출력, 의존성 문서). `net.jqwik` 없음. 설계서는 v1.7(변경 이력 맨 위), 구현 차이 P4-1~11은 `docs/DECISIONS.md`. 미확정 #14(웹뷰 토큰 갱신)는 기본 가정 + `TODO(confirm#14)`.

## 6. 다음 Phase 질문

**Phase 5(분급 타임라인·시뮬레이션)**:

- **Q1 차트와 금액 규칙**: 막대 높이는 금액 ÷ 최댓값 — 화면의 금액 연산이다. ⓐ API가 막대마다 표시용 비율 문자열을 준다(`utilization`과 같은 방식 — 서버 정수 연산, 화면은 CSS 속성으로) ⓑ `src/money/` 옆에 닫힌 예외 파일 하나(픽셀 기하만, 파생 금액을 그리지 않음). 권장: **ⓐ** — 그러면 차트 "라이브러리 1종"이 필요 없을 수 있다(CSS 막대 + 표). 라이브러리를 꼭 둘지.
- **Q2 `forecast_scenario_enabled`**: 테넌트 플래그를 화면이 알 길이 계약에 없다. `/me`에 `features{forecastScenario}`(선택)를 둘지, `/me/deferrals` 응답에 시나리오 가능 여부를 둘지.
- **Q3 시뮬레이션 2탭**(신계약 수수료·해약 영향): 엔진 응답 샘플(심사 Q9 — 엔진 소형 커밋)을 0단계에서 받는다. 화면 문구("시뮬레이션 — 실제 지급과 다를 수 있음" 류)도 `messages.ko.json` 데이터로.
- **Q4 레이트 리밋 초과 응답**: 심사 Q7(`simulation_log` 행 수, 기본 분당 10). 초과는 429 + 고정 바이트(Phase 3 거부 바이트 표에 한 줄) + `Retry-After`? 사유는 감사·지표 어디에.
- **Q5 V6 범위**: `simulation_log`(심사 Q8)와 예상 이월 채권(§14.1 #13)을 같은 V6에 둘지.
- **Q6 계약 개정 묶음**: CR-1~4를 Phase 5의 `portal-api` 1.1.0(시뮬레이션 경로 추가)과 한 번에 올릴지.

**이 Phase 결과에 대한 것**:

- **Q7(F5)** 정정만 있는 달 조건 "키 ⊆ {ADJUSTMENT}" — 이대로 둘지.
- **Q8(F15)** 영문 번역 갱신 시점 — Phase 병합 때마다, 요청이 있을 때만.
- **Q9 §14.1 #14** 웹뷰 호스트의 토큰 갱신 규약 — 기본 가정(401이면 호스트가 프래그먼트로 다시 연다)을 Phase 8 독립 브라우저 PKCE 전까지 유지할지.

## 7. 2판 — `phase-4.1` (수용 심사 조건 A1, 2026-10-22)

심사 `docs/phase-04-수용심사.md` §2: 엔진 E1-2 — 정정(`ADJUSTMENT`)은 마감·지급 런이 읽지 않는다(`StatementConfirmed.adjustment`에는 있고 `PayoutCompleted`에는 없다). 1판은 정정 금액에 월 상태 라벨을 붙여 PAID 달의 정정이 "지급완료"로 보였다. 정정만 있는 달(GA03)만 E2E에 있어 드러나지 않았다(RM-3).

| 항목 | 바뀐 것 |
|---|---|
| 라벨 | `labels.ts` `adjustmentLabel()`("지급 반영 미정" — `messages.ko.json` `status.adjustment`), `lineLabel(commType, status)`(정정 라인이면 정정 라벨, 아니면 월 상태). 적용: `MonthCard` 정정 행·정정만 있는 달 문구 안의 금액, `StatementScreen` `byType.ADJUSTMENT`·라인 목록(접힌 짝 포함), `LineScreen` 상세 금액·한도 삭감 |
| 문구 | 심사 예시 "정정 — 지급 반영 미정"에서 "정정 —"을 뺐다 — 라벨은 늘 "정정" 행 이름·유형 이름 옆에 붙어 중복된다(P4-12) |
| 시험 | `labels.test.ts` +2(정정 라벨 ≠ 월 상태 라벨 넷, `lineLabel` 네 상태 × 정정·FY_COMM·CLAWBACK), `screens.test.tsx` +1(PAID + `FY_COMM`·`INCENTIVE` + 정정 — 정정 행·`by-type-ADJUSTMENT`·정정 라인·정정 라인 상세의 라벨이 "지급 반영 미정"이고 "지급완료" 0회, 정상 금액은 "지급완료"), 정정만 있는 달 기대 문구 갱신. E2E GA03: 문구 갱신 + 정정 행 라벨 단언(건수 그대로) |
| 주입 | W29(§3 표) |
| 문서 | 설계서 v1.7.1(§6.2·§8·§14.1 #12 — RM-3, #14 "다시 시도"의 새로고침은 호스트가 가로채야 한다 — 심사 Q9), DECISIONS P4-12와 "Phase 4 수용 심사" 절(RM-3·DE-20·DE-21·DD-14~16·결정), 심사 보관 `docs/phase-04-수용심사.md` |
| 범위 밖 | 없음 — API·계약·서버·번들 구성 변경 없음. 원천세 라벨 줄(§4.5)은 1판 보고서의 표기 오류이고 코드는 그대로 |

로컬(이 기계에 Docker 데몬이 없어 통합 시험은 설계대로 실패 — 로컬 증거에서 뺐다): `./gradlew build -x integrationTest` 성공, Vitest 73(+3)·실패 0, `webBuild` 초기 로드 118,208 B gzip(1판 118,149 B, +59 — 상한 130,048), `scanPlaintextLeaks` 0 hits. Java 통합 시험·Playwright 건수는 이 커밋의 CI 아티팩트로 센다 — PR #12 댓글.
