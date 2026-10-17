# 포털 Phase 2 지시문 — 프로젝션 (ga-agent-portal, v1.0, 2026-10-17)

## 역할과 맥락

Phase 1(`phase-1` = `b51ca65`)의 인제스트가 적용하는 `Projector` 포트를 **실제 뷰 변환**으로 채운다. 이 Phase가 끝나면 엔진 이벤트 스트림 하나가 설계서 §5의 뷰 테이블(명세·월 요약·한도·분급·채권·계약 상태·설계사)을 **결정론적으로** 만들고, 같은 스트림을 두 번 넣으면 바이트까지 같고, 불변식이 깨지면 그 테넌트가 멈춘다. 화면·API는 Phase 3·4다.

정본: `docs/설계서.md` v1.4(§3.4, §4.2, §5, §6.2~§6.4, §6.7, §12 Phase 2, 부록 A), Phase 0·1 지시문의 불변 규약, `CLAUDE.md`. 브랜치 `work/phase-2`, 태그 `phase-2`, PR, **병합은 수용 심사 회신 뒤**. 계획 → 승인 → 단계 커밋 → 보고서 → 태그 → PR.

엔진 E1(`e1` = `826b3e4`)이 확정한 사실이 설계서 몇 곳과 다르다. 아래 §1의 결정이 설계서보다 우선하고, 해당 절은 **같은 커밋**에서 v1.5로 고친다.

## 0. 선행 PR — 계약 v1.2 (`work/contracts-v1.2`, **이 지시문이 승인**: CI 통과 뒤 병합, 병합 커밋을 엔진 E2에 전달)

Phase 1 수용 심사 §3-3·4에서 결정한 것. **좁힘이 있으므로** 엔진은 E2 0단계에서 `UPSTREAM`을 이 병합 커밋으로 올려 전역 검증기를 다시 돌린다(어휘의 정본은 엔진 빌더의 열거 — 어긋나면 어휘가 틀린 쪽이 고친다).

| # | 변경 | 종류 |
|---|---|---|
| M1 | `engine-internal.openapi.yaml`: `GET /internal/v1/events`에 `400 INVALID_REQUEST`(Problem) | 추가 |
| M2 | `DeferralStatusChanged.reason` → 닫힌 열거 `DUE_RELEASED`·`POLICY_NOT_INFORCE`·`POLICY_TERMINATED`·`SOURCE_REVERSED` | 좁힘 |
| M3 | `ClawbackReceivableUpdated.status` → 닫힌 열거 `OPEN`·`OFFSET`·`CLOSED`·`WRITTEN_OFF`, `amount`·`remaining` `minimum 0` | 좁힘 |
| M4 | 샘플 손질(계약 통과 유지): 08 `StatementConfirmed`에서 `withholdingTax`·`net` 제거, 13 사유 `POLICY_TERMINATED`, 14 금액 양수·상태 `OFFSET`, 16 `role`·`nameMasked` 제거 | 샘플 |
| M5 | 샘플 디렉터리 분리: `samples/a1/`(엔진이 재현하는 A-1 순서)와 `samples/contract-only/`(엔진이 내지 않는 형태 — `StatementEstimated`(E2 뒤 교체), `DeferralScheduled` `LIMIT_DEFER`, 그 취소). `EventSchemaTest`는 두 디렉터리 모두 | 구조 |

`CHECKSUMS` 재생성. 설계서 §4.2·부록 A-1 머리에 "A-1의 정본 값은 엔진 E2 0단계가 월납 150,000으로 낸 스트림(`a1_portal_150000.jsonl`)이며 Phase 2 1단계에서 채택한다" 한 줄. DECISIONS에 **DD-5**(`policy_limit_history` 키), **DD-6**(`tenant.status` 뜻), **DD-7**(A-1 예시값이 엔진 시드로 재현 불가 — 설계 시 엔진 요율을 보지 않았다).

