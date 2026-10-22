# 포털 Phase 4 수용 심사 결과 (2026-10-17) — ga-agent-portal

## 판정: 조건부 수용. 조건 하나(A1 — 정정 금액의 라벨)를 `phase-4.1`로 고친 뒤 PR #12 병합(merge commit). `phase-4` = `5bed1f1` 유지

읽은 것: `phase-4`의 `docs/phase-04-보고서.md`·`docs/phase-04-계획.md` 전문(계획 승인은 사용자가 직접 했으므로 Q1~Q16·P-1~P-12를 이 심사에서 함께 봤다 — 뒤집을 결정은 없다), `CLAUDE.md` 화면 규약 절, `web/src/boot/token.ts`, `money/won.ts`·`Amount.tsx`, `labels/labels.ts`, `eslint/plugin.js`의 `no-money-arithmetic`, `screens/fold.ts`, `ui/SyncChip.tsx`·`MonthCard.tsx`, `HomeScreen.tsx`의 개수 셈, `ScreenConfiguration.SCREEN_PATHS` ↔ `routes.ts`, 계약의 `/me/limits`(페이지 없음 — 홈의 개수가 전수다), `pii-sentinels.properties`의 `e2e` 키, CI 워크플로의 `web-e2e` 잡 조건. 2,660 + 70 + 25(실패 0·스킵 0), axe 63 상태 0, 누출 0, CSP 0, 118,149 B는 CI run 39035626322 아티팩트 출처로 받는다.

## 1. 인정하는 것

- **F3 — 금액 규칙이 실제 API 응답 타입을 못 봤다**는 것을 번들 스캔(셋째 층)이 잡았고, 규칙을 이름이 아니라 **구조 표식**(`__@wonBrand@`)으로 넓혔다. "ESLint 통과 = 안전"이 아니라는 증거이고, 세 층을 둔 이유가 3단계에서 바로 나왔다. W7에서 `!== undefined`까지 잡힌 것을 규칙을 좁히지 않고 `present()`로 옮긴 것도 맞다.
- **F16·F17 — 보고서를 쓰다 찾은 둘.** 요약 파일이 한글 제목에서 겹쳐 25건 중 18건만 남은 것(검사는 다 돌았고 잃은 것은 요약뿐이라는 구분이 정확하다), E2E 센티널 7개와 평문 스캔 6개의 차이 하나. 둘 다 **다음 누락이 스스로 실패**하게(개수 검사 W27, 목록 대조 W28) 고쳤다.
- 세후 16칸 표를 "계약상 오지 않는 조합은 **보이지 않는 쪽으로** 실패"로 닫은 것(`unavailable()`), `netView`의 열거 전수 `switch`.
- 토큰 부트가 React보다 먼저 읽고 `replaceState`로 지우며, `token=`이 아니어도 프래그먼트는 지운다 — 토큰 모양만 보고 서명은 서버.
- 동기화 칩이 **관찰자 있는 쿼리만** 합치고(`getObserversCount() === 0` 제외) 가장 작은 `seq`를 기준으로 쓰는 것 — 화면에 없는 응답으로 칩이 바뀌지 않는다.
- E2E 하네스가 실 인제스트 경로(Q8)로 네 테넌트를 띄우고 머리를 단계별로 올린 것 — 전이·Q3·Q6·DRIFT·`SYNC_DELAYED`가 전부 브라우저에서 보였다. 세 프로젝트에 WebKit을 넣은 것(iOS 웹뷰는 WKWebView).
- P-4(`status=WARN`은 정확히 WARN), P-7(`/me/deferrals.months[].expected`가 이미 있다), P-8(A-1 seq 3은 `deferredTotal` 145,000·`cutTotal` 0) — 내 설계서·지시문이 틀린 것을 계약·코드로 바로잡았다.

## 2. 조건 — A1: 정정 금액에 월 상태 라벨을 붙이면 안 된다

**사실.** 엔진 §11 **E1-2**: 정정(`ADJUSTMENT`)은 마감·지급 런이 읽지 않는다 — `StatementConfirmed.adjustment`에는 있고 `PayoutCompleted`에는 없다. 즉 어느 달이든 정정은 **지급되지 않았다**.

