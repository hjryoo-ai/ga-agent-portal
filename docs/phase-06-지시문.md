# 포털 Phase 6 지시문 — 알림 파생·이의제기 (ga-agent-portal, v1.0, 2026-10-24)

## 형식에 대해

E2 형식 — **범위·완료 기준·질문만**. 지시문 문장이 코드·계약·설계서와 다르면 P-번호로 반증한다. 계획 승인은 Phase 4·5처럼 사용자가 직접 해도 된다(Phase 5 수용 §5) — 단 돌이킬 수 없는 결정(계약 주 버전·마이그레이션 구조)이 있으면 승인 전에 보낸다.

브랜치 `work/phase-6`, 태그 `phase-6`, PR, **병합은 수용 심사 회신 뒤**. 정본: `docs/설계서.md` v1.8.x(§2.1, §5 `notification`·`inquiry`, §6.5, §6.7, §7, §8 1·5·6, §9, §10 보존, §12 Phase 6, §14.1 #3·#12), `contracts/api/v1/portal-api.openapi.yaml` 1.1.1 → **1.2.0**(작업 브랜치 **첫 커밋** — DE-24), `contracts/events/v1/**`(파생 입력), Phase 5 수용 심사 §4(Q2·Q3·Q4·Q6), `CLAUDE.md` 전 절.

## 0. 전제

- 포털 `main` = Phase 5.1 병합 뒤(`3a24ce6`). 이 Phase가 더하는 것은 **알림 파생**(이벤트 → `notification` 행), **알림함 화면·읽음 처리**, **이의제기**(라인 단위 문의 접수·조회 + 운영 CLI 답변), 홈 "최근 알림 3건"과 라인 상세 "이 항목 문의하기" 진입점의 활성화다. 푸시·알림톡 채널, 다이제스트, 운영 화면은 범위 밖(아래 §4).
- **파생 지점은 적용 트랜잭션 안이다 — Phase 5 수용 §4 Q1("별도 파생기 + 자기 커서")을 여기서 되돌린다(RM-4).** `ingest_event`에는 payload가 없어(seq·eventId·해시·상태만) 커서 뒤 파생기는 이벤트를 다시 받아야 한다. 설계서 §6.5의 원문대로 **프로젝터와 같은 트랜잭션**에서, 이미 배선된 `NotificationDeriver` 훅(`IngestApplier` — Phase 2 `NoopNotificationDeriver`)을 실 구현으로 바꾼다. **재구축은 파생하지 않는다** — `Rebuilder`는 지금도 훅을 부르지 않는다(§14.1 #12 확정: 알림은 새 사실의 신호, 재생은 사실을 다시 쓰는 것). 규칙은 `portal-projection`의 순수 함수(이전 뷰 상태·이벤트·테넌트 임계치 → 알림 후보 0..n), 삽입은 `ON CONFLICT (tenant_id, agent_id, dedup_key) DO NOTHING`.
- `notification`·`inquiry` 표는 **V1에 이미 있고 RLS(V2)도 걸려 있다**(DE-22 재발 금지). 새 V는 필요한 열·인덱스·`CHECK`만. 커밋된 V는 고치지 않는다.
- 돈은 여전히 계산하지 않는다. 알림의 금액은 이벤트 값 그대로 **정수 파라미터**로 저장·전달하고 문장은 화면이 만든다(아래 ①·질문 1). 경계 "상향 통과" 판정은 전·후 단계의 **비교**(`LimitStage` — Phase 3 D-13, 곱셈 허용 목록 그대로).
- 이의제기 본문은 설계사 자유 텍스트다 — **작성자 본인의 조회 응답과 운영 CLI 출력에만** 나오고, 로그·지표·다른 응답·감사 `detail`에는 0. 화면에 "고객 이름·연락처·증권번호를 쓰지 마세요" 고정 문구. 평문 스캔 센티널을 본문에 넣는 주입으로 증명.
- 쓰기 API(`PATCH` 읽음·`POST` 문의)에 **CSRF 토큰·`Origin` 검사 없음**(Phase 5 수용 §4 Q4 — 쿠키 0·`Authorization`만·같은 출처·CORS 없음; 웹뷰는 `Origin`이 `null`일 수 있다). 근거는 설계서 §9 문단으로, 시험은 "쿠키를 실은 요청이라도 `Authorization` 없으면 401 같은 바이트".
- 엔진 변경 없음. 계약 1.2.0은 추가·선택 필드만(1.1.1 응답이 1.2.0을 통과 — `PortalApiCompatibilityTest` 확장).

## 1. 범위

⓪ **계약 `portal-api` 1.2.0**(첫 커밋): `GET /api/v1/me/notifications`(`unreadOnly?`, 페이지 — 최신순, `size` ≤ 100; 응답에 `unreadCount`), `PATCH /api/v1/me/notifications/{id}/read`(멱등, 본문 없음, 200 = 갱신된 항목), `POST /api/v1/me/inquiries`(`calcId?`·`category`(닫힌 열거 — 질문 5)·`message` ≤ 1,000자, ≤ 4 KiB), `GET /api/v1/me/inquiries`(본인 것, 최신순, `status` 필터), `GET /api/v1/me/inquiries/{id}`. 알림 항목은 `{id, type, severity, createdAt, readAt?, link?, params}` — `params`는 **`type`별 닫힌 스키마**(`oneOf`/`discriminator` 대신 `if/then` — 생성 클라이언트가 좁힐 수 있게; 금액은 `Won`, 증권번호는 `policyMasked`·`policyKey`만, 월은 `Ym`). `link`는 화면 경로 닫힌 목록 중 하나(`/lines/{calcId}`·`/limits/{ledgerId}`·`/statements/{ym}`·`/inquiries/{id}` — `policyKey` 없음). 거부 바이트에 429 재사용(문의 레이트 리밋), 404 같은 바이트(남의 알림·문의 id). `format` 0, 설명문 따옴표(`noKeyWithoutAValue`).

① **파생 규칙(`portal-projection`)**: §6.5 표 아홉 줄 중 이벤트 기반 여덟을 순수 함수로. dedup_key는 **`LIMIT_{STAGE}:{ledgerId}:{source_seq}`**(Phase 5 수용 Q2 — 상향 통과한 그 전기 이벤트의 seq; 같은 이벤트 재처리는 `UNIQUE`로 1회, 하향 뒤 재상향은 다른 seq라 새 행), 나머지는 표 그대로(`LIMIT_CUT:{calcId}`, `LIMIT_REBASE:{ledgerId}:{postingSeq}`, `CLAWBACK:{calcId}`, `DEFER_{to}:{scheduleId}`, `STMT_CONFIRMED:{ym}`, `PAYOUT:{ym}`). `POLICY_{type}:…`는 질문 3. 상향 판정 = 전기 **전** `accum_paid`의 단계(`LimitStage`) < 전기 **후** 단계 — 한 전기로 두 경계를 넘으면 **가장 높은 단계 하나**만(질문 2). 임계치는 그 테넌트의 bp(기존 `SyncStatusRepository` 경로). 수신자는 이벤트의 `agentId`(엔진 사실) — `identity_link`를 보지 않는다(알림은 설계사 사실이고 링크는 로그인 사실).

② **적용 배선(`portal-ingest`·`portal-infra`)**: `NotificationDeriver` 실 구현 — 프로젝터가 그 이벤트를 적용한 **뒤**, 같은 트랜잭션, 전 상태는 프로젍터가 읽은 행(두 번 읽지 않는다). 삽입 `ON CONFLICT DO NOTHING`. `Rebuilder`는 그대로(훅 호출 0 — ArchUnit·시험으로 고정). 재구축이 `notification`·`inquiry`를 지우지 않는 것은 그대로(`ViewReset` 목록 확인). 독 판정과의 관계: 파생 중 예외는 그 이벤트의 `APPLY_ERROR`(트랜잭션 롤백 — 알림이 뷰보다 먼저 남지 않는다).

③ **V7**: `notification`에 `params JSONB NOT NULL DEFAULT '{}'`, `title`·`body`의 처리(질문 1), `(tenant_id, agent_id, created_at DESC)` 인덱스, `type`·`severity`·`link` `CHECK`(닫힌 값·경로 형식); `inquiry`에 `category` `CHECK`, `(tenant_id, agent_id, created_at DESC)` 인덱스, `message` 길이 `CHECK`. `StatusEnumDdlTest`에 두 표의 상태 열.

④ **조회·쓰기 API(`portal-query` + 새 모듈 또는 기존 — 계획이 정함, 의존 방향 규칙 안)**: 알림 목록·읽음(본인 것만 — `agent_id` 두 번째 술어 스캔 그대로), 문의 접수(레이트 리밋 — 설계사당 **하루** N, 테넌트 `params.inquiry_rate_per_day` 기본 20, Phase 5 `RateLimitRule` 재사용 — 창만 24h, 429 같은 바이트, 행·감사 없음)·목록·상세. `PATCH`는 멱등(두 번째도 200, `readAt` 불변). 모든 응답은 1.2.0 통과, ETag·`private, no-cache` 규약은 GET에, 쓰기 응답은 `no-store`. 감사: 404는 기존대로 `AUTHZ_DENIED`, 성공 쓰기는 **감사 행 없음**(행 자체가 기록) — 질문 6.

⑤ **운영 CLI**: `inquiry list --tenant --status OPEN`, `inquiry answer --tenant --id <uuid> --answer-file <path>`(답변 본문은 **파일**로 — 인자·환경에 싣지 않는다, 로그 0), `inquiry close`. 행위자는 Phase 3 규약(`cli:<user>`, OPERATOR 링크). `answer`는 한 트랜잭션에 `inquiry` 갱신(`ANSWERED`, `answered_by`, `answered_at`) + 알림 `INQUIRY:{inquiryId}`(§6.5 아홉째 줄 — 이벤트가 아닌 유일한 파생) 삽입. CLI 출력에 `message`·`answer`가 나온다(운영자 목적) — 그 외 어디에도 0.

⑥ **화면**: `/notifications`(알림함 — 최신순, 읽음/안읽음 구분, 항목 탭 → `link` 경로로 이동 + 읽음 처리, "모두 읽음"은 없음), `/inquiries`·`/inquiries/:id`(내 문의 목록·상세·답변), 라인 상세 "이 항목 문의하기" → 문의 작성(카테고리 선택·본문·고정 문구·남은 글자 수 — 문자열 길이, 금액 아님) → 접수 결과, 홈 "최근 알림 3건" + 안읽음 수(API 값 — `unreadCount`) → 알림함. 알림 문장은 **`messages.ko.json`의 `type`별 템플릿 + `params`**(금액은 `<Amount>` + 라벨 — 알림 라벨은 "알림 시점 값" 하나, 질문 1). 알림 `type` 열거 ↔ 템플릿 양방향 대조(`messages.test`). 경로·토큰·CSP·번들·a11y 규약 그대로(새 화면은 지연 청크 — `LAZY_SCREENS`).

⑦ **E2E**: GA01(A-1 — `LIMIT_CUT`·`CLAWBACK`·`STMT_CONFIRMED`·`PAYOUT`·`LIMIT_FULL` 상향, 머리를 올리며 알림이 **그 시점에만** 생기는 것), GA04(`DEFER_HELD`·`DEFER_CANCELLED`, 재산정 `LIMIT_REBASE`, DRIFT 달은 알림 0), 상향·하향·재상향 손 스트림(한 원장이 WARN → 환수로 OK → 다시 WARN: 알림 둘, seq 다름), 재구축 뒤 알림 수 불변(하네스 제어로 재구축 → 같은 수), 문의 접수 → 목록 → 하네스 제어가 CLI `answer` 실행 → `INQUIRY` 알림 → 상세에서 답변 → 읽음. 레이트 리밋 N+1 → 429. 누출: 문의 본문에 센티널을 **넣어 접수**하고 작성자 상세·CLI 출력 밖(다른 설계사 응답·로그·지표·E2E 산출물의 다른 파일·콘솔·주소) 0 — 센티널 목록을 본문용으로 하나 더.

⑧ **문서**: 설계서 v1.9(§6.5 dedup_key·파생 지점 확정·재구축 문장, §7 1.2.0, §8 1·5·6 최종, §9 CSRF 근거 문단, §10 레이트 리밋·보존(알림 1년 — 삭제 잡 없음, `docs/운영.md` SQL), §12, §13, §14.1 #3(다이제스트 — v2로 닫기)·#12 닫기), `CLAUDE.md` "알림·문의 규약" 절, `docs/authz-matrix.md` 행 5, `docs/운영.md` CLI 셋, DECISIONS P6-n, RM-4 기록.

## 2. 완료 기준

| # | 기준 | 증명 |
|---|---|---|
| C1 | 계약 1.2.0: 1.1.1 응답 전부 통과, `format` 0, `params`가 `type`별로 닫힘(모르는 키 거부), `link` 닫힌 경로 | `PortalApiCompatibilityTest`·`PortalApiContractTest` 확장, 주입: `params`에 미정의 키 |
| C2 | **경계 상향 1회**: 같은 이벤트 재처리(재시작·재폴링) 알림 0 추가, 하향 뒤 재상향은 새 알림, 한 전기로 두 경계 통과 시 1건 | 프로젝션 순수 시험(표 전수) + 통합(재시작) |
| C3 | **재구축은 파생 0**: 재구축 전후 `notification` 바이트 동일, `Rebuilder` → `NotificationDeriver` 호출 경로 없음 | `RebuildIT` 확장 + ArchUnit, 주입: `Rebuilder`에 훅 호출 |
| C4 | 파생은 적용과 한 트랜잭션: 파생 예외 → 그 이벤트 `APPLY_ERROR`, 뷰·알림 둘 다 롤백 | 통합 시험(파생기에 예외 주입) |
| C5 | 알림 `params`·`link`에 증권번호 원문·`agentId`·dedup_key 0, 응답에 `dedup_key`·`title`·`body` 열 노출 0 | 응답 스캔 확장 + 계약(`additionalProperties: false`) |
| C6 | 알림·문의는 본인 것만 — 남의 id는 404 같은 바이트 + 감사, `PATCH` 멱등, 쿠키 있어도 `Authorization` 없으면 401 | `Notification*IT`·`Inquiry*IT`, `UniformRejectionIT` 확장 |
| C7 | 문의 본문 격리: 센티널 본문이 작성자 상세·CLI 출력에만, 로그·지표·다른 응답·감사·E2E 산출물 0; 길이 상한·4 KiB 본문 상한(상한+1 스트림 읽기 — Phase 5 P5-2) | 통합 + E2E 센티널, 주입 |
| C8 | 문의 레이트 리밋 하루 N: N 통과·N+1 429·동시 N+5 ≤ N, 429는 행·감사 0 | `RateLimitIT` 패턴 |
| C9 | CLI `answer` 한 트랜잭션(문의 갱신 + `INQUIRY` 알림), 답변은 파일 인자만, 행위자 OPERATOR 링크, 로그에 본문 0 | CLI 통합 시험, 주입: 알림 삽입 실패 → 문의도 롤백 |
| C10 | 화면: 알림 문장은 템플릿 + `params`(한글 리터럴 0), 금액은 `<Amount>` + 라벨, `type` ↔ 템플릿 양방향, 안읽음 수는 API 값, 남은 글자 수는 문자열 길이 | Vitest, 주입: 템플릿 하나 삭제 |
| C11 | E2E ⑦ 전 시나리오 × 세 프로젝트, axe 0, CSP 0, 누출 0(본문 센티널 포함), 번들 상한 유지, 새 npm 의존성 0 | `web-e2e` 아티팩트 |
| C12 | Phase 0~5 무손상, `no-docker` 실패, 평문 스캔(센티널 확장), 검증 메타데이터 diff 0 목표, 주입 ID 단위, 실 엔진 E2E 스크립트 무변경(알림은 피드 결과로 자연히 생긴다 — `RESULT.md` 판정에 "알림 행 수 > 0" 한 줄만 추가) | CI 아티팩트 |

## 3. 계획에서 답할 질문

1. **알림 문장의 자리**: V1 `notification.title`·`body`는 `NOT NULL TEXT`다. ⓐ 서버가 한국어 문장을 만들어 저장(금액이 문장 안에 — 화면 라벨 규약 밖) ⓑ **`params JSONB` + 화면 템플릿**, `title`·`body`는 V7에서 `DROP NOT NULL`(또는 `type` 코드로 채움)하고 응답에 내지 않는다. 권장 **ⓑ** — 문구는 데이터(`messages.ko.json`), 금액은 `<Amount>`. 알림 금액의 라벨 하나("알림 시점 값" — 현재 상태가 아니다)를 둘지, 링크 대상 화면이 현재 상태를 보이니 라벨을 두지 않고 금액도 보이지 않을지(알림은 "무슨 일"만, 금액은 화면에서). **금액을 알림에 넣지 않는 쪽**도 검토 — 그러면 `params`에 금액이 없어 누출·라벨 문제가 줄어든다.
2. **한 전기로 두 경계 통과**(OK → CRIT): 알림 하나(최고 단계)인지 둘인지. 권장 하나 — 설계사가 받을 행동은 같다.
3. **`POLICY_{type}:{policyNo}:{occurredOn}`의 증권번호**: dedup_key는 DB 안에만 있지만 원문을 넣을 이유가 없다. ⓐ `policyKey`(HMAC — 비밀 회전 시 dedup이 깨진다) ⓑ 그 계약의 `ledgerId`(원장 없는 계약은?) ⓒ `eventId`. 권장 **ⓒ `POLICY_{type}:{eventId}`** — 같은 이벤트 재처리만 막으면 되고(엔진이 같은 사실을 두 이벤트로 내지 않는다), 비밀·원장 유무와 무관.
4. **파생이 보는 전 상태**: 프로젝터가 읽은 행을 어떻게 파생기에 넘기나(프로젝터 반환값 vs 파생기가 같은 트랜잭션에서 다시 읽기). 권장 프로젝터가 `Applied{before, after}`를 돌려주고 파생기는 읽지 않는다 — 두 번 읽지 않고, 순수 함수 입력이 명시된다.
5. **문의 `category` 열거**: 설계서에 값이 없다. 권장 `AMOUNT`(금액이 다르다)·`LIMIT`(한도 판정)·`CLAWBACK`(환수)·`DEFERRAL`(분급)·`OTHER` — `calcId` 필수인 것은 처음 넷, `OTHER`는 선택.
6. **쓰기 성공의 감사**: `inquiry` 행·`read_at`이 기록이므로 `audit_access`에는 쓰지 않는 것을 권장. 반대 근거가 있으면.
7. **알림 보존 1년**: 삭제 잡 없음 + 운영 SQL(시뮬레이션과 같게)로 충분한지. 읽은 알림만 지울지 전부인지(권장 전부 — 기준은 `created_at`).
8. **홈 "최근 알림 3건"의 콜**: `GET /me/notifications?size=3`(안읽음 수 포함) 한 콜 — §10 홈 콜 수 표에 더한다(다섯). 안읽음 배지만 필요할 때 `/me`에 `unreadNotifications`를 둘지. 권장 전자만.
9. **CLI와 하네스**: E2E가 CLI `answer`를 어떻게 부르나(하네스 제어 엔드포인트가 JavaExec? 같은 JVM의 CLI 진입점 호출?). 비밀·행위자 규약이 깨지지 않는 쪽.
10. **다이제스트**: `notification_digest` 파라미터는 그대로 false, 기능은 v2 — §14.1 #3을 그렇게 닫을지.

## 4. 하지 말 것

커서 뒤 별도 파생기(RM-4). 재구축에서 파생. 서버가 만든 한국어 알림 문장(질문 1 ⓐ로 결정되지 않는 한). 알림·문의 응답의 `dedup_key`·`agent_id`·증권번호 원문. 문의 본문의 로그·지표·감사 기록. CSRF 토큰·`Origin` 검사. 푸시·알림톡·이메일 채널. 다이제스트. 운영 화면(CLI만). "모두 읽음". 엔진 변경. 1.2.0 밖의 API 변경.

## 5. 보고 형식

Phase 5와 같게. 추가: 파생 규칙 표(최종 — 트리거·조건·dedup_key·`params` 키·`link`), 상향/하향/재상향 시험 표, 재구축 전후 알림 수, 문의 본문 격리 증거(센티널이 나온 곳의 닫힌 목록 둘 — 작성자 상세·CLI 출력), 거부 바이트 표(변경 없으면 "그대로"), 홈 콜 수 표, 다음 Phase 질문(Phase 7 — 대사·DRIFT 자동 복구·관측성: 엔진 명세 조회 `GET /internal/v1/agents/{agentId}/statements/{ym}` 사용, 재대사 → `REPAIRED`, 운영 알림 채널, 지표·트레이스).
