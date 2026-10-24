# 포털 Phase 6 계획 — 알림 파생·이의제기 (ga-agent-portal)

> 이 계획은 지시문 `docs/phase-06-지시문.md` v1.0(2026-10-24)에 대한 것이다. 브랜치는 `work/phase-6`(`main` = `3a24ce6`, Phase 5.1 병합)이다. **이 계획이 승인되기 전에는 계약·코드를 쓰지 않는다.**
>
> **돌이킬 수 없는 결정**(지시문 형식 절 — 계약 주 버전·마이그레이션 구조·공유 계약): 없다. 계약은 1.2.0(추가만), V7은 열·인덱스·`CHECK`·`DROP NOT NULL`뿐이고 표를 만들거나 지우지 않는다, 공유 계약(`contracts/shared/`)·엔진은 건드리지 않는다. 그래서 사용자가 직접 승인해도 된다.
>
> 읽은 것:
> - 지시문 전문, Phase 5 수용 심사 §4(Q1~Q6 — Q1은 지시문이 RM-4로 되돌렸다), 설계서 v1.8.1 §2·§3.3·§5·§6.1·§6.2·§6.5·§6.7·§7~§10·§12~§14.
> - 계약 `portal-api.openapi.yaml` 1.1.1, `contracts/events/v1/payloads/*` 13종(필수 필드·열거).
> - 포털 코드:
>   - 적용·재구축: `IngestApplier`(파생 호출 지점·예외 분류), `Rebuilder`(프로젝터만), `ViewReset`, `NotificationDeriver`·`NoopNotificationDeriver`, `ProjectionResult`, `ViewProjector`·`LimitProjector`·`DeferralProjector`·`FlagProjector`, `ViewStore`, 뷰 레코드(`LedgerRow`·`LineRow`·`DeferralRow`·`MonthRow`).
>   - 규칙: `query.rule.LimitStage`·`PolicyMask`, `sim.RateLimitRule`, `SyncStatusRepository.snapshot()`(임계치 bp).
>   - 운영: `IngestCliRunner`·`IngestCommands`·`OperatorAuthorization`.
>   - 시험·규칙: `PortalApiContractTest`(레코드 ↔ 스키마), `AuthzMatrixTest`·`docs/authz-matrix.md`, `StatusEnumDdlTest`, `SeedData`, `ArchitectureRulesTest`(규칙 (a)), `scanPlaintextLeaks`, `web/e2e/support/sentinels.mjs`.
>   - 마이그레이션 V1(`notification`·`inquiry` DDL)·V2(RLS·권한)·V6(형식 참고).
>   - E2E: `WebE2eHarness`(제어 포트), `web/e2e/run.mjs`, 스트림 `a1_portal_150000_e2`·`edges`·`ga01-neighbor`.
>   - 웹: `routes.ts`, `HomeScreen`·`LineScreen`·`StatementScreen`의 `ComingSoon` 진입점, `scripts/bundle-size.mjs`(`LAZY_SCREENS`).
> - 측정:
>   - `openapi-typescript` 7.13.0(웹이 고정한 판)으로 `if/then`과 `oneOf + discriminator`를 각각 생성해 봤다(P-4).
>   - 세 E2E 스트림에 §6.5 규칙을 손으로 돌려 어떤 알림이 나오는지 셌다(P-9 — 스크립트는 저장소 밖).

## 0. 먼저 결정이 필요한 것 (요약)

지시문 §3 질문(Q1~Q10)에 더해, 계획이 고른 것(Q11~Q30)도 승인 대상이다.

