# 포털 Phase 1 지시문 — 인제스트 (ga-agent-portal, v1.0, 2026-10-16)

## 역할과 맥락

당신은 `ga-agent-portal`의 구현자다. Phase 0(`phase-0.1`, `main` = `0ba375f`)이 세운 규약 위에서 **엔진 이벤트 피드를 읽어 테넌트별로 멱등 적용하는 인제스트**를 만든다. 이 Phase가 끝나면 어떤 순서·중복·갭·독 이벤트가 와도 **다른 테넌트에 영향 없이, 커서가 거짓말하지 않고, 같은 스트림을 두 번 넣어도 상태가 같아야** 한다. 프로젝터(뷰 변환)는 Phase 2다 — 이번에는 기록만 하는 `Projector` 포트 구현으로 흐름을 닫는다.

정본: `docs/설계서.md` v1.2(§3.4-1, §4.1·§4.5, §5 `ingest_cursor`·`ingest_event`, §6.1, §12 Phase 1), `docs/phase-00-지시문.md`의 불변 규약 1~8, `CLAUDE.md`. 브랜치 `work/phase-1`, 태그 `phase-1`, PR, **병합은 수용 심사 회신 뒤**. 계획 → 승인 → 단계 커밋 → 보고서 → 태그 → PR.

## 0. 선행 PR — 계약 v1.1 정비 (별도 브랜치 `work/contracts-v1.1`, **이 지시문이 승인이다**: 자체 CI 통과 뒤 병합, 병합 커밋 해시를 엔진 E1에 전달)

엔진을 읽은 결과(2026-10-16, `ga-commission-engine` `main` = `85b4c71`) 포털 계약 v1.0이 **엔진에 없는 데이터를 필수로 요구**하는 곳이 있다. 엔진이 지어내게 둘 수 없으므로 포털 계약을 먼저 고친다. 전부 **필수 → 선택** 또는 **추가**이므로 `version`은 1 그대로(§4.5: 필드 추가·완화는 호환). 설계서 §4.2·§4.3 표와 같은 커밋.

| # | 변경 | 이유(엔진 사실) |
|---|---|---|
| K1 | `CommissionCalculated.policySummary.productName`·`insuredMasked` → 선택 | 엔진에 계약 마스터·고객 데이터가 없다(`POLICY_EVENT`: `policy_no`·`product_key`·`insurer_cd`·`contract_date`·`monthly_premium`뿐). 화면은 없으면 `productKey` 표시 — §8 한 줄 |
| K2 | `CommissionCalculated.basis.rate`·`payoutRate`·`installmentNo` → 선택, `ruleVersionIds`만 필수 | `applied_rate`·`installment_no`는 NULL 가능, 지급률은 typed 컬럼이 아니다. §14.0 "라인 근거 실제 수치"는 **있을 때 보여 준다**로 |
| K3 | `AgentSnapshot.grade`·`role` → 선택 | 등급 이력 없는 설계사 가능, 엔진에 역할 개념 없음(오버라이드 수급자는 `ORG`). 역할은 Phase 3 `identity_link`에서 — §9 한 줄 |
| K4 | `StatementConfirmed.clawbackOffset`·`withholdingTax`·`net` → 선택 | 원천세·상계는 마감이 아니라 **지급 런**에서 계산된다. 마감 시점 참인 것은 `gross`·`adjustment`·`closedAt` |
| K5 | `PayoutCompleted`에 선택 필드 추가: `runSeq`(integer ≥ 1), `gross`, `clawbackOffset`, `receivableOffset`, `carriedReceivable`, `adjustment`, `payable`, `withholdingTax`, `net`(전부 integer). `paidAmount` = `net` | 지급 런이 세액·상계를 확정한다. `receivableOffset`(기존 채권 자동 상계)은 `clawbackOffset`(그 달 환수 라인 합)과 **다른 것** — 둘 다 `description`에 정의 |
| K6 | 금액 뜻을 `description`에: `amount` = 한도 삭감 뒤·분급 분할 뒤 지급 예정액, 요청액 = `amount + limitCutAmt`, 이연분은 `DeferralScheduled`(`CURVE`/`LIMIT_DEFER`) | 엔진 `LimitGateStep`·`DeferralSplitStep`에서 확인(DD-2 확정) |
| K7 | `StatementConfirmed`·`StatementEstimated`·`PayoutCompleted`의 `gross`·`clawbackOffset`·`adjustment` 정의를 `description`에: `gross` = Σ `commType ≠ CLAWBACK` 라인(**전 상태 합산** — 취소 원본·reversal 모두), `clawbackOffset` = Σ `CLAWBACK` 라인(≤ 0), `adjustment` = Σ 그 달 귀속 `AdjustmentPosted` | 포털 `earnings_month` 합산과 **같은 정의**여야 §6.6 대사가 성립한다 — §5 주석도 같은 문장으로 |
| K8 | `engine-internal.openapi.yaml`: `FeedResponse`에 **필수 `tenantId`**(`TenantId` 정규식), `422 AFTER_BEYOND_HEAD`(Problem `{code, message}`), `limit` 1~1000 기본 200 명시 | 엔진은 테넌트별 인스턴스 — 잘못된 `engine_base_url`로 다른 테넌트를 읽는 사고를 응답에서 막는다(C9 아래) |
| K9 | `PolicyEventObserved.eventType` 열거 설명에 "`REINSTATE` = 엔진 `REVIVE`; `PAYMENT_OVERDUE`는 우리 엔진이 발행하지 않는다(다른 공급원용)" | 엔진 `EventType`은 NEW/PAYMENT/CANCEL/LAPSE/REVIVE/REDUCE/WITHDRAW/CORRECT |

