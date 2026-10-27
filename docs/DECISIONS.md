# 결정 기록

설계서·지시문과 다르게 정한 것, 지시문·설계서의 오류로 판정된 것을 날짜순으로 적는다. 판정 원문은 각 항목의 출처 문서다.

## 2026-10-16 — Phase 0 계획 승인 (`docs/phase-00-계획승인.md`)

### 지시문 오류 (승인 §6)

| # | 지시문 문구 | 무엇이 틀렸나 | 정한 것 |
|---|---|---|---|
| DE-1 | v1.2 §5 "payload `oneOf` 13" | 모르는 필드를 허용하는 열린 payload(설계서 §4.5)와 양립하지 않는다 — 필드가 겹치는 payload(`StatementEstimated`/`StatementConfirmed`)는 둘 다 맞아 실패하고, `type`과 무관한 payload도 통과한다 | `type`(+`version`)별 `if/then`, 열거 밖 `type`은 거부(Q5) |
| DE-2 | v1.2 §5 "샘플(부록 A-1 seq 1~9)" | v1.0 문구의 잔재. C7 "13종"과 모순(A-1 1~9는 6종, 1~13은 9종) | A-1 seq 1~13 + 4종(Q6) |
| DE-3 | 플랫폼 0.2 정비 지시문 완료 기준·v1.2 C9의 "패키지 접근 설정" | GitHub Packages의 Maven 레지스트리는 저장소 스코프라 "Manage Actions access" 화면이 없다(세분 권한 패키지 — 컨테이너·npm 등 — 에만 있다). 다른 저장소의 `GITHUB_TOKEN`으로는 읽지 못한다. 계획 ⑥과 표지가 같은 전제를 이어받았다 | PAT(classic, `read:packages`)를 저장소 비밀 `GHP_USER`·`GHP_READ_TOKEN`으로(승인 §3) |
| DE-4 | v1.2 §6 "v1.0 (a)~(d) 그대로", "SQL 스캔 v1.0 그대로" | v1.0 §6 원문을 싣지 않았다. 원문은 승인 문서 §4 | 계획 ⑤ 추정 + 차이 D1~D3(Q7) |

### 설계서 결함 (설계서 v1.2 변경 이력에도)

| # | 무엇 | 정한 것 |
|---|---|---|
| DD-1 | v1.0·v1.1 §5 `notification`의 `UNIQUE (tenant_id, dedup_key)` ↔ §6.5 `STMT_CONFIRMED:{ym}`·`PAYOUT:{ym}`(설계사가 키에 없음) — 같은 테넌트 두 번째 설계사부터 insert 실패 | `UNIQUE (tenant_id, agent_id, dedup_key)`(Q1) |
| DD-2 | §4.2 `CommissionCalculated`의 `amount`와 `limitCutAmt`의 관계가 표에 없었다 | `amount` = 한도 삭감 뒤 지급분, 요청액 = `amount + limitCutAmt`(I1 — 부록 A-1 seq 3~5가 더해지는 유일한 읽기). 엔진이 저장하는 금액은 E1에서 확인, 계약은 포털 것 |

## 2026-10-16 — Phase 0 수용 심사 (`docs/phase-00-수용심사.md`)

### 지시문 오류 (심사 §5)

| # | 지시문 문구 | 무엇이 틀렸나 | 정한 것 |
|---|---|---|---|
| DE-5 | v1.2 §6 (g) "`java.sql`·`javax.sql`·`DataSource` 직접 참조는 플랫폼 클래스와 허용 목록만" | 플랫폼 `RowMapper`가 `ResultSet`(과 `throws SQLException`)을 요구하므로 글자 그대로는 구현할 수 없다. 지시문을 쓸 때 플랫폼 API(`API-0.2.0.txt`)와 대조하지 않았다 | 연결·문장 수준 타입은 플랫폼 클래스·허용 목록만, 그 밖에서는 결과 타입 닫힌 목록 `java.sql.ResultSet`·`java.sql.SQLException`만(어디서 쓰는지는 (d) — `TenantScopedRepository` 하위 클래스). `ArchitectureRulesTest.RESULT_LEVEL_JDBC`, 주입 A9 |
| DE-6 | 계획 승인 Q8 "전 의존성 SHA-256" | 메타데이터를 **빈 `GRADLE_USER_HOME`에서** 만들어야 한다는 조건을 적지 않았다 — 캐시에 있던 파일(BOM `.module` 5개)이 기록되지 않아 첫 CI가 실패했다(보고서 F2) | CLAUDE.md 코드 규약에 "빈 `GRADLE_USER_HOME`에서" |

### 설계서 결함 (설계서 v1.2 변경 이력에도)

| # | 무엇 | 정한 것 |
|---|---|---|
| DD-3 | §5 `simulation_log.status`에 값 집합이 없었다(다른 상태 컬럼은 주석에 있었다) | `REQUESTED`/`SUCCEEDED`/`FAILED`(보고서 F4). Phase 5에서 실패 사유가 필요해지면 값을 늘리지 않고 `error` 컬럼을 새 V로(§5 개정 같은 커밋) |

## 2026-10-17 — 계약 v1.1 (`docs/phase-01-지시문.md` §0, 지시문이 승인)

### 설계서 결함

| # | 무엇 | 정한 것 |
|---|---|---|
| DD-4 | 계약 v1.0이 **엔진에 없는 데이터를 필수로** 요구했다 — `policySummary.productName`·`insuredMasked`(엔진에 계약 마스터·고객 데이터 없음), `basis.rate`·`payoutRate`·`installmentNo`(NULL 가능·typed 컬럼 아님), `AgentSnapshot.grade`·`role`(이력 없는 설계사, 역할 개념 없음), `StatementConfirmed.clawbackOffset`·`withholdingTax`·`net`(지급 런에서 계산). 설계 시 엔진 스키마와 대조하지 않았다 | 전부 선택으로(K1~K4), 지급 런 확정값은 `PayoutCompleted` 선택 필드(K5). 완화·추가뿐이라 `version` 1 그대로(§4.5). 설계서 v1.3 |

### 해석 (지시문 문구와 다르게 적은 곳)

| # | 지시문 | 한 것 | 이유 |
|---|---|---|---|
| CI-1 | K7 "`gross` = Σ `commType ≠ CLAWBACK` 라인" | 계약 설명문과 §5 주석 모두 "Σ 그 달 `CommissionCalculated` 라인 중 commType ∉ {CLAWBACK, ADJUSTMENT}" | 포털은 `AdjustmentPosted`를 `comm_type = ADJUSTMENT` 라인으로 저장하고(§4.2) 기존 §5 정의도 ∉ {CLAWBACK, ADJUSTMENT}였다. "≠ CLAWBACK"을 글자대로 쓰면 정정이 `gross`와 `adjustment`에 두 번 들어간다. 지시문의 목적("포털 합산과 같은 정의")은 이쪽이다 — 확인 요청 |

### 남은 것 (다음 Phase에서)

- **O-1**: `agent_profile.role`은 V1에서 `NOT NULL`인데 계약 v1.1에서 `AgentSnapshot.role`은 선택이다. V1은 고치지 않는다. Phase 2(프로젝터)가 `role` 없는 스냅샷을 받을 때의 값(새 V로 `NOT NULL` 해제 또는 고정 값)을 계획에서 정한다 — 역할의 정본은 어차피 `identity_link`(Phase 3).
- **O-2**(엔진 E1에 전달): 엔진 `PayoutService.settleOne`의 `gross_net`은 `NetAmountCalculator.netOf(records)` — 그 달 미정산 레코드 **전부**(CLAWBACK 포함)의 순합이다. E1 지시문 D-6 "`gross`(= `gross_net`)"는 위 `gross` 정의(CLAWBACK 제외)와 다르다. 또 엔진 `COMM_CALC`에 `comm_type = ADJUSTMENT` 레코드가 있으면 E1 D-5 "`gross` = Σ `comm_type ≠ CLAWBACK`"이 정정을 `gross`에 넣는다. E1 계획에서 엔진 코드로 확정한다.

## 2026-10-17 — Phase 1 계획 승인 (`docs/phase-01-계획승인.md`)

### 지시문 오류 (승인 §5)

| # | 지시문 문구 | 무엇이 틀렸나 | 정한 것 |
|---|---|---|---|
| DE-7 | Phase 1 지시문 §0 K7 "`gross` = Σ `commType ≠ CLAWBACK` 라인" | ADJUSTMENT를 빼지 않았다 — 포털은 `AdjustmentPosted`를 ADJUSTMENT 라인으로 저장하므로 정정이 `gross`와 `adjustment`에 두 번 들어간다. 엔진 E1 지시문 D-6 "`gross`(= `gross_net`)"도 같은 오류(엔진 쪽 기록) | CI-1 확인: Σ `CommissionCalculated` 라인 중 commType ∉ {CLAWBACK, ADJUSTMENT} |
| DE-8 | Phase 1 지시문 D-2·D-4 | "아는 `eventId`가 다른 `seq`로 오는 경우"가 빠졌다 | 독 `EVENT_SEQ_CONFLICT`(Q5) |

### 기록