| # | 질문 | 권장 | 대안 |
|---|---|---|---|
| Q1 | 알림 문장의 자리 | **ⓑ + 금액 없음.** 문장은 `messages.ko.json`의 `type`별 템플릿 + `params`, 서버 문장 0.<br>• `params`에는 **금액을 넣지 않는다** — 무슨 일이 어느 계약·어느 달에 생겼는지(`ledgerId`·`calcId`·`policyMasked`·`ym`·`occurredOn`·`inquiryId`)만. 금액·상태는 `link` 화면이 지금 값으로, 그 화면의 라벨로 보인다.<br>• 그러면 "알림 시점 값"이라는 여섯째 라벨 종류가 생기지 않고, 지난 금액이 지금 금액처럼 보이는 일이 없다(확정 뒤 정정·환수로 바뀐다). C10의 "`<Amount>` + 라벨"은 "알림 템플릿·`params`에 금액 0"(계약 스캔 + Vitest)으로 증명한다.<br>• V7: `title`·`body`는 `DROP NOT NULL` + `CHECK (title IS NULL AND body IS NULL)` — 새 행은 비우고 응답에 내지 않는다 | ⓑ + 금액(`Won`) + 라벨 "알림 시점 값" · ⓐ 서버 문장 |
| Q2 | 한 전기로 두 경계 | **하나 — 가장 높은 단계.** A-1 seq 5가 바로 그 경우다(OK → FULL, WARN·CRIT을 건너뜀 — P-9) | 단계마다 하나 |
| Q3 | `POLICY_*` 키 | **ⓒ `POLICY_{type}:{eventId}`** | ⓐ `policyKey` · ⓑ `ledgerId` |
| Q4 | 파생이 보는 전 상태 | **프로젝터가 돌려준다.** `ProjectionResult`에 `Optional<Change>`(sealed — 아래 ④). 파생기는 뷰를 다시 읽지 않는다. 읽는 것은 테넌트 임계치 하나뿐이고 그것도 원장 전기일 때만 | 파생기가 다시 읽기 |
| Q5 | 문의 `category` | **지시문 권장 다섯**: `AMOUNT`·`LIMIT`·`CLAWBACK`·`DEFERRAL`(`calcId` 필수)·`OTHER`(`calcId` 선택). `calcId`는 그 설계사의 라인에서만 풀린다(아니면 같은 404 `MISSING` — 행 0, 리밋 판정 전) | — |
| Q6 | 쓰기 성공의 감사 | **설계사 쓰기는 감사 없음**(행·`read_at`이 기록). **운영 CLI는 감사**한다 — §6.1의 기존 CLI 규약(전 건 `audit_access`, 거부도)과 같게: `INQUIRY_LIST`·`INQUIRY_ANSWER`·`INQUIRY_CLOSE`(+ `_REJECTED`), `target` = `inquiry:<id>`, `detail`에 본문 0 | 설계사 쓰기도 감사 |
| Q7 | 알림 보존 1년 | **삭제 잡 없음 + 운영 SQL**(`docs/운영.md` §7 — 시뮬레이션 로그와 같은 방식). 기준 `created_at`, 읽음 여부와 무관하게 전부 | 읽은 것만 |
| Q8 | 홈 알림 콜 | **`GET /me/notifications?size=3` 한 콜**(홈 다섯째 — 안읽음 수는 응답의 `unreadCount`). `/me`는 그대로 | `/me`에 `unreadNotifications` |
| Q9 | E2E의 CLI 호출 | **하네스 제어 `POST /cli/<tenant>/answer/<inquiryId>`·`POST /cli/<tenant>/rebuild`가 실 CLI를 자식 프로세스로 띄운다.**<br>• 명령은 운영자와 같다: `java -cp <하네스 클래스패스> com.ga.portal.app.PortalApplication --spring.profiles.active=cli inquiry answer --tenant=… --id=… --answer-file=<상태 디렉터리의 파일>`. 같은 JVM 안 호출은 `System.out`·`user.name`을 공유해 시험이 행위자·출력을 흉내 내게 된다.<br>• 행위자는 하네스를 띄운 OS 사용자 그대로다. 하네스가 테넌트마다 `cli:<user>` OPERATOR 링크를 `docs/운영.md` §3의 SQL로 넣는다.<br>• 답변 파일은 허구 고정 문장이고 상태 디렉터리(권한 700)에 쓴다 — 인자·환경 변수에는 경로만 있다.<br>• DB 주소는 포털 프로세스와 같은 길(`PORTAL_DB_URL`)이다.<br>• 자식의 표준 출력은 버린다(파일·아티팩트에 남기지 않는다). 하네스는 종료 코드만 돌려준다 | 같은 JVM의 진입점 호출 |
| Q10 | 다이제스트 | **v2로 닫는다.** `notification_digest` 키는 그대로 false, 코드 0. §14.1 #3의 다이제스트 부분을 닫는다 | — |
| Q11 | 알림·문의 API의 모듈 | **새 모듈 `portal-inbox`**(`app → inbox → projection → domain` — 규칙 (a)에 한 줄).<br>• `portal-query`는 읽기 전용 트랜잭션이 규약이다(`ReadTransaction` — `setReadOnly(true)`). 쓰기(`PATCH`·`POST`)를 넣으면 그 규약이 깨진다.<br>• `portal-sim`처럼 자기 포트(`InboxStore`·`InboxTransactions`)를 갖는다. 진입점은 Phase 5의 `domain.access` 규약(`@UseCaseEntry`·`Caller`·`ApiAuthorization`) 그대로<br>• 새 외부 의존성 0 — 잠금 파일은 새 모듈 하나만 생긴다(빈 `GRADLE_USER_HOME`에서 다시 쓰고, 검증 메타데이터 diff 0을 본다) | `portal-query`에 쓰기 트랜잭션을 더한다 |
| Q12 | 규칙 클래스의 자리(P-5·P-6) | **`portal-domain` 패키지 `com.ga.portal.domain.rule`로 옮긴다**: `LimitStage`·`PolicyMask`(파생 규칙이 쓴다 — `portal-projection`은 `portal-query`를 의존할 수 없다), `RateLimitRule`(창을 인자로 — 시뮬레이션 60초·문의 24시간). 이름·동작 그대로이고, `MultiplicationScanTest` 허용 목록의 FQN만 바뀐다(`LimitStage#of`) | 각 모듈에 사본 — 규칙이 두 벌 |
| Q13 | `params` 스키마 모양(P-4) | **`oneOf` + `discriminator`(`propertyName: type`)**.<br>• 변이는 `params` 모양마다 하나다(여섯 — ③ 표). 각 변이의 `type`은 그 모양을 쓰는 값들의 `enum`이고, 모든 변이가 `additionalProperties: false`다.<br>• 지시문은 `if/then`이 "생성 클라이언트가 좁힐 수 있게" 해 준다고 했지만 측정은 반대였다(P-4).<br>• 서버 응답 검증은 두 방식 모두 같은 결과다 | `if/then`(생성 타입이 `Record<string, never>`) |
| Q14 | 수신자(P-1) | **엔진 사실의 `agentId`**:<br>• 이벤트에 있으면 이벤트의 값.<br>• 없는 셋(`LimitLedgerPosted`·`LimitLedgerRebased`·`DeferralStatusChanged`)은 프로젝터가 읽은 **뷰 행의 `agent_id`**. 그 값도 개설·예정 이벤트의 `agentId`, 곧 엔진 사실이다.<br>• `identity_link`는 보지 않는다 | — |
| Q15 | `LIMIT_REBASE` 키(P-2) | **`LIMIT_REBASE:{ledgerId}:{seq}`**(이벤트 seq). 계약의 `postingSeq`는 선택 필드이고, 값은 재산정 순간의 마지막 전기 순번이라 재산정마다 다르지 않다 | 지시문대로 `postingSeq` — 없는 재산정은 키가 없고, 전기 없이 이어진 재산정 둘은 둘째가 사라진다 |
| Q16 | 파생 예외의 코드(P-3) | **`APPLY_ERROR`**(지시문 ②). 지금 코드는 파생 호출이 프로젝터와 같은 `try` 안이라 `PROJECTOR_ERROR` + `FAILED` 행이 된다. 파생 호출을 그 `try` 밖, 같은 트랜잭션 안으로 옮긴다 — 바깥 `catch`가 `APPLY_ERROR`(행 없음)로 판정하고 트랜잭션이 통째로 롤백된다 | `PROJECTOR_ERROR` 그대로(지시문과 다름) |
| Q17 | DRIFT 달(P-10) | **`STMT_CONFIRMED`·`PAYOUT`은 적용 뒤 그 달 `data_status`가 `DRIFT`이면 만들지 않는다.** 지시문 ⑦ "DRIFT 달은 알림 0"이 §6.5 표에 없는 조건이라 표에 더한다. 화면이 "확인 중"으로 흐리게 보이는 달을 알림이 "확정"이라 부르지 않는다. 그 달이 나중에 `REPAIRED`가 되어도 알림은 없다(Phase 7 질문) | 조건 없음 — 지시문 ⑦과 충돌 |
| Q18 | 한도 단계 심각도(P-19) | **표 그대로**: `LIMIT_WARN` = INFO, `LIMIT_CRIT` = WARN, `LIMIT_FULL` = CRIT(§6.5 "INFO/WARN/CRIT"를 단계 순서로 읽었다) | WARN = WARN, CRIT·FULL = CRIT |
| Q19 | `POLICY_*`의 `link`(P-16) | **`link` 없음.** 플래그 프로젝터는 원장을 읽지 않는다. 원장 링크를 달려면 파생이 뷰를 한 번 더 읽어야 한다(Q4와 충돌). 홈의 "주의 계약" 칸이 같은 플래그를 이미 센다 | 프로젝터가 `ledgerOf`를 더 읽는다 |
| Q20 | `link` 형식(P-11) | **화면 경로 문자열**(지시문 ⓪), 키 조각은 서버가 퍼센트 부호화한다(RFC 3986 unreserved 밖).<br>• 계약 `pattern`: `^/(lines\|limits)/[A-Za-z0-9._~%-]+$` · `^/statements/{Ym}$` · `^/inquiries/{UUID}$`.<br>• 화면은 그대로 라우터에 넘긴다(라우터가 디코딩한다).<br>• V7 `CHECK`도 같은 식이다 | 구조 `{screen, id}` — 지시문과 다름 |
| Q21 | 문의 429 본문(P-7) | **새 본문 하나** `{"code":"RATE_LIMITED","message":"too many inquiries"}` + `Retry-After`. 코드는 같고 계약 `Problem.code` 열거는 그대로다. 거부 본문은 10 → 11 | 시뮬레이션 본문 재사용 — 문구가 "too many simulations" |
| Q22 | GET의 ETag(P-20) | **응답 본문(JCS)의 SHA-256으로 만든 약한 ETag** + `private, no-cache`.<br>• 뷰 ETag는 `seq`·동기화 상태가 재료인데, 알림·문의는 `seq`와 무관하게 바뀐다(`read_at`·답변·닫힘).<br>• 문장 수는 304여도 같다(본문을 만들어야 비교한다). 바이트만 아낀다 | `count`·`max(created_at)`·`max(read_at)` 지문(닫힘에 시각 열이 없다) |
| Q23 | 본문이 나오는 응답 | **`GET /me/inquiries/{id}` 하나**(작성자 상세).<br>• 목록 항목에는 `message`·`answer`가 없다(분류·상태·시각·`calcId`).<br>• `POST` 201 응답도 본문 없이 `{id, status, category, calcId?, createdAt}`다.<br>• 그래서 C7의 닫힌 목록 "작성자 상세"가 응답 하나로 좁혀진다 | 목록·`POST`에도 본문 |
| Q24 | 문의 작성 화면(P-15) | **경로 `/inquiries/new`**(라인 상세에서 오면 쿼리 `calcId` — 자기 라인 키는 이미 `/lines/:calcId`로 주소에 있다).<br>• 월 명세의 진입점(`StatementScreen`에도 있다)은 `calcId` 없이 `OTHER`로 연다.<br>• 접수 결과는 상세 `/inquiries/:id`로 바꿔 끼운다(`replace`) | 라인 상세 안 접힘 양식 · 월 명세 진입점 제거 |
| Q25 | 재구축이 처음 적용한 이벤트(P-14) | **알림 없음 — 지시문 그대로**, 보고서에 기록한다.<br>• 재구축은 `headSeq`까지 재생하므로 커서 뒤의 이벤트도 처음 적용할 수 있다(`FAILED`에서 재구축할 때, 또는 머리가 앞서 있을 때). 그런 사실의 알림은 생기지 않는다.<br>• C3("`Rebuilder` → 파생 경로 0")와 바이트 동일이 그 대가다 | 재구축이 "처음 적용"한 것만 파생 — C3과 충돌 |
| Q26 | E2E 손 스트림(P-9) | **새 테넌트 GA05**, 스트림 `web/e2e/streams/ga05-limits.jsonl`.<br>• 한 원장: OK → WARN → 환수 음수 전기로 OK → WARN(알림 둘, seq 다름).<br>• 그다음 재산정 `LIMIT_REBASE`.<br>• 문의 리밋 시나리오 전용 `inquiry_rate_per_day` 3도 이 테넌트에 둔다.<br>• `edges.jsonl`(GA04)에 재산정을 이어 붙이지 않는다 — 원장 카드·타임라인 기대값(Phase 4·5)이 바뀐다 | GA04 스트림에 덧붙임 |
| Q27 | "1,000자"의 단위(P-12) | **유니코드 코드 포인트**. JSON Schema `maxLength`와 PostgreSQL `char_length`가 이 단위다. 화면의 남은 글자 수는 `[...s].length`로 센다(UTF-16 `length`가 아니다 — 이모지에서 갈린다) | UTF-16 단위 |
| Q28 | CLI 진입 | **`cli` 프로파일의 실행기 하나**(`CliRunner` — `ingest …`·`inquiry …` 두 묶음). 지금 실행기는 `ingest`만 받는다. 실행기가 둘이면 Spring이 둘 다 돌린다.<br>• 종료 코드 규약 그대로: 0 성공, 2 거부, 64 사용법, 1 = 재구축 실패만.<br>• `answer`·`close`의 출력은 `inquiry <id> ANSWERED` 한 줄이다. 본문은 `list`만 보인다(운영자가 읽을 곳) | 실행기 둘 |
| Q29 | 알림 행의 키·시각·순서 | `notification_id` = 이름 기반 UUID(`tenant ∥ U+001F ∥ agent ∥ U+001F ∥ dedup_key` — 재시작해도 같은 행이 같은 키).<br>`created_at` = 적용 시계(주입된 `Clock` — 엔진의 `occurredAt`이 아니라 포털이 안 시각).<br>정렬 `created_at DESC, notification_id DESC` | 무작위 UUID |
| Q30 | 계약 1.2.0 커밋의 자리(P-23) | **1.2.0이 처음 나타나는 커밋 = 그 경로를 섬기는 코드가 함께 든 커밋**(⑪ 3단계 — 계약 + 레코드 + 인가 표 + 진입점 + 컨트롤러 + 시험). 그 앞의 두 커밋(규칙 이동·V7)은 계약을 건드리지 않는다. DE-24의 뜻(별도 계약 PR 없음 — 작업 브랜치 안)은 그대로다. "첫 커밋"은 문자 그대로는 Phase 5에서도 불가능했다(P5-1) | 계약만 첫 커밋 — `main`과 같은 이유로 빌드 실패 |

