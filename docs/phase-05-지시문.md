# 포털 Phase 5 지시문 — 분급 타임라인·시뮬레이션 (ga-agent-portal, v1.0, 2026-10-22)

## 형식에 대해

Phase 4와 같은 E2 형식 — **범위·완료 기준·질문만**. 모듈 배선·엔진 호출 클라이언트·화면 구조는 계획이 정하고, 지시문 문장이 코드·계약·엔진과 다르면 P-번호로 반증한다. 승인은 계획의 결정(Q·P)에 대해서만 한다.

브랜치 `work/phase-5`, 태그 `phase-5`, PR, **병합은 수용 심사 회신 뒤**. 정본: `docs/설계서.md` v1.7.1(§2.3, §4.3, §5 `simulation_log`, §6.4, §7, §8 3·4, §9 서비스 간 인증, §10 레이트 리밋·보존, §12 Phase 5, §14.1 #1·#7·#13), `contracts/api/v1/engine-internal.openapi.yaml` 1.4.0(엔진 E2가 구현 — R1~R8), `contracts/api/v1/portal-api.openapi.yaml` 1.0.0 → **1.1.0**(이 Phase 0단계), Phase 4 수용 심사 §4(Q1~Q6 결정), `CLAUDE.md` 전 절(화면 규약 포함).

## 0. 전제

- 포털 `main` = Phase 4.1 병합 뒤(`86d6fe6`). 화면 4종과 `web/` 규약은 그대로 — 이 Phase가 더하는 화면은 **분급 타임라인**과 **시뮬레이션 2탭**, 서버는 **`portal-sim`**(엔진 동기 호출 프록시 + `simulation_log`)과 `/me/deferrals`의 확장이다. 홈·한도 상세·라인 상세의 비활성 진입점(타임라인·"해약 시 영향")이 살아난다. 알림·문의는 여전히 비활성(Phase 6).
- **돈은 엔진이 계산한다.** 시뮬레이션 금액은 엔진 dry-run 응답을 **그대로** 통과시킨다(포털 서버·화면 모두 합산·변환 0). 포털이 하는 산술은 Phase 3까지의 SQL 정수 합과 표시용 비율뿐이고, 이 Phase에 **딱 하나 예외**가 들어온다 — §6.4 유지율 시나리오(`ForecastScenario`, 아래 ④). 그 밖의 금액 곱셈은 어디에도 없다.
- 엔진 호출은 **서버만** 한다(화면 → 포털 API → 엔진 `/internal/v1/simulations/*`). 화면은 엔진 주소·서비스 토큰을 모른다. 엔진에 보내는 `agentId`는 `Caller`(identity_link)에서만, `policyNo`는 요청의 `policyKey`를 **그 설계사의 증권번호 집합 안에서** 되푼 것만(Phase 3 규약 — SQL에 키 없음, 남의 키는 404 같은 바이트).
- 응답에 증권번호 원문 없음, 로그에 요청 값 없음은 Phase 3·4 규약 그대로. `simulation_log.request`는 **서버 내부 저장**이라 원문(증권번호·보험료·상품 키·계약일)을 담는다(§2.3 재현 가능) — 어떤 응답·로그·화면에도 옮기지 않는다.
- 계약 1.1.0은 Phase 3의 v1.4처럼 **0단계 별도 PR**(심사가 여기서 선승인, 자체 CI 통과 후 병합). 엔진 쪽 변경은 **응답 샘플 커밋 하나**(코드 0) — 아래 ⓪.

## 1. 범위

⓪ **계약 `portal-api` 1.1.0**(0단계 PR, 내용은 Phase 4 수용 §4 Q6 결정):
- 경로 추가 `POST /api/v1/me/simulations/new-contract`·`POST /api/v1/me/simulations/policy-event` — 요청은 엔진 요청에서 `agentId`를 빼고 `policyNo` 대신 `policyKey`, 응답은 엔진 결과(`NewContractResult`·`PolicyEventResult` 모양 — `$ref`가 아니라 **포털 계약 안의 사본**, 금액은 `Won`) + `simId`(UUID `pattern`) + `generatedAt`·`ruleSnapshotId` + 공통 `asOf`·`dataStatus`. 오류: 400 `INVALID_REQUEST`(입력 형식), 404(남의·없는 `policyKey` — 같은 바이트), **422**(엔진 422 코드 통과 — 닫힌 목록 `INSURER_AMBIGUOUS`·`UNSUPPORTED_EVENT_TYPE`·그 밖의 엔진 코드는 `ENGINE_REJECTED`), **429**(레이트 리밋 — 고정 바이트 + `Retry-After` 정수 초), **503**(엔진 불가 — 고정 바이트, D-3 저하 모드). 거부 바이트 표(Phase 3 §4.2)에 429·503·422 행.
- `GET /api/v1/me/deferrals`: `scenario=ALL_INFORCE|RETENTION`(기본 `ALL_INFORCE`)·`p`(RETENTION일 때 필수, 정수 % 1~100), 응답에 `scenario`·`p?`, `months[]`에 **표시용 막대 비율** `bar: {expected: "61.3%", held: "12.0%"}`(그 응답 안 월별 `expected + held`의 최댓값 대비 — 서버 정수 연산, `utilization`과 같은 규약; 최댓값 0이면 생략), `RETENTION`이면 `months[].expected`는 시나리오 적용값이고 `expectedBase`(원값)가 함께 온다. 테넌트가 비활성인데 `scenario=RETENTION`이면 400.
- `GET /api/v1/me`: `features: {forecastScenario: boolean}`(선택 — 테넌트 `params.forecast_scenario_enabled`).
- Phase 4 **CR-1~CR-4**(`MonthResponse.previous`, `Line.reversalOfYm`·`reversedByYm`, `Ledger.ruleVersionId`·규정 기준일, `Ledger.productName`·`productKey`·`insuredMasked`) — 선택 필드. 화면 반영은 이 Phase 범위에 **들어온다**(홈 "전월 대비"는 두 값을 나란히, 차감 없음; 다른 달 짝의 추가 호출 제거; 한도 카드 상품명·피보험자). `productName`은 Phase 3 응답 스캔 범위.
- `format` 0·`pattern`만, YAML 1.1 안전, `CHECKSUMS` 갱신. 1.0.0 응답은 1.1.0 스키마를 그대로 통과한다(추가는 전부 선택 필드 — 시험).
- **엔진 응답 샘플**: 엔진 `SimulationContractTest`가 쓰는 요청·응답 쌍(한도 초과 월납 150,000 + 정액 시책, 분급 커브 있음/없음, 2026-06-30 체결 한도 미적용, 해약 6/7회차 경계, 오류 코드 전부)을 엔진이 **실제 MockMvc 응답 바이트**로 `contracts/samples/simulations/*.json`에 떨어뜨린다(엔진 소형 커밋 — 시험이 파일을 쓰는 `-Psamples.update=true` 방식, 코드 변경 0). 포털은 `contracts/shared/ga-commission-engine/`에 사본 + `CHECKSUMS` + `UPSTREAM`. 이 쌍이 포털의 엔진 스텁·계약 시험·E2E의 유일한 엔진 데이터다.

① **`portal-sim` 모듈**: 의존 방향 `app → sim → projection → domain`(CLAUDE.md). 엔진 HTTP 클라이언트(Phase 1 `HttpFeedClient`와 같은 자리·같은 서비스 토큰 파일·같은 테넌트 `engine_base_url`), 타임아웃(파라미터), 재시도 **0**(dry-run이라도 두 번 부르지 않는다 — 로그가 두 줄 된다). 엔진 응답은 `engine-internal` 사본 스키마로 검증한 뒤 통과시킨다(검증 실패 = 503이 아니라 **502 `ENGINE_CONTRACT`** 고정 바이트 + 로그에 스키마 위치만).

② **`simulation_log`(V6)**: §5 DDL 그대로(`status REQUESTED/SUCCEEDED/FAILED`, `rule_snapshot_id`, `request`·`response` JSONB) + RLS(V2 규약) + `(tenant_id, agent_id, created_at)` 인덱스. 트랜잭션 둘: 호출 전 `REQUESTED` 커밋 → 호출 → `SUCCEEDED`(response 전부 + `ruleSnapshotId`) 또는 `FAILED`(엔진 코드·HTTP 상태만, 본문 없음). 보존 3년은 테넌트 파라미터(기본값, 삭제 잡은 **없음** — `docs/운영.md`에 SQL 한 줄). `ingest_event`처럼 재구축이 지우지 않는 표 목록에 추가.

③ **레이트 리밋**(Phase 4 수용 §4 Q4): 설계사당 분당 N = 테넌트 `params.sim_rate_per_minute`(기본 10, 정수). 판정 = 그 설계사의 `simulation_log` 행 수(직전 60초, `REQUESTED`·`SUCCEEDED`·`FAILED` 모두) ≥ N → **429** + `Retry-After` + 지표 `api_rate_limited_total{tenant}`, 감사 없음, **로그 행 없음**. 판정과 `REQUESTED` 삽입은 같은 트랜잭션(설계사 advisory lock — 동시 요청이 N을 넘지 않는다, 시험).

④ **`/me/deferrals` 확장 + `ForecastScenario`**: `bar` 비율(서버 정수 연산). RETENTION = `expected × p / 100`(정수 내림) — **이 곱셈은 `portal-query` `ForecastScenario` 한 클래스에만** 있고, 아키텍처 시험이 그 클래스 밖의 `Won`·금액 열에 대한 곱셈·나눗셈(Java 산술과 SQL `*`·`/` 둘 다 — SQL 스캐너 확장)을 금지한다. `held`·`items[].amount`에는 적용하지 않는다(원값). 테넌트 비활성 → 400 한 바이트. `CLAUDE.md` 규칙 1에 "예외 하나 — §6.4 시나리오(`ForecastScenario`), 아키텍처 시험 격리" 문장을 **이 지시문이 승인한다**(의미 변경).

⑤ **화면 — 분급 타임라인**(`/deferrals`): 기간 토글 12/24/48/84개월(`from`=이번 달 KST, `to`=+n−1 — 날짜 연산은 `src/time/`), 월별 막대 = **CSS 막대 + 표**(막대 너비는 `bar.*` 문자열을 CSS 속성으로, `role="img"`·`aria-label`; 표가 접근성 본문 — 라이브러리 없음, Phase 4 수용 §4 Q1), 예정/보류 구분(HELD는 "보류" — "다시 받을 수 있는 돈"으로 읽히는 문구 금지, §6.4 DD-9), 계약 필터(`policyKey` 문자열 비교), 시나리오 토글은 `/me.features.forecastScenario`일 때만 노출(p 입력 → 쿼리), RETENTION이면 모든 금액 라벨 "참고(유지율 {p}% 가정)" + `expectedBase` 나란히, 고정 문구 "분급 확정 스케줄만 반영 · 참고치 · 비확정". 항목 목록은 Phase 4 라벨 규약(`deferralLabel`). 홈 "다음 달 분급 예정액" 줄이 살아난다(`months[]`의 다음 달 `expected` — 화면 산술 0).

⑥ **화면 — 시뮬레이션**(`/simulations`, 탭 둘): ① 신계약 — 상품 입력(질문 3), 월납보험료(정수 원 입력 — 입력값은 금액이 아니라 **문자열 → 서버 400 규칙**, 화면 산술 0), 계약일(기본 오늘 KST), 시책 포함 여부(`incentiveCodes` — 질문 4) → 회차별 예상·한도액·예상 소진·시책·분급 첫해/이후. ② 해약 영향 — 계약 선택(그 설계사의 한도 카드 목록에서 `policyKey` — `/me/limits?status=ALL`), 이벤트 유형(`CANCEL`·`LAPSE`·`WITHDRAW`·`REDUCE`·`REINSTATE` — `PAYMENT_OVERDUE`는 엔진 422이므로 선택지에서 뺀다), 시점(기본 오늘), REDUCE면 새 월납 → 환수액(부호 그대로 — "환수" 라벨), 이번 달 실수령 변화 전/후(두 값 나란히 — 차이는 화면이 내지 않는다), 취소되는 분급 항목, 채권 잔액. 둘 다: 고정 문구 "설계사 참고용 · 비확정 · 고객 제시 불가", 결과에 `ruleSnapshotId`·`simId`·`generatedAt`, 모든 금액에 라벨 "시뮬레이션"(`simulationLabel()` — 월 상태 라벨 어느 것과도 다르다), 429면 마지막 결과 그대로 + "잠시 후 다시(n초)" (Query 캐시 — 로그를 읽지 않는다), 503이면 "시뮬레이션을 지금 할 수 없습니다 — 조회는 가능" + 마지막 결과 유지. 한도 상세 "해약 시 영향" 버튼 → 탭 ②에 그 계약이 선택된 채로(경로·쿼리에 `policyKey` 금지 — Phase 4 규약 — 상태로 전달, 질문 6). 라인 상세 "문의" 버튼은 여전히 비활성.

⑦ **E2E·엔진 스텁**: Phase 4 하네스에 테넌트별 **엔진 시뮬레이션 스텁**(피드 스텁과 같은 서버 — ⓪ 샘플 쌍을 요청 바이트 대조로 응답, 모르는 요청은 500) + 제어(`/control/{tenant}/sim-down|up`, `sim-slow`). 시나리오: 신계약 샘플 셋, 해약 6/7회차, 422 둘, 429(N+1 연타), 503(스텁 다운), 타임라인 12/84·HELD·RETENTION(활성 테넌트 하나 — GA04에 플래그), 홈 "다음 달 분급"·"전월 대비", 한도 상세 → 해약 영향. 누출 센티널에 샘플의 증권번호(`POL-2026-0001` 등)·`simId` 추가 — 화면·URL·콘솔·저장소 0. 실 엔진 E2E(`e2e/engine-feed/run.sh`)에 시뮬레이션 호출 한 쌍을 더한다(사용자 실행 — 보고서 "미실행"이면 그렇게).

⑧ **문서**: 설계서 v1.8(§5 V6, §6.4 격리 범위, §7 1.1.0, §8 3·4 최종 문구, §10 레이트 리밋·거부 바이트 표 추가 행, §12, §13, §14.1 #1·#7 갱신 — #7은 "상품 목록 출처"가 결정되면 닫힘), `CLAUDE.md` 규칙 1 예외 문장 + "시뮬레이션 규약" 절(엔진 호출은 서버만·통과·로그·레이트 리밋), `docs/authz-matrix.md` 행 3(POST 둘 + deferrals 변경 없음), `docs/운영.md`(`sim_rate_per_minute`·보존 SQL·엔진 타임아웃), DECISIONS P5-n.

## 2. 완료 기준

| # | 기준 | 증명 |
|---|---|---|
| C1 | 계약 1.1.0: 1.0.0 응답 전부가 1.1.0을 통과(선택 필드만 추가), `format` 0, `CHECKSUMS`, 엔진 샘플 사본 = 엔진 바이트 | 계약 시험 + `verifyContractChecksums` + 샘플 해시 대조 |
| C2 | **시뮬레이션 금액은 통과만**: 엔진 샘플 응답 → 포털 응답의 금액 필드 집합이 바이트 같다(`simId`·`asOf` 등 포털 필드만 더해진다) | 샘플마다 JSON 비교 시험, 주입: 포털에서 금액 하나 가공 |
| C3 | 곱셈 격리: `ForecastScenario` 밖의 금액 곱셈·나눗셈 0(Java 전 모듈 + `AgentQueryRepository` SQL) | ArchUnit + SQL 스캐너 확장, 주입: 다른 클래스에 `× p` / SQL에 `* :p` |
| C4 | `agentId`는 `Caller`에서만, `policyKey` → 원문은 그 설계사 집합 안에서만 — 남의 키는 404 같은 바이트, 엔진에 그 요청이 **가지 않는다** | 스텁 호출 0 단언, 주입 |
| C5 | 레이트 리밋: N번째 통과·N+1번째 429 + `Retry-After`, 동시 N+5 요청에서 통과 ≤ N, 429는 로그 행·감사 없음·지표 1 | 통합 시험(스레드), 주입: 잠금 제거 |
| C6 | 로그: 성공·실패 모두 행 하나, `REQUESTED`가 호출 **전에** 커밋(엔진 다운에도 행이 남는다), 실패 행에 본문 없음, 응답에 `simId`와 행 일치, 재구축이 지우지 않음 | 통합 시험 + 재구축 시험 확장 |
| C7 | 엔진 불가·타임아웃 → 503 고정 바이트, 엔진 응답 스키마 위반 → 502 고정 바이트, 엔진 422 → 코드 통과(닫힌 목록 밖은 `ENGINE_REJECTED`), 재시도 0 | 스텁 다운·느림·깨진 응답, 호출 횟수 1 단언 |
| C8 | 거부 바이트 표(401·404·400·422·429·502·503) 전부 길이·해시 고정, 로그·`ingest_cursor`·응답 어디에도 증권번호 원문·요청 값 없음 | Phase 3 바이트 시험 확장, 응답 스캔 센티널에 샘플 증권번호 |
| C9 | `/me/deferrals`: `bar` 비율은 SQL 정수 연산 문자열(최댓값 0이면 생략), RETENTION은 활성 테넌트만(비활성 400), `held`·항목은 원값, `expectedBase` 동반 | 통합 시험 + A-1·E2E 스트림 골든 |
| C10 | 화면: 금액 산술 0(Phase 4 세 층 그대로 — 새 입력 필드 포함), 모든 시뮬레이션 금액에 `simulationLabel`, RETENTION 금액에 "참고(유지율)" 라벨, 고정 문구 2종·HELD 문구·`ruleSnapshotId` 렌더, 16칸 표 무변경 | Vitest + `labels.test` 확장, 주입: 시뮬레이션 금액을 `monthLabel`로 |
| C11 | 차트 라이브러리 0, 번들 초기 로드 상한 127 KiB 유지(새 경로는 지연 청크), 외부 출처 0 | `bundle-size`·`scan-dist`·`THIRD-PARTY.md` |
| C12 | E2E ⑦ 전 시나리오 × 세 프로젝트, axe 0, CSP 0, 누출 0(센티널 확장), 스냅샷 0 | `web-e2e` 잡 아티팩트 |
| C13 | 실 엔진 E2E 스크립트에 시뮬레이션 쌍 추가(미실행이면 미실행으로 보고) | `e2e/engine-feed/run.sh` diff |
| C14 | Phase 0~4 시험 무손상, `no-docker` 실패, 평문 스캔(센티널 확장), 검증 메타데이터 재생성 diff(새 의존성 0이 목표), 주입 ID 단위 기록 | CI 아티팩트 |

## 3. 계획에서 답할 질문

1. **엔진 오류 → 포털 상태 매핑**: 엔진 404 `AGENT_NOT_FOUND`(포털에는 링크가 있는데 엔진이 모른다)를 404 같은 바이트로 낼지, 502 `ENGINE_CONTRACT`로 낼지. 권장 **502** — 설계사 잘못이 아니고 운영 문제다(로그에 `agent_id` 해시). `POLICY_NOT_FOUND`는 포털이 키를 풀었는데 엔진에 없다 — 같은 처리.
2. **타임아웃·HTTP 클라이언트**: Phase 1 `HttpFeedClient`를 공유할지, `portal-sim`에 별도 클라이언트(연결·읽기 타임아웃 파라미터, 기본값)를 둘지. 서비스 토큰 파일은 같은 것.
3. **상품 입력의 출처**(§14.1 #7): 엔진에 상품 마스터 API가 없다. ⓐ 그 설계사의 한도 카드·라인에서 본 `productKey` 목록(`/me/limits`·CR-4 `productName`) + 직접 입력, ⓑ 직접 입력만, ⓒ 엔진에 상품 목록 API 요청(E3). 권장 **ⓐ** — 엔진이 모르는 키는 엔진 404/422가 답한다. `insurerCd`는 `INSURER_AMBIGUOUS` 422를 받으면 그때 입력란을 연다(미리 묻지 않는다).
4. **시책 코드 입력**: `incentiveCodes[]`는 설계사가 알 수 없다 — 권장 **보내지 않는다**(엔진이 전부 적용) + "시책 포함" 표시만. 토글이 필요하면 v2.
5. **`Retry-After` 값**: 가장 오래된 행의 `created_at + 60s − now`(정수 초, 최소 1)인지 고정 60인지. 권장 전자.
6. **해약 영향 진입**: 한도 상세 → 시뮬레이션 탭 ②로 `policyKey`를 어떻게 넘길지(경로·쿼리 금지). 권장 라우터 `state`(메모리) — 새로고침하면 선택이 사라지고 목록에서 다시 고른다.
7. **`/me/limits?status=ALL`이 페이지 없음**: 시뮬레이션 ②의 계약 선택이 전 카드 목록이다. 이 Phase에서 그대로 쓸지(카드 수 상한을 §10에 적고), 페이지를 1.1.0에 넣을지. 권장 **그대로**(상한 §10에 — v2 페이지).
8. **RETENTION 적용 범위**: `months[].expected`만인지 `items[]`에도 "적용 후" 열을 줄지. 권장 **월 합계만**(§6.4 문장 그대로). `p`가 100이면 `ALL_INFORCE`와 같은 바이트여야 하는지 — 권장 같다(시험).
9. **E2E 시간 고정**: 시뮬레이션 `generatedAt`·`asOfDate` 기본값(오늘)과 샘플 요청 바이트 대조가 충돌한다 — 스텁이 `asOfDate`·`contractDate`를 무시하고 나머지로 대조할지, E2E 시계를 샘플 날짜(2027-02-15 KST)로 고정할지. 권장 **시계 고정**(Phase 4 `page.clock` 방식) — 스텁 대조는 바이트 그대로.
10. **`sim_rate_per_minute`·보존·타임아웃의 자리**: 전부 `tenant.params` 키인지, 타임아웃은 애플리케이션 설정인지. 권장 리밋·보존은 테넌트, 타임아웃은 앱 설정.

## 4. 하지 말 것

포털의 시뮬레이션 금액 가공(반올림·합산·차이 — "전/후 차이"도). 엔진 재시도. 화면의 엔진 직접 호출. `simulation_log` 삭제 잡. 온디맨드 명세 갱신(#1 — 범위 밖). 차트 라이브러리. 경로·쿼리의 `policyKey`. `PAYMENT_OVERDUE` 선택지. 알림·문의 화면. 1.1.0 밖의 API 변경.

## 5. 보고 형식

Phase 4와 같게. 추가: 거부 바이트 표(최종 7행), 레이트 리밋 동시성 시험 수치, 엔진 샘플 ↔ 포털 응답 비교 표(쌍마다), `ForecastScenario` 격리 증거(스캐너가 본 곱셈 위치 목록 = 그 클래스 하나), 타임라인·시뮬레이션 라벨 표(최종), 번들 표(새 경로 청크), 다음 Phase 질문(Phase 6 — 알림 파생 규칙 §6.5·dedup·이의제기 `inquiry`·`PATCH` 첫 쓰기 API의 CSRF 없는 근거).
