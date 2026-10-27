# Phase 7 보고서 — ga-agent-portal 대사·DRIFT 자동 복구·관측성 (지시문 v1.0)

> 지시문 `docs/phase-07-지시문.md` · 계획 `docs/phase-07-계획.md`(승인 2026-10-27 — Q1~Q14 권장안 전부(Q1 = ⓑ′), 돌이킬 수 없는 결정 셋 모두 예, 조건 A·B, `docs/phase-07-계획승인.md`) ·
> 브랜치 `work/phase-7` · PR [#28](https://github.com/hjryoo-ai/ga-agent-portal/pull/28) · 태그 `phase-7`(이 보고서 커밋). **병합은 수용 심사 회신 뒤.**
> 엔진 변경: 명세 샘플 PR(엔진 #9 — 엔진 CI 통과 뒤 병합, 병합 커밋 `38cdcee`, 승인 결정 1). 엔진 소스 변경 0(샘플 시험 코드만). `run.sh`(실 엔진 E2E)는 사용자 몫 — **미실행**(§4.11).
> **심사에서 결정이 필요한 것 하나**: P7-15(ETag 재료에 데이터 표식 — 설계서 §7 v1.6의 의미 변경, §5 F5). 설계서 v1.10과 코드는 같은 커밋으로 고쳤다.

이 보고서의 **"주입 S…"**는 계획 ⑧의 주입 ID다(S11b·S13 둘째 판은 이 Phase에서 더한 것 — §3). **"ⓐ…ⓔ"**는 지시문 ⑤의 E2E 시나리오다.

## 1. 커밋

`origin/main` = `b87db17`(Phase 6 병합) 위 `work/phase-7`:

| 커밋 | 내용 |
|---|---|
| `1a8b871` | Phase 6 수용 심사 보관(조건 없음), DECISIONS — Phase 7 재료 Q1~Q6, README |
| `9d10fde` | 지시문 v1.0·계획(Q1~Q14, P-1~P-15) |
| `dfd8c45` | 계획 승인 보관 — 조건 A·B, DE-27·DE-28·DD-19 |
| `126d848` | 0단계 — 엔진 명세 샘플 사본(엔진 `38cdcee`, 묶음 셋·19 파일), `UPSTREAM`·`CHECKSUMS`, `verifyUpstreamContract` 확장, `StatementSamplesTest` |
| `c726e52` | 1단계 — V8 `recon_run`(값 열 0), 열거 셋, `StatusEnumDdlTest`·`TenantPredicateScanTest` |
| `bcffb79` | 2단계 — `portal-recon` 판정 순수 함수 `ReconRules` + `ReconRulesTest`(표 전수) |
| `abf2e8f` | V9 — V8의 `CHECK`가 NULL `rebuild_outcome`을 통과시켰다(F1, P7-6) |
| `cdc68b9` | 3단계 — 엔진 명세 클라이언트·응답 검증·샘플 스텁(`StubStatementEngine` — 샘플 밖 500) |
| `1e17824` | 3단계 — 의존성: 트레이스 브리지·시험 exporter, 잠금·검증 메타데이터 재생성(§4.8) |
| `b311ddc` | 4·5단계 — 회차 R0~R6·자동 복구·`NotificationRules.monthRepaired` + `ReconRunnerTest` |
| `7ed1b98` | 4·5단계 — 앱 배선: JDBC 저장소, 회차 잠금, 재구축 어댑터, 엔진 어댑터, CLI `recon run`·`status`, 스케줄러, 통합 시험 |
| `f4dc0d0` | `TenantContextGuardTest`에 새 저장소·메서드(F2, P7-14) |
| `643475e` | 6단계 — `docs/지표.md`(닫힌 표 20), `MetricsCatalogTest`, `PortalMeters`(커밋 뒤에만 센다) |
| `a488394` | 6단계 — 스팬 포트 `Tracing`, 인제스트·대사·엔진 스팬, `TracingLeakIT` |
| `e291afd` | 계획의 남은 검사 — `ReconResultWriteIT`, 파생 호출자 닫힌 목록(ArchUnit), 예산·동시 1, `ReconSchedulerIT` |
| `e3ece4a` | 7단계 — E2E: GA06, 하네스 제어(명세 스텁·라인 누락·자동 재구축·CLI 대사), ⓐ~ⓔ, `run.sh` 판정 ⑤ |
| `75b1a15` | `TracingLeakIT` 마지막 단언(시험 쪽 — F3) |
| `3147ea6` | 8단계 — 설계서 v1.10, `CLAUDE.md` "대사·관측성 규약", `docs/운영.md` §8, DECISIONS P7-1~14, 스케줄러 스레드 2(P7-5) |
| `4c47bff` | `ReconArithmeticScanTest` — 주입 S2가 기존 스캔을 통과했다(F4, P7-16) |
| `0dcbc6f` | **ETag에 커서 갱신 시각·마지막 대사 회차**(E2E ⓑ가 찾은 304 묵은 본문 — F5, P7-15) |
| `9af9ee0` | 평문 스캔이 내보낸 스팬을 읽는다(F6, P7-17), `docs/지표.md` 문구 정정 |
| (이 커밋) | 보고서 |

```
contracts       shared/ga-commission-engine/samples/statements/ (a1·estimate-cycle-01·estimate-cycle-05 — 19 파일) · UPSTREAM · CHECKSUMS
portal-domain   RebuildOutcome · ReconRunResult · trace/Tracing(포트 — tenant·count·kind)
portal-projection  notify/NotificationRules.monthRepaired · StatementSamplesTest
portal-recon    (새 모듈) rule/ReconRules·Verdict·InconclusiveReason · ReconRunner(R0~R6) · engine/HttpStatementClient·StatementResponses
                port/ ReconStore·ReconEngine·ReconLock·ReconRebuild·ReconMetrics·Pacer · testFixtures/testing/StubStatementEngine
portal-infra    V8__recon_run.sql · V9__recon_run_outcome.sql · ReconRunRepository · ReconLockRepository · SyncStatusRepository.lastRecon
portal-query    port/ReconMarkStore · MeQueries(ETag 데이터 표식)
portal-app      recon/ ReconService·ReconCommands(recon run·status)·JdbcReconStore·HttpReconEngine·RebuilderReconRebuild·TransactionalReconLock
                       MicrometerReconMetrics·TenantReconParams·ReconProperties·ReconConfiguration·ReconSchedulingConfiguration · PortalMeters
                trace/ MicrometerTracing·TracedFeedClient·TracedSimulationClient·TracingConfiguration
                integrationTest/ Recon*IT·AutoRepairIT·StatusCliIT·TracingLeakIT·WebE2eHarness(GA06·제어) · archTest/ MetricsCatalogTest·ReconArithmeticScanTest
web             e2e/specs/ga06-recon.spec.ts · e2e/support/env.ts (화면 원천 변경 0)
docs            설계서 v1.10 · 지표.md(신설) · 운영.md §8 · CLAUDE.md 대사·관측성 규약 · DECISIONS P7-1~17
```

## 2. 테스트 (CI 출처)

CI run [⟦RUN⟧](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/⟦RUN⟧)(`9af9ee0` — 마지막 코드·시험 커밋, build·no-docker·web-e2e 모두 success). 수는 그 run의 아티팩트(`test-reports` — 결과 XML·Vitest junit, `web-e2e-reports` — junit·요약·누출·axe·산출물 스캔)를 내려받아 셌다. 기준선은 `main` = `b87db17`(CI run [40485573734](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/40485573734): Java 3,353·Vitest 135 — 같은 스크립트로 셌다, Phase 6 보고서와 같은 값).

| 모듈 | 소스셋 | 건수 | `main` 대비 | 비고 |
|---|---|---:|---:|---|
| `portal-domain` | test | 2,106 | 0 | |
| `portal-projection` | test | 668 | +6 | `StatementSamplesTest` 5(샘플 ↔ payload 스키마), `NotificationRulesTest` +1(`monthRepaired`) |
| `portal-ingest` | test | 84 | 0 | `IngestApplier` 스팬은 기존 시험 그대로(옛 생성자 → `Tracing.NONE`) |
| `portal-query` | test | 43 | +1 | `MeQueriesTest.aReconRunOrARebuildAtTheSameSeqChangesTheEtag`(P7-15) |
| `portal-sim` | test | 15 | 0 | |
| `portal-inbox` | test | 28 | 0 | |
| **`portal-recon`** | test | **1,832** | **+1,832** | 새 모듈 — `ReconRulesTest` 1,807(전수 격자 1,296 = 상태 4 × 엔진 답 9 × 커서 3 × `basisSeq` 3 × 머리 2 × 예산 2, 시드 고정 임의 달 500, 표의 칸 11), `ReconRunnerTest` 17, `HttpStatementClientTest` 5, `StatementResponsesTest` 3 |
| `portal-infra` | test | 10 | 0 | `TenantContextGuardTest`·`StatusEnumDdlTest`는 같은 메서드 안에서 |
| `portal-infra` | integrationTest | 216 | +22 | `ReconRunSchemaIT` 17(V8·V9 `CHECK` — 거부 사례), `RlsIsolationIT` +5(새 표 `recon_run`의 매개변수 사례) |
| `portal-app` | test | 36 | +9 | `PortalMetersTest` 5, `TracedClientsTest` 2, `CliRunnerTest` +2(`recon`·`status` 사용 오류 64, `status` 줄에 달 없음 — 조건 B) |
| `portal-app` | integrationTest | 134 | +21 | `ReconRunIT` 8, `AutoRepairIT` 3, `StatusCliIT` 3, `ReconEngineIT` 2, `ReconSchedulerIT` 2, `ReconResultWriteIT` 1, `TracingLeakIT` 1, `CachingIT` +1(P7-15). `QueryStatementIT`·`IngestPollingIT`·`InquiryRateLimitIT`는 같은 메서드 안에서 |
| `portal-app` | archTest | 79 | +7 | `MetricsCatalogTest` 3, `ReconArithmeticScanTest` 3, `ArchitectureRulesTest` +1(파생 호출자 닫힌 목록·재구축기 → 대사 0) |
| **Java 합계** | | **5,251** | **+1,898** | 실패 0, 스킵 0 |
| `web` | Vitest | 135 | 0 | 화면 원천 변경 0 |
| `web` | Playwright | ⟦E2E_N⟧ | ⟦E2E_D⟧ | `ga06-recon` 다섯(ⓐ~ⓔ) × 세 프로젝트 |

- 같은 run의 빌드 검사: ⟦BUILD_LINES⟧
- `no-docker` 잡: ⟦NODOCKER_LINES⟧
- `web-e2e` 잡: ⟦E2E_SUMMARY⟧

## 3. 완료 기준 증거

| # | 기준 | 증거 | 주입 |
|---|---|---|---|
| C1 | 엔진 샘플 사본 = 엔진 바이트, 응답 검증은 payload 스키마 사본으로, 스텁은 샘플 밖 500 | `verifyUpstreamContract`(명세 19 파일 포함 — 각 줄 `== …@38cdcee…` + SHA-256), `verifyContractChecksums`, `StatementSamplesTest` 5, `StatementResponsesTest` 3(샘플 전부 엔진 뜻대로·계약 위반 200 → `ENGINE_CONTRACT`·오류 본문은 `code`만), `HttpStatementClientTest.aRequestOutsideTheSamplesIs500AndUnavailable` | S1 |
| C2 | 대사는 세 정수 동등 비교뿐 | `MultiplicationScanTest`·`SqlArithmeticScanTest` **원천 무변경**(`git diff origin/main` 0줄)·허용 목록 무변경, `ReconResultWriteIT`(열 목록 — 차이 열 0, DRIFT 행 = 포털 셋·엔진 셋), `ReconRunnerTest.anEngineFieldThatWasNotComparedIsNull`, **`ReconArithmeticScanTest` 3**(P7-16 — 기존 스캔이 못 보는 덧셈·뺄셈) | S2a·S2b |
| C3 | 판정 표 전수 — 순수 함수 | `ReconRulesTest` 1,807(§4.1 — 격자 1,296이 판정·엔진 호출 수·머리 읽기 수를 신탁과 비교) | S3 |
| C4 | 행은 DRIFT·REPAIRED만, DRIFT 행 + `data_status` 한 tx, 결정론 | `ReconRunIT` 8 — `aMatchingTenantWritesNoResultRowsAndOneRunRow`, `aDriftIsOneBatchRowAndTheMonthIsDriftAndTheSameValuesAreNotWrittenAgain`(Q13), `theDriftRowAndTheDataStatusAreOneTransaction`(롤백), `twoTenantsWithTheSameInputsWriteTheSameRows`(바이트), `anUnavailableEngineIsInconclusiveWithoutRows`, `aLaggingCursorCallsNothing` | S4 |
| C5 | 자동 복구 | `AutoRepairIT` 3 — `turningAutoRebuildOnRepairsTheCorruptedMonthWithoutNewNotifications`(끔 → 재구축 0·DRIFT, 켬 → 감사 `recon:` 재구축 1 → `REPAIRED` 행 + `OK` + 알림 추가 0), `anInconclusiveRecheckWritesNoRowButKeepsTheMonthDrift`(조건 A), `aDriftInTheStreamItselfIsTriedOnceAndNotRebuiltAgain`(GA04 모양 — Q14). `ReconRunnerTest` 17(오늘 이미·시도함·재구축 실패 → `FAILED`·재대사 0, P7-2·P7-3) | S5·S6·S13 |
| C6 | 재구축은 파생 0 그대로, `REPAIRED` 알림은 재대사 tx에서만 | `ArchitectureRulesTest` — Phase 6 규칙 `theRebuilderReachesNoDerivation` **무변경**, 새 `theDerivationRulesHaveAClosedSetOfCallers`(`NotificationRules` 호출자 = `InquiryCommands`·`JdbcNotificationDeriver`·`ReconRunner`, `Rebuilder` → `recon..` 전이 0) | S7 |
| C7 | 재시도 0·동시 1·예산·엔진 불가는 정지도 DRIFT도 아니다 | `ReconRunIT.theEngineIsCalledOnceASubjectOneAtATimeAndTheBudgetLeavesTheRestUncalled`(호출 수 = 대상 수, 스텁의 동시 요청 최대값 1, 상한 1 → 호출 1·`BUDGET` 1), `HttpStatementClientTest`(다운·느림·깨짐 → 호출 1·재시도 0, 404 둘·422 샘플), `ReconEngineIT` 2, `ReconRulesTest` 3단계 | S8 |
| C8 | 지표 이름 ↔ 태그 키 = `docs/지표.md` | `MetricsCatalogTest` 3(원천 정적 스캔 ↔ 표 양방향 20, 닫힌 태그 키, 등록 모양 셋 — P7-8), `PortalMetersTest` 5, `IngestPollingIT`(앱 조립의 레지스트리에 `ingest.`·`recon.`·`engine.` 지표 0 — Observation 타이머 없음) | S9·S10 |
| C9 | 트레이스·로그 속성에 값 0 | `TracingLeakIT`(실제 SDK·메모리 내보내기 — 스팬 11 이름·부모·키 ⊆ 표, 값 30개 넘게 훑어 센티널·스트림 원문 0, `recon.run` 속성 = `{tenant, trigger, checked, drift_new}`), `TracedClientsTest` 2, `ReconRunnerTest`(스팬 속성은 종류·수만, 종류 꼴 아니면 `OTHER`), `IngestPollingIT`(앱 조립의 스팬), 기존 `CapturedOutput` 누출 시험 | S11·S11b |
| C10 | `status` CLI | `CliRunnerTest` +2(사용 오류 64, 줄에 `\d{6}` 0 — 조건 B), `StatusCliIT` 3(OPERATOR 인가, 링크 없는 주체 거부, `--all` 거부 섞임 → 2, 출력에 식별자 원문 0) | S12 |
| C11 | E2E ⓐ~ⓔ × 세 프로젝트 | `web-e2e` ⟦E2E_N⟧/⟦E2E_N⟧(§2·§4.7), axe·CSP·누출 0, 화면 원천(`web/src`) 변경 0 — 번들 gzip ⟦BUNDLE⟧ B(Phase 6과 같은 값) | S13 |
| C12 | 무손상·`no-docker`·평문 스캔·메타데이터·주입·`run.sh` | §2(기존 3,353건 전부 통과), `no-docker` 12/12 실패·스킵 0, 평문 스캔 0건(트레이스 덤프 포함 — P7-17), 새 의존성 diff 추가만(§4.8), 아래 주입 표, `run.sh` 판정 ⑤ 추가 — **미실행**(§4.11) | — |

### 주입 기록 (ID 단위 — 13 + 다시 넣은 둘)

각각 단독으로 넣고 해당 검사를 돌려 실패를 본 뒤 되돌렸다. 단위·정적 시험이 잡는 것(S1·S2·S3·S5~S10)은 이 기계에서(`./gradlew --continue …` — 실패 클래스는 결과 XML에서), 통합·E2E만 잡는 것(S4·S11·S12·S13)은 Docker를 켜지 않았으므로 **주입 한 커밋의 초안 PR**(밑 `work/phase-7`)로 CI를 돌리고, 결과를 본 뒤 병합 없이 닫고 가지를 지웠다. 커밋 보안 검토가 주입 커밋의 덩어리(S11b·S12·S13)를 짚었다 — 버릴 가지의 의도된 위반이라 작업 가지는 그대로(§5 F9).

| ID | 기준 | 주입 | 실패한 곳 |
|---|---|---|---|
| S1 | C1 | 명세 샘플 `a1/202608-confirmed.response.json` 한 바이트 | `verifyUpstreamContract` — "differs from …@38cdcee…"(기준선은 명세 19 파일 일치) |
| S2a | C2 | 대사 SQL에 `portal_gross - engine_gross` | `ReconArithmeticScanTest.reconSqlHasNoArithmetic`. **첫 모양(같은 뺄셈)은 기존 두 스캔을 통과했다**(F4) |
| S2b | C2 | `ReconRunner#drift`에 `long` 뺄셈 | `ReconArithmeticScanTest.theReconcilerHasNoLongArithmetic`(`LSUB`) |
| S3 | C3 | 판정 1단계(뒤처짐) 제거 | `ReconRulesTest` 1,807 중 865, `ReconRunnerTest` 17 중 1 |
| S4 | C4 | DRIFT 행과 `data_status`를 다른 트랜잭션으로 | PR [#29](https://github.com/hjryoo-ai/ga-agent-portal/pull/29), run 40499849823: `ReconRunIT.theDriftRowAndTheDataStatusAreOneTransaction` — "[the DRIFT row rolled back with the data_status update] Expecting empty"(통합 시험 133 중 1) |
| S5 | C5 | 재대사 없이 `REPAIRED` | `ReconRunnerTest` 17 중 4 |
| S6 | C5 | "시도함" 판정 제거(미해결 DRIFT에 매일 재구축) | `ReconRunnerTest` 17 중 1("no daily rebuild") |
| S7 | C6 | `Rebuilder`가 `NotificationRules.monthRepaired`를 부른다 | `ArchitectureRulesTest` 둘(`theRebuilderReachesNoDerivation`, `theDerivationRulesHaveAClosedSetOfCallers`) |
| S8 | C7 | 엔진 실패 시 한 번 다시 호출 | `HttpStatementClientTest` 5 중 2(호출 1 → 2, 3 → 6) |
| S9 | C8 | `recon_runs_total`에 `agent_id` 태그 | `MetricsCatalogTest` |
| S10 | C8 | 표에만 있는 행 `recon_drift_count` | `MetricsCatalogTest`(21 ≠ 20) |
| S11 | C9 | `engine.statement`에 속성 `agent_id` = 원문 + `MicrometerTracing.kind`가 원문 그대로 | PR [#30](https://github.com/hjryoo-ai/ga-agent-portal/pull/30), run 40499855610: `TracingLeakIT` — "attribute keys of engine.statement ⊆ table"(키 단언이 먼저 잡아 값 스캔까지 가지 않았다 → S11b) |
| S11b | C9 | 표에 있는 키 `mode`의 값에 설계사 ID 원문 + `kind` 원문 그대로(키 단언 통과) | PR [#33](https://github.com/hjryoo-ai/ga-agent-portal/pull/33), run 40501300566: `TracingLeakIT` — "[a value of engine.statement] expected: 0L but was: 1L"(실패 문구는 수만 — 값을 되풀이하지 않는다) |
| S12 | C10 | `status` 줄에 `oldest=` + 첫 미해결 키(설계사·달) | PR [#31](https://github.com/hjryoo-ai/ga-agent-portal/pull/31), run 40499861450: `StatusCliIT.statusShowsCountsAndAgesOnly` |
| S13 | C5·C11 | 재대사가 `REPAIRED` 뒤에도 `data_status = DRIFT` | **첫 판** PR [#32](https://github.com/hjryoo-ai/ga-agent-portal/pull/32), run 40499865893(`4c47bff` 위): `AutoRepairIT.turningAutoRebuildOnRepairsTheCorruptedMonthWithoutNewNotifications` — "expected: "OK" but was: "DRIFT"". 그 밑의 E2E ⓑ는 주입 없이도 실패하던 때라(F5) 화면 증거가 못 된다. **둘째 판** PR [#34](https://github.com/hjryoo-ai/ga-agent-portal/pull/34), run 40501530610(`0dcbc6f` 위): 같은 `AutoRepairIT` + ⟦S13_E2E⟧ |

## 4. 지시문 §5 추가 항목

### 4.1 판정 표 (최종 — 설계서 §6.6 v1.10, `ReconRules`)

입력: 고정 커서(상태·`last_seq`·`caught_up_at`), 회차 시작 시각, 포털 달(상태·gross·offset·adjustment), 엔진 답, 예산 남음, 피드 머리(확정 불일치일 때만 읽는다).

| 단계 | 조건 | 판정 | 엔진 호출 |
|---|---|---|---:|
| 1 | 커서 ≠ `RUNNING` 또는 `caught_up_at` 없음 또는 회차 시작 − `caught_up_at` > `recon_caught_up_within_seconds`(경계 포함) | `INCONCLUSIVE(LAGGING)` | 0 |
| 2 | 예산(`recon_max_calls`·`recon_max_seconds`) 소진 | `INCONCLUSIVE(BUDGET)` | 0 |
| 3 | 모드 = 달 상태(`PROVISIONAL`·`ESTIMATED` → ESTIMATE, `CONFIRMED`·`PAID` → CONFIRMED). 실패 답 → 404 `AGENT_NOT_FOUND`·404 `STATEMENT_NOT_CONFIRMED` → `NOT_CONFIRMED`·422 `MONTH_CLOSED`·400/예상 밖 4xx → `ENGINE_REJECTED`·연결/시간 초과/5xx/401·403 → `ENGINE_UNAVAILABLE`·스키마 위반/다른 모드의 모양 → `ENGINE_CONTRACT` | `INCONCLUSIVE(사유)` | 1 |
| 4 | ESTIMATE: `basisSeq` > 고정 `last_seq` | `INCONCLUSIVE(LAGGING)` | 1 |
| 5 | ESTIMATE: `clawbackOffset`·`adjustment` 같음 / 다름(gross는 보지 않는다 — Q1 ⓑ′, DD-19) | `MATCH` / `INCONCLUSIVE(ESTIMATE_DIFF)` | 1 |
| 6 | CONFIRMED: `gross`·`adjustment`·(있으면)`clawbackOffset` 같음 | `MATCH`(머리 읽지 않음) | 1 |
| 7 | CONFIRMED 다름 + 피드 머리 > 고정 `last_seq` | `INCONCLUSIVE(LAGGING)` | 1 + 머리 1 |
| 8 | CONFIRMED 다름 + 머리 = 고정 `last_seq` | `DRIFT` | 1 + 머리 1 |

격자 1,296칸 각각에서 판정·엔진 호출 수(커서 신선 ∧ 예산 → 1, 아니면 0)·머리 읽기 수(호출함 ∧ CONFIRMED ∧ 다름 → 1)를 신탁과 비교한다. 쓰기 쪽(설계서 §6.6): `MATCH`·`INCONCLUSIVE` 행 0, `DRIFT` → 행 + `data_status = DRIFT` 한 tx(Q13 — 미해결 마지막 행과 일곱 값이 같으면 행 없이), 미해결 DRIFT 달이 첫 판정에서 `MATCH` → 그 자리에서 `REPAIRED`(P7-2).

### 4.2 질문 1 측정 — 스트림별 엔진 ESTIMATE `gross` vs 포털 Σ라인

계획 ① 측정 그대로(포털이 가진 엔진 스트림 전부 — 명세 이벤트를 만날 때마다 그 시점까지의 Σ):

| 스트림 | 이벤트 | gross 엔진 / 포털 Σ | clawbackOffset 엔진 / 포털 Σ | `basisSeq` |
|---|---|---|---|---|
| A-1 둘째 판(+ 변형 둘) | seq 6 Confirmed 202608 | 1,800,000 / 1,800,000 | 0 / 0 | — |
| 〃 | seq 11 Estimated 202702 | 0 / 0 | −720,000 / −720,000 | 10 |
| `engine-e1-cycle` | Confirmed 202608~202611 | 넷 모두 같음 | 넷 모두 같음 | — |
| `estimate_cycle_01_기본` | seq 4 Estimated 202608 · seq 5 Confirmed | 1,890,000 / 1,890,000(둘) | 0 / 0 | 3 |
| `estimate_cycle_02`~`04` | Estimated + Confirmed | 같음 | 같음(`04`: −1,323,000) | 14·9·8 |
| **`estimate_cycle_05_분급_도래`** | **seq 7 Estimated 202801** | **378,000 / 0 — 다름** | 0 / 0 | 6 |
| 〃 | seq 10 Confirmed 202801 | 378,000 / 378,000 | 0 / 0 | — |

→ ESTIMATE `gross`에는 아직 발행되지 않은 분급 도래분이 들어 있다 — 계약 설명문 "Σ 그 달 `CommissionCalculated` 라인"과 다르다(DD-19, 고치지 않음). 그래서 ESTIMATE는 `gross`를 보지 않는다(Q1 ⓑ′). 샘플에 `cycle_05` ESTIMATE를 넣었다(DE-27) — `StatementResponsesTest`가 그 답을 `Estimated(basisSeq 6, gross 378,000, offset 0, adjustment 0)`로 읽고, 판정은 gross를 보지 않는다(`ReconRulesTest.step5_anEstimateComparesOffsetAndAdjustmentButNotGross`).

### 4.3 자동 복구의 트랜잭션 경계 (최종)

| 단계 | 트랜잭션 | 하는 일 |
|---|---|---|
| R0 | **바깥 테넌트 tx — 회차 내내**(P7-4) | `pg_try_advisory_xact_lock(hashtext('portal.recon'), hashtext(tenant))` — 못 잡으면 회차 없음(CLI 2). 커서 고정, 대상 = 창의 `earnings_month` 행 |
| R1 | 없음(엔진 호출) | 대상 순서대로, 호출 사이 `recon_call_interval_ms`, 예산 `recon_max_calls`·`recon_max_seconds`, 재시도 0 |
| R2 | 설계사×달마다 `REQUIRES_NEW` | `DRIFT` → `recon_result`(BATCH, `run_at` = 회차 시작) + `data_status = DRIFT`(+ Q13). P7-2의 즉시 `REPAIRED`도 여기 |
| R3 | `REQUIRES_NEW` | `recon_run` 행(수 — 재구축 전 임시 결과) |
| R4 | `Rebuilder`의 txA/txB/txC 그대로 | 조건(새 DRIFT 또는 시도 안 한 미해결 ∧ `auto_rebuild_enabled` ∧ 오늘 재구축 0) → 재구축, 행위자 `recon:<tenant>`. 결과를 `recon_run.rebuilt_at`·`rebuild_outcome`에(짧은 tx). 실패 → 커서 `FAILED`, 회차 `FAILED`, 재대사 0 |
| R5 | 설계사×달마다 `REQUIRES_NEW` | 미해결 달만 재호출 → `MATCH`: `REPAIRED` 행 + `data_status = OK` + 알림(`STMT_CONFIRMED:{ym}`, `PAID`면 + `PAYOUT:{ym}` — `ON CONFLICT DO NOTHING`) 한 tx. `DRIFT`: DRIFT 다시(Q13). `INCONCLUSIVE`: **행 0** + `data_status = DRIFT`(조건 A·DE-28) |
| R6 | `REQUIRES_NEW` | `recon_run.result` 확정(우선순위 `FAILED` > `DRIFT` > `REPAIRED` > `OK`), 게이지 `recon_unresolved_drift`. 지표 카운터는 커밋 뒤에만(`PortalMeters` `afterCommit`) |

### 4.4 지표 표 (`docs/지표.md` §2 그대로)

| 이름 | 종류 | 태그 키 | 태그 값 | 단위 | 설명 |
|---|---|---|---|---|---|
| `api_authn_rejected_total` | counter | `tenant`, `reason` | `reason` = `NO_TOKEN`·`MALFORMED`·`ALG_NOT_ALLOWED`·`ISSUER_UNKNOWN`·`SIGNATURE`·`EXPIRED`·`AUDIENCE`·`TENANT_MISMATCH` | 건 | 조회 API 401(설계서 §9 여덟 사유) |
| `api_authz_denied_total` | counter | `tenant`, `reason` | `reason` = `NO_LINK`·`REVOKED`·`ROLE`·`MISSING` | 건 | 조회 API 404 인가 거부 |
| `api_jwks_failures_total` | counter | `issuer_hash` | 발급자 해시 | 건 | 등록 발급자의 메타데이터·JWKS를 받지 못한 500 |
| `api_rate_limited_total` | counter | `tenant`, `route` | `route` = `simulations`·`inquiries` | 건 | 429(시뮬레이션 분당·문의 24시간) |
| `sim_requests_total` | counter | `tenant`, `reason` | `reason` = `OK`·`ENGINE_UNAVAILABLE`·`ENGINE_CONTRACT`·`INSURER_AMBIGUOUS`·`UNSUPPORTED_EVENT_TYPE`·`ENGINE_REJECTED`·포털 거절 사유 | 건 | 시뮬레이션 요청 결과 |
| `ingest_events_total` | counter | `tenant`, `result` | `result` = `applied`·`skipped`·`failed` | 건 | 인제스트 이벤트 |
| `ingest_feed_errors_total` | counter | `tenant` | — | 건 | 피드 조회 실패 |
| `projection_drift_total` | counter | `tenant` | — | 건 | 이벤트 대사 DRIFT(마감·지급 합계 ≠ 포털 합산) |
| `ingest_rebuild_seconds` | timer | `tenant`, `outcome` | `outcome` = `succeeded`·`failed` | 초 | 재구축 소요(운영 CLI·자동 재구축) |
| `ingest_lag_seq` | gauge | `tenant` | — | seq | `headSeq − last_seq` |
| `ingest_lag_seconds` | gauge | `tenant` | — | 초 | 뒤처진 동안 커서 갱신 뒤 경과(따라잡으면 0) |
| `ingest_state` | gauge | `tenant`, `state` | `state` = `RUNNING`·`PAUSED`·`FAILED`·`REBUILDING` | 0/1 | 커서 상태 |
| `recon_checks_total` | counter | `tenant`, `result` | `result` = `MATCH`·`DRIFT`·`INCONCLUSIVE` | 건 | 설계사×달 대사 판정(첫 판정과 재대사 모두) |
| `recon_inconclusive_total` | counter | `tenant`, `reason` | `reason` = `LAGGING`·`BUDGET`·`AGENT_NOT_FOUND`·`NOT_CONFIRMED`·`MONTH_CLOSED`·`ENGINE_REJECTED`·`ENGINE_UNAVAILABLE`·`ENGINE_CONTRACT`·`ESTIMATE_DIFF` | 건 | 판정 불가의 사유(행을 쓰지 않는다 — 승인 조건 A) |
| `recon_runs_total` | counter | `tenant`, `result` | `result` = `OK`·`DRIFT`·`REPAIRED`·`FAILED` | 회 | 대사 회차(한 회차 한 값 — 우선순위 `FAILED` > `DRIFT` > `REPAIRED` > `OK`) |
| `recon_unresolved_drift` | gauge | `tenant` | — | 달 | 회차가 끝났을 때 미해결 DRIFT(설계사×달) 수 |
| `notifications_derived_total` | counter | `tenant`, `type` | `type` = 알림 종류 열넷(`NotificationType`) | 행 | 파생기·CLI 답변·재대사가 **커밋해 넣은** 알림 행(같은 키라 넣지 않은 것은 세지 않는다) |
| `inquiries_total` | counter | `tenant`, `action` | `action` = `CREATED`·`ANSWERED`·`CLOSED` | 건 | 문의 접수(설계사 API)·답변·닫기(운영 CLI), 커밋된 것만 |
| `inquiry_open_total` | gauge | `tenant` | — | 건 | 열린(`OPEN`) 문의 수 — 대사 스케줄러 틱이 갱신 |
| `inquiry_open_oldest_age_seconds` | gauge | `tenant` | — | 초 | 가장 오래된 열린 문의의 나이(없으면 0) |

### 4.5 트레이스 스팬 (`docs/지표.md` §4 — `TracingLeakIT`가 이 표와 대조)

| 스팬 | 부모 | 속성 키 |
|---|---|---|
| `ingest.apply` | 없음 | `tenant`, `seq`, `event.type` |
| `ingest.project` · `ingest.derive` · `ingest.cursor` | `ingest.apply` | — |
| `engine.feed` | 부른 곳(인제스트 회차·`recon.check`·`recon.rebuild`) | `outcome` |
| `engine.simulation` | 부른 곳(시뮬레이션 요청) | `outcome` |
| `engine.statement` | 부른 곳(`recon.check`·`recon.recheck`) | `mode`, `outcome` |
| `recon.run` | 없음 | `tenant`, `trigger`, `checked`, `drift_new` |
| `recon.check` · `recon.recheck` | `recon.run` | `mode`, `result` |
| `recon.rebuild` | `recon.run` | `outcome` |

속성 키 합집합 = {`tenant`, `seq`, `event.type`, `outcome`, `mode`, `trigger`, `checked`, `drift_new`, `result`}. 값은 테넌트 ID·수·종류(열거 이름 꼴 — 아니면 `OTHER`)뿐. 내보내기 0(OTLP는 Phase 8 질문).

### 4.6 엔진 호출 수 (회차당 상한·실측)

| 항목 | 값 | 증거 |
|---|---|---|
| 회차당 상한(테넌트 파라미터 기본값) | `recon_max_calls` 500, `recon_max_seconds` 900, 호출 간격 `recon_call_interval_ms` 200 | 설계서 §6.6, `docs/운영.md` §8 |
| 대상 2(설계사 1 × 달 2) | 호출 2, 동시 최대 1 | `ReconRunIT.theEngineIsCalledOnce…`(스텁 `calls()`·`maxConcurrent()`) |
| 상한 1, 대상 2 | 호출 1, `BUDGET` 1(둘째는 호출 0) | 같은 시험 |
| 뒤처진 커서·잠금 못 잡음 | 호출 0 | `ReconRunIT.aLaggingCursorCallsNothing`, `aRunLockHeldByAnotherConnectionMeansNoRun` |
| 다운·느림·깨짐 | 각 1(재시도 0) | `HttpStatementClientTest.downSlowAndBrokenEnginesAreInconclusiveWithoutRetry` |
| 확정 불일치 | 명세 1 + 머리 1 | `ReconRulesTest` 격자 |
| 재대사 | 미해결 달만 1씩 | `ReconRunnerTest` |

### 4.7 E2E 시나리오 (`ga06-recon.spec.ts` — 세 프로젝트 모두)

⟦E2E_TABLE⟧

### 4.8 새 의존성·검증 메타데이터 (`1e17824` — 빈 `GRADLE_USER_HOME`에서 재생성)

`gradle/verification-metadata.xml` +144줄, **제거 0**(`<component>` 18 추가). 잠금 diff(`portal-app/gradle.lockfile` +18) = 아래 전이 목록뿐. 직접 선언은 승인한 둘 + `micrometer-tracing-bridge-otel`(P7-1 — 승인한 모듈은 브리지가 있을 때만 켜지는 자동 구성뿐). 내보내기(OTLP·Zipkin) 0. 그 뒤 `integrationTestCompileClasspath`에 브리지(P7-10 — 잠금은 기존 여섯 줄의 구성 이름만, 새 아티팩트 0).

| 아티팩트 | 판 | 경로 |
|---|---|---|
| `org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry` | 4.1.1 | 앱(승인) |
| `org.springframework.boot:spring-boot-micrometer-tracing` · `spring-boot-opentelemetry` | 4.1.1 | 전이 |
| `io.micrometer:micrometer-tracing-bridge-otel` | 1.7.1 | 앱(P7-1) |
| `io.micrometer:micrometer-tracing` | 1.7.1 | 전이 |
| `io.micrometer:context-propagation` | 1.2.1 | 전이 |
| `io.opentelemetry:opentelemetry-api` · `-common` · `-context` · `-sdk` · `-sdk-common` · `-sdk-trace` · `-sdk-metrics` · `-sdk-logs` · `-extension-trace-propagators` | 1.62.0 | 전이 |
| `io.opentelemetry.semconv:opentelemetry-semconv` | 1.41.1 | 전이 |
| `aopalliance:aopalliance` | 1.0 | 전이 |
| `io.opentelemetry:opentelemetry-sdk-testing` | 1.62.0 | 통합 시험만(승인) |

판은 모두 Spring Boot 4.1.1 BOM이 정한 값이다(덮어쓰기 0). `verifyPlatformPins`(13 파일) 그대로.

### 4.9 홈 콜 수 표 (`QueryStatementIT` — 라인 1,000·원장 200·알림 500)

| 콜 | SQL 문장 | 비고 |
|---|---:|---|
| `/me` | **5** | +1 대사 표식(P7-15) |
| `/earnings/{ym}` | **6** | +1 |
| `/limits?status=ALL`(·`WARN`) | **5** | +1 |
| `/deferrals?from&to` | **6** | +1 |
| `/notifications?size=3` | 4 | 그대로(동기화 문맥을 읽지 않는다) |

데이터 양과 무관한 것은 그대로다. 대가는 콜마다 `recon_run`의 테넌트 마지막 행 한 번(PK `(tenant_id, run_at)` 인덱스 — §6 질문 5).

### 4.10 번들

화면 원천 변경 0(`git diff origin/main -- web/src` 0줄) — 초기 로드 gzip ⟦BUNDLE⟧ B, Phase 6 run 40094460151과 같은 값. 지연 경로 10 그대로. 새 npm 의존성 0(`licenses ok (257 packages …)` 그대로).

### 4.11 실 엔진 E2E (`run.sh`)

판정 ⑤를 더했다: 운영자 링크를 파일 SQL로 등록(`docs/운영.md` §3)하고 bootJar로 `recon run --tenant=GA01 --ym=202608,202702`(종료 코드·로그를 `.state/`에) → `EngineFeedE2eCheck`가 마지막 `recon_run` 행 = `CLI 2 2 0 0 0 OK`·`recon_result` 0행·종료 0을 본다. **실행은 사용자 몫 — 이 Phase에서는 실행하지 않았다.** 기대: A-1 두 달 `MATCH`, DRIFT 0.

### 4.12 규칙 시험의 의도적 위반 기록

§3 주입 표가 이 Phase의 규칙 시험(`ReconArithmeticScanTest`·`MetricsCatalogTest`·ArchUnit 둘·평문 스캔) 전부를 덮는다. 더해서: `scanPlaintextLeaks`의 트레이스 덤프 — 센티널(`identifiers` 첫 값) 한 줄을 넣은 `portal-app/build/trace-export/inject.spans.log`로 실패("평문 센티널이 시험 출력에 나타났다 … inject.spans.log: s…"), 지운 뒤 통과(P7-17). `TenantContextGuardTest`는 CI가 먼저 잡았다(F2).

### 4.13 CI run 목록 (이 Phase — 직접 조회)

| run | 커밋 | 결과 |
|---|---|---|
| 40487356156 | `9d10fde` | success |
| 40489236502 | `dfd8c45` | success |
| 40490868281 | `c726e52` | build 실패 — `ReconRunSchemaIT`(V8 `CHECK`의 NULL — F1) |
| 40491868214 | `cdc68b9` | success |
| 40492342512 | `1e17824` | success |
| 40495054323 | `7ed1b98` | build 실패 — `TenantContextGuardTest`(F2) |
| 40496359509 | `643475e` | success |
| 40497345807 | `a488394` | build 실패 — `TracingLeakIT` 마지막 단언(F3) |
| 40497834793 | `e291afd` | build 실패 — 같은 단언 |
| 40498434426 | `e3ece4a` | build 실패(같은 단언) + **web-e2e ⓑ 실패 — android·desktop**(F5) |
| 40499141094 | `3147ea6` | build·no-docker success, **web-e2e ⓑ 실패**(F5) |
| 40499675908 | `4c47bff` | build·no-docker success, web-e2e ⓑ 실패(F5) |
| 40499849823·40499855610·40499861450·40499865893 | 주입 S4·S11·S12·S13(PR #29~#32, `4c47bff` 위) | build 실패 — 기대한 시험 하나씩(S4·S11의 web-e2e는 결과를 본 뒤 취소) |
| 40501130840 | `0dcbc6f` | ⟦R_0dcbc6f⟧ |
| 40501300566 | 주입 S11b(PR #33) | build 실패 — `TracingLeakIT` 값 스캔 |
| 40501530610 | 주입 S13 둘째 판(PR #34) | ⟦R_S13b⟧ |
| **⟦RUN⟧** | **`9af9ee0`** | ⟦R_FINAL⟧ |

## 5. 계획과 다르게 한 것·해석·발견

계획과 다르게 한 것은 `docs/DECISIONS.md` "Phase 7 구현에서 계획과 다르게 한 것" P7-1~P7-17(요지: P7-1 브리지 아티팩트, P7-2 첫 판정 `MATCH`면 즉시 `REPAIRED`, P7-3 시도함 = 에피소드 첫 DRIFT 뒤, P7-4 트랜잭션 권고 잠금 + `REQUIRES_NEW`, P7-5 스케줄러 스레드 2, P7-6 V9, P7-7 지표 12 + 8, P7-8 정적 스캔, P7-9 Observation 대신 추적기, P7-10 시험 컴파일 경로, P7-11 하네스 제어, P7-12 `ReconResultWriteIT` 범위, P7-13 400 샘플 없음, P7-14 가드 목록, **P7-15 ETag 데이터 표식**, P7-16 대사 산술 스캔, P7-17 트레이스 덤프).

| # | 발견·해석 | 처리 | 분류 |
|---|---|---|---|
| F1 | V8 `ck_recon_run_outcome`의 `(result <> 'REPAIRED' OR rebuild_outcome = 'SUCCEEDED')`는 NULL에 NULL → 통과 | `ReconRunSchemaIT` 거부 사례가 run 40490868281에서 잡았다 → V9(`IS NOT DISTINCT FROM`), V8은 고치지 않았다(P7-6) | **발견·수정** |
| F2 | 새 저장소 둘을 `TenantContextGuardTest`의 닫힌 목록에 넣지 않았다 | run 40495054323이 잡았다 → `f4dc0d0`(P7-14) | 발견·수정 |
| F3 | `TracingLeakIT` 마지막 단언이 `spans.getFirst()`(`engine.feed`)를 `recon.run`으로 읽었다 — 시험 쪽 잘못 | `75b1a15`(이름으로 찾아 속성 지도 전체를 비교) | 기록 |
| F4 | **주입 S2(대사의 `portal − engine`)가 기존 두 스캔을 통과했다** — 둘은 `*`·`/`·`%`만 본다. C2는 둘을 "무변경"으로 두라 했다 | 둘은 그대로, 새 `ReconArithmeticScanTest`(허용 목록 없음 — 대사 클래스의 `long` 산술·`Math` 덧셈 뺄셈, 대사 표 SQL의 `+ - * / %`) `4c47bff`(P7-16). S2a·S2b가 잡혔다 | **발견·수정** |
| F5 | **E2E ⓑ가 묵은 304를 찾았다.** 대사가 `data_status`를 DRIFT로 바꾸거나 재구축이 뷰를 다시 써도 `seq`·설정이 같으면 ETag(`seq.sync.cfg` — 설계서 §7 v1.6)가 같아 브라우저가 "정상" 본문을 되살렸다(android·desktop, run 40498434426·40499141094·40499675908 — iOS 프로젝트는 세 번 다 통과, 이유는 확인하지 않았다). 실제 운영에서도 대사 뒤 다음 날까지 "확인 중"이 안 보일 수 있었다 | `cfg` 재료에 커서 `updated_at`·마지막 `recon_run` 행(`0dcbc6f`). 같은 문장에 붙이면 임계치 변환 `* 100`이 `ReconArithmeticScanTest`에, `ReconRunRepository`로 읽으면 `ApiLayerRulesTest`(조회 경로 저장소)에 걸려 `SyncStatusRepository.lastRecon` 한 문장으로 옮겼다 — 문장 수 +1(§4.9). `CachingIT`·`MeQueriesTest`. **설계 의미 변경이라 심사에서 승인 요청**(P7-15) | **발견·수정 — 승인 필요** |
| F6 | C12 "평문 스캔(트레이스 exporter 출력 포함)"인데 메모리 내보내기는 파일을 남기지 않아 스캔이 스팬을 본 적이 없었다 — 보고서를 쓰다 찾았다 | `TracingLeakIT`가 단언 전에 `build/trace-export/*.log`로 쓴다, 스캔 줄에 `trace dumps: N`(`9af9ee0`, P7-17). 로컬 주입으로 확인(§4.12) | **발견·수정** |
| F7 | Spring 기본 스케줄러는 스레드 하나 — 대사 회차(최대 15분 + 재구축) 동안 모든 테넌트의 인제스트 폴링이 멈췄을 것이다 | `spring.task.scheduling.pool.size: 2`, `ReconSchedulerIT.ingestAndReconTicksHaveTheirOwnThreads`(P7-5) | **발견·수정** |
| F8 | 주입 S11은 키 단언이 먼저 잡아 값 스캔이 시험되지 않았다. S13 첫 판의 web-e2e 실패는 F5 때문에 증거가 못 된다 | S11b(값만), S13 둘째 판(고친 밑) — §3 | 기록 |
| F9 | 커밋 보안 검토가 주입 커밋의 덩어리(S11b 원문 속성·S12 `oldest=`·S13 DRIFT)를 짚었다 | 버릴 초안 PR의 의도된 위반 — 병합 0, 가지 삭제. 작업 가지는 그대로 | 기록 |
| F10 | 설계서 §5의 열 주석에 설명을 붙이면 `StatusEnumDdlTest`(주석의 값 목록을 읽는다)가 깨진다 | 주석은 값만 두고 설명은 §5 본문 "대사 표" 문단으로 | 기록 |
| F11 | **운영자 `ingest rebuild`도 BATCH DRIFT 표식(`data_status`)을 지운다** — DE-28은 자동 재구축 경로(R5가 다시 쓴다)만 막는다. 운영자 재구축 뒤 다음 회차까지 그 달은 "정상"으로 보인다(`recon_result`의 미해결 행은 남아 다음 회차가 다시 DRIFT를 쓴다) | 이 Phase는 고치지 않았다 — `docs/운영.md` §8에 "운영자 재구축과 DRIFT 표식" 문단(재구축 뒤 `recon run`을 이어 부른다 — 이 커밋). §6 질문 3 | 질문 |
| F12 | `docs/지표.md`가 "프레임워크 지표 접두사는 `MetricsCatalogTest`의 닫힌 목록"이라고 적었으나 정적 스캔에는 그런 목록이 없다(계획 ⑤의 레지스트리 방식의 흔적). §4 머리의 "Observation → 브리지"도 P7-9와 달랐다 | 문구 정정(`9af9ee0`·`0dcbc6f`) — 의미 변경 없음 | 기록 |

## 6. 다음 Phase 질문 (Phase 8 — 데모 생성기·compose·Keycloak PKCE·README)

1. **compose의 대사 실행**(Q9): 앱 안 스케줄러(지금 — 테넌트 `recon_hour_kst`)를 그대로 쓸지, compose에서는 스케줄러를 끄고 cron 컨테이너가 `recon run`을 부를지.
2. **OTLP 수집기**: 내보내기 0(지금). compose에 Jaeger/Tempo + OTLP 내보내기를 넣을지 — 넣으면 새 의존성(`opentelemetry-exporter-otlp`)과 `TracingLeakIT` 같은 평문 스캔을 수집기 쪽에도 둘지.
3. **운영자 재구축 뒤 DRIFT 표식**(F11): `ingest rebuild`가 끝날 때 미해결 BATCH DRIFT 달의 `data_status`를 다시 쓸지(재구축이 대사 표를 읽게 된다 — §6.7 의존 방향), 아니면 운영 절차("재구축 뒤 `recon run`")로 둘지.
4. **엔진 ESTIMATE `gross` 설명문**(DD-19·§4.2): 다음 이벤트 계약 개정 때 "도래 예정 미리보기 포함"으로 고칠지 — E3 후보, 엔진 사본 갱신 PR(선승인 패턴).
5. **ETag 표식 문장**(P7-15): 콜마다 +1 문장. 부하 시험(k6 — §13 성능)에서 문제가 되면 표식을 설정·커서 문장으로 합칠 수 있는지 — 그 문장에는 임계치 `* 100`이 있어 `ReconArithmeticScanTest`가 막는다(규칙을 좁히지 않고 옮긴 결과).
6. **`run.sh`를 compose 안으로**(판정 ⑤ 포함), §14.1 #14 닫기, README 포트폴리오 관점, 데모 생성기 부록 B — 지시문 §5 목록 그대로.