## 1. 결정

| # | 결정 | 근거·조건 |
|---|---|---|
| D-1 | **A-1 정본 = 엔진 스트림.** 엔진 E2 0단계가 커밋하는 `outbox-golden/a1_portal_150000.jsonl`(월납 150,000·2026-08-01 체결·시책 1,000,000·2026-09 마감·지급·2027-02-15 해약)을 **바이트 그대로** `samples/a1/`로 들인다(`eventId`·`occurredAt`·`seq` 포함). 골든 뷰(부록 A)는 그 스트림에서 계산한 기대 행이다 | 두 저장소가 같은 사건에서 같은 바이트. 엔진 파일이 오기 전에는 Phase 1 수용 심사 §3-3의 예상값(한도 1,800,000·FY 945,000·시책 855,000+삭감 145,000)으로 **임시** 골든을 만들고, 파일이 오면 교체(교체 커밋에서 차이가 있으면 보고 — 어느 쪽이 틀렸는지). |
| D-2 | **세후는 지급 시 확정.** `earnings_month.status` 전이와 열: `PROVISIONAL`(라인만) → `ESTIMATED`(`StatementEstimated`: `withholding_tax`·`net` 예상값 + `estimate_as_of`·`estimate_basis_seq`) → `CONFIRMED`(`StatementConfirmed`: `gross`·`clawback_offset`·`adjustment` 엔진값 대조, `confirmed_at`; 세액·순액은 **있으면** 채우고 없으면 ESTIMATED 값 유지 + 라벨 "확정(세후 예상)") → `PAID`(`PayoutCompleted`: `withholding_tax`·`net`·`paid_amount`·`paid_at`·**`receivable_offset`·`carried_receivable`·`payable`·`run_seq`** 확정) | 엔진 §7: 원천세·상계는 지급 런에서. 설계서 §6.2 표 개정. **V4**에 `earnings_month` 네 컬럼 + `estimate_basis_seq` 추가. |
| D-3 | **마감 이벤트가 대사의 첫 지점.** `StatementConfirmed`·`PayoutCompleted`의 `gross`·`clawbackOffset`·`adjustment`가 포털 합산(Σ 라인)과 다르면 **독이 아니라 `data_status = DRIFT`**(그 월 행) + 지표 `projection_drift_total` + `recon_result` 행(`run_at` = 이벤트 `occurredAt`, status `DRIFT`, 출처 `EVENT`). 인제스트는 계속 | 틀릴 수 있는 숫자를 확정처럼 보이지 않게(§6.6) 하되, 엔진 정의와 포털 정의의 차이는 데이터 결함이 아니다. Phase 7 야간 대사가 같은 표를 쓴다 — `recon_result`에 `source` 컬럼(`EVENT`/`BATCH`) V4. |
| D-4 | **마감월 불변(규약 4)은 독.** `CONFIRMED`/`PAID`인 `(agent, closeYm)`에 `CommissionCalculated`·`AdjustmentPosted`(그 `closeYm` 귀속)가 오면 `PROJECTOR_ERROR(CLOSED_MONTH)` → 테넌트 `FAILED` | 엔진 C8이 막는 것이므로 오면 발행자 이상. `AdjustmentPosted`는 익월 귀속이라 정상이면 걸리지 않는다. |
| D-5 | **정정은 지급되지 않는다(엔진 결함 E1-2).** `AdjustmentPosted` → `earnings_line(comm_type=ADJUSTMENT, calc_id=adjId 접두 `ADJ:`)`, `earnings_month.adjustment` 합산. `PayoutCompleted.adjustment`는 오지 않는다 — 월 요약에 `adjustment_paid`를 두지 않고, 화면 규약(Phase 4) "정정은 명세에 표시되나 지급 반영 여부는 엔진 결정"을 §14.1에 기록 | `calc_id` PK 충돌 방지(엔진 `calc_id`는 숫자 문자열, `adjId`도 숫자) — 접두로 구분. 설계서 §5 주석. |
| D-6 | **라인 순서에 기대지 않는다.** 한 계산의 여러 라인은 `계산·계산·전기·전기`로 오고(엔진 §6.8), `LimitLedgerPosted`가 올 때 그 `calcId`의 라인이 아직 없을 수 있다(순서 역전). 그래서 `LimitLedgerPosted`는 `earnings_line`을 **요구하지 않는다** — `policy_limit`·`policy_limit_history`만 갱신하고, 라인이 없어도 독이 아니다. 라인 상세 화면(Phase 4)이 `calc_id`로 양쪽을 잇는다 | 프로젝터는 각 이벤트를 **자기 테이블만으로** 닫는다(교차 참조는 읽기 전용). 골든 스트림에 두 순서(`계산·전기·계산·전기` / `계산·계산·전기·전기`)를 다 둔다. |
| D-7 | **한도 이력 키 = 이벤트 `seq`.** V4: `policy_limit_history`에 `source_seq BIGINT NOT NULL` 추가, PK를 `(tenant_id, ledger_id, source_seq)`로, `posting_seq` NULL 허용(REBASE는 없을 수 있다). `kind` `POST`/`REBASE`/`CUT`? — 아니다: `CUT`은 라인(`limit_cut_amt`)에 있고 이력은 전기·재산정만 | DD-5. 테이블이 비어 있으므로 새 V로 제약 교체 가능(V1·V2·V3 불변). `policy_limit.cut_total`·`deferred_total`은 `earnings_line.limit_cut_amt`를 `limit_cut_policy`로 나눠 합산(이벤트마다 갱신, 저장 — 합산 결과 저장은 허용: 정수 합산). |
| D-8 | **`AgentSnapshot`**: `role` 없으면 NULL(V4 `agent_profile.role` NULL 허용), `grade` NULL 허용(이미), `org_path`는 엔진이 준 문자열 그대로(`/HQ/BRANCH/TEAM` org_id 경로). `status` 어휘 `ACTIVE`/`DISMISSED`(CHECK) | O-1. 역할은 `identity_link`(Phase 3). |
| D-9 | **`DeferralStatusChanged`**: `from` 대조 — 저장된 상태 ≠ `from`이면 독(`PROJECTOR_ERROR(STATE_MISMATCH)`); 모르는 `scheduleId`면 독. `reason` 어휘는 계약 v1.2 열거 그대로 `status_reason`에 | 상태 기계 위반은 발행자 이상. |
| D-10 | **`PolicyEventObserved`**: `CANCEL`→`CANCELLED`, `LAPSE`→`LAPSED`, `WITHDRAW`→`CANCELLED`(사유 `WITHDRAW`를 `policy_flag`에 — V4 `reason` 컬럼), `REDUCE`→`REDUCED`, `PAYMENT_OVERDUE`→`OVERDUE`, `REINSTATE` → 그 계약의 열린 플래그 전부 `cleared_at`. 같은 `(policy, agent, flag_type)`에 다시 오면 `occurred_on` 갱신(upsert) | §5 `policy_flag` CHECK 어휘 확정. |
| D-11 | **재구축(§6.7)은 그림자 스키마가 아니라 단일 트랜잭션 재생**: `rebuild --tenant T` = 테넌트 잠금(advisory, 워커 배제) → 커서 `REBUILDING` → **한 트랜잭션** 안에서 그 테넌트의 뷰 행 전부 DELETE(`ingest_event`·`ingest_cursor`·`audit_access`·`recon_result` 제외) → `afterSeq=0`부터 전부 재적용(`ingest_event`는 해시 대조만 — 있으면 같아야 하고 다르면 롤백) → 불변식 검증 → 커밋 → 커서 `RUNNING`(`last_seq` = 재생한 마지막). 실패·불일치는 **롤백**(아무것도 바뀌지 않음) + `FAILED(REBUILD_FAILED)` | 공유 테이블(멀티테넌트)이라 `RENAME` 스왑이 불가능하고, PostgreSQL MVCC가 "재구축 중에도 기존 행으로 조회 계속"을 준다. FORCE RLS·`portal_migrator` 문제가 사라진다(DDL 없음). 긴 트랜잭션의 비용(부풀림·락 없음)은 §14.1에 기록, 이벤트 수 상한 파라미터. 설계서 §6.7 개정 + `설계고찰` 한 절. **스트림 출처**: 재생은 피드(`afterSeq=0`)에서 다시 받는다 — 포털은 payload를 저장하지 않는다(Phase 1 D-1). |
| D-12 | **결정론**: 같은 스트림 → 두 DB의 테넌트 행 집합이 **모든 뷰 테이블에서 바이트 동일**(고정 `Clock`, `source_seq`·`last_seq` 포함). 재구축 뒤에도 동일 | §6.7. 비교는 `COPY … TO` 정렬 텍스트 또는 행 집합 해시. |
| D-13 | **불변식(§5)은 적용 트랜잭션 안에서 매번** 검사(그 테넌트·그 설계사·그 월/원장 범위만 — 전수 아님): `earnings_month` 세 합산 = Σ 라인, `accum_paid ≤ limit_amount`, `accum_after − 직전 = amount`, 마감월 불변. 위반 → 독 | 전수 검증은 재구축·대사(Phase 7)에서. |
| D-14 | **`StatementEstimated`** 프로젝터는 계약대로 구현(엔진 E2가 낸다). `basisSeq`는 검사하지 않고 `estimate_basis_seq`에 그대로 저장한다(그 자체가 "어느 시점 기준 예상인지"의 기록) | 예상값은 참고치 — 독 판정 대상이 아니다. |
| D-15 | **`notification` 파생**은 Phase 6 — `NotificationDeriver` 포트는 no-op 구현, 호출 지점만(같은 트랜잭션) | — |