## ① 확인한 사실 (지시문·설계서와 다른 곳)

| # | 지시문(또는 설계서) 문구 | 사실 | 계획 |
|---|---|---|---|
| P-1 | ① "수신자는 이벤트의 `agentId`" | `LimitLedgerPosted`·`LimitLedgerRebased`·`DeferralStatusChanged` payload에는 `agentId`가 없다(필수·선택 모두) | Q14 — 뷰 행의 `agent_id`(개설·예정 이벤트의 엔진 사실) |
| P-2 | ① "`LIMIT_REBASE:{ledgerId}:{postingSeq}`"(설계서 §6.5 표도) | `postingSeq`는 선택 필드이고, 프로젝터가 "마지막 전기 순번과 같다"로 대조하는 값이다 — 재산정의 식별자가 아니다 | Q15 — 이벤트 seq |
| P-3 | ② "파생 중 예외는 그 이벤트의 `APPLY_ERROR`" | `IngestApplier`가 `projector.apply`와 `notifications.derive`를 한 `try`로 묶어 둘 다 `PROJECTOR_ERROR` + `FAILED` 행이다 | Q16 — 호출을 `try` 밖으로 |
| P-4 | ⓪ "`oneOf`/`discriminator` 대신 `if/then` — 생성 클라이언트가 좁힐 수 있게" | `openapi-typescript` 7.13.0 측정: `if/then`은 `params: Record<string, never>` & `unknown`(좁힐 수 없음), `oneOf` + `discriminator`는 `type` 리터럴로 갈리는 합 타입 | Q13 |
| P-5 | ① 상향 판정에 `LimitStage`, ⓪ `policyMasked` | 둘 다 `portal-query.rule`에 있다. `portal-projection`은 형제 모듈을 의존할 수 없다(규칙 (a)) | Q12 — `portal-domain` |
| P-6 | ④ "Phase 5 `RateLimitRule` 재사용 — 창만 24h" | `RateLimitRule`은 `portal-sim`에 있고 창은 상수 `WINDOW = 60s`다 | Q12 — 창을 인자로, `portal-domain` |
| P-7 | ⓪·④ "429 재사용 … 같은 바이트" | 지금 429 본문은 `{"code":"RATE_LIMITED","message":"too many simulations"}` | Q21 — 문의용 본문 하나 |
| P-8 | ③ `type`·`category` `CHECK` | 시험 픽스처 `SeedData`의 행이 `type 'LIMIT'`·`category 'LINE'`·`title 't'`·`body 'b'`라 V7 `CHECK`에 걸린다(운영 DB의 `notification`은 비어 있다 — 지금 파생기가 Noop) | 픽스처를 새 값으로(마이그레이션이 아니다). V7 머리 주석에 "기존 행이 있으면 실패"를 적는다 |
| P-9 | ⑦ GA01 "`LIMIT_CUT`·`CLAWBACK`·`STMT_CONFIRMED`·`PAYOUT`·`LIMIT_FULL`", GA04 "재산정 `LIMIT_REBASE`" | 규칙을 스트림에 돌린 결과:<br>• A-1: seq 3 `LIMIT_CUT`, 5 `LIMIT_FULL`(OK → FULL — 두 경계 이상), 6 `STMT_CONFIRMED`, 7 `PAYOUT`, **8 `POLICY_CANCEL`**, 9 `CLAWBACK`, 10은 FULL → OK(하향 — 알림 없음).<br>• GA04 `edges`: 4 `LIMIT_CUT`, 5 `LIMIT_FULL`, 7 하향, 10 202608 확인(**DRIFT**), 14 202701 확인, 16 `LIMIT_WARN`, 23 `DEFER_HELD`, 24 `DEFER_CANCELLED`. **재산정 이벤트가 없다** | GA01 기대값에 `POLICY_CANCEL`을 더한다. 재산정·재상향은 GA05(Q26) |
| P-10 | ⑦ "DRIFT 달은 알림 0" | §6.5 표에 그런 조건이 없다 | Q17 |
| P-11 | ⓪ "`link`는 … `/lines/{calcId}`" | 계약의 키 `Key`는 1~128자의 아무 문자다. 지금 화면들도 자기 링크에 키를 부호화하지 않고 넣는다(`/lines/${calcId}` — 슬래시가 든 키는 깨진다, F로 기록) | Q20 |
| P-12 | ⓪ "`message` ≤ 1,000자", ⑥ "남은 글자 수 — 문자열 길이" | JSON Schema `maxLength`는 코드 포인트를, JS `length`는 UTF-16 단위를 센다 | Q27 |
| P-13 | ⓪ "`size` ≤ 100" | 알림 목록에 `page`가 지시문에 없다 | `page`(0부터)·`size`(1~100, 기본 20)·`total` — `/lines`와 같은 규약 |
| P-14 | §0 "재구축은 파생하지 않는다" | 재구축은 커서 뒤 이벤트도 처음 적용할 수 있다(§6.7 3 — `headSeq`까지) | Q25 |
| P-15 | ⑥ "라인 상세 '이 항목 문의하기'" | 월 명세(`StatementScreen`)에도 같은 진입점이 있다(Phase 4) | Q24 |
| P-16 | ⓪ `link` 닫힌 목록 | `POLICY_*`에 맞는 경로가 없다 — 플래그는 계약 단위이고, 원장이 없는 계약도 있다 | Q19 |
| P-17 | ⑧ "`docs/authz-matrix.md` 행 5" | `AuthzMatrixTest`는 `@GetMapping`·`@PostMapping`만 읽는다 | `@PatchMapping`을 더 읽는다(시험 확장 — 약화 아님) |
| P-18 | ⑤ CLI 셋 | `cli` 프로파일의 실행기(`IngestCliRunner`)는 첫 낱말이 `ingest`가 아니면 64다 | Q28 |
| P-19 | §6.5 표 심각도 "INFO/WARN/CRIT" | 단계 셋(WARN·CRIT·FULL)과 짝이 적혀 있지 않다 | Q18 |
| P-20 | ④ "ETag·`private, no-cache` 규약은 GET에" | 기존 ETag 재료(`seq`·동기화·설정 지문)는 알림·문의의 변화를 보지 못한다 | Q22 |
| P-21 | ③ "`StatusEnumDdlTest`에 두 표의 상태 열" | `notification`에는 상태 열이 없다. 닫힌 값 열은 `type`·`severity`(V1 `CHECK` — 열거 대응 없음)이고, `inquiry`는 `status`(V1)·`category`(V7)다 | 넷을 포털 열거에 잇는다: `NotificationType`·`Severity`·`InquiryStatus`·`InquiryCategory`(`portal-domain`). 새 `CHECK` 열 셋(`notification.type`·`notification.link`(형식)·`inquiry.category`)을 승인 목록에 |
| P-23 | 형식 절·⓪ "1.2.0 = 작업 브랜치 **첫 커밋**(DE-24)" | 새 경로 다섯은 전부 새 인가 표 키·레코드·진입점을 요구한다. `PortalApiContractTest`(경로 = 인가 표 키, 스키마 = 레코드)·`AuthzMatrixTest`·`clientCheck`가 계약을 코드에 묶어, 계약만 든 커밋은 빌드가 실패한다. Phase 5도 같은 이유로 계약을 코드와 함께 두 커밋에 나눴다(P5-1) | Q30 |
| P-22 | ⑦ "누출 … E2E 산출물의 다른 파일" | 하네스·포털 로그와 Playwright 산출물(실패 시 trace·스크린샷)이 아티팩트에 들어간다. 작성자 화면의 DOM에는 본문이 있다(작성 중·상세) | 본문 센티널은 **작성 화면·상세 화면의 DOM과 그 상세 응답 하나**에서만 허용하고, 나머지(다른 화면 DOM·다른 응답·콘솔·주소·저장소·로그·아티팩트 파일)는 0. 이 시험은 trace를 끈다(실패해도 본문이 파일로 남지 않게) |