샘플 17종은 그대로 통과해야 한다(필수 완화·추가뿐). `CHECKSUMS` 재생성, `EventSchemaTest`에 "선택 필드 없는 변형 통과" 케이스 추가(K1~K4 각 1건). 설계서 §4.2·§4.3·§6.2 상태 표("확정 — 세후는 지급 시 확정")·§12(E1 범위에 피드 포함, E2 = `StatementEstimated`·시뮬레이션·명세) 반영. DECISIONS에 **DD-4**(엔진에 없는 데이터를 필수로 둔 설계서 결함 — 설계 시 엔진 스키마와 대조하지 않았다). 이 PR의 병합 커밋이 **엔진 E1의 `UPSTREAM`** 이다 — 해시를 보고에.

## 1. 결정

| # | 결정 | 근거·조건 |
|---|---|---|
| D-1 | **`payload_hash`**: V3 `ingest_event.payload_hash CHAR(64)`(JCS SHA-256 hex, `CHECK` 정규식) + `ingest_event.payload`는 저장하지 않는다. 멱등 판정: 같은 `eventId`·같은 해시 → `SKIPPED`; 같은 `eventId`·**다른 해시** → 독 이벤트(아래 D-4) | Phase 0 수용 심사 Q4. 해시는 `platform-canonical`(`Canonicalizer`·`Sha256`) — Envelope 전체가 아니라 **payload**(`eventId`·`seq`는 키, `occurredAt`은 사실의 시각이라 재발행 시 같아야 하므로 해시 대상에 넣을지 계획에서 판단 — 넣으면 재발행 시각 차이가 독으로 잡히고, 빼면 시각 정정이 조용히 지나간다; 권장: **payload + type + version + occurredAt**, `seq` 제외) |
| D-2 | **seq 규칙(§6.1)**: 기대값 `last_seq + 1`보다 작으면 `SKIPPED(REPLAYED)`(단, `eventId`가 처음 보는 것이면 **독** — 과거 번호에 새 이벤트가 오는 것은 발행자 이상), 같으면 적용, 크면 **갭 → `FAILED(SEQ_GAP)`** 커서 정지 | 엔진 E1 D-1이 갭 없음을 보장하므로 갭은 결함 신호다 |
| D-3 | **`version` > 지원 최대** → 그 테넌트 `PAUSED(UNSUPPORTED_VERSION)`, 운영 알림 | §4.5 |
| D-4 | **독 이벤트** = 스키마 위반(포털 Envelope allOf core + payload — 수신 시 검증)·`type ↔ kind` 불일치·같은 `eventId` 다른 해시·과거 번호의 새 `eventId`·**응답 `tenantId` ≠ 테넌트**(C9) → 트랜잭션 롤백, `ingest_event`에 `FAILED` + `error`(**별도 트랜잭션**), 커서 `FAILED(reason)`, 그 테넌트 워커 정지. 다른 테넌트는 계속 | 격리 테스트 필수(C5) |
| D-5 | **이벤트마다 트랜잭션 하나**: `ingest_event` insert → `Projector.apply` → `ingest_cursor` 갱신, 전부 `TenantContext.runWith(tenant)` 안(`TenantSessionBinder`가 RLS 설정). 배치 안에서 한 건 실패 시 그 건에서 멈추고 앞선 건은 커밋된 채 | §6.1. 워커는 테넌트당 하나 — PostgreSQL **advisory lock**(`pg_try_advisory_xact_lock(hash(tenant_id))`)으로 인스턴스 2개가 떠도 한 테넌트를 둘이 처리하지 않는다(테스트: 두 워커 동시 실행 → 적용 1회) |
| D-6 | **`Projector` 포트**(`portal-projection`, Spring 무의존): `apply(TenantId, Envelope) → ProjectionResult`. Phase 1 구현은 `RecordingProjector`(적용한 `(seq, eventId, type)`을 인메모리/`ingest_event`로만) — 뷰 테이블은 건드리지 않는다. 알림 파생 포트도 **시그니처만**(Phase 6) | Phase 2가 구현을 바꿔 끼운다 |
| D-7 | **피드 클라이언트 2종, 계약 스위트 하나**: `HttpFeedClient`(OpenAPI 계약, 서비스 토큰 — 값은 `SecretSource`/환경변수, 저장소에 0)와 `FileFeedAdapter`(JSONL, 부록 B 데모·테스트). 같은 `FeedClient` 포트, 같은 계약 테스트 상속(순서·갭·`tenantId`·422·빈 응답·`limit`). HTTP는 **엔진 없이** 스텁 서버(계약 응답)로 시험 | 엔진 E1이 끝나기 전에도 Phase 1이 닫힌다. 실 엔진 대조는 E1 수용 뒤 **E2E 1건**(별도, 보고서 §다음) |
| D-8 | **운영 명령**(CLI — `portal-app` 프로파일 `cli`): `ingest status [--tenant]`, `ingest resume --tenant X --from-seq n`(상태 `FAILED/PAUSED` → `RUNNING`, 커서를 n으로 — **n은 마지막 `APPLIED` seq 이하만 허용**, 그보다 크면 거부: 건너뛰기는 재구축으로만), `ingest pause --tenant X`. 전 건 `audit_access`(actor = OS 사용자 또는 `--actor`, 필수) | §6.1 "재처리 명령은 운영자 전용, `audit_access` 기록" |
| D-9 | **지표**: `ingest_lag_seq`(= `headSeq − last_seq`)·`ingest_lag_seconds`·`ingest_state` 테넌트 라벨, Micrometer — Phase 7이 대시보드로 | §10 |
| D-10 | **poller 주기**는 테넌트 파라미터(`tenant.params.poll_interval_seconds`, 기본 10, 범위 5~30 — §3.4) + 전역 기본값. 적용 실패 뒤 **백오프 없음**(정지가 규약) | — |
| D-11 | **마이그레이션**: `V3__ingest_payload_hash.sql`(컬럼 + CHECK + `tenant.params` 기본에 `poll_interval_seconds` 추가는 **하지 않는다** — 기본 키 집합은 §14.0 셋이 정본, 주기는 없으면 전역 기본) | V1·V2 불변. **데이터 마이그레이션이 생기면** `portal_migrator`도 FORCE RLS를 따르므로 테넌트별 `set_config` 뒤 실행(Phase 0.1 B1) |

