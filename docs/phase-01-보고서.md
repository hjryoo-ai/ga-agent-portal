# Phase 1 보고서 — ga-agent-portal 인제스트 (지시문 v1.0)

> 지시문 `docs/phase-01-지시문.md` · 계획 `docs/phase-01-계획.md` · 승인 `docs/phase-01-계획승인.md`(2026-10-17, Q1~Q7 권장안) ·
> 브랜치 `work/phase-1` · PR [#4](https://github.com/hjryoo-ai/ga-agent-portal/pull/4) · 태그 `phase-1`(이 보고서 커밋). **병합은 수용 심사 회신 뒤.**

## 0. 선행 PR — 계약 v1.1·v1.1.1 (엔진 E1 `UPSTREAM`)

| PR | 내용 | CI(그 PR) | 병합 커밋 |
|---|---|---|---|
| [#2](https://github.com/hjryoo-ai/ga-agent-portal/pull/2) | 계약 v1.1 — 지시문 §0 K1~K9(엔진에 없는 데이터는 선택, `PayoutCompleted` 확정값, 피드 `tenantId`·422) | 38953993935 success, 2,274건 | `f30d026ae51cb7885e2536b3afc1b5a038c3a140` |
| [#3](https://github.com/hjryoo-ai/ga-agent-portal/pull/3) | 계약 v1.1.1 — 승인 §3 L1~L3(`LimitLedgerRebased.postingSeq` 선택, `paidAt`·`limitCutPolicy` 뜻) | 38958845185 success, 2,276건 | **`f54d008dd220b529804689aa1e89f8fc6bbc6d4a`** |

**엔진 E1의 `contracts/shared/ga-agent-portal/UPSTREAM` = `hjryoo-ai/ga-agent-portal@f54d008dd220b529804689aa1e89f8fc6bbc6d4a`**(v1.1.1이 v1.1을 대체 — 승인 §3). 병합 뒤 `main` CI: 38954273360(#2) success. 두 PR 모두 지시문·승인이 선승인한 것이라 자체 CI 뒤 병합했다.

## 1. 커밋

`main` = `f54d008` 위 `work/phase-1`(`main..phase-1`, 첫 부모 기준):

| 커밋 | 내용 | 주입 | CI |
|---|---|---|---|
| `cacca1f` | 계획 | — | — |
| `177633b` | `main`(계약 v1.1.1) merge | — | — |
| `80c7e5f` | 승인 반영(Q3 롤·J12, Q4 `TOKEN_MISSING`, Q6 스캐너, 엔진 사실) + DECISIONS DE-7·DE-8 | — | — |
| `d0b9f4b` | 2단계 — V3·`portal_directory`, 상태 기계, `Envelope`·`Projector`, 수신 검증·해시 | J12 | 38960345035 ✓ |
| `b82663c` | 3단계 — 피드 클라이언트 2종 + 계약 스위트, 스텁 서버, 시험 스트림 | J7 | 38961016436 ✓ |
| `ae70238` | 4단계 — 적용기·워커 회차(포트), 인메모리 포트, 설계서 §6.1 | J1·J2·J5·J6·J11 | 38961666884 ✓ |
| `401668d` | 5단계 — PostgreSQL 어댑터·잠금·스케줄·지표, IT 4종 | J3·J4·J9 | 38962977582 ✓ |
| `6d89e69` | 6단계 — 운영 CLI + `audit_access` | J8 | 38963426678 ✓ |
| `490904e` | 7단계 — 설계서 §4.1·§12, CLAUDE.md 인제스트 절, DECISIONS P1-1~4, 토큰 센티널, pause 잠금 순서 수정 | J10 | 38963692932 ✓ |
| (이 커밋) | 보고서·README | — | PR 코멘트 |

```
portal-domain      IngestTrigger·IngestTransitions·IngestErrorCode·IngestEventStatus·IngestEventRecord·CursorRecord
portal-projection  Envelope·Projector·ProjectionResult·NotificationDeriver(시그니처만)·RecordingProjector
portal-ingest      ContractSchemas·EnvelopeValidator·EnvelopeReader·PayloadHasher
                   feed/  FeedClient·FeedPage·FeedException·FeedResponses·HttpFeedClient·FileFeedAdapter·ServiceTokenSource
                   apply/ IngestApplier·IngestWorker·IngestStore·TenantTransactions·IngestLock·IngestMetrics·PollSchedule
                   testFixtures FeedClientContract·StubFeedServer·InMemoryIngest·Samples · test/resources/streams(10 + README)
portal-infra       V3__ingest_payload_hash.sql · IngestEventRepository·IngestLockRepository·AuditAccessRepository·IngestCursorRepository(ensure)
portal-app         ingest/ JdbcIngestStore·SpringTenantTransactions·JdbcIngestLock·MicrometerIngestMetrics·FileServiceTokenSource·
                           IngestScheduler·IngestCommands·IngestCliRunner·IngestConfiguration·IngestPollingConfiguration·IngestCliConfiguration
                   application.yaml(portal.ingest.*) · application-cli.yaml
docker/postgres/init-roles.sql  portal_directory
```

## 2. 테스트 (CI 출처)

CI run [38963692932](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/38963692932)(`490904e` — 마지막 코드 커밋, build·no-docker 모두 success). 수는 그 run의 `test-reports` 아티팩트(결과 XML 36개)에서 셌고 로컬 `build`·`TZ=UTC check --rerun-tasks`와 같다.

| 모듈 | 소스셋 | 건수 | Phase 0 대비 | 비고 |
|---|---|---:|---:|---|
| `portal-domain` | test | 2,060 | +39 | 상태 기계 표 전체·사유 어휘·기록 타입 |
| `portal-projection` | test | 84 | +10 | `EventSchemaTest`(계약 v1.1·v1.1.1 +9) + `RecordingProjectorTest` |
| `portal-ingest` | test | 67 | +67 | 수신 검증·해시·클래스패스 사본·스트림 재계산·피드 스위트 ×2·적용기 17·주기 |
| `portal-infra` | test | 9 | 0 | |
| `portal-infra` | integrationTest | 128 | +15 | `TenantDirectoryIT` 12·`IngestEventRepositoryIT` 3(잠금 포함) |
| `portal-app` | test | 2 | +2 | CLI 인자 |
| `portal-app` | integrationTest | 16 | +13 | 워커 5·격리 1·결정론 1·폴링 조립 1·CLI 3·CLI 실행 1·부팅 4 |
| `portal-app` | archTest | 48 | +1 | Q6 스캐너 문장 |
| **합계** | | **2,414** | +148 | 실패 0, 스킵 0 |

- 같은 run의 빌드 검사: `printPlatformArtifacts: 13 files equal to the handoff §6 table, source=remote`, `verifyPlatformPins: 13 …`, `scanPlaintextLeaks: 36 files, 4 forbidden strings, 0 hits`(허구 설계사 이름 + **허구 서비스 토큰**, 각 원문·16진), `verifyUpstreamContract` "envelope-core.schema.json == …@db82d5d…", `files containing the token: 0`.
- `no-docker` 잡: "gradle exit code: 1", "result files: 6, with failures/errors: 6, skipped: 0"(Phase 0의 4 + `TenantDirectoryIT`·`IngestEventRepositoryIT`), "token occurrences: 0".
- 피드 계약 스위트 건수: `HttpFeedClientTest` 11(F1~F8 + H1~H3), `FileFeedAdapterTest` 10(F1~F8 + 파일 순서·깨진 파일).

## 3. 완료 기준 증거

| # | 기준 | 증거(클래스 › 메서드) | 주입 |
|---|---|---|---|
| C1 | 중복 → `SKIPPED`, 프로젝터 0, 커서 불변 | `IngestApplierTest` › `c1DuplicateIsSkippedWithoutProjectorCallsOrCursorChange`(행·커서 동등까지) | — |
| C2 | 역순 아는 eventId → 건너뜀, 새 eventId → 독 | `IngestApplierTest` › `c2AReplayedKnownEventIsSkipped`, `c2ANewEventAtAPastSeqIsPoison` | J1 |
| C3 | 갭 → `FAILED(SEQ_GAP)`, 커서 그대로, 뒤 미적용 | `IngestApplierTest` › `c3AGap…`, `IngestWorkerIT` › `c3AGapStopsTheTenantWithTheCursorWhereItWas` | J2 |
| C4 | 배치 중간 실패 → 앞 커밋·뒤 미적용 → 재개 시 정확히 | `IngestWorkerIT` › `c4AFailureMidBatchKeepsEarlierEventsAndAResumedWorkerContinuesExactly`(seq마다 프로젝터 1회) | J3 |
| C5 | T1 독, T2 같은 틱에 전진·이후도 전진 | `IngestIsolationIT` › `c5APoisonedTenantDoesNotStopTheOtherTenant`(스케줄러 틱 2회) | J4 |
| C6 | 스키마·`type↔kind` → 독, 롤백 + FAILED 행은 별도 트랜잭션 | `IngestApplierTest` › `c6ASchemaViolation…`·`c6ATypeKindMismatch…`·`c6AProjectorFailure…`, `IngestWorkerIT` › `c6APoisonEventsFailedRowSurvivesTheRollbackOfItsTransaction` | J5, J11 |
| C7 | 같은 eventId 다른 해시 → 독 | `IngestApplierTest` › `c7SameEventIdWithADifferentHash…`, `IngestWorkerIT` › `c7AHashMismatchKeepsTheAppliedRow` | J6 |
| C8 | `version` > 최대 → `PAUSED(UNSUPPORTED_VERSION)` | `IngestApplierTest` › `c8AVersionAboveTheMaximumPausesTheTenantWithoutRows`, `EnvelopeValidatorTest` › `versionAboveTheSupportedMaximum…` | — |
| C9 | 응답 `tenantId` ≠ 테넌트 → 적용 0·`FAILED(TENANT_MISMATCH)` | `FeedClientContract` › `f5AnotherTenantsFeedIsRefused`(HTTP·파일), `IngestApplierTest` › `c9AnotherTenantsFeedAppliesNothingAndFailsTheTenant` | J7 |
| C10 | `resume` n > 마지막 APPLIED 거부, 전 건 감사 | `IngestCliIT` › `c10ResumeIsBoundedByTheLastAppliedSeqAndEveryCommandIsAudited`(감사 8행 순서·대상까지), `IngestCliRunnerIT` | J8 |
| C11 | 두 워커 동시 → 각 이벤트 1회 | `IngestWorkerIT` › `c11TwoWorkersRacingOnOneTenantApplyEachEventOnceAndLeaveItRunning`(래치로 경합, 둘째 워커 적용 0·정지 없음, 테넌트 RUNNING) | J9 |
| C12 | 두 어댑터 같은 스위트, HTTP 401·422 | `HttpFeedClientTest`(F1~F8·H1~H3), `FileFeedAdapterTest`(F1~F8) | — |
| C13 | 같은 스트림 2회(재시작 포함) → 두 표 동일 | `IngestDeterminismIT` › `c13TheSameStreamGivesTheSameRowsWithOrWithoutARestart` | — |
| C14 | Phase 0 무손상, `portal-ingest` platform-spring 0(규칙 (e) + `verifySpringFree`에 추가), `TZ=UTC`, build·no-docker, 평문 스캔(토큰 센티널) | archTest 48, CI 38963692932, 로컬 `TZ=UTC ./gradlew check --rerun-tasks --no-daemon` 성공 | J10 |
| Q3 | `portal_directory`는 `tenant_id`만 | `TenantDirectoryIT`(12), `RlsIsolationIT`(정책·롤 정확 집합) | J12 |

### 주입 기록 (ID 단위 12건 — 승인 §6 예상과 같다)

| ID | 대상 | 주입 | 실패한 곳 |
|---|---|---|---|
| J1 | C2 | 과거 seq·새 eventId를 건너뜀으로 | `IngestApplierTest.c2ANewEventAtAPastSeqIsPoison` |
| J2 | C3 | 갭 검사 끔 | `IngestApplierTest.c3AGap…` |
| J3 | C4 | 워커가 적용 전에 커서를 묶음 끝으로 따로 커밋 | `IngestWorkerIT` c4(첫 회차 적용 0, 기대 5)·c3·c6·c7·c11, `IngestIsolationIT`, `IngestDeterminismIT` — 10건 중 7 |
| J4 | C5 | 스케줄러에 "한 테넌트라도 멈추면 틱 전체 건너뜀" 플래그 | `IngestIsolationIT.c5…` |
| J5 | C6 | 수신 검증을 단순 읽기로 | `IngestApplierTest` c6 스키마·c6 짝·c8 — 61건 중 3 |
| J6 | C7 | 해시 비교 끔 | `IngestApplierTest.c7…` |
| J7 | C9 | `FeedResponses`의 테넌트 대조 제거 | `FileFeedAdapterTest.f5`·`HttpFeedClientTest.f5`(+ 실패 4건을 세는 `h3`) |
| J8 | C10 | 성공한 resume의 감사 쓰기 제거 | `IngestCliIT.c10…`·`IngestCliRunnerIT` |
| J9 | C11 | 잠금이 늘 성공 | `IngestWorkerIT.c11…` — "second worker must not stop a healthy tenant … STOPPED, APPLY_ERROR": 승인이 짚은 그대로(PK 대기 뒤 0행 upsert → 멀쩡한 테넌트 정지) |
| J10 | C14 | `HttpFeedClient`가 토큰을 표준 출력에 | `scanPlaintextLeaks` FAILED(`HttpFeedClientTest.xml`·`IngestPollingIT.xml`) |
| J11 | C6 | `FAILED` 행을 본 트랜잭션 안에서 쓰기(함께 롤백) | `IngestApplierTest` c6 스키마·c6 짝(행 없음) |
| J12 | Q3 | V3에서 `name` 컬럼도 GRANT | `TenantDirectoryIT` 2/12(`SELECT name` 권한 오류 아님, 권한 목록에 `tenant.name`) |

전부 첫 시도에 의도한 시험이 실패했다(J3·J5·J7·J8·J11은 의도한 것 외의 시험도 함께 실패 — 표에 적었다).

## 4. 상태 기계·독 이벤트·스트림 (지시문 §5)

**상태 기계**(정본 `IngestTransitions`, 시험 `IngestTransitionsTest` — 모든 상태 × 사건):

| 지금 | 사건 | 다음 |
|---|---|---|
| (없음) | 첫 회차 | RUNNING(last_seq 0) |
| RUNNING | 적용·건너뜀·피드 일시 실패 | RUNNING |
| RUNNING | 데이터 결함(`SEQ_GAP`·`TENANT_MISMATCH`·`AFTER_BEYOND_HEAD`·`SCHEMA_VIOLATION`·`TYPE_KIND_MISMATCH`·`HASH_MISMATCH`·`EVENT_SEQ_CONFLICT`·`PAST_SEQ_NEW_EVENT`·`PROJECTOR_ERROR`·`APPLY_ERROR`) | FAILED |
| RUNNING | `UNSUPPORTED_VERSION`·`FEED_UNAUTHORIZED`·`TOKEN_MISSING`·운영자 pause(`OPERATOR_PAUSE`) | PAUSED |
| FAILED·PAUSED | 운영자 resume(n ≤ 마지막 APPLIED) | RUNNING |
| 그 밖 | — | 거부(REBUILDING은 Phase 2) |

**독 이벤트 분류**: 설계서 §6.1 표(판정 0~9, 기록 열 포함)가 정본이다 — 계획 ④에 `APPLY_ERROR`(#9) 하나를 더했다(§5 F1).

**스트림 파일**(`portal-ingest/src/test/resources/streams/`, 표는 그 README): `a1-golden`·`dup`·`replay-known`·`replay-new`·`gap`·`poison-schema`·`poison-kind`·`hash-mismatch`·`version2`·`other-tenant` — 10개, `StreamFilesTest`가 샘플에서 다시 계산해 대조. IT는 머리의 `tenantId`만 그 테넌트로 바꾼 사본을 쓴다(`IngestRig.streamFor`).

## 5. 계획과 다르게 한 것·해석·발견

| # | 무엇 | 처리 | 표시 |
|---|---|---|---|
| F1 | 독 분류에 `APPLY_ERROR` 추가(DECISIONS P1-1) | 분류하지 못한 적용 트랜잭션 오류는 멈춘다 — 승인된 J9 분석이 성립하려면 필요했다(재시도면 고유 위반이 조용히 지나가 J9가 잡히지 않는다). detail은 예외 이름만 | **[더함]** |
| F2 | 감사 동작 `INGEST_PAUSE_REJECTED`(P1-2) | "전 건 감사" | [더함] |
| F3 | `tenant.status` ≠ `ACTIVE`인 테넌트는 폴링하지 않음(P1-3) | §5의 뜻대로 | **[더함]** — 확인 요청(§6 Q1) |
| F4 | 행위자 없는 CLI는 DB 전에 거부(P1-4) | 감사할 행위자가 없다 | 기록 |
| F5 | `IngestCommands.pause`가 잠금 전에 커서를 읽었다(6단계 커밋) | 자동 보안 검토가 짚었다 — 그 사이 워커가 커밋한 전진을 낡은 `last_seq`로 되돌릴 수 있었다. 7단계에서 잠금 먼저로 고치고 시험 추가(`commandsAreRefusedWhileAWorkerHoldsTheTenant`) | **결함 수정** |
| F6 | `IngestCursorRepository`가 `Clock`을 갖던 것을 호출자가 시각을 넘기게(인메모리 포트와 같은 모양) | 저장소는 저장만 | 기록 |
| F7 | 피드 응답 모양 검사는 이벤트를 비운 사본으로(`FeedResponses`) | 독 이벤트 하나가 묶음 전체를 "도달 불가"로 만들어 원인 이벤트가 기록되지 않는 것을 막는다 | 해석 |
| F8 | 파일 어댑터는 첫 `seq > afterSeq` 줄부터 파일 순서 그대로(뒤의 작은 seq 포함) | 중복·역순 스트림을 싣기 위해. HTTP 스텁은 엔진 규약(seq > afterSeq만) | 해석 |
| F9 | 새 외부 아티팩트: `io.github.erdtman:java-json-canonicalization` 1.1(`platform-canonical` 경유), slf4j 2.0.17 POM 셋 | 락·검증 메타데이터를 빈 `GRADLE_USER_HOME`에서 세 번 다시 만들었다(2·3·5단계) | 기록 |
| F10 | 폴링은 기본 꺼짐(`portal.ingest.enabled=false`) | 배포 구성(포털 Phase 8)이 켠다. 시험은 `IngestPollingIT`가 켜서 조립 전체를 본다 | 기록 |
| F11 | `RlsIsolationIT`의 정책·롤 단언을 새 정책 하나·새 롤 하나로 늘렸다 | 집합 일치(정확히 그것뿐)는 그대로 — 약화 아님 | 기록 |

**CLI 인가의 한계(승인 ⑨)**: 조회 API·인증이 오는 Phase 3 전까지 운영 CLI는 실행한 OS 사용자를 믿는다(`--actor`는 감사 기록용 이름이지 인증이 아니다) — 운영 호스트 접근 통제가 곧 인가다. README "인제스트 운영"에 같은 문장. Phase 3에서 `identity_link` 역할로 바꾼다.

도구·라이브러리 출력에서 지시문처럼 보이는 문장은 없었다(불변 규약 8). 설계서는 v1.4(변경 이력 맨 위).

## 6. 다음 Phase 질문

- **Q1(F3)**: `tenant.status = PAUSED`인 테넌트를 폴링하지 않는 것을 그대로 둘지(지금), 아니면 인제스트는 `ingest_cursor.state`만 보게 할지.
- **Q2 Phase 2 — 부록 A-1 골든 뷰**: 프로젝터가 `RecordingProjector`를 대체한다. 골든 뷰 파일(샘플 01~17 → `earnings_line`·`earnings_month`·`policy_limit`·`deferral_item`… 기대 행)을 계약 샘플 기준으로 둔다 — 엔진 E1 골든과는 금액이 다르다(FY 1,890,000 vs 2,800,000, 승인 §4).
- **Q3 재구축(§6.7) — 그림자 스키마와 FORCE RLS·`portal_migrator`**: 마이그레이터도 정책을 따르므로(B1) 재구축 스왑은 테넌트마다 `set_config` 뒤 실행해야 한다. 그림자 스키마를 `portal_migrator`가 만들고 `portal_app`이 채우는지, 스왑(`ALTER TABLE … RENAME`)의 권한·잠금 범위, `REBUILDING` 상태 전이(지금 Phase 1은 아무 사건도 받지 않는다)를 Phase 2 계획에서.
- **Q4 O-1**: `agent_profile.role` NULL 허용 새 V(승인 결정 — Phase 2).
- **Q5 DD-5 후보**: `policy_limit_history` PK `(tenant_id, ledger_id, posting_seq)` ↔ `LimitLedgerRebased`(전기 순번 없음·같을 수 있음) — 이벤트 `seq`로.
- **Q6 실 엔진 E2E 1건**: E1 수용 뒤 `HttpFeedClient` → 엔진 `GET /internal/v1/events`(서비스 토큰·`tenantId`·422). 엔진 저장소 쪽 실행 방법(엔진 앱 기동 + Oracle)과 포털 쪽(이 저장소 IT로 둘지, 별도 스크립트로 둘지)을 정해 주기 바란다.