## ② 계약 `portal-api` 1.2.0 (Q30 — 작업 브랜치 안, 그 경로를 섬기는 코드와 같은 커밋)

- 경로 다섯 — 설계사 본인, `agentId` 없음:
  - `GET /api/v1/me/notifications?unreadOnly&page&size` → `NotificationsResponse {items[], page, size, total, unreadCount}`(`unreadCount`는 `unreadOnly`와 무관하게 전체 안읽음 수).
  - `PATCH /api/v1/me/notifications/{id}/read` → 200 `Notification`(멱등 — 두 번째도 200, `readAt` 그대로), 요청 본문 없음.
  - `POST /api/v1/me/inquiries`:
    - 요청 `application/json` ≤ 4 KiB `{category, calcId?, message}`. `message`는 1~1,000 코드 포인트이고 앞뒤 공백만으로는 안 된다(`pattern '\S'`). `if/then`: 앞의 넷이면 `calcId` 필수 — 요청 검증용이고, 화면은 Phase 5 규칙 파일(`portal-api.rules.json`)로 읽는다.
    - 201 `InquiryCreated {id, status, category, calcId?, createdAt}`.
  - `GET /api/v1/me/inquiries?status&page&size` → `InquiriesResponse {items[{id, status, category, calcId?, createdAt, answeredAt?}], page, size, total}`(`status` = `ALL`(기본)·`OPEN`·`ANSWERED`·`CLOSED`).
  - `GET /api/v1/me/inquiries/{id}` → `Inquiry {id, status, category, calcId?, message, createdAt, answeredAt?, answer?}` — `answered_by` 없음.