## 2. 작업 목록

1. **계획** `docs/phase-01-계획.md`: §0 PR 결과(병합 커밋), D-1 해시 범위 판단, 상태 기계(`RUNNING/PAUSED/FAILED/REBUILDING` × 전이와 사유 어휘 — `ingest_cursor.error`는 `{code}: {detail}`), 독 이벤트 분류 표, 트랜잭션 경계 그림, 두 어댑터 계약 스위트 항목, CLI 인가(Phase 3 전이라 OS 사용자 신뢰 — 한계 명시), 완료 기준 ↔ 테스트, 주입 ID. **승인 뒤 착수.**
2. **스키마·도메인**: V3, `IngestState` 전이 규칙(열거 + 전이 표 테스트), `IngestEventRecord`·`CursorRecord`, `Projector`·`FeedClient` 포트, `Envelope` 파싱(모르는 필드 보존·무시 — Jackson `FAIL_ON_UNKNOWN_PROPERTIES=false`를 **이 역직렬화기에만**, 앱 전역은 Phase 3에서 반대로).
3. **피드 클라이언트 2종 + 계약 스위트**(D-7).
4. **적용기**(`IngestApplier`): D-1~D-5 전부. 수신 검증은 Phase 0 `EventSchemaTest`가 쓰는 같은 스키마·같은 IRI 해석(원격 0). `ingest_event.status` 어휘 `APPLIED/SKIPPED/FAILED` + `skip_reason`은 `error` 컬럼 재사용(`REPLAYED`).
5. **워커·스케줄·advisory lock**(D-5·D-10), 지표(D-9).
6. **CLI**(D-8) + `audit_access`.
7. **결정론·재시작**: 같은 JSONL을 두 번(중간 재시작 포함) 넣어 `ingest_event`·`ingest_cursor`가 같음(바이트 비교는 Phase 2 뷰에서 — 여기선 두 표). 부록 B 생성기는 Phase 8이므로 **A-1 샘플 13 + 4를 JSONL로 묶은 소형 스트림**과 변형 스트림(중복·역순·갭·독)을 `portal-ingest/src/test/resources/streams/`에.
8. **문서**: 설계서 §4.1 "엔진 측 테이블" DDL 스케치를 **엔진 설계서 §6.8 참조 + 피드 규약**으로 바꾸고(순번 부여기 — seq는 커밋된 행에만, 갭 없음의 근거는 엔진 쪽), §5 V3, §6.1 D-1~D-5, §12(E1에 피드 포함), `CLAUDE.md`(인제스트 규약 한 절: 독 이벤트는 테넌트 정지·커서는 적용과 같은 트랜잭션·해시 범위), DECISIONS, 보고서, 태그, PR.

