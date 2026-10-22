# 포털 Phase 5 계획 — 분급 타임라인·시뮬레이션 (ga-agent-portal)

> 이 계획은 지시문 `docs/phase-05-지시문.md` v1.0(2026-10-22)에 대한 것이다. 브랜치는 `work/phase-5`(`main` = `86d6fe6`, Phase 4.1 병합)이다. **이 계획이 승인되기 전에는 계약·코드를 쓰지 않는다.**
>
> 읽은 것:
> - 지시문 전문, Phase 4 수용 심사 §4(Q1~Q6), 설계서 v1.7.1 §0·§2.3·§3.3·§3.4·§4.3·§5·§6.4·§6.7·§7~§10·§12~§14.
> - 계약 `engine-internal.openapi.yaml` 1.4.0·`portal-api.openapi.yaml` 1.0.0 전문.
> - 엔진(`ga-commission-engine` main `1179554`): `SimulationInternalController`, `SimulationContractTest`, `DryRunSimulator`(시책 선택·`generatedAt`).
> - 포털 코드:
>   - 수집·조회: `HttpFeedClient`·`ServiceTokenSource`·`FileServiceTokenSource`·`IngestPollingConfiguration`, `MeQueries`·`QueryAuthorization`·`Caller`, `LimitStage`, `AgentQueryRepository`·`SyncStatusRepository`·`TenantRepository`의 SQL.
>   - 시험·규칙: `TenantPredicateScanTest`, `PortalApiContractTest`, `ProblemResponses`, `ViewReset`·`SeedData`, `docs/authz-matrix.md`.
>   - 마이그레이션 V1~V5, E2E: `WebE2eHarness`·`web/e2e/streams`·`e2e/engine-feed/run.sh`.
>   - 웹: `routes.ts`·`EntryPoints`·`HomeScreen`·`LimitScreen`, `build.gradle.kts`의 `verifyUpstreamContract`.
> - 측정: JDK 25 Class-File API로 포털 main 바이트코드 전체의 곱셈·나눗셈 위치를 셌다(P-3).

## 0. 먼저 결정이 필요한 것 (요약)

지시문 §3 질문(Q1~Q10)에 더해, 계획이 고른 것(Q11~Q24)도 승인 대상이다.

