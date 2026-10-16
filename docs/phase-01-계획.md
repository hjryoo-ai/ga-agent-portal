# Phase 1 계획 — 인제스트 (지시문 `docs/phase-01-지시문.md` v1.0)

> 브랜치 `work/phase-1`(`f30d026`에서). **승인 뒤 착수.** 승인 전에는 이 계획 커밋만 있다.

## 0. 먼저 결정이 필요한 것 (요약)

| # | 질문 | 권장 | 대안 |
|---|---|---|---|
| Q1 | 해시 범위(D-1) | JCS(`{type, version, occurredAt, aggregate, payload}`) — 지시문 권장에 **`aggregate`를 더한다**. `seq`·`eventId`와 Envelope의 모르는 필드는 뺀다 | 지시문 권장 그대로(`aggregate` 제외) |
| Q2 | `SKIPPED`를 행으로 남기나 | **남기지 않는다** — 건너뜀은 적용기 결과(지표·로그)이고 `ingest_event`·`ingest_cursor`는 바꾸지 않는다. `SKIPPED`는 V1 열거에 남겨 둔다 | 행을 남긴다 — 같은 `eventId`의 행이 이미 있어 PK 때문에 불가. 별도 표가 필요하다(지시문 §4 금지) |
| Q3 | 테넌트 목록(워커가 돌 테넌트) | **전용 롤 `portal_directory`** + `tenant`의 이 롤 전용 SELECT 정책(`tenant_id` 컬럼만), 플랫폼 `TenantDirectoryReader`로 읽는다 — ga-disclosure `disclosure_operator`(V4)와 같은 방식. V3에 정책·컬럼 GRANT, `init-roles.sql`에 롤 | 설정 파일의 테넌트 목록(스키마 변경 없음 — 대신 `tenant` 표와 따로 놀 수 있다) |
| Q4 | 피드 401 | 그 테넌트 **`PAUSED(FEED_UNAUTHORIZED)`**(설정 문제 — 데이터 결함이 아니다), 운영자가 토큰을 고친 뒤 `resume` | `FAILED` / 재시도만 |
| Q5 | 같은 `eventId`가 **다른 `seq`**로 다시 오면 | **독**(`EVENT_SEQ_CONFLICT`) — 지시문 D-2·D-4에 없는 경우라 더한다 | 해시가 같으면 `SKIPPED` |
| Q6 | 포트 위치 | 포트는 `portal-ingest`, DB 어댑터는 **`portal-app`**(인프라 저장소에 위임). `portal-infra`는 `portal-ingest`를 볼 수 없다(Phase 0 규칙 (a) — infra는 app만 접근). 기록 타입(`IngestEventRecord`·`CursorRecord`)은 `portal-domain` | 규칙 (a)를 고쳐 infra → ingest 허용(규칙 의미 변경 — 권하지 않는다) |
| Q7 | 서비스 토큰 출처 | 테넌트별 **파일**(`portal.ingest.token-dir/<TENANT>` — 컨테이너 시크릿 마운트), 값은 어떤 로그·예외·감사에도 쓰지 않는다. 시험은 허구 토큰 + 평문 스캔 센티널(J10) | 환경변수 `PORTAL_ENGINE_TOKEN_<TENANT>` |

## ① §0 계약 v1.1 결과

- PR #2 병합 커밋 **`f30d026ae51cb7885e2536b3afc1b5a038c3a140`** — 엔진 E1의 `contracts/shared/ga-agent-portal/UPSTREAM`. CI run 38953993935(build·no-docker success, 2,274건), 병합 뒤 `main` run 38954273360 success.
- 지시문 문구와 다르게 적은 것 하나(**CI-1**, 확인 요청): K7 `gross`를 "Σ `commType ≠ CLAWBACK`"이 아니라 기존 §5 정의 "Σ `CommissionCalculated` 라인 중 commType ∉ {CLAWBACK, ADJUSTMENT}"로 — 포털은 `AdjustmentPosted`를 ADJUSTMENT 라인으로 저장하므로 글자대로면 정정이 두 번 센다.
- 남은 것: **O-1**(`agent_profile.role` NOT NULL ↔ 선택 `role` — Phase 2), **O-2**(엔진 `gross_net`은 CLAWBACK 포함 순합 — E1 계획).

