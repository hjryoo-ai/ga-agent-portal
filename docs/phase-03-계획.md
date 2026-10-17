# 포털 Phase 3 계획 — 조회 API·인증·인가 (ga-agent-portal)

> 지시문 `docs/phase-03-지시문.md` v1.0(2026-10-17). 브랜치 `work/phase-3`(`main` = `90041b4` 위). 이 계획이 승인되기 전에는 코드를 쓰지 않는다.
> 읽은 것: 지시문 전문, 설계서 v1.5.1 §5·§7·§9·§10·§14.1, `CLAUDE.md`, V1~V4, `ArchitectureRulesTest`·`TenantPredicateScanTest`, `IngestWorker`(커서 갱신 경로),
> 플랫폼 0.2.0 소스(`IdentityResolver`·`Ratio`·`TenantDirectoryReader`), ga-disclosure 6A의 `ApiSecurityConfiguration`·`AuthorizationCoverageTest`·`AuthzMatrixTest`.

## 승인 반영 (2026-10-17, `docs/phase-03-계획승인.md`) — 아래 본문보다 우선한다

- **Q1~Q12 전부 권장안.** 지시문 오류 DE-12~18, 설계서 결함 DD-13(§7 `/me` "마스킹된 이름").
- **Q1**: 허용 목록 첫 항목 = `com.ga.portal.infra.directory.IssuerDirectoryReader`, 사유 "발급자 표는 테넌트 설정 — 테넌트 바인딩 전에 읽어야 한다". 발급자 URL은 로그에 **해시**(D-14 확장 — `LogIds.issuer`). `IssuerDirectoryIT`(세 열 밖·다른 표 권한 오류, M15).
- **Q2**: `agent_profile.org_path`(엔진 사실 — 표시용)와 `identity_link.org_path`(포털 사실 — 인가용, v2)는 다른 출처 — 설계서 §9에 한 줄, 둘이 달라도 어느 쪽도 고치지 않는다(v2 결정).
- **Q3**: `caught_up_at` 갱신은 피드 호출 성공 **그리고** `last_seq = headSeq` **그리고** 커서 `RUNNING`일 때만, 잠금 안 짧은 트랜잭션. 피드 실패·정지 상태는 건드리지 않는다(M12).
- **Q4**: `cfg` 입력은 그 요청이 실제로 읽은 설정만 — 엔드포인트별 표(⑤ 아래). 소유 확인 뒤 비교.
- **Q5**: 비밀 회전 시 모든 `policyKey`가 바뀐다 — `docs/운영.md`, 화면은 키를 저장하지 않는다. 키 → 원문은 요청 안에서 그 설계사의 증권번호 집합으로만(SQL에 키 없음).
- **Q6**: `detail`은 닫힌 사유 코드만, 지표 `tenant` 태그는 발급자가 등록된 경우만.
- **F7·F8·F9**: 새 의존성 Boot BOM 셋 — 검증 메타데이터 **재생성 diff를 보고서에**(의도 외 아티팩트 0). `limit 0` → `utilization` 생략.
- **②-6**: 허용 오차 60초, 주입된 시계(`JwtTimestampValidator.setClock`).

## 0. 먼저 결정이 필요한 것 (요약)