| # | 질문 | 권장 | 대안 |
|---|---|---|---|
| Q1 | 엔진 오류 → 포털 상태 | **⑥ 표 그대로.**<br>• 엔진 404(`AGENT_NOT_FOUND`·`POLICY_NOT_FOUND`), 엔진 400, 응답 스키마 위반, 예상 밖 상태 → **502 `ENGINE_CONTRACT`**. 설계사 잘못이 아니고 계약·운영 문제다. 로그에는 `agent_id` 해시와 엔진 코드만 남긴다.<br>• 연결 실패·타임아웃·엔진 5xx·엔진 401/403(서비스 토큰) → **503 `ENGINE_UNAVAILABLE`**(저하 모드).<br>• 엔진 422는 닫힌 목록이면 그 코드, 밖이면 `ENGINE_REJECTED`(422).<br>• 엔진의 `message`는 옮기지 않는다. 포털 고정 바이트만 낸다 | 404 같은 바이트 — 운영 고장을 "없는 자원"으로 감춘다 |
| Q2 | HTTP 클라이언트 | **`portal-sim`에 별도 클라이언트**(`HttpEngineSimulationClient` — JDK `HttpClient`).<br>• 연결 2초·읽기 10초. 앱 설정 `portal.sim.connect-timeout`·`read-timeout`이고 시험이 줄일 수 있다.<br>• 토큰은 같은 파일을 쓴다. `portal-sim` 포트 `EngineCredentials`를 앱이 `FileServiceTokenSource`로 구현한다.<br>• 지금 그 빈은 인제스트가 켜졌을 때만 생긴다. 앱 공통 빈으로 옮긴다(P-9) | `HttpFeedClient` 공유 — 재시도·예외 분류가 피드용이고, `portal-sim`이 형제 모듈 `portal-ingest`에 의존하게 된다 |
| Q3 | 상품 입력 출처 | **ⓐ**:<br>• 한도 카드(CR-4 `productKey`·`productName`)의 상품 목록과 직접 입력을 함께 둔다.<br>• `insurerCd`는 422 `INSURER_AMBIGUOUS`를 받은 뒤에만 입력란을 연다.<br>• 엔진이 모르는 상품 키는 엔진이 답한다. 엔진 계약에는 그 경우의 코드가 없어 Q1에 따라 502가 된다(F로 기록) | ⓑ 직접 입력만 · ⓒ 엔진 상품 API(E3) |
| Q4 | 시책 코드 | **보내지 않는다.** 코드가 없으면 엔진이 전부 적용한다(`FilteredIncentives`). 결과의 `incentives[]`를 "포함된 시책"으로 보인다 | 토글(v2) |
| Q5 | `Retry-After` | **창 안 가장 오래된 행의 `created_at` + 60초 − 지금**을 올림한 정수 초(최소 1). 시각은 주입된 `Clock`에서 온다 | 고정 60 |
| Q6 | 한도 상세 → 해약 영향 | **메모리 인계**(`src/sim/handoff.ts`, 한 번 읽으면 비운다). 라우터 `state`는 쓰지 않는다. 브라우저가 `history.state`에 남겨 새로고침 뒤에도 살아난다(P-13) | 라우터 `state` |
| Q7 | `/me/limits` 페이지 없음 | **그대로**(v2에서 페이지). §10에 카드 수 상한 200(Phase 3 `QueryStatementIT`가 잰 크기)을 적는다 | 1.1.0에 페이지 |
| Q8 | RETENTION 범위 | **월 합계 `expected`만** 적용한다. `p = 100`은 바이트가 같을 수 없다(응답에 `scenario`·`p`·`expectedBase`가 더 있다 — P-14). 대신 "모든 달의 `expected` = `expectedBase` = `ALL_INFORCE`의 `expected`이고 `bar`가 같다"를 시험한다 | 항목에도 "적용 후" 열 |
| Q9 | E2E 시각·스텁 대조 | **시계 고정**(`page.clock`). 2027-02-15 09:30 KST가 엔진 샘플의 시계다.<br>스텁 대조는 **JCS 정규형 동일**(RFC 8785 — 플랫폼 `Canonicalizer`)로 한다. 원시 바이트로 대조하면 엔진 시험의 손글씨 JSON(필드 순서·공백)이 포털 직렬화와 묶인다. 정규형은 계약 수준의 동일이고 값 하나만 달라도 실패한다 | 원시 바이트 대조 |
| Q10 | 파라미터 자리 | 테넌트 `params`: `sim_rate_per_minute`(기본 10)·`sim_log_retention_years`(기본 3 — 문서용, 삭제 잡 없음). 앱 설정: 엔진 타임아웃 둘 | 전부 앱 설정 |
| Q11 | 접근 타입의 자리(P-8) | **`Caller`·`UseCaseEntry`·`DenyReason`·`NotFound`·`InvalidRequest`·인가 표를 `portal-domain` 패키지 `com.ga.portal.domain.access`로 옮긴다.**<br>• 클래스 이름은 그대로 두되, 인가 표만 `QueryAuthorization` → `ApiAuthorization`으로 바꾼다.<br>• 모두 Spring·DB 무의존 값이다.<br>• `portal-sim`의 진입점도 같은 표·같은 `@UseCaseEntry`를 쓰고, `ApiLayerRulesTest`·`AuthzMatrixTest`의 FQN을 갱신한다 | `portal-sim → portal-query` 의존 — 의존 방향 규칙(a) 위반 |
| Q12 | 보험료 입력(지시문 ⑥) | **포털 요청의 `monthlyPremium`·`newMonthlyPremium`은 숫자 문자열**이다(`pattern '^(0\|[1-9][0-9]{0,11})$'`). 서버가 정수로 읽어 엔진에 정수로 보낸다.<br>• 화면은 그 `pattern`을 계약에서 생성한 규칙 파일(`src/gen/portal-api.rules.json` — `client:lock`이 쓰고 해시를 고정)에서 읽어 미리 막는다. 규칙을 손으로 두 벌 적지 않는다(Phase 4 수용 §4 Q3).<br>• 금액 응답은 여전히 `Won` 정수다 | 정수 필드 — 화면이 문자열을 숫자로 바꿔야 한다(금지) |
| Q13 | `generatedAt`(P-2) | 엔진 `PolicyEventResult`에는 `generatedAt`이 없다.<br>• 포털 응답 둘 다에 포털 사실 **`requestedAt`**(`simulation_log.created_at`)을 둔다.<br>• `generatedAt`은 신계약에만, 엔진 값 그대로 둔다. 화면은 "요청 시각"과 (있으면) "엔진 산출 시각"을 보인다 | 포털이 해약 영향의 `generatedAt`을 만든다 — 엔진 사실처럼 보인다 |
| Q14 | 경과 회차(P-11) | 탭 ②에 **선택 입력 "경과 회차"**(정수 문자열, 비우면 보내지 않음 — 엔진이 개월 수로 근사)를 둔다. 6/7회차 경계는 이 값이 있어야 재현된다 | 입력 없음 — E2E가 경계를 못 보인다 |
| Q15 | 타임라인 범위·계약 필터(P-6·P-7) | 1.1.0에서 `/me/deferrals`를 이렇게 바꾼다:<br>• 범위 상한 **84개월**(다른 범위 API는 36 그대로).<br>• **`policyKey` 쿼리**를 더한다(그 설계사 집합 안에서 풀고, 모르는 키는 빈 결과 — `/lines`와 같은 규약). 서버가 그 계약만으로 월 합계·`bar`를 낸다 | 화면이 항목만 거른다 — 월 합계를 화면이 다시 낼 수 없어(산술 금지) 막대가 거짓이 된다 |
| Q16 | C3 수단(P-3·P-4) | 포털 금액은 `Won`이 아니라 `long`이라, 타입으로 "금액 곱셈"을 가려낼 수 없다. 그래서 **모든 곱셈·나눗셈을 막고 닫힌 허용 목록을 둔다**:<br>• **바이트코드 스캔**(JDK 25 Class-File API — 새 의존성 0): 포털 9모듈 main의 `LMUL`·`LDIV`·`LREM`·`IMUL`·`IDIV`·`IREM`과 `Math`·`StrictMath`의 `multiply*`·`*Div`·`*Mod` 호출.<br>• 허용 목록은 메서드 단위 셋, 항목마다 사유를 단다. 폐기된 항목이 남으면 실패한다.<br>　– `ForecastScenario#apply`: 금액 × p — 유일한 금액 산출.<br>　– `LimitStage#of`: bp 비교 — 금액을 만들지 않는다, Phase 3 D-13.<br>　– `AgentQueryRepository#lines`: page × size — 금액이 아니다.<br>• **SQL 스캔**: 모든 main SQL의 산술 `*`·`/`·`%`(`count(*)`·`select *` 제외). 허용 목록은 (파일, 문장) 둘 — 임계치 % → bp 변환이다.<br>• 보고서 "곱셈 위치 목록"은 위 셋이 된다. 금액 곱셈은 `ForecastScenario` 하나다 | ArchUnit만 — 연산자를 보지 못한다 |
| Q17 | 엔진 샘플(P-10) | **샘플 = 포털이 실제로 보낼 요청**(③ 표 S1~S12)이다.<br>• 엔진 상태별로 묶는다: 표준 엔진 / `INSURER_AMBIGUOUS` 엔진.<br>• 엔진 변경은 시험 코드뿐이다(`SimulationContractTest` 사례 추가 + `-Psamples.update=true`로 파일 쓰기, main 코드 0).<br>• 엔진 PR은 **자체 CI 통과 뒤 병합**한다(0단계 선승인으로 읽는다 — 확인 요청). 포털 사본의 `UPSTREAM`은 그 병합 커밋을 가리킨다 | 엔진 기존 요청 그대로 — 포털이 보내지 않는 `incentiveCodes`가 있어 E2E가 샘플에 닿지 않는다 |
| Q18 | 계약 1.1.0의 자리(P-26) | **`work/phase-5`의 첫 커밋**으로 한다(별도 PR 없음).<br>• 엔진은 `portal-api`를 읽지 않는다. Phase 3 v1.4처럼 상대 저장소 `UPSTREAM`이 병합 커밋을 가리킬 이유가 없다.<br>• `PortalApiContractTest`(경로 = 인가 표 키, 스키마 = 레코드)·`AuthzMatrixTest`·`clientCheck`가 계약을 코드에 묶어서, 계약만 바꾼 PR은 `main` CI에서 실패하거나 빈 코드를 끼워야 한다 | 지시문대로 별도 PR — 레코드·인가 키·생성 클라이언트를 함께 넣어야 한다(계약 PR이 아니게 된다) |
| Q19 | 거부 바이트(P-17) | 최종 **본문 10개**:<br>• 401·404·400·500(기존 넷).<br>• 422 셋: `INSURER_AMBIGUOUS`·`UNSUPPORTED_EVENT_TYPE`·`ENGINE_REJECTED`.<br>• 429 `RATE_LIMITED`, 502 `ENGINE_CONTRACT`, 503 `ENGINE_UNAVAILABLE`.<br>`Retry-After` 헤더 값만 요청마다 다르다. `UniformRejectionIT`는 그 헤더를 "있음·정수"로 비교한다 | 지시문의 "7행" |
| Q20 | `simId` 센티널(P-15) | `simId`는 화면에 보이도록 설계됐다(⑥). 그래서 **주소·콘솔·저장소·요청 주소에서만 0**을 본다. 시험이 응답에서 모은 동적 목록이다.<br>`POL-2026-0001`은 이미 `identifiers` 센티널이다 | DOM에서도 0 — 화면에 `simId`를 보일 수 없다 |
| Q21 | V6 내용(P-1) | `simulation_log`는 V1부터 있다(RLS V2, `SeedData`, 재구축이 지우지 않음). V6는 다음만 한다:<br>• 인덱스 `(tenant_id, agent_id, created_at)`.<br>• 열 `error_code`(닫힌 형식)·`engine_status`(HTTP 상태).<br>• `CHECK`: `kind IN ('NEW_CONTRACT','POLICY_EVENT')`, 그리고 상태별 열 조건 — `SUCCEEDED`는 `response`·`rule_snapshot_id` 필수, `FAILED`는 `error_code` 필수·`response` NULL, `REQUESTED`는 셋 다 NULL.<br>§5 문장("실패 사유는 값을 늘리지 않고 `error` 열을 새 V로")을 따른 것이다 | 표를 다시 만든다(커밋된 V 수정 금지와 충돌) |
| Q22 | CR-3·CR-4 출처(P-19·P-20) | • CR-3은 `ruleVersionId`만이다. 규정 기준일은 어떤 이벤트에도 없다.<br>• CR-4는 그 계약의 **첫 라인**(`earnings_line`, `source_seq` 최소, 같은 `agent_id`)의 `product_key`·`product_name`·`insured_masked`이다. SQL 조인에 `agent_id` 술어를 표마다 붙이고, 새 V는 없다.<br>• CR-1 `previous`는 월 문장 안의 `LEFT JOIN`이라 문장 수가 그대로다(§10) | 새 V로 원장에 상품 열 — 원장 이벤트에 상품이 없다 |
| Q23 | 타임라인 라벨(P-16) | **⑧ 라벨 표.** "참고(유지율 {p}% 가정)"는 시나리오가 **적용된 값**(`months[].expected`)에만 붙인다. `expectedBase`·`held`·항목은 원값이라 자기 라벨이다. 지시문의 "모든 금액"은 원값에 가정 라벨을 붙이게 된다 | 지시문 문자 그대로 |
| Q24 | "이번 달 실수령 변화"(P-12) | 엔진 값은 `offsetAgainstMonth`다. **환수가 귀속되는 달(`closeYm`)의 세전 + 환수 상계(채권 상계·세금 전)**이고, 그 달은 이번 달이 아닐 수 있다(샘플은 202702). 화면 문구는 "{closeYm} 세전·환수 상계 합계 — 변경 전 / 변경 후"로 한다. "실수령"이라 쓰지 않는다 | "이번 달 실수령" — 엔진 값의 뜻과 다르다 |