**지금 화면.** `MonthCard`의 정정 행(`row-adjustment`), 명세의 `byType.ADJUSTMENT` 합계, `ADJUSTMENT` 라인의 금액이 전부 `monthLabel(status)`를 받는다. PAID 달에 정정이 있으면 **"정정 N원 · 지급완료"**로 보인다 — 거짓이다. 정정 전용 문구(Q3)는 `isAdjustmentOnly`(정정뿐인 CONFIRMED 달)에만 붙어, 정상 라인과 정정이 같이 있는 달(`AdjustmentPosted.closeYm`은 익월이므로 흔한 형태)은 비켜 간다. E2E가 못 본 이유는 GA03이 정정만 있는 달이기 때문이다.

**내 잘못.** Phase 3 수용 Q3가 §14.1 #12("`ADJUSTMENT` 라인이 지급 런에 아직 포함되지 않은 달")를 "`ADJUSTMENT`만 있는 `CONFIRMED` 달"로 **좁혀 적었다** — RM-3. 설계서 v1.7 §6.2의 "세전·환수 상계·정정·라인 금액의 라벨은 월 상태"는 그 문장을 그대로 따른 것이다.

**고칠 것(`phase-4.1`, 범위 닫힘).**
1. `labels.ts`에 `adjustmentLabel()` — 문구는 `messages.ko.json` 키 하나(예: "정정 — 지급 반영 미정"). 정정 행·`byType.ADJUSTMENT`·`commType = ADJUSTMENT` 라인(목록·상세)의 `<Amount>`는 **월 상태와 무관하게** 이 라벨. 정정만 있는 달의 `adjustmentOnly` 문구는 그대로(그 안의 `<Amount>`도 이 라벨).
2. `labels.test.ts`: 정정 라벨이 네 월 상태 라벨 어느 것과도 같지 않다 + `screens.test.tsx`에 **PAID 달 + 정상 라인 + 정정**인 고정물 한 건(정정 금액 옆에 "지급완료" 0회). 주입 W29: 정정 행을 `monthLabel`로 되돌려 실패 확인.
3. 설계서 §6.2 한 문장("세전·환수 상계·라인 금액의 라벨은 월 상태, **정정은 지급 반영 미정** — 엔진 E1-2") + §8 정정 줄, DECISIONS P4-12. 엔진이 정정을 지급에 넣는 날 문구만 바뀐다(Q4와 같은 구조).

위 범위를 넘는 변경이 필요하면 먼저 보고. 범위 안이면 `phase-4.1` CI 통과 후 **재심사 없이 병합**.

## 3. 결정 (보고서 §6 Q7~Q9·§5)