## ② D-1 해시 범위 (Q1)

`payload_hash` = SHA-256 hex(`Canonicalizer.canonicalize({type, version, occurredAt, aggregate, payload})`) — `platform-canonical`.

| 필드 | 넣나 | 이유 |
|---|---|---|
| `payload` | 넣는다 | 사실 그 자체. payload의 모르는 필드도 들어간다 — 같은 `eventId`로 필드가 더해진 재발행은 다른 사실로 본다 |
| `type`·`version` | 넣는다 | 같은 payload가 다른 타입·판으로 오면 해석이 바뀐다 |
| `occurredAt` | 넣는다(지시문 권장) | 사실의 시각 — 재발행에서 같아야 한다. 빼면 시각 정정이 조용히 지나간다 |
| `aggregate` | **넣는다(권장에 더함)** | `{kind, id}`가 달라지면 다른 집합의 사실이다(포털이 그 id로 upsert한다). 같은 `eventId`에 다른 집합은 발행자 이상 |
| `seq` | 뺀다 | 키(커서 위치)이고 Q5에서 따로 대조한다 |
| `eventId` | 뺀다 | 키 |
| Envelope의 모르는 필드(`traceId` 등) | 뺀다 | §4.5 — 소비자는 무시한다. 전달 메타데이터가 재발행마다 달라도 독이 되지 않게 |

`ingest_event.payload`는 저장하지 않는다(D-1). V3 `payload_hash CHAR(64) NOT NULL CHECK (payload_hash ~ '^[0-9a-f]{64}$')` — 기본값 없이 `NOT NULL`을 더하므로 행이 있으면 마이그레이션이 실패한다(제약 검사는 RLS를 따르지 않는다 — B1의 "마이그레이터는 0행을 본다"와 무관하게 안전).

## ③ 상태 기계

`ingest_cursor.state` × 전이. 사유는 `ingest_cursor.error` = `{code}: {detail}`(detail에 `seq`·`eventId`·기대값, **payload 값은 넣지 않는다**).

| 지금 | 사건 | 다음 | `error` code |
|---|---|---|---|
| (없음) | 첫 폴링 | `RUNNING`(last_seq 0) | — |
| `RUNNING` | 적용 성공 | `RUNNING`(last_seq 전진) | — |
| `RUNNING` | 건너뜀(REPLAYED) | `RUNNING`(불변) | — |
| `RUNNING` | 갭 | `FAILED` | `SEQ_GAP` |
| `RUNNING` | 독(④ 표) | `FAILED` | ④의 code |
| `RUNNING` | 응답 `tenantId` ≠ 테넌트 | `FAILED` | `TENANT_MISMATCH` |
| `RUNNING` | 422 | `FAILED` | `AFTER_BEYOND_HEAD` |
| `RUNNING` | `version` > 지원 최대 | `PAUSED` | `UNSUPPORTED_VERSION` |
| `RUNNING` | 401 | `PAUSED` | `FEED_UNAUTHORIZED`(Q4) |
| `RUNNING` | 네트워크 오류·5xx·시간 초과 | `RUNNING`(불변, 다음 주기에 다시) | — (지표 `ingest_feed_errors`) |
| `RUNNING` | `ingest pause` | `PAUSED` | `OPERATOR_PAUSE` |
| `FAILED`·`PAUSED` | `ingest resume --from-seq n`(n ≤ 마지막 APPLIED seq) | `RUNNING`(last_seq = n, error NULL) | — |
| `FAILED`·`PAUSED` | `resume` n > 마지막 APPLIED seq | 불변(거부, 감사) | — |
| `RUNNING` | `resume` | 불변(거부 — 이미 실행 중) | — |
| `REBUILDING` | — | Phase 2(재구축)가 정한다. Phase 1에서는 들어가지도 나오지도 않는다 | — |