## 2. 작업 목록

1. **계획**: 이벤트 13종 → 테이블·컬럼 매핑 표(필드 하나하나), 상태 전이 표(`earnings_month`·`deferral_item`·`policy_flag`), 독 판정 추가 목록(D-4·D-9·D-13), V4 DDL, 재구축 절차·잠금·상한, 골든 뷰 파일 형식, 주입 목록. **승인 뒤 착수.**
2. **V4 + 도메인**: D-2·D-3·D-7·D-8·D-10 컬럼·제약, 설계서 §5 v1.5.
3. **프로젝터 5종 + 2**(`earnings`·`limit`·`deferral`·`clawback`·`flag` + `agent`·`statement`), `portal-projection`(Spring 무의존 — 저장소 포트는 인메모리로 밀리초 시험), 불변식 검사기.
4. **인프라 저장소**(`TenantScopedRepository` 하위, `portal-infra`) + 앱 어댑터(`portal-app`, Phase 1 Q6 방식).
5. **골든 뷰**: A-1 스트림(D-1) → 테이블별 기대 행(`portal-projection/src/test/resources/golden-views/a1/<table>.jsonl`), 순서 변형 스트림(D-6), 계약 전용 샘플 스트림, 마감 뒤 라인·`from` 불일치·합산 불일치(DRIFT) 케이스.
6. **재구축·결정론**(D-11·D-12): CLI `rebuild --tenant T`, `REBUILDING` 전이(`IngestTransitions`), `IngestDeterminismIT` 확장(뷰 테이블 전부), 재구축 IT(중간 실패 → 롤백 → 원래 행 그대로).
7. **문서**: 설계서 v1.5(§5·§6.2·§6.3·§6.4·§6.7·§14.1 — 조직장 v2 이벤트·스냅샷 부트스트랩·긴 트랜잭션·정정 미지급), `CLAUDE.md` 프로젝션 절(각 이벤트는 자기 테이블만, 합산은 정수, 순서 비의존, 마감월 독, 대사 불일치는 DRIFT), DECISIONS, `e2e/engine-feed/README.md` 자리(스크립트는 E2 뒤), 보고서, 태그, PR.

