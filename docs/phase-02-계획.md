# Phase 2 계획 — 프로젝션 (지시문 `docs/phase-02-지시문.md` v1.0)

> 브랜치 `work/phase-2`(`main` = `3f876b6`, 계약 v1.2 병합 뒤에서). **승인 뒤 착수.** 승인 전에는 이 계획 커밋만 있다.
> 선행 §0 계약 v1.2: PR #5 병합 **`3f876b67a6991351f30b6276991c457a043572cf`**(CI run 38999090764, 2,417건) — 엔진 E2 `UPSTREAM`으로 전달했다.

## 승인 반영 (2026-10-17, `docs/phase-02-계획승인.md`) — 아래 본문보다 우선한다

Q1~Q13 전부 권장안. 계약 v1.3(A-1 채택 + 엔진 E2 요청 R1~R8)은 엔진 ⓪a 파일이 push되는 즉시 별도 소형 PR(`work/contracts-v1.3`, 승인 §3 — CI 뒤 병합)로 — Phase 2 단계와 병렬, Phase 2 PR은 그 병합을 merge하고 골든만 바꾼다.

| # | 반영 |
|---|---|
| Q1 | `net_basis` — 설계서 §6.2 표에 열 |
| Q3 | `basis.reason`은 엔진 자유 문자열(설계사·고객 값이 아니다) — 평문 스캔 대상 아님을 README에 |
| Q4 | 설계서 §4.2 표 "효과" 열 정정(불변 4종 insert·`DUPLICATE_KEY`, 상태 3종 upsert) |
| Q5 | **DD-8** §5 불변식 |
| Q8 | **DD-9** §6.2 분급 그림(`HELD→RELEASED` 없음) |
| Q11 | 셋째 골든 = 엔진 E2 `estimate_cycle_*.jsonl`(E2 뒤 — Phase 3 0단계에서 교체). Phase 2의 C6은 `a1-estimated` 손 스트림으로 닫는다 |
| E-2 | 설계서 §6.3에 "현재 엔진은 실제로 삭감(E1-1)" 한 줄 |
| 지시문 오류 | **DE-9**(D-11 한 트랜잭션), **DE-10**(C9 10종), **DE-11**(V4 목록에 `net_basis`·`source_seq` 없음), **DD-10**(§4.3 동기 API 입력 — v1.3 PR) |
| 순서 | 2~5단계(엔진 파일과 무관) → ⓪a 도착 시 v1.3 PR(병렬) → 6단계 교체 → 7단계 |

## 0. 먼저 결정이 필요한 것 (요약)