- `Notification`: `oneOf` 여섯 변이 + `discriminator: type`(Q13). 공통 `{id, type, severity, createdAt, readAt?, link?, params}`, 변이마다 `params`가 닫혀 있다(③ 표). 금액 0(Q1). 증권번호는 `policyMasked`만(`policyKey`는 쓰지 않는다 — 비밀 회전 뒤 남은 알림의 키가 틀린다).
- 헤더: GET 둘은 `ETag`·`Cache-Control: private, no-cache`(304 포함 — Q22), 쓰기 둘은 `no-store`.
- 거부:
  - 401·404·400·500 그대로.
  - 429 문의 본문(Q21)이 추가된다. `PATCH`·`POST`의 경로 변수 형식 오류(UUID 밖)는 같은 404(감사 없음)이고, 남의·없는 id는 같은 404 + `AUTHZ_DENIED MISSING`이다.
  - 요청 본문 형식·크기·스키마 오류는 400 한 가지다. 본문은 상한 + 1바이트까지만 읽는다(P5-2).
- 호환:
  - `PortalApiCompatibilityTest`가 1.1.1 사본(시험 자원 `portal-api-1.1.1.openapi.yaml`, SHA 고정)과 1.0.0·1.1.0을 1.2.0에 구조 대조한다.
  - 기존 경로·스키마는 바이트 단위로 같다 — 설명문 줄만 다르다(`info`·머리 변경 기록).
  - `CHECKSUMS`·`client:lock`을 다시 쓴다.
- `PortalApiContractTest` 확장:
  - 레코드 ↔ 스키마: `oneOf` 변이마다 레코드 하나(sealed 인터페이스 `NotificationParams`의 구현).
  - `jsonType`이 sealed 인터페이스를 `oneOf`로 대응한다.
  - 경로 = 인가 표 키.
  - `noSchemaTalksAboutAPolicyNumber`는 그대로다.
  - 새 검사 하나: 알림 `params` 스키마 어디에도 `Won` 참조가 없다(Q1).

## ③ 파생 규칙 — 최종 표 (C2·C5, `portal-projection` `notify.NotificationRules`)

순수 함수: `(Envelope, Change, Thresholds) → List<Candidate>`. `Candidate {agentId, type, severity, dedupKey, params, link}`이고, 쓰기·시각·id는 어댑터의 몫이다. "후"는 이 이벤트를 적용한 뒤의 행이다.

| 트리거 | 조건 | `type` | 심각도 | dedup_key | `params` | `link` | 수신자 |
|---|---|---|---|---|---|---|---|
| `LimitLedgerPosted` | `stage(후) > stage(전)` — 전 = 프로젝터가 읽은 원장 행의 `accum_paid`, 후 = `accumAfter`, 같은 `limit_amount`·같은 테넌트 bp. 단계가 하나 이상 오르면 **후 단계 하나** | `LIMIT_WARN`·`LIMIT_CRIT`·`LIMIT_FULL` | INFO·WARN·CRIT(Q18) | `LIMIT_{STAGE}:{ledgerId}:{seq}` | `{ledgerId, policyMasked}` | `/limits/{ledgerId}` | 원장 행 |
| `LimitLedgerRebased` | 항상(한도가 줄어 단계가 올라도 이것 하나 — `LIMIT_*` 아님) | `LIMIT_REBASE` | INFO | `LIMIT_REBASE:{ledgerId}:{seq}`(Q15) | `{ledgerId, policyMasked}` | `/limits/{ledgerId}` | 원장 행 |
| `CommissionCalculated` | `limitCutAmt > 0` | `LIMIT_CUT` | WARN | `LIMIT_CUT:{calcId}` | `{calcId, policyMasked, closeYm}` | `/lines/{calcId}` | 이벤트 |
| `CommissionCalculated` | `commType = CLAWBACK` | `CLAWBACK` | WARN | `CLAWBACK:{calcId}` | `{calcId, policyMasked, closeYm}` | `/lines/{calcId}` | 이벤트 |
| `DeferralStatusChanged` | `to ∈ {HELD, CANCELLED}` | `DEFER_HELD`·`DEFER_CANCELLED` | WARN | `DEFER_{to}:{scheduleId}` | `{calcId(= 원 라인), policyMasked, dueYm}` | `/lines/{sourceCalcId}` | 분급 행 |
| `PolicyEventObserved` | `eventType ∈ {PAYMENT_OVERDUE, LAPSE, CANCEL}` | `POLICY_PAYMENT_OVERDUE`·`POLICY_LAPSE`·`POLICY_CANCEL` | WARN | `POLICY_{type}:{eventId}`(Q3) | `{policyMasked, occurredOn}` | 없음(Q19) | 이벤트 |
| `StatementConfirmed` | 후 달 `data_status ≠ DRIFT`(Q17) | `STMT_CONFIRMED` | INFO | `STMT_CONFIRMED:{ym}` | `{ym}` | `/statements/{ym}` | 이벤트 |
| `PayoutCompleted` | 후 달 `data_status ≠ DRIFT`(Q17) | `PAYOUT` | INFO | `PAYOUT:{ym}` | `{ym}` | `/statements/{ym}` | 이벤트 |
| CLI `inquiry answer` | `OPEN → ANSWERED` | `INQUIRY_ANSWERED` | INFO | `INQUIRY:{inquiryId}` | `{inquiryId}` | `/inquiries/{id}` | 문의 행 |

- `params` 모양 여섯 = 계약 변이 여섯: 원장(`LIMIT_*`·`LIMIT_REBASE`) · 라인(`LIMIT_CUT`·`CLAWBACK`) · 분급 · 계약 · 월 · 문의. `type` 열넷.
- 다른 이벤트 일곱(`AgentSnapshot`·`AdjustmentPosted`·`LimitLedgerOpened`·`DeferralScheduled`·`ClawbackReceivableUpdated`·`StatementEstimated`, 그리고 조건 밖의 위 이벤트)은 후보 0이다.
- `policyMasked`는 `PolicyMask`(Q12)로 파생 시점에 만든다. 증권번호 원문은 `params`·`link`·dedup_key 어디에도 없다(C5 — 순수 시험이 후보 전부를 스트림의 증권번호 집합과 대조).
- 한 이벤트의 후보가 둘일 수 있다(삭감 + 환수 라인). 각각 따로 dedup된다.
- **시험(C2)**:
  - 순수 표 시험 `NotificationRulesTest`가 표의 칸마다 맞는 경우·안 맞는 경우를 본다. 단계 전이는 OK·WARN·CRIT·FULL 16쌍 전수다.
  - 시드 고정 생성기(`SeededCases`)로 임의 원장 전기열을 만들어 "후보 수 = 상향 횟수"를 본다.
  - 상향 → 하향 → 재상향은 키 둘(seq 다름)이고, 두 경계 동시 통과는 1건이다.

## ④ 적용 배선 (C3·C4)

