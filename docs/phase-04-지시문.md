# 포털 Phase 4 지시문 — 화면 1차 (ga-agent-portal, v1.0, 2026-10-17)

## 형식에 대해

E2·Phase 3 승인 §3대로 **범위·완료 기준·질문만** 적는다. 컴포넌트 구조·상태 관리·라우팅·빌드 배선은 계획이 정하고, 지시문 문장이 코드·플랫폼·계약과 다르면 P-번호로 반증한다(E2 계획 §0 형식). 승인은 계획의 결정에 대해서만.

브랜치 `work/phase-4`, 태그 `phase-4`, PR, **병합은 수용 심사 회신 뒤**. 정본: `docs/설계서.md` v1.6(§1.3, §2.3, §8, §10, §12 Phase 4), `contracts/api/v1/portal-api.openapi.yaml` 1.0.0(화면의 유일한 데이터 원천), Phase 3 수용 심사 §3(화면 규약 Q1~Q6 — 이 지시문의 일부), `CLAUDE.md`. 참고 구현: ga-disclosure Phase 7(`web/` — React 19 + Vite + TS strict, `openapi-typescript` 생성 클라이언트 해시 고정, `messages.ko.json`, Playwright + axe, CSP·누출·접근성 검사, 번들 상한). **같은 규약을 쓰되 코드는 복사하지 않는다**(ga-disclosure는 직원 화면, 포털은 설계사 모바일 화면).

## 0. 전제

- 포털 `main` = Phase 3 병합 뒤(계약 v1.4·`portal-api.openapi.yaml` 1.0.0). API는 `/api/v1/me/**` 8개뿐 — 화면이 필요로 하는 것이 API에 없으면 **계약 요청 목록**으로 올리고(E1 §0 방식), 화면에서 계산하지 않는다.
- 화면은 **같은 출처**(`portal-app`이 정적 자산을 서빙 — `/` 하위, API는 `/api`)로 배포한다. CORS 없음. 도입 GA 앱의 **웹뷰 임베드**가 1차 경로(§1.4) — 토큰은 웹뷰가 주입한다(아래 Q1).
- 돈은 화면이 계산하지 않는다(규약 1 — 화면도 포함): 금액은 API의 정수 그대로 포맷만, 비율은 API 문자열 그대로, **합산·차감·곱셈 0**(ESLint 규칙 + 번들 스캔으로 증명). "전월 대비"(§8 홈)도 API가 주지 않으면 **표시하지 않는다**(계약 요청).

## 1. 범위

