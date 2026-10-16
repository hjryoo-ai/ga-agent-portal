# 포털 Phase 0 수용 심사 결과 (2026-10-16) — ga-agent-portal

## 판정: 수용. PR #1 병합 승인(merge commit). 조건 1건(K7)은 병합 전 커밋 + `phase-0.1` 태그, `phase-0`은 `28654b6` 유지

읽은 것: `phase-0` 태그 커밋의 `docs/phase-00-보고서.md` 전문, `DECISIONS.md`, `CLAUDE.md`, `settings.gradle.kts`, `.github/workflows/ci.yml`, `V2__rls.sql`, 포털 Envelope 스키마와 샘플 17, `ArchitectureRulesTest`의 (g)·(h)·허용 목록 검사, `EventSchemaTest`의 IRI 해석 — 전부 저장소에서 직접. 테스트 수 2,266(실패 0·스킵 0)은 규약대로 CI 아티팩트(run 38892671446) 출처로 받는다. 저장소 전체에서 토큰 형태 문자열(`ghp_…`·`github_pat_…`) 0건을 확인했다.

## 1. 인정하는 것

- **C9가 첫 시도에 통과했고, 증거가 "성공했다"가 아니라 경로·출처·해시 13개다.** `printPlatformArtifacts`가 Gradle 캐시 경로와 `source=remote`를 찍고 13개 SHA-256이 인계 §6 표·검증 메타데이터와 같다는 것을 `verifyPlatformPins`가 빌드마다 다시 센다. 로컬 `mavenLocal`과 CI GitHub Packages가 **같은 바이트**라는 사실이 두 환경에서 따로 증명됐다.
- **F2 — 첫 CI 실패의 원인을 끝까지 따라간 것.** "로컬 캐시에 이미 있던 파일은 `--write-verification-metadata`가 기록하지 않는다"는 Gradle의 성질이고, 메타데이터를 빈 `GRADLE_USER_HOME`에서 다시 만들고 **또 다른 빈 홈에서 통과를 확인**한 것이 맞다. 전 의존성 고정(Q8)을 승인했을 때 이 함정을 지시문이 적지 않았다.
- **F3 — "POM도 대조한다"는 인계 문구가 받을 때만 참**이라는 발견과, 13개 전부가 검증을 거치도록 `printPlatformArtifacts`가 소스 jar·POM을 명시적으로 받게 한 처리. 문서의 주장을 믿지 않고 동작으로 확인했다.
- **F1 — (g)를 글자 그대로 쓰면 플랫폼 API 자체와 충돌**한다는 것을 규칙을 약화시키지 않고(연결·문장 수준 타입은 그대로 금지) 경계를 v1.0 (d)와 같은 자리로 되돌려 푼 것. `IngestCursorRepository`가 `Timestamp` 대신 `OffsetDateTime`을 써서 `java.sql`을 `ResultSet` 하나로 줄인 것도 "코드를 옮긴다" 규약 그대로다.
- **J1 첫 시도가 `NO-SOURCE`로 통과한 것을 성공으로 적지 않은 것**, R1·K2가 의도한 단언보다 앞에서 실패했다고 그대로 적은 것(F10). 빈 모듈에 임시 클래스를 넣어 규칙이 공허하게 통과하지 않게 한 것.
- `V2__rls.sql`의 `WITH CHECK` + 13문장 권한 오류 시험(C3) + 마이그레이션 끝의 롤 단언. `flyway_schema_history`까지 `portal_app`에서 떼어 둔 것.
- `else: false`를 14번째 `if/then`으로 구현하고 `$comment`에 이유를 적은 것, 스키마 IRI를 저장소 파일로만 풀어 **원격 조회 0**인 것(`EventSchemaTest` 41~47행).

## 2. 결정

| # | 결정 | 조건 |
|---|---|---|
| F1 / Q1 (g) 경계 | **수용 — 연결·문장 수준 타입은 플랫폼 클래스·허용 목록만, 결과 타입(`ResultSet`, 그 시그니처의 `SQLException`)은 `TenantScopedRepository` 하위 클래스까지** | 결과 타입 허용 집합을 규칙 안에 **닫힌 목록**으로 둔다(지금 `CONNECTION_LEVEL_JDBC`가 금지 목록이듯, 허용 쪽도 열거 — `java.sql.ResultSet`·`java.sql.SQLException`뿐). 지시문 (g) 문구는 틀렸다 — DECISIONS **DE-5**. |
| F4 / Q2 `simulation_log.status` | **`REQUESTED`/`SUCCEEDED`/`FAILED` 수용** | Phase 5에서 실패 사유가 필요해지면 `status`를 늘리지 말고 `error` 컬럼을 새 V로(§5 개정 같은 커밋). 설계서 §5에 값이 없던 것은 설계서 결함 — **DD-3**. |
| Q3 엔진 E1 계약 사본 | **수용 — 엔진도 `contracts/shared/<업스트림>/`별 디렉터리·`UPSTREAM`** | E1 지시문의 전제로. 포털 업스트림은 **`phase-0` 태그 커밋 `28654b6`**(Q7대로). `phase-0.1`이 생겨도 계약 파일은 바뀌지 않으므로 업스트림은 그대로. |
| Q4 Phase 1 `payload_hash` | **원칙 수용 — Phase 1 계획에서 확정** | V3 `ingest_event.payload_hash`(JCS SHA-256, hex 64자, `CHECK` 정규식), 의미: 같은 `eventId`·같은 해시 → `SKIPPED`, 같은 `eventId`·다른 해시 → **독 이벤트**(그 테넌트 `FAILED`, 다른 테넌트 무영향 — §6.1). 설계서 §5·§6.1 개정 같은 커밋. Phase 1 지시문에 넣는다. |
| F3 / Q5 인계 문구 | **정정 1줄 승인 — ga-disclosure의 다음 작업에 묶는다**(단독 PR 불요) | 포털의 `docs/handoff/` 사본은 "받은 원문 그대로"가 기록이므로 **동기화하지 않는다**(머리말이 이미 `552920c` 바이트 사본이라고 적는다). |
| Q6 역할 출처 | Phase 3 | 받은 결정 그대로. |
| F2 빈 홈 재생성 | 수용 | CLAUDE.md 코드 규약의 메타데이터 재생성 문장에 **"빈 `GRADLE_USER_HOME`에서"** 를 더한다 — 사실 정정이므로 병합 전 커밋에 바로. |
| F5·F6·F7·F8·F9 | 수용 | F9(json-schema-validator 3.0.6 — Jackson BOM 우선)는 카탈로그 주석에 있음을 확인. F7(앱 기동 Flyway)은 포털 Phase 8에서 ga-disclosure와 같이 운영자 명령으로. |
| F10 R1·K2 조기 실패 | R1 수용. **K2는 아래 K7로 보완** | — |
| 태그 | `phase-0` = `28654b6` 유지. 병합 전 추가 커밋에 **`phase-0.1`** | 공개 태그 불이동 규약. |

