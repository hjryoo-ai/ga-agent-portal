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

### 결정 (설계서 v1.7)

- 화면 라벨 16칸 → §6.2, `DEFER_AFTER_FY` 문구 → §6.3, `REBUILDING` → §6.6, CR-1~4 → §7, 화면 구현 규약 → §8, 토큰 주입·화면 헤더 → §9, 홈 `status=ALL`·번들 상한 → §10, 스택 → §11, 알림함 Phase 6 → §12, 프론트 시험 → §13.
- §14.1 #14(웹뷰 호스트의 토큰 갱신 규약) — 기본 가정 "401이면 호스트가 프래그먼트로 다시 연다", `TODO(confirm#14)`는 `web/src/boot/token.ts`·`web/src/ui/States.tsx`.