| # | 항목 | 요구 |
|---|---|---|
| ① | **스택·빌드** | `web/` — React 19, Vite, TypeScript `strict`, pnpm(락 + `--frozen-lockfile`), `openapi-typescript`로 `portal-api.openapi.yaml` → 타입·클라이언트 **생성물 커밋 + 계약 파일 해시 고정**(계약이 바뀌면 빌드 실패), 서버 상태 캐시 1종(TanStack Query 류), 차트 없음(Phase 5 분급 타임라인에서), 폰트는 시스템 스택(서브셋 논의 없음), **번들 상한**(gzip, 초기 로드 — 수치는 계획이 측정해 정하고 상한을 CI에), 의존성 전부 락·`pnpm audit` 기록(올림은 없음). |
| ② | **화면 4종**(§8) | **홈**(이번 달 카드 — 실수령 예상/확정·세전·환수 상계·원천세·지급예정일·상태 라벨 / 주의 계약 N건 → 한도 / 최근 알림은 **Phase 6까지 자리만**), **월별 명세**(월 선택 → 유형별 합계 → 라인 목록(페이지) → 라인 상세 — 근거·한도 판정·연결 분급·채권), **한도 현황**(필터 주의/전체 → 카드 → 상세: 산식·타임라인(`history`)·삭감/이연·플래그), **동기화 칩**(공통 상단 — `dataStatus`·`asOf`). 분급 타임라인·시뮬레이션(5), 알림함·이의제기(6)는 **진입점만**(비활성). |
| ③ | **화면 규약**(Phase 3 수용 §3) | 상태 라벨 표는 `messages.ko.json`의 데이터(Q1); `CONFIRMED` + `netBasis ESTIMATE` → 세후 "예상", `netBasis` 없음 → 세전만 + "세후는 지급 시 확정", `PAYOUT` → 확정(Q2); 정정만 있는 달 "정정 N원 — 지급 반영은 정산팀 확인"(Q3); `DEFER_AFTER_FY`는 **"한도 초과 삭감"** + 보조 문구(Q4); 예상 이월 미표시(Q5); 라인 접기는 같은 달 `reversalOf`만, 다른 달 취소는 배지 + 링크(Q6); `SYNC_DELAYED` 칩 "동기화 지연 — 기준 {asOf.at}"; `monthDataStatus DRIFT` → "확인 중" 배지 + 금액 **흐리게**(§6.6); 시뮬레이션 문구는 Phase 5. 모든 금액 옆에 상태 라벨(대전제 2) — 라벨 없는 금액 컴포넌트가 **없음**을 시험으로. |
| ④ | **인증 연결** | 토큰 주입 방식은 Q1. 401 → "다시 로그인" 화면(IdP로 — 포털은 로그인 화면이 없다), 404(`/me` 링크 없음) → "등록되지 않은 사용자 — 소속 GA에 문의". 토큰은 `localStorage`·쿠키에 **저장하지 않는다**(웹뷰 주입·메모리). |
| ⑤ | **접근성·현장성**(§8 끝) | 최소 터치 44px, 큰 숫자 우선, `prefers-reduced-motion`, 다크 모드는 **하지 않는다**(범위 밖 — 질문 Q5), axe 위반 0(WCAG 2.1 AA 규칙 집합), 키보드 전 경로, 화면 캡처 방지 없음. |
| ⑥ | **보안 헤더·CSP** | 정적 자산 응답에 CSP(`default-src 'self'`, `script-src 'self'` — 인라인·eval 0, `connect-src 'self'`), `frame-ancestors`는 **웹뷰 임베드 정책에 따라**(Q2), `Referrer-Policy: no-referrer`, `X-Content-Type-Options`. API 응답 헤더는 Phase 3 그대로. |
| ⑦ | **E2E·검사** | Playwright(모바일 뷰포트 2종 + 데스크톱 1종) + axe: 시드 테넌트(A-1 둘째 판 — 202608 PAID·202702 ESTIMATED, 한도 카드 1(삭감 145,000), 해약 플래그) 시나리오 — 홈 → 명세 → 라인 상세 → 한도 → 상세, 상태 전이 표시(PROVISIONAL → ESTIMATED → CONFIRMED → PAID — 스트림을 단계별로 넣으며), `SYNC_DELAYED`·`DRIFT` 표시, 401·404 화면. **누출 검사**: 렌더된 DOM·네트워크 응답·콘솔에 증권번호 원문·남의 ID 0(Phase 3 `ResponseLeakScanIT`의 화면판), 토큰이 URL·스토리지·콘솔에 0. CSP 위반 0(브라우저 리포트). 번들 스캔: `*`·`/`·`Math.*` 금액 연산 0(ESLint 커스텀 규칙 + 생성 번들 정규식 — 금액 타입에 한정하는 방법은 계획이). |
| ⑧ | **배포·실행** | `./gradlew build`가 `pnpm build`를 포함하고 산출물을 `portal-app` 정적 자산으로 포함(단일 아티팩트). `no-docker` 잡은 그대로. 로컬 실행 README(시험 발급기로 토큰 받기 — Phase 3 `runTestIssuer` 재사용). |

## 2. 완료 기준

| # | 기준 | 증명 |
|---|---|---|
| C1 | 생성 클라이언트가 계약 해시에 고정 — 계약 1바이트 변경 → 빌드 실패; 화면의 모든 API 호출이 생성 클라이언트 경유(직접 `fetch` 0) | 빌드 검사 + ESLint 규칙(주입: 계약 변경 / 직접 `fetch`) |
| C2 | **금액 연산 0**: `web/src`에 금액 타입 피연산자의 `+ - * /`·`Math.*`·`Number()` 변환 0, 포맷은 `Intl.NumberFormat` 정수만 | ESLint 커스텀 규칙 + 번들 스캔(주입: 홈에서 `gross + adjustment`) |
| C3 | 모든 금액 표시에 상태 라벨이 붙는다 — 금액 컴포넌트는 `status`(또는 "확정값" 표식) 없이는 **타입 오류** | TS 타입 + 컴포넌트 시험(주입: 라벨 없는 금액 렌더 → 컴파일 실패) |
| C4 | 상태 라벨 표(Q1·Q2) — `status × netBasis` 전 조합 × 월 상태 전이 E2E | Vitest 표 전수 + Playwright 전이 시나리오 |
| C5 | `DRIFT` 흐리게 + "확인 중", `SYNC_DELAYED` 칩, 정정 문구(Q3), `DEFER_AFTER_FY` 문구(Q4), 라인 접기(Q6 — 같은 달 접힘·다른 달 배지) | 컴포넌트 시험 + E2E |
| C6 | 누출 0(DOM·응답·콘솔·URL·스토리지 — 증권번호·남의 ID·토큰) | Playwright 누출 검사(주입: 라인 상세에 `policyKey` 대신 증권번호 표시 — API는 안 주므로 시드 센티널을 화면 상수에 심어 검사가 잡는지) |
| C7 | axe 위반 0(AA), 터치 44px(계산된 스타일 검사), 키보드 전 경로, `prefers-reduced-motion` | Playwright + axe(주입: 대비 위반 색 하나) |
| C8 | CSP 위반 0, 인라인 스크립트·`eval` 0, 헤더 집합 | Playwright 리포트 수집 + 헤더 시험(주입: 인라인 `onclick`) |
| C9 | 번들 상한(gzip 초기 로드 ≤ 계획값) | CI 측정(주입: 큰 라이브러리 추가 → 실패) |
| C10 | 401·404 화면, 토큰 저장 0 | E2E |
| C11 | 모바일 2 + 데스크톱 1 뷰포트에서 시나리오 전부 통과, 스냅샷은 **쓰지 않는다**(구조·텍스트 단언) | Playwright |
| C12 | 단일 아티팩트(`./gradlew build` → 정적 자산 포함), Phase 0~3 무손상, `TZ=UTC`, CI, 평문 스캔(웹 산출물 포함), 주입 ID 단위, 의존성 락·`pnpm audit` 기록 | CI 아티팩트 |

