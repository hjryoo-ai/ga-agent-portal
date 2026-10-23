# Phase 5 보고서 — ga-agent-portal 분급 타임라인·시뮬레이션 (지시문 v1.0)

> 지시문 `docs/phase-05-지시문.md` · 계획 `docs/phase-05-계획.md`(승인 2026-10-23 — Q1~Q24·P-1~P-26 권장안 전부, Q17·Q18 포함, `docs/DECISIONS.md`) ·
> 브랜치 `work/phase-5` · PR [#13](https://github.com/hjryoo-ai/ga-agent-portal/pull/13) · 태그 `phase-5`(이 보고서 커밋). **병합은 수용 심사 회신 뒤.**
> 엔진 쪽: `ga-commission-engine` PR #8(시험 코드만 — 샘플) → 병합 `2c3b093`(Q17 선승인 — 자체 CI 통과 뒤).
>
> **2판**(2026-10-24, 태그 `phase-5.1`): 수용 심사 `docs/phase-05-수용심사.md`(조건부 수용) — 조건 B1(예정·보류가 모두 0인 달 — 계약 1.1.1)·F10 선택 개선. 바뀐 곳은 §4.3 끝 문단·주입 S17·F10·F11 행·§7. `phase-5`는 1판(`0fccc05`) 그대로.

이 보고서에서 **"샘플 S…"**는 엔진 응답 샘플(계획 ③ 표 S1~S12·S11b)이고 **"주입 S…"**는 주입 ID(계획 ⑩)다 — 계획이 두 이름에 같은 머리글자를 썼다.

## 1. 커밋

**엔진**(`ga-commission-engine`, `work/sim-samples` → `main` `2c3b093`):

| 커밋 | 내용 | 주입 |
|---|---|---|
| `e3eefca` | `SimulationContractTest` 샘플 13건(포털이 실제로 보낼 요청 — 엔진 상태 standard·ambiguous) + `contracts/samples/simulations/` 27파일(`-Psamples.update=true`로 쓰고, 평소에는 바이트 대조), 설계서 v1.2.8 | E-1~E-3 |
| `5923dbf` | Problem 본문은 JSON 값으로 대조(엔진 main은 그대로) — 첫 PR CI(run 39939859651)의 빠른 티어가 `Map.of` 키 순서(실행마다 무작위)로 실패했다. 로컬 재현 1/3 → 수정 뒤 0/8 | — |

엔진 CI: run 39940402617(pr)·39940396064(push) 네 잡 success(빠른 티어 아티팩트 `SimulationContractTest` 6건 실패 0). 엔진 main 코드 변경 0.

**포털**(`origin/main` = `86d6fe6` 위 `work/phase-5`):

| 커밋 | 내용 | 주입 |
|---|---|---|
| `4941337` | 지시문·계획(Q1~Q24, P-1~P-26) | — |
| `10f0ecf` | 1단계(계획 ⑪ 2) — 접근 타입을 `portal-domain` `com.ga.portal.domain.access`로(Q11), 인가 표 `ApiAuthorization` | S15 |
| `856ece9` | 2단계(⑪ 1·4) — 계약 1.1.0의 기존 경로(타임라인 시나리오·막대·84개월·`policyKey`·CR-1~4·`features`), 엔진 사본·`verifyUpstreamContract` 일반화, `ForecastScenario`, 곱셈·SQL 산술 스캐너, 호환 시험 | S1·S2·S4·S5 |
| `610520a` | 3단계(⑪ 3) — 계약 1.1.0 POST 경로, `portal-sim`, V6, 레이트 리밋, 엔진 클라이언트·분류·매핑, 컨트롤러, 거부 바이트 여섯, 지표, 엔진 스텁(시험 픽스처), 시뮬레이션 통합 시험 | S3·S8·S9 |
| `bb8bb51` | 3단계 CI 수정(F6) — 컨트롤러의 웹 조건, 본문 상한+1바이트 읽기(F3 — 커밋 보안 검토), 시험 행 판독기, 같은 코드 머리 비교 | — |
| `717795b` | 계약 설명문 여덟 곳 따옴표(F4) + `noKeyWithoutAValue` | S16 |
| `0e0a327` | 4단계(⑪ 5) — 화면 `/deferrals`·`/simulations`, 홈 다음 달·전월, 한도 상세 인계, CR-2·CR-4 반영, 규칙 파일 생성, 지연 화면 닫힌 목록 | S12·S13 |
| `86bfbeb` | 5단계(⑪ 6) — E2E 하네스 엔진 스텁·제어, GA04 스트림 seq 11~24, 명세 넷, 동적 센티널 `simId`, `run.sh`·`e2eCheck` 시뮬레이션 한 쌍, 시뮬레이션 응답 센티널 스캔 | (S6·S7·S10 — CI) |
| `f19c919` | E2E 센티널 키에 `POL-EDGE-0002`(첫 E2E가 시작 전에 멈췄다 — Phase 4 F17의 검사가 일했다) | — |
| `fbb19f1` | 6단계(⑪ 7) — 설계서 v1.8 §10~§14, `docs/운영.md` §6, DECISIONS P5-1~14, README, 시뮬레이션 타임아웃 환경 자리 | — |
| `bdbc740` | 시뮬레이션 실패 로그 스캔(C8) | — |
| `47b01db` | 첫 E2E 결과 수정(F7·F8) — `<select>` 대신 버튼 묶음, `<dl>` 안 `<p>`, 접근 이름 기대값, CSP 표식 줄에 요소 이름, P5-15 | (S14 — CI) |
| `aba8131` | 84개월 경계 쌍 203401·203402(F9) | — |
| (이 커밋) | 보고서, 계획 머리의 승인 줄, 설계서 번들 측정값(CI) | — |

```
portal-domain   access/ Caller·UseCaseEntry·DenyReason·NotFound·InvalidRequest·ApiAuthorization(+SIM 둘) · SimulationKind
portal-query    rule/ForecastScenario(유일한 금액 곱셈) · MeQueries(84개월·시나리오·막대·policyKey·CR-1) · api/ 레코드(1.1.0)
portal-sim      SimulationUseCases(진입 둘) · RateLimitRule · SimRequests · HttpEngineSimulationClient · EngineOutcomes · SimResponses · SimContracts
                port/ SimulationLogStore·SimTransactions·PolicyResolver·EngineCredentials·SimFacts·SimMetrics · testFixtures/StubSimulationEngine
portal-infra    V6__simulation_log.sql · SimulationLogRepository · SimulationLockRepository · AgentQueryRepository(monthDetail·policies·CR-2·CR-4)
portal-app      web/SimulationController · sim/ SimConfiguration·JdbcSimulationLogStore·MicrometerSimMetrics·SimProperties · ProblemResponses(+6)
                archTest/ MultiplicationScanTest·SqlArithmeticScanTest · integrationTest/ Sim*IT 6·RateLimitIT·DeferralsQueryIT · WebE2eHarness(엔진 스텁)
contracts       api/v1/portal-api.openapi.yaml 1.1.0 · shared/ga-commission-engine/(엔진 2c3b093 사본 — 샘플 27·스키마·UPSTREAM·CHECKSUMS)
web             screens/ Timeline·Simulation · sim/ rules·handoff·eventTypes · ui/ Choices·policyOptions · gen/portal-api.rules.json(생성)
                e2e/specs/ ga01-simulation·ga02-insurer·ga03-ratelimit·ga04-timeline · streams/edges.jsonl(+14) · support/samples.ts
e2e/engine-feed run.sh ⑧ 판정에 시뮬레이션 한 쌍(EngineFeedE2eCheck ③)
```

## 2. 테스트 (CI 출처)

CI run [39951478495](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/39951478495)(`aba8131`, build·no-docker·web-e2e 모두 success). 수는 그 run의 아티팩트(`test-reports` — 결과 XML·Vitest junit·`bundle-size.json`·`audit.json`, `web-e2e-reports` — junit·요약·누출·axe 파일)를 내려받아 셌다. 기준선은 `main` = `86d6fe6`(Phase 4.1 — CI run 39772668357: Java 2,660·Vitest 73·E2E 25).

| 모듈 | 소스셋 | 건수 | `main` 대비 | 비고 |
|---|---|---:|---:|---|
| `portal-domain` | test | 2,084 | 0 | |
| `portal-projection` | test | 135 | 0 | |
| `portal-ingest` | test | 80 | 0 | |
| `portal-query` | test | 46 | +20 | `ForecastScenarioTest` 10, `MeQueriesTest` +6(13), `PortalApiCompatibilityTest` 2, `PortalApiContractTest` +2(5 — `everyObjectSchemaIsClosed`·`noKeyWithoutAValue`) |
| `portal-sim` | test | 17 | +17 | `SimulationUseCasesTest` 5, `EngineOutcomesTest` 3, `SimulationSchemaParityTest` 3, `RateLimitRuleTest`·`SimRequestsTest`·`SimResponsesTest` 각 2 |
| `portal-infra` | test | 10 | 0 | `TenantContextGuardTest`는 새 저장소·메서드를 같은 메서드 안에서 |
| `portal-infra` | integrationTest | 172 | 0 | |
| `portal-app` | test | 22 | +2 | `SimulationControllerTest` 2 |
| `portal-app` | integrationTest | 86 | +17 | `DeferralsQueryIT` 5, `EngineFailureIT` 3, `SimulationAuthzIT`·`SimulationLogIT`·`SimulationRejectionIT`·`RateLimitIT` 각 2, `SimulationPassThroughIT` 1. `RebuildIT`·`QueryStatementIT`·`ApiContract`·`PortalApplicationIT`는 같은 메서드 안에서 |
| `portal-app` | archTest | 69 | +5 | `MultiplicationScanTest` 2, `SqlArithmeticScanTest` 2, `TenantPredicateScanTest` +1(`everySimulationLogStatementIsBoundToTheAgent`) |
| **Java 합계** | | **2,721** | **+61** | 실패 0, 스킵 0 |
| `web` | Vitest | 98 | +25 | `phase5` 12, `rules` 5, `lintRules` +3(17), `labels` +1, `messages` +3, `fold` +1 |
| `web` | Playwright | 52 | +27 | 새 시나리오 9 × 3 프로젝트(ga01-simulation 4·ga02-insurer 1·ga03-ratelimit 1·ga04-timeline 3) — 프로젝트별 17·17·18, 스킵 0 |

- 같은 run의 빌드 검사: `printPlatformArtifacts: 13 files equal to the handoff §6 table, source=remote (Gradle cache)`, `verifyPlatformPins: 13 com.ga.platform:0.2.0 files pinned, equal to the handoff §6 table`, **`verifyContractChecksums: contracts/CHECKSUMS and contracts/shared/{ga-commission-engine,ga-disclosure}/CHECKSUMS match`**, `verifyUpstreamContract` 28줄 "… == …/ga-commission-engine@2c3b0931…:commission-system/contracts/…"(샘플 27 + 스키마) + ga-disclosure `db82d5d`, `scanPlaintextLeaks: 113 files, 76 forbidden strings, 0 hits`, `verifyTestInputs: 9 test tasks read repository files, all declared`(+`:portal-sim:test`), `verifyBootJarScreens: portal-app-0.1.0.jar ⊃ web-0.1.0.jar ⊃ portal-web/index.html + 13 scripts`, `client lock ok (openapi-typescript@7.13.0, contract 8bf4e71cee7f)`, `dist scan ok (16 files, 11 app chunks, 28 money properties)`, `bundle size ok: initial 121056 B gzip (cap 130048), 6 lazy routes`, `licenses ok (257 packages, 7 bundled, 8 dev-only exceptions)`, `verifySpringFree` 다섯(`portal-sim` 추가), `files containing the token: 0`.
- `no-docker` 잡: "gradle exit code: 1", "result files: 10, with failures/errors: 10, skipped: 0", "scanPlaintextLeaks: 10 files, 76 forbidden strings, 0 hits", "token occurrences: 0".
- `web-e2e` 잡: `summary.json` — tests 52·passed 52·skipped 0, 누출·axe 파일 각 52, 응답 342·DOM 148·콘솔 줄 36·주소 282·요청 953, CSP 위반 0, 외부 요청 0, **`simIds` 27**(동적 센티널 — 시뮬레이션 응답에서 모은 값), axe 상태 99·위반 0·미완 0. `artifact-scan.json` = 금지 문자열 44(센티널) + 토큰 36, hits 0.

## 3. 완료 기준 증거

| # | 기준 | 증거 | 주입 |
|---|---|---|---|
| C1 | 계약 1.1.0 — 1.0.0 응답이 1.1.0을 통과(추가·완화뿐), `format` 0, `CHECKSUMS`, 엔진 사본 = 엔진 바이트 | `PortalApiCompatibilityTest`(1.0.0 사본 SHA 고정, 구조 대조 — 기존 필수·정의·제약 키워드 불변, 새 파라미터 선택), Phase 3 응답 검증 시험들이 1.1.0으로 그대로 통과(`ApiContract` — 모든 시험 응답), `PortalApiContractTest`(경로 = 인가 표, 레코드 = 스키마, 값 없는 키 0), `EventSchemaTest` 87(`format` 0), `verifyContractChecksums`, `verifyUpstreamContract`(엔진 `2c3b093` 28파일) | S1·S2·S16 |
| C2 | 시뮬레이션 금액은 통과만 | `SimulationPassThroughIT`(성공 샘플 여덟 — 포털 응답 − 포털 사실 넷 = 엔진 응답 JSON, 정수 리터럴 목록이 순서까지 같다, 1.1.0 검증), `SimResponsesTest` 2, `SimulationSchemaParityTest` 3(포털 사본의 속성·필수 = 엔진 스키마), 화면은 계산하지 않는다(C10) — §4.2 | S3 |
| C3 | 곱셈 격리 | `MultiplicationScanTest`(바이트코드 — 위치 셋 = 허용 목록, 금액은 `ForecastScenario#apply` 하나, 폐기 항목 실패, 자기 시험 `Probe`), `SqlArithmeticScanTest`(SQL 산술 넷 = 허용 목록), `ForecastScenarioTest` 10 — §4.4 | S4·S5 |
| C4 | `agentId`는 `Caller`에서만, `policyKey`는 그 설계사 집합 안에서만 — 남의 키는 404 같은 바이트, 엔진 호출 0 | `SimulationAuthzIT` 2(남의 설계사·없는 키·다른 테넌트의 키 → 404 같은 바이트·머리, 스텁 호출 0, 행 0, 감사 `AUTHZ_DENIED/MISSING` 셋; `MANAGER` 두 경로 404 `ROLE`), `ApiLayerRulesTest`(진입점만 `Caller`를 받는다 — `domain.access` 포함), `AuthzMatrixTest`(인가 표 ↔ 문서 ↔ 컨트롤러) | S6·S15 |
| C5 | 레이트 리밋 | `RateLimitIT` 2(N 통과·N+1 429 + `Retry-After`, 429는 행·감사·엔진 호출 0·지표 +1; 동시 8 스레드 → 통과 3·429 다섯 — §4.3), `RateLimitRuleTest` 2(`Retry-After` 올림·최소 1, 시계 주입) | S7 |
| C6 | 로그 행 하나, `REQUESTED`가 호출 전 커밋, 실패 행 본문 없음, `simId` = 행, 재구축이 지우지 않음 | `SimulationLogIT` 2(성공 — 저장된 엔진 요청이 샘플 요청과 JCS 같음·응답·룰 스냅샷·엔진 상태 200; 엔진 다운 — `FAILED`·사유·503·본문 NULL), `SimulationUseCasesTest`(순서 tx·lock·settings·facts·window·insert·engine·tx·succeeded), V6 `CHECK`(상태별 열), `RebuildIT`(재구축 전후 `simulation_log` 바이트 같음) | S8 |
| C7 | 엔진 불가·시간 초과 503, 스키마 위반 502, 422 통과(밖은 `ENGINE_REJECTED`), 재시도 0 | `EngineFailureIT` 3(다운·지연(읽기 2초 < 지연 3초)·깨진 200·샘플 S9·S10·S8·목록 밖 422·토큰 없음 → 표, **호출 수 정확히 1(토큰 없음은 0)**, 지표 사유; 보험사 모호 → 422 → `insurerCd`로 200; 로그 스캔), `EngineOutcomesTest` 3 | S9 |
| C8 | 거부 바이트 고정, 로그·응답에 증권번호·요청 값 0 | `SimulationRejectionIT` 2(본문 10개 길이·해시 — §4.1, 같은 코드 같은 머리, 엔진 `message`·증권번호·설계사 ID 0; 1 MiB 본문 → 400·행 0·호출 0), `UniformRejectionIT`, `SimulationPassThroughIT`(센티널 파일의 증권번호 전부 0), `EngineFailureIT.theLogsCarryCodesNotRequestValuesOrEngineMessages`(로그에 코드뿐 — 증권번호·설계사 ID·상품 키·보험료·날짜·엔진 문장 0), `scanPlaintextLeaks`(센티널 +`POL-9999`·`POL-EDGE-0002`·식별자 넷) | S10 |
| C9 | `/me/deferrals` — 막대 문자열(최댓값 0이면 생략), RETENTION은 켠 테넌트만, `held`·항목 원값, `expectedBase` | `DeferralsQueryIT` 5(A-1·손 데이터: 막대·0 생략·RETENTION 400(꺼진 테넌트)·`held`/항목 원값·`expectedBase`·`p = 100`이면 모든 달 `expected` = `expectedBase` = 모두 유지 값·막대 같음·`policyKey` 필터(남의·모르는 키 빈 결과)·84개월 상한), `MeQueriesTest` 13, `QueryStatementIT`(문장 수 5) | S11 |
| C10 | 화면 — 금액 산술 0(새 입력 포함), 시뮬레이션 금액 `simulationLabel`, RETENTION 라벨, 고정 문구 둘·HELD 문구·`ruleSnapshotId`, 16칸 표 그대로 | `phase5.test` 12(타임라인 셋·시뮬레이션 여섯·인계·홈 둘 — 렌더된 모든 금액의 라벨 집합), `labels.test`(새 라벨 셋 ≠ 기존, 16칸 그대로), `messages.test`(변경 유형 = 계약 열거 − `PAYMENT_OVERDUE`, 금지 문구), `rules.test` 5(규칙 파일 = 잠금 해시, `pattern`·필수·`if/then`), `lintRules`(시뮬레이션 전후 차이·변이 응답 문자열 변환·타임라인 비교 → 보고), Phase 4 세 층(불투명 `Won`·ESLint·산출물 스캔 28 속성) | S12 |
| C11 | 차트 라이브러리 0, 초기 로드 ≤ 127 KiB, 새 경로 지연 청크, 외부 출처 0 | `bundle-size.mjs`(초기 121,056 B, **지연 화면 닫힌 목록** — §4.6), `webLicenses`(새 패키지 0 — `pnpm-lock.yaml` diff 0), `scan-dist`, E2E 외부 요청 0 | S13 |
| C12 | E2E 전 시나리오 × 세 프로젝트, axe 0, CSP 0, 누출 0(센티널 확장), 스냅샷 0 | `web-e2e` 52/52 — §2·§4.7. 시나리오 일곱(계획 ⑨): ① 신계약 샘플 S1·S2·S3 ② 해약 S5·S6(한도 상세 인계) ③ 422 S11 → 보험사 → S11b, 샘플 S8은 API 요청 ④ 429(GA03 연타) ⑤ 503(엔진 다운 — 마지막 결과 유지, 한도 화면 정상) ⑥ 타임라인 12/84·HELD·계약 필터·유지율 30% ⑦ 홈 다음 달·전월. `toHaveScreenshot` 0 | S14 |
| C13 | 실 엔진 E2E 스크립트에 시뮬레이션 쌍 | `e2e/engine-feed/run.sh`(머리 주석·⑧ 줄) + `EngineFeedE2eCheck` ③(샘플 S1·S5 요청을 포털로 → 200·계약 통과·증권번호 0, `simulation_log` `NEW_CONTRACT`·`POLICY_EVENT` `SUCCEEDED 200`), README 둘. **미실행**(사용자 실행 — §4.10) | — |
| C14 | Phase 0~4 무손상, `no-docker` 실패, 평문 스캔, 검증 메타데이터 재생성 diff, 주입 ID 단위 | §2(기존 2,660건 전부 통과 + 61), `no-docker` 10/10 실패·스킵 0, `scanPlaintextLeaks` 76 문자열 0, §4.8(빈 `GRADLE_USER_HOME` 재생성 diff 0), 아래 주입 표 | — |

### 주입 기록 (ID 단위 — 엔진 3 + 포털 16, 2판 +1)

각각 단독으로 넣고 해당 검사를 돌려 실패를 본 뒤 되돌렸다. 통합·E2E 주입(S6·S7·S10·S14)은 이 기계에서 Docker를 켜지 않았으므로 **`work/phase-5`를 밑으로 한 초안 PR**(주입 한 커밋)을 열어 CI가 돌게 하고, 실패를 본 뒤 병합 없이 닫고 가지를 지웠다(PR #14·#15·#16·#18 — #17은 E2E 수정 전에 잘린 가지라 결과가 섞여 #18로 다시 열었다).

| ID | 기준 | 주입 | 실패한 곳 |
|---|---|---|---|
| E-1 | (엔진) | 샘플 `event-cancel-7.response.json` 1바이트 | `SimulationContractTest` 1 실패(바이트 대조) |
| E-2 | (엔진) | 샘플 디렉터리에 `stray.json` | 같은 시험 1 실패(닫힌 파일 집합) |
| E-3 | (엔진) | `new-ambiguous` Problem의 `code` 값 | 같은 시험 1 실패(Problem 값 대조 — 순서가 아니라 값) |
| S1 | C1 | 1.1.0에서 `MonthSummary.required`의 `adjustment` 제거 | `PortalApiCompatibilityTest` 1 실패 |
| S2 | C1 | 엔진 사본 `event-cancel-7` 1바이트(-720001) | `verifyContractChecksums` 실패 + `verifyUpstreamContract` "differs from" |
| S3 | C2 | 매퍼가 `clawbackAmount`를 `negate` | `SimResponsesTest`·`SimulationUseCasesTest` 2 실패 |
| S4 | C3 | `MeQueries` 타임라인에 `expected * p / 100` | `MultiplicationScanTest` 1 실패(`MeQueries` 람다 `LMUL`·`LDIV`) |
| S5 | C3 | `AgentQueryRepository.dueMonths` SQL에 `::bigint * :p` | `SqlArithmeticScanTest` 1 실패 |
| S6 | C4 | `policies()`의 설계사 술어 무력화(`agent_id = :agentId OR true` — P5-11) | CI 39947727954 `SimulationAuthzIT.anotherAgentsOrAnUnknownPolicyKeyIsNotFoundWithoutARowOrACall` "expected 404 but was 503"(남의 키가 풀려 엔진까지 갔다) — 85건 중 1 |
| S7 | C5 | 설계사 권고 잠금 문장을 잠그지 않는 `SELECT`로 | CI 39947731601 `RateLimitIT.concurrentRequestsAdmitExactlyTheLimit` "expected 3 but was 4" — 85건 중 1 |
| S8 | C6 | `REQUESTED` 넣기를 엔진 호출 뒤로 | `SimulationUseCasesTest` 1 실패(순서) |
| S9 | C7 | 실패면 엔진을 한 번 더 부름 | `SimulationUseCasesTest` 1 실패(호출 1) |
| S10 | C8 | 422 본문에 엔진 `message` 통과 | CI 39947736651 `EngineFailureIT` 2·`SimulationRejectionIT.tenFixedBodiesAndNoEngineMessage` — 85건 중 3 |
| S11 | C9 | RETENTION을 `held`에도 적용 | `MeQueriesTest.retentionScalesOnlyTheMonthlyExpectedAndKeepsTheBaseHeldAndItems` — 46건 중 1 |
| S12 | C10 | 신계약 결과의 라벨을 `monthLabel('ESTIMATED')`로 | `phase5.test` "new contract: …" — 라벨 집합에 "예상" |
| S13 | C11 | 시뮬레이션 화면을 라우터에서 정적 import | `bundle-size.mjs` "screens/SimulationScreen.tsx is not a lazy route chunk", rc 1(상한만으로는 못 잡는다 — P5-8) |
| S14 | C12 | 신계약 결과 뒤 `history.replaceState`로 주소에 `?sim=<simId>` | CI 39951495956 `web-e2e` — 고정 장치 "simId in addresses", 신계약 결과를 내는 시험 9개(ga01-simulation·ga02-insurer·ga03-ratelimit × 3) 실패, 34 통과 |
| S15 | C4(Q11) | `ApiAuthorization`에 `Caller`를 받는 공개 메서드 | `ApiLayerRulesTest` 1 실패(규칙 (c)가 `domain.access`도 본다) |
| S16 | C1(F4) | `DigitCount` 설명문의 따옴표를 뺌 | `PortalApiContractTest.noKeyWithoutAValue` "['/components/schemas/DigitCount/1.1.0)']" |
| S17 | 심사 B1(2판) | `dueMonths`의 `HAVING`을 뺌 | CI 40019619655(초안 PR #19, 병합 없이 닫음): `DeferralsQueryIT.aMonthWithOnlyCancelledItemsHasNoMonthButKeepsItsItem` — 월 `["202609", "202610", "202811"]`, 87건 중 1. `web-e2e` GA04 "12개월·84개월, 보류 범례, 막대 = API 문자열, 계약 필터"가 세 프로젝트에서 실패(84개월 행 텍스트), 같은 파일의 직렬 뒤 시험 둘 × 3 건너뜀 — 52건 중 실패 3·건너뜀 6 |

## 4. 지시문 §5 추가 항목

### 4.1 거부 바이트 표 (최종 — 본문 10개, `ProblemResponses`)

지시문은 "7행"이었고 계획 Q19가 10개로 정했다(기존 넷 + 시뮬레이션 여섯). 아래 길이·SHA-256 앞 12자는 CI run 39951478495의 `SimulationRejectionIT` 출력 `REJECTION_BYTES …`와 같다(결과 XML에서 읽었다).

| 상태 | `code` | 길이(B) | SHA-256 앞 12자 | 언제 |
|---|---|---:|---|---|
| 401 | `UNAUTHENTICATED` | 62 | `83ec09b61b9d` | 토큰 사유 여덟 — Phase 3 |
| 404 | `NOT_FOUND` | 42 | `caa8747f38d8` | 링크·역할·남의(없는) 자원·없는 경로, **남의·없는 `policyKey`**(엔진 호출 0) |
| 400 | `INVALID_REQUEST` | 54 | `e97da8643de1` | 쿼리·본문 형식, 본문 > 2 KiB, `Content-Type` 밖, REDUCE에 새 월납 없음 |
| 500 | `INTERNAL_ERROR` | 52 | `852112e471f3` | 예상 밖 — Phase 3 |
| 422 | `INSURER_AMBIGUOUS` | 63 | `b5b2831d3004` | 엔진 422 그 코드 |
| 422 | `UNSUPPORTED_EVENT_TYPE` | 70 | `ea683a8ffbbb` | 엔진 422 그 코드(`PAYMENT_OVERDUE` — API로만) |
| 422 | `ENGINE_REJECTED` | 61 | `3bb00cf8019c` | 닫힌 목록 밖의 엔진 422 |
| 429 | `RATE_LIMITED` | 56 | `d4bca6294f54` | 설계사당 60초 창 ≥ N — `Retry-After`만 요청마다 다르다 |
| 502 | `ENGINE_CONTRACT` | 63 | `bfb210b89cec` | 엔진 200 스키마 위반·정수 아님, 엔진 400·404, 예상 밖 상태 |
| 503 | `ENGINE_UNAVAILABLE` | 60 | `8ea3a208cd6b` | 연결 실패·읽기 시간 초과·엔진 5xx·401/403·토큰 파일 없음 |

같은 코드면 사유가 달라도 같은 바이트·같은 머리다(502 — 엔진 404와 스키마 위반, 503 — 엔진 503과 시간 초과). 503에는 Tomcat이 늘 `Connection: close`를 붙인다(홉 머리 — P5-10).

### 4.2 엔진 샘플 ↔ 포털 응답 (쌍마다)

샘플은 엔진 `SimulationContractTest`가 엔진 응답과 대조하는 파일의 사본(엔진 `2c3b093`)이고, 스텁은 요청의 JCS 정규형이 같은 샘플의 바이트를 그대로 낸다(없으면 500 — 시험이 고른 요청이 샘플 밖).

| 샘플 | 엔진 상태 | 엔진 답 | 포털 답 | 시험 |
|---|---|---|---|---|
| S1 `new-limit-exceeded` | standard | 200 | 200 — 엔진 속성 값 그대로 + 포털 사실 넷 | `SimulationPassThroughIT`, `SimulationLogIT`, E2E ga01·ga03 |
| S2 `new-curve` | standard | 200 | 200 | `SimulationPassThroughIT`, E2E ga01 |
| S3 `new-no-limit` | standard | 200(한도 없음) | 200 — 화면 "한도 원장 대상 아님" | `SimulationPassThroughIT`, E2E ga01 |
| S4 `new-term-3` | standard | 200 | 200 | `SimulationPassThroughIT` |
| S5 `event-cancel-7` | standard | 200 | 200 | `SimulationPassThroughIT`, E2E ga01(한도 상세 인계) |
| S6 `event-cancel-6` | standard | 200 | 200 | `SimulationPassThroughIT`, E2E ga01 |
| S7 `event-reduce` | standard | 200 | 200 | `SimulationPassThroughIT` |
| S8 `event-unsupported` | standard | 422 `UNSUPPORTED_EVENT_TYPE` | 422 같은 코드, 포털 고정 바이트 | `EngineFailureIT`, `SimulationRejectionIT`, E2E ga01(API) |
| S9 `event-unknown-policy` | standard | 404 `POLICY_NOT_FOUND` | 502 `ENGINE_CONTRACT` | `EngineFailureIT`(+로그) |
| S10 `new-unknown-agent` | standard | 404 `AGENT_NOT_FOUND` | 502 `ENGINE_CONTRACT` | `EngineFailureIT`(+로그), `SimulationRejectionIT` |
| S11 `new-ambiguous` | ambiguous | 422 `INSURER_AMBIGUOUS` | 422 같은 코드 | `EngineFailureIT`, `SimulationRejectionIT`, E2E ga02 |
| S11b `new-ambiguous-samlife` | ambiguous | 200 | 200 | `SimulationPassThroughIT`, `EngineFailureIT`, E2E ga02 |
| S12 `event-reduce-no-premium` | standard | 400 | 보내지 않는다 — 포털 400 `INVALID_REQUEST`(계약 `if/then`), 화면은 규칙 파일로 미리 막는다 | `SimRequestsTest`, `rules.test`, `phase5.test` |

엔진 400의 502 분류는 `EngineOutcomesTest`가 본다(포털이 그 요청을 보내지 않아 통합 시험에서는 만들 수 없다).

### 4.3 레이트 리밋 동시성 수치

CI run 39951478495 `RateLimitIT` 출력: `RATE_LIMIT_CONCURRENCY threads=8 limit=3 admitted=3 limited=5 statuses=[503, 503, 503, 429, 429, 429, 429, 429]`. 테넌트 RL(`sim_rate_per_minute` 3)의 한 설계사가 같은 순간 8건(N+5) → 통과(행을 남긴 것) 3·429 다섯, 그 설계사의 `simulation_log` 행 3. 통과한 셋이 503인 것은 그 설계사(`A-1002`)의 요청이 엔진 샘플 밖이라 스텁이 500을 내기 때문이다 — 시험은 통과 수와 행 수만 본다("the engine answer does not matter here"). 잠금을 빼면(주입 S7) 통과가 4가 됐다. **2판(F10)**: 동시 시험을 자기 테넌트 `BURST`(한도 3)의 샘플 설계사(A-1001)로 옮겼다 — CI run 40019597220: `RATE_LIMIT_CONCURRENCY threads=8 limit=3 admitted=3 limited=5 statuses=[429, 429, 200, 200, 429, 429, 429, 200]`, 통과 셋 전부 200, 행 셋 `SUCCEEDED`, 엔진 호출 3(P5-17). 순차 시험: N(3) 통과 뒤 넷째 429 + `Retry-After` 1~60 정수, 429는 행·감사·엔진 호출 0, `api_rate_limited_total{tenant}` +1.

### 4.4 `ForecastScenario` 격리 증거 (스캐너가 본 곱셈·나눗셈 위치)

`MultiplicationScanTest`(JDK 25 Class-File API — 포털 9모듈 main의 `LMUL`·`LDIV`·`LREM`·`IMUL`·`IDIV`·`IREM`과 `Math`·`StrictMath`의 `multiply*`·`*Div`·`*Mod`)가 본 위치는 셋이고 허용 목록과 같다(폐기 항목이 남으면 실패):

| 위치 | 연산 | 금액인가 | 사유 |
|---|---|---|---|
| `com.ga.portal.query.rule.ForecastScenario#apply` | `Math.multiplyExact`·`Math.floorDiv` | **예 — 유일** | `floor(expectedBase × p / 100)`(설계서 §6.4, `CLAUDE.md` 규칙 1의 예외) |
| `com.ga.portal.query.rule.LimitStage#of` | bp 비교의 곱 | 아니오 | 소진율 bp 비교 — 금액을 만들지 않는다(Phase 3 D-13) |
| `com.ga.portal.infra.persistence.AgentQueryRepository#lines` | `page × size` | 아니오 | 페이지 오프셋 |

`SqlArithmeticScanTest`(모든 main SQL의 산술 `*`·`/`·`%`, `count(*)`·`select *` 제외)가 본 곳은 넷 — 임계치 % → bp(`round(… * 100)`, `SyncStatusRepository` 둘·`TenantRepository` 둘) — 이고 허용 목록과 같다. 막대 비율은 SQL이 아니라 플랫폼 `Ratio.of(n, d).toPercentString()`(정수 연산 문자열)이다.

### 4.5 타임라인·시뮬레이션 라벨 표 (최종 — `labels.ts`·`messages.ko.json`)

| 금액 | 라벨 함수 | 문구 |
|---|---|---|
| 타임라인 `months[].expected`(모두 유지) · 홈 다음 달 분급 | `forecastLabel()` | 분급 예정 |
| 타임라인 `months[].expected`(유지율 가정 적용) | `retentionLabel(p)` — `p`는 응답 값 | 참고(유지율 {p}% 가정) |
| 타임라인 `months[].expectedBase` | `forecastLabel()` | 분급 예정 |
| 타임라인 `months[].held` | `deferralLabel('HELD')` + 범례 | 보류 — "보류: 지급 조건 미충족으로 멈춘 분급 — 예정 합계에 넣지 않음" |
| 타임라인 항목 `amount` | `deferralLabel(status)` | 예정 · 이번 달 지급 대상 · 보류 · 취소 |
| 시뮬레이션 결과의 모든 금액(회차·한도액·예상 누적·시책·삭감·첫해·스케줄·환수·변경 전/후·취소 분급·채권 잔액) | `simulationLabel()` | 시뮬레이션 |
| 홈 전월 | `monthLabel(previous.status)`, 세후는 `netView(previous…)` | 예상 · 확정 · 지급완료(16칸 표 그대로) |

새 라벨 셋은 서로 다르고 Phase 4 라벨(월 상태 넷·정정·원장·계약·지급·분급 넷·채권 넷) 어느 것과도 다르다(`labels.test`). 고정 문구: 타임라인 "분급 확정 스케줄만 반영 · 참고치 · 비확정", 시뮬레이션 "설계사 참고용 · 비확정 · 고객 제시 불가"(화면 머리 + 결과마다). 해약 영향의 엔진 값은 "{월} 세전·환수 상계 합계 — 변경 전 / 변경 후"(Q24 — "실수령"이라 쓰지 않는다, 차이는 내지 않는다). 금지 문구(`messages.test`): 보류를 "다시 받/돌려받/회복/추후 지급/나중에 지급/받을 수 있"으로, 시뮬레이션 문구의 "실수령".

### 4.6 번들 (CI run 39951478495 `bundle-size.json`, gzip 바이트)

| 초기 로드 | Phase 4 | Phase 5 |
|---|---:|---:|
| `index.html` | 375 | 369 |
| `assets/index-*.js`(진입) | 2,822 | 3,520 |
| `assets/index-*.css` | 1,683 | 2,096 |
| 홈·공통 정적 청크(`dday-*` → `kst-*`) | 4,663 | 5,992 |
| `assets/vendor-*.js` | 108,238 | 108,711 |
| `assets/rolldown-runtime-*.js` | 368 | 368 |
| **합계**(상한 130,048) | **118,149** | **121,056** |

| 경로 청크(지연) | 파일 | gzip B |
|---|---|---:|
| `/statements`, `/statements/:ym` | `StatementScreen`·`LimitCut` | 2,264 |
| `/lines/:calcId` | `LineScreen`·`LimitCut` | 1,476 |
| `/limits` | `LimitsScreen` | 1,055 |
| `/limits/:ledgerId` | `LimitScreen`·`LimitsScreen`·`LimitCut`·`handoff` | 2,511 |
| **`/deferrals`** | `TimelineScreen`·`policyOptions`(공유 — `Choices` 포함) | **2,948** |
| **`/simulations`** | `SimulationScreen`·`policyOptions`·`handoff` | **4,488** |

새 npm 의존성 0(`pnpm-lock.yaml` diff 0, 차트 라이브러리 없음 — CSS 막대 + 표). 초기 로드 +2,907 B는 홈의 다음 달·전월(넷째 콜 훅·라벨)과 메시지 데이터다. 지연 화면 여섯은 닫힌 목록(`LAZY_SCREENS`)이다.

### 4.7 E2E — axe·누출·CSP

- 새 화면 상태 axe 36개(프로젝트마다 12 — 시뮬레이션 신계약 셋·해약·엔진 다운, 보험사 둘, 429, 타임라인 12·84·유지율, 홈 다음 달·전월) 위반 0. 전체 99 상태 위반 0·미완 0.
- 누출: Phase 4 고정 장치 그대로(DOM·응답·콘솔·주소·요청 주소·저장소 다섯 종) + **동적 센티널 `simId`**(시뮬레이션 응답에서 모은 27개 — 주소·요청 주소·콘솔·현재 주소 0, DOM에는 보이도록 설계됨 — Q20). 정적 센티널은 E2E 스트림의 증권번호 전부(`POL-EDGE-0002` 추가)와 `pii-sentinels.properties` `e2e` 키(`POL-9999`·`POL-EDGE-0002` 추가) — `run.mjs`가 두 목록을 대조한다.
- CSP: 위반 0. 첫 실행에서 WebKit `<select>`가 `style-src-attr`를 냈다 — 요소를 바꿨고 CSP는 그대로다(F7). CSP 표식 줄에 위반 요소 이름을 더했다.

### 4.8 의존성·검증 메타데이터 diff (빈 `GRADLE_USER_HOME`에서 재생성)

`aba8131`에서 빈 `GRADLE_USER_HOME`으로 `./gradlew resolveAndLockAll --write-locks --write-verification-metadata sha256 printPlatformArtifacts` → **작업 트리 diff 0**(락·`gradle/verification-metadata.xml` 그대로, 플랫폼 13파일 SHA-256은 인계 §6 표와 같다). 이 Phase의 Gradle 변경은 `portal-sim/gradle.lockfile` 하나(3단계 — `json-schema-validator` 3.0.6·`jackson-dataformat-yaml`·`platform-canonical`(시험 픽스처) 등 **다른 모듈이 이미 쓰는 판**을 이 모듈에 더했다 — 새 아티팩트 0). npm 변경 0.

### 4.9 `pnpm audit` 기록

CI run 39951478495의 `build/web/audit.json`: 권고 0, 심각도별 info 0·low 0·moderate 0·high 0·critical 0, 의존성 297(런타임 11). 올림 없음.

### 4.10 실 엔진 E2E

`e2e/engine-feed/run.sh`는 **미실행**(사용자 실행). 이 Phase는 판정(`EngineFeedE2eCheck` ③)에 시뮬레이션 한 쌍을 더했다 — 실 엔진의 값은 엔진 시드에 따라 다를 수 있어 대조하지 않고, 엔진 답이 사본 스키마를 통과(포털 200)·포털 계약 통과·로그 행 둘을 본다. 실행하면 `RESULT.md`에 "시뮬레이션 한 쌍" 절이 생긴다.

## 5. 계획과 다르게 한 것·해석·발견

구현 차이 P5-1~15는 `docs/DECISIONS.md`. 여기에는 발견과 그 처리:

| # | 무엇 | 처리 | 표시 |
|---|---|---|---|
| F1 | **엔진 빠른 티어 불안정** — Problem 본문을 `Map.of`로 만들어 키 순서가 실행마다 달랐다. 샘플 바이트 대조가 1/3 확률로 실패 | 엔진 main은 두고 시험만 고쳤다(Problem은 JSON 값으로 대조, 200 본문은 바이트 그대로). 엔진 PR #8 댓글·엔진 설계서 v1.2.8 | **발견·수정**(엔진) |
| F2 | 계약 1.1.0이 커밋 둘(P5-1) | 계약 시험이 경로를 인가 표·레코드에 묶는다 | 기록 |
| F3 | **본문 상한을 다 받은 뒤에 검사했다** — 커밋 보안 검토 | 상한+1바이트까지만 스트림에서 읽는다(P5-2) — 단위 2 + 1 MiB 통합 시험 | **발견·수정** |
| F4 | **계약 1.1.0 설명문 여덟 곳이 값 없는 키를 만들었다** — 흐름 매핑의 따옴표 없는 쉼표. 생성 클라이언트의 설명이 잘려 보였다(규칙 파일을 만들다 발견) | 따옴표, `noKeyWithoutAValue`(주입 S16) — 뜻 변화 없음(P5-3) | **발견·수정** |
| F5 | 규칙 (c)가 `PolicyResolver.policyNo(Caller, …)`를 진입점으로 잡았다 | 규칙을 좁히지 않고 포트가 `(TenantId, AgentId, key)`를 받게 옮겼다(P5-4) | 기록 |
| F6 | **3단계 첫 CI(run 39945321894)에서 통합 6건 실패**: ① `SimulationController`에 웹 조건이 없어 CLI·폴링 맥락이 뜨지 않았다(2) ② 닫힌 컨트롤러 목록 시험(`PortalApplicationIT`)에 시뮬레이션 API가 없었다 ③ 시험 행 판독기가 뒤 열의 `wasNull()`을 읽어 `engine_status`가 0/NULL로 바뀌었다(2) ④ 503에 Tomcat `Connection: close` — 503과 502의 머리를 비교했다(P5-10) | `bb8bb51` — ① 조건 ② 목록에 더함(Phase 5가 연 API) ③ `getObject(…, Integer.class)` ④ 같은 코드끼리 비교 | **발견·수정** |
| F7 | **WebKit `<select>`가 CSP `style-src-attr` 위반** — 첫 E2E(run 39949109509)의 mobile-ios만, 화면의 select 수만큼 | 버튼 묶음(`ui/Choices.tsx`, P5-15). CSP는 넓히지 않았다 | **발견·수정** |
| F8 | 첫 E2E: axe `definition-list`(시뮬레이션 "한도 원장 대상 아님" `<p>`가 `<dl>` 안), 막대 접근 이름 기대값에 공백이 빠졌다(브라우저는 칸 사이·금액과 라벨 사이에 공백을 넣는다) | `<p>`를 목록 밖으로, 기대값 정정 | 수정 |
| F9 | **84개월 경계 산수** — 2027-02부터 84개월의 마지막 달은 2034-01이다. 계획 ⑨ "2027-03 ~ 2033-12에 걸쳐 84개월 경계 안팎"과 첫 스트림이 한 달 일렀다(경계 "밖" 항목이 안이었다 — run 39950414203, ga04-timeline 셋 실패) | 경계 쌍 203401(안)·203402(밖)로(`aba8131`). 화면·API의 `to = from + 83`은 처음부터 맞았다 | **발견·수정**(계획 오류) |
| F10 | 레이트 리밋 동시 시험의 통과 셋이 503 | 그 설계사의 요청이 샘플 밖 — 시험은 통과 수·행 수만 본다(§4.3) | 해석 → 2판 개선(P5-17 — 통과 셋 200) |
| F11 | **타임라인에 취소만 있는 달이 0/0 행으로 나온다**(GA04 2030-01 — "0원 분급 예정 · 0원 보류") — API의 월 합(`dueMonths`)은 그 범위의 모든 상태를 묶고 `HAVING`이 없다. 막대는 0.0%·0.0%, 항목 목록에는 "취소"로 보인다 | 이 Phase는 API를 바꾸지 않았다(지시문 ⑬ — 1.1.0 밖 변경 금지, 계약 설명은 "Σ SCHEDULED + RELEASED") — Q5 | **심사 B1 → 2판에서 고침**(API에서 뺀다 — 계약 1.1.1, P5-16) |
| F12 | 응답 누출 스캔의 시뮬레이션 몫을 `SimulationPassThroughIT`에(P5-9) | 시뮬레이션 테넌트·스텁이 그 기반에 있다 | 기록 |
| F13 | 주입 S6의 모양(P5-11) | 테넌트 전체 문장은 정적 스캔이 먼저 잡는다 — 행동 시험을 증거로 하려고 술어의 뜻만 지웠다 | 기록 |
| F14 | `CLAUDE.md` "경로·쿼리에 `policyKey` 금지" → "화면 주소(경로·쿼리)에"(P5-13) | Q15가 API 쿼리를 더했다 — 대상만 글자로 | **확인 요청**(규칙 파일 문구) |
| F15 | 계약 상품 목록은 한도 카드에서 — 카드 200장이면 버튼 200개 | 그대로(Q7 — §10 카드 수 200) | Q6 |
| F16 | 영문 번역(`docs/en/`)은 v1.6 그대로 | Phase 4 수용 Q8(요청이 있을 때만) | 기록 |

도구 출력 중 지시문처럼 보이는 문장은 없었다(빌드·테스트 로그, pnpm·Playwright 출력, 엔진 저장소 출력, 의존성 문서). `net.jqwik` 없음. 설계서는 v1.8(변경 이력 맨 위). 미확정은 §14.1 #1(범위 밖)·#7(닫음)·#13(V7 후보)으로 정리했고 새 `TODO(confirm#N)`은 없다.

## 6. 다음 Phase 질문

**Phase 6(알림 파생·이의제기 — 지시문 §5)**:

- **Q1 알림 파생 규칙(§6.5)**: 파생 지점이 프로젝터(이벤트 적용 트랜잭션 안)인지 별도 파생기(커서 뒤 읽기)인지. 재생·재구축은 파생하지 않는다(§14.1 #12 기본 방향) — 프로젝터에 두면 재생 표식이 필요하고, 파생기에 두면 "새 사실" 경계가 커서 하나로 정해진다. 권장: 파생기(`portal-projection`의 규칙은 순수 함수, 실행은 인제스트 뒤 단계).
- **Q2 dedup**: §5 `notification`의 `UNIQUE (tenant_id, agent_id, dedup_key)`(Phase 0 Q1로 설계사별)로 "경계 상향 1회·하향 뒤 재상향 다시"를 표현하려면 키에 상향 회차가 들어가야 한다. 회차를 원장 이력(`source_seq`)에서 뽑을지, 상태 표(마지막 단계)를 둘지.
- **Q3 이의제기 `inquiry`**: 라인 상세 "이 항목 문의하기"가 살아난다. 본문은 설계사 자유 텍스트 — 개인정보(고객명·연락처)를 쓰지 말라는 안내 + 길이 상한 + 평문 스캔 대상인지(로그·응답에 원문 0, DB에만). 저장 표(V7)와 운영 쪽 응답 경로(운영 CLI?)를 Phase 6에서 정할지.
- **Q4 첫 쓰기 API(`PATCH` 읽음 처리·`POST` 문의)의 CSRF**: 시뮬레이션 `POST`와 같은 근거(쿠키 없음·`Authorization` 머리만·같은 출처·CORS 없음 — 설계서 §9 v1.8)로 CSRF 토큰 없이 갈지. 권장 같다 — 다만 `Origin` 머리 검사(같은 출처만)를 한 줄 더할지.
- **Q5(F11) 취소만 있는 달**: 타임라인 월 합에서 예정·보류가 모두 0인 달을 API가 빼야 하는지(계약 1.1.x — 표기 변경), 화면이 숨겨야 하는지(화면 판단 — 금액 비교가 아니라 `isZero` 둘), 지금처럼 0/0으로 보일지.
- **Q6(F15) 계약 고르기**: 계약이 많은 설계사(카드 수십~200)의 타임라인 필터·해약 영향 계약 선택 — 검색 입력 또는 한도 카드에서만 진입(인계)으로 좁힐지. v2 페이지(Q7)와 같이 볼지.

**이 Phase 결과에 대한 것**:

- **Q7(F14)** `CLAUDE.md` 화면 규약 문구 "화면 주소(경로·쿼리)" — 이대로 둘지.
- **Q8** 실 엔진 E2E(`run.sh` — Phase 3·5 판정 둘)는 여전히 미실행 — 병합 뒤 `main`에서 사용자 실행으로 둘지(Phase 3 Q12와 같게).

## 7. 2판 — `phase-5.1` (수용 심사 조건 B1, 2026-10-24)

심사 `docs/phase-05-수용심사.md` §2: `dueMonths`가 `GROUP BY due_ym`뿐이라 항목이 없는 달은 행이 없고 취소만 있는 달은 0/0 행이었다 — 한 응답 안에 두 모양(F11). 결정: API에서 뺀다(화면이 숨기지 않는다).

| 항목 | 바뀐 것 |
|---|---|
| SQL | `AgentQueryRepository.dueMonths`에 `HAVING` 예정 ≠ 0 OR 보류 ≠ 0 — 한 문장 그대로(`QueryStatementIT` 상한 불변). 막대 분모는 `MeQueries.timeline`이 돌려받은 행에서 계산하므로 `HAVING` 뒤 행만 본다(심사 문구의 `max(...) over ()`는 SQL에 없다 — P5-16) |
| 계약 | `portal-api` 1.1.1 — `info`·`months` 설명문만. `PortalApiCompatibilityTest` 3(+1): 1.0.0·1.1.0(`phase-5` 바이트 사본, SHA-256 `8bf4e71c…` 고정) 둘에 대한 추가·완화 대조, 설명문·요약·`info.version`을 뺀 1.1.0과 1.1.1이 같다(제약 하나를 바꾼 사본은 다르다 — 자기 검사). `CHECKSUMS`, `client:lock`(생성물에 설명 한 줄, 규칙 파일 바이트 그대로) — CI "client lock ok (…, contract 2f529ade506b)" |
| 시험 | `DeferralsQueryIT` +1(시드: 설계사 갑 202811 취소 30,000 — 다른 시험 범위 밖): 202609~202811의 월은 202609·202610뿐, 항목은 셋(202811 `CANCELLED` 포함), 월 합·막대는 취소 달 없는 범위와 같다, 202811 한 달은 월 0·항목 1. E2E GA04: 84개월 행 5 → 4(2030-01은 항목 목록에만 "취소"), 막대 4, 계약 필터 행 5 → 4. 골든 파일은 이 경로에 없다 — GA04 기대값이 그 몫 |
| F10(선택) | 동시 레이트 리밋 시험을 테넌트 `BURST`로 — 통과 셋이 샘플 응답 200(§4.3, P5-17) |
| 주입 | S17(§3 표) |
| 문서 | 설계서 v1.8.1(§6.4·§7·§8·변경 이력), DECISIONS P5-16·P5-17과 "Phase 5 수용 심사" 절(DE-22~24·DD-17·결정·Phase 6 재료), 심사 보관 `docs/phase-05-수용심사.md`, README |
| 범위 밖 | 없음 — 화면 코드·번들 구성 변경 없음(초기 로드 121,056 B 그대로). `CLAUDE.md` 문구는 심사 Q7대로 그대로 |

CI run [40019597220](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/40019597220)(`5d9d979`, build·no-docker·web-e2e 모두 success — 아티팩트를 내려받아 셌다): Java **2,723**(+2 — `portal-query` test 47(`PortalApiCompatibilityTest` +1), `portal-app` integrationTest 87(`DeferralsQueryIT` +1)), 실패 0·스킵 0. Vitest 98. Playwright 52/52(건수 그대로 — GA04 기대값만 바뀜), 스킵 0, axe 99 상태·위반 0, CSP 위반 0, 외부 요청 0, `simIds` 27. `verifyContractChecksums` 일치, `scanPlaintextLeaks: 113 files, 76 forbidden strings, 0 hits`, `bundle size ok: initial 121056 B gzip`. `no-docker`: "result files: 10, with failures/errors: 10, skipped: 0".

