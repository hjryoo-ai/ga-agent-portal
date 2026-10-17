# 포털 Phase 3 지시문 — 조회 API·인증·인가 (ga-agent-portal, v1.0, 2026-10-17)

## 역할과 맥락

Phase 2(`phase-2` = `87c673f`, `main` = `90041b4` 계약 v1.4 포함)까지 포털은 엔진 스트림을 받아 뷰를 만든다. 이 Phase는 그 뷰를 **설계사 본인에게만** 보여 주는 조회 API를 연다 — `/api/v1/me/**`. 이 Phase가 끝나면 "다른 설계사의 데이터를 어떤 요청으로도 꺼낼 수 없다"가 **구조**(경로에 `agentId` 없음 · `identity_link`만 · RLS)와 **시험**(IDOR·인가 매트릭스·같은 바이트)으로 증명돼야 한다. 화면은 Phase 4, 시뮬레이션은 Phase 5, 알림·이의제기는 Phase 6.

정본: `docs/설계서.md` v1.5(§7, §9, §10, §12 Phase 3, §14.1), Phase 0~2 지시문의 불변 규약, `CLAUDE.md`. 브랜치 `work/phase-3`, 태그 `phase-3`, PR, **병합은 수용 심사 회신 뒤**. 계획 → 승인 → 단계 커밋 → 보고서 → 태그 → PR. **Phase 2 수용 심사 §2·§3의 결정(v1.4 — 이미 병합 `90041b4`, V5 `limit_multiple TEXT`, Q7 `SYNC_DELAYED`, Q6 REINSTATE no-op)은 이 지시문의 일부다.**

플랫폼 0.2.0 확인(2026-10-17, `API-0.2.0.txt`): `com.ga.platform.spring.identity.IdentityResolver`(`resolve(subject) → Optional<ResolvedIdentity(agentId, roles, orgPath)>`)는 **인터페이스뿐** — 포털이 `identity_link`로 구현한다. OIDC·JWT 도우미는 플랫폼에 **없다**(ga-disclosure는 앱 안에 자기 체인을 두었다) — 포털도 Spring Security 리소스 서버로 직접 만든다. 데모 발급기(`DemoOidcIssuer`)는 ga-disclosure 앱 것이라 쓸 수 없다 — 시험은 인프로세스 발급기(아래 D-3).

## 1. 결정

