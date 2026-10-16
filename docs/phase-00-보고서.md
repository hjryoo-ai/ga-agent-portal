# Phase 0 보고서 — ga-agent-portal 골격 (지시문 v1.2)

> 지시문 `docs/phase-00-지시문.md`(v1.2) · 계획 `docs/phase-00-계획.md` · 승인 `docs/phase-00-계획승인.md`(2026-10-16, Q1~Q10 권장안) ·
> 브랜치 `work/phase-0` · PR [#1](https://github.com/hjryoo-ai/ga-agent-portal/pull/1) · 태그 `phase-0`(이 보고서 1판 커밋 `28654b6`). **병합은 수용 심사 회신 뒤.**
>
> **2판(태그 `phase-0.1`)** — 수용 심사 `docs/phase-00-수용심사.md`(2026-10-16, 수용 · 조건 K7)의 조건과 보강을 반영했다: §3 주입 표에 K7·A9, §5 F1·F10, 새 §7. 1판의 나머지 본문은 그대로다.

## 1. 태그·커밋·변경 파일

`main` = `4b56b6e`(설계서 v1.1·지시문 v1.2·ga-disclosure 인계 사본 — 받은 원문 그대로). 이 Phase의 커밋(`main..phase-0`):

| 커밋 | 내용 | 주입 |
|---|---|---|
| `76951a3` | 계획 | — |
| `fc76bb5` | 승인 보관 + 계획 갱신(C9 PAT 비밀, D1~D3, 주입 27) + `docs/DECISIONS.md`(DE-1~4, DD-1~2) | — |
| `426c607` | 1단계 빌드 골격 — Gradle 9.8.1, 모듈 9, 카탈로그·락·검증 메타데이터, 플랫폼 0.2.0 해석·고정, CI build 잡, README·LICENSE·CLAUDE.md | J1·V1·V2 |
| `9096fc5` | 검증 메타데이터를 빈 Gradle 홈에서 재생성(첫 CI가 BOM `.module` 5개 누락으로 실패 — §5 F2) | — |
| `fc30e3f` | 2단계 도메인 — 포털 식별자 4·`CommType`·상태 열거 4 + 플랫폼 `Won`·`Ym`·`Ratio` 재증명 | — |
| `53576fc` | 3단계 스키마·RLS·롤·저장소 2·하네스·C1~C4·C10·Q9, CI no-docker 잡 | R1~R4·Q9-1 |
| `4614c65` | 4단계 계약 — 공유 핵심 사본·UPSTREAM·포털 Envelope·payload 13·샘플 17·OpenAPI·`EventSchemaTest` | K1~K6 |
| `237e09f` | 5단계 아키텍처 (a)~(h)·SQL 스캔(D1~D3) | A1~A8·S1~S4 |
| `33263a8` | 6단계 평문 스캔 골격 | P1 |
| (이 커밋) | 보고서 | — |

변경 파일 트리(요약):

```
.github/workflows/ci.yml            build(Docker, C9 해석 증거, jqwik 0, build, verifyUpstreamContract, 보고서·결과 XML 업로드) · no-docker(실패해야 성공)
build.gradle.kts                    jqwik 차단·락·verifySpringFree·verifyNoSnapshots·verifyPlatformPins·printPlatformArtifacts·contract 3태스크·scanPlaintextLeaks·verifyTestInputs
settings.gradle.kts                 com.ga.platform exclusiveContent — CI GitHub Packages(GHP_USER·GHP_READ_TOKEN, 없으면 즉시 실패) / 로컬 mavenLocal
gradle/libs.versions.toml · gradle/verification-metadata.xml(전 의존성 SHA-256) · *.lockfile · gradle/wrapper(9.8.1, 배포본 SHA-256)
CLAUDE.md · README.md · LICENSE(MIT) · web/README.md · .gitattributes(contracts/shared/** -text)
docker/postgres/init-roles.sql      portal_migrator · portal_app (정본)
contracts/
  shared/ga-disclosure/             events/v1/envelope-core.schema.json(db82d5d 바이트 사본) · CHECKSUMS · UPSTREAM
  events/v1/                        envelope · defs · payloads 13 · samples 17
  api/v1/engine-internal.openapi.yaml · CHECKSUMS
portal-domain        CalcId·PolicyNo·LedgerId·ScheduleId·CommType·MonthStatus·DataStatus·DeferralStatus·IngestState (+ 시험 4)
portal-projection    (코드 없음) EventSchemaTest
portal-infra         V1__init.sql·V2__rls.sql · TenantRepository·IngestCursorRepository(+ 레코드 2) · 픽스처 PostgresHarness·SeedData·RecordingDataSource·pii-sentinels
                     시험: TenantContextGuardTest·RlsSettingNameTest·StatusEnumDdlTest / IT: RlsIsolationIT·TenantSessionBinderTest·TenantRepositoryIT·IngestCursorRepositoryIT
portal-app           PortalApplication·application.yaml · IT PortalApplicationIT · archTest ArchitectureRulesTest·TenantPredicateScanTest·SqlTenantScanner
portal-ingest·query·sim·recon·demo   package-info만
docs/                설계서.md(v1.2) · DECISIONS.md · phase-00-계획.md · phase-00-계획승인.md · phase-00-보고서.md
```

## 2. 테스트 (CI 출처)

CI run [38892671446](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/38892671446)(`33263a8` — 마지막 코드 커밋, 잡 build·no-docker 모두 success). 수는 그 run의 `test-reports` 아티팩트(결과 XML 15개)에서 셌고, 로컬 `build`·`TZ=UTC check`(2,266)와 같다. 보고서 커밋의 run은 PR 코멘트에 적는다.

| 모듈 | 소스셋 | 건수 | 비고 |
|---|---|---:|---|
| `portal-domain` | test | 2,021 | 속성 시험(SeededCases) 대부분 — `Won` 500 + 넘침 500, `Ym` 500, 식별자 500 |
| `portal-projection` | test | 74 | `EventSchemaTest` |
| `portal-infra` | test | 9 | C1·C10·Q9 (Docker 불요) |
| `portal-infra` | integrationTest | 113 | Testcontainers PostgreSQL 18.6(digest) — C2·C3·C4·B3·저장소 |
| `portal-app` | integrationTest | 3 | 부팅 스모크 |
| `portal-app` | archTest | 46 | C5·C6 |
| **합계** | | **2,266** | 실패 0, 스킵 0 |

- 단위 2,104 · 통합 116 · 아키텍처 46.
- `no-docker` 잡(같은 run): "gradle exit code: 1", "result files: 4, with failures/errors: 4, skipped: 0", "OK: integration tests failed (not skipped) because Docker is missing".
- 같은 run의 빌드 검사: `printPlatformArtifacts: 13 files equal to the handoff §6 table, source=remote (Gradle cache)`, `verifyPlatformPins: 13 …`, `scanPlaintextLeaks: 15 files, 2 forbidden strings, 0 hits`, `verifyUpstreamContract` "envelope-core.schema.json == ***/ga-disclosure@db82d5d…".
- 단계별 CI(전부 pull_request, 잡 build·no-docker): `426c607` [38889028147](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/38889028147) **실패**(§5 F2) → `9096fc5` [38889363908](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/38889363908)(C9 첫 확인) → `fc30e3f` [38889578264](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/38889578264) → `53576fc` [38890934613](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/38890934613) → `4614c65` [38891831296](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/38891831296) → `237e09f` [38892501732](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/38892501732) → `33263a8` [38892671446](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/38892671446) — 첫 run 말고 전부 성공.

## 3. 완료 기준 증거

| # | 기준 | 증거(클래스 › 메서드) | 주입 |
|---|---|---|---|
| C1 | 미바인딩 저장소 호출은 DB 도달 전 예외 | `portal-infra` `TenantContextGuardTest` › `everyRepositoryMethodFailsBeforeReachingTheDatabaseWhenUnbound`(두 저장소 공개 메서드 전부 `TenantNotBoundException`, 연결 획득 0 — 닿으면 `AssertionError`를 던지는 가짜 데이터소스), `anUnboundTransactionCannotEvenStart`, `theGuardedMethodsAreAllPublicMethodsOfBothRepositories`(공개 메서드 목록 고정) | 해당 없음 — 플랫폼 보장(jar는 SHA-256 고정, 계획 ⑦) |
| C2 | 미설정 0행, 지정 시 그 테넌트만(`tenant` 포함) | `RlsIsolationIT` › `unsetTenantSeesZeroRows`·`emptyTenantSettingSeesZeroRows`·`boundTenantSeesOnlyItsOwnRows`(17개 테이블 × 두 테넌트 — 목록은 DB 카탈로그, 개수 17 고정·시드 목록과 대조), `everyTenantTableHasForcedRlsAndTheTenantPolicy`, `policiesAreExactlyTenantIsolation`. B3: `cannotWriteRowsOfAnotherTenant`·`cannotMoveOwnRowToAnotherTenant`(42501) | R1·R2 |
| C3 | `portal_app` RLS 우회 불가, 모든 롤 `NOBYPASSRLS` | `RlsIsolationIT` › `appRoleCannotBypassOrDisableProtections`(13문장 — `SET ROLE portal_migrator`·`DISABLE/NO FORCE ROW LEVEL SECURITY`·`DROP POLICY`·`ALTER ROLE … BYPASSRLS`·`TRUNCATE`·`CREATE TABLE`·`flyway_schema_history` 읽기 등, 전부 SQLSTATE 42501), `rowSecurityOffIsRejectedWhenPoliciesApply`, `everyPortalRoleIsNoBypassRlsAndTheAppRoleHasNoExtraAttributes`, `migrationAssertionRejectsABypassRole`(V2 단언 블록이 BYPASSRLS 롤을 실제로 잡음) | R3 |
| C4 | 트랜잭션 밖 거부, 컨텍스트 없는 트랜잭션은 SET을 건너뛰지 않고 실패 | `TenantSessionBinderTest`(integrationTest — 실제 PostgreSQL `portal_app`을 기록기로 감쌈) › `repositoryCallOutsideATransactionIsRejectedBeforeTheDatabase`, `transactionNotStartedByTheBinderIsRejected`, `transactionWithoutContextFailsToStartInsteadOfSkippingTheSet`, `theFirstStatementOfEveryTransactionSetsTheTenantAndTheSettingIsTransactionLocal`(첫 문장 = `SELECT set_config('app.tenant_id', ?, true)`, 트랜잭션 뒤 새 세션엔 값 없음) | 해당 없음(C1과 같은 이유) |
| C5 | 의존 방향·Spring 무의존·`double`/`float`/`BigDecimal`·저장소 상속·플랫폼 이름 중복·`platform-spring` 범위 | `portal-app` archTest `ArchitectureRulesTest` › `layeredModuleDependencies`(a), `springAndDbFreeModules`(b), `noDoubleOrFloat`(c), `dbAccessOnlyViaTenantScopedRepository`(d), `platformSpringOnlyInInfraAndApp`(e), `noPortalClassSharesASimpleNameWithAPlatformCoreType`(f), `connectionLevelJdbcTypesOnlyInPlatformClasses`(g), `noBigDecimalInAnyPortalModule`(h), `allowlistsHaveNoStaleEntries`, `importsEveryModuleAndThePlatform` | A1~A8 |
| C6 | SQL 스캔이 `tenant_id` 없는 문장을 잡고 허용 목록 폐기 항목이 실패 | `TenantPredicateScanTest` › `everyTenantTableAccessHasTenantPredicate`, `allowlistEntriesHaveReasonsAndAreUsed`, `scanCoversMigrationsAndRepositories`(V1·V2, 17 테이블, 자바 SQL 3), 스캐너 자체 시험 32건 | S1~S4 |
| C7 | 샘플 13종 통과·필수 필드 제거 실패, 사본 바이트 동일(`UPSTREAM` = 병합 커밋), 포털 Envelope ⊇ 핵심, `format` 0, 계약 디렉터리 입력 선언 | `portal-projection` `EventSchemaTest`(74건) › `everySamplePassesTheEnvelopeTheCoreAndItsPayloadSchema`, `samplesCoverThe13TypesInSeqOrderOfAppendixA1`, `removingAnyRequiredFieldFails`, `everythingTheCoreRejectsThePortalEnvelopeRejects`(12 변형), `thePortalEnvelopeIncludesTheCoreCopyAsItsFirstAllOf`, `aPayloadOfAnotherTypeIsRejected`, `aggregateKindMustBeTheOnePairedWithTheType`, `unknownTypeOrVersionIsRejectedEvenWhenTheCoreAcceptsIt`, `statementAndPayoutAggregateIdsAreAgentColonYm`, `unknownFieldsAreAllowedInTheEnvelopeAndThePayload`, `ratesAndMultiplesAreDecimalStringsNotNumbers`, `amountIsDocumentedAsPaidAfterTheLimitCut`, `noContractFileHasAFormatKeyword`(34개 파일), `feedUsesIntegerCursorsAndAFeedOfSamplesIsValid`. 빌드: `verifyContractChecksums`(check), `verifyUpstreamContract`(CI — "envelope-core.schema.json == …/ga-disclosure@db82d5d…"), `verifyTestInputs` | K1~K6 |
| C8 | 클린 체크아웃 + Docker `./gradlew build`, `no-docker` 실패로 성공, `TZ=UTC` check, jqwik 0 | §2 CI run의 build·no-docker 잡(no-docker: "gradle exit code: 1 … result files: 4, with failures/errors: 4, skipped: 0 … OK"), build 잡 "net.jqwik must not appear" 단계, 로컬 `TZ=UTC ./gradlew check --rerun-tasks --no-daemon`(호스트 KST) 성공 2,266건 | J1 |
| C9 | 플랫폼 0.2.0이 **발행본**에서 해석(CI에 mavenLocal 없음), jar SHA-256 = 검증 메타데이터, SNAPSHOT 0 | 아래 C9 표(CI 로그 1차 증거) · `gradle/verification-metadata.xml`(2차) · `verifyPlatformPins`(check — 메타데이터의 13개 = 인계 §6 표) · `verifyNoSnapshots` | V1·V2 |
| C10 | RLS 설정 이름 상수 ↔ `V2__rls.sql` | `portal-infra` `RlsSettingNameTest` › `rlsPoliciesReadThePlatformSettingName`, `everySessionSettingInTheMigrationsIsTheTenantSettingOrAKnownMarker`, `theConstantIsTheDocumentedName` | R4 |
| Q9 | 닫힌 값 CHECK = 정본 | `StatusEnumDdlTest` › `checkedColumnsAreExactlyTheApprovedTwelve`, `enumBackedColumnsMatchTheirJavaEnums`, `everyCheckedColumnMatchesTheDesign5Comment` | Q9-1 |

**C9 — CI가 GitHub Packages에서 받은 파일**(run [38889363908](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/38889363908), `printPlatformArtifacts`, 경로는 `/home/runner/.gradle/caches/modules-2/files-2.1/com.ga.platform/…` — 원격에서 받아 Gradle 캐시에 둔 것, `~/.m2` 아님. 출력: `source=remote (Gradle cache)`). 13개 전부 인계 §6 표와 같다:

| 파일 | SHA-256 (CI 로그 = 인계 §6 = 검증 메타데이터) |
|---|---|
| `platform-core-0.2.0.jar` | `981ad2635c56a39299beb40ce0883bf7e0d7d1f6c66e7fea833668378f76f35c` |
| `platform-core-0.2.0-test-fixtures.jar` | `c4f4e680b61bc9fd97c93c398d00c838c82199ebe0d64a4847b59e2539966355` |
| `platform-core-0.2.0-sources.jar` | `d7a9ad154272bfb4a7eaac040c3a6b2b7b88cc4a0e9d2091d4945f9c9a152b79` |
| `platform-core-0.2.0.pom` | `11931c99270da6436b9957103226e93ec33c78703d62496fb449a00ce6123d89` |
| `platform-core-0.2.0.module` | `1755e752029dad33ce8430d379ef467b24e61c4526278f65c9965b4b54a4434d` |
| `platform-canonical-0.2.0.jar` | `14856a61d9e311d6b9f229a232a73bde58ee425d2ff5796b244dc404a7632ca0` |
| `platform-canonical-0.2.0-sources.jar` | `f31dc36c3fa5bcccacd41dee5bb8b1cd3fa4d047fb9a483715f63929871e851a` |
| `platform-canonical-0.2.0.pom` | `af2f15a3933409d1d3b7a674887223fb22f6743e28aef8537bd091da085dc8ee` |
| `platform-canonical-0.2.0.module` | `89705f3ccee8f4fc9f7ce59685192a1dbc061d35dcd917327e674774ea882cf7` |
| `platform-spring-0.2.0.jar` | `7b45f07bb30126491861cb537335c61b5476effb767a8ef01403e98c032524e0` |
| `platform-spring-0.2.0-sources.jar` | `b01426e47bbfb3ddf46ec6f0d792a11ae1b4cf96a8054bd13349ef1f3a431b57` |
| `platform-spring-0.2.0.pom` | `2134532a1a0c12cb22a2062eb9b22eff16654bd019334ddbc220e69951888809` |
| `platform-spring-0.2.0.module` | `57fd96c20ceaa477f71743044e113722cc3c5883c0f8c9d3a426ae4766312868` |

자격 증명은 저장소 비밀 `GHP_USER`·`GHP_READ_TOKEN`(PAT, 승인 §3)이고 첫 해석에서 그대로 통했다 — 사용자 조치 없음. 로그는 `GHP_USER`의 값과 같은 문자열(저장소 소유자 이름)을 `***`로 가린다(GitHub의 비밀 가림 — `verifyUpstreamContract` 줄의 `***/ga-disclosure@…`). 로컬은 `mavenLocal`에서 같은 13개(`source=mavenLocal`, 2026-10-16 실측).

### 주입 기록 (ID 단위 29건 — 1판 27건은 승인 B1 예상과 같다. 2판에서 K7(수용 심사 §3)과 A9(심사 §2 F1의 결과 타입 닫힌 목록 — 새 규칙이라 주입을 더했다))

| ID | 대상 | 주입 | 실패한 곳 |
|---|---|---|---|
| J1 | C8 | `portal-domain` `testImplementation("net.jqwik:jqwik:1.9.3")` + 임시 시험 원천 | `compileTestJava`: "CLAUDE.md 절대 규칙 7 위반: 'net.jqwik:jqwik' 의존은 금지다 (설정 'testCompileClasspath')". 첫 시도는 시험 원천이 없어 `NO-SOURCE`로 해석하지 않아 통과했다 — 원천을 넣어 다시 |
| V1 | C9 | `-Dmaven.repo.local=<스크래치>`에 0.2.0 사본, `platform-core-0.2.0.jar` 끝 1바이트 | "Dependency verification failed … platform-core-0.2.0.jar … from repository MavenLocal". 사용자 `~/.m2`는 손대지 않음 |
| V2 | C9 | 카탈로그 `ga-platform = "0.2.0-SNAPSHOT"` | `verifyNoSnapshots` |
| R1 | C2 | 임시 V3 `DROP POLICY tenant_isolation ON earnings_line` | `RlsIsolationIT` 초기화(42501 "new row violates row-level security policy") — FORCE RLS에 정책이 없으면 시드부터 거부된다. 의도한 C2 단언보다 앞에서 걸렸다 |
| R2 | C2 | 임시 V3 `ALTER TABLE deferral_item DISABLE ROW LEVEL SECURITY` | `RlsIsolationIT` 4건(미설정 0행 기대 → 2, 빈 값 → 2, 바인딩 1행 기대 → 2, ENABLE false) |
| R3 | C3 | `init-roles.sql`의 `portal_app` → `BYPASSRLS` | 마이그레이션 "portal roles must be NOBYPASSRLS and NOSUPERUSER: portal_app", infra 통합 시험 전부 |
| R4 | C10 | V2 정책의 설정 이름 `app.tenantid` | `RlsSettingNameTest` 2건 |
| Q9-1 | Q9 | V1 `earnings_month.status` CHECK에서 `'PAID'` 제거 | `StatusEnumDdlTest` 2건(열거·설계서 주석 대조) |
| K1 | C7 | 사본 `envelope-core.schema.json` 끝 1바이트 | `verifyContractChecksums` |
| K2 | C7 | `UPSTREAM` 커밋 → `60d52599…`(PR #15 이전 main) | `verifyUpstreamContract` `FileNotFoundException` — 그 커밋에는 핵심 파일이 없어 바이트 비교가 아니라 404로 실패 |
| K7 | C7 (2판) | 사본 `envelope-core.schema.json` 끝 1바이트(`\n` → 공백) **+ `contractChecksums`로 `CHECKSUMS` 재생성** — `verifyContractChecksums`는 통과("… CHECKSUMS match") | `verifyUpstreamContract` 바이트 불일치: "…/events/v1/envelope-core.schema.json differs from hjryoo-ai/ga-disclosure@db82d5d…:contracts/events/v1/envelope-core.schema.json (copy sha256=4a69d18e…a07, upstream sha256=3c9900a5…b9c)" — 로컬 실행(네트워크). 이 메시지에 두 해시를 넣은 것도 2판 |
| K3 | C7 | `PayoutCompleted` payload `paidAt`에 `"format": "date-time"` | `EventSchemaTest.noContractFileHasAFormatKeyword` |
| K4 | C7 | 포털 Envelope `allOf`에서 핵심 항 제거 | `everythingTheCoreRejectsThePortalEnvelopeRejects`(eventId·seq·occurredAt·aggregate.id 변형 등) + `thePortalEnvelopeIncludesTheCoreCopyAsItsFirstAllOf` |
| K5 | C7 | `portal-projection` test의 `contracts` 입력 선언 제거 | `verifyTestInputs` ":portal-projection:test reads contracts" |
| K6 | C7 | `:portal-projection:test` UP-TO-DATE 두 번 → 샘플 16만 변경 | 태스크 재실행(UP-TO-DATE 아님) — 실패가 아니라 "재실행됨"이 증거(C7 문구대로) |
| A1 | C5(a) | `portal-query` → `portal-ingest` Gradle 의존 + 호출(Gradle 의존 없이는 컴파일 불가) | `layeredModuleDependencies` |
| A2 | C5(b) | `portal-domain` record에 `java.sql.Timestamp` | `springAndDbFreeModules`(+ `dbAccessOnlyVia`) |
| A3 | C5(c) | `portal-domain` `double` 필드·반환 | `noDoubleOrFloat` |
| A4 | C5(d) | `portal-infra`가 `JdbcClient` 직접 사용 | `dbAccessOnlyViaTenantScopedRepository`(+ 스캔 SQL 수 단언) |
| A5 | C5(e) | `portal-ingest`에 platform-spring 의존(락은 `--write-locks` 뒤 원복) + `TenantSessionBinder` 참조 | `platformSpringOnlyInInfraAndApp`(+ 레이어) |
| A6 | C5(f) | `portal-domain`에 `record Won` | `noPortalClassSharesASimpleNameWithAPlatformCoreType` |
| A7 | C5(g) | `portal-recon`이 `javax.sql.DataSource` 참조 | `connectionLevelJdbcTypesOnlyInPlatformClasses`(+ `dbAccessOnlyVia`) |
| A9 | C5(g) (2판) | `IngestCursorRepository`(저장소 하위 클래스)에 `java.sql.Timestamp` 인자·호출 | `outsidePlatformClassesOnlyResultLevelJdbcTypes` 2건 위반 — 47건 중 이 1건만 실패((d)·기존 (g)는 저장소 하위 클래스의 `Timestamp`를 막지 않는다 — 닫힌 목록이 잡은 것) |
| A8 | C5(h) | `portal-projection`이 `BigDecimal` 참조 | `noBigDecimalInAnyPortalModule` |
| S1 | C6 | 저장소에 `UPDATE ingest_cursor SET state = 'PAUSED'`(술어 없음) | `everyTenantTableAccessHasTenantPredicate` "without tenant_id = :tenantId predicate" |
| S2 | C6 | 허용 목록에 쓰이지 않는 항목(사유 있음) | `allowlistEntriesHaveReasonsAndAreUsed` "쓰이지 않는 허용 목록 항목" |
| S3 | C6 | 허용 목록 항목 사유 `" "` | 같은 시험 "사유 없는 허용 목록 항목" |
| S4 | C6 (D3) | `INSERT INTO ingest_event (event_id, seq, …)`(tenant_id 없음) | "INSERT INTO ingest_event does not name tenant_id column" |
| P1 | §7 평문 스캔 | `CommTypeTest`가 센티널 출력 | `scanPlaintextLeaks` "…CommTypeTest.xml: 허…" |

## 4. 플랫폼 API 대조표 (v1.0 작업 2·3·4)

정본 ga-disclosure `platform/API-0.2.0.txt`, 인계 §2. 시험 열은 포털이 그 성질을 다시 증명하는 곳.

| v1.0 항목 | 분류 | 0.2.0 | 포털 시험 |
|---|---|---|---|
| `Won`(plus·minus·negate·isNegative·ZERO·비교·of·isZero·isGreaterThan·isLessThan) | 플랫폼에서 가져옴 | `core.money.Won` | `PlatformValueTypesPropertyTest`(결합·교환·항등·역원·비교, 넘침 예외) |
| `Ratio`(소수 1자리 문자열, `Won` 생성 없음) | 플랫폼에서 | `core.money.Ratio` | `ratioIsDisplayOnlyPercentWithOneDecimal`, `TenantRepositoryIT`(bp → %) |
| `Ym`(next·prev·range·parse) | 플랫폼에서 | `core.time.Ym` | `ymNextPrevRoundTripAndParseValueRoundTrip` |
| `TenantId`(대문자·하이픈 금지) | 플랫폼에서 | `core.tenant.TenantId` | V1 형식 CHECK 같은 식 |
| `AgentId` | 플랫폼에서 | `core.tenant.AgentId` | V1 형식 CHECK 같은 식, payload `agentId` 정규식 |
| `TenantContext`(ScopedValue·current·runWith·isBound) | 플랫폼에서 | `core.tenant.TenantContext` | C1·C4 |
| 세션 바인딩 | 플랫폼에서 | `spring.jdbc.TenantSessionBinder`·`TenantSession.SETTING_NAME` | C4·C10 |
| 저장소 기반 클래스 | 플랫폼에서 | `spring.jdbc.TenantScopedRepository`·`TenantJdbcGateway`·`PlatformJdbcAutoConfiguration` | C1, `PortalApplicationIT`(유일 트랜잭션 매니저) |
| 아키텍처 도우미·시드 생성기 | 플랫폼에서 | `core.arch.ArchRules`, test-fixtures `SeededCases` | C5, 속성 시험 |
| `CalcId`·`PolicyNo`·`LedgerId`·`ScheduleId` | **포털 구현** | — | `IdentifiersTest` |
| `MonthStatus`·`DataStatus`·`DeferralStatus`·`IngestState` | **포털 구현** | — | `StatusEnumsTest`, `StatusEnumDdlTest` |
| `CommType` | **포털 구현** | — | `CommTypeTest` |
| `TenantRepository`·`IngestCursorRepository` | **포털 구현** | — | C1·C4, `TenantRepositoryIT`, `IngestCursorRepositoryIT` |

**플랫폼에 없음: 0건.** 0.2.0에 있으나 Phase 0 범위 밖: `OrgPath`, `TenantDirectoryReader`, `IdentityResolver`, `platform-canonical`(의존 자체를 아직 두지 않았다 — payload 정규 해시는 Phase 1).

## 5. 설계서와 다르게 한 것·해석·발견

설계서는 v1.2로 고쳤다(변경 이력 맨 위 — S1~S13, Q1·Q2·Q3·Q4·Q5·Q9, I1을 각 단계 커밋에서). 아래는 그 밖에 판단이 들어간 곳이다.

| # | 무엇 | 처리 | 표시 |
|---|---|---|---|
| F1 | (g)를 글자 그대로("`java.sql`·`javax.sql` 직접 참조는 플랫폼 클래스와 허용 목록만") 쓰면 `RowMapper`의 `ResultSet` 때문에 포털 저장소(`TenantScopedRepository` 하위 클래스)가 위반이 된다 — 플랫폼 API가 `RowMapper`를 요구한다 | 연결·문장 수준 타입(`DataSource`·`DriverManager`·`Connection`·`Statement`·`PreparedStatement`·`CallableStatement`)은 플랫폼 클래스·허용 목록만(g), `ResultSet` 같은 결과 타입은 (d)가 저장소 하위 클래스까지 허용 — v1.0 (d)·ga-disclosure `dbAccessOnlyVia`와 같은 경계. `IngestCursorRepository`는 `Timestamp` 대신 `OffsetDateTime`을 써서 `java.sql`을 `ResultSet` 하나로 줄였다 | **[넓힘]** (g) 글자 대비 — 확인 요청(§6 Q1). **2판: 수용**(심사 §2) — 결과 타입을 닫힌 목록 `RESULT_LEVEL_JDBC` = {`ResultSet`, `SQLException`}으로 규칙 안에 두었다(A9). 지시문 문구 오류는 DE-5 |
| F2 | 첫 CI(run 38889028147)가 설정 단계에서 실패: Spring Boot 플러그인 해석의 BOM `.module` 5개(junit-bom 4판·opentelemetry-bom)가 검증 메타데이터에 없었다. 로컬 Gradle 캐시에 이미 있던 파일은 `--write-verification-metadata`가 기록하지 않는다 | 메타데이터를 빈 `GRADLE_USER_HOME`에서 다시 만들고(`9096fc5`) 두 번째 빈 홈에서 build 통과를 확인. 이후 의존성을 더할 때마다 같은 방식(빈 홈)으로 갱신 | 발견 |
| F3 | Gradle은 `.module`이 있으면 POM을 받지 않는다 — 처음 생성한 메타데이터에 POM 3개가 없어 `verifyPlatformPins`가 실패했다 | `printPlatformArtifacts`가 소스 jar·POM을 명시적으로 받아 13개 전부가 검증을 거친다. 인계 §6의 "Gradle 의존성 검증은 jar뿐 아니라 POM·.module도 대조"는 POM에 대해선 맞지 않았다(받을 때만 대조) | 발견(인계 문구 정정 필요 — §6 Q5) |
| F4 | 설계서 §5에 `simulation_log.status` 값이 없다 | `REQUESTED`/`SUCCEEDED`/`FAILED`로 정하고 §5 주석·V1 CHECK에(`response`가 NULL 가능한 것과 맞춤). `notification.severity`의 주석은 §6.5 표(INFO/WARN/CRIT)에서 옮겼다 | **[빈칸]** — 확인 요청(§6 Q2) |
| F5 | 형식 CHECK를 `tenant_id`·`agent_id` 컬럼 **전부**에(S6은 컬럼을 특정하지 않았다), `ingest_cursor.last_event_id`에도 UUID CHECK | V1 | [좁힘] |
| F6 | 포털 정본 파일의 `contracts/CHECKSUMS`(shared/ 제외) | 엔진 E1이 포털 계약 사본을 들일 때 대조할 수 있게 — 계획 ③에 있던 것 | 계획대로 |
| F7 | 앱 기동 Flyway(`portal_migrator`) | ga-disclosure Phase 0과 같은 구성. ga-disclosure는 Phase 8에서 운영자 명령으로 옮겼다 — 포털도 Phase 8에서 같은 결정 | 기록 |
| F8 | `TenantSessionBinderTest`(C4)는 지시문 이름 그대로지만 `integrationTest` 소스셋에 있다 — 실제 PostgreSQL로 증명하므로 | 이름 유지(지시문 표) | 기록 |
| F9 | json-schema-validator 3.0.6(최신 3.0.8 아님) | 3.0.7·3.0.8은 Jackson 3.2.1을 요구해 Boot BOM(3.1.5)을 끌어올린다 — BOM 우선 규약 | 기록 |
| F10 | R1·K2 주입은 의도한 단언보다 앞에서 실패했다(R1: 시드 거부, K2: 404) | 실패는 확인했고 원인을 그대로 적었다(§3 표). **2판: K2가 증명하지 못한 바이트 비교 분기는 K7로 보완**(사본과 `CHECKSUMS`를 함께 바꿔 오프라인 검사를 통과시킨 뒤 원격 대조가 두 해시와 함께 실패) | 기록 |

불변식을 넓히는 항목: **F1 하나**(그것도 v1.0 원문 (d)와 같은 경계로 되돌린 것). 도구·라이브러리 출력에서 지시문처럼 보이는 문장은 없었다(불변 규약 8).

## 6. 다음 Phase(E1 엔진 아웃박스, Phase 1 인제스트)를 위한 질문

| # | 질문 | 권장 |
|---|---|---|
| Q1 | §5 F1의 (g) 경계 — 연결·문장 수준 타입만 플랫폼, 결과 타입은 저장소 하위 클래스까지 | 이대로(지시문 (g) 문구를 "연결·문장 수준"으로 고침) |
| Q2 | `simulation_log.status` = `REQUESTED/SUCCEEDED/FAILED` | 이대로(Phase 5에서 바꾸려면 새 V) |
| Q3 | 엔진 E1의 계약 사본: 엔진은 지금 `contracts/` 전체를 ga-disclosure 한 곳과 대조한다(`UPSTREAM` 한 줄). 포털 이벤트 계약(`events/v1`·`api/v1/engine-internal`)이 들어가면 업스트림이 둘이 된다 | 엔진도 포털처럼 `contracts/shared/<업스트림>/`별 디렉터리·`UPSTREAM`으로 나눈다(포털 H4와 같은 구조). E1 지시문의 전제로 |
| Q4 | Phase 1의 payload 정규 해시(JCS)를 어디에 두나 — `ingest_event`에는 해시 컬럼이 없다(설계서 §5). "같은 eventId, 다른 payload"를 독 이벤트로 잡으려면 해시를 저장해야 한다 | Phase 1에서 V3로 `ingest_event.payload_hash`(설계서 §5 개정과 같은 커밋) |
| Q5 | 인계 문서 §6의 "Gradle 의존성 검증은 … POM·.module도 대조"는 POM에 대해선 받을 때만 맞다(§5 F3) | ga-disclosure 인계에 정정 1줄(표기 변경 — 다음 ga-disclosure 작업에 묶음) |
| Q6 | (받은 결정 그대로) 역할은 `identity_link`에서만, 토큰 `roles` 불신 — 설계서 §9 "토큰 클레임: … roles"는 Phase 3에서 고친다 | Phase 3 |
| Q7 | E1 전제로 넘길 것(승인 §7): Q3 요율 문자열·Q4 `kind` 9종과 `id` 규약·Q5 `if/then`·I1 `amount` = 삭감 뒤·핵심 사본 규약·`format` 0·E3.2 규약 | 포털 `contracts/` = `phase-0` 태그 커밋을 E1의 포털 업스트림으로 |

## 7. 수용 조건 반영 (2판 — 태그 `phase-0.1`)

수용 심사(`docs/phase-00-수용심사.md`)의 조건 K7과 같은 커밋으로 낼 것들. `phase-0`(`28654b6`)은 옮기지 않았다.

| 심사 | 한 것 | 증거 |
|---|---|---|
| §3 K7 | `verifyUpstreamContract`의 바이트 비교 분기가 실패함을 주입으로 확인. 실패 메시지에 사본·업스트림 두 SHA-256을 넣었다(`build.gradle.kts`) | §3 표 K7 |
| §2 F1 | (g) 결과 타입 닫힌 목록 `ArchitectureRulesTest.RESULT_LEVEL_JDBC` = {`java.sql.ResultSet`, `java.sql.SQLException`} — 플랫폼 클래스·허용 목록 밖의 `java.sql`·`javax.sql` 참조는 이 둘뿐(`outsidePlatformClassesOnlyResultLevelJdbcTypes`). 폐기 검사는 "쓰이는가"가 아니라 **"플랫폼 `RowMapper.mapRow` 시그니처의 `java.sql` 타입과 같은가"**로 했다 — 포털 저장소는 람다로 `RowMapper`를 쓰고 `throws SQLException`을 적지 않아 지금 `SQLException` 참조가 0이다. 그래도 그 항목은 심사가 정한 목록이고 플랫폼 API가 근거다. 규칙이 공허하지 않음은 "포털 저장소가 `ResultSet`을 읽는다"로 함께 단언 | `allowlistsHaveNoStaleEntries`, §3 표 A9 |
| §2 F2 | CLAUDE.md 코드 규약: 메타데이터 재생성은 **빈 `GRADLE_USER_HOME`에서** | CLAUDE.md |
| §5 | DECISIONS: DE-5((g) 문구), DE-6(Q8 승인의 빈 홈 조건 누락), DD-3(`simulation_log.status` 값 집합) | `docs/DECISIONS.md` |
| §2 F4 | 설계서 §5 스키마 블록 아래 "상태 컬럼" 한 줄: `simulation_log.status` 실패 사유는 값을 늘리지 않고 `error` 컬럼을 새 V로(스키마 블록 주석은 `StatusEnumDdlTest`가 읽으므로 그 밖에) | `docs/설계서.md` §5 |
| §4 B1 | 설계서 §5 RLS 문단: 데이터 마이그레이션은 테넌트를 열거하며 테넌트마다 설정한 뒤 실행 | `docs/설계서.md` §5, 변경 이력 v1.2 |
| §4 B2 | `no-docker` 잡: 아티팩트 업로드 전 `it.log`·결과 XML에서 `GHP_READ_TOKEN` 값 0건 확인, 1건 이상이면 올리지 않고 실패. **같은 검사를 `build` 잡의 `test-reports` 아티팩트에도 두었다**(같은 이유 — 아티팩트는 비밀 가림을 거치지 않는다. 심사는 `no-docker`만 적었다) | `.github/workflows/ci.yml`, 이 커밋의 CI run(PR 코멘트) |

테스트 수는 `archTest`가 46 → 47(새 규칙 1)이라 합계 2,267이 된다(로컬). CI 출처의 수는 이 커밋의 run과 함께 PR 코멘트에 적는다.

심사 §2 F3(인계 문구 정정)은 ga-disclosure의 다음 작업에 묶는다 — 포털의 `docs/handoff/` 사본은 받은 원문 그대로 둔다.