## ① 확인한 사실 (지시문·설계서와 다른 곳)

| # | 지시문(또는 설계서) 문구 | 사실 | 계획 |
|---|---|---|---|
| P-1 | ② "`simulation_log`(V6): §5 DDL 그대로 + RLS(V2 규약)" | V1이 표를 만들었다(`ck_simulation_log_status` 포함). V2 RLS 목록에도 있고, `SeedData`·`RlsIsolationIT`·재구축(`ViewReset` — 지우지 않음)이 이미 다룬다 | Q21 — V6는 인덱스·실패 열·`CHECK`만 |
| P-2 | ⓪ "응답 … + `generatedAt`·`ruleSnapshotId`"(둘 다) | 엔진 계약 1.4.0 `PolicyEventResult` 필수 목록과 `SimulationInternalController.policyEventBody`에 `generatedAt`이 없다 | Q13 |
| P-3 | ④·C3 "곱셈은 `ForecastScenario` 한 클래스에만 … 스캐너가 본 곱셈 위치 목록 = 그 클래스 하나" | 바이트코드 측정(포털 9모듈 main): `LimitStage#of` `Math.multiplyExact` 3(Phase 3 D-13 — bp 비교), `AgentQueryRepository#lines` `LMUL` 1(page × size). SQL: `SyncStatusRepository`·`TenantRepository`의 `round(limit_*_pct * 100)`(% → bp). 금액을 만드는 곱셈은 없다 | Q16 — 허용 목록 셋·SQL 둘, 금액 곱셈은 `ForecastScenario` 하나 |
| P-4 | C3 "`Won`·금액 열에 대한 곱셈" | 포털의 금액은 뷰 레코드에서 `long`이다(`MonthRow.gross` 등). 플랫폼 `Won`(곱셈 없음)은 거의 쓰지 않는다. 타입으로 금액을 가려낼 수 없다 | Q16 — 연산 전부를 닫힌 목록으로 |
| P-5 | C9 "`bar` 비율은 SQL 정수 연산 문자열" / ⓪ "`utilization`과 같은 규약" | `utilization`은 SQL이 아니라 자바에서 플랫폼 `Ratio.of(누적, 한도).toPercentString()`(플랫폼 내부 정수 연산)이다. 두 문장이 서로 다르다 | `utilization`과 같게 — 합(`expected + held`)·최댓값은 SQL, 비율은 `Ratio`. 포털 바이트코드에 나눗셈이 생기지 않는다 |
| P-6 | ⑤ "기간 토글 12/24/48/84개월" | `/me/deferrals`의 범위 상한은 36개월이다(`MeQueries.MAX_RANGE_MONTHS` — 48·84는 400) | Q15 — 그 경로만 84 |
| P-7 | ⑤ "계약 필터(`policyKey` 문자열 비교)" | 응답의 `months[]`는 설계사 전체 합이다. 화면이 항목을 걸러도 월 합계·막대를 다시 낼 수 없다(산술 금지) | Q15 — 서버 필터 |
| P-8 | ① "`app → sim → projection → domain`" + 진입점 규약(`@UseCaseEntry`·`Caller`·`QueryAuthorization.require`) | 셋 다 `portal-query`에 있다. `portal-sim`은 형제 모듈을 의존할 수 없다(규칙 (a)) | Q11 — `portal-domain`의 `access` 패키지로 이동 |
| P-9 | ① "같은 서비스 토큰 파일" | `ServiceTokenSource`는 `portal-ingest`에 있고, `FileServiceTokenSource` 빈은 `portal.ingest.enabled=true`일 때만 생긴다. 인제스트 없이 시뮬레이션만 켜면 토큰이 없다 | Q2 — 토큰 파일 빈을 앱 공통으로, 두 포트에 같은 구현 |
| P-10 | ⓪ "`SimulationContractTest`가 쓰는 요청·응답 쌍" | 엔진의 한도 초과 요청에는 `insurerCd`·`incentiveCodes`가 있다(포털은 Q3·Q4로 둘 다 보내지 않는다). `INSURER_AMBIGUOUS` 사례는 **보험사가 둘인 다른 엔진 상태**에서 표준 사례와 같은 요청 바이트로 만들어졌다 — 한 스텁에서는 둘이 구별되지 않는다 | Q17 — 포털 요청 기준의 샘플, 엔진 상태별 묶음을 테넌트에 붙인다 |
| P-11 | ⑥ 탭 ② 입력 "계약·유형·시점·(REDUCE) 새 월납" / ⑦ "해약 6/7회차" | 엔진의 경계 사례는 `elapsedInstallments` 6·7로 만든다. 없으면 엔진이 체결일 ~ 기준일의 개월 수로 근사한다 | Q14 |
| P-12 | ⑥ "이번 달 실수령 변화 전/후" | `offsetAgainstMonth` = 환수가 귀속되는 달의 `gross + clawbackOffset`(채권 상계·원천세 전) 전후다(엔진 계약 1.3 R5). 샘플의 `closeYm`은 202702다 | Q24 |
| P-13 | 질문 6 "라우터 `state`(메모리) — 새로고침하면 선택이 사라지고" | `BrowserRouter`의 `state`는 `history.state`에 저장되어 같은 탭의 새로고침 뒤에도 남는다 | Q6 — 모듈 메모리 |
| P-14 | 질문 8 "`p`가 100이면 `ALL_INFORCE`와 같은 바이트" | RETENTION 응답에는 `scenario`·`p`·`expectedBase`가 있다 | Q8 — 값 동일 시험 |
| P-15 | ⑦ "센티널에 … `simId` 추가 — 화면 … 0" / ⑥ "결과에 … `simId`" | 둘이 충돌한다. 게다가 `simId`는 요청마다 새 UUID라 정적 센티널 목록에 둘 수 없다 | Q20 |
| P-16 | ⑤ "RETENTION이면 모든 금액 라벨 '참고(유지율 {p}% 가정)'" | `held`·`items[].amount`·`expectedBase`는 원값이다(같은 문단 ④) | Q23 |
| P-17 | §5 "거부 바이트 표(최종 7행)" / C8 "(401·404·400·422·429·502·503)" | 기존 표에 500이 있다. 422는 코드 셋이라 본문도 셋이다 | Q19 — 본문 10개 |
| P-18 | ⑦ "422 둘" / ⑥ "`PAYMENT_OVERDUE`는 선택지에서 뺀다" | 화면에서는 `UNSUPPORTED_EVENT_TYPE`을 낼 수 없다 | 포털 요청의 `eventType`은 엔진과 같은 여섯 값(계약 사본), 화면 선택지는 다섯. E2E의 둘째 422는 Playwright 안 API 요청(같은 토큰·같은 누출 감시) — 화면 422 문구는 Vitest |
| P-19 | ⓪ CR-4 `Ledger.productName`·`productKey`·`insuredMasked` | `LimitLedgerOpened`·`policy_limit`에는 상품·피보험자가 없다. 라인(`CommissionCalculated`)에 있다 | Q22 |
| P-20 | ⓪ CR-3 "`Ledger.ruleVersionId`·규정 기준일" | `policy_limit.rule_version_id`는 있다. 규정 기준일은 어떤 이벤트에도 없다 | Q22 — `ruleVersionId`만 |
| P-21 | ① "엔진 응답은 사본 스키마로 검증" | 엔진 400은 포털이 요청 규칙을 먼저 지키면 생길 수 없다. 예: `REDUCE`인데 `newMonthlyPremium`이 없으면 엔진 400(샘플 S12). 포털이 같은 규칙으로 먼저 400을 낸다 | 엔진 400 → 502 `ENGINE_CONTRACT`(Q1) |
| P-22 | ⓪ 엔진 샘플 `contracts/samples/simulations/` | 엔진 저장소의 계약 디렉터리는 git 루트 밑 `commission-system/contracts/`다. 포털 `verifyUpstreamContract`는 `contracts/<path>`만 가정한다 | `UPSTREAM` 첫 줄에 경로 접두(`owner/repo@commit:commission-system/contracts`) — 검사기가 둘 다 읽게 고친다 |
| P-23 | §10 "홈 세 콜" | 홈은 이제 `/me/deferrals?from=다음달&to=다음달`도 부른다(넷) | §10 문장 수 표에 deferrals를 더한다(`QueryStatementIT`) |
| P-24 | `AuthzMatrixTest` "`@GetMapping` 템플릿" | 첫 `@PostMapping`이 생긴다(`ApiLayerRulesTest.MAPPINGS`는 이미 Post를 안다) | 대조 대상을 Get·Post로, 문서 행은 `METHOD path` 그대로 |
| P-25 | C2 "금액 필드 집합이 바이트 같다" | 엔진 스키마는 `additionalProperties`를 막지 않고(호환 추가 허용 — 설계서 §4.5), 포털 계약은 막는다 | 포털은 **사본 스키마의 속성만** 옮긴다. 모르는 엔진 필드는 버리고 개수만 로그에 남긴다. C2 시험은 "알려진 속성 전부가 JSON 값 그대로 + 정수 리터럴 바이트 동일" |
| P-26 | §0 "계약 1.1.0은 … 0단계 별도 PR(자체 CI 통과 후 병합)" | `PortalApiContractTest`가 "경로 = 인가 표의 키"·"스키마 = 응답 레코드"를 양방향으로 보고, `clientCheck`는 생성 클라이언트 해시를 본다. 계약만 바꾼 PR은 `main` CI가 실패한다. 엔진은 `portal-api`를 읽지 않는다 | Q18 |