| # | 결정 | 근거·조건 |
|---|---|---|
| D-1 | **테넌트는 토큰의 `iss`로 정한다.** V5 `tenant.oidc_issuer TEXT UNIQUE`·`oidc_audience TEXT`(둘 다 NULL이면 로그인 불가 테넌트). 요청 토큰의 `iss`가 어느 테넌트의 발급자인지로 테넌트를 바인딩하고, 토큰에 `tenant_id` 클레임이 **있으면 같아야**(다르면 401), 없어도 된다. JWKS는 발급자 메타데이터에서(Spring 기본), 발급자별 디코더 캐시 | 설계서 §9 "토큰 클레임 tenant_id"는 신뢰 근거가 아니라 교차 검사다. 발급자 ↔ 테넌트 표가 포털 소유 사실. `portal_directory`는 이 두 열을 읽지 못한다(Phase 1 Q3 그대로). |
| D-2 | **`agent_id`·역할은 `identity_link`에서만.** V5 `identity_link`에 `role TEXT NOT NULL CHECK (role IN ('AGENT','MANAGER','OPERATOR'))`, `status TEXT NOT NULL CHECK ('ACTIVE','REVOKED')`, `linked_at`. 토큰의 `roles`·`agent_id`·`sub` 외 어떤 클레임도 인가에 쓰지 않는다(시험: 다른 설계사의 `agent_id` 클레임을 실은 토큰 → 자기 데이터). `IdentityResolver` 구현 = `identity_link` PK 조회 1회(캐시 없음) | 규약 3. `MANAGER`는 v2(§14.1), 지금은 `/api/v1/me/**` 접근 **거부**(AGENT만). `OPERATOR`는 CLI(D-9). `REVOKED`는 모든 요청 거부. |
| D-3 | **거부는 같은 바이트**: 토큰 없음·서명·만료·`iss` 미등록·`aud` 불일치·`tenant_id` 불일치·`alg` 비허용 → **401 한 가지 본문**; `identity_link` 없음·`REVOKED`·역할 ≠ AGENT·남의/없는 자원 → **404 한 가지 본문**. 본문은 `application/problem+json` `{code, message}`(코드 `UNAUTHENTICATED` / `NOT_FOUND`), 헤더 집합도 같다(`WWW-Authenticate`는 401 전부에 같은 값). 거부 사유는 **감사**(`audit_access` action `AUTHZ_DENIED`, 별도 트랜잭션, detail = 사유 코드 — 값 없음)와 지표에만 | ga-disclosure 6A와 같은 규약. 시험은 응답 바이트 비교. 시험 발급기: 인프로세스 RSA 키 + JWKS 스텁 서버(`StubFeedServer`와 같은 JDK `HttpServer`) — Keycloak 컨테이너는 Phase 8 데모에서. |
| D-4 | **경로에 `agentId`가 없다**(§7). 자원 조회는 전부 `(tenant, agent_id, 키)`로 — 남의 `calcId`·`ledgerId`는 "없다"(404). `TenantScopedRepository` 쿼리에 `agent_id = :agentId` 술어가 **반드시** 있어야 하는 저장소 집합을 `TenantPredicateScanTest`에 **두 번째 술어**로 추가(`/api` 조회 저장소 = `agent_id` 술어 필수, 허용 목록 빈 상태) | IDOR의 구조적 제거 + 스캔 시험. |
| D-5 | **엔드포인트(Phase 3 범위)**: `GET /api/v1/me`, `/me/earnings?from&to`, `/me/earnings/{ym}`, `/me/earnings/{ym}/lines?page&size&commType&policyKey`, `/me/earnings/lines/{calcId}`, `/me/limits?status=WARN\|CRIT\|FULL\|ALL`, `/me/limits/{ledgerId}`, `/me/deferrals?from&to`(시나리오 토글은 Phase 5). `page` 0부터, `size` 1~200 기본 50. 응답 공통: `asOf{seq, at}`(커서 `last_seq`·`updated_at`), `dataStatus` | §7. 알림·이의제기(6), 시뮬레이션(5), `/api/v1/org`(v2) 없음. |
| D-6 | **`dataStatus`**: `SYNC_DELAYED` ⇔ 커서 상태 ∈ {`FAILED`,`PAUSED`,`REBUILDING`} 또는 `now − 커서 updated_at` > `tenant.params.sync_delay_seconds`(기본 300); 아니면 그 월/자원의 `data_status`(`OK`/`DRIFT`). 월 응답은 둘을 **따로** 준다: `dataStatus`(동기화) + `monthDataStatus`(`OK`/`DRIFT`) — 설계서 §7 "`dataStatus: OK\|DRIFT\|SYNC_DELAYED`" 한 필드는 두 뜻을 섞었다(DD-12) | Phase 2 수용 Q7. 재구축 중에도 옛 행은 정확하지만 "갱신 중"을 숨기지 않는다. |
| D-7 | **증권번호는 응답에 없다.** `policyMasked`(끝 4자리만, 나머지 `*`)와 `policyKey` = SHA-256(`tenant_id` + `\u001f` + `policy_no`) 앞 16자 16진(필터·연결용, 비가역). 라인·한도·분급 응답 모두. 고객 이름은 계약의 `insuredMasked` 그대로(없으면 생략). 설계사 자기 이름은 그대로 | §9 개인정보 최소화. 응답 전체에 `policy_no` 원문이 없음을 **응답 스캔 시험**(E2E 모든 응답 본문 ∩ 시드 증권번호 = ∅)으로. |
| D-8 | **캐시·ETag**: `/api/v1/me/**` 응답은 `Cache-Control: private, no-cache`, `ETag: W/"<seq>"`(커서 `last_seq`; 라인 목록은 `<seq>:<page>:<size>:<필터>`), `If-None-Match` → 304. `no-store`는 쓰지 않는다(304가 의미 없어진다) | §10 집중 부하. 304에도 본문 없음·같은 헤더. |
| D-9 | **CLI 인가 전환**(Phase 1 ⑨): `--actor`는 폐기, CLI는 **OS 사용자**(`user.name`)를 subject `cli:<user>`로 `identity_link(role=OPERATOR, status=ACTIVE)`에서 찾는다 — 없으면 거부(감사 `AUTHZ_DENIED`). 테넌트 지정 명령은 그 테넌트의 `identity_link`. 시드·운영 등록은 SQL(`docs/운영.md`에 절차) | 운영 호스트 접근 통제 + 명시 등록. `OPERATOR` 토큰은 `/api`에 접근 못 한다(D-2). |
| D-10 | **V5** (`V5__query_api.sql`): `tenant.oidc_issuer`·`oidc_audience`, `identity_link.role`·`status`·`linked_at`, `policy_limit.limit_multiple` → `TEXT`(`USING limit_multiple::text`, 프로젝터는 계약 원문 저장 — Phase 2 Q1), `tenant.params` 키 `sync_delay_seconds` 문서화(기본값은 코드 상수가 아니라 §5 주석 + 설정 기본 300). V1~V4 불변 | — |
| D-11 | **인가 매트릭스 정본** `docs/authz-matrix.md`(역할 × 엔드포인트 × 결과) ↔ 코드 양방향 대조 시험(ga-disclosure `authz-matrix`와 같은 방식). 모든 컨트롤러 메서드는 `@UseCaseEntry` 표식 + `Caller`(테넌트·agentId·역할)만 받는다 — ArchUnit: 표식 없는 `@GetMapping` 0, 컨트롤러가 `JwtAuthenticationToken`·`Principal`을 직접 읽지 않는다 | 6A 규약 재사용. |
| D-12 | **`payoutExpectedOn`**: `tenant.payout_day_rule` `NEXT_MONTH_D{n}`만 지원(§14.1 #2 영업일 보정 미결정 — 보정 없음, 룰 형식이 다르면 생략·경고). 날짜 산술은 금액이 아니다 — 허용 | — |
| D-13 | **한도 카드 `utilization`·경고 단계**: `Ratio(accum_paid, limit_amount)`로 **표시 문자열만**(저장 없음), 단계는 `tenant.limit_warn_pct`·`limit_crit_pct`(Phase 0 Q2 방식으로 bp 비교 — 정수 비교) | 규약 1. |
| D-14 | **로그·지표에 식별자 평문 0**: `sub`·`agent_id`·`policy_no`·토큰은 로그에 해시(`agent_id`는 SHA-256 앞 12자)로만, 지표 태그는 `tenant`뿐(`agent` 태그 금지) | §10. 평문 스캔 센티널에 시드 subject·agent_id·증권번호 추가. |
| D-15 | **포털 API 계약 신설** `contracts/api/v1/portal-api.openapi.yaml`(포털 소유 — 웹 Phase 4가 클라이언트를 생성한다): 모든 응답을 계약 스키마로 검증(`format` 0, 금액 `integer`, 비율·배수 문자열). 401·404 Problem 포함 | — |
| D-16 | **실 엔진 E2E 스크립트** `e2e/engine-feed/run.sh`(정규 빌드 밖): 엔진 저장소 경로(`ENGINE_REPO`)의 `localstack/up.sh` → 토큰 파일 경로·`engine_base_url`로 테넌트 `GA01` 등록 → 포털 기동(인제스트 켬) → `ingest_lag_seq = 0` 대기 → 뷰 9표 = `golden-views/a1`(둘째 판) 대조 → `GET /api/v1/me/earnings/202608`(시험 발급기 토큰)가 계약 통과 → 결과를 `e2e/engine-feed/RESULT.md`에 날짜·해시와 함께. **사용자가 손으로 실행**, 수용 심사를 기다리게 하지 않는다 | Phase 1 수용 Q6. |

## 2. 작업 목록

1. **계획**: 인가 매트릭스 초안, 토큰 검증 순서(어느 검사가 어느 순서로 — 전부 401 한 바이트), `Caller` 해석 흐름(필터 → `iss`→테넌트 → `TenantContext` → `identity_link` → `Caller`), 응답 DTO ↔ 뷰 컬럼 표, ETag 규칙, V5, 응답 스캔 방식, 주입 목록. **승인 뒤 착수.**
2. **V5 + 도메인**(D-10, `Caller`·`Role`·`LinkStatus`).
3. **보안 체인**: 리소스 서버(발급자별 디코더), 테넌트 바인딩 필터, `IdentityResolver` 구현, 거부 장벽(같은 바이트), 감사(별도 tx), 시험 발급기.
4. **조회 서비스·컨트롤러**(D-5~D-8·D-12·D-13), `portal-query`(Spring 무의존 서비스 + `portal-app` 컨트롤러 또는 계획이 정한 배치 — 규칙 (a)·(e) 안에서), 계약 `portal-api.openapi.yaml`.
5. **인가 매트릭스·ArchUnit·스캔**(D-4·D-11·D-14).
6. **CLI 인가 전환**(D-9) + `docs/운영.md`.
7. **E2E 스크립트**(D-16) + `RESULT.md`(사용자 실행 뒤 — 보고서에는 "미실행"이면 그렇게).
8. **문서**: 설계서 v1.6(§5 V5, §7 응답 모양·`policyKey`·두 상태 필드(DD-12), §9 발급자 표·역할·CLI, §10 ETag, §14.1), `CLAUDE.md` 조회 규약(경로에 agentId 없음·identity_link만·같은 바이트·증권번호 원문 금지·로그 해시), DECISIONS, 보고서, 태그, PR.

## 3. 완료 기준

| # | 기준 | 증명 | 주입 |
|---|---|---|---|
| C1 | **IDOR**: 설계사 A 토큰으로 B의 `calcId`·`ledgerId`·월 조회 → 404, 없는 키와 **바이트 동일** | `AuthzIT`(두 설계사 시드) | M1 저장소 술어에서 `agent_id` 제거 → `TenantPredicateScanTest`·`AuthzIT` 실패 |
| C2 | 401 일곱 사유 같은 바이트·헤더 | `UniformRejectionIT` | M2 만료 토큰에 다른 메시지 → 실패 |
| C3 | 404 네 사유(링크 없음·REVOKED·MANAGER·남의 자원) 같은 바이트 | 〃 | M3 MANAGER 허용 → 매트릭스·IT 실패 |
| C4 | `iss` ↔ 테넌트, `tenant_id` 클레임 불일치 401, 남의 `agent_id` 클레임 → 자기 데이터 | `TenantBindingIT` | M4 클레임의 `agent_id` 사용 → 실패 |
| C5 | 인가 매트릭스 ↔ 코드 양방향, `@UseCaseEntry` 전수, 컨트롤러가 `Principal` 직접 안 읽음 | `AuthzMatrixTest`·ArchUnit | M5 표식 없는 엔드포인트 추가 → 실패 |
| C6 | 응답 전체에 `policy_no` 원문 0, `policyMasked`·`policyKey` 규칙 | `ResponseLeakScanIT`(모든 엔드포인트 × 시드) | M6 라인 상세에 `policy_no` 포함 → 실패 |
| C7 | ETag·304·`private, no-cache` | `CachingIT` | — |
| C8 | `dataStatus`·`monthDataStatus` 매핑(커서 상태 셋·지연·DRIFT) | 단위 + IT | M7 `REBUILDING`을 OK로 → 실패 |
| C9 | 거부 감사 별도 tx(본 요청 실패해도 남음), 로그·지표 식별자 평문 0 | `AuditIT`·`scanPlaintextLeaks` 센티널 | M8 로그에 `sub` 평문 → 스캔 실패 |
| C10 | CLI: 미등록 OS 사용자 거부·감사, OPERATOR 토큰 `/api` 404 | `IngestCliIT` 확장 | M9 CLI 등록 검사 제거 → 실패 |
| C11 | 모든 응답이 `portal-api.openapi.yaml` 통과, `format` 0 | 계약 시험 | — |
| C12 | `payoutExpectedOn`·`utilization`·경고 단계(정수 비교) | 단위(경계: 80.00% 정확히) | — |
| C13 | 집중 조회 성질: 홈 3콜(`/me`, `/earnings/{ym}`, `/limits?status=WARN`)이 각각 **쿼리 수 상한**(이벤트 수·라인 수와 무관) | `QueryStatementIT`(Phase 2 C11 방식) | M10 N+1 → 실패 |
| C14 | V5 뒤 `limit_multiple` 원문 보존("12" ↔ "12"), 기존 골든 무손상 | `ProjectionGoldenIT` | — |
| C15 | E2E 스크립트가 존재·문서화, 실행 결과(있으면) `RESULT.md` | 보고서 | — |
| C16 | Phase 0~2 무손상, 아키텍처 규칙, `TZ=UTC`, CI, 평문 스캔, 주입 ID 단위 | CI 아티팩트 | — |

## 4. 하지 말 것

화면(4). 시뮬레이션·시나리오 토글·레이트 리밋(5). 알림·이의제기(6). `/api/v1/org`(v2). 토큰 클레임으로 `agent_id`·역할 판정. 경로·쿼리에 `agentId`. 응답에 `policy_no` 원문·타인 데이터·사규 요율표. `no-store`. Keycloak 컨테이너(8). 포털에서 금액 계산(`utilization` 표시 문자열 외). V1~V4 수정.

## 5. 보고 형식

Phase 2와 같게. 추가: 인가 매트릭스 최종 표, 401·404 바이트(길이·해시), 응답 스캔 결과(엔드포인트 × 응답 수), 쿼리 수 표(홈 3콜), E2E 실행 여부·`RESULT.md`, 다음 Phase 질문(Phase 4 화면 — 상태 라벨 규약·"확정(세후 예상)"·정정 미지급 문구·`DEFER_AFTER_FY` 문구; Phase 5 — 레이트 리밋·`simulation_log`·엔진 v1.4 시뮬레이션 계약).