- **`ProjectionResult`에 `Optional<Change>`**(Q4). sealed 변이: `LedgerPosted(before, after)`·`LedgerRebased(before, after)`·`LineAdded(line)`·`DeferralChanged(before, after)`·`FlagObserved(eventType, policyNo, agentId, occurredOn)`·`MonthClosed(after)`(확정·지급). 프로젝터는 이미 읽은 행과 저장할 행을 그대로 담는다 — 읽기 추가 0. 나머지 이벤트는 비어 있다.
- **`NotificationDeriver` 실 구현** `portal-app` `ingest.JdbcNotificationDeriver`:
  - `NotificationRules`를 부르고 `InboxStore.insertIfAbsent`로 넣는다(`ON CONFLICT (tenant_id, agent_id, dedup_key) DO NOTHING`).
  - 임계치는 `SyncStatusRepository.snapshot()`(지시문 ① — 같은 문장, SQL 산술 허용 목록 그대로)이고, `LedgerPosted`일 때만 읽는다.
  - Noop은 시험 픽스처로 내린다.
- **`IngestApplier`**: 파생 호출을 프로젝터 `try` 밖으로 옮긴다(Q16). 순서는 프로젝터 → 파생 → 커서. 파생 예외는 `APPLY_ERROR`이고 뷰·알림·`ingest_event`가 한꺼번에 롤백된다.
- **`Rebuilder`**: 바꾸지 않는다. 고정하는 장치는 셋이다.
  - ArchUnit(`archTest`): `Rebuilder`가 `NotificationDeriver`와 그 구현에 **전이적으로도** 의존하지 않는다.
  - 행동 시험: 재구축을 세는 파생기로 돌리면 호출이 0이다.
  - `RebuildIT` 확장: 재구축 전후 `notification` 표 덤프가 바이트 동일하다(`read_at` 포함 — 읽은 알림을 하나 두고 돌린다).
- `ViewReset` 목록(뷰 8표)은 그대로다 — 시험이 `notification`·`inquiry`가 목록 밖임을 단언한다.
- **재시작(C2)**: `IngestDeterminismIT` 패턴을 따른다. 같은 스트림을 ① 한 번에 ② 중간 재시작으로 적용하면 `notification`(테넌트 열 제외)이 바이트 동일하다 — 고정 시계·이름 기반 id(Q29).

## ⑤ V7 (`V7__inbox.sql`)

- **`notification`**:
  - `params JSONB NOT NULL DEFAULT '{}'` + `CHECK (jsonb_typeof(params) = 'object')`.
  - `title`·`body` `DROP NOT NULL` + `CHECK (title IS NULL AND body IS NULL)`(Q1).
  - `type` `CHECK (type IN (…열넷…))`, `link` `CHECK`(Q20의 식 — `NULL` 허용).
  - 인덱스 `(tenant_id, agent_id, created_at DESC)`.
  - `severity` `CHECK`는 V1 그대로다.
- **`inquiry`**:
  - `category` `CHECK (category IN ('AMOUNT','LIMIT','CLAWBACK','DEFERRAL','OTHER'))`.
  - `CHECK (char_length(message) BETWEEN 1 AND 1000)`, `CHECK (answer IS NULL OR char_length(answer) BETWEEN 1 AND 4000)`.
  - 상태별 열 `CHECK` — V6과 같은 방식:
    - `OPEN`: 답변 셋이 모두 NULL.
    - `ANSWERED`: 셋이 모두 있다.
    - `CLOSED`: 답변이 있거나 없거나 — 열린 채 닫힐 수 있다.
  - `calcId` 필수 `CHECK`(앞의 넷).
  - 인덱스 `(tenant_id, agent_id, created_at DESC)` — 목록과 24시간 창이 같은 인덱스를 쓴다.
- RLS·권한: V2 그대로(두 표 다 이미 목록에 있다). 새 표 0.
- `StatusEnumDdlTest`(P-21), `SeedData` 값 갱신(P-8).

## ⑥ API — `portal-inbox` (C5·C6·C7·C8)

- **진입점**: `InboxUseCases` — `@UseCaseEntry` 다섯, `ApiAuthorization` 키 다섯, `docs/authz-matrix.md` 행 다섯(`SELF` / 나머지 404).
- **SQL**: `InboxRepository`(`portal-infra`). 모든 문장이 `tenant_id`·`agent_id` 두 술어를 가진다 — `TenantPredicateScanTest` 그대로.
- **알림 목록**: 문장 둘(페이지, 그리고 `count(*)` + `count(*) FILTER (WHERE read_at IS NULL)`). 홈 콜 문장 수는 발급자 1 + 링크 1 + 본문 2이고, `QueryStatementIT`가 행 수와 무관함을 잰다(§10 홈 표에 다섯째 줄).
- **읽음**: `UPDATE … SET read_at = coalesce(read_at, :now) … RETURNING …` 한 문장. 0행이면 404 `MISSING`(감사 — 별도 트랜잭션, 기존 규약)이다.
- **문의 접수**: 한 트랜잭션.
  1. 본문을 읽는다(상한 + 1).
  2. 스키마를 검증한다(400).
  3. `calcId`를 그 설계사 라인에서 푼다(404 `MISSING` — 행 0).
  4. 설계사 권고 잠금 `pg_advisory_xact_lock(hashtext('portal.inquiry'), hashtext(테넌트 ∥ U+001F ∥ 설계사))`을 잡는다.
  5. 24시간 창의 행 수와 가장 오래된 `created_at`을 본다.
  6. `RateLimitRule.retryAfter(…, Duration.ofHours(24))`가 거부하면 429(행·감사 0, 지표 `api_rate_limited_total{tenant, route}`)다.
  7. 통과하면 `inquiry` 행을 넣는다(`OPEN`).
  - 한도는 테넌트 `params.inquiry_rate_per_day`(1~9자리 양의 정수, 아니면 앱 기본 20)다.
- **본문 격리(C7)**:
  - 요청 본문을 읽는 매퍼는 소스 위치 포함을 끈다(Jackson `INCLUDE_SOURCE_IN_LOCATION` — 파싱 예외 문구에 입력 조각이 실리지 않게).
  - 400 응답·로그는 고정 바이트·예외 클래스 이름뿐이다.
  - 지표 태그에 본문 0, 감사 `detail`에 본문 0.
  - 상세 응답 밖의 응답에 본문 필드가 없다(계약 — Q23).
- **CSRF(지시문 §0)**: 토큰·`Origin` 검사 0. 시험은 `Cookie` 머리를 싣고 `Authorization`이 없는 `PATCH`·`POST`·`GET`이 같은 401 바이트를 받는 것이다(`UniformRejectionIT` 확장). 설계서 §9 CSRF 문단이 근거를 세 쓰기 경로로 넓힌다.

## ⑦ 운영 CLI (C9)