- `FAILED`·`PAUSED`·`REBUILDING` 테넌트는 워커가 건너뛴다(피드 호출 0).
- 전이 규칙은 `portal-domain`의 `IngestTransitions`(열거 × 사건 → 다음 상태 또는 거부)에 두고 표 전체를 시험한다(`IngestTransitionsTest` — 표의 모든 행 + 표에 없는 조합은 거부).
- `resume --from-seq n`: 커서를 n에 두므로 다음 요청은 `afterSeq = n`이다. n+1~마지막 APPLIED는 이미 아는 `eventId`라 `SKIPPED(REPLAYED)`, 그다음이 실패했던 이벤트다. 건너뛰기(n > 마지막 APPLIED)는 재구축으로만(지시문 §4).

## ④ 독 이벤트 분류

판정 순서 = 표의 순서(앞에서 걸리면 뒤는 보지 않는다). "기록"은 실패 뒤 **별도 트랜잭션**에서 쓰는 것이다(본 트랜잭션은 롤백).

| # | 판정 | code | 커서 | `ingest_event` 기록 |
|---|---|---|---|---|
| 0 | 응답 `tenantId` ≠ 테넌트(배치 단위, 이벤트를 보기 전) | `TENANT_MISMATCH` | `FAILED` | 없음(아무것도 적용하지 않는다) |
| 1 | Envelope 핵심(공유 사본) 위반 — `eventId`·`seq`·`type`·`version`·`occurredAt`·`aggregate`·`payload` 형식 | `SCHEMA_VIOLATION` | `FAILED` | 없음 — 키(`eventId` UUID·`seq`)를 믿을 수 없다 |
| 2 | `version` > 지원 최대(1) | `UNSUPPORTED_VERSION` | **`PAUSED`**(D-3) | 없음 |
| 3 | `type` ↔ `aggregate.kind` 짝 불일치 | `TYPE_KIND_MISMATCH` | `FAILED` | `FAILED` 행(키는 핵심이 보증) |
| 4 | 포털 Envelope·payload 스키마 위반(위 셋 밖) | `SCHEMA_VIOLATION` | `FAILED` | `FAILED` 행 |
| 5 | 아는 `eventId`, 저장된 해시 ≠ 새 해시 | `HASH_MISMATCH` | `FAILED` | 없음 — 기존 `APPLIED` 행을 지키고 커서 `error`에만 |
| 6 | 아는 `eventId`, 저장된 `seq` ≠ 새 `seq`(Q5) | `EVENT_SEQ_CONFLICT` | `FAILED` | 없음(위와 같은 이유) |
| 7 | 모르는 `eventId`, `seq` < 기대값 | `PAST_SEQ_NEW_EVENT` | `FAILED` | `FAILED` 행 |
| — | 아는 `eventId`(`APPLIED`), 같은 해시·같은 `seq` | (건너뜀 `REPLAYED`) | 불변 | 없음(Q2) |
| — | 모르는 `eventId`, `seq` > 기대값 | `SEQ_GAP` | `FAILED` | 없음 — 결함은 빠진 쪽이다 |
| 8 | 프로젝터 예외(Phase 2부터 불변식 위반) | `PROJECTOR_ERROR` | `FAILED` | `FAILED` 행 |

- `FAILED` 행이 이미 있는 `eventId`(재개 뒤 다시 온 실패 이벤트)는 "모르는 것"으로 보고 다시 판정한다 — 적용되면 그 행을 `APPLIED`로 바꾼다(같은 트랜잭션), 또 실패하면 `error`·`received_at`을 갱신한다.
- `error` 문자열에 payload 값·토큰을 넣지 않는다(평문 스캔). 스키마 위반은 검증기 메시지의 JSON 경로·키워드만.

## ⑤ 트랜잭션 경계

