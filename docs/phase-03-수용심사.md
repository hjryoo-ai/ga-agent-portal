# 포털 Phase 3 수용 심사 결과 (2026-10-17) — ga-agent-portal

## 판정: 수용. PR #9 병합 승인(merge commit). `phase-3` = `2649700` 유지. 조건 없음

읽은 것: `phase-3` 태그의 `docs/phase-03-보고서.md` 전문, `CLAUDE.md` 조회 API 절, `IssuerTenantResolver`(`SignedJWT` + `RS256`만 — `none`·HS256·JWE 거부), `HmacPolicyKeys`(비밀 파일 읽기·HmacSHA256), `AgentQueryRepository`의 `agent_id = :agentId` 21곳. 2,655건(실패 0·스킵 0)·평문 스캔 50 센티널 0 hits는 CI run 39018974140·39019368915 아티팩트 출처로 받는다. E2E 미실행은 **합의된 그대로**다.

## 1. 인정하는 것

- **F8 — Phase 2의 `TenantContextGuardTest` 누락**(`ViewResetRepository`)을 찾고, 목록을 "패키지의 모든 저장소"로 바꿔 **다음 누락이 스스로 실패**하게 한 것. Phase 2 심사가 놓쳤다(RM-2).
- **M6b** — 계약은 통과하는 누출(`productName` 안의 증권번호)을 응답 스캔이 잡는다는 것을 주입으로 보인 것. "계약 통과 = 안전"이 아니라는 증거다.
- **F6 — JWKS 장애는 500**: 토큰의 잘못이 아닌 것을 401로 내면 클라이언트가 재로그인을 되풀이한다. 지시문·계획 둘 다 없던 판단.
- **F9** — Spring `Jwt`가 `exp ≤ iat`를 생성자에서 거부해 지표 사유가 `SIGNATURE`로 분류되는 것을 적고, 바이트는 같으니 응답 규약은 지켜진다고 가른 것.
- **F10·F11** — 전체 실행의 순서 의존(`IngestPollingIT`가 다른 시험의 커서를 먼저 만듦)과 공허한 단언(테넌트 하나로 종료 코드 2를 "증명")을 고친 것. 둘 다 통과하던 시험이었다.
- 거부 바이트 네 종의 길이·해시(§4.2), 82 응답 스캔 표, 홈 3콜 문장 수 표, 검증 메타데이터 diff(24 아티팩트, 제거 0, 전부 Boot BOM) — 지시문이 요구한 증거가 전부 표로 있다.

## 2. 결정 (보고서 §6 Q10~Q12·§5)

