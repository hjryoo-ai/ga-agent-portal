# Phase 0 계획 — ga-agent-portal 골격 (지시문 v1.2)

> 지시문 `docs/phase-00-지시문.md`(v1.2, 2026-10-14). 정본 설계서 `docs/설계서.md`(v1.1, 2026-09-22). 인계 `docs/handoff/ga-disclosure-portal-phase0.md`
> (ga-disclosure `docs/handoff/portal-phase0.md`의 바이트 동일 사본, ga-disclosure `main` = `552920c`).
> `main` = `4b56b6e`(위 세 문서). 브랜치 `work/phase-0`. **승인 전에는 구현하지 않는다.**
>
> 전제 충족: 플랫폼 0.2.0 정비 PR(ga-disclosure #15)이 병합(`db82d5d`)·태그 `platform-v0.2.0`·발행(run 38552151950)됐고 인계 PR #14가 병합됐다(`552920c`).
> 이 머신의 `~/.m2/repository/com/ga/platform/*/0.2.0` 13개 파일의 SHA-256은 인계 §6 표와 전부 같다(2026-10-16 실측).
>
> 두 문서가 다르면 **v1.2 지시문이 우선**한다(설계서 v1.1은 ga-disclosure 이전 문서다). 그 차이는 ①에 **[설계서 반영]**으로 모았다.
> 표시: **[설계서 반영]** 지시문대로 설계서를 고칠 곳 · **[빈칸]** 설계서·지시문이 정하지 않아 정해야 하는 것 · **[계약 추가]** 엔진 E1이 따라야 할 계약 결정 ·
> **[넓힘]** 불변식·허용 범위를 넓히는 것(이 계획에는 없다) · **[좁힘]** 지시문보다 엄격하게 하는 것.

## 승인 반영 (2026-10-16, `docs/phase-00-계획승인.md`) — 아래 본문보다 우선한다

Q1~Q10 전부 권장안(조건 포함), I1~I3·S1~S13 승인. 재승인 불요.

| # | 결정·조건 | 이 계획에서 바뀌는 것 |
|---|---|---|
| Q1 | `UNIQUE (tenant_id, agent_id, dedup_key)` | 설계서 §5 DDL과 §6.5 머리말("`dedup_key`는 설계사 안에서 유일")을 같은 커밋에. `dedup_key` 문자열은 그대로 |
| Q2 | `BigDecimal` 전 모듈 금지 | 변환 `round(x * 100)::bigint`는 `TenantRepository`의 SQL 한 곳에만. 시험: `80.00` → `Ratio(8000, 10000)` → `"80.0%"`, `95.50` → `"95.5%"`. `limit_multiple`은 설계서 §5 주석에 같은 규약만 |
| Q3 | 소수 문자열 `^(0|[1-9][0-9]*)(\.[0-9]{1,6})?$`, `description` "표시 전용 — 소비자는 산술에 쓰지 않는다" | `basis.rate`·`basis.payoutRate`·`limitMultiple` 세 곳. E1 전제 |
| Q4 | 9종 + 짝 강제 | `STATEMENT`·`PAYOUT`의 `aggregate.id`를 `^[A-Za-z0-9][A-Za-z0-9._-]{0,63}:[0-9]{4}(0[1-9]|1[0-2])$`로 좁힌다(포털 Envelope `if/then` 안 — 사본은 손대지 않음) |
| Q5 | `type`(+`version`)별 `if/then` | 마지막에 `else: false` 구실을 하는 가드 — 열거 밖 `type`은 payload 검사 없이 통과하지 못한다(열거와 이중) |
| Q6 | A-1 seq 1~13 + 4종 | `LimitLedgerRebased` 샘플은 A-3(`newLimit < accum`) |
| Q7 | v1.0 §6 원문 = 승인 문서 §4. ⑤ 추정대로 + 차이 3건 | **D1** SQL 스캔 대상 = `main` 문자열 SQL **+ 마이그레이션 `.sql`의 DML**(DDL 제외). **D2** `tenant` 제외하지 않음 — 제외는 `flyway_schema_history`뿐. **D3** INSERT는 컬럼 목록에 `tenant_id` + 그 값이 `:tenantId`, 나머지는 `tenant_id = :tenantId` 술어 — 주입 S1(술어 없는 UPDATE)·**S4**(`tenant_id` 없는 INSERT) |
| Q8 | 전 의존성 SHA-256, 신뢰 예외 0(플러그인·BOM 포함) | 메타데이터의 `com.ga.platform` 13개 항목을 인계 §6 표와 **스크립트로** 대조, 결과를 1단계 커밋 본문에 |
| Q9 | `CHECK (… IN …)`(`comm_type` 제외) | 시험 `StatusEnumDdlTest`: Java 열거 값 집합 = V1 `CHECK` 목록. 열거가 없는 컬럼은 DDL이 정본이고 설계서 §5 주석과 대조. 주입 **Q9-1**(열거 값 하나를 `CHECK`에서 뺌 → 실패) |
| Q10 | 각 단계 코드와 같은 커밋 | 1단계 커밋에서 설계서 제목·변경 이력(v1.2, 2026-10-16, "지시문 v1.2 반영 — S1~S13, Q1·Q9")만 먼저, 본문은 단계마다. 변경 이력에 Q1 = "설계서 v1.0·v1.1 결함(모순)", I1 = "표에 뜻이 없었음"(B4) |
| I1 | `amount` = 한도 삭감 뒤 지급분, 요청액 = `amount + limitCutAmt` | 스키마 `description`과 설계서 §4.2 표에 같은 문장 |
| S7 | DB 헬스 비활성 | 설계서 §9 롤 표에 "`portal_health` — Phase 8, 전용 롤" 자리 예약 |
| S11 | JUnit 6 | Boot 4.1.1 BOM 관리판, 카탈로그에 버전 없음 |
| B3 | `WITH CHECK` | `RlsIsolationIT`에 다른 테넌트 행 insert가 정책 위반으로 실패하는 시험 1건(주입 아님) |
| B2 | C9 1차 증거 | CI 해석 경로(Gradle 캐시) + `printPlatformArtifacts` 해시 13개 = 인계 §6 표 — 보고서에 표로. 2차는 검증 메타데이터 |

**C9 자격 증명 정정(승인 §3)** — 계획 ⑥의 "`GITHUB_ACTOR`·`GITHUB_TOKEN` + `permissions: packages: read`"와 표지의 "Manage Actions access"는 Maven 레지스트리에 맞지 않는다
(Maven 레지스트리는 저장소 스코프 — 다른 저장소의 `GITHUB_TOKEN`은 읽지 못하고, 세분 권한 화면은 컨테이너·npm용). 사용자가 PAT(classic, `read:packages`)를
`ga-agent-portal` 저장소 비밀 **`GHP_USER`·`GHP_READ_TOKEN`**으로 등록했다(2026-10-16 10:02Z, `gh secret list`로 이름 확인).
- CI 저장소 자격은 환경변수 `GHP_USER`·`GHP_READ_TOKEN`(워크플로 `env:`에 비밀을 연결). 둘 중 하나라도 없거나 비면 **설정 단계에서 즉시 실패**("자격 증명 없음" — 401까지 가지 않음). 로컬은 `mavenLocal()`만이라 PAT 불요.
- 저장소 어느 파일에도 토큰·사용자명 값을 쓰지 않는다(README에는 비밀 이름만). `printPlatformArtifacts`는 경로·해시만. 포크 PR에는 비밀이 전달되지 않아 C9가 실패하는 것이 정상 — CLAUDE.md 한 줄.
- 첫 CI가 실패하면 보고할 사용자 조치는 PAT 범위(`read:packages`; `ga-disclosure`가 비공개면 `repo` — 2026-10-16 현재 공개)와 비밀 이름 철자.

**주입 수**: 25 + S4 + Q9-1 = **27건 예상**(ID 단위).

**지시문 오류**(`docs/DECISIONS.md`에, 승인 §6): v1.2 §5 "payload oneOf 13"(Q5), v1.2 §5 "부록 A-1 seq 1~9"(Q6), 0.2 정비 지시문·v1.2 C9의 "패키지 접근 설정"(§3), v1.0 §6 원문 미수록(Q7).

## 0. 먼저 결정이 필요한 것 (요약)

| # | 무엇 | 권장 | 표시 |
|---|---|---|---|
| Q1 | `notification`의 `UNIQUE (tenant_id, dedup_key)` — §6.5의 `STMT_CONFIRMED:{ym}`·`PAYOUT:{ym}`에 설계사가 없어 같은 테넌트 두 번째 설계사의 알림이 충돌한다. V1은 커밋 뒤 못 고친다 | `UNIQUE (tenant_id, agent_id, dedup_key)` | [빈칸] 설계서 모순 |
| Q2 | `NUMERIC` 세 컬럼(`tenant.limit_warn_pct`·`limit_crit_pct`, `policy_limit.limit_multiple`)과 `BigDecimal` 금지(h) | DDL은 설계서대로. 포털은 `BigDecimal`을 **전 모듈** 금지하고 `NUMERIC`은 SQL에서 정수로 읽는다(`round(limit_warn_pct * 100)::bigint` → `Ratio(bp, 10000)`) | [좁힘] |
| Q3 | 돈이 아닌 소수(`basis.rate`·`basis.payoutRate`·`LimitLedgerOpened.limitMultiple`)의 계약 타입 | **문자열 + 소수 정규식**(불투명, 표시 전용 — ga-disclosure `ratioToAvg`와 같은 이유). JSON `number`면 어느 모듈이든 파싱 순간 `double`·`BigDecimal`이 생긴다 | [계약 추가] |
| Q4 | `aggregate.kind` 열거 — 설계서에는 `COMM_CALC` 예시 하나뿐 | 9종(③ 표), `type` ↔ `kind` 짝을 스키마로 강제 | [계약 추가] |
| Q5 | 포털 Envelope의 "payload oneOf 13" | `type`(+`version`)별 `if/then`으로 payload를 고른다. `oneOf`는 모르는 필드 허용(§4.5)과 함께 쓰면 비슷한 payload(`StatementEstimated`/`Confirmed`)가 둘 다 맞아 실패하고, `type`과 다른 payload도 통과시킨다 | 지시문 표현 해석 |
| Q6 | 계약 샘플 — 지시문 §5는 "부록 A-1 seq 1~9", C7은 "13종". A-1 1~13이 덮는 타입은 9종 | A-1 seq 1~13의 값 + 4종(`AgentSnapshot`·`AdjustmentPosted`·`LimitLedgerRebased`(A-3)·`ClawbackReceivableUpdated`)은 A-1 계약을 이어 쓴 예시값 | [빈칸] |
| Q7 | 아키텍처 규칙 (a)~(d)·SQL 스캔이 "v1.0 그대로"인데 **v1.0 지시문(2026-09-22)이 저장소·세션 어디에도 없다** | ⑤의 추정(C5 목록 + 설계서 §3.3·§9)으로 구현. v1.0 원문을 주시면 대조 | 확인 요청 |
| Q8 | 의존성 검증 메타데이터의 범위 | **전 의존성** SHA-256(신뢰 예외 0). 플랫폼만 고정하면 나머지는 검증이 없는 것과 같다 | [좁힘] |
| Q9 | 닫힌 상태 컬럼의 `CHECK (… IN …)` | 넣는다(`comm_type`은 열린 값이라 제외) — 형식 정규식과 같은 이유 | [좁힘] |
| Q10 | 설계서 개정 시점 | ①의 [설계서 반영]을 승인 뒤 **각 단계의 코드와 같은 커밋**으로 `docs/설계서.md` v1.2에 반영(지시문 불변 규약 5) | 절차 |

## ① 설계서 v1.1 ↔ 지시문 v1.2 차이 — [설계서 반영]

설계서 v1.1(2026-09-22)은 ga-disclosure Phase 0(2026-09-29) 이전 문서라 플랫폼 소비·공유 계약·작업 규약이 없다. 아래는 지시문이 우선하는 곳이고, 승인되면 그대로 설계서 v1.2가 된다.

| # | 설계서 v1.1 | 지시문 v1.2 / 인계 | 고칠 곳 |
|---|---|---|---|
| S1 | §3.3 `portal-domain` = "값객체(Won, Ym, TenantId, AgentId)… 의존성 없음" | `Won`·`Ratio`·`Ym`·`TenantId`·`AgentId`·`TenantContext`는 `platform-core` 0.2.0에서. 포털은 고유 식별자·열거형만. `domain`·`projection`은 `platform-core`만 의존 가능 | §3.3 모듈 표, 의존 방향 문단 |
| S2 | §3.3 `portal-infra` = "RLS 세션 바인딩"(포털 구현), §5 "애플리케이션은 트랜잭션마다 `SET LOCAL app.tenant_id`(데이터소스 인터셉터)" | `platform-spring`의 `TenantSessionBinder`(트랜잭션 시작 때 `SELECT set_config('app.tenant_id', ?, true)` — 트랜잭션 로컬, `SET LOCAL`과 같은 효과)·`TenantScopedRepository`·`TenantJdbcGateway`. 이름은 공개 상수 `TenantSession.SETTING_NAME` | §3.3, §5 RLS 문단, §9 격리 ② |
| S3 | §3.3·§11 영속 "Spring Data JDBC" | 저장소는 `TenantScopedRepository`(`JdbcClient` 봉투) 하위 클래스뿐. Spring Data JDBC 저장소 인터페이스는 이 경로를 우회하므로 쓰지 않는다 | §3.3, §11 |
| S4 | §5 RLS `USING (tenant_id = current_setting('app.tenant_id'))` | `current_setting('app.tenant_id', true)`(미설정 = NULL → 0행, 오류 아님) + `ENABLE` + `FORCE`, `tenant`는 자기 행만 | §5 RLS 문단 |
| S5 | §5 `tenant.params … DEFAULT '{}'`(키는 주석) | "기본 세 키": `DEFAULT '{"org_member_lines_visible": false, "forecast_scenario_enabled": false, "notification_digest": false}'` | §5 DDL |
| S6 | §5 DDL에 형식 제약 없음 | 정규식 `CHECK`: `tenant_id` `^[A-Z0-9][A-Z0-9_]{0,31}$`(플랫폼 `TenantId`), `agent_id` `^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`(플랫폼 `AgentId`), `close_ym`·`due_ym` `^[0-9]{4}(0[1-9]|1[0-2])$`(`Ym`), `event_id` UUID 정규식(인계 §3 핵심과 같은 식) | §5 DDL |
| S7 | §9에 DB 롤 없음 | 롤 표(이름 → 용도 → GRANT → 사용 컴포넌트): `portal_migrator`(소유자, 마이그레이션), `portal_app`(`NOBYPASSRLS`, DML·시퀀스 USAGE만). 정본 `docker/postgres/init-roles.sql`. 모든 롤 `NOBYPASSRLS`를 마이그레이션이 단언. DB 헬스 지표는 Phase 0 비활성(포털 Phase 8에서 전용 롤로) | §9 새 절 |
| S8 | §3.3 `contracts/` = 엔진↔포털 계약 | `contracts/shared/ga-disclosure/`(공유 핵심 사본 + 전용 `CHECKSUMS`·`UPSTREAM`) + 포털 정본(`events/v1`, `api/v1`) | §3.3, §4 머리말 |
| S9 | §4.1 Envelope는 포털이 정의 | 포털 Envelope = `allOf: [ga-disclosure envelope-core 사본, 포털 제약]`. 핵심 일곱 필드·정규식은 공유 계약이라 포털이 바꾸지 않는다(바꿀 일은 ga-disclosure 정비로) | §4.1 |
| S10 | §4.1 예시 `occurredAt` 등 `format` 언급 없음 / §11 "JSON Schema(2020-12)" | 날짜·시각·UUID는 `format`이 아니라 `pattern`, `contracts/` 전 파일 `format` 0 | §4 머리말, §11 |
| S11 | §11·§13 "JUnit 5, … jqwik", "속성 테스트(jqwik)" | jqwik 금지(Gradle 해석 단계 차단). JUnit은 Boot 4.1 BOM 관리판(JUnit 6), 속성 테스트는 `@ParameterizedTest` + `SeededCases`(`platform-core` test-fixtures) | §11, §13 |
| S12 | §13 계약 "CI가 양쪽 `contracts/` 해시 일치 검사" | `verifyContractChecksums`(오프라인, `check`)와 `verifyUpstreamContract`(네트워크, CI) — **`contracts/shared/`만**. `CHECKSUMS`는 평문 SHA-256(`sha256sum -c` 형식). JCS는 payload 정규 해시에만 | §13, §4.5 |
| S13 | §11 PostgreSQL 18.x | 이미지 digest 고정(ga-disclosure와 같은 18.6 digest에서 시작, 구현 때 최신 18.x 확인) | §11 |

**설계서 v1.1 안의 다른 것 — 고치지 않고 해석만 적는다**

| # | 무엇 | 해석 |
|---|---|---|
| I1 | §4.2 `CommissionCalculated`의 `amount`와 `limitCutAmt` | 부록 A-1 seq 3~5(한도 3,600,000, 누적 2,800,000 → 시책 800,000·삭감 200,000 → 누적 3,600,000)가 맞으려면 `amount` = **삭감 뒤 지급분**, 요청액 = `amount + limitCutAmt`. payload 스키마 `description`에 이 뜻을 적는다(엔진 E1이 같은 뜻으로 발행) |
| I2 | §5 `earnings_month.data_status`(OK/DRIFT/REBUILDING) ↔ §7 응답 `dataStatus`(OK/DRIFT/SYNC_DELAYED) | `DataStatus` 열거는 DB 값 셋. `SYNC_DELAYED`는 `ingest_cursor` 상태에서 나오는 응답 값이라 Phase 3의 응답 타입에서 다룬다 |
| I3 | 부록 A-1 seq 4·5 "WARN·CRIT 경계도 동시에 통과했으나 최상위 1건만" | Phase 6 규칙. Phase 0은 무관 — 기록만 |

## ② 플랫폼 API 대조표 (지시문 §1 — v1.0 작업 2·3·4 항목별)

정본: ga-disclosure `platform/API-0.2.0.txt`, 인계 §2. v1.0 원문이 없으므로(Q7) 항목은 v1.2 §2~§4가 열거한 v1.0 정의다.

| v1.0 항목 | 분류 | 플랫폼 0.2.0 | 비고 |
|---|---|---|---|
| `Won` — `plus`·`minus`·`negate`·`isNegative`·`ZERO`·비교 + `of`·`isZero`·`isGreaterThan`·`isLessThan` | 플랫폼에서 | `core.money.Won` — 전부 있음, `compareTo` | 포털 속성 시험(결합·교환·오버플로)으로 재증명 |
| `Ratio` — 분자·분모 long, 백분율 소수 1자리 문자열만, `Won` 생성 불가 | 플랫폼에서 | `core.money.Ratio` — `of`, `toPercentString` | |
| `Ym` — `next`·`prev`·`range`·`parse` | 플랫폼에서 | `core.time.Ym` — 전부 있음(`range(from, to)` 포함) | `next/prev` 왕복 속성 시험 |
| `TenantId` — 대문자·하이픈 금지 | 플랫폼에서 | `core.tenant.TenantId` — `[A-Z0-9][A-Z0-9_]{0,31}` | DDL `CHECK`가 같은 식(S6) |
| `AgentId` | 플랫폼에서 | `core.tenant.AgentId` — `[A-Za-z0-9][A-Za-z0-9._-]{0,63}` | DDL `CHECK`(S6) |
| `TenantContext` — `ScopedValue`, `current()` 미바인딩 예외, `runWith`, `isBound()` | 플랫폼에서 | `core.tenant.TenantContext`·`TenantNotBoundException` | 비동기 재바인딩은 플랫폼 Javadoc. 웹 필터는 Phase 3 |
| `CalcId`·`PolicyNo`·`LedgerId`·`ScheduleId` | **포털 구현** | 없음(포털 고유) | `record`, 생성자 검증(비지 않음·길이 상한·제어문자 금지) |
| `MonthStatus`·`DataStatus`·`DeferralStatus`·`IngestState` | **포털 구현** | 없음 | 값은 설계서 §5 주석 그대로(I2) |
| `CommType` — 문자열 값객체 + 알려진 상수 + `isClawback()`·`isAdjustment()` | **포털 구현** | 없음 | 상수 `FY_COMM`·`RENEWAL`·`INCENTIVE`·`OVERRIDE`·`DEFERRED`·`CLAWBACK`·`ADJUSTMENT`, 모르는 값 허용 |
| 세션 바인딩(트랜잭션마다 테넌트 설정) | 플랫폼에서 | `spring.jdbc.TenantSessionBinder`, `TenantSession.SETTING_NAME` | C4·C10을 포털 시험으로 |
| 저장소 기반 클래스(`tenant_id` 술어 강제) | 플랫폼에서 | `spring.jdbc.TenantScopedRepository`·`TenantJdbcGateway`·`PlatformJdbcAutoConfiguration` | C1을 포털 시험으로 |
| 구체 저장소 `TenantRepository`·`IngestCursorRepository` | **포털 구현** | — | 패턴 확정용 두 개뿐 |
| 아키텍처 규칙 도우미 | 플랫폼에서 | `core.arch.ArchRules`(`archunit`은 소비자가 선언) | |
| 시드 고정 생성기 | 플랫폼에서 | `platform-core` test-fixtures `SeededCases` | |
| JCS·SHA-256 | 플랫폼에서 | `platform-canonical` `Canonicalizer`·`Sha256` | Phase 0은 의존만(payload 정규 해시는 Phase 1) |

**플랫폼에 없음(보고 대상): 0건.** 0.2.0에 있으나 Phase 0 범위 밖: `OrgPath`(v2 조직 뷰), `TenantDirectoryReader`(운영 롤), `IdentityResolver`(Phase 3).

## ③ 계약 (`contracts/`)

```
contracts/
├── shared/ga-disclosure/                      # 사본 — 고치지 않는다
│   ├── events/v1/envelope-core.schema.json    # ga-disclosure db82d5d의 contracts/events/v1/envelope-core.schema.json, 바이트 동일
│   ├── CHECKSUMS                              # <sha256>  events/v1/envelope-core.schema.json  (평문, sha256sum -c)
│   └── UPSTREAM                               # hjryoo-ai/ga-disclosure@db82d5d4ca35de2e16ac1d6238fef5d39ae304a2 + 파일 목록
├── events/v1/                                 # 포털 정본
│   ├── envelope.schema.json                   # allOf: [core 사본, 포털 제약]
│   ├── payloads/<Type>.v1.schema.json × 13
│   └── samples/<Type>.v1.json × 13
├── api/v1/engine-internal.openapi.yaml        # §4.1·§4.3 골격(피드 + 동기 API 3종), format 0
└── CHECKSUMS                                  # 포털 정본 파일의 평문 SHA-256(포털 쪽 변경 감지 — 엔진 E1이 사본을 들일 때 대조)
```

- 핵심 사본은 다른 파일을 참조하지 않는다(`$defs` 자체 포함 — db82d5d에서 확인, SHA-256 `3c9900a5…0b9c`). 그래서 "+ 그것이 참조하는 defs"는 0개다.
- `UPSTREAM`은 엔진의 한 줄 형식(`owner/repo@<40자>`)에 파일 목록 줄을 더한다. `verifyUpstreamContract`는 목록의 각 파일을
  `raw.githubusercontent.com/hjryoo-ai/ga-disclosure/<커밋>/contracts/<경로>`와 바이트 대조한다(CI 전용, 네트워크).
  `verifyContractChecksums`는 오프라인으로 `check`에 연결.
- 포털 Envelope(Q5): `allOf[0] = {"$ref": "../../shared/ga-disclosure/events/v1/envelope-core.schema.json"}`, `allOf[1]` = `type` 13개 열거 +
  `aggregate.kind` 9개 열거(Q4) + `version` `const 1` + `type`별 `if/then`(payload `$ref`, `aggregate.kind` 짝).
- `aggregate.kind`(Q4, [계약 추가]):

  | kind | 이벤트 | aggregate.id |
  |---|---|---|
  | `AGENT` | `AgentSnapshot` | agentId |
  | `COMM_CALC` | `CommissionCalculated` | calcId |
  | `ADJUSTMENT` | `AdjustmentPosted` | adjId |
  | `LIMIT_LEDGER` | `LimitLedgerOpened`·`Posted`·`Rebased` | ledgerId |
  | `DEFERRAL_SCHEDULE` | `DeferralScheduled`·`DeferralStatusChanged` | scheduleId |
  | `CLAWBACK_RECEIVABLE` | `ClawbackReceivableUpdated` | receivableId |
  | `POLICY` | `PolicyEventObserved` | policyNo |
  | `STATEMENT` | `StatementEstimated`·`StatementConfirmed` | `{agentId}:{closeYm}` |
  | `PAYOUT` | `PayoutCompleted` | `{agentId}:{closeYm}` |

- payload(설계서 §4.2 필드 그대로): 금액 `integer`, `closeYm`·`dueYm` 6자리 정규식, 날짜 `YYYY-MM-DD` 정규식, 시각은 핵심 사본의 `instant` 정규식과 같은 식,
  `commType`·`limitCutPolicy`·`payCondition`·`reason` 등 문자열(닫힌 것은 `enum` — `DeferralStatusChanged.to`, `PolicyEventObserved.eventType`, `limitCutPolicy`,
  `DeferralScheduled.reason`), 요율·배수는 Q3의 소수 문자열. `?` 표시 필드는 `required`에서 뺀다. 모르는 필드 허용(§4.5).
- 샘플(Q6): 설계사 A·테넌트 `T1`·2026-08-01 체결·월납 300,000(A-1). 이름은 허구(평문 스캔 센티널로 등록 — ⑥). 금액·세액은 예시값.
- 시험(`portal-projection` `EventSchemaTest`, 계약 디렉터리를 Gradle 입력으로 선언):
  - 샘플 13종 통과, 각 샘플의 `required` 필드를 하나씩 뺀 변형 전부 실패.
  - 포털 Envelope ⊇ 핵심: 핵심이 거부하는 변형(필드 7개 각각 누락, `eventId` 비UUID, `seq`·`version` 0, `type` 소문자 시작, `occurredAt` 오프셋 없음, `kind` 소문자, `payload` 비객체) 전부를 포털 Envelope도 거부 + `allOf[0]`이 사본을 가리킴.
  - `type`과 다른 payload 거부, `type` ↔ `kind` 짝 아니면 거부.
  - `contracts/` 전 파일(`json`·`yaml`) `format` 키워드 0.
  - OpenAPI 파일 파싱 + 피드 응답 필드(`events`·`nextSeq`·`headSeq`·`schemaVersion`)가 정수 규약.

## ④ 스키마·롤 (`portal-infra`)

- `V1__init.sql`: 설계서 §5의 17개 테이블과 인덱스 그대로 + S5·S6 + Q1·Q9. 마감월 불변(지시문 불변 규약 4)은 `earnings_line`·`earnings_month`에 주석으로만.
- `V2__rls.sql`: 17개 테이블 `ENABLE`·`FORCE ROW LEVEL SECURITY`, 정책 `tenant_isolation USING/WITH CHECK (tenant_id = current_setting('app.tenant_id', true))`.
  `portal_app`에 테이블 DML·`audit_access_id_seq` USAGE만. 끝에 `portal_%` 롤 중 `rolbypassrls`·`rolsuper`가 있으면 `RAISE EXCEPTION`.
- `docker/postgres/init-roles.sql`(정본): `portal_migrator`(LOGIN, NOSUPERUSER, NOBYPASSRLS, DB 소유), `portal_app`(LOGIN, NOSUPERUSER, NOBYPASSRLS, NOINHERIT).
- 저장소(포털 구현 두 개, `TenantScopedRepository` 하위):
  - `TenantRepository.current()` — 자기 행. `limit_warn_pct`·`limit_crit_pct`는 SQL에서 `round(x * 100)::bigint`로 읽어 `Ratio(bp, 10000)`(Q2). `params`는 문자열로.
  - `IngestCursorRepository` — `find()`, `upsert(lastSeq, lastEventId, state, updatedAt)`(`updatedAt`은 주입된 `Clock`).
- `portal-app`: Boot 진입점 + `/actuator/health`만, `management.health.db.enabled=false`, 데이터소스 `portal_app`, Flyway는 `portal_migrator`.

## ⑤ 아키텍처 규칙 (`portal-app` `archTest`)

(a)~(d)는 v1.0 원문이 없어 **추정**이다(Q7) — C5의 목록과 설계서 §3.3·§9에서.

| 규칙 | 내용 | 도우미 | 주입 |
|---|---|---|---|
| (a) 의존 방향 | `app → query/ingest/sim/recon → projection → domain`, `infra`는 `app`만 | `layeredDependencies` | A1 `projection` 클래스가 `query` 타입 참조 |
| (b) Spring·DB 무의존 | `domain`·`projection`: `org.springframework..`·`java.sql..`·`javax.sql..`·`org.flywaydb..` 0, `com.ga.platform..`은 `platform-core`만 | `springFreeModules` | A2 `domain`에 Spring 애너테이션 |
| (c) `double`/`float` 금지 | 전 모듈 필드·인자·반환 타입, 부동소수 타입의 멤버 호출·참조, 부동소수를 돌려주는 호출(지역 변수 타입은 바이트코드에 남지 않아 "만드는 호출"로 막는다) | `noDoubleOrFloat` | A3 `double` 필드 |
| (d) 저장소 상속 | DB 접근(`JdbcClient`·`JdbcTemplate`·`DataSource`·`Connection`)은 `TenantScopedRepository` 하위 클래스·플랫폼 클래스·허용 목록(빈 목록)만 | `dbAccessOnlyVia` | A4 `infra`에 `JdbcClient` 직접 쓰는 클래스 |
| (e) `platform-spring` 범위 | `com.ga.platform.spring..` 참조는 `infra`·`app`만 | `typesOnlyUsedIn` | A5 `ingest`에서 `TenantSessionBinder` 참조 |
| (f) 플랫폼 이름 중복 0 | `com.ga.platform.core..` 타입과 단순 이름이 같은 포털 클래스 0 | 포털 규칙 | A6 `portal-domain`에 `Won` 레코드 |
| (g) JDBC 타입 직접 참조 | `java.sql..`·`javax.sql..`·`DataSource`는 플랫폼 클래스·허용 목록만 | (d)와 같은 도우미, 패키지 기준 | A7 `recon`에서 `javax.sql.DataSource` |
| (h) `BigDecimal` 금지 | 지시문은 `domain`·`projection`. Q2 승인 시 **전 모듈** | `typesOnlyUsedIn`(빈 허용) | A8 `projection`에 `BigDecimal` |
| — 플랫폼 `core`의 Spring 무의존 | `platform-core` jar 클래스가 Spring·DB 0(의존성 그래프 확인) | ArchUnit 클래스 가져오기 | 주입 불가(플랫폼 jar 고정) — 근거만 |

- 비어 있는 모듈(`ingest`·`query`·`sim`·`recon`·`demo`)은 Phase 0에 클래스가 없다. 빈 층이 규칙을 공허하게 통과시키지 않게 각 주입은 해당 모듈에 임시 클래스를 넣어 확인한다.
- `TenantPredicateScanTest`(ga-disclosure와 같은 방식 — 추정): `main` 소스의 SQL 문자열 상수 중 테넌트 테이블을 건드리는 문장은 `:tenantId` 술어를 가져야 한다.
  허용 목록은 빈 상태, 항목은 `Allowed(fqn, reason)`(사유 빈 문자열 실패), 폐기 항목이 남으면 실패.
  주입 S1 술어 없는 문장 → 실패, S2 폐기 항목 → 실패, S3 사유 없는 항목 → 실패.

## ⑥ 빌드·하네스·CI

- Gradle(Kotlin DSL) **9.8.1**(2026-10-16 현재 최신 — ga-disclosure는 9.8.0), Java 25 툴체인, Spring Boot **4.1.1**(4.1.x 최신, BOM). BOM 밖: ArchUnit 1.5.1,
  json-schema-validator(3.0.8이 최신 — Boot BOM의 Jackson을 끌어올리면 ga-disclosure처럼 BOM 호환 최신판으로, 사유를 카탈로그 주석에). 버전 카탈로그 + 의존성 락.
- **플랫폼 해석(C9)**: `settings.gradle.kts`에서 `com.ga.platform` 그룹을 `exclusiveContent`로 **환경마다 정확히 한 저장소**에 묶는다 —
  CI(`CI=true`)는 GitHub Packages(`maven.pkg.github.com/hjryoo-ai/ga-disclosure`, 자격 ~~`GITHUB_ACTOR`·`GITHUB_TOKEN`~~ → 비밀 `GHP_USER`·`GHP_READ_TOKEN` — 승인 §3)만,
  로컬은 `mavenLocal()`만. 둘 다 `gradle/verification-metadata.xml`의 SHA-256(Q8 — 전 의존성)으로 검증되므로 바이트가 다르면 출처와 무관하게 실패한다.
  메타데이터는 `--write-verification-metadata sha256`로 만들고 `com.ga.platform` 13개 항목을 인계 §6 표와 대조한다(다르면 멈추고 보고).
- `printPlatformArtifacts`: 해석된 플랫폼 jar의 파일 경로와 SHA-256을 출력 — CI 로그에서 경로가 Gradle 캐시(원격에서 받음)이고 `~/.m2`가 아님을 1차 증거로.
- `verifyNoSnapshots`(`check`): 카탈로그·락 파일에 `SNAPSHOT` 0.
- jqwik 차단: 루트 `resolutionStrategy`에서 `net.jqwik` 그룹이 해석되면 실패(ga-disclosure 규칙 9와 같은 방식).
- Testcontainers PostgreSQL 18(digest), JVM당 1회, `init-roles.sql` → `portal_migrator`로 Flyway → 시험 데이터소스 `portal_app`. Docker가 없으면 **실패**(스킵 0).
- `verifyTestInputs`(`check`): ga-disclosure 구현을 옮긴다 — 시험 원천의 저장소 경로를 그 태스크의 입력과 대조.
- 평문 스캔 골격: 센티널 목록(샘플의 허구 설계사 이름 등) → 시험 결과 XML·로그에서 0건. Phase 3부터 실제 대상이 생긴다.
- CI(`.github/workflows/ci.yml`): `build`(Docker 러너, `./gradlew build`, `verifyUpstreamContract`, `printPlatformArtifacts`, 시험 보고서·결과 XML 아티팩트 업로드),
  `no-docker`(호스트 러너에서 Docker 데몬 정지 후 통합 시험 → **실패해야 성공**, 스킵 0 확인). 로컬에서 `TZ=UTC ./gradlew check` 한 번 더.
- 루트 `README.md`(소개·면책·실행·모듈 표), `LICENSE`(MIT, "ga-agent-portal contributors"), `web/README.md` 한 줄, `CLAUDE.md`(아래).

**`CLAUDE.md`**: ga-disclosure의 것을 바탕으로. 시스템 경계(돈은 엔진이 계산, 포털은 보여준다, 플랫폼·핵심 Envelope는 ga-disclosure 소유),
절대 규칙 = 지시문 불변 규약 1~8, 코드 규약·작업 방식 = ga-disclosure와 같게(계획 → 승인 → 단계 커밋 → 보고서 → 태그 → PR, 병합은 수용 심사 뒤,
공개 태그는 옮기지 않음, 주입은 ID 단위, 거짓 양성은 코드를 옮김, 입력 선언 규칙, 멈춰야 할 때는 묻는다). ga-disclosure 고유 규칙(봉인·서명·추천사유)은 넣지 않는다.

## ⑦ 완료 기준 → 시험

| # | 시험 | 주입 |
|---|---|---|
| C1 | `TenantContextGuardTest`: 미바인딩 상태에서 두 저장소의 모든 공개 메서드가 DB 도달 전 예외(연결 획득 0) | 해당 없음(플랫폼 보장 — jar는 SHA로 고정) |
| C2 | `RlsIsolationIT`: 17개 테이블에 두 테넌트 행을 `portal_migrator`로 넣고, `portal_app` 미설정 0행 / `T1` 설정 시 `T1`만(`tenant` 포함) | R1 한 테이블 정책 제거(임시 V3) → 실패, R2 한 테이블 RLS 해제 → 실패 |
| C3 | `RlsIsolationIT`: `SET ROLE portal_migrator`·`ALTER TABLE … DISABLE ROW LEVEL SECURITY`·`NO FORCE` 권한 오류, 마이그레이션 단언 | R3 `portal_app`에 `BYPASSRLS` → 마이그레이션 실패 |
| C4 | `TenantSessionBinderTest`(포털 데이터소스로): 트랜잭션 밖 거부, 컨텍스트 없는 트랜잭션 시작 실패, 시작 첫 문장이 설정 | 해당 없음(C1과 같은 이유) |
| C5 | `ArchitectureRulesTest` (a)~(h) | A1~A8 |
| C6 | `TenantPredicateScanTest` | S1~S3 |
| C7 | `EventSchemaTest`, `verifyContractChecksums`, `verifyUpstreamContract`, `verifyTestInputs` | K1 사본 1바이트 → 체크섬 실패, K2 `UPSTREAM` 커밋 변경 → 대조 실패(CI), K3 payload에 `format` → 실패, K4 포털 Envelope에서 핵심 `allOf` 제거 → ⊇ 시험 실패, K5 계약 입력 선언 제거 → `verifyTestInputs` 실패, K6 샘플만 바꿈 → `EventSchemaTest`가 UP-TO-DATE가 아님(재실행) |
| C8 | CI `build`·`no-docker`, 로컬 `TZ=UTC` check, jqwik 0 | J1 `net.jqwik` 의존 추가 → 해석 실패 |
| C9 | CI 해석 로그·`printPlatformArtifacts`·검증 메타데이터, `verifyNoSnapshots` | V1 `-Dmaven.repo.local=<임시 디렉터리>`에 바이트를 바꾼 0.2.0 jar → 검증 실패(사용자 `~/.m2`는 건드리지 않는다), V2 카탈로그에 `-SNAPSHOT` → 실패 |
| C10 | `RlsSettingNameTest`: `V2__rls.sql`이 읽는 설정 이름이 `TenantSession.SETTING_NAME`뿐 | R4 정책의 이름을 `app.tenantid`로 → 실패 |
| — | 평문 스캔 | P1 시험이 센티널 출력 → 실패 |

주입은 ID 하나 단위로 세고 각 단계 커밋 본문에 주입 → 실패 → 제거를 적는다(예상 25건 → 승인 뒤 27건: S4·Q9-1 추가).

## ⑧ 단계 — 단계마다 시험·주입·설계서 같은 커밋

1. **빌드 골격**: 래퍼·카탈로그·락·검증 메타데이터·플랫폼 해석·jqwik 차단·`verifyNoSnapshots`·`printPlatformArtifacts`·모듈 9개·README·LICENSE·CLAUDE.md·CI.
   설계서 S3·S11·S13. 주입 J1·V1·V2. **push해서 첫 CI로 C9(GitHub Packages 읽기)를 먼저 확인**한다 — 실패하면 사용자 조치(PAT 범위·비밀 이름 철자 — 승인 §3)로
   보고하고 기다린다.
2. **도메인**: 포털 값객체·열거형 + 속성 시험(`Won`·`Ym` 재증명, 포털 식별자 검증). 설계서 S1.
3. **스키마·롤·저장소**: V1·V2·`init-roles.sql`·하네스·두 저장소·C1~C4·C10. 설계서 S2·S4~S7. 주입 R1~R4.
4. **계약**: 사본·`CHECKSUMS`·`UPSTREAM`·두 검증 태스크·포털 Envelope·payload 13·샘플 13·OpenAPI·`EventSchemaTest`·`verifyTestInputs`. 설계서 S8~S10·S12. 주입 K1~K6.
5. **아키텍처**: `ArchitectureRulesTest`·`TenantPredicateScanTest`. 주입 A1~A8·S1~S3.
6. **마무리**: 평문 스캔 골격(P1), `TZ=UTC` check, 보고서 `docs/phase-00-보고서.md`, 태그 `phase-0`, PR. 병합은 수용 심사 회신 뒤.

## ⑨ 하지 않는 것 (지시문 "하지 말 것" 재확인)

프로젝터·poller·조회 API·인증·프론트, Kafka·Redis·캐시, 엔진 저장소 수정, 설계서에 없는 테이블·컬럼(Q1·Q9는 제약이지 컬럼이 아니다),
플랫폼 타입 재구현, 핵심 Envelope 사본 수정, `format`, jqwik, 컨테이너 잡 `no-docker`, 커밋된 V* 수정.

## ⑩ 다음 Phase 질문 (보고서에서 다시 정리)

- Phase 3: 설계서 §9는 역할을 토큰 클레임 `roles`에서, ga-disclosure는 `identity_link`에서 결정한다(인계 §4, 플랫폼 `IdentityResolver`는 역할·조직을 돌려준다).
  포털 §5 `identity_link`에는 `agent_id`만 있다. Phase 3에서 새 V로 역할·조직 컬럼을 더할지.
- E1: Q3·Q4의 계약 결정(요율 문자열, `aggregate.kind` 9종)과 I1(`amount` = 삭감 뒤)이 엔진 발행 규약이 된다. E1 지시문의 전제로.
