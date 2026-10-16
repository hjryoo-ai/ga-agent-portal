# 포털 Phase 1 계획 승인 (2026-10-17) — ga-agent-portal

## 판정: 승인. Q1~Q7 전부 권장안. CI-1 확인(내 문구가 틀렸다). 선행 소형 PR 하나 더(§3 — 엔진 E1 계획에서 나온 계약 완화 2건, 이 문서가 승인) 뒤 2단계 착수

읽은 것: `work/phase-1` = `cacca1f`의 `docs/phase-01-계획.md` 전문, PR #2 병합 `f30d026`의 계약 v1.1. 엔진 E1 계획(`work/e1` = `3fba123`)도 함께 읽었고 두 계획이 맞물리는 곳은 아래 §3·§4에.

## 1. 인정하는 것

- **④ 독 이벤트 분류의 판정 순서와 "기록 여부"를 가른 것.** 핵심 Envelope 위반(#1)·해시 불일치(#5)·seq 충돌(#6)에서 행을 남기지 않는 이유("키를 믿을 수 없다", "기존 `APPLIED` 행을 지킨다")가 각각 맞다. 표에 없는 조합은 거부하는 `IngestTransitions` 시험도.
- **⑤ J9가 실제로 무엇을 잡는지** — 잠금이 없으면 두 번째 워커가 PK 대기 뒤 고유 위반을 독으로 오판해 **멀쩡한 테넌트를 `FAILED`로 만든다**는 분석과, 래치로 경합을 실제로 만드는 시험. "중복 적용"만 보는 시험이었으면 놓쳤을 것이다.
- **CI-1** — 지시문 K7 "Σ `commType ≠ CLAWBACK`"을 글자대로 쓰지 않고 §5 정의(ADJUSTMENT도 제외)를 택한 것. `AdjustmentPosted`를 ADJUSTMENT 라인으로 저장하므로 글자대로면 정정이 두 번 센다 — 맞다. 엔진 E1 계획 P5가 같은 결론(엔진 `COMM_CALC`에는 ADJUSTMENT 유형이 없다)이라 양쪽이 일치한다.
- Q2의 "대안은 PK 때문에 불가"처럼 대안이 왜 안 되는지를 적은 것, Q6에서 규칙 (a)를 고치지 않고 어댑터를 `portal-app`에 둔 것.

## 2. 결정

| # | 결정 | 조건 |
|---|---|---|
| CI-1 | **확인.** `gross` = Σ `CommissionCalculated` 라인 중 `commType ∉ {CLAWBACK, ADJUSTMENT}` | 지시문 K7 문구 오류 — DECISIONS **DE-7**. 엔진 E1 D-6의 "`gross`(= `gross_net`)"도 같은 오류(엔진 쪽에 기록). |
| Q1 | **`aggregate` 포함** — `{type, version, occurredAt, aggregate, payload}` | 표의 "뺀다" 둘(`seq`·`eventId`·모르는 Envelope 필드)도 그대로. |
| Q2 | **`SKIPPED` 행 없음** | V1 열거의 `SKIPPED`는 미사용으로 남는다 — 설계서 §5 주석에 "(미사용 — Phase 1 Q2)". 지표 `ingest_events_total{result=skipped}`로만. |
| Q3 | **전용 롤 `portal_directory`** 승인 — `tenant`의 `tenant_id` **컬럼만** | `status`·`params`는 tx₀에서 그 테넌트 컨텍스트로 읽는다(계획대로 — 추가 GRANT 없음). 허용 목록 항목(FQN·사유)과 `TenantDirectoryIT`(다른 컬럼·표 권한 오류). 설계서 §9 롤 표에 행 추가. V2 롤 단언(`portal_%` NOBYPASSRLS)이 새 롤도 잡는지 확인. |
| Q4 | **401 → `PAUSED(FEED_UNAUTHORIZED)`** | 토큰 파일이 없거나 비면 폴링 전에 `PAUSED(TOKEN_MISSING)` — 상태 표에 한 행. |
| Q5 | **독 `EVENT_SEQ_CONFLICT`** | — |
| Q6 | **포트 `portal-ingest`, DB 어댑터 `portal-app`** | `IngestLockRepository`의 SQL은 표를 건드리지 않으므로 `TenantPredicateScanTest`가 보지 않는다 — 허용 목록이 아니라 "표 없음"으로 통과해야 하고 그 사실을 스캐너 시험에 한 건 추가(함수 호출만 있는 문장은 대상 아님). |
| Q7 | **테넌트별 파일** | 파일 권한이 소유자 외 읽기 가능하면 기동 시 경고(차단은 하지 않는다 — 컨테이너 마운트 환경마다 다르다). 값은 로그·예외·감사·`error`에 0(J10). |
| ③ 네트워크 오류·5xx | `RUNNING` 불변 — 승인 | 연속 실패 수를 지표로(`ingest_feed_errors_total`) — 알림은 Phase 7. |
| ⑥ `processResources` 사본 | 승인 | 클래스패스 = 저장소 바이트 시험 + 그 시험의 입력 선언(`verifyTestInputs`). |
| ⑨ CLI 인가 한계 | 승인 — README·보고서에 | — |
| O-1 `agent_profile.role` | **Phase 2**에서 새 V로 `NULL` 허용(기본값으로 `AGENT`를 지어내지 않는다). 화면은 "—" | Phase 2 계획에. |

## 3. 선행 소형 PR — 계약 v1.1.1 (`work/contracts-v1.1.1`, **이 문서가 승인**: CI 통과 뒤 병합, 병합 커밋을 엔진 E1에 전달)

엔진 E1 계획이 엔진 코드에서 확인한 사실 둘 때문에 계약을 한 번 더 완화한다. 전부 완화·설명이라 `version` 1 그대로.

| # | 변경 | 엔진 사실(E1 계획 P2·P6) |
|---|---|---|
| L1 | `LimitLedgerRebased.postingSeq` → **선택**(있으면 `minimum 1` 그대로 — "재산정 직전 마지막 전기 순번", 전기가 없던 원장이면 부재) | 재산정은 전기(DTL)가 아니라 순번이 없다. `LIMIT_LEDGER_HIST`는 어떤 코드도 쓰지 않는다 |
| L2 | `PayoutCompleted.paidAt` `description`: "지급 확정(정산행 기록·PAID 전이) 시각 — 이체 파일 실행 시각이 아니다" | 엔진은 이체 실행을 모른다. `paid_at`은 E1이 새로 만드는 `PAYOUT_STATEMENT`에 생긴다 |
| L3 | `CommissionCalculated.limitCutPolicy` `description`에: "룰의 `over_limit_action` 그대로. **현재 엔진은 `DEFER_AFTER_FY`도 삭감과 같게 동작하며 이연 스케줄을 만들지 않는다**(엔진 §14) — 포털은 `LIMIT_DEFER` 스케줄이 없어도 오류로 보지 않는다" | E1 계획 P1 |

샘플 06(`DeferralScheduled` reason `LIMIT_DEFER`)은 **계약이 허용하는 형태의 예시로 남긴다**(다른 공급원·미래 엔진) — 설계서 부록 A-1 seq 6에 "현재 엔진 미발행(E1 P1)" 주석. **Phase 2에 넘길 것(DD-5 후보)**: §5 `policy_limit_history` PK `(tenant_id, ledger_id, posting_seq)`는 REBASE가 직전 POST와 같은 `posting_seq`를 가지면 충돌한다 — 이력 정렬·키는 이벤트 `seq`로 바꿔야 한다. Phase 2 계획에서 다룬다.

`CHECKSUMS` 재생성, `EventSchemaTest`에 L1 변형(`postingSeq` 없는 `LimitLedgerRebased` 통과) 1건. 이 병합 커밋이 엔진 E1 `UPSTREAM`이다(`f30d026`을 대체). `work/phase-1`은 그 뒤 `main`을 merge.

## 4. 엔진 E1과 맞물리는 것 (Phase 1이 알아야 할 엔진 사실 — 설계서 반영은 Phase 2)

- 엔진은 `DeferralScheduled`를 **`CURVE`만** 낸다(P1). `HELD` 전이와 `POLICY_NOT_INFORCE` 사유는 어휘에 있으나 발행 여부는 E1 보고서에서.
- 정정(`AdjustmentPosted`)은 엔진에서 **지급되지 않는다**(P5) — `StatementConfirmed.adjustment`에는 들어오고 `PayoutCompleted`에는 없다. Phase 2 화면 규약("정정은 명세에 표시되나 지급 반영은 엔진 결함으로 미정")의 재료.
- `StatementConfirmed`에 `clawbackOffset`은 **들어온다**(마감 시점에 참) — 세액·순액만 없다.
- 엔진 시드로는 A-1 금액(FY 2,800,000)이 재현되지 않는다(1,890,000) — 포털 샘플은 **계약 예시**로 유지, 엔진 골든은 엔진 것. 두 숫자가 다른 이유를 부록 A-1 머리에 한 줄.

## 5. 지시문 오류 (DECISIONS)

- **DE-7** K7 `gross` 정의(ADJUSTMENT 누락) — CI-1.
- **DE-8** 지시문 D-2·D-4가 "아는 `eventId`가 다른 `seq`로 오는 경우"를 빠뜨림 — Q5.
- 지시문 §0에 L1~L3이 없었던 것은 엔진 E1 계획이 코드를 읽기 전이라 알 수 없었다 — 오류가 아니라 순서. 다만 §0 K-목록을 쓸 때 `LIMIT_LEDGER_HIST`·`paid_at`의 존재를 확인하지 않은 것은 기록.

## 6. 절차

계약 v1.1.1 PR(§3) → 병합 커밋 해시를 엔진 세션에 → 승인 보관 + 계획 갱신 1커밋(Q4 `TOKEN_MISSING`, Q6 스캐너 시험, Q3 롤 표, §4 메모) → 2단계. 주입 11건 + Q3 `TenantDirectoryIT` 주입 1건(롤에 다른 컬럼 GRANT → 실패) = 12. 보고서·`phase-1` 태그·PR까지, 병합은 수용 심사 뒤.
