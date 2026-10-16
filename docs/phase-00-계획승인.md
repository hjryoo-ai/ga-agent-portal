# 포털 Phase 0 계획 승인 (2026-10-16) — ga-agent-portal, 지시문 v1.2

## 판정: 승인. Q1~Q10 전부 권장안(조건 포함). 1단계 CI 자격 증명 한 곳 정정(§3) 뒤 착수. 계획 갱신 1커밋, 재승인 불요

읽은 것: `work/phase-0` = `76951a3`의 `docs/phase-00-계획.md` 전문(저장소에서 직접), `main` = `4b56b6e`의 세 문서. 전제(ga-disclosure #15 병합 `db82d5d`·`platform-v0.2.0`·#14 병합 `552920c`, `~/.m2` 0.2.0 13개 파일 SHA 일치)는 계획이 적은 대로 받는다.

## 1. 인정하는 것

- **Q1 — `notification`의 UNIQUE가 설계서 안에서 모순**이라는 발견. §6.5의 `STMT_CONFIRMED:{ym}`·`PAYOUT:{ym}`은 설계사별 알림인데 §5의 `UNIQUE (tenant_id, dedup_key)`에는 `agent_id`가 없다. 두 번째 설계사부터 insert가 실패했을 것이고 Phase 6에서야 드러났을 결함을 V1 커밋 전에 잡았다. 설계서 v1.0·v1.1 두 판을 쓴 쪽의 결함이다.
- **Q5 — `oneOf`가 열린 payload와 양립하지 않는다**는 지적. §4.5 "모르는 필드 무시"를 지키면 `StatementEstimated`/`StatementConfirmed`처럼 필드가 겹치는 payload는 둘 다 맞아 `oneOf`가 실패하고, 반대로 `type`과 무관한 payload도 통과한다. 지시문 v1.2가 "payload oneOf 13"이라 쓴 것은 지시문 오류다 — 아래 §6에 기록.
- **I1 — 부록 A-1 seq 3~5가 더해지려면 `amount`는 삭감 뒤 지급분**이라는 해석. 2,800,000 + 800,000 = 3,600,000이 되는 유일한 읽기이고, 설계서 표가 그 뜻을 적지 않았다. 스키마 `description`에 적는 것이 맞다.
- 빈 모듈에 임시 클래스를 넣어 **빈 층이 규칙을 공허하게 통과시키지 않게** 하는 것, `~/.m2`를 건드리지 않고 `-Dmaven.repo.local`로 V1 주입을 하는 것, C9를 1단계에서 push해 가장 먼저 확인하는 순서.
- 플랫폼 대조표에서 "없음 0건"을 항목별로 보인 것. `OrgPath`·`TenantDirectoryReader`·`IdentityResolver`를 "있으나 범위 밖"으로 가른 것도 맞다.

## 2. 결정

| # | 결정 | 조건 |
|---|---|---|
| Q1 | **`UNIQUE (tenant_id, agent_id, dedup_key)`** | 설계서 §5 DDL과 §6.5 머리말("`dedup_key`는 설계사 안에서 유일")을 같은 커밋에. `dedup_key` 문자열 자체는 그대로(설계사를 키에 넣지 않는다 — 컬럼이 이미 있다). |
| Q2 | **`BigDecimal` 전 모듈 금지, `NUMERIC`은 SQL에서 정수로 읽는다** | 변환(`round(x * 100)::bigint`)은 `TenantRepository`의 SQL 한 곳에만. 시험: `80.00` → `Ratio(8000, 10000)` → `toPercentString` = `"80.0%"`, `95.50` → `"95.5%"`. `limit_multiple`은 Phase 0에 읽는 코드가 없으므로 같은 규약만 설계서 §5 주석에. DDL은 설계서대로 `NUMERIC` 유지 — 이 컬럼들은 표시용 파라미터이고 포털이 곱하지 않는다. |
| Q3 | **문자열 + 소수 정규식** [계약 추가] | 정규식은 `^(0|[1-9][0-9]*)(\.[0-9]{1,6})?$` 수준으로 자리수 상한을 두고(계획이 정한다), `description`에 "표시 전용 — 소비자는 산술에 쓰지 않는다". `basis.rate`·`basis.payoutRate`·`limitMultiple` 세 곳. **E1 지시문의 전제로 넘긴다**(§7). |
| Q4 | **9종 + `type` ↔ `kind` 짝 강제** [계약 추가] | 핵심 사본의 `aggregate.id`는 `minLength: 1`뿐이다(`552920c`에서 확인) — `{agentId}:{closeYm}`는 통과한다. 포털 Envelope의 `if/then`에서 `STATEMENT`·`PAYOUT`의 `id`에 `^[A-Za-z0-9][A-Za-z0-9._-]{0,63}:[0-9]{4}(0[1-9]\|1[0-2])$` 정규식을 더해 좁혀도 좋다(좁힘, 선택). 사본은 손대지 않는다. |
| Q5 | **`type`(+`version`)별 `if/then`** | 시험 "type과 다른 payload 거부"·"type ↔ kind 짝 아니면 거부"는 계획대로. `else: false`로 **열거 밖 `type`이 payload 검사 없이 통과하지 않게** 한다(열거가 이미 막지만 이중으로). |
| Q6 | **A-1 seq 1~13 + 4종 추가 샘플** | 추가 4종의 값은 A-1 계약(설계사 A·T1·2026-08-01·월납 300,000)을 잇는다 — 계획대로. `LimitLedgerRebased` 샘플은 A-3(`newLimit < accum`)으로. 지시문 "seq 1~9"는 v1.0 문구의 잔재 — §6에 기록. |
| Q7 | **v1.0 원문을 §4에 그대로 붙인다.** ⑤의 추정은 원문보다 넓거나 같다 — 추정대로 가되 §4의 차이 3건을 반영 | 아래 §4. |
| Q8 | **전 의존성 SHA-256, 신뢰 예외 0** | 플러그인·BOM 포함. 메타데이터 생성 뒤 `com.ga.platform` 13개 항목을 인계 §6 표와 **파일로 대조**(눈이 아니라 스크립트·시험)하고 그 결과를 1단계 커밋 본문에. 의존성 올릴 때마다 메타데이터 재생성이 따라오는 비용은 받아들인다. |
| Q9 | **`CHECK (… IN …)` 추가**(`comm_type` 제외) | 시험 `StatusEnumDdlTest`: Java 열거(`MonthStatus`·`DataStatus`·`DeferralStatus`·`IngestState`, `ingest_event.status`·`recon_result.status`·`inquiry.status`·`simulation_log.status`·`tenant.status`·`policy_limit_history.kind`·`policy_flag.flag_type`·`notification.severity`)의 값 집합이 V1의 `CHECK` 목록과 **같다**(C10 `RlsSettingNameTest`와 같은 방식 — 두 정본이 어긋나지 않게). 열거가 없는 컬럼은 DDL 목록이 정본이고 설계서 §5 주석과 대조. |
| Q10 | **각 단계의 코드와 같은 커밋**으로 설계서 v1.2 | 표준 규약. 1단계 커밋에서 제목·변경 이력(v1.2, 2026-10-16, "지시문 v1.2 반영 — S1~S13, Q1·Q9")만 먼저 올리고 본문은 단계마다. |
| I1 | **승인** — 스키마 `description`에 "`amount` = 한도 삭감 뒤 지급분, 요청액 = `amount + limitCutAmt`" | 엔진 `COMM_CALC`가 삭감 전·후 어느 금액을 저장하는지는 **E1에서 엔진 코드로 확인**한다. 어느 쪽이든 계약은 포털 것이고 엔진이 맞춘다. 설계서 §4.2 표에도 같은 문장. |
| I2·I3 | 승인(해석만) | — |
| S1~S13 | **전부 승인** | S7의 "DB 헬스 지표 Phase 0 비활성"은 설계서 §9 롤 표에 "`portal_health` — Phase 8, 전용 롤"로 자리를 예약. S11 JUnit 6은 Boot 4.1.1 BOM 관리판 — 버전은 카탈로그에 쓰지 않는다. |
| 버전 | **Gradle 9.8.1·Spring Boot 4.1.1 확인** | `services.gradle.org/versions/current` = 9.8.1(빌드 2026-10-07). Boot 4.1.1은 2026-08-20 발행, 10-16 현재 4.1 계열 최신. 둘 다 계획대로. |

## 3. 정정 — C9 자격 증명 (1단계 착수 전 계획 갱신)

계획 ⑥은 CI에서 `GITHUB_ACTOR`·`GITHUB_TOKEN` + `permissions: packages: read`로 GitHub Packages를 읽는다고 썼고, 표지는 실패 시 "패키지 → Manage Actions access → ga-agent-portal Read"를 사용자 조치로 적었다. **둘 다 Maven 레지스트리에는 맞지 않는다.**

- GitHub Packages의 **Maven 레지스트리는 저장소 스코프**다. 패키지 권한은 발행 저장소(`ga-disclosure`)의 권한을 그대로 따르고, "Manage Actions access"(세분 권한) 화면은 컨테이너·npm 등 세분 권한 패키지에만 있다. `ga-agent-portal` 워크플로의 `GITHUB_TOKEN`은 자기 저장소의 패키지만 읽는다.
- 그래서 사용자가 **PAT(classic, `read:packages`)**를 만들어 `ga-agent-portal` 저장소 비밀로 넣었다 — **`GHP_USER`·`GHP_READ_TOKEN`**(2026-10-16 등록 확인).

고칠 것:
1. `settings.gradle.kts`의 CI 저장소 자격: `credentials { username = System.getenv("GHP_USER"); password = System.getenv("GHP_READ_TOKEN") }`. 워크플로 `env:`에 `GHP_USER: ${{ secrets.GHP_USER }}`, `GHP_READ_TOKEN: ${{ secrets.GHP_READ_TOKEN }}`. `permissions: packages: read`는 두어도 무해하나 근거로 쓰지 않는다.
2. 두 환경변수가 **없으면** CI 빌드가 "자격 증명 없음"으로 **즉시 실패**(빈 문자열로 401까지 가지 않게). 로컬은 `mavenLocal()`만이므로 PAT가 필요 없다 — 계획의 `exclusiveContent` 분리가 이 점에서도 맞다.
3. 저장소 안 어떤 파일에도 토큰·사용자명 값을 쓰지 않는다(README에는 비밀 **이름**만). `printPlatformArtifacts`는 경로·해시만 출력. 포크 PR에는 비밀이 전달되지 않으므로(GitHub 규약) 그 경우 C9가 실패하는 것은 정상 — CLAUDE.md 한 줄.
4. 1단계 첫 CI가 실패하면 보고할 사용자 조치는 "Manage Actions access"가 아니라 **PAT 범위(`read:packages`; `ga-disclosure`가 비공개면 `repo`)와 비밀 이름 철자** 확인이다.

## 4. Q7 — v1.0 지시문 §6 원문 (2026-09-22) 과 ⑤의 대조

원문(그대로):

> ### 6. 아키텍처 테스트 (`portal-app`의 `archTest` 소스셋 또는 별도 테스트 모듈)
> - ArchUnit: (a) 모듈 의존 방향, (b) `portal-domain`·`portal-projection`에서 `org.springframework..`, `java.sql..`, `javax.sql..` 참조 금지, (c) 전 모듈에서 `double`/`float`/`Double`/`Float` 타입의 필드·파라미터·반환 금지, (d) `..infra..` 패키지의 DB 접근 클래스는 `TenantScopedRepository`를 상속해야 하며 `JdbcClient`/`JdbcTemplate`/`DataSource` 직접 참조는 `TenantScopedRepository`와 `TenantSessionBinder`에서만 허용.
> - SQL 소스 스캔 테스트: `portal-infra`의 모든 `.sql`·문자열 SQL을 스캔해 `tenant`, `flyway_schema_history`를 제외한 테이블 접근 문장에 `tenant_id` 조건 또는 `tenant_id` 컬럼 지정이 포함되는지 검사. 허용 목록 파일(`allowlist`)은 비어 있는 상태로 시작하고, 항목 추가 시 사유 주석을 강제한다.

(v1.0 C5·C6: "각 규칙을 **의도적으로 위반한 임시 클래스**로 실패함을 한 번씩 확인한 뒤 제거(보고서에 기록)", "`TenantPredicateScanTest` + 위반 문장 임시 삽입으로 실패 확인 후 제거".)

대조 — ⑤의 (a)~(d)는 원문을 전부 포함하고 세 곳이 더 넓다(`org.flywaydb..`, 부동소수 **호출**까지, `Connection`). 그대로 간다. 차이 3건만 반영:

| # | 원문 | ⑤ 추정 | 결정 |
|---|---|---|---|
| D1 | SQL 스캔 대상 = **`.sql` 파일 + 문자열 SQL** | `main` 소스의 SQL 문자열 상수 | **둘 다.** 마이그레이션 `.sql`의 DML(`INSERT/UPDATE/DELETE/SELECT`)도 대상 — Phase 0에는 없지만 뒤 Phase의 데이터 마이그레이션이 걸린다. DDL(`CREATE/ALTER`)은 제외. |
| D2 | `tenant` 테이블 제외 | 제외 없음 | **⑤대로 제외하지 않는다.** v1.2 S4로 `tenant`도 RLS 자기 행이고 `TenantRepository.current()`는 어차피 `tenant_id = :tenantId`를 가진다. 제외 목록은 `flyway_schema_history`뿐. |
| D3 | "`tenant_id` 조건 **또는** `tenant_id` 컬럼 지정" | `:tenantId` 술어 | **INSERT는 술어가 없다** — INSERT는 컬럼 목록에 `tenant_id`가 있고 그 값이 `:tenantId` 바인드인 것, 나머지는 `WHERE … tenant_id = :tenantId`. 주입 S1은 두 형태(술어 없는 UPDATE, `tenant_id` 없는 INSERT)로 하나씩 → 주입 1건 추가(S4). |

## 5. 보강

- **B1. 주입 수**: 계획 25건 + S4(D3) + Q9 시험 주입 1건(열거 값 하나를 DDL `CHECK`에서 뺌 → 실패) = 27건 예상. ID 단위.
- **B2. C9 1차 증거**는 CI 로그의 해석 경로(Gradle 캐시)와 `printPlatformArtifacts` 해시 13개가 인계 §6 표와 같다는 것 — 보고서에 표로. 2차는 검증 메타데이터 파일 자체.
- **B3. `WITH CHECK`** (계획 ④) — 승인. 다른 테넌트 행 insert 시도가 정책 위반으로 실패하는 시험 1건(`RlsIsolationIT`에 추가, 주입 아님).
- **B4. 설계서 v1.2 변경 이력**에 Q1을 "설계서 v1.0·v1.1 결함(모순)"으로, I1을 "표에 뜻이 없었음"으로 적는다 — 심사 측 오류 기록 규약.

## 6. 지시문 오류 (DECISIONS.md에)

- v1.2 §5 "payload oneOf 13" — 열린 payload와 양립 불가(Q5).
- v1.2 §5 "부록 A-1 seq 1~9" — v1.0 문구 잔재, C7 "13종"과 모순(Q6).
- 플랫폼 0.2 정비 지시문 완료 기준의 "패키지 설정에서 저장소 접근" 및 v1.2 C9의 "패키지 접근 설정" — Maven 레지스트리에 없는 화면. 올바른 조치는 PAT 비밀(§3). 계획 ⑥·표지가 같은 전제를 이어받은 것은 지시문 탓.
- 지시문 v1.2가 v1.0 §6 원문을 싣지 않고 "그대로"라고만 한 것(Q7). 이 문서 §4가 원문이다.

## 7. 다음

- Phase 0 수용 뒤 **E1(엔진 아웃박스) 지시문**. 전제로 넘어가는 것: Q3(요율 문자열)·Q4(`kind` 9종·`id` 규약)·Q5(`if/then`)·I1(`amount` = 삭감 뒤)·핵심 Envelope 사본 규약(`contracts/shared/ga-disclosure/`)·`format` 0·E3.2 규약.
- Phase 3 질문(`identity_link`에 역할·조직 컬럼) — 지금 답하지 않는다. 다만 방향은 정해져 있다: 역할은 ga-disclosure와 같이 **`identity_link`에서만**, 토큰 `roles` 클레임은 신뢰하지 않는다 → 설계서 §9 "토큰 클레임: … `roles`"는 Phase 3에서 고친다. Phase 0은 컬럼을 더하지 않는다(V1 불변).

## 8. 절차

계획 갱신 1커밋(§3 자격 증명, §2 조건, §4 D1~D3, 주입 수) → 1단계 push → 첫 CI로 C9 → 이후 단계. 보고서·`phase-0` 태그·PR까지, 병합은 수용 심사 회신 뒤.