## 3. 계획에서 답할 질문

1. **토큰 주입 방식**(④): 웹뷰가 ⓐ `window.__PORTAL_TOKEN__`(초기 HTML 주입) ⓑ `postMessage` ⓒ URL 프래그먼트(ga-disclosure `/s#token`과 같은 — 로그·히스토리에 남지 않음) 중 무엇으로 주나. 독립 브라우저(데모·Phase 8)에서는 OIDC 리다이렉트(PKCE)를 쓸지, 시험 발급기 토큰을 붙여 넣는 개발용 경로만 둘지. 권장 방향: **ⓒ + 메모리**, 독립 브라우저는 Phase 8에서 Keycloak PKCE.
2. **`frame-ancestors`**(⑥): 웹뷰 임베드는 `iframe`이 아니라 네이티브 웹뷰라 `frame-ancestors 'none'`이 맞는지 — 확인.
3. **번들 상한값**(①·C9): 측정 뒤 제안(ga-disclosure Phase 7 값 참고 — 그 수치는 계획이 확인).
4. **"전월 대비"·"다음 달 분급 예정액"**(§8 홈): API가 주지 않는다 — 계약 요청(`/me/earnings`의 두 달 조회로 화면이 **비교 문구만**(증감 방향 — 계산 아님?)을 만들 수 있나, 아니면 API 필드로). 금액 차감은 화면이 못 하므로 **API 요청**(`previousMonth{gross, net}` 선택 필드)이 맞는지 판단.
5. **다크 모드·국제화**: 범위 밖 확인(한국어만, `messages.ko.json` 하나).
6. **같은 출처 서빙**(⑧): `portal-app`이 `web/dist`를 정적으로 — Spring 리소스 핸들러 vs 별도 정적 서버(Phase 8 인그레스) — 계획이 결정. 인증 필터는 `/api`에만.
7. **알림 자리**(②): 홈의 "최근 알림 3건"은 Phase 6까지 API가 없다 — 빈 상태 문구로 둘지, 영역 자체를 숨길지.
8. **E2E 시드 주입**: Playwright가 A-1 스트림을 어떻게 넣나(Phase 3 `QuerySeed` 재사용 — 파일 피드 어댑터로 포털 기동 → `caught_up_at`?) — 계획이 정한다.

## 4. 하지 말 것

금액 연산(화면 포함). 직접 `fetch`. 토큰 저장(스토리지·쿠키·URL 쿼리). 분급 타임라인·시뮬레이션·알림·이의제기 화면(진입점만). 다크 모드·다국어. 스냅샷 시험. 외부 CDN(폰트·스크립트 — 전부 번들). API 변경(요청 목록으로). 화면 캡처 방지.

## 5. 보고 형식

Phase 3과 같게. 추가: 번들 크기 표(초기·경로별), axe 결과 요약, 누출 검사 범위(응답 수·DOM 수), CSP 리포트 0 증거, 상태 라벨 표(최종), 계약 요청 목록(있으면), 다음 Phase 질문(Phase 5 — 분급 타임라인 차트 라이브러리 1종·시나리오 토글 `forecast_scenario_enabled`·시뮬레이션 2탭·`simulation_log`·레이트 리밋).
