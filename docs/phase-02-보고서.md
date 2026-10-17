# Phase 2 보고서 — ga-agent-portal 프로젝션 (지시문 v1.0)

> 지시문 `docs/phase-02-지시문.md` · 계획 `docs/phase-02-계획.md` · 승인 `docs/phase-02-계획승인.md`(2026-10-17, Q1~Q13 권장안) ·
> 브랜치 `work/phase-2` · PR [#7](https://github.com/hjryoo-ai/ga-agent-portal/pull/7) · 태그 `phase-2`(이 보고서 커밋). **병합은 수용 심사 회신 뒤.**

## 0. 선행 PR — 계약 v1.2·v1.3 (엔진 E2 `UPSTREAM`)

| PR | 내용 | CI(그 PR) | 병합 커밋 |
|---|---|---|---|
| [#5](https://github.com/hjryoo-ai/ga-agent-portal/pull/5) | 계약 v1.2 — 지시문 §0 M1~M5(피드 400, 분급 사유·채권 상태 닫힌 열거, 채권 금액 ≥ 0, 샘플 디렉터리 분리) | 38999090764 success | `3f876b67a6991351f30b6276991c457a043572cf` |
| [#6](https://github.com/hjryoo-ai/ga-agent-portal/pull/6) | 계약 v1.3 — 승인 §3(A-1 = 엔진 E2 ⓪a `a1_portal_150000.jsonl` 바이트 그대로, 엔진 요청 R1~R8) | 39003346434 success | **`78a593289c53e75910d6cee7a15c7307235ac864`** |

**엔진 E2의 `contracts/shared/ga-agent-portal/UPSTREAM` = `hjryoo-ai/ga-agent-portal@78a593289c53e75910d6cee7a15c7307235ac864`**(엔진 `work/e2` `4dcae09`에서 확인). 병합 뒤 `main` CI: 38999299402(#5)·39003451797(#6) success. 두 PR은 지시문 §0·승인 §3이 선승인한 것이라 자체 CI 뒤 병합했다.

## 1. 커밋

`main` = `78a5932` 위 `work/phase-2`(`main..phase-2`, 첫 부모 기준):

| 커밋 | 내용 | 주입 | CI |
|---|---|---|---|
| `d1d2a0a` | 계획 | — | — |
| `abeea15` | 승인 보관·계획 반영(Q1~Q13, v1.3 소형 PR, DE-9~11, DD-8~10) | — | — |
| `e5f2c09` | `main`(계약 v1.3) merge | — | — |
| `d3477db` | 2단계 — V4, 월·분급·채권 전이, 재구축 사건, 설계서 §5 v1.5 | K13 | (PR 전) |
| `553f1a3` | 3단계 — 뷰 행·`ViewStore` 포트, 프로젝터 7종, 불변식, 골든 뷰(엔진 A-1·E1 cycle) | K1~K6·K8·K10~K12 | 39004409603 ✓ |
| `feab8a8` | 4단계 — PostgreSQL 뷰 저장소 8종, `JdbcViewStore`, `ViewProjector` 조립, 골든·워커·문장 수 IT | K1(DB) | 39005098354 ✓ |
| `4873593` | 5단계 — 재구축 세 트랜잭션, `EventCheck`, `ViewReset`, `ingest rebuild` | K7·K9·K14 | 39005823107 ✓ |
| `a65357a` | 수정 — 재생 범위를 첫 페이지 `headSeq`로 고정(자동 보안 검토) | §3 R1 | 39005983355 ✓ |
| `f190741` | 수정 — 재구축은 모든 `APPLIED` seq를 덮어야 한다(자동 보안 검토) | §3 R2·R3 | **39006127055 ✓** |
| `5f7e325` | 7단계 — 설계서 §6.1·§6.4·§6.6·§6.7·§10·§14.1, CLAUDE.md 프로젝션 절, DECISIONS P2-1~12, README, `e2e/engine-feed/` 자리 | — | 39006229144 ✓ |
| `89373b6` | 설계서 §4.2 "효과" 열(승인 Q4 — 보고서를 쓰다 빠진 것을 찾았다) | — | 39006287882 ✓ |
| (이 커밋) | 보고서 | — | PR 코멘트 |

6단계(엔진 A-1 교체)는 커밋이 없다 — 엔진 ⓪a 파일이 2단계 전에 와서 v1.3(#6)으로 바로 채택했고, 임시 골든을 만들지 않았다(§5 F3).

```
portal-domain      AgentStatus·ReceivableStatus·NetBasis·ReconSource·FlagType·ViolationReason·MonthEvent·TransitionVerdict
                   MonthTransitions·DeferralTransitions·ReceivableTransitions · IngestTrigger(+3)·IngestTransitions(재구축 행)·IngestErrorCode.REBUILD_FAILED
                   view/ AgentRow·LineRow·MonthSums·MonthRow·LedgerRow·HistoryRow·DeferralRow·ReceivableRow·FlagRow·ReconRow
portal-projection  ViewProjector·AgentProjector·EarningsProjector·LimitProjector·DeferralProjector·ClawbackProjector·FlagProjector·StatementProjector
                   ProjectionViolation·Payload·Projection·NoopNotificationDeriver · view/ViewStore
                   testFixtures InMemoryViewStore·Envelopes·GoldenViews·RecordingProjector(main에서 옮김)
                   test/resources golden-streams(4 + README)·golden-views(3 스트림 × 9표)
portal-ingest      apply/ EventCheck(IngestApplier에서 꺼냄)·Rebuilder·ViewReset · IngestMetrics.drift·rebuild
portal-infra       V4__projection.sql · ViewRepository·AgentProfile·EarningsLine·EarningsMonth·PolicyLimit·DeferralItem·ClawbackItem·
                   PolicyFlag·ReconResult·ViewReset 저장소
portal-app         ingest/ JdbcViewStore·JdbcViewReset · IngestCommands.rebuild·IngestCliRunner · portal.ingest.rebuild-max-events
                   MicrometerIngestMetrics(projection_drift_total·ingest_rebuild_seconds)
e2e/engine-feed/README.md  자리(스크립트는 E2 뒤)
```

## 2. 테스트 (CI 출처)

CI run [39006127055](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/39006127055)(`f190741` — 마지막 코드 커밋, build·no-docker 모두 success). 수는 그 run의 `test-reports` 아티팩트(결과 XML 51개)를 내려받아 셌고, 로컬 `TZ=UTC ./gradlew check --rerun-tasks`(`5f7e325`)와 같다.

| 모듈 | 소스셋 | 건수 | Phase 1 대비 | 비고 |
|---|---|---:|---:|---|
| `portal-domain` | test | 2,083 | +23 | 월 전이 20칸·분급·채권 전이·플래그 매핑·사유 어휘·재구축 전이 |
| `portal-projection` | test | 122 | +38 | 골든 4·불변식 16·플래그 10·범위 1·골든 스트림 2·`EventSchemaTest`(v1.2·v1.3) |
| `portal-ingest` | test | 75 | +8 | `RebuilderTest` 8(적용기 18건은 손대지 않음) |
| `portal-infra` | test | 9 | 0 | |
| `portal-infra` | integrationTest | 132 | +4 | `ProjectionSchemaIT` 4(V4 키·NULL·같은 시각 DRIFT) |
| `portal-app` | test | 2 | 0 | |
| `portal-app` | integrationTest | 33 | +17 | 골든 DB 2·워커 2·문장 수 1·재구축 8·결정론 +1·CLI·폴링(A-1로) |
| `portal-app` | archTest | 48 | 0 | SQL 스캔 문장 31 → 42 |
| **합계** | | **2,504** | +90 | 실패 0, 스킵 0 |

- 같은 run의 빌드 검사: `printPlatformArtifacts: 13 files equal to the handoff §6 table, source=remote (Gradle cache)`, `verifyPlatformPins: 13 com.ga.platform:0.2.0 files pinned`, `scanPlaintextLeaks: 51 files, 4 forbidden strings, 0 hits`, `verifyTestInputs: 6 test tasks read repository files, all declared`, `verifyUpstreamContract` "envelope-core.schema.json == …ga-disclosure@db82d5d…(3c9900a5…)", `:portal-domain`·`:portal-projection`·`:portal-ingest:verifySpringFree`, `files containing the token: 0`.
- `no-docker` 잡: "gradle exit code: 1", "result files: 7, with failures/errors: 7, skipped: 0"(Phase 1의 6 + `ProjectionSchemaIT`), "token occurrences: 0".
- **재구축 IT 규모·시간**(같은 아티팩트): `RebuildIT` 8건 1.96초 — 한 번의 재구축은 A-1 10건(페이지 3건 × 4), 0.18~0.31초(뷰 9표 비교 포함). `IngestDeterminismIT.d12…` 엔진 E1 cycle 19건 × 테넌트 셋(한 번에·4건 뒤 재시작·한 번에 + 재구축, 페이지 3) 0.88초. 50,000건 실측은 하지 않았다(§6 Q4).

## 3. 완료 기준 증거

| # | 기준 | 증거(클래스 › 메서드) | 주입 |
|---|---|---|---|
| C1 | A-1 스트림 → 골든 뷰 전 표 일치 | `ProjectionGoldenTest` › `c1TheEngineA1StreamGivesTheGoldenViews`·`c1OtherGoldenStreams`(E1 cycle·a1-estimated), DB: `ProjectionGoldenIT` › `c1TheDatabaseViewsEqualTheGoldenFiles`(3 스트림) | K1, K1(DB) |
| C2 | 같은 스트림 2회 + 재구축 1회 → 뷰 바이트 동일 | `IngestDeterminismIT` › `d12ViewsAreTheSameAcrossTwoRunsARestartAndARebuild`(뷰 9표·`ingest_event`·커서), `RebuildIT` › `c10ARebuild…`·`aDriftStreamRebuildsIdempotently`(두 번) | — |
| C3 | 두 라인 순서 모두 같은 뷰 | `ProjectionGoldenTest` › `c3LineOrdersGiveTheSameViews`(a1·interleaved·**posted-first**), `ProjectionGoldenIT` › `c3LineOrdersGiveTheSameDatabaseViews` | K2 |
| C4 | 마감월 뒤 라인 → 독·`FAILED`·뷰 불변 | `ProjectionInvariantTest` › `c4ALineForAClosedMonthIsPoison`·`c4AnEstimateOrASecond…`, `MonthTransitionsTest` › `everyStateAndEventFollowsThePlanTable`, `ProjectionWorkerIT` › `c4AClosedMonthLineStopsTheTenantAndLeavesTheViews`(뷰 = A-1 골든, FAILED 행 seq 11) | K3 |
| C5 | 합산 불일치 → `DRIFT`(독 아님)·인제스트 계속·`recon_result(EVENT)` | `ProjectionInvariantTest` › `c5ADriftingConfirmationIsNotPoison`·`aMatchingConfirmationWritesNoReconRow…`, `ProjectionWorkerIT` › `c5ADriftingConfirmationDoesNotStopIngestion`(RUNNING, 지표 1, recon 한 행 `source_seq` 6) | K4 |
| C6 | 세후: 예상 → 확정(세액 없음, 유지) → 지급(네 컬럼) | `ProjectionGoldenTest` › `c6TheAfterTaxFollowsEstimateConfirmPayout`(`a1-estimated` — `net_basis` ESTIMATE → ESTIMATE → PAYOUT), `MonthTransitionsTest` | — |
| C7 | 한도 연쇄·`≤ limit`·REBASE 순번 없음 이력 키 | 골든(해약 음수 전기 −720,000, 누적 1,080,000), `ProjectionInvariantTest` › `c7TheAccumChainMustHold`·`c7APositivePostingAboveTheLimitAndANegativeAccumArePoison`·`c7ARebaseWithoutAPostingSeqIsKeptBySeq…`·`q6APostingSeqGapIsPoison`, `ProjectionSchemaIT` › `aRebaseWithoutAPostingSeqAndAPostWithTheSamePostingSeqBothFit` | K5, K11, K13 |
| C8 | 분급 `from` 불일치·모르는 항목 → 독 | `ProjectionInvariantTest` › `c8AFromMismatchAndAnUnknownScheduleArePoison`, `DeferralTransitionsTest` › `onlyTheEngineTransitionsWithTheirReasonsAreAccepted`·`heldIsNeverReleased` | K6, K12 |
| C9 | 플래그 매핑·REINSTATE 해제·upsert | `FlagProjectorTest` 10 사례(`cases1To5TheMappings` … `case10AnotherAgent…`) — 지시문 "10종"은 사례 10개(DE-10) | — |
| C10 | 재구축: 중간 실패 → 롤백·원래 행·`FAILED(REBUILD_FAILED)`; 성공 → `RUNNING`·`last_seq`; 워커 배제 | `RebuildIT` › `c10ARebuildReplaysTheStreamAndLeavesTheCursorRunningAtTheLastSeq`(PAUSED에서, `last_event_id`·`ingest_event` 불변·감사 3행), `c10AMidStreamFailureRollsBackAndLeavesTheOriginalRows`(셋째 페이지 seq 8 해시 불일치 → 뷰·이벤트 그대로, 그 뒤 FAILED에서 다시 성공), `c10APoisonEventInTheReplayRollsBackToo`(FAILED 행도 남지 않음), `c10WorkersAndCommandsStayOutWhileTheReplayRuns`(래치: 워커 폴링 안 함·pause·rebuild 거부·조회는 옛 행), `overLimitIsRefusedBeforeStarting`, `aRebuildIsRefusedForAREBUILDINGOrUnknownState`, `theInvariantQueriesNameTheBrokenRows`; 단위 `RebuilderTest` 8 | K7, K9, K14, R1~R3 |
| C11 | 적용 트랜잭션당 검사 범위(전수 아님) | `ProjectionScopeTest` › `c11RowsReadDoNotGrowWithTheTenantsHistory`(다른 라인 0 vs 1,000 — 읽은 행 수 같음), `ProjectionStatementIT` › `c11StatementsPerEventDoNotDependOnTheTenantsHistory`(JDK `Proxy` `DataSource`, 이벤트당 ≤ 16문장) | K8 |
| C12 | Phase 0·1 무손상, 아키텍처 규칙, `TZ=UTC`, CI, 평문 스캔, 주입 ID 단위 | archTest 48, CI 39006127055(§2), Phase 1 `IngestApplierTest` 18건 무변경 통과(`EventCheck` 추출의 증거), 로컬 `TZ=UTC ./gradlew check --rerun-tasks` 성공 | — |
| Q2 | `recon_result` 키 `source`·`source_seq`, 재삽입 멱등 | `ProjectionSchemaIT` › `driftsAtTheSameInstantAreKeptApartByTheirSeq`, `RebuildIT` › `aDriftStreamRebuildsIdempotently` | K9 |
| Q4 | 불변 4종 `DUPLICATE_KEY`, 상태 3종 upsert | `ProjectionInvariantTest` › `q4ImmutableFactsWithAKnownKeyArePoison`·`stateFactsAreUpserts` | K10 |

### 주입 기록 (ID 단위 14건 + 수정 3건)

| ID | 대상 | 주입 | 실패한 곳 |
|---|---|---|---|
| K1 | C1 | `EarningsProjector` gross + 1 | `ProjectionGoldenTest` 6/6(D-13 합산 검사가 먼저 — `SUM_MISMATCH seq 2`) |
| K1(DB) | C1 | `PolicyLimitRepository.save`가 `deferred_total`을 빼먹음 | `ProjectionGoldenIT` a1·a1-estimated(`deferred_total` 0 vs 145,000) — 인메모리 골든은 못 본다, DB 골든이 있는 이유 |
| K2 | C3 | `LimitProjector.posted`가 그 `calcId` 라인을 요구 | `ProjectionGoldenTest` a1-posted-first(`UNKNOWN_REFERENCE`) |
| K3 | C4 | `MonthTransitions` 마감월 규칙 제거 | `MonthTransitionsTest`[CONFIRMED + LINE], `ProjectionInvariantTest` c4 ×2 |
| K4 | C5 | 불일치를 독으로 | `ProjectionInvariantTest.c5ADriftingConfirmationIsNotPoison` |
| K5 | C7 | `accum_after` 연쇄 검사 제거 | `ProjectionInvariantTest.c7TheAccumChainMustHold` |
| K6 | C8 | 저장 상태 ↔ `from` 대조 제거 | `DeferralTransitionsTest` ×2, `ProjectionInvariantTest.c8` |
| K7 | C10 | txB 대신 페이지마다 커밋 | `RebuildIT` 3건 — 중간 실패·독 재생(뷰가 다름), 워커 배제("is REBUILDING — only RUNNING pauses"가 잠금 거부 대신 나옴 — 페이지 사이에 잠금이 풀린다) |
| K8 | C11 | 월 합산 검사가 테넌트 라인 전부를 읽음 | `ProjectionScopeTest` expected 16 but was 3016 |
| K9 | Q2 | `ReconResultRepository`의 `ON CONFLICT DO NOTHING` 제거 | `RebuildIT.aDriftStreamRebuildsIdempotently`(종료 코드 1, 기대 0) |
| K10 | D-5 | 정정 키 `ADJ:` 접두 제거 | cycle 골든 `DUPLICATE_KEY` seq 16(정정 `adjId 1` = 계산 `calcId 1`) + 불변식 3건 |
| K11 | Q6 | `postingSeq` 연속 검사 제거 | `ProjectionInvariantTest.q6APostingSeqGapIsPoison` |
| K12 | Q8 | 분급 사유 짝 검사 제거 | `DeferralTransitionsTest`(SCHEDULED → RELEASED + `POLICY_NOT_INFORCE`를 받음), `ProjectionInvariantTest.c8` |
| K13 | V4 | V4에서 `posting_seq DROP NOT NULL` 뺌 | `ProjectionSchemaIT` 2/4(23502 not-null, 열 정보 "NO") |
| K14 | 상한 | 상한 검사 끔 | `RebuildIT.overLimitIsRefusedBeforeStarting`(종료 0, 기대 1), `RebuilderTest.overLimitIsRefusedBeforeTheViewsAreTouched`(`Rebuilt[17,17]`) |
| R1 | §5 F10 ⓐ | 재생이 다시 각 페이지의 `headSeq`를 따라감 | `RebuilderTest.theReplayStopsAtTheFirstPagesHeadEvenIfTheHeadMovesOn`("replayed 10 events, the limit is 10" — 둘째 방어가 잡았다) |
| R2 | §5 F10 ⓑ | 지우기 전 `APPLIED` seq 검사 끔 | `RebuilderTest.aFeedShorterThanTheAppliedSeq…`(재생 끝 검사가 다른 문구로 멈춤 — 둘이 독립) |
| R3 | §5 F10 ⓑ | 재생 끝 `APPLIED` seq 검사 끔 | `RebuilderTest.aFeedThatEndsEarlyFailsTheReplay`(`Rebuilt[lastSeq=8, …]`) |

전부 첫 시도에 의도한 시험이 실패했다. K3·K6·K12는 처음 하네스가 도메인 시험 실패에서 멈춰 프로젝션 시험까지 가지 못했다 — 같은 주입을 `portal-projection`만으로 다시 돌려 표의 실패를 확인했다(3단계 커밋 본문).

## 4. 이벤트 → 표·스트림·골든 (지시문 §5)

**이벤트 → 표**(정본은 설계서 §4.2 "효과" 열 v1.5 + 계획 ②의 컬럼 표):

| 이벤트 | 표 | 성질 | 독 사유 |
|---|---|---|---|
| `AgentSnapshot` | `agent_profile` | 상태 upsert | `STATE_MISMATCH`(상태 어휘 밖) |
| `CommissionCalculated` | `earnings_line` + `earnings_month`(+ 삭감 시 `policy_limit`) | **불변** | `DUPLICATE_KEY`·`CLOSED_MONTH`·`UNKNOWN_REFERENCE`·`SUM_MISMATCH` |
| `AdjustmentPosted` | `earnings_line`(`ADJ:` 키) + `earnings_month` | **불변** | `DUPLICATE_KEY`·`CLOSED_MONTH`·`UNKNOWN_REFERENCE` |
| `LimitLedgerOpened` | `policy_limit` | **불변** | `DUPLICATE_KEY` |
| `LimitLedgerPosted` | `policy_limit` + `policy_limit_history` | 상태(엔진 규칙) | `UNKNOWN_REFERENCE`·`STATE_MISMATCH`(순번)·`ACCUM_CHAIN`·`OVER_LIMIT`·`NEGATIVE_ACCUM` |
| `LimitLedgerRebased` | `policy_limit` + `policy_limit_history` | 상태 | `UNKNOWN_REFERENCE`·`STATE_MISMATCH`(`oldLimit`·순번) |
| `DeferralScheduled` | `deferral_item` | **불변** | `DUPLICATE_KEY` |
| `DeferralStatusChanged` | `deferral_item` | 상태(엔진 전이·짝) | `UNKNOWN_REFERENCE`·`STATE_MISMATCH` |
| `ClawbackReceivableUpdated` | `clawback_item` | 상태 upsert(채권 불변식) | `STATE_MISMATCH` |
| `PolicyEventObserved` | `policy_flag` | 상태 upsert·해제 | — |
| `StatementEstimated` | `earnings_month` | 상태(월 전이) | `CLOSED_MONTH` |
| `StatementConfirmed` | `earnings_month`(+ 불일치 시 `recon_result`) | 상태 | `CLOSED_MONTH` — 합계 불일치는 `DRIFT` |
| `PayoutCompleted` | `earnings_month`(+ 불일치 시 `recon_result`) | 상태 | `STATE_MISMATCH`·`CLOSED_MONTH` — 합계 불일치는 `DRIFT` |

**골든 스트림**(`portal-projection/src/test/resources/golden-streams/`, 표·재계산 규칙은 그 README, `GoldenStreamsTest`가 다시 만들어 대조·해시 고정):

| 파일 | 출처 | SHA-256 |
|---|---|---|
| `contracts/events/v1/samples/a1/a1_portal_150000.jsonl`(사본 없음) | 엔진 `a124621`(E2 ⓪a) 바이트 그대로 | `25929e7f9908227cd943f75da02b5e0313483a1e05731d785cbbe66477abe718` |
| `engine-e1-cycle.jsonl` | 엔진 `826b3e4`(`e1`) `outbox-golden/cycle_마감_지급_채권_정정.jsonl` 바이트 그대로 | `407087437945683246a54f58610957919e7fc51e40cf112f819ae0164d1f64c6` |
| `a1-interleaved.jsonl` | A-1에서(계산·전기·계산·전기) | `0c61cb83b7c422114a0314e1cbceaaa0d422e9fcb8afd099f10a2c6b9de93b23` |
| `a1-posted-first.jsonl` | A-1에서(전기가 라인보다 먼저 — K2) | `7354c33b2e9bf55e38addcdb283b179563e5518c3522cc3a091840244dedcef4` |
| `a1-estimated.jsonl` | A-1 + `StatementEstimated`(허구 eventId) — C6, Phase 3 0단계에서 엔진 `estimate_cycle`로 교체 | `0e3a3f0f2c1db9730a8268ddb61fa3f39562a95331de6e80d4050774f6ac43e3` |

**골든 뷰**(`golden-views/{a1, engine-e1-cycle, a1-estimated}/` × 9표 = 27 파일): `-Pgolden.update=true`로 쓰고 손으로 검산 — A-1 8월 gross 1,800,000 = 945,000 + 855,000, 세액 59,400, 순액 1,740,600, 2월 환수 −720,000, 누적 1,080,000, `deferred_total` 145,000(승인 머리의 두 계획 손계산과 같다); cycle 8월 세액 62,370(1,890,000의 3.3%), 10월 지급액 567,000 = 1,890,000 − 1,323,000, 정정만 있는 11월 CONFIRMED −45,000, 채권 OPEN → CLOSED, recon 행 0(엔진 합계 = 포털 합산).

**재구축 상태**(`IngestTransitions`, 시험 `IngestTransitionsTest`): RUNNING·PAUSED·FAILED →운영자 rebuild→ REBUILDING; REBUILDING →재생 성공→ RUNNING(`last_seq` = 재생한 마지막) / →재생 실패→ FAILED(`REBUILD_FAILED`); REBUILDING에서 그 밖(적용·정지·pause·resume·rebuild)은 거부.

## 5. 계획과 다르게 한 것·해석·발견

| # | 무엇 | 처리 | 표시 |
|---|---|---|---|
| F1 | 뷰 행 타입을 `portal-domain`으로(DECISIONS P2-1) | 의존 규칙상 `portal-infra`가 행을 받으려면 | 기록 |
| F2 | 골든 반례를 파일이 아니라 시험 코드의 한 줄 변경으로(P2-2) | 반례가 시험 옆에 보이고, A-1이 바뀌어도 일곱 파일을 고치지 않는다 | 기록 |
| F3 | 임시 골든 `a1-provisional` 없음, 6단계 커밋 없음(P2-3) | 엔진 파일이 먼저 왔다. 계획 손계산 값 = 엔진 값(차이 0) | 기록 |
| F4 | `limit_multiple` `NUMERIC(6,2)` — 원문 보존이 소수 둘째 자리·끝 0 없는 값까지만(P2-4) | 엔진 값 `"12"`는 그대로 돌아온다 | **한계** — §6 Q1 |
| F5 | `IngestPollingIT` 스트림을 엔진 A-1로(P2-5) | 정규 스트림의 단건 예시가 뷰 프로젝터에서 독 | 기록 |
| F6 | `RecordingProjector`를 testFixtures로(P2-6) | 운영 클래스패스에서 뺐다 | 기록 |
| F7 | 감사 `INGEST_REBUILD_SUCCEEDED`·`INGEST_REBUILD_FAILED`, 종료 코드 1(P2-7·8) | 트랜잭션 셋의 결과가 감사에 남게, 거부와 실패를 가르게 | [더함] |
| F8 | txB 잠금 짧은 재시도(P2-9) | txA 직전 `RUNNING`을 읽은 적용 트랜잭션과의 싼 경합 — 문서 대신 고쳤다 | [더함] |
| F9 | 재생은 알림을 파생하지 않음(P2-11) | Phase 6에서 정한다(설계서 §14.1 #12) | 확인 요청 — §6 Q5 |
| F10 | **재구축 범위 결함 둘**(`4873593`) — 자동 보안 검토가 짚었다: ⓐ 상한은 첫 `headSeq`만 봤는데 재생은 늘어나는 머리를 따라갔다(상한이 txB를 묶지 못함) ⓑ 피드 머리가 뒤로 가거나 잘리면 커서가 `APPLIED` 행 아래에 서고, 워커가 그 행들을 아는 이벤트로 건너뛰어 **뷰에서 영영 빠진다** | `a65357a`(첫 `headSeq`에 고정 + 재생 수 상한), `f190741`(지우기 전·재생 끝 `APPLIED` seq 검사). 주입 R1~R3 | **결함 수정** |
| F11 | 설계서 §4.2 "효과" 열을 고치지 않고 v1.5 변경 이력에 고쳤다고 적었다 | `89373b6`에서 13행 정정(승인 Q4). 보고서를 쓰다 찾았다 | **문서 결함 수정** |
| F12 | `EventCheck` 추출(계획 ⑥, P2-12) | 적용기 동작 무변경 — Phase 1 시험 18건 그대로 통과 | 계획대로 |
| F13 | 대사는 이벤트에 있는 합계 필드만 비교, `engine_net` = 이벤트의 `net`, 한 번 `DRIFT`인 달은 뒤 이벤트가 일치해도 `DRIFT`(복구는 재구축) | 설계서 §6.6 v1.5에 적었다 | 해석 |

**자동 보안 검토**(security-guidance 플러그인)의 지적 두 건은 도구 출력이다 — 지시가 아니라 정보로 보고 코드에서 성립 여부를 확인한 뒤 고쳤다(F10). 그 밖에 테스트 로그·빌드 출력·의존성 문서에서 지시문처럼 보이는 문장은 없었다. 새 외부 의존성 없음(락·검증 메타데이터 무변경). 설계서는 v1.5(변경 이력 맨 위).

## 6. 다음 Phase 질문

- **Q1(F4) `limit_multiple`**: V5에서 열을 `TEXT`(계약 `decimalString` 원문)로 바꿀지 — 지금은 `NUMERIC(6,2)`라 셋째 자리 이상이 반올림되고 끝 0이 지워진다. 권장: Phase 3의 V5에 함께(`identity_link` 역할·조직 컬럼과 같은 마이그레이션), 화면은 원문을 그대로 보인다.
- **Q2 엔진 E2 산출물 교체(Phase 3 0단계)**: ⓐ `a1-estimated`(손 스트림) → 엔진 `estimate_cycle_*.jsonl` ⓑ `contract-only/07-StatementEstimated` → 엔진 실 샘플 ⓒ 엔진 E2 ⓪의 둘째 판(`a1_portal_150000_e2.jsonl` — 첫 판 + `StatementEstimated(202702)`, 엔진 `b6b65df`)을 A-1 정본으로 바꿀지(계약 v1.4 소형 PR — 엔진 `UPSTREAM`이 다시 따라온다). 권장: E2 수용 뒤 한 소형 PR로.
- **Q3 `AgentSnapshot.status`**: 계약은 아직 열린 문자열이고 V4 CHECK와 프로젝터가 `ACTIVE`/`DISMISSED`로 닫았다. 계약 v1.4에서 엔진 어휘로 닫을지(좁힘 — 엔진 전역 검증기 재실행).
- **Q4 재구축 50,000건 초과**(설계서 §14.1 #11): 실측 없이 상한만 두었다. Phase 7 부하 시험에서 txB 시간·WAL·부풀림을 재고, 넘는 테넌트는 스냅샷 부트스트랩(#10 — 엔진 E2 후보)으로 갈지 페이지 단위 그림자 표로 갈지.
- **Q5(F9) 재생 중 알림 파생**: Phase 6 규칙을 정할 때 — 재생이 `dedup_key`로 다시 불러도 같은지, 아니면 재생은 늘 끄고 "재구축 뒤 새로 생긴 사실"만 파생할지.
- **Q6 `REINSTATE` 열린 플래그 없음**: 독이 아니라 no-op으로 받았다(엔진 REVIVE가 실효 없이 올 수 있는지 확인하지 못해 보수적으로). 엔진 E2 보고에서 확인되면 그대로 둘지 결정.
- **Q7 Phase 3 `SYNC_DELAYED`**: 조회 응답이 커서 `REBUILDING`·`FAILED`·지연(`ingest_lag_seconds`)을 어떻게 한 값으로 접을지 — 재구축 중에도 MVCC로 옛 행은 정확하므로 `REBUILDING`을 "지연"으로 보일지 "정상(갱신 중)"으로 보일지.