```
워커 1회(테넌트 T, 주기 = T의 poll_interval_seconds — 기본 10, 범위 5~30, 밖이면 전역 기본 + 경고):
  ┌ tx₀ [TenantContext.runWith(T) · TenantSessionBinder] 커서 읽기(없으면 만들기) · 상태 RUNNING이 아니면 끝
  └ commit
  피드 호출(트랜잭션 밖) fetch(T, afterSeq = last_seq, limit)          ← 0번 판정(tenantId), 422·401
  이벤트마다(순서대로):
  ┌ txₑ [runWith(T)]
  │  pg_try_advisory_xact_lock(ns, hashtext(T)) — 못 잡으면 롤백하고 이번 회차 끝(다른 워커가 처리 중)
  │  커서 다시 읽기(상태·last_seq — 이 트랜잭션 안의 값으로만 판정)
  │  판정 1~7 → ingest_event insert(또는 FAILED 행 → APPLIED) → Projector.apply → ingest_cursor 갱신
  └ commit      예외/독 → rollback →  ┌ txₓ [runWith(T)] FAILED 행(④) · 커서 FAILED/PAUSED + error ┐ → 이 회차 끝
                                        └ commit                                                      ┘
```

- 한 건 실패면 그 건에서 멈추고 앞선 건은 커밋된 채다(D-5).
- 잠금은 이벤트 트랜잭션마다 잡는다(`xact` — 커밋·롤백 때 풀린다). 판정은 잠금을 잡은 뒤 읽은 커서로만 하므로 두 워커가 같은 배치를 들고 있어도 각 이벤트는 한 번 적용된다. 잠금 키는 2-인자형(`ns` = 포털 인제스트 상수, `hashtext(tenant_id)`) — 해시 충돌은 두 테넌트를 직렬화할 뿐 정확성에 영향이 없다.
- **J9가 실제로 무엇을 잡는지**: 잠금이 없으면 두 번째 워커가 같은 `eventId`를 넣다가 PK에서 기다린 뒤 고유 위반으로 실패하고, 그것을 독으로 처리해 **멀쩡한 테넌트를 `FAILED`로 만든다**(또는 프로젝터를 두 번 부른다). 시험은 "각 이벤트 프로젝터 호출 1회 **그리고** 테넌트 `RUNNING`·`FAILED` 행 0"을 단언하고, 첫 워커를 프로젝터 안에서 래치로 붙잡아 경합을 실제로 만든다.

## ⑥ 모듈·포트 (Q6)

| 위치 | 것 | Spring |
|---|---|---|
| `portal-domain` | `IngestTransitions`, `IngestEventRecord`(tenant·eventId·seq·type·receivedAt·status·error·payloadHash), `CursorRecord`(Phase 0 `IngestCursor`를 옮긴다), `IngestErrorCode`(④·③의 code — 닫힌 열거) | 없음 |
| `portal-projection` | `Envelope`(record — payload는 Jackson `JsonNode`, 모르는 필드 보존), `Projector` 포트 `apply(TenantId, Envelope) → ProjectionResult`, `NotificationDeriver` 포트(시그니처만, Phase 6), `RecordingProjector`(적용한 `(seq, eventId, type)` 인메모리) | 없음 |
| `portal-ingest` | `FeedClient` 포트·`FeedPage`·예외(`FeedUnauthorized`·`AfterBeyondHead`·`FeedTenantMismatch`·`FeedUnavailable`), `HttpFeedClient`(JDK `HttpClient`), `FileFeedAdapter`(JSONL), `EnvelopeReader`(이 역직렬화기에만 `FAIL_ON_UNKNOWN_PROPERTIES=false`), `EnvelopeValidator`(포털·핵심 스키마 — 클래스패스 사본, IRI는 클래스패스로만, 원격 0), `PayloadHasher`, `IngestApplier`, `IngestWorker`, 포트 `IngestStore`(이벤트 행·커서), `TenantTransactions`(`inTransaction(T, work)`), `IngestLock`, `ServiceTokenSource`, `IngestMetrics` | 없음(시험이 인메모리 포트로 빠르다) |
| `portal-infra` | V3, `IngestEventRepository`(새), `IngestCursorRepository`(커서 갱신에 `IngestErrorCode`), `IngestLockRepository`(`SELECT pg_try_advisory_xact_lock(...)` — `:tenantId` 바인드), `AuditAccessRepository`(insert만) | 저장소 |
| `portal-app` | 포트 어댑터(인프라 저장소 위임), `TransactionTemplate` + `TenantContext.runWith` 구현, 스케줄(1초 틱 → 기한이 된 테넌트), Micrometer 지표, CLI(`cli` 프로파일), 토큰 파일 소스 | 조립 |