| # | 질문 | 권장 | 대안과 그것이 안 되는 이유 |
|---|---|---|---|
| Q1 | CONFIRMED 월의 세후가 **예상인지 확정인지**를 행에서 알 수 있어야 한다(D-2 라벨 "확정(세후 예상)") | V4에 `earnings_month.net_basis TEXT`(`ESTIMATE`/`CONFIRM`/`PAYOUT`, NULL = 세후 없음) — 세액·순액을 마지막으로 쓴 이벤트 | ⓑ 규칙으로 유도("CONFIRMED면 늘 예상") — 엔진 v1은 마감 이벤트에 세액을 싣지 않지만 계약은 허용한다(v1.1 K4). 실리는 순간 라벨이 틀린다. ⓒ 확정 세액이 오면 `estimate_as_of`를 지운다 — 예상의 기록이 사라진다. **지시문 V4 목록 밖 컬럼 하나** |
| Q2 | `recon_result`의 **키 충돌** — 같은 `(agent, closeYm)`에서 `StatementConfirmed`·`PayoutCompleted`가 둘 다 DRIFT이고 `occurredAt`이 같으면 PK `(…, run_at)`이 충돌한다(엔진 골든은 고정 시계라 **모든 이벤트의 `occurredAt`이 같다**). 재구축은 `recon_result`를 지우지 않으므로(D-11) 같은 행을 다시 넣는다 | V4: `source`(`EVENT`/`BATCH`) + **`source_seq BIGINT NOT NULL`**(EVENT = 이벤트 `seq`, BATCH = 0), PK `(tenant_id, agent_id, close_ym, run_at, source, source_seq)`. 쓰기는 `ON CONFLICT DO NOTHING`(재구축·재적용 멱등) | ⓑ `source`만 더하고 `ON CONFLICT DO NOTHING` — 같은 시각의 지급 DRIFT가 조용히 사라진다(`engine_net` 대조 손실). ⓒ `run_at`을 수신 시각으로 — 결정론(D-12)이 깨진다. **지시문 V4 목록 밖 컬럼 하나** |
| Q3 | `AdjustmentPosted`에는 `policyNo`·`productKey`가 없다 — `earnings_line.policy_no`는 `NOT NULL` | `targetCalcId`의 라인에서 `policy_no`·`product_key`·`policy_summary`를 복사(읽기 전용 교차 참조, D-6이 허용). `basis` = `{"targetCalcId", "reason"}`(사유 원문 — 엔진 자유 문자열, 로그·예외에는 쓰지 않는다). 대상 라인이 없으면 **독 `UNKNOWN_REFERENCE`** | ⓑ `policy_no`에 표지값 — 화면의 계약별 묶음이 틀린다. ⓒ V4로 NULL 허용 — 지시문 밖, 인덱스 `ix_line_policy`가 의미를 잃는다. 대상은 늘 앞선다(엔진: 정정은 마감 뒤, `seq` 0부터 받는 전제 — 수용 심사 §3-2) |
| Q4 | 같은 키의 **두 번째 사실**(같은 `calcId`·`ledgerId`·`scheduleId`·`adjId`) | 불변 사실 4종(`CommissionCalculated`·`AdjustmentPosted`·`LimitLedgerOpened`·`DeferralScheduled`)은 **독 `DUPLICATE_KEY`**, 상태 사실 3종(`AgentSnapshot`·`ClawbackReceivableUpdated`·`PolicyEventObserved`)은 upsert | ⓑ 설계서 §4.2 표대로 전부 upsert — 덮어쓰면 월 합산에서 옛 값을 빼야 하고(라인 금액이 바뀐 이유는 엔진에 없다: `COMM_CALC`는 불변, 정정은 reversal·ADJUSTMENT), 조용히 틀린다. 같은 이벤트의 재전송은 인제스트가 이미 걸러낸다(eventId) |
| Q5 | 설계서 §5 불변식 "`accum_paid ≤ limit_amount`"는 **감액 재산정(A-3, newLimit < accum)과 모순**된다 | 엔진 규칙 그대로: **양수 전기만** `accum_after ≤ limit_amount`, 모든 전기 `accum_after ≥ 0`(엔진 `LimitLedger.post` 행 95~107), 재산정은 검사 없음. 설계서 §5 문장 정정 — 결함 **DD-8** 후보 | ⓑ 글자대로 — A-3 재산정 뒤 첫 이벤트(음수 환수 전기라도)에서 멀쩡한 테넌트가 멈춘다 |
| Q6 | `postingSeq` 연속성 | `LimitLedgerPosted.postingSeq = last_posting_seq + 1` 아니면 독 `STATE_MISMATCH`, `LimitLedgerRebased.postingSeq`(있으면) `= last_posting_seq`, `oldLimit = limit_amount` 아니면 독 | 엔진 사실: 원장마다 `MAX+1` 채번, 재산정은 "적재 때의 마지막 전기 순번"(`OutboxLimitLedgerStore` 행 25~28). ⓑ 단조 증가만 — 빠진 전기를 못 본다(`accum_after` 연쇄가 잡지만 원인이 흐려진다) |
| Q7 | 월 상태 전이에 표에 없는 조합 | 아래 ③ 표 밖은 독(`STATE_MISMATCH` 또는 `CLOSED_MONTH`) — 특히 마감월의 `StatementEstimated`, `CONFIRMED` 전의 `PayoutCompleted`, `PAID` 뒤 두 번째 `PayoutCompleted` | ⓑ 무시(건너뜀) — 발행자 이상을 조용히 삼킨다. 엔진 E2 지시문 ①은 "마감된 달에는 내지 않는다", E1은 "같은 달 반복 지급 런은 새 이벤트 없음"이다 |
| Q8 | 분급 전이 표·`to`↔`reason` 짝 | 엔진 `ScheduleEntry`(행 42~60) 그대로: `SCHEDULED→RELEASED/HELD/CANCELLED`, `HELD→CANCELLED`. 짝(v1.2 닫힌 어휘): `RELEASED↔DUE_RELEASED`, `HELD↔POLICY_NOT_INFORCE`, `CANCELLED↔{POLICY_TERMINATED, SOURCE_REVERSED}` — 어긋나면 독 `STATE_MISMATCH` | ⓑ 설계서 §6.2 그림대로 `HELD→RELEASED`도 — 엔진이 거부하는 전이(`requireStatus(SCHEDULED)`)라 오면 발행자 이상이다 |
| Q9 | `data_status = REBUILDING`을 쓰나 | **쓰지 않는다**(V1 열거에 남는다 — `ingest_event.status`의 `SKIPPED`처럼 "미사용"을 설계서 §5에). 재구축 중이라는 신호는 커서 `REBUILDING`(Phase 3 응답 `SYNC_DELAYED`의 재료) | ⓑ 재구축 시작 때 그 테넌트 월 행을 `REBUILDING`으로 — 실패 시 원래 `OK`/`DRIFT`를 되돌릴 기록이 없고, 성공 시 새 행이 덮으니 의미가 없다 |
| Q10 | D-11 "커서 `REBUILDING` → **한 트랜잭션** 안에서 재생" — 같은 트랜잭션의 `REBUILDING`은 커밋 전까지 아무도 못 본다 | 트랜잭션 셋: **txA** 잠금 → `REBUILDING` 커밋(워커·운영자가 보고 비켜 간다) → **txB** 잠금 → 뷰 DELETE → 재생 → 불변식 → `RUNNING` 커밋(한 트랜잭션 — D-11의 원자성 그대로) → 실패면 txB 롤백 + **txC** `FAILED(REBUILD_FAILED)` | ⓑ 글자대로 하나 — `REBUILDING`이 관측되지 않아 상태 기계의 그 상태가 장식이 된다. 원자성(실패 시 아무것도 안 바뀜)은 txB가 그대로 준다 |
| Q11 | 다른 이벤트 원천 — 엔진 E1 골든 `cycle_마감_지급_채권_정정.jsonl`(19건: 채권 OPEN→CLOSED, 정정 → 정정만 있는 월의 `StatementConfirmed`, 음수 순액 채권화, 원장 둘) | **골든 스트림 둘째로 들인다**(엔진 `e1` = `826b3e4`의 파일 바이트 그대로 + 출처 해시를 README에). A-1이 덮지 않는 `AdjustmentPosted`·`ClawbackReceivableUpdated`·`receivableOffset`·`carriedReceivable`을 엔진 바이트로 시험한다 | ⓑ 포털이 손으로 만든 스트림만 — 엔진이 실제로 내는 모양(같은 `occurredAt`, 숫자 `ledgerId`, `limitCutPolicy`는 삭감이 있을 때만)과 어긋날 수 있다. 계약 파일이 아니라 시험 자원이므로 `UPSTREAM`·계약 변경 없음 |
| Q12 | C9 "플래그 매핑 **10종**" — `eventType`은 6종이다 | 시험 사례 10개로 읽는다: 매핑 5(CANCEL·LAPSE·WITHDRAW·REDUCE·PAYMENT_OVERDUE) + REINSTATE 해제 1 + 같은 플래그 재수신 upsert 1 + 해제 뒤 재플래그 1 + WITHDRAW 뒤 CANCEL(같은 `CANCELLED`, `reason` 갱신) 1 + 다른 설계사 같은 계약 1 | 지시문 숫자 오류일 수 있다 — 확인 요청 |
| Q13 | D-1 엔진 파일을 `samples/a1/`로 들이는 것은 계약 샘플 변경이다 — 엔진 `UPSTREAM`이 다시 낡는다 | **계약 v1.2.1 소형 PR**(지시문 §0과 같은 절차 — 자체 CI 뒤 병합, 병합 커밋을 엔진에): `samples/a1/`의 10개 파일 → 엔진 jsonl 하나 바이트 그대로, `EventSchemaTest`가 jsonl 줄도 읽게. Phase 2 PR은 그 병합을 merge하고 골든만 바꾼다 | ⓑ Phase 2 PR 안에서 — Phase 2 병합(수용 심사 뒤)까지 엔진이 따라올 커밋이 없다. ⓒ 시험 자원으로만 두고 샘플은 그대로 — D-1 "`samples/a1/`로"와 다르고 계약 샘플이 엔진 바이트와 계속 어긋난다 |

나머지는 지시문 §1 결정 그대로다. 아래 본문은 위 권장안을 전제로 쓴다.

## ① 엔진 사실 (E1 `826b3e4` 코드·골든에서 확인 — 지시문·설계서와 다른 곳)

