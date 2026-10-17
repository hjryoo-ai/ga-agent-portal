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