## ② 계약 `portal-api` 1.1.0 (Q18 — `work/phase-5` 첫 커밋)

추가는 전부 선택 필드·새 경로·열거 확장이다. 기존 경로의 필수 목록·기존 속성 정의는 바꾸지 않는다.

**새 경로 둘**(둘 다 `AGENT`만 — 인가 표):
- `POST /api/v1/me/simulations/new-contract`
  - 요청 `{productKey, monthlyPremium: 숫자 문자열, contractDate, paymentTermMonths?, insurerCd?}`. 엔진 사본의 `incentiveCodes`는 받지 않는다(Q4).
- `POST /api/v1/me/simulations/policy-event`
  - 요청 `{policyKey, eventType: 엔진 여섯 값, asOfDate, elapsedInstallments?, newMonthlyPremium?: 숫자 문자열}`.
  - `REDUCE`면 `newMonthlyPremium`이 필수다(스키마 `if/then` — 서버도 같은 규칙).
- 공통:
  - 본문은 `application/json` ≤ 2 KiB. 다른 `Content-Type`·초과·형식 오류는 400 한 바이트다.
  - 요청 객체도 `additionalProperties: false`다.
- 응답 200:
  - `{asOf, dataStatus, simId, requestedAt}` + 엔진 결과 사본의 속성(신계약은 `generatedAt` 포함)이다.
  - 금액은 `Won`이고, `Cache-Control: no-store`이며 ETag는 없다(요청마다 새 결과).
  - 사본 스키마의 필수 목록은 엔진 1.4.0과 같다.
- 오류: 400·401·404·422(세 코드)·429(+`Retry-After`)·502·503 — 전부 고정 바이트(Q19). `Problem.code` 열거에 여섯 코드를 더한다.

**기존 경로**:
- `GET /me` 응답: `features {forecastScenario}`(선택).
- `GET /me/deferrals`:
  - 쿼리 `scenario`(`ALL_INFORCE`·`RETENTION`, 기본 `ALL_INFORCE`)·`p`(1~100, RETENTION일 때 필수 — 아니면 400)·`policyKey`(선택, Q15). 범위 상한은 84다.
  - 응답 `scenario`·`p?`, `months[].expectedBase?`(RETENTION만)·`bar? {expected, held}`(`Percent`).
  - 테넌트가 비활성인데 RETENTION이면 400이다.