- 계약 v1.1.1(L1~L3 — PR #3, `f54d008`)이 v1.1 지시문 §0에 없었던 것은 엔진 E1 계획이 코드를 읽기 전이라 알 수 없었다 — 오류가 아니라 순서. 다만 §0 K-목록을 쓸 때 엔진의 `LIMIT_LEDGER_HIST`(아무도 쓰지 않음)·`paid_at`(없음)을 확인하지 않은 것은 기록한다.

## 2026-10-17 — Phase 1 구현에서 계획과 다르게 한 것 (보고서 §5)

| # | 계획 | 한 것 | 이유 |
|---|---|---|---|
| P1-1 | 독 분류 표 #1~#8 | **#9 `APPLY_ERROR`**(FAILED) 추가 — 분류하지 못한 적용 트랜잭션 오류(DB 제약 위반 등) | 승인된 J9 분석("잠금이 없으면 두 번째 워커의 고유 위반이 멀쩡한 테넌트를 멈춘다")은 그런 오류가 재시도가 아니라 정지여야 성립한다. 정지가 규약(지시문 D-10)과도 맞다. detail은 예외 이름만 |
| P1-2 | 감사 동작 `INGEST_STATUS`·`INGEST_RESUME`·`INGEST_RESUME_REJECTED`·`INGEST_PAUSE` | `INGEST_PAUSE_REJECTED` 추가 | "전 건 감사"(D-8) — 거부된 pause도 남긴다 |
| P1-3 | 스케줄 | `tenant.status`가 `ACTIVE`가 아닌 테넌트는 폴링하지 않는다 | §5 `tenant.status`(ACTIVE/PAUSED)의 뜻. 계획에 없던 조건 |
| P1-4 | 행위자 없는 CLI | DB에 닿기 전에 거부(감사 행 없음) | 감사할 행위자가 없다 |

## 2026-10-17 — Phase 1 수용 심사·계약 v1.2 (`docs/phase-01-수용심사.md`, `docs/phase-02-지시문.md` §0)

### 설계서 결함 (설계서 v1.4.1 변경 이력에도)

| # | 결함 | 처리 |
|---|---|---|
| DD-5 | §5 `policy_limit_history` PK `(tenant_id, ledger_id, posting_seq)` — `LimitLedgerRebased`는 전기 순번이 없을 수 있고(v1.1.1 L1) 있으면 직전 전기와 같은 값이라 키가 충돌한다 | Phase 2 V4에서 키를 이벤트 `seq`(`source_seq`)로, `posting_seq` NULL 허용(Phase 2 지시문 D-7) |
| DD-6 | §5 `tenant.status`가 `ACTIVE/PAUSED`만 적고 뜻을 적지 않았다 — Phase 1 지시문이 `tenant.status`와 커서 상태의 관계를 정하지 않았다(수용 심사 Q1) | `PAUSED` = 관리 상태(폴링·조회 모두 중지), 커서 상태와 독립 — 두 문이 다 있어야 한다. §5 주석(v1.4.1). Phase 3 조회 거부는 같은 상수 |
| DD-7 | 부록 A-1 예시값(월납 300,000, FY 2,800,000)은 엔진 시드로 재현되지 않는다 — 설계 시 엔진 요율을 보지 않았다 | 정본은 엔진 E2 0단계가 월납 150,000으로 낸 스트림(Phase 2 D-1). 그때까지 샘플을 `a1/`·`contract-only/`로 나눈다(M5) |

### 해석 (지시문 문구와 다르게 적은 곳)

| # | 지시문 | 한 것 | 이유 |
|---|---|---|---|
| V12-1 | M5 "샘플 디렉터리 분리: `samples/a1/`와 `samples/contract-only/`" | 세 번째 디렉터리 **`samples/standalone/`**(14 `ClawbackReceivableUpdated`·15 `AdjustmentPosted`·16 `AgentSnapshot`·17 `LimitLedgerRebased`) | 넷은 엔진이 **내는** 형태라 `contract-only/`(엔진이 내지 않는 형태)의 정의에 맞지 않고, A-1 시나리오 밖이라 `a1/`(엔진 스트림으로 바뀔 자리)에도 맞지 않는다. 13종 전부를 덮는 샘플(Phase 0 C7)은 세 디렉터리를 합쳐 유지 |
| V12-2 | M4 "14 금액 양수·상태 `OFFSET`" | `amount` 1,200,000, `remaining` 350,000(그대로 크기만), `OFFSET` | 엔진 `ReceivableStatus.OFFSET` = 일부 상계(잔액 > 0) — 원래 샘플의 `PARTIALLY_OFFSET`과 같은 뜻 |

## 2026-10-17 — 계약 v1.3 (`docs/phase-02-계획승인.md` §3 — 승인이 선승인)

### 설계서 결함 (설계서 v1.4.2 변경 이력에도)

| # | 결함 | 처리 |
|---|---|---|
| DD-10 | §4.3 동기 API가 엔진이 계산에 쓰는 입력을 빠뜨렸다 — 요율 키의 보험사(`insurerCd`), 해약의 경과 회차(`elapsedInstallments` — 없으면 엔진은 개월 수로 근사해 회차 경계가 하루 차이로 바뀐다), 감액의 새 보험료(`newMonthlyPremium`). 한도 미적용 계약을 표현할 수 없게 `limitAmount`·`projectedAccum`을 필수로 했고, 결과 필드의 뜻과 오류 응답이 없었다(엔진 E2 계획 P14~P16) | R1~R8 — 전부 추가·완화·설명. `version` 1 그대로 |

### 해석

| # | 승인 문구 | 한 것 | 이유 |
|---|---|---|---|
| V13-1 | R1 "`insurerCd` 선택" | 없을 때의 뜻을 엔진 E2 계획 P15 그대로 적고, 그 422의 코드를 **`INSURER_AMBIGUOUS`**로 이름 붙였다 | 계획은 "아니면 422"라고만 했고 코드 이름이 없다 — Problem은 `code`가 필수다. 엔진 E2가 다른 이름을 쓰면 엔진 쪽이 계약을 따른다(계약은 포털 것) |
| V13-2 | Q13 "`samples/a1/` 10개 → 엔진 jsonl 하나" | 시험의 정규 스트림(`Samples.all()`)은 그 10건 뒤에 단건 예시 7개를 seq 11~17로 다시 매겨 붙인다 | 단건 예시의 seq는 예시값이라 a1과 겹친다(6·7). 해시는 seq를 보지 않아(Phase 1 Q1) 다시 매겨도 이벤트 해시는 같다. Phase 1 시험 스트림 10개는 그 정규 스트림에서 다시 만들었다(`StreamFilesTest` 규칙 그대로) |

## 2026-10-17 — Phase 2 계획 승인 (`docs/phase-02-계획승인.md`)

### 지시문 오류 (승인 §4)

| # | 지시문 | 무엇이 틀렸나 | 처리 |
|---|---|---|---|
| DE-9 | Phase 2 지시문 D-11 "커서 `REBUILDING` → 한 트랜잭션 안에서" | 같은 트랜잭션 안의 상태는 커밋 전까지 아무도 보지 못한다 — `REBUILDING`이 장식이 된다 | 트랜잭션 셋(txA `REBUILDING` 커밋 → txB 재생, 원자성은 여기 → 실패면 txC `FAILED(REBUILD_FAILED)`) — Q10 |
| DE-10 | C9 "플래그 매핑 10종" | `eventType`은 6종이다 | 시험 사례 10개로(Q12) |
| DE-11 | D-2·D-3의 V4 목록 | `earnings_month.net_basis`(확정 월의 세후가 예상인지 확정인지)·`recon_result.source_seq`(같은 시각의 확정·지급 DRIFT, 재구축 재삽입)를 빠뜨렸다 | V4에 둘 다(Q1·Q2) |

### 설계서 결함 (설계서 v1.5 변경 이력에도)

| # | 결함 | 처리 |
|---|---|---|
| DD-8 | §5 불변식 "`accum_paid ≤ limit_amount`"가 부록 A-3 감액 재산정(newLimit < accum)과 모순 — 글자대로면 재산정 뒤 첫 전기(음수 환수라도)에서 멀쩡한 테넌트가 멈춘다 | 엔진 `LimitLedger.post` 규칙: 양수 전기 뒤만 상한, 모든 전기 뒤 누적 ≥ 0, 재산정은 검사 없음(Q5). §5 문장 v1.5 |
| DD-9 | §6.2 분급 상태 그림이 `HELD → RELEASED`를 그렸다 — 엔진 `ScheduleEntry.released`는 `SCHEDULED`에서만 받는다 | 엔진 전이·사유 짝 그대로(Q8, `DeferralTransitions`). §6.2는 해당 단계에서 |


## 2026-10-17 — Phase 2 구현에서 계획과 다르게 한 것 (보고서 §5)

| # | 계획 | 구현 | 이유 |
|---|---|---|---|
| P2-1 | 뷰 행 타입은 `portal-projection` | **`portal-domain`의 `com.ga.portal.domain.view`**(`AgentRow`·`LineRow`·`MonthRow`·`LedgerRow`·`HistoryRow`·`DeferralRow`·`ReceivableRow`·`FlagRow`·`ReconRow`·`MonthSums`) | 의존 규칙 `infra`는 `app`만 — `portal-infra` 저장소가 행 타입을 받으려면 둘 다 보는 모듈에 있어야 한다. Phase 1 `CursorRecord`·`IngestEventRecord`와 같은 이유. JSONB 열은 거기서 JSON 문자열 |
| P2-2 | 골든 반례는 파일(`closed-month-line` 외 6개, 계획 ⑨) | **시험 코드가 A-1에서 한 줄을 바꾼다**(`ProjectionInvariantTest`·`ProjectionWorkerIT`·`RebuildIT`) | 반례마다 파일을 두면 A-1이 바뀔 때 일곱 파일을 같이 고쳐야 하고, 바꾼 줄이 시험 옆에 보이지 않는다. 정상 스트림 4개만 파일(출처 해시는 README) |
| P2-3 | 임시 골든 `a1-provisional.jsonl`(D-1) | **만들지 않았다** | 엔진 E2 ⓪a 파일(`a124621`)이 2단계 전에 와서 v1.3으로 바로 채택했다(6단계 "교체"가 할 일이 없다). 계획 값과 엔진 값의 차이는 0 — 보고서 §6 |
| P2-4 | `limit_multiple`은 원문 문자열 보존 | `NUMERIC(6,2)` 열(V1)이라 **소수 둘째 자리까지만** 되돌아온다 — 저장 `CAST(:s AS NUMERIC)`, 읽기 `trim_scale(…)::text` | 계약 `decimalString`은 소수 여섯 자리까지 허용한다(`^(0\|[1-9][0-9]*)(\.[0-9]{1,6})?$`). 엔진 값은 지금 `"12"`라 골든이 같다. 셋째 자리 이상은 PostgreSQL이 반올림하고, 끝의 0(`"12.50"`)은 `trim_scale`이 지워 원문과 바이트가 달라진다 — 열을 `TEXT`로 바꾸는 V 마이그레이션은 다음 Phase 질문(보고서 §8) |
| P2-5 | `IngestPollingIT`는 정규 시험 스트림(17건) | **엔진 A-1(10건)** + 월 행 단언 | 뷰 프로젝터가 조립되면 정규 스트림의 단건 예시(seq 11~17, A-1과 무관한 키)가 `UNKNOWN_REFERENCE`로 독이다. 폴링 경로를 보는 시험이라 스트림만 바꿨다 |
| P2-6 | `RecordingProjector`는 main에서 운영 조립만 빠진다 | `portal-projection` **testFixtures로 옮겼다** | 운영 클래스패스에 시험용 프로젝터를 남길 이유가 없다. Phase 1 IT는 명시적으로 넘긴다 |
| P2-7 | 감사 동작 `INGEST_REBUILD`·`INGEST_REBUILD_REJECTED` | **`INGEST_REBUILD_SUCCEEDED`·`INGEST_REBUILD_FAILED`** 추가(트랜잭션마다 한 행) | 재구축은 트랜잭션 셋이라 "시작"만 남기면 결과가 감사에 없다. P1-2와 같은 이유(전 건 감사) |
| P2-8 | 종료 코드 0·2·64 | **1 = 재구축 실패**(커서 `FAILED`, 뷰 그대로) | 거부(2 — 아무것도 바뀌지 않음)와 실패(커서가 바뀜)는 운영자가 할 일이 다르다 |
| P2-9 | txB 잠금 1회 | **짧은 재시도**(50 ms × 20) | txA 커밋 직전에 `RUNNING`을 읽은 워커의 적용 트랜잭션이 잠금을 잠깐 쥘 수 있다 — 그 트랜잭션은 `REBUILDING`을 보고 곧 끝난다. 실패한 `pg_try_advisory_xact_lock`은 트랜잭션을 깨지 않는다. 싼 경합은 문서로 남기지 않고 고친다 |
| P2-10 | 재생은 피드 끝까지 | **첫 페이지 `headSeq`까지**, 재생 수가 상한에 닿으면 실패, 그 `headSeq`·재생 끝이 이미 `APPLIED`인 최대 seq보다 작으면 실패 | 자동 보안 검토 두 건(`a65357a`·`f190741`): ⓐ 재생 중 늘어난 머리를 따라가면 상한이 txB를 묶지 못한다 ⓑ 피드가 뒤로 가거나 잘리면 커서가 `APPLIED` 행 아래에 서고, 워커가 그 행들을 아는 이벤트로 건너뛰어 뷰에서 영영 빠진다 |
| P2-11 | (계획에 없음) | 재생은 **알림을 파생하지 않는다** | Phase 6 전까지 no-op이고, `dedup_key`로 다시 불러도 되는지는 그 규칙을 볼 때 정한다(설계서 §14.1 #12) |
| P2-12 | `EventCheck`로 꺼냄(계획 ⑥) | 그대로 — 적용기는 `Known` → `SKIPPED`, 재생은 `Known`도 seq 연속을 요구하고 다시 투영 | 동작 무변경의 증거: Phase 1 `IngestApplierTest` 18건이 손대지 않고 통과 |

## 2026-10-17 — Phase 2 수용 심사 (`docs/phase-02-수용심사.md`)

### 심사 누락

| # | 무엇 | 처리 |
|---|---|---|
| RM-1 | 재구축의 F10 ⓑ(Phase 1 멱등 규칙 `SKIPPED(REPLAYED)`와 재구축의 상호작용 — 커서가 `APPLIED` 행 아래에 서면 워커가 그 행들을 건너뛰어 뷰에서 영영 빠진다)를 지시문·계획 승인이 보지 못했다. Phase 1 D-2를 승인할 때 "커서가 `APPLIED` 아래로 가는 경로"를 묻지 않았다 | 구현이 `f190741`에서 막았다(지우기 전·재생 끝 두 검사). 커서를 움직이는 새 경로(Phase 3 이후 운영 명령 등)는 같은 질문을 계획에서 답한다 |

### 결정 (다음 Phase로)

- `limit_multiple` → Phase 3 V5에서 `TEXT`(P2-4 해소). `AgentSnapshot.status` 닫기·A-1 둘째 판·엔진 잠정 명세 실 샘플·YAML `'no'` → 계약 v1.4(Phase 3 0단계, 심사 §3 N1~N6 — 선승인). `SYNC_DELAYED` 정의 → Phase 3 지시문(심사 Q7). 재생 중 알림은 파생하지 않는 방향(§14.1 #12), `REINSTATE` 열린 플래그 없음은 no-op 유지(§6.3).


## 2026-10-17 — 계약 v1.4 (`docs/phase-02-수용심사.md` §3 — 심사가 선승인, Phase 3 0단계)

### 설계서 결함 (설계서 v1.5.1 변경 이력에도)

| # | 무엇 | 처리 |
|---|---|---|
| DD-11 | 계약 YAML이 YAML 1.1 해석기에서 뜻이 바뀌는 키를 썼다 — `fyCommissionByInstallment[].no`가 1.1(SnakeYAML·PyYAML·Jackson 2)에서 불리언 `false`가 되어, 엔진 시험 픽스처에서 모든 신계약 시뮬레이션 응답이 스키마 검증에 실패했다(엔진 E2 §9-6). 포털 시험은 Jackson 3(SnakeYAML Engine, 1.2)로만 읽어 드러나지 않았다. 설계 시 해석기 차이를 보지 않았다 | 키를 `'no'`로(N1). `ContractYamlParsersTest`: 계약 YAML 전부를 1.1·1.2 두 해석기로 읽어 타입까지 같은 트리인지, 따옴표 없는 1.1 불리언 철자(`y/n/yes/no/on/off` 대소문자 — `true`·`false` 밖)가 없는지, 그리고 두 해석기가 그 글자들에서 실제로 갈리는지(비교가 공허하지 않음) |

### 해석

| # | 심사 문구 | 구현 | 이유 |
|---|---|---|---|
| V14-1 | N5 "`contract-only/07-StatementEstimated` → 둘째 판 마지막 줄(실 샘플)" | 파일을 **지웠다** — 실 샘플은 `a1/` seq 11 그 자체 | 같은 줄을 `contract-only/`에 복사하면 정규 스트림(`Samples.all()`)에 같은 eventId가 두 seq로 들어가 `EVENT_SEQ_CONFLICT`가 된다. `contract-only/`의 뜻("엔진이 내지 않는 형태")에도 맞지 않는다. 13종 덮기(Phase 0 C7)는 a1 seq 11이 맡는다 |
| V14-2 | N5 "`golden-streams/engine-e2-estimate-*/`" | 디렉터리 하나 `golden-streams/engine-e2-estimate/`에 엔진 파일 이름 그대로 다섯, 골든 뷰는 `golden-views/engine-e2-estimate/<파일 이름>/` | 출처 경로와 이름이 같아야 바이트 대조·추적이 쉽다 |
| V14-3 | N4 "골든 뷰 `a1` 재생성(202702 `ESTIMATED` 행 추가)" | 행이 늘지 않고 202702 행이 `PROVISIONAL` → `ESTIMATED`로 바뀐다(세액 0·순액 0·`net_basis ESTIMATE`·`estimate_basis_seq` 10) | 202702 행은 seq 9(환수 라인)에서 이미 있다. 잠정 명세의 `receivableOffset`·`carriedReceivable`(720,000)·`payable`은 지급 런 열에 넣지 않는다(Phase 2 계획 ② `StatementEstimated` — 예상 합계·이월은 쓰지 않음) — 화면에 예상 이월을 보일지는 Phase 4 질문 |

## 2026-10-17 — Phase 3 계획 승인 (`docs/phase-03-계획승인.md`)

### 지시문 오류 (승인 §3)

| # | 지시문 문구 | 무엇이 틀렸나 | 정한 것 |
|---|---|---|---|
| DE-12 | D-1 "테넌트는 토큰의 `iss`로", "`portal_directory`는 이 두 열을 읽지 못한다" | 테넌트를 바인딩하기 **전에** 발급자 열을 읽을 롤을 정하지 않았다 — `portal_app`은 RLS로 못 읽고 `portal_directory`는 못 읽게 했다 | 전용 롤 `portal_issuer`(세 열 SELECT + 전용 정책, V5) — 포털 직접 접근 허용 목록 첫 항목 `IssuerDirectoryReader`(Q1). 발급자 URL은 로그에 해시 |
| DE-13 | D-2·D-9·D-10 | `identity_link.agent_id NOT NULL`(V1)인데 `OPERATOR`에는 설계사가 없다. 플랫폼 0.2.0 `ResolvedIdentity`는 `orgPath` 공백에 예외를 던지는데 A-1 둘째 판에 `AgentSnapshot`이 없어 `agent_profile`에서 읽으면 A-1 설계사 전원이 404였다 | `agent_id` NULL은 `OPERATOR`만(CHECK), `identity_link.org_path`(포털 사실·인가용, `OPERATOR` 밖 필수) — `agent_profile.org_path`(엔진 사실·표시용)와 다른 출처, 달라도 어느 쪽도 고치지 않는다(Q2, 설계서 §14.1 #9) |
| DE-14 | D-6 "`now − 커서 updated_at` > `sync_delay_seconds`" | 유휴 폴링은 `updated_at`을 바꾸지 않는다 — 이벤트 없는 밤마다 모든 테넌트가 `SYNC_DELAYED`가 된다 | `ingest_cursor.caught_up_at`(V5): 피드 호출 성공 **그리고** `last_seq = headSeq` **그리고** `RUNNING`일 때만 잠금 안에서 갱신. `SYNC_DELAYED` ⇔ 상태 ≠ `RUNNING` ∨ `caught_up_at` 없음 ∨ `now − caught_up_at > 기준`(Q3) |
| DE-15 | D-7 "`policyKey` = SHA-256(…) 앞 16자(비가역)" | 비밀 없는 해시는 증권번호 형식이 좁아 열거로 되돌아가고, 키는 쿼리 문자열로 접근 로그에 남는다 | HMAC-SHA256(배포 비밀 파일, `tenant_id` + `U+001F` + `policy_no`) 앞 16자. 회전하면 모든 키가 바뀐다 — 화면은 키를 저장하지 않는다(`docs/운영.md` §4). 키 → 원문은 그 설계사의 증권번호 집합 안에서만(Q5) |
| DE-16 | D-8 "`ETag: W/"<seq>"`" | 같은 `seq`에서도 응답이 바뀐다 — 지연 상태(시간)와 테넌트 설정(지급일 규칙·임계치·지연 기준) | `W/"<seq>.<sync>.<cfg>"`, `cfg` = 그 응답이 실제로 읽은 설정의 지문, 라인 목록은 `.<page>.<size>.<필터>`. 비교는 소유 확인 뒤(Q4) |
| DE-17 | D-3 "감사 `audit_access` … detail = 사유 코드" | `audit_access`에 `detail` 열이 없고, 테넌트를 모르는 401은 감사 행을 쓸 곳이 없다. 등록된 `iss`만 실은 위조 토큰이 감사 표를 채울 수 있다(쓰기 증폭) | V5 `audit_access.detail`(닫힌 코드만). 404 → 감사(별도 트랜잭션), 401 → 지표만(Q6) |
| DE-18 | D-10 "`USING limit_multiple::text`", D-11 "모든 컨트롤러 메서드는 `@UseCaseEntry` 표식" | `NUMERIC(6,2)::text`는 `"12.00"` — 계약 원문 `"12"`와 다르다. 표식을 컨트롤러에 두면 ga-disclosure 6A(표식은 유스케이스 메서드)와 다르고, 컨트롤러가 유스케이스를 건너뛰어도 잡지 못한다 | `trim_scale(limit_multiple)::text`(F6, M14). 표식은 `portal-query`의 유스케이스 메서드, 컨트롤러는 그것을 정확히 하나 부른다(Q8 — `ApiLayerRulesTest` ⓐⓒⓓ) |

### 설계서 결함 (설계서 v1.6 변경 이력에도)

| # | 무엇 | 정한 것 |
|---|---|---|
| DD-12 | §7 공통 필드 `dataStatus: OK\|DRIFT\|SYNC_DELAYED` 한 필드가 두 뜻 — 동기화(테넌트 커서, 시간)와 대사(그 달의 `data_status`)를 섞었다. 지연 중인 DRIFT 달을 표현할 수 없다 | `dataStatus: OK\|SYNC_DELAYED`(모든 응답) + 월 응답의 `monthDataStatus: OK\|DRIFT`(지시문 D-6) |
| DD-13 | §7 `/me` "마스킹된 이름" — 본인에게 본인 이름을 가릴 이유가 없고, 설계서 §9의 마스킹 대상(고객)과 섞였다 | 본인 이름 그대로, `agent_profile` 행이 없으면 프로필 필드 생략 200(Q9) |

## 2026-10-17 — Phase 3 구현에서 계획과 다르게 한 것 (보고서 §5)

| # | 계획 | 구현 | 이유 |
|---|---|---|---|
| P3-1 | ② Spring `JwtIssuerAuthenticationManagerResolver`에 `IssuerTenantResolver`를 꽂는다 | **포털 `AuthenticationManager` 하나**(`IssuerTenantResolver`)가 검사 2~8을 계획 순서대로 하고, 발급자별 디코더만 캐시한다 | 그 리졸버는 형식 오류와 미등록 발급자를 같은 예외로 내고 `alg` 검사 전에 발급자를 찾는다 — 여덟 사유를 계획 순서로 가를 수 없다(바이트는 같아도 지표 사유가 틀린다) |
| P3-2 | ③ `Jwt` 멤버를 호출하는 클래스 셋의 닫힌 목록 | **하나**(`IssuerTenantResolver`) | 검증기가 클레임을 직접 읽고 (테넌트, `sub`)만 남긴다 — 읽는 곳이 적을수록 목록이 짧다. `ApiLayerRulesTest` ⓑ |
| P3-3 | `portal-infra` `persistence.query.AgentQueryRepository` | **`persistence.AgentQueryRepository`** | 뷰 저장소의 행 매퍼를 그대로 쓰려고(패키지 비공개로 넓혔다) — 매퍼를 복제하면 두 곳이 갈린다 |
| P3-4 | 조회 행 타입은 `portal-query` | **`portal-domain.view`**(`LineItem`·`LineDetail`·`LinePage`·`LedgerItem`·`DueMonth`·`SyncSnapshot`) | P2-1과 같은 이유 — `portal-infra`는 `portal-query`를 볼 수 없다 |
| P3-5 | 승인 Q6 "지표 `tenant` 태그는 발급자가 등록된 경우만" | 태그 키는 늘 있고 미등록 발급자는 값 **`none`** | Micrometer Prometheus 레지스트리는 같은 이름에 다른 태그 키 집합을 거부한다. `none`은 테넌트 ID 형식(`^[A-Z0-9]…`)과 겹칠 수 없다 |
| P3-6 | (계획에 없음) | 라인 목록은 그 설계사의 그 달 월 행이 없으면 **404** | `/earnings/{ym}`과 같은 자원 판단 — 같은 `ym`이 한 엔드포인트에서는 404, 다른 엔드포인트에서는 빈 200이 되지 않게 |
| P3-7 | (계획에 없음) | JWKS를 받지 못하면 **500**(401 아님) | 발급자 장애는 토큰의 잘못이 아니다 — 401이면 클라이언트가 재로그인을 되풀이한다. 본문은 같은 500 바이트 |
| P3-8 | V5 기존 행 채우기 | `ADD COLUMN … DEFAULT` + `DROP DEFAULT`(`UPDATE` 없음) | `TenantPredicateScanTest`가 `tenant_id` 술어 없는 `UPDATE`를 거부했다 — 규칙을 좁히지 않고 문장을 바꿨다 |
| P3-9 | (계획에 없음) | `TenantContextGuardTest`의 목록 = 패키지의 모든 저장소(코드 출처로 열거) | 목록 대조를 더하자 Phase 2의 `ViewResetRepository`가 빠져 있던 것이 드러났다 — 지금은 덮인다 |
| P3-10 | 자리 단계 E2E README 4번 "`ingest rebuild` 뒤 재대조", D-16 "`ingest_lag_seq = 0` 대기" | 재구축 재대조 없음, 대기는 DB의 `caught_up_at` | D-16 범위 밖이고 `RebuildIT`가 계약 사본으로 증명한다. actuator는 `health`만 노출 — `caught_up_at`이 같은 조건(`last_seq = headSeq`·`RUNNING`)이다 |

## 2026-10-17 — Phase 3 수용 심사 (`docs/phase-03-수용심사.md`)

### 지시문·승인 오류

| # | 문구 | 무엇이 틀렸나 | 정한 것 |
|---|---|---|---|
| DE-19 | 계획 승인 Q6 "지표 `tenant` 태그는 발급자가 등록된 경우만" | Micrometer Prometheus 레지스트리는 같은 이름에 다른 태그 키 집합을 거부한다 — 레지스트리 제약을 보지 않았다 | 태그 키는 늘 있고 미등록 발급자는 `none`(P3-5 그대로 — 심사 Q10). `none`은 테넌트 식별자가 아니다 |

### 심사 누락

| # | 무엇 | 처리 |
|---|---|---|
| RM-2 | Phase 2 심사가 `TenantContextGuardTest` 목록에서 `ViewResetRepository`가 빠진 것을 보지 못했다(Phase 3 F8) | Phase 3이 목록을 "패키지의 모든 저장소"(코드 출처로 열거)로 바꿨다(P3-9) — 다음 누락은 시험이 스스로 실패한다 |

### 플랫폼 질문

| # | 질문 | 처리 |
|---|---|---|
| PQ-1 | 플랫폼 0.2.0 `ResolvedIdentity`는 설계사 없는 주체(`OPERATOR`)를 표현하지 못한다(Phase 3 F14) | 지금은 포털 처리(`identity_link.agent_id` NULL은 `OPERATOR`만, 운영 명령 인가는 포털 코드)로 충분. ga-disclosure 다음 정비 때 `ResolvedIdentity`에 "설계사 없는 주체" 표현을 넣을지 |

### 결정 (설계서 v1.6.1)

- F5 라인 목록 404 → §7 한 줄, F9 `exp ≤ iat` → §9 각주, F6 JWKS 장애 → 지표 `api_jwks_failures_total{issuer_hash}`(이 커밋 — `IssuerTenantResolverTest`), Q11 → §14.1 "구현 위치" 열, Q3 → §14.1 #12, Q5 → §14.1 #13.
- Q12 E2E 결과는 `main`에 별도 커밋(사용자 실행 뒤). 실패하면 정비 PR.
- 심사 §3 Q1~Q6(화면 규약)은 Phase 4 지시문의 일부, Q7~Q9는 Phase 5.

## 2026-10-17 — Phase 4 구현에서 계획과 다르게 한 것 (보고서 §5)

| # | 계획 | 구현 | 이유 |
|---|---|---|---|
| P4-1 | 계획 ③ 판 표 eslint 10.13.0 | **10.12.0** | `pnpm-workspace.yaml`의 `minimumReleaseAge: 1440`(공개 1일 미만 판 금지)에 10.13.0이 걸렸다 — 규칙을 끄지 않고 판을 내렸다 |
| P4-2 | ④b `portal/no-money-arithmetic` — "피연산자 중 하나라도 `Won`이면" | 타입 이름 `Won` **또는 구조 표식**(`__@wonBrand@` 속성)으로 인식 | `openapi-fetch` 응답 타입(매핑 타입)을 거치면 별칭 이름이 사라진다 — 이름만 보면 `data.gross + 1`을 놓쳤다. 실제 `api.GET` 응답 타입을 쓰는 RuleTester 사례가 이름만 보는 규칙에서 실패한다 |
| P4-3 | ④b 비교 금지 | 있음/없음 확인(`!== undefined`)도 금지 그대로 — **`present()`**(`won.ts`)로 옮겼다 | 규칙 거짓 양성은 규칙을 좁히지 않고 코드를 옮긴다(`CLAUDE.md` 작업 방식) |
| P4-4 | ④b 캐스트 | `as`·`<T>` 단언으로 `Won`을 벗기는 것도 금지 | 주입 사례에서 `(x as unknown as number) + 1`이 통과했다 |
| P4-5 | ⑧ 표 "정정만 있는 달: `byType` 비어 있음" | **`byType` 키 ⊆ {`ADJUSTMENT`}**(비어 있음 포함) | GA03(엔진 E1 바이트 그대로)의 정정 달 202611은 `ADJUSTMENT` 라인을 가진다 — 계획 조건이면 실제 정정만 있는 달이 문구를 받지 못한다 |
| P4-6 | (계획에 없음) | 생성 클라이언트 `baseUrl` = `window.location.origin` | 상대 경로는 Vitest(Node `Request`)에서 거부된다. 브라우저에서는 같은 출처라 결과가 같다 |
| P4-7 | ⑨ E2E 센티널을 평문 스캔 목록에 | `pii-sentinels.properties`의 **새 키 `e2e`** | 기존 `identifiers`에 넣으면 `ResponseLeakScanIT`(Phase 3)가 E2E 전용 센티널을 자기 응답에서 찾다 실패한다 — Phase 3 시험은 그대로 두고 키를 나눴다 |
| P4-8 | ⑨ 키보드 시험 — 데스크톱 | 모바일 두 프로젝트에서는 `testIgnore`(건너뜀 0) | 건너뛰는 시험으로 두면 결과에 "skipped"가 남는다 — 스킵 0 원칙 |
| P4-9 | ⑦ 계약 요청 CR-1~3 | **CR-4** 추가(`Ledger.productName`·`productKey`·`insuredMasked`) | 한도 카드의 상품명·피보험자(설계서 §8)가 원장 응답에 없다 — 카드는 그 둘 없이 그린다 |
| P4-10 | ⑪ C9 W23 "큰 라이브러리(예: 전체 locale 데이터)" | `react-dom/server`를 진입 청크에 정적 import | locale 데이터 패키지는 의존성에 없다(설치하면 락이 바뀐다). 결과: 초기 로드 178,573 B > 상한 130,048 B로 실패, 복원 |
| P4-11 | ⑨ 로컬 실행 "`.state/` 파일의 로그인 URL" | `:web:e2eServe`(`run.mjs --serve`) — C 사본 테넌트의 피드를 끝까지 열고 `build/e2e/.state/login-urls.txt`(600)에만 쓴다 | 토큰을 터미널·인자·환경 변수에 싣지 않는다 |
| P4-12 | ⑤ 라벨 표 "세전·환수 상계·정정·라인 금액은 월 상태"(설계서 v1.7 §6.2) | 정정 금액(월 정정 합·`byType.ADJUSTMENT`·`ADJUSTMENT` 라인)은 **`adjustmentLabel()` — "지급 반영 미정"**, 월 상태와 무관(`phase-4.1` — 수용 심사 A1). 라인은 `lineLabel(commType, status)` 한 곳에서 고른다 | 엔진 E1-2: 정정은 마감·지급 런이 읽지 않는다 — PAID 달의 정정이 "지급완료"로 보였다. 문구는 심사 예시("정정 — 지급 반영 미정")에서 "정정 —"을 뺐다: 라벨은 늘 "정정" 행·유형 이름 옆에 붙는다("정정 -45,000원 정정 — …" 중복) |

### 결정 (설계서 v1.7)

- 화면 라벨 16칸 → §6.2, `DEFER_AFTER_FY` 문구 → §6.3, `REBUILDING` → §6.6, CR-1~4 → §7, 화면 구현 규약 → §8, 토큰 주입·화면 헤더 → §9, 홈 `status=ALL`·번들 상한 → §10, 스택 → §11, 알림함 Phase 6 → §12, 프론트 시험 → §13.
- §14.1 #14(웹뷰 호스트의 토큰 갱신 규약) — 기본 가정 "401이면 호스트가 프래그먼트로 다시 연다", `TODO(confirm#14)`는 `web/src/boot/token.ts`·`web/src/ui/States.tsx`.

## 2026-10-22 — Phase 4 수용 심사 (`docs/phase-04-수용심사.md`)

판정: 조건부 수용 — A1(정정 금액의 라벨)을 `phase-4.1`로 고친 뒤 병합. `phase-4` = `5bed1f1` 유지.

### 심사 누락

| # | 무엇 | 처리 |
|---|---|---|
| RM-3 | Phase 3 수용 Q3가 §14.1 #12("`ADJUSTMENT` 라인이 지급 런에 아직 포함되지 않은 달")를 "`ADJUSTMENT`만 있는 `CONFIRMED` 달"로 좁혀 적었다 — 설계서 v1.7 §6.2가 정정 금액을 월 상태 라벨로 둔 근거 | A1 → P4-12, 설계서 v1.7.1 §6.2·§8·§14.1 #12. PAID 달 + 정상 라인 + 정정 고정물(`screens.test.tsx`), 주입 W29 |

### 지시문 오류

| # | 문구 | 무엇이 틀렸나 | 정한 것 |
|---|---|---|---|
| DE-20 | 지시문 ⑦ "한도 카드 1(삭감 145,000)" | A-1 seq 3은 `DEFER_AFTER_FY` — 원장에서는 이연 145,000·삭감 0 | P-8 그대로 |
| DE-21 | 지시문 Q4 "다음 달 분급 예정액을 API가 주지 않는다" | `/me/deferrals.months[].expected`가 있다 | P-7 그대로 |

### 설계서 결함 (전부 v1.7에서 고쳐졌다)

| # | 무엇 | 처리 |
|---|---|---|
| DD-14 | §10 홈 콜 `status=WARN` — 정확히 WARN만 돌려준다 | `status=ALL` 한 콜(P-4) |
| DD-15 | §8 한도 상세의 규정 기준일·상품명·피보험자 — 계약 `Ledger`에 없다 | 그리지 않음(P-10·F9) → CR-3·CR-4 |
| DD-16 | §11 "PWA"·§12 Phase 4 알림함 | PWA는 v2, 알림함은 Phase 6(P-5·P-6) |

### 결정 (설계서 v1.7.1)

- 보고서 Q7(F5) 정정만 있는 달 = `CONFIRMED` + 키 ⊆ {`ADJUSTMENT`} + 정정 ≠ 0 — 그대로(빈 `byType`도 포함). A1 뒤에도 조건은 같다.
- Q8(F15) 영문 번역은 요청이 있을 때만 — 파일 머리의 원본 커밋이 낡음을 드러낸다.
- Q9(#14) 기본 가정은 Phase 8까지 유지. `location.reload()`는 혼자서는 토큰을 되살리지 못한다 — 호스트가 새로고침을 가로채야 한다(§14.1 #14 한 문장).
- F1·F2·F6~F14 수용, 계획 Q1~Q16·P-1~P-12는 사후 확인(뒤집은 것 없음 — 계획 승인을 사용자가 직접 한 Phase).
- Phase 5 재료(심사 §4): Q1 차트는 API의 막대별 표시용 비율 문자열 + CSS 막대 + 표(라이브러리 없음), Q2 `/me.features.forecastScenario`, Q3 입력 검증 정본은 API 400(화면은 계약의 `pattern`·`minimum`을 읽어 미리 막을 뿐), Q4 429 고정 바이트 + `Retry-After`·지표만·`simulation_log`에 쓰지 않음, Q5 V6는 `simulation_log`만(#13은 Phase 7 후보), Q6 `portal-api` 1.1.0 한 번에(시뮬레이션 + CR-1~4 + `features` + 비율 필드) — Phase 5 0단계 계약 PR.

## 2026-10-23 — Phase 5 계획 승인 (`docs/phase-05-계획.md`)

승인: 계획 Q1~Q24·P-1~P-26 권장안 전부(사용자 2026-10-23). Q17 — 엔진 샘플 PR은 자체 CI 통과 뒤 병합(엔진 #8 → `2c3b093`), Q18 — 계약 1.1.0은 `work/phase-5` 커밋으로(별도 PR 없음).

## 2026-10-23 — Phase 5 구현에서 계획과 다르게 한 것 (보고서 §5)

| # | 계획 | 구현 | 이유 |
|---|---|---|---|
| P5-1 | Q18 계약 1.1.0을 "첫 커밋"으로 | 두 커밋 — 기존 경로·CR-1~4·`features`(`856ece9`), POST 경로 둘(`610520a`) | `PortalApiContractTest`(경로 = 인가 표 키, 스키마 = 레코드)·`AuthzMatrixTest`·`clientCheck`가 계약을 코드에 묶는다 — POST 경로만 먼저 넣으면 인가 표·레코드 없이 빌드가 실패한다. 어느 커밋도 계약과 코드가 어긋난 채로 남지 않는다 |
| P5-2 | ⑥ 시뮬레이션 요청 본문 ≤ 2 KiB | 컨트롤러가 본문을 **상한+1바이트까지만** 스트림에서 읽는다(`byte[]` 전체 받기 대신) | 커밋 보안 검토: 상한을 넘는 본문을 다 받아 놓고 거부했다. `SimulationControllerTest`(끝없는 본문 → 2,049바이트만), `SimulationRejectionIT`(1 MiB → 400·행 0·호출 0) |
| P5-3 | ② 계약 1.1.0 | 설명문 여덟 곳에 따옴표(뜻 변화 없음) + `PortalApiContractTest.noKeyWithoutAValue` | YAML 흐름 매핑의 따옴표 없는 설명에 쉼표가 있어 설명이 잘리고 나머지가 값 없는 키가 됐다 — 규칙 파일을 만들다 발견. 주입 S16 |
| P5-4 | ④ `PolicyResolver.policyNo(Caller, key)` | 포트가 `(TenantId, AgentId, key)`를 받는다 | 규칙 (c)(`Caller`를 받는 공개 메서드는 진입점)가 포트 메서드를 잡았다 — 규칙을 좁히지 않고 코드를 옮겼다 |
| P5-5 | Q12 규칙 파일 = `pattern` | `pattern`·길이·필수·**`if/then`**(REDUCE면 `newMonthlyPremium`) — `when` 항목 | 조건부 필수를 화면에 손으로 다시 적지 않게(Phase 4 수용 §4 Q3) |
| P5-6 | ⑧ 막대 `aria-label="{월} 예정 {금액} · 보류 {금액}"` | `aria-labelledby` = 아래 표의 월·예정·보류 칸 | 금액을 문자열로 만들려면 `formatWon`을 `Amount` 밖에서 불러야 한다(화면 규약 — 금액은 `Amount`로만). 접근 이름은 같은 글자(라벨 포함)다 |
| P5-7 | ⑧ 시나리오 토글 | 두 버튼 분할 컨트롤(`aria-pressed`) | 체크박스는 20px 대상이다(E2E 44px 검사) |
| P5-8 | C11 S13 "시뮬레이션 화면을 정적 import" → 번들 상한 | `bundle-size.mjs`에 **지연 화면 닫힌 목록**(`LAZY_SCREENS`) — 정적 import·목록 밖 지연 청크면 실패 | 시뮬레이션 화면(3.2 KiB gzip)을 정적으로 넣어도 상한(127 KiB)을 넘지 않는다 — 상한만으로는 S13을 잡지 못한다 |
| P5-9 | ⑨ `ResponseLeakScanIT`에 시뮬레이션 두 경로 | `SimulationPassThroughIT`가 성공 응답마다 센티널 파일의 증권번호 전부를 본다(같은 원천) | 시뮬레이션 테넌트·엔진 스텁은 `SimIntegrationTest` 기반에 있다 — `ApiIntegrationTest` 테넌트에는 엔진이 없다 |
| P5-10 | Q19 "같은 코드면 같은 머리" | 같은 코드 안에서 비교(502 — 엔진 404·스키마 위반, 503 — 엔진 503·시간 초과) | Tomcat은 모든 503에 `Connection: close`를 붙인다(홉 머리) — 첫 시험이 502와 503을 비교해 CI에서 실패했다 |
| P5-11 | C4 S6 "키를 테넌트 전체 집합에서" | `policies()`의 설계사 술어를 무력화(`agent_id = :agentId OR true`) | 테넌트 전체 문장을 새로 쓰면 정적 스캔(`TenantPredicateScanTest` — 문장 수·설계사 술어)이 먼저 잡아 통합 시험에 닿지 않는다. 술어 글자는 남기고 뜻만 지워 행동 시험(`SimulationAuthzIT`)을 증거로 했다 — 두 겹 |
| P5-12 | ⑨ S8 "422 S11 시나리오와 함께(GA02)" | S8 API 요청은 **GA01** 명세 | `policyKey`는 그 설계사의 라인 증권번호 집합에서만 풀린다 — 엔진 샘플의 `POL-2026-0001` 라인은 GA01(A-1)에 있다 |
| P5-13 | (계획에 없음) | `CLAUDE.md` 화면 규약 "경로·쿼리에 `policyKey` 금지" → "**화면 주소**(경로·쿼리)에" | 승인된 Q15가 API 쿼리 `policyKey`를 더했다 — 규칙의 대상(화면 주소)을 글자로 밝혔을 뿐 뜻은 같다(`rule-file-corrections`) |
| P5-15 | ⑧ 계약 필터·계약 선택·변경 유형(목록 고르기) | `<select>` 대신 **누름 상태 버튼 묶음**(`ui/Choices.tsx` — 기간 고르기와 같은 모양) | 첫 E2E에서 WebKit(mobile-ios)만 `style-src-attr` CSP 위반을 냈다 — 화면의 select 수만큼(타임라인 1·시뮬레이션 2), select가 없는 화면은 0. CSP는 넓히지 않고 요소를 바꿨다. E2E의 CSP 표식 줄에 위반 요소 이름을 더했다 |
| P5-14 | (계획에 없음) | 시뮬레이션 타임아웃 설정에 환경 자리(`PORTAL_SIM_CONNECT_TIMEOUT`·`PORTAL_SIM_READ_TIMEOUT`) | `docs/운영.md` §6 — Spring 완화 바인딩의 이름(`…READTIMEOUT`)에 기대지 않는다 |
| P5-16 | 수용 심사 B1(`phase-5.1`) — `dueMonths`에 `HAVING`, "`max(expected + held) over ()`는 HAVING 뒤의 행으로", 계약 1.1.1, "GA04 골든·E2E" | `HAVING` 예정 ≠ 0 OR 보류 ≠ 0(한 문장 그대로 — `QueryStatementIT` 상한 그대로). 계약 1.1.1은 `info`와 `months` 설명문만 — `PortalApiCompatibilityTest`가 1.0.0·1.1.0(`phase-5` 바이트 사본, SHA-256 고정) 둘에 대한 추가·완화 대조 + **설명문을 뺀 두 문서가 같다**(1.1.0 → 1.1.1). `DeferralsQueryIT`에 취소만 있는 달(202811), E2E GA04 기대값(84개월 행 5 → 4, 2030-01은 항목에만). 주입 S17 | 막대 분모는 SQL 창 함수가 아니라 `MeQueries.timeline`이 **돌려받은 행에서** 계산한다 — `HAVING` 뒤 행만 보는 것이 구조상 성립(SQL에 `over ()`는 없다). 이 경로에는 골든 파일이 없다 — GA04의 E2E 기대값이 그 몫이다 |
| P5-17 | 수용 심사 F10(선택) — 동시 레이트 리밋 시험의 통과 셋을 샘플 안 요청으로 | 그 시험만의 테넌트 `BURST`(한도 3, 자기 엔진 스텁)에서 샘플의 설계사(A-1001)로 보낸다 — 통과 셋이 전부 200, 행 셋 `SUCCEEDED`, 엔진 호출 3 | 샘플의 설계사는 하나뿐이고 레이트 리밋 창은 (테넌트, 설계사)마다다 — `RL` 테넌트의 A-1001 창은 다른 시험이 쓴다. 테넌트를 나누면 창이 비어 시작한다 |

### 결정 (설계서 v1.8)

- 계약 1.1.0 → §7, V6 → §5, 곱셈 격리·막대·84개월·계약 필터 → §6.4, 포털 쪽 시뮬레이션 → §4.3, 화면 → §8, 거부 바이트·리밋·CSRF → §9, 리밋·관측·번들·카드 수 → §10, 스택(차트 라이브러리 없음) → §11, Phase 5 → §12, 시험 → §13, §14.1 #1(범위 밖)·#7(닫음)·#13(V7 후보).
- 영문 번역(`docs/en/`)은 v1.6 그대로(Phase 4 수용 Q8 — 요청이 있을 때만).

## 2026-10-23 — Phase 5 수용 심사 (`docs/phase-05-수용심사.md`)

판정: 조건부 수용 — B1(예정·보류가 모두 0인 달)을 `phase-5.1`로 고친 뒤 병합(범위 안이면 재심사 없이). `phase-5` = `0fccc05` 유지. 엔진 PR #8(`2c3b093`)은 선승인대로 — 엔진 main 소스 변경 0.

### 지시문 오류

| # | 문구 | 무엇이 틀렸나 | 정한 것 |
|---|---|---|---|
| DE-22 | 지시문 ②·⓪·① | `simulation_log`는 V1에 이미 있다(P-1), 해약 영향에 `generatedAt`이 없다(P-2), 포털 자바 금액은 `long`이라 타입 가름 불가(P-3·P-4), `utilization`은 SQL이 아니라 `Ratio`(P-5), `/me/deferrals` 상한 36(P-6), `portal-sim`이 `portal-query`의 진입 규약을 쓸 수 없다·토큰 빈은 인제스트 조건부(P-8·P-9), CR-3 규정 기준일·CR-4 상품은 원장에 없다(P-19·P-20) | 계획 그대로 |
| DE-23 | 지시문 ⑦ "`simId` 센티널 — 화면 0" ↔ ⑥ "결과에 `simId`", 질문 6 "라우터 `state`는 새로고침에 사라진다" | 서로 충돌(P-15), `history.state`에 남는다(P-13) | `simId`는 DOM에 보이되 주소·콘솔·저장소 0, 모듈 메모리 인계 |
| DE-24 | 지시문 §0 "계약 1.1.0은 별도 PR" | 계약 시험이 경로·레코드·생성 클라이언트를 코드에 묶어 계약만의 PR은 `main` CI에서 실패한다(P-26·F2·P5-1) | **앞으로 포털 계약 개정은 작업 브랜치 첫 커밋** |

### 설계서 결함

| # | 무엇 | 처리 |
|---|---|---|
| DD-17 | §8 "이번 달 실수령 변화" — 엔진 값은 귀속월 `gross + clawbackOffset` 전후(Q24). §8 "규정 기준일"은 어떤 이벤트에도 없다(DD-15 재확인) | v1.8에서 고쳤다(§8 해약 영향 줄, 금지 문구 "실수령") |

### 결정 (설계서 v1.8.1)

- B1 → P5-16: 타임라인 `months[]` = 예정 또는 보류가 있는 달(API에서 뺀다 — 화면이 숨기지 않는다). 계약 1.1.1(설명문만), §6.4·§7·§8.
- Q7(F14) `CLAUDE.md` "화면 주소(경로·쿼리)" 문구 그대로 확정 — 규칙의 대상은 처음부터 화면 주소였다(Phase 3 `/lines?policyKey`).
- Q8 실 엔진 E2E는 병합 뒤 `main`에서 사용자가 실행 → `RESULT.md`(판정 둘 — 피드·시뮬레이션 쌍).
- F2·F4·F5·F6·F12·F13·F15·F16 수용. F10 수용 + 선택 개선 → P5-17. 계획 Q1~Q24·P-1~P-26은 사후 확인(뒤집은 것 없음). 계획은 사용자 승인·심사에서 사후 확인 방식을 이어 간다 — 단, 돌이킬 수 없는 결정(계약 주 버전·마이그레이션 구조·공유 계약)이 계획에 있으면 승인 전에 심사로 보낸다.
- Phase 6 재료(심사 §4): Q1 알림은 **별도 파생기**(규칙 = `portal-projection` 순수 함수, 실행 = 인제스트 뒤 단계, 자기 커서 `notification_cursor(tenant_id, last_seq)` — 커서 전진과 알림 삽입 한 트랜잭션·테넌트 잠금, 재구축은 txB 끝에 커서만 `headSeq`로 옮기고 파생하지 않는다 — §14.1 #12 확정, 폴링 주기는 인제스트와 같다), Q2 dedup은 상태 표 없이 사실에서(`LIMIT_{STAGE}:{ledgerId}:{source_seq}` — 전후 단계는 `LimitStage` 비교뿐), Q3 이의제기 V7 `inquiry` + RLS(본문 ≤ 1,000자, 작성자 본인 `GET`에만, 운영 응답은 운영 CLI `inquiry answer` — 운영 화면은 v2), Q4 CSRF 토큰·`Origin` 검사 없음(시험: 쿠키를 실어도 `Authorization` 없으면 401 같은 바이트, 설계서 §9 근거 문단), Q6 계약 고르기는 검색 입력(Phase 6 화면 정비). 0단계 = 계약 1.2.0(작업 브랜치 첫 커밋, DE-24). 엔진 변경 없음.

## 2026-10-24 — Phase 6 계획 승인 (`docs/phase-06-계획승인.md`)

승인: 계획 Q1~Q30·P-1~P-23 권장안 전부 — Q1 알림 `params`에 금액 없음, Q25 재구축이 처음 적용한 사실의 알림은 없음(기록만 — 보고서 §5, Phase 7 질문, `docs/운영.md` 한 줄). 지시문이 되돌린 Phase 5 수용 §4 Q1(별도 파생기 + 자기 커서)은 **RM-4**: `ingest_event`에 payload가 없어 커서 뒤 파생기는 이벤트를 다시 받아야 한다 — 파생은 적용 트랜잭션 안, 프로젝터 다음(설계서 §6.5 원문).

추가 지시 A: 화면은 서버의 `link`를 `ROUTE_PATHS`의 넷(`/lines/:calcId`·`/limits/:ledgerId`·`/statements/:ym`·`/inquiries/:id`)에 대조해 맞는 것만 라우터에 넘기고, 아니면 `link` 없는 알림처럼 읽음만 한다(Vitest + 주입 S15).

### 지시문 오류

| # | 지시문 문구 | 무엇이 틀렸나 | 정한 것 |
|---|---|---|---|
| DE-25 | ⓪ "`oneOf`/`discriminator` 대신 `if/then` — 생성 클라이언트가 좁힐 수 있게" | `openapi-typescript` 7.13.0 측정이 반대다 — `if/then`은 `params: Record<string, never>`, `oneOf` + `discriminator`가 합 타입(P-4) | `oneOf` + `discriminator`(Q13) |
| DE-26 | ① "수신자는 이벤트의 `agentId`"·"`LIMIT_REBASE:…:{postingSeq}`", ② "`APPLY_ERROR`", ④ "Phase 5 `RateLimitRule` 재사용", ⓪·④ "429 재사용 — 같은 바이트", 형식 절 "1.2.0 = 첫 커밋", ⑦ GA01·GA04 기대 목록 | 세 이벤트에 `agentId` 없음(P-1), `postingSeq`는 선택·재산정마다 다르지 않음(P-2), 코드는 `PROJECTOR_ERROR`(P-3), 규칙은 `portal-sim`·창 상수(P-6), 429 문구가 "simulations"(P-7), 계약 시험이 경로를 코드에 묶는다(P-23 — P5-1과 같은 이유), A-1 seq 8 `POLICY_CANCEL` 누락·GA04에 재산정 없음(P-9) | Q14·Q15·Q16·Q12·Q21·Q30·Q26 |

DE-24 문구 정정(Q30): "앞으로 포털 계약 개정은 작업 브랜치 첫 커밋" → **"작업 브랜치 안, 그 경로를 섬기는 코드와 같은 커밋"**(별도 계약 PR 없음 — 뜻 그대로).

### 설계서 결함

| # | 무엇 | 처리 |
|---|---|---|
| DD-18 | §6.5 표 — `LIMIT_REBASE` 키의 `postingSeq`, 단계 ↔ 심각도 짝 미명시, DRIFT 달 조건 없음, `agentId` 없는 이벤트의 수신자 미명시(P-2·P-19·P-10·P-1) | v1.9 §6.5를 계획 ③ 최종 표로(키 = 이벤트 seq, `LIMIT_WARN` INFO·`LIMIT_CRIT` WARN·`LIMIT_FULL` CRIT, 확정·지급은 후 달 `DRIFT`이면 없음, 수신자 = 이벤트 또는 뷰 행의 엔진 `agentId`) |

## 2026-10-24 — Phase 6 구현에서 계획과 다르게 한 것 (보고서 §5)

| # | 계획 | 구현 | 이유 |
|---|---|---|---|
| P6-1 | ④ "id 형식이 틀린 경로는 감사 없이 404" | 형식이 틀린 id도 같은 404 + `AUTHZ_DENIED`(detail `MISSING`) | 기존 경로 키(`pathKey`)의 규약 — 남의 id·없는 id·형식 오류가 감사에서도 구별되지 않게(`NotificationApiIT` 넷이 같은 감사 줄) |
| P6-2 | ⑥ 쪽수 계산 | `Paging.offset`(허용 클래스)이 `AgentQueryRepository#lines`의 곱셈 허용 항목을 대신했다 | 곱셈 스캔이 새 쪽수 계산 둘을 잡았다 — 규칙을 넓히지 않고 곱셈을 한 클래스로 옮겼다(폐기 항목 실패 1회 기록 — 보고서 §4) |
| P6-3 | ⑧ 규칙 파일 `when` | `{field, in[], required}` 모양, 화면 검사기는 코드 포인트로 센다(서로게이트 짝) | 문의 분류(`OTHER` 밖은 `calcId` 필수)가 Phase 5의 단일 값 `when`으로는 안 된다. 린트가 문자열 전개를 막아 짝을 직접 센다 |
| P6-4 | ④ `?status=` | 빈 `?status=`는 기본값 `ALL`(Spring `defaultValue`) | `/limits?status=`와 같은 동작 — CI 40088726404에서 시험 기대값을 고쳤다(구현은 그대로) |
| P6-5 | ⑤ `NoopNotificationDeriver` | `portal-projection` 시험 픽스처로 옮기고 `IngestApplier`의 6인자 생성자를 지웠다 | 운영 코드가 조용히 파생 없음으로 조립될 길을 없앴다 — 시험만 명시적으로 넘긴다 |
| P6-6 | ⑤ 파생기 | 임계치는 테넌트 행에서 읽는다 — 행이 없으면 `APPLY_ERROR` | 조회 카드와 같은 문장(`SyncStatusRepository`). 실 파생기를 쓰는 시험은 테넌트 행을 넣는다 |
| P6-7 | ⑥ 답변 파일 | 끝 줄바꿈 **하나**(`\n` 또는 `\r\n`)를 뺀다 | 편집기가 붙이는 마지막 줄바꿈이 답변의 일부가 되지 않게. 둘 이상이면 뒤의 것은 본문이다 |
| P6-8 | ⑥ `inquiry list` 출력 | 설계사가 쓴 글의 제어·서식(양방향 등)·구분 문자·짝 없는 서로게이트를 `\uXXXX`(BMP 밖 `\UXXXXXXXX`)로, `calcId`도 같은 이스케이프 | 커밋 보안 검토(CWE-150 — 터미널 제어 문자 주입, `780d274`). 줄바꿈·탭·역슬래시만 막으면 ESC·양방향 문자가 운영자 터미널을 조작한다 |
| P6-9 | ④ 지표 `api_rate_limited_total{tenant}` | `{tenant, route}` — 시뮬레이션 `route="simulations"`, 문의 `route="inquiries"` | Prometheus는 한 이름에 태그 키 집합 하나만 받는다 — 문의 리밋을 같은 이름에 다른 태그로 낼 수 없다(설계서 §10 — 태그 값은 닫힌 두 값) |
| P6-10 | (계획에 없음) | 통합 시험의 계약 검사기가 경로를 **방법 + 경로**로 고른다, 한 인자 `check` 제거 | `GET`·`POST /inquiries`가 한 경로를 쓴다. 한 인자 판은 `EngineFeedE2eCheck`의 `POST`를 검사 없이 지나쳤을 것이다 |
| P6-11 | Q9 "`java -cp <하네스 클래스패스> PortalApplication`" | 하네스가 **bootJar**를 운영자와 같은 명령으로 띄운다(`java -jar <jar> --spring.profiles.active=cli …`, `run.mjs`가 `-Dga.portalJar`로 경로만) | 통합 시험 클래스패스에 `@TestConfiguration` 중첩 클래스(`QueryStatementIT.Recording`)가 있고, 시험 문맥 밖의 `PortalApplication` 컴포넌트 스캔은 그것을 빈으로 올린다(`TestTypeExcludeFilter`는 시험 문맥에만 있다). 자식의 표준 출력은 버리고 표준 오류만 하네스 로그로 |
| P6-12 | (계획에 없음) | E2E 재구축 제어는 종료 코드 2면 1초 간격으로 다섯 번까지 다시 친다 | 포털 워커가 그 순간 테넌트 잠금을 쥐면 CLI가 "being processed by a worker — try again"(2)로 거부한다 — 운영자가 하는 것과 같다. 인가 거부(역시 2)는 다섯 번 뒤에도 실패한다 |
| P6-13 | ⑨ E1 | E1을 `ga01-a1.spec.ts` 안에 넣었다(seq 2 단계를 앞에 더함) | 머리는 되돌리지 않는다 — 같은 프로젝트의 GA01 시험이 5·6·7·11을 차례로 연다 |
| P6-14 | ⑨ GA05 | 재산정이 한도를 누적과 같게 낮춘다(1,000,000 → 900,000 — 단계는 FULL) + 같은 단계 안의 전기 하나(seq 11 — 90%) | 재산정은 단계가 올라도 `LIMIT_REBASE` 하나(§6.5), 같은 단계 재알림 0(주입 S2의 E2E 증거)을 같은 스트림에서. `EnvelopeValidatorTest`가 손 스트림을 Docker 없이 계약으로 검증한다 |
| P6-15 | ⑨ E7 | 전체 실행이면 프로젝트마다 허용된 상세 응답에 본문 센티널이 실제로 실린 시험이 하나 이상이어야 한다(`run.mjs`) | 허용된 곳에 본문이 없으면 "그 밖 0"은 공허하다(CI 40091780371: 셋) |
| P6-16 | C7 거부 본문 | 따옴표 없는 토큰 본문(`Qv75문의센티널` — ASCII로 시작)을 더했다 | Jackson 3은 파싱 오류에 원문 위치를 숨긴다(`INCLUDE_SOURCE_IN_LOCATION` 꺼짐) — 여섯 거부 본문의 문구에는 입력 조각이 없어 "거부 경로가 예외 문구를 로그에"(주입 S8)를 시험이 못 잡는다. `Unrecognized token '…'`만 토큰 전체를 싣는데, **첫 바이트가 다중 바이트면** 파서는 "Invalid UTF-8 start byte"만 쓴다 — 첫 판(`문의센티널Qv75`, `45a32d4` — 커밋 문구의 "한글은 식별자 글자라 끊기지 않는다"는 틀렸다)은 그래서 S8을 놓쳤다(PR #22 — CI 통과). 둘째 판에서 ASCII로 시작하게 했다 |
| P6-18 | ⑨ E5 | 상세 화면에서 **앱 안 이동**(라인 링크 — 다시 불러오기 없음)으로 나간 화면도 검사한다(두 번) | E5는 상세를 늘 `portal.open`(새로 불러오기)으로 떠났다 — 새 문서가 제목·노드를 버리므로 "상세가 남긴 본문"(주입 S14 — `document.title`)을 못 잡았다(PR #25 — CI 통과) |
| P6-17 | ⑧ 문의 내역 | 항목 전체가 44px 링크(`.item__link`) | 첫 E2E(CI 40091780371)가 머리 줄 링크 92×19를 세 프로젝트에서 잡았다 |

### 결정 (설계서 v1.9)

- 계약 1.2.0 → §7, V7 → §5, `portal-inbox` → §3.3·§9 ⓖ, 파생 규칙·지점(RM-4)·재구축 → §6.5·§6.7, 운영 CLI → §6.1, 화면 → §8, CSRF 근거·본문 격리·거부 본문 → §9, 리밋·보존·홈 콜·지표 `route` → §10, Phase 6 → §12, 시험 → §13, §14.1 #3(다이제스트 — v2)·#12(재생 파생 — 닫음)·#13(V8 후보).
- `CLAUDE.md` "알림·문의 규약" 절, 의존 방향에 `inbox`, E2E 센티널 `inquiry` 키.
- 문의 보존기간은 설계서에 없다 — 정하지 않았다(`docs/운영.md` §7 — 다음 Phase 질문). 알림 보존은 1년(운영 SQL).
- 영문 번역(`docs/en/`)은 v1.6 그대로.

## 2026-10-24 — Phase 6 수용 심사 (`docs/phase-06-수용심사.md`)

판정: **수용, 조건 없음** — PR #20 병합(merge commit `b87db17`), `phase-6` = `f865034` 유지. F1~F13·P6-1~18 전부 수용. 새 RM·DE 없음(DE-25·DE-26·DD-18 그대로). 사용자 직접 계획 승인 세 번째 Phase — 사후 확인, 뒤집은 것 없음.

- 습관(조건 아님): 다음 Phase부터 본문 센티널 단언은 `.describedAs` 없이 `contains` 결과만 단언한다 — 실패 메시지가 잡은 출력을 되풀이하지 않게(F13).
- Phase 7 재료(심사 §3): Q1 `REPAIRED` 달의 확정·지급 알림은 **같은 키로 낸다** — 파생 지점은 재대사가 `REPAIRED`를 쓰는 트랜잭션(§6.5에 이벤트 아닌 둘째 파생 행), 재구축은 여전히 파생 0, 새 종류 없음. Q2 재구축이 처음 적용한 사실의 알림은 메우지 않는다 — §14.1 #12 "공백은 달 알림 범위에서만 메워진다". Q3 운영 알림 v1 = 지표 + CLI `status`(DRIFT 수·`APPLY_ERROR`·커서 정지·`REPAIRED` 실패·문의 적체 `inquiry_open_oldest_age_seconds{tenant}`·`inquiry_open_total{tenant}`), 이메일·웹훅 v2. Q4 지표 태그 지금 고정 — `notifications_derived_total{tenant, type}`, `inquiries_total{tenant, action}`(`CREATED`·`ANSWERED`·`CLOSED`), `recon_runs_total{tenant, result}`(`OK`·`DRIFT`·`REPAIRED`·`FAILED`). Q5 문의 보존 = 감사와 같은 기간(예시값 5년, `inquiry_retention_years`), 삭제 잡 없음. Q6 적용 트랜잭션 스팬 하나 + 프로젝터·파생·커서 자식 스팬, 속성에 값 0. 엔진은 `GET /internal/v1/agents/{agentId}/statements/{ym}`(계약 1.4.0) 사용만.
- 실 엔진 E2E는 병합 뒤 `main`에서 사용자가 실행 → `RESULT.md`(판정 넷).