- 스키마 사본: `portal-ingest`의 `processResources`가 저장소 `contracts/events/v1/**`·`contracts/shared/ga-disclosure/events/v1/**`를 `contracts/…`로 넣는다(복사본은 빌드 산출물 — 원본은 하나). 시험: 클래스패스 바이트 = 저장소 바이트.
- `portal-ingest`는 `json-schema-validator`(3.0.6, Phase 0과 같은 판)·`platform-canonical`·Jackson을 main에 갖는다. `platform-spring` 참조 0(규칙 (e), C14).
- 계약 v1.1의 `FeedResponse` 형태(필수 `tenantId`·정수 커서)는 클라이언트가 응답을 받을 때 검증한다(OpenAPI 사본의 `FeedResponse` — 이벤트 본문은 적용기가 따로 검증).

## ⑦ 피드 클라이언트 계약 스위트 (D-7)

`FeedClientContract`(추상, `portal-ingest` testFixtures) — `HttpFeedClientTest`(JDK `HttpServer` 스텁이 계약 응답을 낸다)와 `FileFeedAdapterTest`가 상속한다.

| # | 항목 | 둘 다 |
|---|---|---|
| F1 | `seq` 오름차순 그대로 돌려준다(재정렬·제거 없음 — 갭도 그대로, 판정은 적용기) | ✓ |
| F2 | `nextSeq` = 돌려준 마지막 `seq`, 빈 응답이면 `afterSeq` | ✓ |
| F3 | `headSeq` | ✓ |
| F4 | `limit` 경계: 생략 → 200, 1·1000 통과, 0·1001은 클라이언트가 호출 전에 거부 | ✓ |
| F5 | 응답 `tenantId` = 요청 테넌트가 아니면 `FeedTenantMismatch`(C9) | ✓ |
| F6 | `afterSeq > headSeq` → `AfterBeyondHead`(422) | ✓ |
| F7 | 빈 응답(`events: []`) | ✓ |
| F8 | 응답이 `FeedResponse` 형태가 아니면(`tenantId` 없음·문자열 커서) `FeedUnavailable` | ✓ |
| H1 | 서비스 토큰을 `Authorization: Bearer`로 보낸다 / 토큰 없이 401 → `FeedUnauthorized` | HTTP |
| H2 | 5xx·연결 실패·시간 초과 → `FeedUnavailable`(커서·상태 불변) | HTTP |
| H3 | 예외 메시지·로그에 토큰 값 없음 | HTTP |

파일 어댑터 형식(부록 B 데모·시험): 첫 줄 머리 `{"tenantId": "...", "schemaVersion": 1}`, 다음 줄부터 Envelope 한 줄씩. `headSeq` = 파일의 최대 `seq`.

## ⑧ 스트림 파일 (`portal-ingest/src/test/resources/streams/`)

| 파일 | 내용 | 쓰는 시험 |
|---|---|---|
| `a1-golden.jsonl` | 샘플 01~17을 seq 순서로(머리 `GA01`) | C13 결정론, IT 기본 |
| `dup.jsonl` | 같은 이벤트 두 번(seq 3 반복) | C1 |
| `replay-known.jsonl` | 1~5 뒤 3 다시(같은 eventId·해시) | C2 |
| `replay-new.jsonl` | 1~5 뒤 seq 3에 새 eventId | C2 독 |
| `gap.jsonl` | 1~4, 6~ | C3 |
| `poison-schema.jsonl` | seq 5 payload 필수 필드 없음 | C6 |
| `poison-kind.jsonl` | seq 5 `type`↔`kind` 불일치 | C6 |
| `hash-mismatch.jsonl` | 1~5 뒤 seq 5와 같은 eventId·다른 amount | C7 |
| `version2.jsonl` | seq 5 `version: 2` | C8 |
| `other-tenant.jsonl` | 머리 `GA02`인 A-1 스트림 | C9 |