- CR-1 `MonthResponse.previous? {closeYm, status, gross, net?, netBasis?}` — 지난달 행이 없으면 생략한다.
- CR-2 `Line.reversalOfYm?`·`reversedByYm?`.
- CR-3 `Ledger.ruleVersionId?`.
- CR-4 `Ledger.productKey?`·`productName?`·`insuredMasked?`.

**시험**:
- `PortalApiCompatibilityTest`(새): 1.0.0 사본(`portal-query` 시험 자원, `main` `86d6fe6`의 바이트 — SHA-256 `0d14a182…`, `contracts/CHECKSUMS`의 그 줄과 대조)과 1.1.0을 구조로 대조한다.
  - 1.0.0의 모든 스키마·경로가 1.1.0에 있다.
  - 필수 목록이 같다.
  - 기존 속성의 정의가 같다(열거는 늘기만).
  - 새 속성은 선택이다.
- Phase 3의 모든 응답 검증 시험은 그대로 1.1.0으로 본다. 그것이 "1.0.0 응답이 1.1.0을 통과"의 실측이다.
- 그 밖: `PortalApiContractTest`(새 레코드 결합·경로 열 = 인가 키), `EventSchemaTest`의 `format` 0·YAML 1.1 안전(새 키에 `no`·`on`·`yes` 없음), `CHECKSUMS`, `pnpm client:lock`(생성물·잠금·규칙 파일 — Q12).

## ③ 엔진 응답 샘플 (0단계 — 엔진 저장소, Q17)

엔진 PR `work/sim-samples`(엔진 `main` 기준):
- `SimulationContractTest`에 아래 사례를 더하고, `-Psamples.update=true`이면 요청·응답을 파일로 쓴다(응답은 MockMvc 바이트 그대로).
- 기본 실행은 파일과 바이트를 대조한다. 엔진이 바뀌면 그 시험이 먼저 실패한다.
- main 코드 변경은 0이다.
- 경로는 `commission-system/contracts/samples/simulations/`이다.
  - `index.json`: 사례마다 `{name, engineState, path, status}`.
  - `<name>.request.json`(포털이 보낼 요청, `agentId`·`policyNo` 포함 — 엔진 요청).
  - `<name>.response.json`(응답 바이트).

| 이름 | 엔진 상태 | 요청(요지) | 기대 |
|---|---|---|---|
| S1 `new-limit-exceeded` | 표준 | WHOLE-LIFE-20Y, 150,000, 2026-08-01(보험사·시책 코드 없음) | 200 — 한도 1,800,000, 시책 Q4-NEW 855,000·삭감 145,000(엔진 기존 단언과 같은 값이어야 한다 — 다르면 엔진 PR에서 보고) |
| S2 `new-curve` | 표준 | 같은 상품, 2027-01-15 | 200 — `schedule` 있음 |
| S3 `new-no-limit` | 표준 | 2026-06-30 | 200 — `limitAmount`·`projectedAccum` 없음 |
| S4 `new-term-3` | 표준 | 2026-08-01, `paymentTermMonths` 3 | 200 — 회차 3, `schedule` 없음 |
| S5 `event-cancel-7` | 표준 | POL-2026-0001 CANCEL 2027-02-15 경과 7 | 200 — 환수 −720,000 |
| S6 `event-cancel-6` | 표준 | 같은 계약, 경과 6 | 200 — 환수 −1,260,000 |
| S7 `event-reduce` | 표준 | REDUCE, 새 월납 100,000, 경과 7 | 200 |
| S8 `event-unsupported` | 표준 | PAYMENT_OVERDUE | 422 `UNSUPPORTED_EVENT_TYPE` |
| S9 `event-unknown-policy` | 표준 | POL-9999 | 404 `POLICY_NOT_FOUND` |
| S10 `new-unknown-agent` | 표준 | 설계사 A-9999 | 404 `AGENT_NOT_FOUND` |
| S11 `new-ambiguous` / S11b `new-ambiguous-samlife` | 보험사 둘 | S1 요청 / S1 + `insurerCd` SAMLIFE | 422 `INSURER_AMBIGUOUS` / 200 |
| S12 `event-reduce-no-premium` | 표준 | REDUCE, 새 월납 없음 | 400 `INVALID_REQUEST`(포털은 보내지 않는다 — 502 매핑 시험용) |

**포털 쪽**:
- `contracts/shared/ga-commission-engine/samples/simulations/`에 사본을 두고 `CHECKSUMS`·`UPSTREAM`(엔진 병합 커밋, 경로 접두 — P-22)을 단다.
- `verifyUpstreamContract`는 두 업스트림을 같은 규칙으로 본다: 목록 = 사본 집합, 바이트 동일.
- 포털 시험·스텁·E2E의 엔진 데이터는 이 사본뿐이다.

## ④ 모듈·배선

- **`portal-domain` `access`**(Q11): `Caller`, `UseCaseEntry`, `DenyReason`, `NotFound`, `InvalidRequest`, `ApiAuthorization`(GET 여덟 + POST 둘).
  - `portal-query`는 이것을 쓴다. 클래스를 옮기는 것뿐이고 동작은 그대로다.
  - `ApiLayerRulesTest`·`AuthzMatrixTest`의 FQN 목록을 갱신한다.
- **`portal-sim`**(Spring·DB 무의존 — `verifySpringFree`와 ArchUnit 목록에 더한다):
  - `SimulationUseCases`: `@UseCaseEntry` 둘.
  - `RateLimitRule`: 판정·`Retry-After` — 순수 함수.
  - `EngineSimulationClient`(포트) + `HttpEngineSimulationClient`: JDK `HttpClient`, 재시도 0.
  - `EngineResponses`: 엔진 사본 스키마 검증 — `json-schema-validator`(이미 카탈로그에 있다), 계약은 클래스패스 사본에서만.
  - `SimResponseMapper`: 사본 속성만 옮긴다 — P-25.
  - 포트 `SimulationLogStore`·`PolicyResolver`(그 설계사의 원장 증권번호 집합)·`EngineCredentials`·`SimTransactions`.
- **`portal-infra`**:
  - `SimulationLogRepository`: 잠금·창 개수·삽입·상태 갱신 — 모든 문장 `tenant_id = :tenantId AND agent_id = :agentId`.
  - `AgentQueryRepository`: CR-1~4 조인, `dueMonths`의 RETENTION 입력(원값만 — 곱셈은 자바 `ForecastScenario`), `policyKey` 필터용 집합.
- **`portal-query`**: `ForecastScenario`(`apply(long expected, int p)` = `Math.floorDiv(Math.multiplyExact(expected, p), 100)` — 금액 곱셈의 유일한 자리), `MeQueries.deferrals` 확장, `me.features`.
- **`portal-app`**:
  - `SimulationController`: `@PostMapping` 둘 — 얇게, `Caller` → 진입점 하나.
  - `ProblemResponses`: 여섯 바이트를 더한다.
  - `SimulationConfiguration`: 타임아웃·클라이언트.
  - `EngineTokenConfiguration`: 토큰 파일 빈을 앱 공통으로(P-9).
  - 지표 `api_rate_limited_total{tenant}`·`sim_requests_total{tenant, reason}`(`reason` = `OK`·`ENGINE_UNAVAILABLE`·`ENGINE_CONTRACT`·엔진 422 코드 — 닫힌 값).