| # | 사실(파일:행) | Phase 2에 미치는 것 |
|---|---|---|
| E-1 | 엔진 골든 스트림은 **고정 시계** — 한 스트림의 모든 `occurredAt`이 같다(`outbox-golden/*.jsonl`) | Q2. 정렬·키에 `occurredAt`을 쓰지 않는다(`source_seq`로) |
| E-2 | `limitCutPolicy`는 **삭감이 있을 때만** 있다(07 골든 seq 7 `DEFER_AFTER_FY`), 시드의 `over_limit_action` = `DEFER_AFTER_FY` | A-1(월납 150,000) 시책 삭감 145,000은 `deferred_total`로 간다(D-7 규칙대로) — 실제 동작은 삭감(L3). 화면 문구는 Phase 4 |
| E-3 | `LimitLedgerOpened`는 원장을 처음 만들 때(`getOrCreate` 직전 `find`가 빈 때), 같은 계산 트랜잭션에서 `COMM_CALC`보다 앞선다(`OutboxLimitLedgerStore` 행 23) | `limitCutAmt > 0` 라인이 왔는데 `(policy, agent)` 원장이 없으면 독 `UNKNOWN_REFERENCE` |
| E-4 | 원장 불변식: 양수 전기 `accum ≤ limit`, 누적 ≥ 0, 재산정은 누적 유지(`LimitLedger.post`·`reprice`) | Q5 |
| E-5 | 분급 전이(`ScheduleEntry` 행 42~60) | Q8 |
| E-6 | 채권: `remaining ∈ [0, amount]`, 상태 `OPEN→OFFSET→CLOSED`, `WRITTEN_OFF`는 `CLOSED` 아닌 데서(`ClawbackReceivable` 행 33~64). 상계 없이 `OPEN→CLOSED`도 있다(cycle seq 10→17) | 채권 검사: `amount` 불변, `remaining` 비증가, `remaining ≤ amount`, `CLOSED ⇒ remaining = 0`, 종결 상태(`CLOSED`·`WRITTEN_OFF`) 뒤 갱신 없음 — 어기면 독 `STATE_MISMATCH` |
| E-7 | 정정만 있는 설계사도 그 달 `StatementConfirmed`가 온다(E1 수용 Q1, cycle seq 19 `gross 0`·`adjustment −45,000`) | 마감 이벤트가 그 달 `earnings_month` 행을 **만들 수 있다**(라인 없는 달) |
| E-8 | `PayoutCompleted`에 `adjustment`가 없다(E1 P5 — 정정 미지급) | D-3 대조는 **있는 필드만** |

## ② 이벤트 → 테이블·컬럼 매핑 (필드 하나하나)

표기: `p.x` = payload 필드, `e.seq`·`e.occurredAt` = Envelope. 모든 행에 `tenant_id` = 바인딩된 테넌트. "검사"는 독 판정(③·④).

**`AgentSnapshot`** → `agent_profile` upsert(키 `agent_id`)