- `inquiry list --tenant=T [--status=OPEN|ANSWERED|CLOSED|ALL]`: 탭 구분 출력(id·설계사·`calcId`·분류·상태·접수 시각·본문 — 본문은 줄바꿈을 `\n` 글자로)이고, 감사는 `INQUIRY_LIST`다.
- `inquiry answer --tenant=T --id=<uuid> --answer-file=<path>`:
  - 파일 요건: UTF-8(엄격), 1~4,000 코드 포인트, 16 KiB 이하. 경로가 없거나 읽을 수 없으면 64다(경로만 말하고 내용은 말하지 않는다).
  - 한 트랜잭션: `inquiry` `OPEN → ANSWERED`(`answered_by` = `cli:<user>`, `answered_at` = 시계) + 알림 `INQUIRY:{id}` 삽입 + 감사 `INQUIRY_ANSWER`.
  - 상태가 `OPEN`이 아니면 2(`INQUIRY_ANSWER_REJECTED`).
- `inquiry close --tenant=T --id=<uuid>`: `OPEN`·`ANSWERED → CLOSED`, 감사 `INQUIRY_CLOSE`.
- **인가**: `cli:<OS 사용자>`가 그 테넌트의 OPERATOR·ACTIVE 링크여야 한다(`OperatorAuthorization` 그대로 — 거부 2 + `AUTHZ_DENIED`).
- **전이**: `InquiryTransitions`(`portal-domain`) — `OPEN → ANSWERED`, `OPEN|ANSWERED → CLOSED`. 그 밖은 거부한다.
- **시험**: `InquiryCliIT`가 실행기에 잡은 `PrintStream`을 넘긴다. CLI 출력이 시험 결과 XML에 실리면 평문 스캔이 센티널을 잡는다 — 그것이 의도된 자리다.
  - `list` 출력에 센티널이 있다.
  - `answer` 출력·로그·감사에 본문이 0이다.
  - 주입: 알림 삽입이 실패하면 문의도 `OPEN` 그대로다.

## ⑧ 화면 (C10)

- **경로**(`routes.ts` ↔ `SCREEN_PATHS` — `WebScreensIT`): `/notifications`·`/inquiries`·`/inquiries/new`·`/inquiries/:id`. 서버 목록은 `/notifications`·`/inquiries`·`/inquiries/*`다. 대조 규칙이 정적 조각 `new`를 `*`로 받는지 확인하고, 못 받으면 대조기에 정적 조각 규칙을 더한다(시험 확장). 넷 다 지연 청크(`LAZY_SCREENS`)다.
- **홈**: "최근 알림" 칸은 `size=3` 결과 + 안읽음 수(`unreadCount` 그대로)다. 항목을 누르면 → 읽음 `PATCH` → `link` 경로로 간다. 알림이 없으면 "새 알림이 없습니다". 알림함 링크가 따른다.
- **알림함**: 최신순으로 보이고, 안읽음은 굵게 + 점 표식 + 숨은 글자 "안읽음"을 단다. 항목은 위와 같고, `link`가 없으면 읽음만 한다. "더 보기"는 다음 페이지다. "모두 읽음"은 없다.
- **문장**: `messages.ko.json`의 `notifications.<TYPE>` 템플릿 열넷 + `params` 자리다. 월은 기존 월 형식기, 날짜는 기존 날짜 형식기를 쓴다. 금액 자리는 없다(Q1).
  - `messages.test`가 `type` 열거(생성 클라이언트) ↔ 템플릿 키를 양방향으로, 템플릿의 자리 ↔ 그 변이의 `params` 키를 대조한다.
  - 주입: 템플릿 하나를 지운다.
- **문의 작성**(`/inquiries/new`):
  - 분류는 누름 버튼 묶음(`Choices` — P5-15)이다. `calcId`가 없으면 `OTHER`만이다.
  - 본문 `textarea`, 고정 문구 "고객 이름·연락처·증권번호를 쓰지 마세요", 남은 글자 수(`1000 − [...s].length` — 문자열 길이이지 금액이 아니다, `aria-live=polite`)가 있다.
  - 제출하면 `POST` → 상세로 `replace`한다. 429는 "오늘 문의 한도를 넘었습니다 — {Retry-After} 뒤 다시"이고, 입력은 그대로 둔다(메모리).
- **문의 목록·상세**: 목록은 분류·상태 라벨·접수 시각이다. 상세는 본문·답변(있으면)·답변 시각이고, 라인 키가 있으면 라인 상세 링크를 단다. 상태 라벨 셋(접수됨·답변 완료·종료)이 `messages.ko.json`에 있다.
- **라인 상세·월 명세의 진입점 활성화**(Q24). 다른 `ComingSoon`은 이제 0이므로 컴포넌트를 지운다(쓰는 곳이 없다).
- 경로·토큰·CSP·번들·a11y·움직임·키보드 규약 그대로, 새 npm 의존성 0이다.

## ⑨ E2E (C11, Q9·Q26)

| # | 시나리오 | 테넌트 | 증거 |
|---|---|---|---|
| E1 | 머리를 seq 2 → 5 → 7 → 11로 올리며 알림함 개수가 그 시점에만 는다: `LIMIT_CUT`(3) · `LIMIT_FULL`(5 — 한 건) · `STMT_CONFIRMED`·`PAYOUT`(6·7) · `POLICY_CANCEL`·`CLAWBACK`(8·9) · 하향(10) 추가 0 | GA01 | 단계별 개수·`type` 열 |
| E2 | `DEFER_HELD`·`DEFER_CANCELLED`, 202608(DRIFT) `STMT_CONFIRMED` 없음·202701 있음 | GA04 | 알림 목록 |
| E3 | OK → WARN → (환수 음수 전기) OK → WARN: `LIMIT_WARN` 둘 — 응답 `id`가 다르다. 이어서 재산정: `LIMIT_REBASE` | GA05 | 목록·`id` |
| E4 | 하네스 `POST /cli/<T>/rebuild` → 종료 0 → 알림 수·항목(읽음 표시 포함) 그대로 | GA05 | 전후 응답 동일 |
| E5 | 라인 상세 → 문의 작성(본문에 **본문 센티널**) → 상세 → 하네스 `POST /cli/<T>/answer/<id>` → 홈 안읽음 수 +1 → 알림 `INQUIRY_ANSWERED` → 상세의 답변 → 읽음 | GA01 | DOM·응답 |
| E6 | 문의 넷째 → 429 문구(한도 3) | GA05 | 상태·문구 |
| E7 | 누출: 본문 센티널이 작성·상세 화면 DOM과 상세 응답 밖 어디에도 0(다른 화면 DOM·다른 응답·콘솔·주소·저장소). 실행 뒤 `run.mjs`가 아티팩트 디렉터리 전부(로그·보고서)를 같은 목록으로 훑는다 | 전부 | 센티널 목록 `inquiry-sentinels`(허구 — 이름 꼴·전화번호 꼴·증권번호 꼴 셋) |

- GA05: 원장 하나 + 라인·전기 다섯 + 재산정 하나(시나리오 이름 `ga05-limits`), `params.inquiry_rate_per_day = 3`. 스트림은 계약 스키마로 검증된다(하네스 기존 규칙).
- 본문 센티널은 `pii-sentinels.properties`에도 더한다 — `scanPlaintextLeaks`가 시험 XML·빌드 로그·웹 산출물을 본다. CLI `list` 시험은 잡은 출력만 단언하고 시험 출력으로 내보내지 않는다.
- 실 엔진 E2E: `run.sh` 무변경. `EngineFeedE2eCheck`의 판정에 "알림 행 수 > 0" 한 줄을 더한다(`RESULT.md` 판정 셋째 줄).