- **CSRF**: POST는 `Authorization: Bearer`로만 인증하고 쿠키가 없다(Phase 4 — 화면은 메모리 토큰). 같은 출처이고 CORS가 없다. 그래서 브라우저가 남의 출처에서 자격 증명을 실어 보낼 경로가 없다. Phase 6 질문에 근거로 다시 적는다.

## ⑤ `simulation_log`·레이트 리밋 (C5·C6)

**트랜잭션 A**(짧다):
1. `pg_advisory_xact_lock(hashtext('portal.sim'), hashtext(tenant ∥ U+001F ∥ agent))` — 인제스트의 `hashtext('portal.ingest')` 키 공간과 겹치지 않는다. 잡을 때까지 기다린다.
2. `SELECT count(*), min(created_at) … created_at > :now − 60s`(세 상태 전부).
3. ≥ N이면 롤백한다 → 429 + `Retry-After`(Q5) + 지표 1. 행·감사가 없다.
4. 아니면 `REQUESTED` 행(`sim_id` = UUID, `request` = 엔진 요청 JSON, `created_at` = `Clock`)을 넣고 커밋한다.

**호출**: 트랜잭션 밖이고 재시도는 0이다.

**트랜잭션 B**:
- `SUCCEEDED`: `response` = 검증된 엔진 응답(JSONB)과 `rule_snapshot_id`.
- `FAILED`: `error_code` = 포털 사유 또는 엔진 코드, `engine_status` = HTTP 상태(없으면 NULL). 본문은 남기지 않는다.
- B가 실패하면 행은 `REQUESTED`로 남는다(500). 지표만 남긴다.

**재구축**: `ViewReset`이 이미 지우지 않는다. `RebuildIT`에 "재구축 전후 `simulation_log` 행 바이트 동일"을 더한다.

**보존**: 삭제 잡은 없다. `docs/운영.md`에 보존 SQL 한 줄을 적는다(테넌트 바인딩 뒤 `DELETE … WHERE created_at < now() − make_interval(years => :n)` — 운영자가 손으로 실행). 이 문장은 저장소 코드가 아니라 문서다.

## ⑥ 엔진 호출·응답 매핑 (Q1, C7)

| 엔진 결과 | 포털 응답 | `simulation_log` | 지표 `reason` |
|---|---|---|---|
| 200 + 사본 스키마 통과 | 200 = 포털 필드 + 사본 속성 | `SUCCEEDED` | `OK` |
| 200 + 스키마 위반 / JSON 아님 | 502 `ENGINE_CONTRACT` | `FAILED` `ENGINE_CONTRACT`, 200 | `ENGINE_CONTRACT` |
| 400 / 404(`AGENT_NOT_FOUND`·`POLICY_NOT_FOUND`) / 그 밖 4xx | 502 `ENGINE_CONTRACT` | `FAILED` 엔진 코드(형식이 맞으면), 상태 | `ENGINE_CONTRACT` |
| 422 `INSURER_AMBIGUOUS`·`UNSUPPORTED_EVENT_TYPE` | 422 그 코드(포털 고정 바이트) | `FAILED` 그 코드, 422 | 그 코드 |
| 422 그 밖 | 422 `ENGINE_REJECTED` | `FAILED` 그 코드, 422 | `ENGINE_REJECTED` |
| 401·403·5xx·연결 실패·타임아웃 | 503 `ENGINE_UNAVAILABLE` | `FAILED` `ENGINE_UNAVAILABLE`(또는 `ENGINE_TIMEOUT`), 상태 | `ENGINE_UNAVAILABLE` |
| 토큰 파일 없음 | 503 | `FAILED` `TOKEN_MISSING`(호출 없음) | `ENGINE_UNAVAILABLE` |

**포털 쪽 판정**(엔진에 가지 않는 것):
- 요청 형식 → 400. `REQUESTED` 이전이라 행이 없다.
- 남의·없는 `policyKey` → 404(감사 `AUTHZ_DENIED` `MISSING` — 기존 규약). 행이 없고 호출 0이다(C4).
- 리밋 → 429.

## ⑦ `/me/deferrals` 확장·곱셈 격리 (C3·C9)

- **SQL**:
  - 월별 `expected`·`held` 합(기존 문장).
  - `max(expected + held) over ()`를 같은 문장에 더한다(문장 수 그대로).
  - `policyKey`가 있으면 `policy_no = :policyNo` 술어 — 키는 그 설계사의 `deferral_item` 증권번호 집합에서 자바가 푼다.
- **자바**:
  - 막대: `bar = Ratio.of(값, 최댓값).toPercentString()`. 최댓값 ≤ 0이면 생략한다. 값이 음수면 음수 문자열 그대로 두고, CSS는 음수 너비를 무시한다(시험).
  - RETENTION이면 `expected = ForecastScenario.apply(expectedBase, p)`이고, 막대는 적용값으로 낸다.
- **비활성 테넌트 + RETENTION** → 400. `p`가 없거나 범위 밖이면 400이다.
- **골든**: A-1(분급 없음 — 빈 `months`)과 E2E GA04 스트림(⑨) — 84개월·HELD·RETENTION 30%·`p = 100`.
- **스캐너 둘**(Q16): `MultiplicationScanTest`(archTest — 바이트코드)와 `TenantPredicateScanTest` 옆의 `SqlArithmeticScanTest`. 주입은 S-n(⑩).

## ⑧ 화면

**경로**: `/deferrals`·`/simulations`(지연 청크)를 `routes.ts`와 `SCREEN_PATHS`에 함께 더한다. 진입점이 살아나는 곳:
- 홈: 타임라인·시뮬레이션.
- 한도 상세: "해약 시 영향" → 메모리 인계(Q6).

라인 상세 "문의"는 비활성 그대로다.

**타임라인**:
- 기간 12/24/48/84(`src/time/` `ymShift`)와 계약 필터(한도 카드의 `policyKey` → 쿼리).
- 막대: 월마다 `<div role="img" aria-label="{월} 예정 {금액} · 보류 {금액}">`. 너비는 CSSOM 사용자 속성 `--bar-expected`·`--bar-held`이고 값은 문자열 그대로다.
- 그 아래 표(월·예정·보류 — 접근성 본문).
- 시나리오 토글은 `features.forecastScenario`일 때만 나온다. p는 정수 문자열 입력이고 계약 `pattern`으로 막는다.
- 고정 문구 "분급 확정 스케줄만 반영 · 참고치 · 비확정". 항목 목록은 상태 라벨이다.