## 3. 완료 기준

| # | 기준 | 증명 | 주입 |
|---|---|---|---|
| C1 | A-1 스트림 → 골든 뷰 전 테이블 일치(D-1 임시값, 엔진 파일 뒤 교체) | `ProjectionGoldenTest` | K1 프로젝터 금액 하나 변경 → 실패 |
| C2 | 같은 스트림 2회 + 재구축 1회 → 뷰 테이블 바이트 동일 | `IngestDeterminismIT`(확장), `RebuildIT` | — |
| C3 | 두 라인 순서 모두 같은 뷰 | `ProjectionGoldenTest` 순서 변형 | K2 `LimitLedgerPosted`가 라인을 요구 → 변형 실패 |
| C4 | 마감월 뒤 라인 → 독, 테넌트 `FAILED`, 뷰 불변 | `ProjectionInvariantTest` + `IngestWorkerIT` | K3 검사 제거 → 실패 |
| C5 | `StatementConfirmed` 합산 불일치 → `DRIFT`(독 아님), 인제스트 계속, `recon_result(source=EVENT)` | 〃 | K4 불일치를 독으로 → 실패 |
| C6 | 세후 전이: ESTIMATED 값 → CONFIRMED(세액 없음, 유지) → PAID(확정값 네 컬럼) | 골든 + 전이 표 시험 | — |
| C7 | 한도: `accum_after` 연쇄·`≤ limit`·REBASE(순번 없음) 이력 `source_seq` 키 | 골든(해약 음수 전기 포함) + 불변식 | K5 `accum_after` 연쇄 검사 제거 → 실패 |
| C8 | 분급 `from` 불일치·모르는 schedule → 독 | `ProjectionInvariantTest` | K6 |
| C9 | 플래그 매핑 10종·REINSTATE 해제·upsert | 단위 | — |
| C10 | 재구축: 중간 실패 → 롤백·원래 행 그대로·커서 `FAILED(REBUILD_FAILED)`; 성공 → `RUNNING`·`last_seq` 정확; 워커 배제 | `RebuildIT` | K7 롤백 대신 부분 커밋 → 실패 |
| C11 | 적용 트랜잭션당 불변식 검사 범위(전수 아님 — 쿼리 수 상한) | 쿼리 카운터 시험 | — |
| C12 | Phase 0·1 무손상, 아키텍처 규칙(`portal-projection` Spring·DB 0, `platform-core`만), `TZ=UTC`, CI, 평문 스캔, 주입 ID 단위 | CI 아티팩트 | — |

## 4. 하지 말 것

조회 API·인증·화면(Phase 3·4). 알림 규칙(Phase 6 — 포트 no-op). 야간 대사 배치(Phase 7 — `recon_result`에 `EVENT` 출처만). 시뮬레이션(Phase 5). 그림자 스키마·DDL 재구축. 설계서에 없는 테이블(컬럼은 §1의 V4만). 계약 의미 변경(§0 외). V1~V3 수정. `double`·`BigDecimal`(비율은 `Ratio` 표시 전용 — `utilization`은 저장하지 않는다).

## 5. 보고 형식

Phase 1과 같게. 추가: 이벤트 → 테이블 매핑 표(최종), 골든 뷰 파일 목록과 D-1 교체 여부(엔진 파일 해시), 재구축 IT의 이벤트 수·소요, 다음 Phase 질문(Phase 3 — `identity_link` 역할·조직 컬럼 V5, 조회 응답의 `dataStatus` `SYNC_DELAYED`, CLI 인가 전환; E2 뒤 — `StatementEstimated` 실 샘플 교체, 실 엔진 E2E 스크립트).