생성 규칙은 파일마다 머리말 없이 `streams/README.md` 한 표로(어느 샘플을 어떻게 바꿨는지). 샘플 원본이 바뀌면 `StreamFilesTest`가 실패한다(스트림의 기본 줄 = 샘플 바이트의 JSON 동치).

## ⑨ CLI (D-8)

- `portal-app` 프로파일 `cli`: `ingest status [--tenant T]`, `ingest resume --tenant T --from-seq n`, `ingest pause --tenant T`, 공통 `--actor A`(없으면 OS 사용자 `user.name`, 둘 다 없으면 거부).
- 전 건 `audit_access`(같은 트랜잭션): `actor_subject` = `cli:<actor>`, `actor_agent_id` NULL, `action` ∈ {`INGEST_STATUS`, `INGEST_RESUME`, `INGEST_RESUME_REJECTED`, `INGEST_PAUSE`}, `target` = `cursor:<T>@<n>`. `status`(테넌트 지정 없음)는 테넌트마다 한 행.
- **인가의 한계**: Phase 3 전이라 CLI는 실행한 OS 사용자를 믿는다 — 운영 호스트 접근 통제가 곧 인가다. 보고서·README에 적고, Phase 3에서 `identity_link` 역할로 바꾼다(ga-disclosure `--operator`와 같은 방향).
- 거부(`n` > 마지막 APPLIED seq, `RUNNING` 중 resume, 커서 없음)도 감사 행을 남기고 종료 코드 ≠ 0.

## ⑩ 지표 (D-9)

Micrometer(`portal-app`), 태그 `tenant`: `ingest_lag_seq` = `headSeq − last_seq`(마지막 폴링 값), `ingest_lag_seconds` = `lag_seq > 0`이면 `now − 커서 updated_at`, 따라잡았으면 0, `ingest_state`(상태별 0/1 게이지), `ingest_events_total{result=applied|skipped|failed}`, `ingest_feed_errors_total`. 시각은 주입된 `Clock`.

## ⑪ 완료 기준 → 시험·주입

| # | 시험 | 주입 |
|---|---|---|
| C1 | `IngestApplierTest`(인메모리 포트) — 중복: 결과 `SKIPPED`, 프로젝터 호출 0, 커서·행 불변 | — |
| C2 | 〃 — 역순 아는 eventId → `SKIPPED(REPLAYED)`; 역순 새 eventId → `PAST_SEQ_NEW_EVENT` | **J1** 판정 7 제거(새 eventId도 건너뜀) → 실패 |
| C3 | 〃 + `IngestWorkerIT` — 갭 → `FAILED(SEQ_GAP)`, 커서 그대로, 뒤 이벤트 미적용 | **J2** 갭을 건너뛰고 적용 → 실패 |
| C4 | `IngestWorkerIT`(PostgreSQL) — 배치 중간(seq 6) 프로젝터 예외 → 1~5 커밋·6~ 미적용·커서 5 → 재개 뒤 6부터 정확히 이어짐 | **J3** 커서를 프로젝터 앞에서 갱신 + 별도 커밋 → 실패 |
| C5 | `IngestIsolationIT` — T1 독, T2 같은 회차 정상 전진, T1 `FAILED` 뒤에도 T2 다음 회차 전진 | **J4** 독 처리에서 워커 전역 정지 플래그 → 실패 |
| C6 | `IngestApplierTest` + `IngestWorkerIT` — 스키마 위반·`type↔kind` → 독, 본 트랜잭션 롤백 + `FAILED` 행은 별도 트랜잭션에 남음 | **J5** 수신 검증 제거 → 실패. **J11** `FAILED` 행을 본 트랜잭션에서 쓰기(같이 롤백) → 실패 |
| C7 | 〃 — 같은 eventId 다른 해시 → `HASH_MISMATCH`, 기존 `APPLIED` 행 불변 | **J6** 해시 비교 제거 → 실패 |
| C8 | 〃 — `version: 2` → `PAUSED(UNSUPPORTED_VERSION)`, 행 0 | — |
| C9 | `FeedClientContract` F5(HTTP·파일) + `IngestWorkerIT` — `FAILED(TENANT_MISMATCH)`, 적용 0 | **J7** 클라이언트의 tenantId 대조 제거 → 실패 |
| C10 | `IngestCliIT` — resume n > 마지막 APPLIED 거부, 성공·거부·pause·status 모두 `audit_access` | **J8** 감사 생략 → 실패 |
| C11 | `IngestWorkerIT` — 두 워커 동시(래치로 경합 강제) → 각 이벤트 프로젝터 1회, 테넌트 `RUNNING`, `FAILED` 행 0 | **J9** 잠금 제거 → 실패 |
| C12 | `HttpFeedClientTest`·`FileFeedAdapterTest`(⑦ 스위트) + H1·H2 | — |
| C13 | `IngestDeterminismIT` — `a1-golden.jsonl` 두 DB에: ① 한 번에 ② 중간(seq 9 뒤) 워커 재시작 → 두 표 행 집합 동일(고정 `Clock`이라 시각까지) | — |
| C14 | 아키텍처 규칙 전부 + `portal-ingest` platform-spring 0, `TZ=UTC` check, CI build·no-docker, 평문 스캔 센티널에 허구 서비스 토큰 | **J10** 토큰을 로그에 출력 → `scanPlaintextLeaks` 실패 |