**시뮬레이션**:
- 탭 둘이다(`role="tablist"` — 키보드).
- 입력은 문자열이다. 날짜 기본값은 KST 오늘(`src/time/`), 규칙 파일로 미리 막는다(Q12).
- 결과에는 모든 금액에 `simulationLabel()`, 고정 문구 "설계사 참고용 · 비확정 · 고객 제시 불가", `ruleSnapshotId`·`simId`·요청 시각·(신계약) 엔진 산출 시각.
- 신계약 결과:
  - 회차 표.
  - 한도액과 예상 누적을 나란히(소진율은 계산하지 않는다 — 엔진이 비율을 주지 않는다).
  - 시책(코드·지급 예정·삭감).
  - 첫해 지급분, 이연 스케줄.
- 해약 영향 결과:
  - 환수.
  - {closeYm} 변경 전/후(Q24 — 두 값 나란히, 차이 없음).
  - 취소되는 분급(표), 채권 잔액.
- 오류 처리(마지막 결과는 TanStack Query 변이 결과로 메모리에만 둔다):
  - 429: 마지막 결과 유지 + "잠시 후 다시 시도해 주세요({n}초)" — `n`은 `Retry-After` 문자열 그대로.
  - 503: "시뮬레이션을 지금 할 수 없습니다 — 조회는 가능" + 마지막 결과 유지.
  - 422 `INSURER_AMBIGUOUS`: 보험사 입력란을 연다.
  - 그 밖의 422: 코드별 문구.
  - 502: "일시적 오류 — 운영팀 확인 중".

**홈**:
- "다음 달 분급 예정액"(`/me/deferrals` 다음 달 한 칸 — `expected`와 `forecastLabel`, 0이면 "다음 달 예정 없음").
- "전월 대비"(CR-1 `previous` — 지난달 값과 그 달 라벨을 나란히, 차감 없음).

**CR-2·CR-4 반영**: 명세의 다른 달 짝 배지는 `reversalOfYm`·`reversedByYm`(추가 호출 제거)으로 한다. 한도 카드에 상품명(없으면 `productKey`)·피보험자.

**라벨 표**(초안 — 문구는 `messages.ko.json`, 최종판은 보고서):

| 금액 | 라벨 |
|---|---|
| 타임라인 `months[].expected`(ALL_INFORCE)·홈 다음 달 분급 | `forecastLabel()` "분급 예정" |
| 타임라인 `months[].expected`(RETENTION) | `retentionLabel(p)` "참고(유지율 {p}% 가정)" |
| `months[].expectedBase` | `forecastLabel()` — 나란히 |
| `months[].held` | `deferralLabel('HELD')` "보류" + 범례 "지급 조건 미충족으로 멈춘 분급 — 예정 합계에 넣지 않음"("다시 받을 수 있는" 류 문구 0 — 메시지 시험이 금지어를 본다) |
| 항목 `amount` | `deferralLabel(status)`(Phase 4) |
| 시뮬레이션 금액 전부 | `simulationLabel()` "시뮬레이션"(네 월 상태·정정·원장·분급 라벨 어느 것과도 다르다 — 시험) |
| 홈 전월 대비 | 지난달 `monthLabel(previous.status)`·세후는 `netView(previous…)`(16칸 표 그대로) |

**번들**: 새 의존성은 0이다. 두 경로는 지연 청크이고, 초기 로드 상한 127 KiB는 그대로다.

## ⑨ E2E·엔진 스텁 (C12, Q9·Q17·Q20)

**스텁**:
- `StubFeedServer`에 경로 처리기를 더하는 자리(`route(path, handler)`)를 둔다. 같은 서버·같은 `engine_base_url`·같은 서비스 토큰 검사를 쓴다.
- 처리기 `StubSimulationEngine`(`portal-sim` 시험 픽스처)는 그 테넌트에 붙인 엔진 상태의 샘플 묶음에서 요청의 JCS 정규형이 같은 것을 찾아 응답 바이트를 그대로 낸다. 없으면 500이다.
- 호출 횟수를 센다(C4·C7 단언).

**제어**: `POST /sim/<tenant>/down|up|slow|fast|broken`.
- `slow`는 읽기 타임아웃 + 1초 지연, `broken`은 스키마 위반 200이다.
- 지시문의 `/control/{tenant}/…`는 Phase 4 제어 포트의 `/head`·`/fail` 형식에 맞춘다.

**테넌트**:
- GA01(A-1, POL-2026-0001 원장): 표준 엔진 묶음, `sim_rate_per_minute` 100(같은 분 안의 다른 시험이 리밋에 닿지 않게).
- GA03(E1 cycle — 같은 설계사·POL-2026-0001): 표준 엔진 묶음, `sim_rate_per_minute` 2 — 429 시나리오 전용.
- GA02: 보험사 둘 묶음(S11·S11b).
- GA04: 분급 사건을 `edges.jsonl` 뒤에 더한다.
  - `DeferralScheduled` 여섯: 2027-03 ~ 2033-12에 걸쳐 84개월 경계 안팎.
  - `DeferralStatusChanged` → HELD 하나·CANCELLED 하나.
  - 허구 값, 계약 검증 통과.
  - `forecast_scenario_enabled` true.
- 홈 "전월 대비": A-1은 202608과 202702뿐이라 "이번 달"(202702)의 지난달 행이 없다(→ `previous` 생략 — 그것도 시나리오). 연속한 두 달은 GA04에 한 달을 더해 보인다.

**시나리오**(세 프로젝트):
1. 신계약 S1·S2·S3.
2. 해약 S5·S6(한도 상세 → 해약 영향 인계 포함).
3. 422 S11(화면) → 보험사 입력 → S11b, 그리고 S8(API 요청 — P-18).
4. 429(GA03 연타 3회 — 셋째가 429, 마지막 결과 유지).
5. 503(`down`, 마지막 결과 유지·조회 화면 정상).
6. 타임라인 12/84·HELD·RETENTION 30%.
7. 홈 다음 달 분급·전월 대비.

시계는 2027-02-15 09:30 KST로 고정한다(Q9). axe·44px·움직임·키보드(데스크톱 탭 이동)·CSP·누출 검사는 Phase 4 고정 장치 그대로다.

**센티널**:
- 정적: 샘플 증권번호 `POL-2026-0001`(이미 있음)·`POL-9999`와 GA04 분급 증권번호를 `e2e` 키에 더한다.
- 동적: 응답에서 모은 `simId` — 주소·콘솔·저장소·요청 주소만(Q20).
- `simulation_log.request`의 원문은 DB에만 있다. 응답 스캔(`ResponseLeakScanIT`)에 시뮬레이션 두 경로를 더한다.

**실 엔진 E2E**: `run.sh` ⑧ 뒤에 시뮬레이션 한 쌍(S1·S5 요청 — 실 엔진 응답이 사본 스키마를 통과하고 `simulation_log` 두 행)을 더한다. 실행은 사용자 몫이고, 실행하지 않으면 보고서에 미실행으로 적는다.

## ⑩ 완료 기준 → 시험·주입