| # | 결정 | 조건 |
|---|---|---|
| Q10 (F4) 미등록 발급자 지표 태그 `none` | **그대로** — Prometheus 레지스트리의 제약이 맞고, `none`은 테넌트 식별자가 아니다 | 승인 Q6 문구("등록된 경우만")가 레지스트리 제약을 몰랐다 — DE-19. |
| Q11 (F13) `TODO(confirm#N)` 소급 | **소급하지 않는다.** 코드에 판단 지점이 있는 곳(#2·#5·#9)에는 이번에 달았고, #11·#12는 설정 값·no-op이라 지점이 없다는 판단이 맞다. Phase 0~2의 다른 §14.1 기본 가정은 **설계서 §14.1에 "구현 위치" 열**로 대신한다(코드 표식은 앞으로의 Phase부터) | §14.1 표에 열 하나 — 다음 Phase 문서 커밋에. |
| Q12 E2E 결과의 자리 | **ⓑ 병합 뒤 `main`에 별도 커밋**(`RESULT.md` + README 재현 기록). 태그는 보고서에 머문다 | 사용자가 `ENGINE_REPO=<엔진 commission-system 경로> e2e/engine-feed/run.sh`를 돌린다. 실패하면 **정비 PR**(`phase-3.1` 아님 — 병합 뒤라 새 작업). |
| F1 포털 `AuthenticationManager` | 수용 — Spring 리졸버는 형식 오류와 미등록 발급자를 구분하지 못하고 `alg` 전에 발급자를 찾는다 | — |
| F2·F3·F5·F7·F12·F14 | 수용 | F5(월 행 없으면 라인 목록도 404)는 설계서 §7에 한 줄. F14(`ResolvedIdentity`가 OPERATOR를 표현 못 함)는 플랫폼 질문으로 — 지금은 포털 처리로 충분, ga-disclosure 다음 정비 때 `ResolvedIdentity`에 "설계사 없는 주체" 표현을 넣을지. |
| F6 JWKS 장애 500 | 수용 — [더함] | 지표 `api_jwks_failures_total{issuer_hash}`, 재시도는 클라이언트. |
| F9 `exp ≤ iat` → `SIGNATURE` | 수용(응답 동일) | 설계서 §9 검증 순서 표에 각주. |

## 3. 다음 Phase 질문 — 결정 (Phase 4·5 지시문의 재료)

| # | 결정 |
|---|---|
| Q1 상태 라벨 | **웹 데이터 파일**(`messages.ko.json` 류 — ga-disclosure Phase 7과 같은 방식). API는 사실만. |
| Q2 "확정(세후 예상)" | `CONFIRMED` + `netBasis ESTIMATE` → 세후를 **"예상"으로 표시**(잠정 명세 값), `netBasis` 없음 → 세전만 + "세후는 지급 시 확정". `PAYOUT` → 확정. |
| Q3 정정 미지급 문구 | `ADJUSTMENT`만 있는 `CONFIRMED` 달: "정정 N원 — 지급 반영은 정산팀 확인"(단정하지 않는다). §14.1 #12. |
| Q4 `DEFER_AFTER_FY` | **정책 이름이 아니라 사실대로 "한도 초과 삭감"** + 보조 문구 "(사규: 초년도 후 이연 — 현재 이연 스케줄 없음)". 엔진이 이연을 구현하면 문구만 바뀐다. |
| Q5 예상 이월 | Phase 4에서는 **보이지 않는다**(V6 없음). §14.1에 "예상 이월 채권 표시 — V6 후보". |
| Q6 라인 접기 | **같은 달 안에서만** `reversalOf`로 접고, 다른 달의 취소는 두 달 모두에 "취소/취소됨" 배지 + 상대 달 링크. `SYNC_DELAYED` 칩: "동기화 지연 — 기준 {asOf.at}". |
| Q7 레이트 리밋 | **`simulation_log` 행 수**(DB, 인스턴스 간 일관) — 설계사당 분당 N(테넌트 파라미터, 기본 10). |
| Q8 `simulation_log` | 요청 전부(보험료·상품 키·계약일·이벤트 유형·`policyKey`가 아니라 원문 — 서버 내부), 응답은 **금액 전부 + `ruleSnapshotId`**(재현 가능이 목적 — §2.3). 보존 3년 예시값(테넌트 파라미터). 로그·화면에는 옮기지 않는다. |
| Q9 엔진 시뮬레이션 계약 | 엔진 E2가 v1.3 R1~R8을 **구현했다**(E2 보고서 ②·`SimulationContractTest`). Phase 5 0단계에서 엔진 `BootSmokeIT`/계약 시험의 실 응답을 **응답 샘플**로 받는다(엔진 소형 커밋 — 샘플 파일 추가, 코드 0). |

## 4. 기록 (DECISIONS)

- **DE-19** 승인 Q6 "등록된 경우만 태그" — 레지스트리 제약 미고려(Q10).
- **RM-2** Phase 2 심사가 `TenantContextGuardTest` 목록 누락을 보지 못했다(F8).
- 플랫폼 질문 **PQ-1**: `ResolvedIdentity`의 설계사 없는 주체(OPERATOR) 표현 — ga-disclosure 다음 정비 후보.

## 5. 다음

- **포털 Phase 4(화면) 지시문 — E2 형식**(범위·완료 기준·질문만). 범위: React 19 + Vite + TS strict(ga-disclosure Phase 7과 같은 스택·규약 — `openapi-typescript` 클라이언트를 `portal-api.openapi.yaml`에서 해시 고정 생성, `messages.ko.json`, Playwright + axe, CSP·누출 검사, 번들 상한), 화면 5종 중 홈·월별 명세·한도 현황·동기화 칩(§8 — 분급 타임라인·시뮬레이션은 Phase 5, 알림함은 Phase 6), 모바일 우선, 같은 출처 배포(CORS 없음). 위 §3 Q1~Q6이 화면 규약.
- 사용자: `run.sh` 실행 → `RESULT.md`(Q12 ⓑ).