| # | 질문 | 권장 | 대안 |
|---|---|---|---|
| Q1 | D-1 `iss` → 테넌트 조회는 **테넌트를 바인딩하기 전**에 해야 한다. `tenant`는 FORCE RLS이고 `portal_directory`는 `tenant_id`만 읽는다(D-1이 그대로 두라고 했다) — 어느 롤이 `oidc_issuer`를 읽나 | **전용 롤 `portal_issuer`**(V5: `tenant`의 `(tenant_id, oidc_issuer, oidc_audience)` 컬럼 SELECT + 이 롤 전용 정책만) + 포털 리더 `com.ga.portal.infra.directory.IssuerDirectoryReader` — **포털 직접 DB 접근 허용 목록의 첫 항목**(불변 규약 2의 세 조건: 닫힌 FQN·전용 롤·테넌트 데이터 미접근 — 발급자 표는 업무 데이터가 아니라 테넌트 설정). 요청마다 고유 인덱스 조회 1회, 캐시 없음(발급자 해제가 즉시) | ⓑ `portal_directory` 목록 → 테넌트마다 `portal_app`으로 읽어 메모리 표(주기 갱신) — 허용 목록은 비지만 등록·해제가 주기만큼 늦고 요청마다가 아닌 시점의 사실로 인가한다. ⓒ 설정 파일 — D-1(V5 열)과 다르다 |
| Q2 | V1 `identity_link.agent_id NOT NULL` — D-9의 `OPERATOR` 주체(`cli:<user>`)에는 설계사가 없다. 또 플랫폼 `ResolvedIdentity`는 **`orgPath`가 비면 예외**인데, 엔진 A-1 스트림에는 `AgentSnapshot`이 없어 `agent_profile`이 비어 있다(둘째 판 11건 중 0) — `agent_profile`에서 조직을 읽으면 A-1 설계사 전원이 404 | V5: `agent_id` NULL 허용 + `CHECK ((role = 'OPERATOR') = (agent_id IS NULL))`, **`identity_link.org_path TEXT`**(AGENT·MANAGER 필수 — `CHECK (role = 'OPERATOR' OR org_path IS NOT NULL)`; 설계서 §14.1 #9 "그 전까지 조직 정보는 `identity_link`(Phase 3)" 그대로). 플랫폼 `IdentityResolver`를 그대로 구현: PK 조회 1회 → `ACTIVE`이고 `agent_id`가 있으면 `ResolvedIdentity(agent_id, {role}, org_path)`, 아니면 빈 값 | ⓑ 플랫폼 인터페이스를 v2(조직장)까지 미루고 포털 포트(`Caller`만)로 — 지시문 §1 머리·D-2 문구와 다르다 |
| Q3 | D-6 "`now − 커서 updated_at` > 300s ⇒ `SYNC_DELAYED`" — **유휴 폴링은 `updated_at`을 바꾸지 않는다**(`IngestWorker.finish`는 `ensureCursor`로 읽기만, 적용이 있을 때만 갱신). 글자대로면 이벤트가 없는 밤사이 모든 테넌트가 5분 뒤 `SYNC_DELAYED` | V5 `ingest_cursor.caught_up_at TIMESTAMPTZ`: 회차가 피드를 받았고 끝에 `last_seq = headSeq`면 그 시각(별도 짧은 트랜잭션, 잠금 안에서 커서 상태가 `RUNNING`일 때만). `SYNC_DELAYED` ⇔ 상태 ≠ `RUNNING` **또는** `caught_up_at` 없음 **또는** `now − caught_up_at > sync_delay_seconds` — 엔진 다운(피드 실패)·밀림(머리 뒤처짐)이 둘 다 잡힌다. `asOf.at`은 지시문대로 `updated_at`(마지막 적용 시각) | ⓑ `updated_at`을 유휴 폴링마다 갱신 — `asOf.at`의 뜻(마지막 반영 시각)이 사라진다 |
| Q4 | D-8 `ETag: W/"<seq>"` — 같은 `seq`에서도 응답이 바뀐다: ⓐ `dataStatus`는 시간이 지나면 `OK` → `SYNC_DELAYED`(Q3) ⓑ `payoutExpectedOn`·경고 단계는 `tenant` 설정(지급일 규칙·임계치)에서 온다. 304가 바뀐 상태를 숨긴다 | `W/"<seq>.<sync>.<cfg>"` — `sync` = `dataStatus` 한 글자(`O`/`D`), `cfg` = 그 요청이 읽은 테넌트 설정(지급일 규칙·임계치 bp·지연 초)의 SHA-256 앞 8자. 라인 목록은 뒤에 `.<page>.<size>.<필터 해시 8자>`. 소유 확인 뒤에만 비교(남의 자원은 304가 아니라 404) | ⓑ 지시문 글자대로 + `max-age` 없음이라 괜찮다고 보기 — 지연 배지가 캐시에 갇힌다 |
| Q5 | D-7 `policyKey` = SHA-256(`tenant_id` + `\u001f` + `policy_no`) 앞 16자 — **비밀이 없으면 되돌릴 수 있다**: 증권번호는 형식이 좁아(A-1 `POL-2026-0001` — 해마다 수만 개) 테넌트 ID를 아는 누구나 열거로 원문을 찾는다. `policyKey`는 쿼리 문자열(`?policyKey=`)로 접근 로그·프록시에 남는다 | **HMAC-SHA256(배포 비밀, `tenant_id` + `\u001f` + `policy_no`) 앞 16자** — 비밀은 파일(`portal.query.policy-key-secret-file`, 토큰 디렉터리와 같은 취급, 값은 저장소·로그에 0). 결정적이라 재구축·재시작 뒤에도 같다. 계산은 앱(`portal-query`는 포트로 받는다)에서 — SQL 필터는 키 → 그 설계사의 증권번호 집합을 앱이 대조(아래 ⑤) | ⓑ 지시문 글자대로(비가역이라는 문구를 "난독화"로 고쳐 적기) |
| Q6 | D-3 거부 감사: `audit_access`에는 `detail` 열이 없고(V1: actor·action·target·at), 키가 `tenant_id`라 **테넌트를 모르는 401**(발급자 미등록·형식 오류)은 행을 쓸 곳이 없다. 서명이 틀린 토큰도 등록된 `iss`만 실으면 그 테넌트에 행을 만들 수 있다(쓰기 증폭) | V5 `audit_access.detail TEXT`(닫힌 사유 코드). **404 계열**(인증은 됐으나 거부 — 링크 없음·REVOKED·역할·남의 자원)은 감사 행(별도 트랜잭션), **401 계열**은 지표 `api_authn_rejected_total{reason}`(+ 발급자가 등록된 경우만 `tenant` 태그)만 | ⓑ 401도 아는 테넌트면 감사 — 무인증 요청이 감사 표를 채운다 |
| Q7 | §7 "라인 목록(취소 짝 접기)" + 페이지 — 원본과 취소분이 다른 페이지에 갈 수 있다 | 응답은 평평한 라인(`reversalOf`·`reversedBy` 필드)이고 **접기는 화면(Phase 4)**. 정렬은 `source_seq, calc_id`(안정) | ⓑ 서버에서 짝 단위 페이지 — 페이지 크기가 행 수와 달라진다 |
| Q8 | D-11 "모든 컨트롤러 메서드는 `@UseCaseEntry` 표식 + `Caller`만" — ga-disclosure는 표식을 **유스케이스 메서드**에 두고 "컨트롤러는 진입점만 부른다"로 묶었다 | ga-disclosure와 같게: 표식은 `portal-query` 유스케이스 메서드(`Caller`를 받고 스스로 역할을 확인 — `AuthorizationPort.require`의 포털판), ArchUnit ⓐ 모든 `/api` 컨트롤러 메서드가 `Caller` 인자를 받고 `@UseCaseEntry` 메서드를 **정확히 하나** 부른다 ⓑ 컨트롤러가 `Jwt`·`JwtAuthenticationToken`·`Principal`·`Authentication`을 참조하지 않는다 ⓒ 표식 없는 `Caller` 인자 공개 메서드 0 | ⓑ 컨트롤러에 표식(지시문 글자대로) — 인가가 웹 계층에 묶여 CLI·시험 경로가 우회한다 |
| Q9 | 설계서 §7 `GET /me` "마스킹된 이름" ↔ D-7 "설계사 자기 이름은 그대로" | D-7(본인에게 본인 이름) — 설계서 §7 고침(DD-13). `agent_profile` 행이 없으면(엔진이 `AgentSnapshot`을 아직 안 냈다 — A-1) 프로필 필드는 생략하고 200 | — |
| Q10 | D-9 테넌트 없는 `ingest status`(전 테넌트)는 어느 `identity_link`를 보나 | 테넌트마다 그 테넌트의 OPERATOR 링크 — 없는 테넌트는 줄 `T not authorized` + 그 테넌트에 `AUTHZ_DENIED`, 하나라도 거부면 종료 코드 2. `--actor` 인자는 사용법 오류(64)로 | ⓑ 하나라도 없으면 전체 거부 |
| Q11 | `/me/earnings?from&to` 범위 규칙(지시문에 없음) | 둘 다 필수, `from ≤ to`, 폭 ≤ 36개월 — 아니면 **400 `INVALID_REQUEST`**(한 가지 본문, 사유 없음). 그 범위에 행이 없으면 빈 목록 200(월 하나 `/{ym}`은 행이 없으면 404) | — |
| Q12 | `payout_day_rule` `NEXT_MONTH_D{n}`에서 n이 그 달 일수보다 크면(D31 · 2월) | 생략 + 경고(룰을 고쳐 해석하지 않는다 — D-12 "형식이 다르면 생략"과 같은 취급). `1 ≤ n ≤ 28`이면 늘 존재 | ⓑ 말일로 당김 — 정하지 않은 영업 규칙을 지어낸다 |

**지시문 측 오류 후보**(승인에서 확정되면 DECISIONS DE-12~): D-1(조회 롤 미지정 — Q1), D-2·D-9·D-10(`identity_link.agent_id NOT NULL`·`orgPath` 필수와 OPERATOR — Q2), D-6(유휴 폴링 — Q3), D-8(ETag가 시간·설정 의존을 빠뜨림 — Q4), D-7("비가역" — Q5), D-3(`detail` 열 없음·무테넌트 401 — Q6), D-10 `USING limit_multiple::text`(아래 ⑥ — 기존 행이 `"12.00"`이 된다; `trim_scale(...)::text`여야 지금 읽기와 같다).

## ① 확인한 사실 (지시문·설계서와 다른 곳)

| # | 사실 | 출처 | 영향 |
|---|---|---|---|
| F1 | `ingest_cursor.updated_at`은 적용·정지·운영 명령 때만 바뀐다 | `IngestWorker.finish`(읽기만), `IngestApplier` | Q3 |
| F2 | `ResolvedIdentity`는 `orgPath` 공백이면 `IllegalArgumentException`, `agentId`는 `AgentId` | 플랫폼 0.2.0 소스 | Q2 |
| F3 | A-1 둘째 판에 `AgentSnapshot` 0건 — `agent_profile` 빈 테넌트가 정상 | `a1_portal_150000_e2.jsonl` | Q2·Q9, E2E(D-16) |
| F4 | `tenant`는 FORCE RLS, `portal_directory`는 `tenant_id`만 | V2·V3 | Q1 |
| F5 | `audit_access`에 `detail` 없음, PK `(tenant_id, id)` | V1 | Q6 |
| F6 | V4 `limit_multiple NUMERIC(6,2)`의 현재 읽기는 `trim_scale(...)::text`(`"12"`); `::text`는 `"12.00"` | `PolicyLimitRepository`, PostgreSQL | ⑥ |
| F7 | 아키텍처 규칙 (a): `portal-query`는 `domain`·`projection`만 볼 수 있다(`infra`·플랫폼 spring 불가). (b) Spring 무의존 목록에 `portal-query`는 없다 | `ArchitectureRulesTest` | ⑦ — (b)에 `portal-query`를 더한다 |
| F8 | Spring Security·리소스 서버 의존성이 아직 없다 | `portal-app/build.gradle.kts` | 새 의존성(Boot BOM: `spring-boot-starter-security`·`-oauth2-resource-server`, 전이 `nimbus-jose-jwt`) — 락·검증 메타데이터를 빈 `GRADLE_USER_HOME`에서 다시 |
| F9 | `Ratio.toPercentString()`은 소수 1자리(`"80.0%"`), 분모 0이면 예외 | 플랫폼 소스 | `limit_amount = 0` 원장은 `utilization` 생략(엔진이 한도 미적용 계약에는 원장을 열지 않으므로 보통 없다) |

## ② 토큰 검증 순서 (전부 같은 401 바이트)

`/api/**` 체인 하나(무상태, 세션·CSRF·요청 캐시·익명 없음). Spring `JwtIssuerAuthenticationManagerResolver`에 포털 `IssuerTenantResolver`를 꽂는다:

| 순서 | 검사 | 실패 사유 코드(감사·지표용 — 응답에는 없음) |
|---|---|---|
| 1 | `Authorization: Bearer <토큰>` 하나(없음·다른 스킴·둘 이상) | `NO_TOKEN` |
| 2 | JWS 형식 파싱(서명 검증 전, 헤더·`iss`만 읽음) | `MALFORMED` |
| 3 | 헤더 `alg` = `RS256`(그 밖 — `none`·`HS256` 포함) | `ALG_NOT_ALLOWED` |
| 4 | `iss` → `IssuerDirectoryReader`(Q1) — 등록 테넌트 하나 | `ISSUER_UNKNOWN` |
| 5 | 그 발급자의 디코더(발급자 메타데이터 → JWKS, 발급자별 캐시 — 테넌트가 아니라 발급자 URL이 키): 서명 | `SIGNATURE` |
| 6 | `exp`·`nbf`(허용 오차 60초, **주입된 `Clock`** — `JwtTimestampValidator.setClock`) | `EXPIRED` |
| 7 | `aud`에 그 테넌트의 `oidc_audience` | `AUDIENCE` |
| 8 | `tenant_id` 클레임이 있으면 = 4의 테넌트 | `TENANT_MISMATCH` |

지시문의 일곱 사유(토큰 없음·서명·만료·`iss`·`aud`·`tenant_id`·`alg`) + 형식 오류 하나. 응답: `401`, `Content-Type: application/problem+json`, `WWW-Authenticate: Bearer`(값 하나 — 사유·`error=` 없음), 본문 `{"code":"UNAUTHENTICATED","message":"authentication required"}` 고정 바이트. 헤더 집합은 Spring Security 기본 보안 헤더 + 위 둘 — `UniformRejectionIT`가 이름·값 전부를 사유 여덟 개에서 비교한다.

## ③ `Caller` 해석 흐름

```
[예외 장벽 필터] → [보안 체인: ②] → [TenantBindingFilter: 인증된 Jwt의 테넌트(②-4) → TenantContext.runWith]
  → [CallerArgumentResolver: 요청 트랜잭션(읽기 전용, TenantSessionBinder) 안에서 IdentityResolver.resolve(sub)]
      ├ 빈 값(링크 없음·REVOKED·OPERATOR) → NotFound(사유 NO_LINK / REVOKED / ROLE)
      └ ResolvedIdentity(agentId, {role}, orgPath) → Caller(tenant, agentId, role)
  → 컨트롤러 → @UseCaseEntry 유스케이스: caller.role == AGENT 아니면 NotFound(ROLE); 자원 조회는 (tenant, caller.agentId, 키) — 없으면 NotFound(MISSING)
  → NotFound → 장벽: 404 고정 바이트 + 감사 AUTHZ_DENIED(별도 트랜잭션, detail = 사유, target = 경로 템플릿)
```

- `Caller`는 `portal-query`의 레코드(`TenantId`·`AgentId`·`Role`) — 생성자는 `CallerArgumentResolver`(앱)만 부른다(ArchUnit).
- 토큰에서 읽는 것은 `iss`(②)·`sub`·`aud`·`exp`/`nbf`·`tenant_id`(교차 검사)뿐 — `roles`·`agent_id` 클레임은 어디서도 읽지 않는다(`Jwt` 멤버 호출 클래스 = `IssuerTenantResolver`·`TenantBindingFilter`·`CallerArgumentResolver` 셋 — ArchUnit 닫힌 목록).
- 404 바이트: `{"code":"NOT_FOUND","message":"not found"}`. `/api/**`의 경로 없음·메서드 불일치·경로 변수 형식 오류(예: `ym` 형식)도 같은 404(라우팅 밖 처리기) — 존재 여부를 형식 오류로 흘리지 않는다. 쿼리 파라미터 형식 오류(Q11)만 400 `INVALID_REQUEST` 한 가지.

## ④ 인가 매트릭스 초안 (`docs/authz-matrix.md` — 정본, `AuthzMatrixTest`가 코드와 양방향 대조)

```authz-matrix
endpoint,AGENT,MANAGER,OPERATOR,NO_LINK,REVOKED
GET /api/v1/me,SELF,404,404,404,404
GET /api/v1/me/earnings,SELF,404,404,404,404
GET /api/v1/me/earnings/{ym},SELF,404,404,404,404
GET /api/v1/me/earnings/{ym}/lines,SELF,404,404,404,404
GET /api/v1/me/earnings/lines/{calcId},SELF,404,404,404,404
GET /api/v1/me/limits,SELF,404,404,404,404
GET /api/v1/me/limits/{ledgerId},SELF,404,404,404,404
GET /api/v1/me/deferrals,SELF,404,404,404,404
```

`SELF` = 그 설계사 자신의 행만(남의 키 → 404). 코드 쪽 정본은 `portal-query`의 `QueryAuthorization`(엔드포인트 템플릿 → 허용 역할 집합) — 표의 행 = 앱의 `@GetMapping` 템플릿 집합 = `QueryAuthorization` 키 집합(셋 중 하나만 바뀌어도 실패).

## ⑤ 응답 DTO ↔ 뷰 컬럼

공통: `asOf{seq = ingest_cursor.last_seq, at = updated_at}`, `dataStatus`(`OK`/`SYNC_DELAYED` — Q3 규칙), 금액 `integer`, 날짜 `YYYY-MM-DD`, 시각 ISO-8601 오프셋(`pattern`). `policyMasked` = `policy_no`의 끝 4자 외 `*`(4자 이하면 전부 `*`), `policyKey` = Q5.

| 엔드포인트 | 필드 ← 컬럼 |
|---|---|
| `/me` | `agentName` ← `agent_profile.name`, `grade`·`orgPath`·`status` ← `agent_profile`(행 없으면 생략 — Q9), `role` ← `identity_link.role`, `tenant{name, payoutDayRule, limitWarnBp, limitCritBp}` ← `tenant`(bp = SQL `round(x * 100)::bigint`) |
| `/me/earnings?from&to` | 항목마다 `closeYm, status, monthDataStatus(data_status), gross, clawbackOffset, adjustment, withholdingTax?, net?, netBasis?, paidAmount?, paidAt?` ← `earnings_month` |
| `/me/earnings/{ym}` | 위 + `byType{commType: Σ amount}` ← `earnings_line` `GROUP BY comm_type`(정수 합 — Phase 2 합산 규칙과 같은 성격), `estimateAsOf?, estimateBasisSeq?, confirmedAt?, receivableOffset?, carriedReceivable?, payable?, runSeq?` ← `earnings_month`, `payoutExpectedOn?`(D-12·Q12) |
| `/me/earnings/{ym}/lines` | `items[]{calcId, commType, amount, limitCutAmt, limitCutPolicy?, productKey?, productName?(policy_summary), insuredMasked?(policy_summary), policyMasked, policyKey, reversalOf?, reversedBy?}`, `page, size, total` — 필터 `commType`(정확 일치), `policyKey`(앱이 키를 그 설계사의 증권번호 집합에서 찾아 SQL에 `policy_no = :policyNo`로 — 키 → 원문 표는 요청 안에서만) |
| `/me/earnings/lines/{calcId}` | 라인 항목 + `basis{rate?, payoutRate?, installmentNo?, ruleVersionIds?}`(불투명 문자열 그대로), `policySummary{contractDate?, monthlyPremium?}`, `deferrals[]{scheduleId, dueYm, amount, status}` ← `deferral_item.source_calc_id`, `receivable?{amount, remaining, status}` ← `clawback_item.origin_calc_id`, `ledgerId?` ← `policy_limit(policy_no, agent_id)` |
| `/me/limits?status` | `items[]{ledgerId, policyMasked, policyKey, contractDate, fyStart, fyEnd, monthlyPremium, limitMultiple(문자열), limitAmount, accumPaid, remaining(= limit − accum, 정수 차감), utilization(Ratio 문자열), stage(OK/WARN/CRIT/FULL), cutTotal, deferredTotal, flags[](열린 policy_flag)}` |
| `/me/limits/{ledgerId}` | 위 한 항목 + `history[]{sourceSeq, kind, postingSeq?, calcId?, amount?, accumAfter, limitAfter, reason?, occurredAt}` ← `policy_limit_history` `ORDER BY source_seq` |
| `/me/deferrals?from&to` | `months[]{dueYm, expected(Σ SCHEDULED+RELEASED), held(Σ HELD)}`(§6.4 — 정수 합), `items[]{scheduleId, dueYm, amount, status, statusReason?, reason, payCondition, policyMasked, policyKey, sourceCalcId}` |

`stage`: `remaining ≤ 0` → `FULL`; `accum × 10000 ≥ crit_bp × limit` → `CRIT`; `≥ warn_bp × limit` → `WARN`; 아니면 `OK` — 전부 `long` 곱셈 비교(`Math.multiplyExact`), 저장 없음(D-13). `status` 필터는 그 단계와 같은지(`ALL` 기본).

**ETag `cfg` 입력(승인 Q4)** — 그 요청이 실제로 읽은 테넌트 설정만:

| 엔드포인트 | `cfg` 입력 |
|---|---|
| `/me` | `name`·`payout_day_rule`·`limit_warn_pct`·`limit_crit_pct`(응답에 싣는 넷) + `sync_delay_seconds` |
| `/me/earnings`·`/me/earnings/{ym}/lines`·`/me/earnings/lines/{calcId}`·`/me/deferrals` | `sync_delay_seconds` |
| `/me/earnings/{ym}` | `payout_day_rule` + `sync_delay_seconds` |
| `/me/limits`·`/me/limits/{ledgerId}` | `limit_warn_pct`·`limit_crit_pct` + `sync_delay_seconds` |

**조회 문장 수 상한(C13)** — 요청 하나(필터 포함): 발급자 1 + 링크 1 + 커서·테넌트 1(조인) + 본문. `/me` 본문 1(`agent_profile`) → **4**. `/earnings/{ym}` 본문 2(월 행·`byType`) → **5**. `/limits?status=WARN` 본문 1(원장 + 열린 플래그 `array_agg` 조인) → **4**. 라인·이벤트 수와 무관(`QueryStatementIT` — 라인 0 vs 1,000, 원장 1 vs 200).

## ⑥ V5 (`V5__query_api.sql`)

```sql
-- 발급자 표(D-1, Q1)
ALTER TABLE tenant ADD COLUMN oidc_issuer TEXT, ADD COLUMN oidc_audience TEXT;
ALTER TABLE tenant ADD CONSTRAINT uq_tenant_oidc_issuer UNIQUE (oidc_issuer);
ALTER TABLE tenant ADD CONSTRAINT ck_tenant_oidc_pair CHECK ((oidc_issuer IS NULL) = (oidc_audience IS NULL));
ALTER TABLE tenant ADD CONSTRAINT ck_tenant_oidc_issuer CHECK (oidc_issuer ~ '^https?://[^\s]+$');
CREATE POLICY tenant_issuer ON tenant FOR SELECT TO portal_issuer USING (true);
GRANT SELECT (tenant_id, oidc_issuer, oidc_audience) ON TABLE tenant TO portal_issuer;   -- + 롤 단언(V3 방식), init-roles.sql

-- identity_link(D-2, Q2): 기존 행은 Phase 0~2의 뜻 그대로 설계사 매핑 → AGENT·ACTIVE로 채운 뒤 NOT NULL
ALTER TABLE identity_link ADD COLUMN role TEXT, ADD COLUMN status TEXT, ADD COLUMN org_path TEXT, ADD COLUMN linked_at TIMESTAMPTZ;
UPDATE identity_link SET role = 'AGENT', status = 'ACTIVE', linked_at = now();
-- org_path는 채울 원천이 없다 — 기존 행이 있으면 아래 ck_identity_link_org가 마이그레이션을 실패시킨다. Phase 0~2에는 링크를 만드는 경로가 없어 운영 행 0(V4와 같은 이유)
ALTER TABLE identity_link ALTER COLUMN role SET NOT NULL, ALTER COLUMN status SET NOT NULL, ALTER COLUMN linked_at SET NOT NULL,
                          ALTER COLUMN agent_id DROP NOT NULL;
ALTER TABLE identity_link ADD CONSTRAINT ck_identity_link_role CHECK (role IN ('AGENT', 'MANAGER', 'OPERATOR')),
                          ADD CONSTRAINT ck_identity_link_status CHECK (status IN ('ACTIVE', 'REVOKED')),
                          ADD CONSTRAINT ck_identity_link_operator CHECK ((role = 'OPERATOR') = (agent_id IS NULL)),
                          ADD CONSTRAINT ck_identity_link_org CHECK (role = 'OPERATOR' OR org_path IS NOT NULL);

-- 동기화 시각(Q3), 감사 사유(Q6)
ALTER TABLE ingest_cursor ADD COLUMN caught_up_at TIMESTAMPTZ;
ALTER TABLE audit_access ADD COLUMN detail TEXT;

-- limit_multiple 원문(D-10·F6): 지금 읽기와 같은 글자로 옮긴다. 원문 그대로는 재구축이 되돌린다(계약 원문이 이벤트에 있다)
ALTER TABLE policy_limit ALTER COLUMN limit_multiple TYPE TEXT USING trim_scale(limit_multiple)::text;
ALTER TABLE policy_limit ADD CONSTRAINT ck_policy_limit_limit_multiple CHECK (limit_multiple ~ '^(0|[1-9][0-9]*)(\.[0-9]{1,6})?$');
```

`tenant.params.sync_delay_seconds`: §5 주석 + 설정 `portal.query.default-sync-delay-seconds: 300`(코드 상수 아님). 닫힌 값 컬럼 19 → **21**(`identity_link.role`·`status` — `StatusEnumDdlTest`). 프로젝터: `PolicyLimitRepository`의 `CAST(:limitMultiple AS NUMERIC)`·`trim_scale(...)::text` → 원문 그대로(C14 — `ProjectionGoldenIT`가 `"12"`).

## ⑦ 모듈·포트

| 모듈 | 추가 |
|---|---|
| `portal-domain` | `Role`(AGENT/MANAGER/OPERATOR)·`LinkStatus`, `IdentityLinkRecord`, `domain.view`에 `ProfileRow`·`SyncRow`(커서+테넌트 설정) |
| `portal-query`(Spring·DB 무의존 — 규칙 (b)에 추가, `verifySpringFree`) | `Caller`, `@UseCaseEntry`, `NotFound(사유)`, `QueryAuthorization`, 유스케이스 `MeQueries`(엔드포인트마다 진입점 하나), 포트 `AgentQueryStore`(모든 메서드가 `agentId`를 받는다)·`SyncStatusStore`·`PolicyKeys`, 규칙 `SyncStatusRule`(Q3)·`LimitStage`·`PayoutDay`·`PolicyMask`, 응답 레코드 |
| `portal-infra` | `persistence.query.AgentQueryRepository`(**두 번째 술어 대상** — 모든 문장 `agent_id = :agentId`), `IdentityLinkRepository`, `SyncStatusRepository`(커서·테넌트 조인), `directory.IssuerDirectoryReader`(Q1 — 허용 목록), `IngestCursorRepository.caughtUp` |
| `portal-ingest` | `IngestWorker`: 회차 끝 `caught_up_at`(Q3) — 포트 `IngestStore.markCaughtUp` |
| `portal-app` | `web/` 보안 구성·`IssuerTenantResolver`·`TenantBindingFilter`·`CallerArgumentResolver`·`ExceptionBarrierFilter`·`ProblemResponses`(고정 바이트)·`UnroutedApiHandler`·`EtagSupport`·`MeController`, 어댑터 `JdbcAgentQueryStore`·`HmacPolicyKeys`, CLI `OperatorAuthorization`(D-9), 지표 `api_authn_rejected_total`·`api_authz_denied_total`(태그 `tenant`·`reason`) |

시험 픽스처: `TestIssuer`(인프로세스 RSA 2048 + JDK `HttpServer` — `/.well-known/openid-configuration`·`/jwks`, 토큰 발급 도우미: 사유별 잘못된 토큰 여덟 종), `QuerySeed`(두 설계사·두 테넌트·발급자·링크 — 허구 데이터).

## ⑧ 응답 스캔·평문 스캔

- `ResponseLeakScanIT`: 두 테넌트 × 두 설계사 시드(A-1 둘째 판 + 증권번호를 바꾼 사본 — 허구), 모든 엔드포인트 × 모든 키(월·라인 전부·원장 전부·페이지 전부)를 호출해 본문을 모은다 → 시드 증권번호 원문 집합과 교집합 0, 다른 설계사의 `calcId`·`ledgerId`·이름 0. 결과 표(엔드포인트 × 응답 수)는 보고서.
- `scanPlaintextLeaks` 센티널에 시드 subject·`agent_id`·증권번호 추가(D-14) — 시험 결과 XML·빌드 로그. 로그 해시 도우미 `LogIds.agent(id)` = SHA-256 앞 12자, `sub`는 앞 12자 해시.

## ⑨ 완료 기준 → 시험·주입

| C | 증명 | 주입 |
|---|---|---|
| C1 | `AuthzIT`(A 토큰으로 B의 월·라인·원장 → 404 = 없는 키 404, 바이트) | **M1** `AgentQueryRepository` 한 문장에서 `agent_id` 술어 제거 → `TenantPredicateScanTest`(두 번째 술어)·`AuthzIT` |
| C2 | `UniformRejectionIT`(② 여덟 사유 바이트·헤더 집합) | **M2** 만료에 다른 메시지 |
| C3 | 〃(링크 없음·REVOKED·MANAGER·OPERATOR·남의 자원 — 404 다섯) | **M3** MANAGER 허용 → `AuthzMatrixTest`·IT |
| C4 | `TenantBindingIT`(두 발급자 ↔ 두 테넌트, `tenant_id` 불일치, 남의 `agent_id` 클레임 → 자기 데이터, 미등록 발급자) | **M4** 클레임 `agent_id` 사용 |
| C5 | `AuthzMatrixTest`(표 ↔ `QueryAuthorization` ↔ `@GetMapping`), `ApiLayerRulesTest`(Q8 ⓐⓑⓒ) | **M5** 표식 없는 엔드포인트 |
| C6 | `ResponseLeakScanIT`, `PolicyMaskTest`·`PolicyKeysTest` | **M6** 라인 상세에 `policy_no` |
| C7 | `CachingIT`(ETag·304·같은 헤더·`private, no-cache`, 지연 전환 시 ETag 바뀜 — Q4) | **M11** ETag에서 `sync` 제거 → 지연 전환 뒤 304 |
| C8 | `SyncStatusRuleTest`(상태 넷 × 지연 경계), `SyncStatusIT`(유휴 폴링이 `caught_up_at`을 갱신, 피드 실패는 갱신 안 함) | **M7** `REBUILDING` → OK, **M12** 유휴 폴링에서 `caught_up_at` 갱신 빼기 |
| C9 | `AuditIT`(404 감사 행 — 본 트랜잭션 롤백 뒤에도 남음, 401은 행 0·지표), 센티널 | **M8** 로그에 `sub` 평문, **M13** 감사를 본 트랜잭션 안에서 |
| C10 | `IngestCliIT` 확장(미등록 OS 사용자·REVOKED·다른 테넌트 링크 → 거부·감사, OPERATOR 토큰 `/api` 404) | **M9** CLI 등록 검사 제거 |
| C11 | `PortalApiContractTest`(계약 `format` 0) + 모든 IT 응답을 계약으로 검증(`ApiContractValidator` — 응답 가로채기) | — |
| C12 | `LimitStageTest`(80.00% 정확히·79.99%·95%·FULL·`limit 0`), `PayoutDayTest`(D10·D28·D31 2월 생략·다른 형식 생략) | — |
| C13 | `QueryStatementIT`(⑤ 상한) | **M10** 원장마다 플래그 조회(N+1) |
| C14 | `ProjectionGoldenIT`·`ProjectionSchemaIT`(V5 뒤 `"12"`) | **M14** V5 `USING limit_multiple::text` → `"12.00"` |
| C15 | 보고서 | — |
| C16 | CI 아티팩트, 아키텍처 규칙, `verifySpringFree`(+`portal-query`) | — |
| 더함 | Q1 `IssuerDirectoryIT`(전용 롤이 세 열 밖·다른 표를 못 읽음) | **M15** `portal_issuer`에 `name` GRANT |
| 더함 | ② `alg` | **M16** `HS256`·`none` 허용 |

주입 **16건**(M1~M10 지시문 + M11~M16).

## ⑩ 단계 (단계마다 시험·주입·설계서 같은 커밋)

1. 승인 보관 + 계획 갱신.
2. V5·도메인(`Role`·`LinkStatus`·`Caller`)·`StatusEnumDdlTest` 21·`ProjectionSchemaIT`·프로젝터 `limit_multiple` 원문·`caught_up_at`(워커). 주입 M12·M14·M15.
3. 보안 체인·발급자 리더·시험 발급기·장벽·감사. `UniformRejectionIT`·`TenantBindingIT`·`AuditIT`·`IssuerDirectoryIT`. 주입 M2·M4·M8·M13·M16. 새 의존성(락·검증 메타데이터 — 빈 `GRADLE_USER_HOME`).
4. 조회 유스케이스·저장소·컨트롤러·계약 `portal-api.openapi.yaml`·ETag. `AuthzIT`·`CachingIT`·`ResponseLeakScanIT`·`QueryStatementIT`·단위. 주입 M1·M6·M7·M10·M11.
5. 인가 매트릭스·ArchUnit·두 번째 술어 스캔. 주입 M3·M5.
6. CLI 전환 + `docs/운영.md`. 주입 M9.
7. E2E 스크립트(`e2e/engine-feed/run.sh` — 시험 발급기를 띄우는 `./gradlew :portal-app:runTestIssuer`(정규 빌드 밖 `JavaExec`) 포함, 토큰·subject는 파일로) + README.
8. 문서(설계서 v1.6, `CLAUDE.md` 조회 규약, DECISIONS), 보고서, 태그, PR.

## ⑪ 설계서 v1.6에서 고칠 곳

§5(V5 — 발급자·링크 열·`caught_up_at`·`detail`·`limit_multiple TEXT`, `sync_delay_seconds`), §6.1(`caught_up_at`), §7(엔드포인트 표 — `policyKey` 필터·`/me` 이름(DD-13)·두 상태 필드(DD-12)·라인 평평·400/401/404·응답 모양), §9(발급자 표·검증 순서·역할 셋·`portal_issuer`·CLI 인가·`policyKey` HMAC), §10(ETag·지표), §14.1(#2 지급일 n > 28, #9 조직 정보 = `identity_link.org_path`).

## ⑫ 하지 않는 것

지시문 §4 그대로. 추가로: 토큰 갱신·로그인 화면(IdP 몫), CORS(웹 Phase 4가 같은 출처로 붙는다 — 그때), 레이트 리밋(5).

## ⑬ 다음 Phase 질문 (보고서에서 다시)

Phase 4 — 상태 라벨 규약(`netBasis` 셋 × 월 상태), "확정(세후 예상)", 정정 미지급 문구(§14.1 #12), `DEFER_AFTER_FY` 문구, 잠정 명세의 예상 이월(`carriedReceivable` — 지금 저장 안 함, V14-3), 같은 출처·CORS. Phase 5 — 레이트 리밋, `simulation_log`, 엔진 v1.4 시뮬레이션 계약.