- 전이 표: `IngestTransitionsTest`. Q3을 고르면 `TenantDirectoryIT`(전용 롤이 `tenant_id`만 읽고 다른 컬럼·표는 권한 오류).
- 주입은 **ID 단위 11건**(J1~J11) — 커밋 본문에 주입 → 실패 → 제거.

## ⑫ 단계 — 단계마다 시험·주입·설계서 같은 커밋

1. 이 계획(승인 뒤 승인 보관 + 갱신 1커밋).
2. 스키마·도메인: V3(+ Q3이면 정책·GRANT, `init-roles.sql`), `IngestTransitions`·기록 타입·`IngestErrorCode`, `Envelope`·`Projector`, `EnvelopeReader`·`EnvelopeValidator`·`PayloadHasher`. 설계서 §5(V3)·§9(롤).
3. 피드 클라이언트 2종 + 계약 스위트 + 스트림 파일(J7).
4. 적용기(J1·J2·J5·J6·J11) + 인메모리 포트.
5. 인프라 저장소 + 앱 어댑터 + 워커·스케줄·잠금·지표(J3·J4·J9) + C13.
6. CLI + `audit_access`(J8).
7. 문서: 설계서 §4.1(엔진 측 DDL 스케치 → 엔진 설계서 §6.8 참조 + 피드 규약), §6.1(D-1~D-5·④·③), §12, `CLAUDE.md` 인제스트 한 절, DECISIONS, J10, 보고서, 태그, PR.

## ⑬ 하지 않는 것

프로젝터 뷰 변환·알림 규칙(Phase 2·6), 조회 API·인증(Phase 3), 브로커·Redis, 엔진 저장소 수정, 설계서에 없는 표·컬럼(V3 `payload_hash` 외 — Q3의 정책·GRANT는 표·컬럼이 아니지만 스키마 변경이라 승인을 묻는다), 갭 건너뛰기 옵션, V1·V2 수정, 계약 의미 변경.

## ⑭ 다음 Phase 질문 (보고서에서 다시)

- Phase 2: 부록 A-1 골든 뷰, 재구축 그림자 스키마와 FORCE RLS·`portal_migrator`(B1 — 테넌트마다 설정), O-1(`agent_profile.role`).
- E1 수용 뒤 실 엔진 E2E 1건(이 저장소의 `HttpFeedClient` → 엔진 `GET /internal/v1/events`).