| # | 결정 | 비고 |
|---|---|---|
| Q7 (F5) 정정만 있는 달 = `CONFIRMED` + 키 ⊆ {ADJUSTMENT} + 정정 ≠ 0 | **그대로** — 빈 `byType`(라인 없는 달)도 `every`가 참이라 포함된다. 계획의 "비어 있음"이면 실제 정정 달이 문구를 못 받았을 것이다 | A1 뒤에도 조건은 같다 |
| Q8 (F15) 영문 번역 | **요청이 있을 때만.** Phase 산출물이 아니다. 파일 머리의 원본 커밋이 낡음을 드러내므로 그대로 둔다 | — |
| Q9 (#14) 웹뷰 토큰 갱신 | **Phase 8까지 기본 가정 유지**("401이면 호스트가 프래그먼트로 다시 연다"). 단, `location.reload()`는 혼자서는 토큰을 되살리지 못한다(프래그먼트는 지워졌다) — 호스트가 reload를 가로채야 한다는 것을 §14.1 #14 문장에 적어 둔다(4.1 문서 커밋에 한 줄) | PKCE 전환 때 이 버튼이 사라진다 |
| F1 eslint 10.12.0 | 수용 — `minimumReleaseAge`에 예외를 두지 않은 것이 맞다 | — |
| F2·F6·F7·F8·F10~F14 | 수용 | F7(`e2e` 키 분리)은 `ResponseLeakScanIT`의 단언을 지킨 올바른 선택. F14 PWA는 v2 |
| F9 → CR-4 | 수용 — 원장 응답에 없는 것을 화면이 만들지 않았다 | §4 Q6 |
| 계획 Q1~Q16·P-1~P-12 | 전부 그대로(사후 확인) | Q16 "이번 달"=KST 달력 월·404면 안내, Q12 그 달 전체 수신 후 같은 달만 접기, Q3 측정+10% — 뒤집을 것 없음 |

## 4. 다음 Phase 질문 — 결정 (Phase 5 지시문 재료)

| # | 결정 |
|---|---|
| Q1 차트 | **ⓐ API가 막대마다 표시용 비율 문자열**(`utilization`과 같은 방식 — 서버 정수 연산, 기준은 그 응답 안의 최댓값, 화면은 CSS 속성 + `role="img"`·`aria-label`). 차트 라이브러리 **없음** — CSS 막대 + 표(표가 접근성 본문). 번들 상한 그대로. |
| Q2 `forecast_scenario_enabled` | `/me`에 선택 `features: { forecastScenario: boolean }` — `/me`는 관문이라 추가 콜이 없다. `/me/deferrals`에는 두지 않는다(플래그는 설계사 사실이 아니라 테넌트 설정). |
| Q3 시뮬레이션 2탭 | 그대로 — 0단계에서 엔진 실 응답 샘플(엔진 소형 커밋, 코드 0). 입력 검증은 **API 400 한 바이트**가 정본, 화면은 같은 규칙을 데이터(계약의 `pattern`·`minimum`)에서 읽어 미리 막을 뿐 — 두 벌로 적지 않는다. "실제 지급과 다를 수 있음" 문구는 `messages.ko.json`. |
| Q4 레이트 리밋 초과 | **429 + 고정 바이트**(거부 바이트 표에 한 줄) + `Retry-After: <초>`(정수). 지표 `api_rate_limited_total{tenant}`만, 감사 없음(401과 같은 이유 — 쓰기 증폭). 초과 요청은 `simulation_log`에 **쓰지 않는다**(행 수가 한도이므로). "마지막 결과 재표시"는 화면 메모리(Query 캐시)의 값 — 로그를 읽지 않는다. |
| Q5 V6 | **`simulation_log`만.** 예상 이월 채권(#13)은 Phase 5 범위(타임라인·시뮬레이션)가 아니다 — Phase 7 대사 후보로 남긴다. |
| Q6 계약 묶음 | **한 번에** — `portal-api` 1.1.0 = 시뮬레이션 경로 + CR-1~4 + Q2 `features` + Q1 비율 필드, Phase 5 **0단계 계약 PR**(Phase 3의 v1.4와 같은 절차). CR-4의 `productName`은 Phase 3 M6b 응답 스캔 범위에 들어간다(상품명 안의 증권번호). |

## 5. 기록 (DECISIONS)

- **RM-3** Phase 3 수용 Q3가 §14.1 #12를 정정만 있는 달로 좁혔다 → A1.
- **DE-20** 지시문 ⑦ "한도 카드 1(삭감 145,000)" — A-1 seq 3은 `DEFER_AFTER_FY`라 원장에서는 이연 145,000·삭감 0(P-8). **DE-21** 지시문 Q4 "다음 달 분급 예정액을 API가 주지 않는다" — `/me/deferrals.months[].expected`가 있다(P-7).
- 설계서 결함 **DD-14** §10 홈 콜 `status=WARN`(정확히 WARN만 — P-4), **DD-15** §8 한도 상세의 규정 기준일·상품명·피보험자(계약 `Ledger`에 없다 — P-10·F9 → CR-3·CR-4), **DD-16** §11 "PWA"·§12 Phase 4 알림함(P-5·P-6). 전부 v1.7에서 고쳐졌다.
- 계획 승인을 사용자가 직접 한 Phase — 결정 Q1~Q16은 이 심사에서 사후 확인했고 뒤집은 것은 없다.

## 6. 다음

- Claude Code: **`phase-4.1`**(§2 세 항목 + Q9 한 줄) → CI → 병합.
- 사용자: `ENGINE_REPO=<엔진 commission-system 경로> e2e/engine-feed/run.sh` → `RESULT.md`(Phase 3 Q12 ⓑ, 여전히 미실행).
- 다음 지시문: **Phase 5(분급 타임라인·시뮬레이션) — E2 형식**, 0단계 = 계약 1.1.0 PR(§4 Q6) + 엔진 응답 샘플 커밋. 엔진 쪽은 샘플 커밋 외 변경 없음.