## ⑩ 완료 기준 → 시험·주입

| C | 시험 | 주입(ID 단위로 하나씩) |
|---|---|---|
| C1 | `PortalApiCompatibilityTest`(1.0.0·1.1.0·1.1.1 → 1.2.0), `PortalApiContractTest`(레코드 ↔ 변이, `params`에 `Won` 0), 모든 시험 응답의 계약 검증 | S1 `params`에 미정의 키(레코드 성분 하나) → 계약 시험 실패 |
| C2 | `NotificationRulesTest`(표 전수·16쌍·시드 생성기), `NotificationDerivationIT`(재시작 바이트 동일·재폴링 0 추가·재상향 새 행) | S2 상향 판정을 `≥`로(같은 단계 재알림) · S3 키에서 seq를 뺀다(재상향이 사라짐) |
| C3 | `RebuildIT`(전후 바이트), `ArchitectureRulesTest`(전이 의존 0), 세는 파생기 0 | S4 `Rebuilder`가 파생기를 부른다 |
| C4 | `NotificationDerivationIT`(파생기 예외 → `APPLY_ERROR`, 그 seq의 뷰·알림·`ingest_event` 0, 커서 정지) | S5 파생 호출을 다른 트랜잭션으로 |
| C5 | `ResponseLeakScanIT` 확장(알림·문의 응답 ∩ 증권번호·남의 ID·dedup_key 형식 = ∅), 계약 `additionalProperties: false` | S6 `params`에 `policyNo` |
| C6 | `NotificationApiIT`·`InquiryApiIT`(본인만·남의 id 404 + 감사·`PATCH` 멱등), `UniformRejectionIT`(쿠키만 → 401 같은 바이트, 문의 429 바이트), `AuthzMatrixTest`(`@PatchMapping`) | S7 읽음 문장에서 `agent_id` 술어 무력화 |
| C7 | `InquiryIsolationIT`(센티널 본문 — 상세 응답에만, 로그 캡처·지표·감사·다른 응답 0, 4 KiB + 1 → 400, 1,001 코드 포인트 → 400, 깨진 JSON에 센티널 → 400 + 로그 0), E7 | S8 400 처리기가 예외 메시지를 로그에 · S9 목록 항목에 `message` |
| C8 | `InquiryRateLimitIT`(N 통과·N+1 429·동시 N+5 → 통과 N·429 행·감사 0·`Retry-After`) | S10 권고 잠금 제거(동시 초과) |
| C9 | `InquiryCliIT`(한 트랜잭션·파일만·OPERATOR·로그 0·출력 규약) | S11 알림 삽입 실패 → 문의 롤백 확인 · S12 `--answer` 인자 수용(64여야 한다) |
| C10 | Vitest `messages.test`(양방향·자리 대조), `phase6.test.tsx`(문장·안읽음 수·남은 글자 수 코드 포인트·`ComingSoon` 0) | S13 템플릿 하나 삭제 |
| C11 | `web-e2e` E1~E7 × 세 프로젝트, axe·CSP·누출·번들 | S14 상세 밖 화면에 본문 노출 |
| C12 | 전체 CI(Java·Vitest·Playwright·`no-docker`·평문 스캔·잠금·검증 메타데이터) | — |

## ⑪ 단계 (단계마다 시험·주입·설계서 같은 커밋)

1. 규칙 클래스 이동(Q12) — 동작 변경 0, 시험 그대로 통과. 계약 무변경.
2. V7 + 열거 넷 + `StatusEnumDdlTest`·`SeedData`. 계약 무변경.
3. **계약 1.2.0**(1.1.1 사본·`CHECKSUMS`·`client:lock`·계약 시험 확장) + `portal-inbox` + 컨트롤러 + 인가 표·매트릭스 + 리밋 — 계약이 처음 바뀌는 커밋(Q30). 알림 행은 시험이 직접 넣는다(파생은 다음 단계).
4. 파생 규칙(`portal-projection`) + `ProjectionResult.Change` + 프로젝터 반환.
5. 파생기 배선 + `IngestApplier` 예외 자리 + 재구축 고정(ArchUnit·`RebuildIT`).
6. CLI(`CliRunner`·`inquiry` 셋).
7. 화면.
8. E2E(GA05·하네스 제어 둘·센티널).
9. 문서·보고서·태그 `phase-6`·PR(병합은 심사 뒤).

## ⑫ 설계서 v1.9·`CLAUDE.md`에서 고칠 곳

- §3.3 모듈 목록에 `portal-inbox`, 의존 방향 한 줄.
- §5 `notification`·`inquiry` V7 주석.
- §6.5:
  - 표를 ③의 최종 표로 바꾼다(조건·키·심각도·수신자).
  - 파생 지점은 적용 트랜잭션 안, 프로젝터 다음이다(RM-4).
  - 재구축 문장(Q25 포함), 다이제스트는 v2.
- §6.7 "알림 파생은 재생에서 부르지 않는다 — Phase 6에서 정한다" → 확정 문장.
- §7: 1.2.0 경로 다섯, 홈 다섯째 콜.
- §8: 1·5·6 최종 — 홈 알림 칸, 라인·월 진입점, 알림함·문의 화면.
- §9: CSRF 문단(세 쓰기 경로), 본문 격리, 거부 본문 11.
- §10: 문의 리밋, 보존(알림 1년 — 운영 SQL), 홈 콜 표.
- §12 Phase 6 줄, §13 시험 줄.
- §14.1: #3 다이제스트 닫음, #12 재생 파생 닫음.
- `CLAUDE.md` "알림·문의 규약" 절(파생은 적용 트랜잭션·재구축 파생 0·금액 없는 `params`·본문은 작성자 상세와 CLI `list`만).
- `docs/authz-matrix.md` 행 다섯, `docs/운영.md` §7(문의 CLI 셋·답변 파일·알림 보존 SQL), `DECISIONS.md`(계획 승인·RM-4·P6-n).

## ⑬ 하지 않는 것

지시문 §4 전부. 더해서:
- 알림 `params`의 금액(Q1).
- `policyKey`의 저장(Q20 본문).
- 재구축이 처음 적용한 사실의 파생(Q25).
- 기존 화면 링크의 키 부호화 수정(P-11 — F로만 기록).
- `/me` 응답 변경(Q8).
- 알림·문의 삭제 API.
- 답변 수정·재답변.

## ⑭ 다음 Phase 질문 (보고서에서 다시)

Phase 7(대사·DRIFT 자동 복구·관측성):
- `REPAIRED`가 된 달의 `STMT_CONFIRMED`·`PAYOUT`을 그때 낼지(Q17).
- 재구축이 처음 적용한 사실의 알림(Q25)을 재대사 흐름이 메울지.
- 운영 알림 채널이 `inquiry` `OPEN` 적체도 볼지.
- 지표 `notifications_derived_total{tenant, type}`의 자리.