## 3. 완료 기준

| # | 기준 | 증명 | 주입 |
|---|---|---|---|
| C1 | 중복(`eventId`·해시 같음) → `SKIPPED`, 프로젝터 호출 0, 커서 불변 | `IngestApplierTest` | — |
| C2 | 역순(작은 seq, 아는 `eventId`) → `SKIPPED(REPLAYED)`; 작은 seq·새 `eventId` → 독 | 〃 | J1 역순 새 `eventId`를 적용하게 → 실패 |
| C3 | 갭 → `FAILED(SEQ_GAP)`, 커서 그대로, 뒤 이벤트 미적용 | 〃 + `IngestWorkerIT` | J2 갭 건너뛰고 적용 → 실패 |
| C4 | 재시작 뒤 커서 정합(배치 중간 실패 → 앞선 건 커밋·뒤 건 미적용 → 재개 시 정확히 이어짐) | `IngestWorkerIT`(실제 PostgreSQL, 중간에 예외 주입) | J3 커서를 프로젝터보다 먼저 갱신 → 실패 |
| C5 | **테넌트 격리**: T1에 독 이벤트, T2는 같은 라운드에 정상 적용·전진; T1 `FAILED` 뒤에도 T2 계속 | `IngestIsolationIT` | J4 독 처리에서 전역 플래그 → 실패 |
| C6 | 스키마 위반·`type↔kind` 불일치 → 독(롤백 + `FAILED` 행은 별도 트랜잭션에 남음) | `IngestApplierTest` + IT | J5 수신 검증 제거 → 실패 |
| C7 | 같은 `eventId` 다른 해시 → 독 | 〃 | J6 해시 비교 제거 → 실패 |
| C8 | `version` > 최대 → `PAUSED(UNSUPPORTED_VERSION)` | 〃 | — |
| C9 | 응답 `tenantId` ≠ 테넌트 → 아무것도 적용하지 않고 `FAILED(TENANT_MISMATCH)` | 피드 계약 스위트(HTTP·파일 둘 다) | J7 검사 제거 → 실패 |
| C10 | `resume`가 `APPLIED` 최대 seq보다 큰 n을 거부, 성공·거부 모두 `audit_access` | `IngestCliIT` | J8 감사 생략 → 실패 |
| C11 | 두 워커 동시 실행 → 각 이벤트 적용 1회 | `IngestWorkerIT`(advisory lock) | J9 락 제거 → 중복 적용 검출 |
| C12 | 두 어댑터가 같은 계약 스위트 통과; HTTP는 서비스 토큰 없이 401 처리·422 처리 | `FeedClientContractTest` × 2 | — |
| C13 | 결정론: 같은 스트림 2회(재시작 포함) → `ingest_event`·`ingest_cursor` 동일 | 7단계 | — |
| C14 | Phase 0 무손상(아키텍처 규칙 — `portal-ingest`는 `platform-spring` 참조 0, `TenantScopedRepository` 하위만 DB 접근), `TZ=UTC` check, CI build·no-docker, 주입 ID 단위, 평문 스캔(서비스 토큰 센티널 포함) | CI 아티팩트 | J10 토큰을 로그에 출력 → 스캔 실패 |

## 4. 하지 말 것

프로젝터 뷰 변환·알림 규칙(Phase 2·6). 조회 API·인증 필터(Phase 3). 브로커·Redis. 엔진 저장소 수정(E1은 별도 지시). 설계서에 없는 테이블·컬럼(V3 `payload_hash` 외). 갭을 건너뛰는 옵션(재구축으로만). V1·V2 수정. 포털 계약의 **의미** 변경(§0은 완화·추가뿐).

## 5. 보고 형식

Phase 0과 같게. 추가로: §0 PR 병합 커밋 해시(엔진 E1 `UPSTREAM`), 상태 기계 표, 독 이벤트 분류 표, 스트림 파일 목록, 두 어댑터 스위트 건수, 다음 Phase 질문(Phase 2 프로젝터 — 부록 A-1 골든 뷰, 재구축 그림자 스키마와 FORCE RLS·`portal_migrator`의 관계, E1 수용 뒤 실 엔진 E2E 1건).