| C | 시험 | 주입(ID 하나씩) |
|---|---|---|
| C1 | `PortalApiCompatibilityTest`, `PortalApiContractTest`, `verifyContractChecksums`, `verifyUpstreamContract`(엔진 사본), `EventSchemaTest` `format` 0 | S1 1.1.0에서 기존 필수 하나 제거 · S2 엔진 사본 1바이트 변경 |
| C2 | `SimulationPassThroughIT`(샘플 S1~S7·S11b마다 포털 응답 − 포털 필드 = 엔진 응답의 알려진 속성, 정수 리터럴 바이트 동일) | S3 매퍼가 `clawbackAmount`를 `negate` |
| C3 | `MultiplicationScanTest`(허용 셋, 폐기 항목 실패), `SqlArithmeticScanTest`(허용 둘) | S4 `MeQueries`에 `expected * p / 100` · S5 `AgentQueryRepository` SQL에 `* :p` |
| C4 | `SimulationAuthzIT`(남의·없는 키 404 같은 바이트 + 스텁 호출 0 + 행 0, `MANAGER` 404) | S6 키를 테넌트 전체 집합에서 푼다 |
| C5 | `RateLimitIT`(N 통과·N+1 429·`Retry-After`, 동시 N+5 스레드 → 통과 ≤ N·429 = 나머지, 429 행·감사 0, 지표 1), `RateLimitRuleTest` | S7 잠금 문장 제거 |
| C6 | `SimulationLogIT`(성공·실패 행 하나, 엔진 다운 중에도 `REQUESTED` → `FAILED`, 실패 행 본문 NULL, 응답 `simId` = 행), `RebuildIT` 확장 | S8 `REQUESTED` 삽입을 호출 뒤로 |
| C7 | `EngineFailureIT`(down·slow·broken·S9·S10·S12·S8 → 표 ⑥, 호출 1) | S9 클라이언트에 재시도 1회 |
| C8 | `UniformRejectionIT` 확장(본문 10개 길이·해시), `ResponseLeakScanIT` 확장, 로그 스캔(요청 값·증권번호 0) | S10 422 본문에 엔진 `message` 통과 |
| C9 | `DeferralsQueryIT`(막대·0 생략·RETENTION 400·`held`/항목 원값·`expectedBase`·`p = 100` 값 동일·`policyKey` 필터·84 상한), `ForecastScenarioTest` | S11 RETENTION을 `held`에도 적용 |
| C10 | `labels.test`(새 라벨 넷이 기존 라벨과 다름·16칸 그대로), `screens.test`(타임라인·시뮬레이션·홈 — 고정 문구·HELD 범례·`ruleSnapshotId`), `messages.test`(금지어), `lintRules`(새 입력에서 금액 산술) | S12 시뮬레이션 금액을 `monthLabel`로 |
| C11 | `bundle-size`·`scan-dist`·`webLicenses`(새 패키지 0) | S13 시뮬레이션 화면을 정적 import |
| C12 | `web-e2e`(시나리오 ⑨ × 3, axe·CSP·누출·요약 개수 검사) | S14 `simId`를 주소에(동적 센티널) |
| C13 | `run.sh` diff | — |
| C14 | CI 전 잡, `no-docker`, 평문 스캔, 검증 메타데이터 재생성(빈 `GRADLE_USER_HOME`) | — |

## ⑪ 단계 (단계마다 시험·주입·설계서 같은 커밋)

0. **엔진 PR**(샘플 — ③). 병합 뒤 포털이 사본을 받는다.
1. **계약 1.1.0 + 엔진 사본**: `verifyUpstreamContract` 일반화, 호환 시험, 생성 클라이언트·규칙 파일. 설계서 §7 한 줄.
2. **접근 타입 이동**(Q11): 동작 0, 규칙 시험의 FQN만.
3. **V6 + `portal-sim`**: 로그·리밋·클라이언트·매핑·컨트롤러·바이트·지표 — C2·C4~C8.
4. **`/me/deferrals` 확장 + CR-1~4 + 스캐너 둘** — C3·C9.
5. **화면**: 타임라인·시뮬레이션·홈·한도·명세 — C10·C11.
6. **E2E**: 스텁·스트림·시나리오·센티널, `run.sh` — C12·C13.
7. **문서**: 설계서 v1.8·`CLAUDE.md`·`authz-matrix`·`운영.md`·README·DECISIONS.
8. **보고서** → 태그 `phase-5` → PR(병합은 심사 뒤).

## ⑫ 설계서 v1.8·`CLAUDE.md`에서 고칠 곳

- **설계서**:
  - §5: V6 열·`CHECK`·인덱스, `kind` 값.
  - §6.4: 격리 범위 — 허용 셋과 금액 곱셈 하나, 막대 비율, 84개월, 계약 필터.
  - §7: 1.1.0 표 행 — POST 둘·deferrals 쿼리·`features`·CR-1~4.
  - §8 3·4: 최종 문구, Q23·Q24.
  - §9:
    - 레이트 리밋 문장 — 행 수 기준·429·감사 없음.
    - CSRF 근거 한 줄.
    - 거부 바이트 표에 여섯 행 추가.
  - §10: 레이트 리밋 행, 홈 네 콜 문장 수, 카드 상한 200.
  - §12, §13(프론트·계약 행).
  - §14.1: #1(여전히 범위 밖), #7(상품 출처 = 카드 + 직접 입력, 레이트 리밋 값 = 테넌트 10 → **닫힘** 제안), #13(Phase 7 후보 — 그대로).
- **`CLAUDE.md`**:
  - 규칙 1에 "예외 하나 — §6.4 시나리오(`ForecastScenario`), 아키텍처 시험이 곱셈·나눗셈 위치를 닫힌 목록으로 격리"(지시문 승인).
  - 새 절 "시뮬레이션 규약": 엔진 호출은 서버만·재시도 0·사본 속성만 통과·로그 두 트랜잭션·리밋은 행 수·고정 바이트.
  - 코드 규약에 "곱셈·나눗셈은 허용 목록 메서드에서만".

## ⑬ 하지 않는 것

- 지시문 §4 전부:
  - 금액 가공(전/후 차이 포함).
  - 재시도, 화면의 엔진 호출.
  - 로그 삭제 잡, 온디맨드 명세(#1).
  - 차트 라이브러리, 경로·쿼리의 `policyKey`, `PAYMENT_OVERDUE` 선택지.
  - 알림·문의.
  - 1.1.0 밖 API.
- 여기에 더해:
  - 소진율(한도 대비 예상 누적)을 화면이나 포털이 계산하지 않는다.
  - `incentiveCodes`를 받지 않는다(Q4).
  - 엔진 `message`를 통과시키지 않는다.
  - 예상 이월 채권(#13)은 Phase 7.

## ⑭ 다음 Phase 질문 (보고서에서 다시)

- Phase 6:
  - 알림 파생 규칙(§6.5)의 경계 판정 자리: 프로젝터 같은 트랜잭션, 재생 제외 — §14.1 #12.
  - dedup 키.
  - `inquiry` 상태 전이와 답변 주체(운영 CLI?).
  - `PATCH /notifications/{id}/read`의 CSRF 없는 근거(④ 끝 문장을 정식화).
- 엔진 E3 후보:
  - 상품 마스터 API(Q3 ⓒ).
  - `PolicyEventResult.generatedAt`(Q13).
  - 모르는 상품의 오류 코드(Q3).