| 컬럼 | 값 |
|---|---|
| `agent_id` | `p.agentId` |
| `name` | `p.name` |
| `grade` | `p.grade`(없으면 NULL) |
| `org_path` | `p.orgPath` 그대로(D-8) |
| `role` | `p.role`(없으면 NULL — V4 NULL 허용) |
| `status` | `p.status`(V4 CHECK `ACTIVE`/`DISMISSED` — 밖이면 계약이 아니라 DB가 거부 → `APPLY_ERROR`. 계약은 아직 열린 문자열이라 프로젝터가 먼저 `STATE_MISMATCH`로 거른다) |
| `valid_from` | `p.validFrom` |
| `last_seq` | `e.seq` |
| (버림) | `p.nameMasked`(포털은 표시 시점에 마스킹 — §14.1 #5) |

**`CommissionCalculated`** → `earnings_line` insert(키 `calc_id` — 있으면 `DUPLICATE_KEY`), `earnings_month` upsert, `policy_limit` 갱신(삭감이 있을 때)

| 컬럼 | 값 |
|---|---|
| `earnings_line.calc_id` | `p.calcId` |
| `agent_id`·`policy_no`·`product_key`·`comm_type`·`amount`·`close_ym` | `p.agentId`·`p.policyNo`·`p.productKey`·`p.commType`(열린 값)·`p.amount`·`p.closeYm` |
| `reversal_of_calc_id` | `p.reversalOfCalcId`(없으면 NULL) |
| `limit_cut_amt`·`limit_cut_policy` | `p.limitCutAmt`·`p.limitCutPolicy`(없으면 NULL) |
| `basis` | `p.basis` 원문(JSONB — `rate`·`payoutRate`는 불투명 문자열) |
| `policy_summary` | `p.policySummary` 원문 |
| `source_seq` | `e.seq` |
| `earnings_month(agent, closeYm)` | 없으면 `PROVISIONAL`·합 0으로 만들고 `gross += amount`(comm_type ∉ {CLAWBACK, ADJUSTMENT}) 또는 `clawback_offset += amount`(CLAWBACK) — 전 상태 합산(reversal 포함), `last_seq = e.seq`. 상태는 바꾸지 않는다(ESTIMATED인 달에 새 라인 → ESTIMATED 그대로, `estimate_as_of`가 낡음을 말한다) |
| `policy_limit(policy_no, agent_id)` | `limit_cut_amt > 0`일 때만: `CUT` → `cut_total += limit_cut_amt`, `DEFER_AFTER_FY` → `deferred_total += limit_cut_amt`(D-7). 원장 없음 → `UNKNOWN_REFERENCE`(E-3) |
| 검사 | 마감월(D-4 `CLOSED_MONTH`), 월 합산 = Σ 라인(D-13) |

**`AdjustmentPosted`** → `earnings_line` insert(키 `ADJ:` + `p.adjId`, D-5), `earnings_month` upsert

| 컬럼 | 값 |
|---|---|
| `calc_id` | `"ADJ:" + p.adjId` |
| `agent_id`·`amount`·`close_ym` | `p.agentId`·`p.amount`·`p.closeYm`(익월 귀속) |
| `comm_type` | `ADJUSTMENT` |
| `policy_no`·`product_key`·`policy_summary` | `p.targetCalcId` 라인의 것(Q3 — 없으면 `UNKNOWN_REFERENCE`) |
| `reversal_of_calc_id`·`limit_cut_policy` | NULL |
| `limit_cut_amt` | 0 |
| `basis` | `{"targetCalcId": p.targetCalcId, "reason": p.reason}` |
| `source_seq` | `e.seq` |
| `earnings_month` | `adjustment += amount` |
| 검사 | 마감월(D-4), 월 합산(D-13), `DUPLICATE_KEY` |

**`LimitLedgerOpened`** → `policy_limit` insert(키 `ledger_id` — 있으면 `DUPLICATE_KEY`; `(policy_no, agent_id)` UNIQUE 위반도 `DUPLICATE_KEY`로 먼저 판정)

| 컬럼 | 값 |
|---|---|
| `ledger_id`·`agent_id`·`policy_no`·`contract_date`·`fy_start`·`fy_end`·`monthly_premium`·`limit_amount`·`rule_version_id` | payload 같은 이름 |
| `limit_multiple` | `p.limitMultiple` 문자열을 SQL에 그대로 넘겨 `NUMERIC`으로(포털 코드에서 숫자로 바꾸지 않는다 — Phase 0 승인 Q2) |
| `accum_paid`·`cut_total`·`deferred_total`·`last_posting_seq` | 0 |

**`LimitLedgerPosted`** → `policy_limit` 갱신, `policy_limit_history` insert. **`earnings_line`을 요구하지 않는다**(D-6)

| 컬럼 | 값 |
|---|---|
| `policy_limit.accum_paid` | `p.accumAfter`(엔진 값 그대로 — 포털 합산 금지) |
| `last_posting_seq` | `p.postingSeq` |
| `history.source_seq`(PK) | `e.seq` |
| `posting_seq`·`calc_id`·`amount`·`accum_after` | `p.postingSeq`·`p.calcId`·`p.amount`·`p.accumAfter` |
| `kind` | `POST` |
| `limit_after` | 그때의 `limit_amount` |
| `reason` | NULL |
| `occurred_at` | `e.occurredAt` |
| 검사 | 원장 있음(`UNKNOWN_REFERENCE`), `postingSeq = last + 1`(Q6), `accum_after − accum_paid = amount`(D-13, K5), `amount > 0 ⇒ accum_after ≤ limit_amount`, `accum_after ≥ 0`(Q5) |

**`LimitLedgerRebased`** → `policy_limit` 갱신, `policy_limit_history` insert

| 컬럼 | 값 |
|---|---|
| `policy_limit.limit_amount`·`monthly_premium` | `p.newLimit`·`p.monthlyPremium` |
| `history.source_seq`·`kind`·`posting_seq` | `e.seq`·`REBASE`·`p.postingSeq`(없으면 NULL — V4) |
| `amount`·`calc_id` | NULL |
| `accum_after` | 그때의 `accum_paid`(재산정은 누적을 바꾸지 않는다) |
| `limit_after`·`reason`·`occurred_at` | `p.newLimit`·`p.reason`·`e.occurredAt` |
| 검사 | 원장 있음, `p.oldLimit = limit_amount`, `p.postingSeq`(있으면) `= last_posting_seq`(Q6) |

**`DeferralScheduled`** → `deferral_item` insert(있으면 `DUPLICATE_KEY`)

| 컬럼 | 값 |
|---|---|
| `schedule_id`·`agent_id`·`policy_no`·`source_calc_id`·`due_ym`·`amount`·`pay_condition`·`curve_version_id`·`reason` | payload 같은 이름(`reason` CURVE/LIMIT_DEFER) |
| `status` | `SCHEDULED` |
| `status_reason` | NULL |
| `last_seq` | `e.seq` |

**`DeferralStatusChanged`** → `deferral_item` 갱신

| 컬럼 | 값 |
|---|---|
| `status`·`status_reason`·`last_seq` | `p.to`·`p.reason`·`e.seq` |
| 검사 | 있음(`UNKNOWN_REFERENCE`), 저장된 `status = p.from`(D-9 `STATE_MISMATCH`), 전이·짝(Q8) |

**`ClawbackReceivableUpdated`** → `clawback_item` upsert(키 `receivable_id`)

| 컬럼 | 값 |
|---|---|
| `receivable_id`·`agent_id`·`origin_calc_id`·`amount`·`remaining`·`status` | payload 같은 이름 |
| `last_seq` | `e.seq` |
| 검사 | 첫 수신: `remaining ≤ amount`. 갱신: 같은 `agent_id`·`origin_calc_id`·`amount`, `remaining` 비증가, 종결 뒤 갱신 없음, `CLOSED ⇒ remaining = 0`(E-6, `STATE_MISMATCH`) |

**`PolicyEventObserved`** → `policy_flag`(D-10)

| `p.eventType` | 효과 |
|---|---|
| `CANCEL`·`LAPSE`·`WITHDRAW`·`REDUCE`·`PAYMENT_OVERDUE` | `(policy_no, agent_id, flag_type)` upsert: `flag_type` = `CANCELLED`·`LAPSED`·`CANCELLED`·`REDUCED`·`OVERDUE`, `reason` = `p.eventType`(V4), `occurred_on` = `p.occurredOn`, `cleared_at` = NULL(해제 뒤 재플래그도 다시 연다) |
| `REINSTATE` | 그 `(policy_no, agent_id)`의 `cleared_at IS NULL`인 행 전부 `cleared_at = e.occurredAt`. 열린 행이 없어도 독이 아니다(엔진 REVIVE는 실효 없이도 올 수 있다 — 확인 못 함, 보수적으로 허용) |

**`StatementEstimated`** → `earnings_month` upsert

| 컬럼 | 값 |
|---|---|
| `withholding_tax`·`net` | `p.withholdingTax`·`p.net` |
| `estimate_as_of`·`estimate_basis_seq` | `p.asOf`·`p.basisSeq`(검사 없음, D-14) |
| `status` | `ESTIMATED`(PROVISIONAL·ESTIMATED에서만 — Q7) |
| `net_basis` | `ESTIMATE`(Q1) |
| (쓰지 않음) | `p.gross`·`p.clawbackOffset`·`p.adjustment` — 예상은 대사 대상이 아니다(D-14) |

**`StatementConfirmed`** → `earnings_month` upsert(라인 없는 달이면 만든다 — E-7), DRIFT면 `recon_result` insert

| 컬럼 | 값 |
|---|---|
| `status`·`confirmed_at` | `CONFIRMED`·`p.closedAt` |
| `withholding_tax`·`net`·`net_basis` | 둘 다 있으면 그 값과 `CONFIRM`, 없으면 그대로(ESTIMATED 값·`ESTIMATE` 또는 NULL) |
| 대조(D-3) | `p.gross` ↔ `gross`, `p.clawbackOffset`(있으면) ↔ `clawback_offset`, `p.adjustment` ↔ `adjustment`. 하나라도 다르면 `data_status = DRIFT` + `recon_result(run_at = e.occurredAt, source EVENT, source_seq = e.seq, portal_* = 행의 합, engine_* = payload, engine_net = p.net, status DRIFT)` + `projection_drift_total{event=StatementConfirmed}`. 같으면 아무것도 쓰지 않는다(`data_status`를 OK로 되돌리지도 않는다 — 한 번 DRIFT면 Phase 7 대사가 판정) |

**`PayoutCompleted`** → `earnings_month` 갱신(CONFIRMED에서만 — Q7)

| 컬럼 | 값 |
|---|---|
| `status`·`paid_at`·`paid_amount` | `PAID`·`p.paidAt`·`p.paidAmount` |
| `withholding_tax`·`net`·`net_basis` | 있으면 그 값과 `PAYOUT`, 없으면 그대로 |
| `receivable_offset`·`carried_receivable`·`payable`·`run_seq` | 있으면 payload, 없으면 NULL(V4) |
| 대조(D-3) | 있는 필드만: `gross`·`clawbackOffset`·`adjustment`(E-8 — 엔진은 `adjustment`를 싣지 않는다) |

## ③ 상태 전이

**`earnings_month.status`**(정본 `MonthTransitions`, `portal-domain` — 모든 상태 × 사건 시험)

| 지금 | 사건 | 다음 | 표 밖 |
|---|---|---|---|
| (없음) | 라인(`CommissionCalculated`·`AdjustmentPosted`) | PROVISIONAL | — |
| (없음) | `StatementEstimated` | ESTIMATED | — |
| (없음) | `StatementConfirmed` | CONFIRMED(정정만 있는 달 등 — E-7) | — |
| PROVISIONAL·ESTIMATED | 라인 | 그대로 | — |
| PROVISIONAL·ESTIMATED | `StatementEstimated` | ESTIMATED | — |
| PROVISIONAL·ESTIMATED | `StatementConfirmed` | CONFIRMED | — |
| CONFIRMED | `PayoutCompleted` | PAID | — |
| CONFIRMED·PAID | 라인·`StatementEstimated`·`StatementConfirmed` | — | `CLOSED_MONTH` |
| (없음)·PROVISIONAL·ESTIMATED | `PayoutCompleted` | — | `STATE_MISMATCH` |
| PAID | `PayoutCompleted` | — | `CLOSED_MONTH` |

**`deferral_item.status`**: (없음) →`DeferralScheduled`→ SCHEDULED; SCHEDULED → RELEASED(`DUE_RELEASED`)·HELD(`POLICY_NOT_INFORCE`)·CANCELLED(`POLICY_TERMINATED`·`SOURCE_REVERSED`); HELD → CANCELLED(같은 둘). 그 밖 `STATE_MISMATCH`(Q8).

**`policy_flag`**: (없음/해제됨) →플래그 이벤트→ 열림(`cleared_at` NULL, upsert로 `occurred_on`·`reason` 갱신) →`REINSTATE`→ 해제(`cleared_at`).

**`clawback_item.status`**: (없음) → OPEN·OFFSET·CLOSED·WRITTEN_OFF(첫 수신은 어떤 상태든 — 생성 시점에 이미 상계됐을 수 있다); OPEN → OFFSET·CLOSED·WRITTEN_OFF; OFFSET → OFFSET·CLOSED·WRITTEN_OFF; CLOSED·WRITTEN_OFF → 없음.

**`ingest_cursor.state`**(`IngestTransitions`에 추가 — 사건 `OPERATOR_REBUILD`·`REBUILD_SUCCEEDED`·`REBUILD_FAILED`)

| 지금 | 사건 | 다음 |
|---|---|---|
| RUNNING·PAUSED·FAILED | 운영자 `rebuild` | REBUILDING |
| REBUILDING | 재생 성공 | RUNNING(`last_seq` = 재생한 마지막) |
| REBUILDING | 재생 실패 | FAILED(`REBUILD_FAILED`) |
| REBUILDING | 그 밖(적용·정지·pause·resume) | 거부 |

## ④ 독 판정 추가 (`PROJECTOR_ERROR`의 사유)

프로젝터는 `ProjectionViolation(reason, keys)`을 던진다 → 적용기가 지금처럼 `PROJECTOR_ERROR`로 멈추고 `FAILED` 행을 남긴다(키를 믿을 수 있다). `error` = `"PROJECTOR_ERROR: {reason} seq {n} eventId {id}"` — 금액·이름 등 값은 넣지 않는다(평문 규약).

| reason | 언제 |
|---|---|
| `CLOSED_MONTH` | D-4, ③ 표의 마감월 행 |
| `STATE_MISMATCH` | D-9 `from`, ③ 표 밖, Q6 순번·`oldLimit`, Q8 짝, E-6 채권, `AgentSnapshot.status` 어휘 밖 |
| `UNKNOWN_REFERENCE` | 모르는 원장·스케줄·정정 대상 라인, 삭감 라인의 원장 없음(E-3) |
| `DUPLICATE_KEY` | Q4 |
| `SUM_MISMATCH` | D-13 월 합산 ≠ Σ 라인 |
| `ACCUM_CHAIN` | D-13 `accum_after − accum_paid ≠ amount`(K5) |
| `OVER_LIMIT`·`NEGATIVE_ACCUM` | Q5 |

`IngestErrorCode`에 **`REBUILD_FAILED`**(FAILED) 하나. 프로젝터 사유는 코드가 아니라 detail의 첫 단어다(코드 어휘를 늘리지 않는다 — 상태 기계는 사유를 보지 않는다).

## ⑤ 불변식 범위 (D-13·C11)

적용 트랜잭션에서 그 이벤트가 건드린 범위만: 월 합산은 그 `(agent, closeYm)`의 라인 `SUM` 한 문장(인덱스 `ix_line_agent_ym`), 원장은 그 `ledger_id` 한 행, 분급·채권은 그 키 한 행. **C11 증명**: ⓐ 단위 — 인메모리 포트가 읽은 행 수를 세고, 같은 이벤트를 기존 라인 1개·1,000개인 상태에 적용해 읽기 수가 같음을(월 합산은 집계 1회) ⓑ IT — JDK `Proxy`로 감싼 `DataSource`가 이벤트당 문장 수를 세고 상한(이벤트 종류별 표, 예: `CommissionCalculated` ≤ 8)과 기존 행 수 무관함을. 전수 검증은 재구축 끝(⑥)에서 한 번.

## ⑥ 재구축 (D-11·Q10)

`ingest rebuild --tenant T --actor A`(CLI, Phase 1 명령과 같은 감사 — `INGEST_REBUILD`·`INGEST_REBUILD_REJECTED`):

1. **txA**(테넌트 바인딩): 잠금(`pg_try_advisory_xact_lock` — 워커와 같은 키). 못 잡으면 거부(감사). 커서 상태가 `OPERATOR_REBUILD`를 받지 않으면 거부. `REBUILDING` 커밋.
2. 피드 첫 페이지(`afterSeq=0`)의 `headSeq` > 상한(`portal.ingest.rebuild.max-events`, 기본 50,000)이면 txC로 `FAILED(REBUILD_FAILED: over limit)` — 시작하지 않는다.
3. **txB**: 잠금 → 뷰 DELETE(그 테넌트: `agent_profile`·`earnings_line`·`earnings_month`·`policy_limit`·`policy_limit_history`·`deferral_item`·`clawback_item`·`policy_flag` — **지우지 않는 것**: `ingest_event`·`ingest_cursor`·`audit_access`·`recon_result`(D-11), `tenant`·`identity_link`·`notification`(읽음 상태 — Phase 6 파생은 `dedup_key`가 막는다)·`inquiry`·`simulation_log`(사용자 데이터)) → 페이지마다 이벤트를 적용기와 **같은 검증·해시·seq 규칙**으로(재생 모드: 기존 `APPLIED` 행은 해시·seq가 같아야 하고 같으면 프로젝터를 다시 부른다, 없거나 `FAILED`면 `APPLIED`로) → 끝에서 전수 불변식(월 합산 전체, 원장 연쇄 전체) → 커서 `RUNNING`·`last_seq`·`last_event_id` → 커밋.
4. 실패(독·불변식·피드 오류·해시 불일치): txB 롤백(뷰·`ingest_event`·`recon_result` 전부 원래대로) → **txC** `FAILED(REBUILD_FAILED: {원인 사유} seq n)`.

`recon_result` 쓰기는 `ON CONFLICT DO NOTHING`(Q2)이라 재생이 같은 DRIFT 행을 다시 만들어도 같다. 피드 호출이 txB 안에 있다(긴 트랜잭션 — 부풀림·스냅샷 유지 비용은 설계서 §14.1에, 상한이 그 크기를 묶는다). 조회는 MVCC로 옛 행을 계속 본다.

포트: `portal-ingest`에 `Rebuilder`(검증기·해셔·`IngestStore`·`Projector`·`FeedClient`·새 포트 `ViewReset`), 적용기의 판정 부분을 재생 모드와 공유하도록 `IngestApplier`에서 `EventCheck`로 꺼낸다(동작 무변경 — Phase 1 시험 그대로 통과가 증거).

## ⑦ 모듈·포트

| 모듈 | 더하는 것 |
|---|---|
| `portal-domain` | `MonthTransitions`, `AgentStatus`(ACTIVE/DISMISSED), `ReceivableStatus`, `FlagType`(+ `PolicyEventType` → `FlagType` 매핑), `NetBasis`, `ReconSource`, `IngestTrigger` 셋·`IngestErrorCode.REBUILD_FAILED` |
| `portal-projection` | `ViewProjector implements Projector`(타입 → 프로젝터 7종: `AgentProjector`·`EarningsProjector`(계산·정정)·`LimitProjector`(개설·전기·재산정)·`DeferralProjector`·`ClawbackProjector`·`FlagProjector`·`StatementProjector`(예상·확정·지급)), 저장 포트 `ViewStore`(테이블별 읽기·쓰기 — 이벤트마다 자기 테이블만 쓰고 교차 참조는 읽기 메서드만), `ProjectionViolation`, `InvariantChecks`, `NoopNotificationDeriver`. 시험 픽스처 `InMemoryViewStore`(읽은 행 수 계수). Spring·DB 무의존(규칙 (e) 그대로) |
| `portal-ingest` | `Rebuilder`, `ViewReset` 포트, `EventCheck`(적용기에서 꺼냄). 적용기가 프로젝터 뒤 `NotificationDeriver.derive`를 같은 트랜잭션에서 부른다(D-15 — 구현은 no-op) |
| `portal-infra` | V4, 저장소 `AgentProfileRepository`·`EarningsLineRepository`·`EarningsMonthRepository`·`PolicyLimitRepository`·`DeferralItemRepository`·`ClawbackItemRepository`·`PolicyFlagRepository`·`ReconResultRepository`·`ViewResetRepository`(전부 `TenantScopedRepository` 하위 — SQL 스캔이 `tenant_id` 조건을 본다) |
| `portal-app` | `JdbcViewStore`·`JdbcViewReset`(어댑터, Phase 1 Q6 방식), `IngestConfiguration`이 `RecordingProjector` 대신 `ViewProjector`, CLI `ingest rebuild`, 지표 `projection_drift_total`·`ingest_rebuild_seconds` |

`RecordingProjector`는 Phase 1 시험(적용 횟수 대조)이 쓰므로 시험 픽스처로 남긴다(main에서 시험 픽스처로 옮김 — 운영 조립에서 빠진다).

## ⑧ V4 DDL (`V4__projection.sql`)

```sql
-- agent_profile: role NULL 허용(O-1, D-8), status 닫힌 값
ALTER TABLE agent_profile ALTER COLUMN role DROP NOT NULL;
ALTER TABLE agent_profile ADD CONSTRAINT ck_agent_profile_status CHECK (status IN ('ACTIVE', 'DISMISSED'));

-- earnings_month: 지급 확정값(D-2) + 예상 기준 seq + 세후의 출처(Q1)
ALTER TABLE earnings_month
    ADD COLUMN receivable_offset  BIGINT,
    ADD COLUMN carried_receivable BIGINT,
    ADD COLUMN payable            BIGINT,
    ADD COLUMN run_seq            BIGINT,
    ADD COLUMN estimate_basis_seq BIGINT,
    ADD COLUMN net_basis          TEXT CONSTRAINT ck_earnings_month_net_basis CHECK (net_basis IN ('ESTIMATE', 'CONFIRM', 'PAYOUT'));

-- policy_limit_history: 키 = 이벤트 seq(D-7, DD-5). 표가 비어 있으므로 제약 교체(데이터 마이그레이션 없음)
ALTER TABLE policy_limit_history DROP CONSTRAINT policy_limit_history_pkey;
ALTER TABLE policy_limit_history ADD COLUMN source_seq BIGINT NOT NULL;
ALTER TABLE policy_limit_history ALTER COLUMN posting_seq DROP NOT NULL;
ALTER TABLE policy_limit_history ADD PRIMARY KEY (tenant_id, ledger_id, source_seq);

-- policy_flag: 원 이벤트 유형(D-10)
ALTER TABLE policy_flag ADD COLUMN reason TEXT
    CONSTRAINT ck_policy_flag_reason CHECK (reason IN ('CANCEL', 'LAPSE', 'WITHDRAW', 'REDUCE', 'PAYMENT_OVERDUE'));

-- clawback_item·deferral_item: 계약 v1.2 닫힌 어휘
ALTER TABLE clawback_item ADD CONSTRAINT ck_clawback_item_status CHECK (status IN ('OPEN', 'OFFSET', 'CLOSED', 'WRITTEN_OFF'));
ALTER TABLE deferral_item ADD CONSTRAINT ck_deferral_item_reason CHECK (reason IN ('CURVE', 'LIMIT_DEFER'));
ALTER TABLE deferral_item ADD CONSTRAINT ck_deferral_item_status_reason
    CHECK (status_reason IN ('DUE_RELEASED', 'POLICY_NOT_INFORCE', 'POLICY_TERMINATED', 'SOURCE_REVERSED'));

-- recon_result: 출처(D-3) + 이벤트 seq(Q2)
ALTER TABLE recon_result DROP CONSTRAINT recon_result_pkey;
ALTER TABLE recon_result
    ADD COLUMN source     TEXT   NOT NULL CONSTRAINT ck_recon_result_source CHECK (source IN ('EVENT', 'BATCH')),
    ADD COLUMN source_seq BIGINT NOT NULL;
ALTER TABLE recon_result ADD PRIMARY KEY (tenant_id, agent_id, close_ym, run_at, source, source_seq);
```

PK 이름은 V1이 이름을 주지 않아 PostgreSQL 기본(`<table>_pkey`)이다 — 마이그레이션 IT가 V4 뒤 제약 이름·열을 단언한다. 닫힌 값 CHECK 다섯 개가 늘어나므로 설계서 §5 주석·`StatusEnumDdlTest`(주석·V1~V4·열거 대조)를 같은 커밋에서. RLS·GRANT는 V2가 표 단위로 이미 준다(새 표 없음 — 확인 시험 한 건).

## ⑨ 골든 뷰 (D-1·C1·C3)

- **스트림**(`portal-projection/src/test/resources/golden-streams/`):
  - `a1-provisional.jsonl` — 엔진 파일 전 **임시** A-1(D-1). 엔진 모양 그대로(같은 `occurredAt`, 숫자 문자열 키, `계산·계산·전기·전기`, `limitCutPolicy`는 삭감 라인에만): 개설(월납 150,000·한도 1,800,000) → FY 945,000 → 시책 855,000(삭감 145,000 `DEFER_AFTER_FY`) → 전기 945,000(누적 945,000) → 전기 855,000(누적 1,800,000) → 확정 202608(gross 1,800,000) → 지급(세액 59,400·순액 1,740,600 — 3.3%, 엔진 E1 cycle의 비율과 같게) → 해약 관측(2027-02-15) → 환수 −720,000(202702 — 7~12회차 40%) → 전기 −720,000(누적 1,080,000). **금액은 예상**이다 — 엔진 파일이 오면 교체하고 차이를 보고(지시문 D-1).
  - `a1-interleaved.jsonl`·`a1-posted-first.jsonl` — 같은 사실을 `계산·전기·계산·전기`로, 그리고 전기를 그 라인보다 먼저(D-6). `seq`만 다르다.
  - `engine-e1-cycle.jsonl` — 엔진 `e1`의 `cycle_마감_지급_채권_정정.jsonl` 바이트 그대로(Q11, 출처 해시는 README).
  - 반례(⑩ 주입·C4·C5·C8용): `closed-month-line`, `drift-confirmed`, `deferral-from-mismatch`, `unknown-schedule`, `accum-chain-break`, `rebase-without-posting-seq`, `adjustment-only-month` — 각각 정상 스트림에서 한 줄을 바꾼 것(README에 규칙, `StreamFilesTest`처럼 다시 계산해 대조).
- **기대 행**(`golden-views/<stream>/<table>.jsonl`): 테이블마다 PK 순 한 줄 한 행, 열 이름은 DB 열 그대로(snake_case), 시각은 UTC ISO-8601, `JSONB`는 정규화(JCS) 문자열. 빈 테이블은 빈 파일(있어야 한다 — "그 테이블은 비어야 한다"도 대조).
- `ProjectionGoldenTest`(인메모리, 밀리초): 스트림 → 열 테이블(`agent_profile`·`earnings_line`·`earnings_month`·`policy_limit`·`policy_limit_history`·`deferral_item`·`clawback_item`·`policy_flag`·`recon_result`) 전부 대조. 순서 변형(C3)은 `source_seq`·`last_seq`·`last_posting_seq`를 뺀 투영이 같음을.
- **`ProjectionGoldenIT`**(더함): 같은 파일을 PostgreSQL 어댑터로 — 인메모리와 DB가 같은 행을 내는지(인메모리만 맞고 SQL이 틀리는 경우를 막는다).
- **D-1 교체**: 엔진 E2 0단계 파일이 오면 ⓐ `contracts/events/v1/samples/a1/`의 10개 파일을 그 jsonl 하나(`a1_portal_150000.jsonl`, 바이트 그대로)로 바꾸고 `EventSchemaTest`·`Samples`가 jsonl 줄도 읽게, `CHECKSUMS` 재생성 ⓑ 골든 스트림 `a1-provisional`을 지우고 그 파일을 쓰며 기대 행을 다시 계산 ⓒ 임시 값과의 차이를 보고(어느 쪽이 틀렸는지). ⓐ는 계약 샘플 변경이라 엔진 `UPSTREAM`이 다시 낡는다(엔진 자기 산출물이라 의미 변경은 없다) — 이 교체를 **계약 v1.2.1 소형 PR**(지시문 §0과 같은 절차)로 할지, Phase 2 PR 안에서 할지 결정 필요(**Q13**, 권장: 소형 PR — 엔진이 `UPSTREAM`을 따라오는 지점이 하나로 남는다).

## ⑩ 완료 기준 → 시험·주입

| # | 시험 | 주입(관측 뒤 제거) |
|---|---|---|
| C1 | `ProjectionGoldenTest`(a1·cycle) + `ProjectionGoldenIT` | **K1** `EarningsProjector`가 `gross`에 1 더함 → 두 시험 실패 |
| C2 | `IngestDeterminismIT`(확장 — 뷰 9표, 재시작 포함 2회), `RebuildIT.c2RebuildGivesTheSameBytes` | — |
| C3 | `ProjectionGoldenTest.c3LineOrdersGiveTheSameViews` — 세 순서: `계산·계산·전기·전기`(a1), `계산·전기·계산·전기`(interleaved), **전기가 그 라인보다 먼저**(`a1-posted-first` — D-6의 순서 역전. 엔진 두 순서에서는 라인이 늘 먼저라 이것이 없으면 K2가 잡히지 않는다) | **K2** `LimitProjector`가 그 `calcId` 라인을 요구 → posted-first 실패 |
| C4 | `ProjectionInvariantTest.c4…`, `IngestWorkerIT.c4ClosedMonthLineStopsTheTenantAndLeavesViews` | **K3** 마감월 검사 제거 |
| C5 | `ProjectionInvariantTest.c5DriftIsNotPoison`, `IngestWorkerIT.c5…`(인제스트 계속, `recon_result` 한 행) | **K4** 불일치를 독으로 |
| C6 | `MonthTransitionsTest`(전 상태 × 사건), 골든(ESTIMATED 행이 있는 스트림 `a1-estimated` — 예상 → 확정(세액 없음, 값·`net_basis ESTIMATE` 유지) → 지급(네 컬럼·`PAYOUT`)) | — |
| C7 | 골든(해약 음수 전기), `ProjectionInvariantTest.c7…`(연쇄·한도·REBASE 순번 없음 이력 키) | **K5** `accum_after` 연쇄 검사 제거 |
| C8 | `ProjectionInvariantTest.c8FromMismatchAndUnknownSchedule…` | **K6** `from` 대조 제거 |
| C9 | `FlagProjectorTest`(Q12의 10 사례) | — |
| C10 | `RebuildIT`(중간 독 → 롤백·뷰·`ingest_event` 그대로·`FAILED(REBUILD_FAILED)`; 성공 → `RUNNING`·`last_seq`; 워커 배제 — 래치로 경합, Phase 1 c11 방식) | **K7** txB를 페이지마다 커밋 |
| C11 | ⑤ ⓐⓑ | **K8** 월 합산 검사를 테넌트 전체 `SUM`으로 → 읽기 수 비교 실패 |
| C12 | CI 아티팩트, 아키텍처 규칙, `verifySpringFree`, 평문 스캔 | — |
| 더함 | `recon_result` 재삽입 멱등(Q2) | **K9** `ON CONFLICT DO NOTHING` 제거 → `RebuildIT`(DRIFT 스트림 재구축) 실패 |
| 더함 | 정정 `calc_id` 접두(D-5) | **K10** 접두 제거 → cycle 골든(정정 `adjId 1` = 계산 `calcId 1`)이 `DUPLICATE_KEY` |
| 더함 | 순번 연속(Q6) | **K11** `postingSeq` 검사 제거 → 반례 `posting-seq-gap` 통과해 실패 |
| 더함 | 분급 짝(Q8) | **K12** 짝 검사 제거 |
| 더함 | V4 `posting_seq` NULL 허용 | **K13** V4에서 `DROP NOT NULL` 뺌 → `rebase-without-posting-seq` IT 실패 |
| 더함 | 재구축 상한 | **K14** 상한 검사 제거 → `RebuildIT.overLimitIsRefusedBeforeStarting` 실패 |

주입 **14건**(K1~K7 지시문 + K8~K14).

## ⑪ 단계 — 단계마다 시험·주입·설계서 같은 커밋

1. 계획(이 커밋) → 승인.
2. V4 + 도메인(`MonthTransitions`·열거·`IngestTransitions`) + 설계서 §5 v1.5 + `StatusEnumDdlTest`. 주입 K13.
3. `portal-projection`: 포트·프로젝터 7종·불변식·인메모리·임시 골든·cycle 골든. 주입 K1(인메모리 쪽)·K2·K3·K4·K5·K6·K8·K10·K11·K12.
4. 인프라 저장소 + 앱 어댑터 + 조립 교체 + `ProjectionGoldenIT`·워커 IT(C4·C5)·문장 수 IT. 주입 K1(DB 쪽 확인).
5. 재구축·결정론: `Rebuilder`·CLI·전이·`RebuildIT`·`IngestDeterminismIT`. 주입 K7·K9·K14.
6. D-1 교체(엔진 E2 0단계 파일이 오면 — 그 전이면 보고서에 "임시 골든"으로 적고 교체는 Q13 절차로).
7. 문서(설계서 v1.5 §5·§6.2·§6.3·§6.4·§6.7·§14.1, `CLAUDE.md` 프로젝션 절, DECISIONS, `e2e/engine-feed/README.md` 자리), 보고서, 태그, PR.

## ⑫ 설계서 v1.5에서 고칠 곳 (단계마다 같은 커밋)

§5(V4 열·제약, `data_status` `REBUILDING` 미사용, 불변식 문장 — Q5 **DD-8** 후보), §6.2(세후 전이 표 — D-2, `net_basis`), §6.3(`deferred_total`은 현재 엔진에서 실제로 삭감된 금액 — E-2), §6.6(이벤트 대사 = 첫 지점, `source`), §6.7(그림자 스키마 → 단일 트랜잭션 재생, Q10의 세 트랜잭션), §14.1(조직장 v2 이벤트 결정 항목, #4 스냅샷 부트스트랩 확장, 재구축 긴 트랜잭션, 정정 미지급 화면 규약), 부록 A-1(D-1 교체 뒤 값).

## ⑬ 하지 않는 것

지시문 §4 그대로: 조회 API·인증·화면, 알림 규칙(포트 no-op), 야간 대사, 시뮬레이션, 그림자 스키마·DDL 재구축, 새 테이블, 계약 의미 변경, V1~V3 수정, `double`·`BigDecimal`. 그리고 `utilization` 저장, 포털에서의 금액 계산(합산 외).

## ⑭ 다음 Phase 질문 (보고서에서 다시)

Phase 3 — `identity_link`의 역할·조직 컬럼(V5), 조회 응답 `dataStatus`의 `SYNC_DELAYED`(커서 `REBUILDING`·`FAILED`·지연), CLI 인가 전환. E2 뒤 — `StatementEstimated` 실 샘플 교체(`contract-only/07`), 실 엔진 E2E 스크립트(`e2e/engine-feed/`).