## 3. 조건 — K7 (병합 전)

K2는 `UPSTREAM` 커밋에 파일이 없어 **404로** 실패했다. 그것은 "고정한 커밋에서 받는다"의 증거이지, `verifyUpstreamContract`의 **바이트 비교 분기가 실패할 수 있다**는 증거가 아니다. K1은 `verifyContractChecksums`(오프라인) 쪽만 증명한다. 두 검사 사이에 "사본과 `CHECKSUMS`를 함께 바꾸면 오프라인 검사는 통과하고 원격 대조만 남는다"는 틈이 있고, 그 분기가 한 번도 실패한 적이 없다.

- **K7**: 사본 `envelope-core.schema.json` 1바이트 변경 **+ `CHECKSUMS` 재생성**(→ `verifyContractChecksums` 통과) → 로컬에서 `./gradlew verifyUpstreamContract` 실행 → **바이트 불일치로 실패**해야 한다(메시지에 파일명·두 해시). 네트워크는 사용자 머신에서 된다(raw.githubusercontent). 주입 → 실패 → 제거를 커밋 본문에, 보고서 §3 표에 K7 행과 §5 F10에 한 줄 추가(주입 28건).
- 같은 커밋에: F1의 결과 타입 **닫힌 허용 목록**(§2), CLAUDE.md "빈 `GRADLE_USER_HOME`" 한 줄, DECISIONS DE-5·DD-3, 그리고 아래 B1의 설계서 한 줄. 그 커밋에 `phase-0.1`. PR 코멘트에 그 커밋의 CI run.

## 4. 보강 (같은 커밋, 작은 것)

- **B1. 마이그레이터도 정책을 따른다는 결과를 설계서에.** `portal_migrator`가 `NOBYPASSRLS`이고 전 테이블이 `FORCE`이므로, 앞으로의 **데이터 마이그레이션**(백필·재구축 스왑)은 `set_config('app.tenant_id', …, true)` 없이는 0행을 본다 — R1이 바로 그 현상이었다(시드가 거부됨). 설계서 §5 RLS 문단에 한 줄: "데이터 마이그레이션은 테넌트를 열거하며 테넌트마다 설정한 뒤 실행한다". Phase 2 재구축(§6.7 그림자 스키마 스왑) 설계 때 다시 본다.
- **B2. `no-docker` 잡의 `it.log` 아티팩트** — Gradle은 자격 증명을 출력하지 않고 `GHP_USER` 값(저장소 소유자명)은 공개 정보이므로 지금은 문제가 없다. 다만 아티팩트는 GitHub의 비밀 가림을 거치지 않으므로, 업로드 전 `grep -c "$GHP_READ_TOKEN"` 0건 확인 한 줄을 그 잡에 둔다(ga-disclosure 평문 스캔과 같은 자리).

## 5. 심사·지시문 측 기록 (DECISIONS.md)

- **DE-5**: 지시문 v1.2 §6 (g) "`java.sql`·`javax.sql`·`DataSource` 직접 참조는 플랫폼 클래스와 허용 목록만" — 플랫폼 `RowMapper`가 `ResultSet`을 요구하므로 글자 그대로는 구현 불가. 올바른 문구는 "연결·문장 수준 타입". 지시문을 쓸 때 플랫폼 API(`API-0.2.0.txt`)와 대조하지 않았다.
- **DD-3**: 설계서 §5 `simulation_log.status`에 값 집합이 없었다(다른 상태 컬럼은 주석에 있었다).
- 지시문 Q8 승인 시 "빈 Gradle 홈에서 생성"을 적지 않은 것(F2) — 조건 누락.

## 6. 다음

- **엔진 E1(아웃박스) 지시문**을 쓴다. 전제는 보고서 §6 Q7 그대로 + Q3(사본 디렉터리 구조) + I1(엔진 `COMM_CALC` 금액이 삭감 전인지 후인지 **엔진 코드로 확인**해 `amount`·`limitCutAmt` 발행 규약을 맞춘다). 포털 업스트림 = `28654b6`.
- 포털 Phase 1(인제스트)은 E1과 병렬 가능 — 계약이 `phase-0`에 고정돼 있고 Phase 1은 파일 피드 어댑터(부록 B)로 엔진 없이 시험한다. Phase 1 지시문에 Q4(`payload_hash`)를 넣는다.
