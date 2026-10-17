# Phase 3 보고서 — ga-agent-portal 조회 API·인증·인가 (지시문 v1.0)

> 지시문 `docs/phase-03-지시문.md` · 계획 `docs/phase-03-계획.md` · 승인 `docs/phase-03-계획승인.md`(2026-10-17, Q1~Q12 권장안, DE-12~18) ·
> 브랜치 `work/phase-3` · PR [#9](https://github.com/hjryoo-ai/ga-agent-portal/pull/9) · 태그 `phase-3`(이 보고서 커밋). **병합은 수용 심사 회신 뒤.**

## 1. 커밋

`main` = `90041b4`(계약 v1.4, #8) 위 `work/phase-3`(`main..phase-3`):

| 커밋 | 내용 | 주입 |
|---|---|---|
| `301926a` | 지시문·계획(검증 순서, `Caller` 흐름, 인가 매트릭스 초안, DTO ↔ 열 표, V5, ETag, 주입 M1~M16, Q1~Q12) | — |
| `c9dab26` | 승인 보관·계획 반영(Q1~Q12, DE-12~18, DD-13, ETag `cfg` 표) | — |
| `85c5ddd` | 2단계 — V5, `identity_link` 역할·상태·조직, `portal_issuer`, `caught_up_at`, `limit_multiple` `TEXT` | M12·M14·M15 |
| `fd6a870` | 3단계 — `/api` 보안 체인(`iss` → 테넌트, `identity_link` 호출자, 같은 401·404, 거부 감사), 새 의존성 | M2·M4·M8·M13·M16 |
| `7189ed7` | 4단계 — `/api/v1/me/**` 유스케이스 8, 설계사 술어 저장소, 계약 `portal-api.openapi.yaml`, ETag | M1·M6·M6b·M7·M10·M11 |
| `2884e72` | 5단계 — 인가 매트릭스·`ApiLayerRulesTest`·두 번째 SQL 술어·`portal-query` Spring 무의존 | M1(스캔)·M3·M5 |
| `f72c1c0` | 6단계 — CLI 인가(`OPERATOR` 링크, `--actor` 폐기), `docs/운영.md` | M9 |
| `2cc5f52` | 7단계 — 실 엔진 E2E `e2e/engine-feed/run.sh`, `runTestIssuer`·`e2eCheck`(정규 빌드 밖) | — |
| `5447f3a` | 8단계 — 설계서 v1.6 머리·§13·§14.1, CLAUDE.md 조회 API 규약, DECISIONS, `TODO(confirm#2/#5/#9)` | — |
| (이 커밋) | 보고서 | — |

단계마다 로컬 `TZ=UTC ./gradlew check --rerun-tasks`가 성공한 뒤 커밋했다(수는 각 커밋 본문). PR은 8단계 뒤에 열어 CI는 `5447f3a` 한 번이다(§2).

```
portal-domain   Role·LinkStatus·IdentityLinkRecord · view/ LineItem·LineDetail·LinePage·LedgerItem·DueMonth·SyncSnapshot
portal-query    (신설 내용 — Spring·DB 무의존) MeQueries(@UseCaseEntry 8)·QueryAuthorization·UseCaseEntry·Caller·DenyReason·NotFound·InvalidRequest·QueryResult
                rule/ SyncStatusRule·LimitStage·PayoutDay·PolicyMask·Etags · port/ AgentQueryStore·SyncStatusStore·PolicyKeys·ReadTransaction · api/ 응답 레코드 12
portal-infra    V5__query_api.sql · directory/IssuerDirectoryReader(전용 롤 portal_issuer — 직접 접근 허용 목록 첫 항목) · identity/IdentityLinkResolver
                persistence/ AgentQueryRepository(16문장, 모두 agent_id = :agentId)·SyncStatusRepository·IdentityLinkRepository · AuditAccessRepository.recordDenial
                IngestCursorRepository.caughtUp
portal-ingest   IngestApplier.markCaughtUp · IngestWorker(성공한 페이지마다)
portal-app      web/ ApiSecurityConfiguration·IssuerTenantResolver·TenantBindingFilter·CallerArgumentResolver·ApiExceptionHandler·ExceptionBarrierFilter·
                     ProblemResponses·ApiAuthenticationEntryPoint·SingleBearerTokenResolver·DenialAudit·ApiMetrics·LogIds·MeController·EtagSupport·UnroutedHandler
                query/ QueryConfiguration·JdbcAgentQueryStore·HmacPolicyKeys · ingest/ OperatorAuthorization · IngestCliRunner(--actor → 64)
                integrationTest/ TestIssuer(인프로세스 RS256 + JDK HttpServer)·ApiContract·QuerySeed·TestIssuerMain·EngineFeedE2eCheck
contracts/api/v1/portal-api.openapi.yaml 1.0.0 · docs/authz-matrix.md · docs/운영.md · e2e/engine-feed/run.sh
```

## 2. 테스트 (CI 출처)

CI run [39018974140](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/39018974140)(`5447f3a`, build·no-docker 모두 success). 수는 그 run의 `test-reports` 아티팩트(결과 XML 77개)를 내려받아 셌고, 로컬 `TZ=UTC ./gradlew check --rerun-tasks`(`5447f3a`)와 같다. 기준선은 `main` = `90041b4`의 CI run 39009873162 아티팩트(2,521 — Phase 2 태그 2,504 + 계약 v1.4의 17).

| 모듈 | 소스셋 | 건수 | `main` 대비 | 비고 |
|---|---|---:|---:|---|
| `portal-domain` | test | 2,084 | +1 | `IdentityLinkRecordTest`(두 CHECK의 거울) |
| `portal-projection` | test | 135 | 0 | `EventSchemaTest`가 새 계약 파일까지 `format` 0 검사 |
| `portal-ingest` | test | 80 | +5 | `CaughtUpTest` 5 |
| `portal-query` | test | 26 | +26 | `MeQueriesTest` 7·`SyncStatusRuleTest` 6·`LimitStageTest` 3·`PayoutDayTest` 3·`PortalApiContractTest` 3·`PolicyMaskTest` 2·`EtagsTest` 2 |
| `portal-infra` | test | 10 | +1 | `TenantContextGuardTest`(목록 = 패키지의 모든 저장소 — F8) |
| `portal-infra` | integrationTest | 172 | +40 | `QueryApiSchemaIT` 22·`IssuerDirectoryIT` 15·`V5UpgradeIT` 1·`TenantDirectoryIT` +2 |
| `portal-app` | test | 20 | +18 | `IssuerTenantResolverTest` 10·`AuthzMatrixTest` 3·`HmacPolicyKeysTest` 2·`EtagSupportTest` 2·`IngestCliRunnerTest` +1 |
| `portal-app` | integrationTest | 64 | +27 | `UniformRejectionIT` 4·`TenantBindingIT` 5·`AuditIT` 4·`AuthzIT` 4·`CachingIT` 5·`ResponseLeakScanIT` 1·`QueryStatementIT` 1·`CaughtUpIT` 1·`IngestCliIT` c10 +2 |
| `portal-app` | archTest | 64 | +16 | `ApiLayerRulesTest` 6, `TenantPredicateScanTest` +10(두 번째 술어·스캐너 사례) |
| **합계** | | **2,655** | **+134** | 실패 0, 스킵 0 |

- 같은 run의 빌드 검사: `printPlatformArtifacts: 13 files equal to the handoff §6 table, source=remote (Gradle cache)`, `verifyPlatformPins: 13 com.ga.platform:0.2.0 files pinned, equal to the handoff §6 table`, `verifyContractChecksums: contracts/CHECKSUMS and contracts/shared/ga-disclosure/CHECKSUMS match`, `scanPlaintextLeaks: 77 files, 50 forbidden strings, 0 hits`, `verifyTestInputs: 8 test tasks read repository files, all declared`, `verifyUpstreamContract` "envelope-core.schema.json == …ga-disclosure@db82d5d…", `:portal-domain`·`:portal-projection`·`:portal-ingest`·**`:portal-query`**`:verifySpringFree`, `files containing the token: 0`.
- `no-docker` 잡: "gradle exit code: 1", "result files: 10, with failures/errors: 10, skipped: 0"(Phase 2의 7 + `IssuerDirectoryIT`·`QueryApiSchemaIT`·`V5UpgradeIT`), "token occurrences: 0".
- 평문 스캔 센티널 50(Phase 2의 4 → 시드 subject·`agent_id`·증권번호 — D-14).

## 3. 완료 기준 증거

| # | 기준 | 증거(클래스 › 메서드) | 주입 |
|---|---|---|---|
| C1 | IDOR — 남의 `calcId`·`ledgerId`·월 → 404, 없는 키와 바이트 동일 | `AuthzIT` › `anotherAgentsKeysAreNotFoundWithTheSameBytesAsAMissingKey`·`anAgentIdInTheQueryStringChangesNothing`·`anotherAgentsPolicyKeyFiltersToNothing`, `MeQueriesTest` › `anotherAgentsKeysAreMissing`, `TenantPredicateScanTest`(두 번째 술어 — 16문장) | M1(IT·스캔) |
| C2 | 401 같은 바이트·헤더 | `UniformRejectionIT` › `everyUnauthenticatedRequestGetsTheSameBytesAndHeadersAndOnlyTheMetricKnowsWhy` — 15 요청 × **여덟** 사유(지시문은 일곱 — `NO_TOKEN`·`MALFORMED`를 가름), 헤더 집합 비교(`Date` 제외), 사유는 지표에만. `IssuerTenantResolverTest` 10(순서·시계·대상·`alg`) | M2, M16 |
| C3 | 404 네 사유 같은 바이트 | `UniformRejectionIT` › `everyNotFoundGetsTheSameBytesAndHeaders`(링크 없음·`REVOKED`·`OPERATOR`·없는 자원 + 경로 없음·경로 변수 형식·메서드 불일치), `AuthzIT` › `aManagerLinkIsDeniedAtEveryEndpointEvenWithData` | M3 |
| C4 | `iss` ↔ 테넌트, `tenant_id` 불일치 401, 남의 `agent_id` 클레임 → 자기 데이터 | `TenantBindingIT` › `theIssuerChoosesTheTenantAndTheLinkChoosesTheAgent`·`agentAndRoleClaimsInTheTokenChangeNothing`·`aTenantClaimMustMatchTheIssuersTenantAndAnotherIssuersAudienceIsRejected`·`unregisteringAnIssuerTakesEffectOnTheNextRequest`·`anIssuersMetadataIsFetchedOnceAndOnlyForRegisteredIssuers` | M4 |
| C5 | 매트릭스 ↔ 코드 양방향, `@UseCaseEntry` 전수, 컨트롤러가 `Principal` 안 읽음 | `AuthzMatrixTest` 3(문서 ↔ `QueryAuthorization` ↔ `@GetMapping` ↔ `@UseCaseEntry`, 칸 변경 감지), `ApiLayerRulesTest` ⓐ~ⓕ | M5 |
| C6 | 응답에 `policy_no` 원문 0, `policyMasked`·`policyKey` | `ResponseLeakScanIT` › `noResponseCarriesAPolicyNumberOrAnotherAgentsData`(82 응답 — §4.3), `MeQueriesTest` › `responsesCarryMaskedPoliciesAndKeysOnly`·`aPolicyKeyResolvesOnlyWithinTheCallersPolicies`, `PolicyMaskTest` 2, `HmacPolicyKeysTest` 2, `PortalApiContractTest` › `noSchemaTalksAboutAPolicyNumber` | M6, M6b |
| C7 | ETag·304·`private, no-cache` | `CachingIT` › `aMatchingIfNoneMatchGets304WithTheSameHeadersAndNoBody`·`anotherAgentsResourceIs404EvenWithAMatchingTag`·`aDelaySwitchAtTheSameSeqChangesTheTagAndTheStatus`·`onlyTheSettingsAResponseReadsChangeItsTag`, `EtagsTest`, `EtagSupportTest` | M11 |
| C8 | `dataStatus`·`monthDataStatus` | `SyncStatusRuleTest` 6(상태 넷 × 지연 경계 — 경계 포함), `CachingIT` › `everyNonRunningCursorStateIsSyncDelayedAndTheMonthStatusIsSeparate`, `CaughtUpTest` 5·`CaughtUpIT`(유휴 회차가 갱신, 피드 실패·정지·머리 뒤는 갱신 안 함) | M7, M12 |
| C9 | 거부 감사 별도 tx, 로그·지표 식별자 평문 0 | `AuditIT` › `eachNotFoundReasonIsAuditedWithTheRouteTemplateAndAClosedCode`·`theDenialAuditSurvivesTheRollbackOfTheRequestTransaction`·`unauthenticatedRequestsWriteNoAuditRowAndTagTheTenantOnlyForARegisteredIssuer`·`theDenialLogCarriesTheSubjectHashNotTheSubject`, `scanPlaintextLeaks` 0 hits | M8, M13 |
| C10 | CLI 미등록 OS 사용자 거부·감사, `OPERATOR` 토큰 `/api` 404 | `IngestCliIT` › `c10AnOsUserWithoutAnActiveOperatorLinkIsRefusedAndAudited`·`c10StatusOverSeveralTenantsReportsEachAndExitsTwoIfAnyIsRefused`, `IngestCliRunnerIT`(허구 `user.name`), `IngestCliRunnerTest`(`--actor` → 64); `OPERATOR` 토큰은 `UniformRejectionIT`(404 바이트)·`AuditIT`(detail `ROLE`) | M9 |
| C11 | 모든 응답이 계약 통과, `format` 0 | `ApiContract.check`가 IT의 **모든** `/api` 응답(200·304·400·401·404)을 `portal-api.openapi.yaml`로 검증, `PortalApiContractTest` 3(레코드 ↔ 스키마 양방향, 경로 = 인가 키, 경로마다 401·404), `EventSchemaTest`(계약 파일 전부 `format` 0) | (M6 — 계약이 잡음) |
| C12 | `payoutExpectedOn`·`utilization`·단계(정수 비교) | `LimitStageTest` › `boundariesAreExactIntegerComparisons`(정확히 80.00%·79.99%·95.00%)·`aZeroLimitIsFullAndNegativeAccumAfterClawbackIsOk`·`overflowFailsLoudly`, `PayoutDayTest` 3(2월 D31 생략 — 말일로 당기지 않음), `MeQueriesTest` › `utilizationIsOmittedForAZeroLimitAndTheStageFilterApplies` | — |
| C13 | 홈 3콜 쿼리 수 상한 | `QueryStatementIT` › `homeCallsHaveAFixedStatementCountIndependentOfData` — §4.4 | M10 |
| C14 | V5 뒤 `limit_multiple` 원문, 기존 골든 무손상 | `QueryApiSchemaIT` › `limitMultipleKeepsTheContractStringByteForByte`·`limitMultipleOutsideTheContractPatternIsRejected`, `V5UpgradeIT` › `existingLimitMultiplesKeepTheirReadForm`(V4 데이터 → V5), `ProjectionGoldenIT` 9 | M14 |
| C15 | E2E 스크립트 존재·문서화, 결과 | `e2e/engine-feed/run.sh` + `README.md` — **미실행**(§4.6). `RESULT.md` 없음 | — |
| C16 | Phase 0~2 무손상, 아키텍처, `TZ=UTC`, CI, 평문 스캔, 주입 ID 단위 | CI 39018974140(§2), archTest 64, 로컬 `TZ=UTC ./gradlew check --rerun-tasks` 성공, 아래 주입 표 | — |
| Q1 | 전용 롤이 세 열만 | `IssuerDirectoryIT` 15(세 열 밖·다른 표·쓰기 = 권한 오류, `NOINHERIT`·`NOBYPASSRLS`) | M15 |

### 주입 기록 (ID 단위 17건)

각각 단독으로 넣고 해당 시험을 돌려 실패를 본 뒤 되돌렸다. 실패 문구는 단계 커밋 본문에 있다.

| ID | 기준 | 주입 | 실패한 곳 |
|---|---|---|---|
| M1 | C1 | 원장 조회 문장에서 `agent_id = :agentId` 제거 | `AuthzIT` [B의 원장] 404 기대 200(4단계), `TenantPredicateScanTest` "2 agent table references but 1 predicate"(5단계) |
| M2 | C2 | 만료 토큰에 다른 본문 | `UniformRejectionIT` [expired] 바이트 |
| M3 | C3 | `QueryAuthorization`이 `MANAGER` 허용 | `MeQueriesTest`, `AuthzMatrixTest`, `AuthzIT` [/api/v1/me] 404 기대 200 |
| M4 | C4 | `Caller`가 토큰의 `agent_id` 클레임 사용 | `TenantBindingIT` — `PH3-A-7731` 대신 `PH3-B-7732`를 돌려줌 |
| M5 | C5 | 유스케이스 없는 `@GetMapping /api/v1/me/whoami` | `ApiLayerRulesTest` "whoami(Caller) calls 0 @UseCaseEntry methods", `AuthzMatrixTest` 2건 |
| M6 | C6 | 라인 응답에 `policyNo` | `MeQueriesTest`, `PortalApiContractTest`, `ResponseLeakScanIT`(계약: additional property `policyNo`) |
| M6b | C6 | 증권번호를 `productName` 안에(계약은 통과) | `ResponseLeakScanIT` [T1/A responses contain a policy number] — 계약 밖 누출은 스캔이 잡는다 |
| M7 | C8 | `REBUILDING`을 `RUNNING`처럼 | `SyncStatusRuleTest` [REBUILDING], `CachingIT` [REBUILDING] `dataStatus` OK |
| M8 | C9 | 거부 로그에 `sub` 평문 | `AuditIT`, `scanPlaintextLeaks`(AuditIT XML) |
| M9 | C10 | CLI 등록 검사 제거 | `IngestCliIT` 2건(`cli:nobody`가 상태를 받음, 다중 테넌트 0), `IngestCliRunnerIT` 2 기대 0 |
| M10 | C13 | 카드마다 원장 재조회(N+1) | `QueryStatementIT` `/limits` heavy 203 vs light 4 |
| M11 | C7 | ETag에서 동기화 글자 제거 | `EtagsTest`, `MeQueriesTest`, `CachingIT`(지연 전환 뒤 200 대신 304) |
| M12 | C8 | `markCaughtUp`을 적용 건수 > 0일 때만 | `CaughtUpTest.aRoundThatReachesTheHeadRecordsItAndAnIdleRoundRefreshesIt`(01:00 vs 01:10) |
| M13 | C9 | 거부 감사를 링크 조회 트랜잭션 안에서 | `AuditIT` — 감사 행 0(롤백), 404 바이트는 그대로 |
| M14 | C14 | V5 `USING limit_multiple::text` | `V5UpgradeIT` `[L1=12.00, L2=1.50, L3=12.25]` |
| M15 | Q1 | `portal_issuer`에 `name` GRANT | `IssuerDirectoryIT` 2/14(당시 14건) |
| M16 | ② | `alg` 검사 제거 | `IssuerTenantResolverTest`(`ALG_NOT_ALLOWED` 대신 `SIGNATURE`), `UniformRejectionIT` 지표 `ALG_NOT_ALLOWED` 0 vs 2 — 응답은 401 그대로(디코더가 RS256만 받는 둘째 층) |

## 4. 지시문 §5 추가 항목

### 4.1 인가 매트릭스 (정본 `docs/authz-matrix.md`, `AuthzMatrixTest`가 코드와 양방향 대조)

| 엔드포인트 | AGENT | MANAGER | OPERATOR | 링크 없음 | REVOKED |
|---|---|---|---|---|---|
| `GET /api/v1/me` | SELF | 404 | 404 | 404 | 404 |
| `GET /api/v1/me/earnings` | SELF | 404 | 404 | 404 | 404 |
| `GET /api/v1/me/earnings/{ym}` | SELF | 404 | 404 | 404 | 404 |
| `GET /api/v1/me/earnings/{ym}/lines` | SELF | 404 | 404 | 404 | 404 |
| `GET /api/v1/me/earnings/lines/{calcId}` | SELF | 404 | 404 | 404 | 404 |
| `GET /api/v1/me/limits` | SELF | 404 | 404 | 404 | 404 |
| `GET /api/v1/me/limits/{ledgerId}` | SELF | 404 | 404 | 404 | 404 |
| `GET /api/v1/me/deferrals` | SELF | 404 | 404 | 404 | 404 |

`SELF` = 그 설계사의 행만(남의 키·없는 키 → 같은 404). 404의 감사 detail은 `NO_LINK`·`REVOKED`·`ROLE`·`MISSING`. 인증 실패는 이 표 앞에서 같은 401(감사 없음, 지표만).

### 4.2 거부 바이트

| 상태 | 본문 | 길이 | SHA-256 |
|---|---|---:|---|
| 401 | `{"code":"UNAUTHENTICATED","message":"authentication required"}` | 62 | `83ec09b61b9dbdb8d10cda92c5cc8656e74068bcd76f2fe92d71f0cb36181a6e` |
| 404 | `{"code":"NOT_FOUND","message":"not found"}` | 42 | `caa8747f38d8b92344e8f31b23cdb9c1c3c02d9ba3f471d3230ac147f4ef110a` |
| 400 | `{"code":"INVALID_REQUEST","message":"invalid request"}` | 54 | `e97da8643de113045aa44a97049edb14a4526a690226ec4002ea6a6dd152e9a1` |
| 500 | `{"code":"INTERNAL_ERROR","message":"internal error"}` | 52 | `852112e471f33f369bbd48d53c19380db5ef7b975a041fe51d3673302da1803b` |

전부 `Content-Type: application/problem+json`, `Cache-Control: private, no-cache`(`/api`), 401만 `WWW-Authenticate: Bearer`(여덟 사유 같은 값). `ProblemResponses`의 고정 바이트이고 `UniformRejectionIT`가 요청마다 본문 바이트와 헤더 집합(`Date` 제외)을 첫 응답과 비교한다.

### 4.3 응답 스캔 (`ResponseLeakScanIT`, CI 39018974140의 출력)

시드: 테넌트 둘 × 설계사 넷(A-1 둘째 판을 실 적용기로 넣은 설계사 포함), 증권번호·남의 ID·남의 이름 센티널.

| 엔드포인트 | 응답 수 |
|---|---:|
| `GET /api/v1/me` | 4 |
| `GET /api/v1/me/earnings` | 8 |
| `GET /api/v1/me/earnings/{ym}` | 9 |
| `GET /api/v1/me/earnings/{ym}/lines` | 12 |
| `GET /api/v1/me/earnings/lines/{calcId}` | 18 |
| `GET /api/v1/me/limits` | 16 |
| `GET /api/v1/me/limits/{ledgerId}` | 7 |
| `GET /api/v1/me/deferrals` | 8 |
| **합계** | **82** |

결과: 증권번호 원문 0, 남의 설계사 ID·이름 0, 한 증권번호 → 한 `policyKey`(모든 응답에서 같음), 모든 응답 계약 통과.

### 4.4 조회 문장 수 (`QueryStatementIT`, CI 출력)

| 호출 | 데이터 적음 | 데이터 많음(라인 1,000·원장 200) | + 발급자 조회 | 상한(계획 ⑤) |
|---|---:|---:|---:|---:|
| `/api/v1/me` | 3 | 3 | 1 | 4 |
| `/api/v1/me/earnings/202608` | 4 | 4 | 1 | 5 |
| `/api/v1/me/limits?status=WARN` | 3 | 3 | 1 | 4 |

애플리케이션 문장 = 링크 1 + 커서·테넌트 조인 1 + 본문(`/me` 1, 월 2 — 월 행·`byType`, 한도 1 — 원장 + 열린 플래그 `array_agg`). 발급자 조회는 전용 풀(`portal_issuer`)이라 따로 센다.

### 4.5 검증 메타데이터 diff (빈 `GRADLE_USER_HOME`에서 재생성)

| 단계 | 추가 | 제거 | 내용 |
|---|---:|---:|---|
| 3 | 24 아티팩트 / 12 구성 요소 | 0 | `spring-boot-starter-security`·`-security-oauth2-resource-server`, `spring-boot-security`·`-security-oauth2-resource-server`(4.1.1), `spring-security-{config,core,crypto,oauth2-core,oauth2-jose,oauth2-resource-server,web}` 7.1.1, `nimbus-jose-jwt` 10.9.1 — 각 jar + pom/module. 전부 Boot BOM 관리 버전 |
| 4 | 0 | 0 | 락 파일의 구성 이름 줄만(새 모듈 의존 배선) |
| 5~8 | 0 | 0 | 의존성 변경 없음 |

의도 외 아티팩트 0(승인 F7~F9 조건). HikariCP는 이미 Boot BOM으로 잠겨 있던 7.0.2라 메타데이터가 바뀌지 않았다.

### 4.6 E2E (`e2e/engine-feed/run.sh`)

**미실행.** 승인 §4 "E2E는 사용자 실행" — 이 Phase에서 나는 실행하지 않았고 `RESULT.md`는 없다. 확인한 것은 셋뿐: `bash -n` 구문, `:portal-app:runTestIssuer` 단독 기동(발급자 메타데이터 응답·토큰 파일 600·PID 파일 — 상태 디렉터리는 작업 공간 밖 임시 경로), 두 메인 클래스 컴파일. 엔진 스택·포털 인제스트·판정은 돌려 보지 않았다. 스크립트가 하는 일과 비밀 처리는 `e2e/engine-feed/README.md`. 지시문과 다른 점 둘(DECISIONS P3-10): `ingest_lag_seq` 지표 대신 같은 조건의 DB 열 `caught_up_at`을 기다린다(actuator는 `health`만 노출), 자리 단계 README의 재구축 재대조는 넣지 않았다.

## 5. 계획과 다르게 한 것·해석·발견

| # | 무엇 | 처리 | 표시 |
|---|---|---|---|
| F1 | Spring `JwtIssuerAuthenticationManagerResolver` 대신 포털 `AuthenticationManager`(P3-1) | 그 리졸버는 형식 오류와 미등록 발급자를 같은 예외로 내고 `alg` 전에 발급자를 찾는다 | 기록 |
| F2 | `Jwt` 독자 셋 → 하나(P3-2) | 검증기가 (테넌트, `sub`)만 남긴다 | 기록 |
| F3 | `AgentQueryRepository` 패키지(P3-3), 조회 행 타입 `portal-domain.view`(P3-4) | 매퍼 공유·의존 규칙 | 기록 |
| F4 | 지표 `tenant` 태그 `none`(P3-5) | 승인 Q6 "등록된 경우만"을 태그 값으로 — Prometheus 레지스트리는 태그 키 집합이 다른 같은 이름을 거부한다 | **확인 요청** |
| F5 | 라인 목록: 그 달 월 행이 없으면 404(P3-6) | `/earnings/{ym}`과 같은 자원 판단 | 해석 |
| F6 | JWKS 장애 → 500(P3-7) | 토큰의 잘못이 아니다 — 401이면 클라이언트가 재로그인을 되풀이 | [더함] |
| F7 | V5 채우기를 `UPDATE` 없이(P3-8) | SQL 스캔이 거부 — 규칙을 좁히지 않고 문장을 바꿨다 | 기록 |
| F8 | **`TenantContextGuardTest` 누락** — Phase 2의 `ViewResetRepository`가 가드 목록에 없었다(P3-9) | 목록 = 패키지의 모든 저장소로 바꾸어 이제 덮인다(새 저장소가 빠지면 실패) | **Phase 2 결함 수정** |
| F9 | Spring `Jwt`는 `exp ≤ iat`를 생성자에서 거부한다 — 그 토큰은 서명 단계 예외로 와서 `SIGNATURE`로 분류된다 | 401 바이트는 같고 지표 사유만 `SIGNATURE`. 시험 토큰의 `iat`를 고쳤다 | 발견 |
| F10 | 전체 실행에서 `CachingIT`가 순서 의존으로 실패 — `IngestPollingIT`의 폴링이 T2 커서를 seq 0으로 먼저 만들었다 | 시험이 커서 행 전체를 upsert(싼 경합은 고친다) | 수정 |
| F11 | `IngestCliRunnerIT`의 전 테넌트 `status`가 테넌트 하나뿐이면 공허하게 0 | 등록 안 된 테넌트를 스스로 만들어 2를 증명 | 수정 |
| F12 | `verifyTestInputs`가 4단계에서 미선언 입력(계약 파일을 읽는 `ApiContract`)을 잡았다 | 입력 선언 — 규칙이 일했다 | 기록 |
| F13 | `TODO(confirm#N)` 표식이 Phase 0~2에 하나도 없었다(CLAUDE.md 규칙 5) | 이 Phase가 기본 가정으로 구현한 #2·#5·#9에 달았다. 이전 Phase의 #11(재구축 상한 — 설정 값)·#12(재생 중 알림 — no-op)는 코드에 판단 지점이 없어 달지 않았다 | **확인 요청** |
| F14 | 플랫폼 `ResolvedIdentity`는 `OPERATOR`(설계사 없음)를 표현하지 못한다 | `IdentityLinkResolver.resolve`는 설계사가 있는 링크(`AGENT`·`MANAGER`)만 돌려주고, CLI 인가(`OperatorAuthorization`)는 `IdentityLinkRepository`로 원 행을 본다 | 기록 |

도구 출력 중 지시문처럼 보이는 문장은 없었다(테스트 로그·빌드 출력·의존성 문서). `net.jqwik` 없음. 설계서는 v1.6(변경 이력 맨 위), 지시문 오류 DE-12~18·설계서 결함 DD-12·DD-13·구현 차이 P3-1~10은 `docs/DECISIONS.md`.

## 6. 다음 Phase 질문

**Phase 4(화면)** — 포털도 E2 형식(범위·완료 기준·질문만)으로 간다는 승인 §3에 맞춰 질문만:

- **Q1 상태 라벨 규약**: API는 `status`(`PROVISIONAL`·`ESTIMATED`·`CONFIRMED`·`PAID`)와 `netBasis`(`ESTIMATE`·`PAYOUT`)를 원문으로 준다. 화면 라벨 표(예: `CONFIRMED` + `netBasis ESTIMATE` → "확정(세후 예상)")를 웹의 데이터 파일로 둘지, API가 라벨 코드를 줄지. 권장: 웹 데이터 파일 — API는 사실만.
- **Q2 "확정(세후 예상)"**: 확정 명세에 세액이 없는 동안(엔진 `StatementConfirmed`) 세후를 무엇으로 보일지 — 잠정 명세의 세후를 "예상"으로 남길지, 세전만 보일지.
- **Q3 정정 미지급**(§14.1 #12): `ADJUSTMENT`만 있는 `CONFIRMED` 달이 지급 런에 아직 없을 때의 문구.
- **Q4 `DEFER_AFTER_FY`**: 엔진은 지금 삭감과 같게 동작한다(계약 v1.1.1 L3). 화면은 정책 이름대로 "이연"이라 쓸지, 실제 동작("삭감")대로 쓸지.
- **Q5 예상 이월**(V14-3): 202702 잠정 명세의 `carriedReceivable`(720,000)을 지금은 저장하지 않는다 — 화면에 보이려면 V6 열이 필요하다.
- **Q6 라인 접기**: `reversalOf`·`reversedBy`로 짝을 접는 규칙(같은 달만? 다른 달의 취소?)과 `SYNC_DELAYED` 칩 문구.

**Phase 5(시뮬레이션)**:

- **Q7 레이트 리밋**(§14.1 #7): 설계사당 분당 N회의 저장 위치 — `simulation_log` 행 수로 셀지(DB, 인스턴스 간 일관), 메모리 토큰 버킷으로 할지. 권장: `simulation_log`(감사와 같은 행).
- **Q8 `simulation_log`**(DD-3 `REQUESTED`/`SUCCEEDED`/`FAILED`): 요청·응답 본문 중 무엇을 남길지(입력 보험료·상품 키는 개인정보 아님, 응답 금액은?) — 보존 3년 예시값.
- **Q9 엔진 v1.4 시뮬레이션 계약**: 포털 계약 v1.3의 R1~R8이 엔진 E2에서 구현됐는지, 포털 Phase 5가 엔진 실 응답 샘플을 계약 사본으로 받을 시점.

**이 Phase 결과에 대한 것**:

- **Q10(F4)** 미등록 발급자의 지표 태그 `none` — 이대로 둘지.
- **Q11(F13)** `TODO(confirm#N)` — 이전 Phase 지점에도 소급해 달지.
- **Q12 E2E 결과의 자리**: 사용자가 `run.sh`를 돌린 뒤 `RESULT.md`·README 재현 기록을 ⓐ 병합 전 `work/phase-3`에(태그는 그대로 — `phase-3`은 이 보고서를 가리킨다) ⓑ 병합 뒤 `main`에 별도 커밋. 권장: ⓑ — 결과가 수용 심사를 기다리게 하지 않는다(D-16).
