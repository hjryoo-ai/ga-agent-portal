# 포털 Phase 8 계획 — 데모 테넌트·통합 실행 (ga-agent-portal)

> 이 계획은 지시문 `docs/phase-08-지시문.md` v1.0(2026-10-28)에 대한 것이다. 브랜치는 `work/phase-8`(`main` = `2ccfc44` 위 `1581f50` — Phase 7 심사 보관)이다. **이 계획이 승인되기 전에는 엔진 커밋·계약 사본·코드를 쓰지 않는다.**
>
> **승인**(2026-10-28 — `docs/phase-08-계획승인.md`): Q1~Q14 권장안 전부, 돌이킬 수 없는 결정 일곱 전부 예(검증 메타데이터 추가만·제거 0·전이 목록 표). 작은 조건 A(`/auth/config` 출처 정확 일치·열거 불가 시험)·B(콜백·`BroadcastChannel` 규약) — 아래 Q2·P-9 행과 §④ S16에 반영. Q5 관리 포트 호스트 공개 0을 compose E2E가 본다. Q11 명세 샘플은 엔진 커밋에서 `index.json` 하나로 묶는다. P-2 수치는 "예시값". `docs/DECISIONS.md` "Phase 8 계획 승인"(DE-30·DD-21). 구현에서 달라진 곳은 같은 파일 P8-n과 보고서 §5.
>
> **돌이킬 수 없는 결정 — 승인 전에 확인을 바라는 일곱**(지시문 형식 절의 넷 + 이 계획이 더한 셋):
> 1. **엔진 데모 스트림 PR**(⓪b) — 엔진 `ga-commission-engine`에 시험 코드·시험 픽스처·샘플만 바꾸는 PR 하나. 자체 CI 통과 뒤 내가 병합한다(Phase 5 #8·Phase 7 #9와 같은 방식). 스트림 파일이 약 9 MB이고 두 저장소에 한 벌씩 들어간다(Q1 측정).
> 2. **이벤트 계약 표기 1.4.1**(⓪a, DD-19) — 순서는 셋이다. ⓐ 포털 `main` 기반의 작은 PR(설명문 한 줄 + `CHECKSUMS` + 파싱 동치 시험)을 자체 CI 뒤 병합한다(`CLAUDE.md` 작업 방식의 "공유 계약 표기 변경" 예외). ⓑ 엔진 사본 갱신 PR을 낸다(`UPSTREAM` = ⓐ의 병합 커밋). ⓒ 그 PR을 엔진 CI 뒤 병합한다.
> 3. **새 의존성** — `opentelemetry-exporter-otlp`(④), 그리고 Q5에서 고르면 `micrometer-registry-prometheus`. 둘 다 Boot 4.1.1 BOM 판이고, 검증 메타데이터는 빈 `GRADLE_USER_HOME`에서 재생성한다(추가만·제거 0).
> 4. **CSP 변경**(③) — 화면 응답의 `connect-src`가 정적 `'self'`에서 "`'self'` + 그 요청 출처에 등록된 테넌트의 발급자 출처"로 바뀐다. 다른 아홉 지시어는 그대로다.
> 5. **V10 — `tenant`에 열 둘**(P-13): `web_origin`(UNIQUE)과 `oidc_client_id`. `portal_issuer` 롤에 그 두 열의 `SELECT` 권한을 준다.
> 6. **인증 없는 끝점 하나** — `GET /auth/config`(Q2). `/api/**` 밖의 함수형 경로라 `AuthzMatrixTest`·`ApiLayerRulesTest`(닫힌 컨트롤러 셋·핸들러 15)는 그대로 둔다. 대신 "인증 없는 경로" 닫힌 목록 시험을 새로 둔다.
> 7. **웹 린트 규칙의 닫힌 예외 하나**(P-11) — 네트워크 호출 모듈이 `src/api/client.ts` 하나에서 `src/auth/oidc.ts`까지 둘이 된다(파일 경로 열거, 패턴 아님).
>
> 읽은 것:
> - 지시문 전문, Phase 7 수용 심사 §2·§3.
> - 설계서 v1.10: §3.2, §6.7, §9, §10, §11, §12, §13, §14.1 전 항목, 부록 B·C.
> - `CLAUDE.md` 전 절.
> - 포털 코드:
>   - 재구축·대사: `Rebuilder`(txA/B/C)·`ViewReset`·`ViewResetRepository`, `ArchitectureRulesTest`(`theRebuilderReachesNoDerivation`·`theDerivationRulesHaveAClosedSetOfCallers`·계층 규칙), `RebuildIT`.
>   - 스캔: `MultiplicationScanTest`·`ReconArithmeticScanTest`·`SqlArithmeticScanTest`·`TenantPredicateScanTest`.
>   - 엔진 클라이언트·스텁: `HttpFeedClient`·`FileFeedAdapter`, 스텁 셋(`StubFeedServer`·`StubSimulationEngine`·`StubStatementEngine`), `FileServiceTokenSource`.
>   - 인증·화면: `IssuerTenantResolver`·`IssuerDirectoryReader`·`ApiSecurityConfiguration`·`ScreenConfiguration`(CSP), `WebScreensIT`·`UniformRejectionIT`·`AuthzMatrixTest`·`ApiLayerRulesTest`.
>   - 웹: `web/src/boot/token.ts`·`ReloginScreen`·`routes.ts`·`eslint.config.js`·`bundle-size.mjs`, Playwright 픽스처.
>   - 계약·시험: `EventSchemaTest`, `contracts/` 배치와 두 검증 태스크, V1~V9, `docker/postgres/init-roles.sql`, `e2e/engine-feed/run.sh`, `ci.yml`.
> - 엔진(`ai-comm/commission-system`, `main` = `38cdcee`): 골든 러너 `OutboxEngine`(인메모리 저장소 + 발행 검증기)·`A1Scenario`·`PortalA1StreamTest`, `RuleFixtures.standardRules()`, `LimitGateStep`(CUT/DEFER), `DeferralReleaseService`(HELD), `localstack/`(Oracle Free digest)·`up.sh`.
> - 측정: 엔진 골든 러너로 데모 규모 스트림을 실제로 돌렸다(Q1 — 아래 표). 일회용 시험 파일은 측정 뒤 지웠고 엔진 작업 트리는 깨끗하다.

## 0. 먼저 결정이 필요한 것 (요약)

| # | 질문 | 권장 | 다른 길 |
|---|---|---|---|
| Q1 | 골든 러너가 데모 규모를 감당하나 | **감당한다 — 부록 B 목표치(설계사 30·계약 약 400)를 줄이지 않는다.** 측정 표는 아래에 있다. 30명·420건·24개월이 이벤트 19,235건(재구축 상한 50,000의 38%), JSONL 8.9 MB, 인메모리 3.75초(JVM 기동 포함), 실패 0이다.<br>• 최종 수는 필수 케이스(감액·정정·조정·HELD — 측정에는 없다)를 넣은 엔진 커밋에서 다시 재 보고서에 싣는다.<br>• 체결일 범위는 부록 B의 2025-07~가 아니라 **2026-01~2027-06**이다(P-3).<br>• §14.1에는 "목표치 그대로, 기간만 P-3"을 적는다. | 하한(5명·40건 — 1,942건, 0.9 MB)으로 줄인다 |
| Q2 | 화면이 발급자를 아는 방법 | **ⓐ `GET /auth/config`** — 응답은 `{"issuer":"…","clientId":"…"}` 둘만 담는다.<br>• 테넌트는 **요청 출처(`web_origin`)로** 고른다(V10, P-13). 등록이 없는 출처는 고정 404 바이트를 받는다(지금의 `NOT_FOUND`와 같은 바이트). 응답 헤더는 `Cache-Control: no-cache`다.<br>• 같은 조회가 화면 응답의 CSP `connect-src`에 그 발급자 출처를 더한다.<br>• 읽기는 기존 허용 목록 클래스 `IssuerDirectoryReader`(롤 `portal_issuer`)의 새 메서드 하나다. 허용 목록 항목은 그대로 하나다.<br>• 시험에서의 자리:<br>  – 경로는 `/api/**` 밖의 함수형 경로라 권한 표(`authz-matrix`)·`AuthzMatrixTest`·`ApiLayerRulesTest`는 바뀌지 않는다.<br>  – `docs/authz-matrix.md`에 **"인증 없는 경로" 닫힌 목록** 블록(헬스 셋 + `/auth/config`)을 새로 두고, `PublicPathsTest`가 보안 설정의 `permitAll` 경로와 양방향으로 대조한다.<br>  – 바이트는 `UniformRejectionIT`(등록 없는 출처 = 404 같은 바이트, 인증 헤더가 있어도 무시)로 고정한다.<br>  – 계약은 `portal-api` 1.3.0에 `/auth/config`(`security: []`)를 더한다.<br>• **조건 A**: 테넌트 선택은 요청 `Host`로 만든 출처(`Origin`이 있으면 그것)와 `web_origin`의 **정확 일치**뿐이다. 응답은 두 필드뿐이다. `AuthConfigIT.eachOriginSeesOnlyItsOwnIssuer` — 등록 출처 둘은 각자 자기 것만, 제3 출처는 404 같은 바이트(열거 불가). `PublicPathsTest`의 닫힌 목록에 `/auth/callback`은 넣지 않는다(화면 경로 — `SCREEN_PATHS`·`routes.ts`). | ⓑ `index.html`의 `<meta>`(서버가 HTML을 고쳐 써야 한다 — 지금은 정적 파일) · ⓒ 빌드 시 고정(다중 테넌트 불가 — 지시문 금지 "하드코딩 발급자") |
| Q3 | refresh 토큰 | **메모리 보관 + 회전.** 세션은 그 탭의 수명이다.<br>• Keycloak 렐름 예시값: 액세스 토큰 300초, SSO 세션 유휴 1,800초·최대 8시간, `revokeRefreshToken: true`·`refreshTokenMaxReuse: 0`(회전 — 쓴 refresh는 다시 못 쓴다).<br>• 갱신 시점은 액세스 만료 60초 전 타이머다. 갱신이 실패하면(400·401) 토큰 셋을 버리고 로그인 화면으로 간다.<br>• 프래그먼트로 주입된 토큰(웹뷰 호스트 경로)은 refresh가 없다 — 그대로 둔다. | 액세스 토큰만(5분마다 재로그인) |
| Q4 | compose에서 DRIFT를 보이는 법 | **ⓐ** — 데모 스트림에 엔진이 낼 수 없는 불일치를 넣지 않는다. compose E2E는 `recon run --ym=…` → 결과 `OK`(MATCH)와 `status`만 본다.<br>• **F11(운영자 재구축 뒤 표식 유지)의 증거는 compose가 아니라** `RebuildIT` 확장과 하네스 E2E ⓔ 변형이다. compose에는 미해결 DRIFT가 없으므로 보일 것도 없다 — 지시문 ⑥의 "운영자 재구축 뒤 표식 유지(①)로 대체"는 compose 안에서는 성립하지 않는다.<br>• compose E2E는 대신 운영자 `ingest rebuild`가 **화면 응답을 바꾸지 않음**(재구축 전후 다섯 화면 API 응답 동일)을 본다. 데모 규모 재구축 시간도 여기서 잰다(§14.1 #11 "부하 실측은 Phase 8 compose").<br>• README에는 "DRIFT 감지·복구·표식 유지의 증거는 하네스 E2E ⓐ~ⓔ"라고 쓴다. | ⓑ `portal-demo`의 DB 조작 명령(제품 경로에 시험 진입점 — 지금까지 0) |
| Q5 | Prometheus | **Jaeger만.** 지표는 긁기 끝점 `/actuator/prometheus`까지 둔다(새 의존성 `micrometer-registry-prometheus`).<br>• 노출은 **관리 포트**(`management.server.port`, compose 안 네트워크 전용 — 호스트에 공개하지 않는다)에서만 한다. 앱 포트의 `/actuator/prometheus`는 지금처럼 404 바이트다(`UniformRejectionIT` 그대로).<br>• DB 헬스는 **끈 채** 둔다 — `portal_health` 롤은 만들지 않는다. 허용 목록 조건(전용 롤 + 닫힌 FQN + 테넌트 데이터 미접근)을 새로 세울 만한 쓰임이 데모에 없다. §9 표의 "(예약)"을 "v2"로 옮긴다. | Prometheus + Grafana · 긁기 끝점도 없음(의존성 0 — 지표는 시험으로만) |
| Q6 | `engine` 프로파일 | **범위 밖으로 기록하고 `run.sh`를 그대로 둔다.** 이유는 넷이다.<br>• Oracle Free 이미지(엔진 `oracle-free-image` digest)가 압축 1.2 GB(arm64)·1.3 GB(amd64)다 — 레지스트리 매니페스트에서 쟀다.<br>• 엔진 앱에 컨테이너 이미지가 없다. 만들려면 엔진 저장소에 Dockerfile·빌드 경로를 더해야 해서 ⓪의 "시험 코드·샘플만"을 넘는다.<br>• `run.sh`가 이미 한 명령이다.<br>• CI에서 돌릴 수 없다(시간·크기).<br>Oracle Free 재배포 조건은 이미지를 우리가 재배포하지 않으므로(공개 레지스트리에서 끌어온다) 걸리지 않는다 — 문제는 크기·엔진 이미지다. `RESULT.md`는 여전히 사용자 몫이다. | ⓐ `engine` 프로파일 = `ENGINE_REPO`를 빌드 문맥으로 한 서비스 둘(Oracle + JDK 이미지에서 bootJar) — 사용자 실행 전용, CI 밖 |
| Q7 | 데모 로그인 비밀번호 | **README 허구값 하나**(설계사 전원 같음 — 운영자 계정은 Keycloak에 없다, CLI 전용). 렐름 파일에 평문이 아니라 Keycloak 자격 증명 해시로 둔다. 평문은 README 한 곳뿐이고, 평문 스캔의 금지 문자열 목록에는 넣지 않는다(README에 있어야 하므로). | 처음 뜰 때 생성·출력(로그에 남는다) |
| Q8 | 영문 동기화 | **마지막 커밋에 한 번**: `README.en.md`·`docs/en/design.md`(v1.6 → v1.11)·`docs/en/authz-matrix.md`·`e2e/engine-feed/README.en.md`. 이후는 요청 때만. | Phase마다 |
| Q9 | 릴리스 태그 `v1.0` | **병합 커밋에 사용자가 단다.** 태그 메시지 초안(Phase 표·CI run)은 보고서 부록에 둔다. `phase-8`은 보고서 커밋에 단다. | 내가 병합 뒤 단다 |
| Q10 | v2 목록 | **§14.1과 §12 Phase 9 줄을 §14.2 "v2로 남는 것" 한 표로 합친다**(항목·출처 결정·지금의 기본 가정·코드 위치).<br>• 들어갈 항목: 조직장 뷰(#9)·PWA·다이제스트·`/me/limits` 페이지·운영 화면·푸시/알림톡·PDF 명세·오프라인 스냅샷·#1·#4·#6 재매핑·#10·#13·`portal_health`(Q5)·`engine` 프로파일(Q6).<br>• §14.1에는 Phase 8 뒤에도 열린 것만 남긴다. | 둘을 따로 둔다 |
| Q11 | compose의 야간 대사(스케줄러 그대로 — Phase 7 Q1) | **명세 샘플을 데모 설계사 전원 × 스트림의 전 달로 만든다**(엔진 같은 커밋 — 확정 달은 CONFIRMED, 마지막 열린 달은 ESTIMATE). 그러면 스트림 기간 안의 어느 날짜에 띄워도 스케줄러 회차가 `MATCH`다.<br>• 지시문의 "명세 두 달"만 두면 그 두 달 밖의 회차는 스텁 500 → `INCONCLUSIVE(ENGINE_UNAVAILABLE)`이 되어 `status`가 매일 "엔진 장애" 신호를 낸다.<br>• 비용은 샘플 파일 약 750개(작은 JSON)다. | 두 달만 + README에 "그 밖은 INCONCLUSIVE" |
| Q12 | 데모 스트림의 골든 뷰 | **표마다 행 수 + 정규화 행 전체의 SHA-256**(`GoldenViews`의 요약 모드)을 쓰고, **손 검산 설계사 둘의 전 행**을 더한다. 전 행 파일은 9표 × 약 2만 행으로 수 MB이고, 사람이 검토할 수 없는 크기다.<br>• 손 검산 요약은 보고서 표다: 둘의 달별 세전·환수 상계·정정·한도 누계를 엔진 이벤트 값에서 바로 대조한다. | 전 행 커밋 |
| Q13 | k6·compose 실측을 어디서 | **수동 워크플로 `perf.yml`(`workflow_dispatch` — 게이트 아님)**. GitHub 러너에서 compose를 띄우고 k6를 돌려 결과 JSON을 아티팩트로 올린다. 보고서는 그 run ID를 1차 증거로 인용한다(이 기계에서는 Docker를 켜지 않는다).<br>• 수치는 러너 환경값이라 보고서에 "공유 러너 2 vCPU" 조건을 함께 적는다. | 사용자가 로컬에서 돌린다 |
| Q14 | 포털 이미지를 "누구나" 빌드하는 법 | **이미지 빌드 첫 단계에서 공개 저장소 `ga-disclosure`를 태그 `platform-v0.2.0`에서 받아 `publishToMavenLocal`**한 뒤 포털을 빌드한다(P-16). 자격 증명이 필요 없다. 검증 메타데이터가 GitHub Packages 판과 같은 바이트임을 강제한다(로컬 개발이 이미 이 경로다 — `README` "로컬: `mavenLocal()`").<br>• 바이트가 다르면 빌드가 실패하므로 그때는 멈추고 보고한다. | 빌드에 `GHP_READ_TOKEN`을 BuildKit 비밀로 넘긴다(누구나 PAT가 필요) · 이미지를 GHCR에 발행 |

### Q1 측정 — 엔진 골든 러너(인메모리 `OutboxEngine`, 발행 검증기 켬)

조건: `RuleFixtures.standardRules()`, 조직 경로 셋(T1/B1/H1·T2/B1/H1·T3/B2/H2), SR·JR 번갈아, 회차 입금 매월, 해약 약 8%(7회차), 시책 20%, 매월 마감(익월 5일)·지급(익월 10일), 마지막 달 잠정 명세. 감액·정정·조정·HELD는 없다. 실행 환경은 Corretto 21.0.9, 이 기계(Apple Silicon), `./gradlew :commission-outbox:test`다.

| 규모 | 이벤트 | 바이트 | 시간 | 유형별 |
|---|---:|---:|---:|---|
| 설계사 5·계약 40 | 1,942 | 892,360 | 0.67 s | CommissionCalculated 607 · DeferralScheduled 678 · LimitLedgerPosted 378 · StatementConfirmed 112 · PayoutCompleted 112 · LimitLedgerOpened 35 · DeferralStatusChanged 14 · StatementEstimated 5 · PolicyEventObserved 1 |
| 설계사 30·계약 420 | **19,235** | **8,891,858** | **3.75 s** | CommissionCalculated 6,063 · DeferralScheduled 7,083 · LimitLedgerPosted 3,903 · StatementConfirmed 681 · PayoutCompleted 681 · LimitLedgerOpened 377 · DeferralStatusChanged 355 · ClawbackReceivableUpdated 35 · StatementEstimated 30 · PolicyEventObserved 27 |

- 재구축 상한 50,000의 38%다. 포털 쪽 재구축 시간(한 트랜잭션 txB)은 compose E2E에서 잰다(Q4).
- 첫 동기화 시간은 `FeedClient.DEFAULT_LIMIT` 200 기준 약 97쪽이다. compose 기동 시간 표에 함께 싣는다.

## ① 확인한 사실 (지시문·설계서와 다른 곳)

| # | 지시문·설계서 문구 | 사실 | 처리 |
|---|---|---|---|
| P-1 | 설계서 §13 "k6 … 홈 3콜" | 홈 화면은 다섯 콜이다 — `/me`(Layout)·`month`·`limits`·`deferrals`·`notifications/recent`(`HomeScreen.tsx:53,76,108,109`, `Layout.tsx:11`). 지시문 "다섯 콜"이 맞다 | §13 고침 |
| P-2 | 지시문 ⑤ "p95 목표 §10(화면 p95 — 설계서 값)" | 설계서에 응답 시간 목표가 **없다**. §10의 p95는 "엔진 커밋 → 화면 60초"(투영 지연)이고, §13에는 수치가 없다 | §13에 **예시값**을 새로 둔다: 콜마다 p95 ≤ 300 ms, 홈 다섯 콜 묶음(브라우저처럼 동시) p95 ≤ 800 ms, 설계사 30명 동시. 판정은 그 값 대비 |
| P-3 | 부록 B "체결일 2025-07 ~ 2027-06" | 엔진 표준 룰의 요율·지급률 효력이 **2026-01-01부터**다(`RULES_FROM`). 2025년 체결분은 산출이 없다 | 체결일 2026-01~2027-06, 스트림 2026-01~2027-12(24개월). 두 경계(2026-06-30/07-01 한도, 2027-01-01 분급) 양쪽은 그대로 들어간다 |
| P-4 | 부록 B "CUT·DEFER_AFTER_FY 각 1, HELD 분급 1" | 표준 룰의 한도 규칙은 `DEFER_AFTER_FY` 하나뿐이다. `OutboxEngine`의 유지 판정은 `(policyNo, asOf) -> true` 고정이라 HELD가 나올 수 없다 | 엔진 커밋에서 **시험 픽스처만** 바꾼다. ① 데모 룰 저장소 = 표준 룰 + 한도 규칙을 기간으로 나눈다(예: 2026-07~09 체결 CUT, 10월~ DEFER — 단건 해석 그대로). ② `OutboxEngine`에 유지 판정 주입(기본값은 지금과 같은 `true`이므로 기존 골든 바이트가 그대로다). 엔진 `main` 소스는 0 |
| P-5 | 지시문 ② "파일 피드 어댑터 … `portal-demo`에 같은 계약의 구현", ⓕ "Phase 5·7 스텁과 같은 대조 규약" | 계층 규칙상 `Demo`는 `Domain`·`PlatformCore`만 쓸 수 있다(`ArchitectureRulesTest:120-136`). `FileFeedAdapter`·`FeedResponses`(ingest), 정규화기(PlatformCanonical), 스텁(시험 픽스처)은 못 쓴다 | `portal-demo` = JDK `HttpServer` 하나가 세 경로를 섬긴다(`/internal/v1/events`·`/simulations/*`·`/agents/*/statements/*`). 대조는 JCS 바이트가 아니라 **Jackson 트리 값 동치**다(정수만인 샘플에서 같은 결과 — 대조 시험으로 확인). Jackson은 `portal-demo` 의존에 더한다(새 아티팩트 0, 잠금에 이미 있다). 하네스 스텁은 그대로 둔다 |
| P-6 | 지시문 ① "`recon_result`의 마지막 행이 미해결 DRIFT" | 재구축기는 `com.ga.portal.recon..`에 닿을 수 없다(`theDerivationRulesHaveAClosedSetOfCallers` — "the rebuilder never reaches the reconciler"). 미해결 판정 `Episodes`는 `portal-recon`에 있다 | 불변식은 **`ViewReset` 포트의 새 메서드 `restoreDriftMarks()`**로 둔다. 구현은 infra 한 문장 — `(agent_id, close_ym)`마다 `Episodes`와 같은 순서 규칙(Phase 7 Q10 — 같은 시각의 처리까지 구현 때 `Episodes`에서 읽어 맞춘다)으로 고른 마지막 행이 `DRIFT`면 `data_status = 'DRIFT'`. txB의 `verifyAll()` 앞에서 부른다. `Episodes`와 같은 답인지 격자 시험으로 묶는다(`DriftMarkAgreementIT`). ArchUnit은 무변경 |
| P-7 | 지시문 ⓪a "`contracts/events/v1` 1.4.1" | 이벤트 계약에는 판 표시 파일이 없다 — "1.4"는 설계서·DECISIONS 문장에만 있다. `EventSchemaTest`가 `gross` 설명에 "commType ∉ {CLAWBACK, ADJUSTMENT}"·"전 상태 합산"을 요구한다. 틀린 곳은 "포털 earnings_month.gross와 같은 정의"뿐이다(`basisSeq` 설명은 이미 "하한") | 설명문만 고친다("확정은 위 정의, 잠정은 그 달 도래 예정 분급 미리보기를 더할 수 있다 — 포털 Σ와 같다는 보장 없음, `basisSeq`는 하한"). **파싱 동치** = 모든 스키마에서 `description`을 뺀 JSON 트리가 1.4.0과 같다. 판은 설계서 변경 이력·DECISIONS·`CHECKSUMS`로 남긴다(판 파일 신설 없음) |
| P-8 | 지시문 ⓪b "시험을 데모 스트림 전체에" | 투영 불변식·`NotificationStreamsTest`·골든 시험은 스트림 목록이 코드에 박혀 있다(디렉터리 탐색 없음). `EventSchemaTest`는 `contracts/events/v1/samples` 밑 디렉터리 셋 밖을 거부한다 | 사본은 지시문대로 `contracts/shared/ga-commission-engine/samples/demo/`(`UPSTREAM` 닫힌 목록에 추가)에 두고, 각 시험에 명시적으로 한 줄씩 더한다. `NotificationStreamsTest`의 손 계수는 데모 스트림에서 불가능하다 → 유형별 수 + 예외 0 + 손 검산 설계사 둘의 튜플 |
| P-9 | 지시문 ③ "`state`·`code_verifier`는 메모리" | **전체 페이지 리다이렉트로는 성립하지 않는다** — IdP로 떠나는 순간 그 페이지의 JS 메모리가 사라진다. 저장소 없이 돌아올 방법이 없다 | **팝업 흐름**: 본 창이 `state`·`code_verifier`를 메모리에 쥐고 팝업(사용자 클릭 — 모바일은 새 탭)으로 인가 요청을 연다. 팝업이 `/auth/callback?code&state`에 돌아오면 주소에서 둘을 지우고, 이름이 `pkce:<state>`인 `BroadcastChannel`(같은 출처·저장소 아님)로 본 창에 넘기고 닫힌다. 본 창이 `state`를 대조한 뒤 메모리에서 교환한다. 팝업이 막히면 안내 문구를 보인다. iframe은 IdP의 `X-Frame-Options`와 우리 `frame-ancestors 'none'` 때문에 쓸 수 없다.<br>**조건 B**: 콜백 페이지는 `code`·`state`를 그리지 않고, 받자마자 `replaceState`로 주소에서 지운다(프래그먼트 토큰과 같은 규약). 본 창은 자기가 연 팝업의 `state`와 일치하는 메시지 **하나만** 받고 채널을 닫는다 — `state` 불일치·두 번째 메시지·자기가 열지 않은 시도의 메시지는 버린다. Vitest `channelRejectsAForeignState`·`channelIgnoresASecondMessage` |
| P-10 | (같은 곳) "새 탭·새로고침이면 다시 로그인" | Keycloak SSO 세션 쿠키는 **IdP 출처**에 남는다(포털 출처 저장소 5종 아님) — 다시 누르면 비밀번호 없이 돌아올 수 있다 | 로그아웃은 `end_session`이 아니라 Keycloak `logout` 끝점에 refresh 토큰을 POST한다(세션 종료 — 주소에 토큰 없음). 그 뒤 메모리를 비운다. 저장소 시험은 포털 출처만 본다(지금 규약 그대로) |
| P-11 | 지시문 ③ (토큰 교환·갱신을 화면이) | 웹 린트가 `fetch`·`XMLHttpRequest` 등을 `src/**`에서 막고, `openapi-fetch`는 `src/api/client.ts`에서만 허용한다(`eslint.config.js:10-23,65-72`) | **닫힌 예외 파일 하나** `src/auth/oidc.ts`(디스커버리·토큰·갱신·로그아웃 — 발급자 출처로만)를 둔다. 린트 시험이 "예외는 정확히 두 파일"과 "다른 파일의 `fetch`는 여전히 실패"를 단언한다(규칙을 좁히지 않고 코드를 한 곳에 모은다 — 돌이킬 수 없는 결정 7) |
| P-12 | 지시문 Q2 "`AuthzMatrixTest`에 어떻게" | 표는 `SELF`/`404`만 표현하고, 인증 없는 경로를 표현할 수 없다. `ApiLayerRulesTest`는 컨트롤러 셋과 핸들러 15를 닫아 둔다 | Q2 — `/api/**` 밖 함수형 경로 + "인증 없는 경로" 닫힌 목록 블록·`PublicPathsTest` |
| P-13 | 지시문 Q2 "테넌트 설정에서", ③ "`aud`·`iss` = 포털 `tenant.oidc_*`" | `tenant`에 클라이언트 id도 웹 출처도 없다(V5는 `oidc_issuer`·`oidc_audience`뿐). 화면 체인은 테넌트 문맥이 없고 CSP는 정적 상수다(`ScreenConfiguration.java:48-49`) | **V10**: `web_origin TEXT UNIQUE`(`^https?://[^/]+$` — 경로 없음) + `oidc_client_id TEXT`. 둘 다 있거나 둘 다 없다. 있으면 `oidc_issuer`도 있어야 한다. 조회는 `IssuerDirectoryReader.byWebOrigin`(같은 롤, 열 단위 `GRANT`)이고, 출처마다 60초 메모리 캐시(주입 `Clock`)를 둔다. 하네스 테넌트는 `web_origin`이 없다 → 하네스 E2E의 화면·CSP·`auth.spec`가 그대로다 |
| P-14 | 지시문 ② "`oidc_issuer` = compose의 Keycloak 렐름" | 발급자 주소는 브라우저와 포털 컨테이너에서 **같은 문자열**이어야 한다. `iss` 정확 일치이고, 포털은 `withIssuerLocation(issuer)`로 디스커버리·JWKS를 그 주소에서 받는다. 브라우저의 `localhost:8180`은 컨테이너 안에서 자기 자신이다 | Keycloak `KC_HOSTNAME=http://localhost:8180`으로 둔다. 포털 컨테이너의 네트워크 이름공간을 공유하는 작은 중계 사이드카(`network_mode: service:portal`, socat — digest 고정)가 컨테이너 안 `localhost:8180`을 `keycloak:8080`으로 잇는다. 공용 DNS(`*.localtest.me`)에 기대지 않는다 |
| P-15 | 지시문 §0 "비밀은 compose가 처음 뜰 때 생성" | `docker/postgres/init-roles.sql`의 롤 비밀번호 기본값 `*_local_only`가 저장소에 있다(로컬 일회용 컨테이너용) | compose는 기본값을 쓰지 않는다. 비밀 생성 서비스가 볼륨에 파일(600)을 만든다 — 롤 넷 + 슈퍼유저 + 서비스 토큰 + HMAC 키 + Keycloak 관리자.<br>• PostgreSQL: `POSTGRES_PASSWORD_FILE`과 `psql -v`로 받는다.<br>• 포털: Spring `configtree:`로 받는다(환경 변수에 비밀 0 → `docker compose config` 출력에 0).<br>• Keycloak 관리자: 진입점 래퍼가 파일에서 읽는다 |
| P-16 | 지시문 §0 "누구나 `docker compose up`" | 플랫폼 아티팩트가 로컬은 `mavenLocal`, CI는 GitHub Packages(PAT 필요)에서만 온다 | Q14 — 이미지 빌드 단계에서 공개 `ga-disclosure`를 받아 `publishToMavenLocal`한다. 검증 메타데이터의 SHA가 같은 바이트를 강제한다 |
| P-17 | 지시문 ④ "Prometheus 긁기 끝점" | `micrometer-registry-prometheus`가 의존성에 없다. 보안 체인은 헬스 셋만 연다. `PortalApplicationIT`가 "db" 헬스 기여자 0을 단언한다 | Q5 — 새 의존성 1(승인 대상), 관리 포트 전용, DB 헬스 끈 채(그 단언 그대로) |
| P-18 | 설계서 §6.7·§14.1 #11 "`portal.ingest.rebuild.max-events`" | 실제 키는 `portal.ingest.rebuild-max-events`다(`application.yaml:50`) | 문서만 고침(사실 정정) |
| P-19 | 지시문 ⑥ "데모 증권번호 전부 = 센티널" | 데모 스트림의 증권번호는 약 420개다. 하네스의 센티널 대조(`pii-sentinels.properties` ↔ `sentinels.mjs`)는 손으로 적는 목록이다 | compose E2E의 센티널은 **스트림에서 읽는다**(데모 사본 파일이 시험 입력 — `verifyTestInputs` 선언). 평문 스캔(`scanPlaintextLeaks`)에는 하네스 목록 그대로에 더해 compose 아티팩트에 대한 같은 목록 스캔을 둔다 |
| P-20 | (지시문에 없음) 데모의 업무 시각 | 스트림은 2026-01~2027-12의 업무 시각을 담고, 화면의 "이번 달"·대사 창은 실제 날짜(`Clock`)다. 데모 전용 시계는 "데모 코드 경로 0"에 걸린다 | 그대로 둔다. README에 "데모 데이터는 2026-01~2027-12 — 그 밖의 날짜에는 홈의 이번 달이 비어 있다"를 적고, §14.2 v2에 "스트림 재생성"을 둔다 |
| P-21 | 지시문 ④ "Jaeger의 스팬 속성 키 ⊆ 표" | Jaeger·OTel SDK가 키를 더한다(`otel.scope.name`·`span.kind`·`otel.status_code`·`internal.span.format`, 자원 속성 `service.name`·`telemetry.sdk.*` 등) | `docs/지표.md` §4에 **"수집 경로가 더하는 키" 닫힌 목록**을 두고, 앱 키 ⊆ 스팬 표, 나머지 ⊆ 그 목록인지 본다. 목록 밖 키는 실패다 |
| P-22 | 지시문 ⑤·⑦ (compose에서 돌린다) | 이 기계에서는 Docker를 켜지 않고 통합·E2E는 CI에서 돌린다(지금까지의 진행 방식 — 1차 증거는 CI run) | k6·기동 시간은 Q13 수동 워크플로(1차 증거 run ID)로 잰다. `engine`은 Q6 |
| P-23 | 지시문 ③ "데모 사용자 N명 … `aud`" | Keycloak 액세스 토큰의 `aud`는 기본값으로 포털 audience를 담지 않는다. k6는 브라우저 없이 토큰을 받아야 한다 | 렐름에 audience 매퍼를 둔다(`aud` ∋ `tenant.oidc_audience`). k6에는 **별도 클라이언트 `k6`**(Direct Access Grants — 데모 렐름 전용)를 둔다. 포털 웹 클라이언트 `portal-web`은 PKCE S256 필수·Direct Access Grants 끔·Implicit 끔·리다이렉트 = `http://localhost:8080/auth/callback` 하나 |
| P-24 | 지시문 C5 "시뮬레이션 화면 데이터" | 스텁은 샘플 요청에만 답한다(밖은 500). 화면에 임의 값을 넣으면 503 저하 화면이 뜬다 | 데모 설계사마다 샘플 요청 둘(신계약·해약 — Q11의 엔진 커밋)을 README 표로 싣고, compose E2E가 그 값을 입력한다. 화면에 미리 채우는 코드는 두지 않는다(데모 경로 0) |

## ② 단계와 커밋

| 단계 | 내용 | 저장소 |
|---|---|---|
| 0a | 이벤트 계약 1.4.1 — `StatementEstimated.gross` 설명문, `CHECKSUMS`, `EventSchemaTest` 파싱 동치(`descriptionOnlyChangesSince140`). 포털 `main` 기반 PR → CI → 병합. 이어서 엔진 사본 PR(`contracts/shared/ga-agent-portal/` + `UPSTREAM`) → 엔진 CI → 병합. `work/phase-8`은 `main`을 병합해 받는다 | 포털·엔진 |
| 0b | 엔진 데모 PR — `DemoScenario`(설계사 30, 조직 경로 셋 = 본부 1·지점 2·팀 6, 계약 약 420, 체결일 2026-01~2027-06, 해약 연 5%·감액 연 2% 예시, 필수 케이스 아홉), `DemoStreamTest`(스트림 바이트 = `contracts/samples/demo/demo_ga.jsonl`, 케이스 ↔ seq 표를 단언), P-4 픽스처 둘, 데모 시뮬레이션 샘플(설계사마다 신계약 1·해약 1)·명세 샘플(Q11). 엔진 CI → 병합 | 엔진 |
| 0c | 포털 사본(`samples/demo/` + `UPSTREAM` 닫힌 목록 + `CHECKSUMS`), 데모 스트림 시험: `EnvelopeValidator` 전 건 · `ProjectionInvariantTest` · 결정론(두 번 투영 = 같은 뷰) · `GoldenViews` 요약 모드(Q12) · `NotificationStreamsTest` 예외 0·유형별 수 · `ReconRules`(투영한 달 × 명세 샘플 = 전부 MATCH) | 포털 |
| 1 | F11 불변식 — `ViewReset.restoreDriftMarks()`, `ViewResetRepository` 한 문장, `Rebuilder` txB 한 줄, `RebuildIT` 둘(`anOperatorRebuildKeepsAnUnresolvedBatchDriftMarked`·`aRepairedMonthStaysOk`), `DriftMarkAgreementIT`, 하네스 E2E ⓔ′(운영자 재구축 뒤 "확인 중" 유지), `docs/운영.md` §8 문단 교체 | 포털 |
| 2 | `portal-demo` — `DemoServer`(피드·시뮬레이션·명세, 서비스 토큰 파일 대조, 재생 속도 `DEMO_REPLAY=instant\|paced:<n/s>`), `FeedClientContract`를 **HTTP로** 통과(`HttpFeedClient` → `DemoServer` — 포털 클라이언트와의 계약 시험, 시험 원천이라 계층 규칙 밖), 샘플 밖 500 시험 | 포털 |
| 3 | 인증 — V10, `IssuerDirectoryReader.byWebOrigin`, `/auth/config`, 요청별 CSP, `PublicPathsTest`, `WebScreensIT`·`UniformRejectionIT` 확장, 계약 `portal-api` 1.3.0. 웹 — `src/auth/oidc.ts`(PKCE S256·팝업·`BroadcastChannel`·교환·회전·로그아웃), `ReloginScreen`(설정이 있으면 "로그인" 버튼, 없으면 지금 문구), `/auth/callback`(두 닫힌 목록에 함께), 로그아웃 버튼(PKCE 세션에서만). Vitest(저장소 5종 0·`state` 불일치 거부·회전·401 → 로그인·주소 정리), 번들(초기 청크 안 — 넘으면 지연 청크 + `LAZY_SCREENS` 한 줄, 승인) | 포털 |
| 4 | compose — `docker/compose.yaml`(digest 고정: PostgreSQL 18·Keycloak·Jaeger·socat·busybox, 포털·데모는 로컬 빌드 `docker/portal.Dockerfile`), 서비스 `secrets`→`postgres`→`portal`(Flyway)→`seed`(파일 SQL: 테넌트 `DEMO` "데모GA"·설계사 30·운영자 `cli:portal`)→`demo`·`keycloak`(렐름 가져오기)·`jaeger`·`keycloak-loopback`. OTLP 의존성·속성(`compose`에서만 켬)·관리 포트 긁기 끝점 | 포털 |
| 5 | compose E2E — Playwright 프로젝트 `compose`(데스크톱 Chromium) + CI 잡 `compose-e2e`. 흐름: 로그인(팝업) → 다섯 화면 데이터 → 알림함·문의 접수 → `docker compose exec portal … recon run --ym` → `status` → 운영자 `ingest rebuild` 전후 응답 동일·시간 → Jaeger API 키 검사 → 로그아웃. 고정 장치: 누출(센티널 = 스트림의 증권번호 전부 + 토큰·`code`·`state`)·CSP(외부 출처 = 발급자만)·axe·저장소 5종. 비밀 스캔: `docker compose config`·전 컨테이너 로그·이미지 파일 목록 | 포털 |
| 6 | k6 `perf/k6/home.js` + `perf.yml`(수동), compose 기동 시간·digest 표 | 포털 |
| 7 | 문서 — README(부록 C 다섯 + "AI 협업 방법론" + Phase 표), 설계서 v1.11, `docs/운영.md` §9 compose, `DECISIONS` 색인, `CLAUDE.md` "데모·배포 규약", 영문 동기화(Q8), 보고서 | 포털 |

## ③ 완료 기준별 증거

| # | 증거 |
|---|---|
| C1 | `EventSchemaTest.descriptionOnlyChangesSince140`(1.4.0 트리에서 `description`만 다르다), `verifyContractChecksums`, `verifyUpstreamContract`(엔진 사본·데모 사본 바이트), 엔진 사본 PR 병합 커밋 |
| C2 | 엔진 `DemoStreamTest` 케이스 표(필수 아홉 ↔ seq·설계사), 포털 0c 시험 다섯, 보고서 케이스 표·손 검산 요약 |
| C3 | `MultiplicationScanTest`·`ReconArithmeticScanTest`·`SqlArithmeticScanTest` 허용 목록 diff 0(보고서에 `git diff main -- …` 빈 출력), `portal-demo` 스캔 포함 확인(`importsEveryModuleAndThePlatform` 그대로) |
| C4 | `RebuildIT` 둘, `DriftMarkAgreementIT`, 하네스 E2E ⓔ′, ArchUnit 무변경 diff |
| C5 | `compose-e2e` 잡 run ID, 보고서 화면 상태 표(화면마다 표시된 상태 문구·행 수 — 스냅샷 아님) |
| C6 | `compose-e2e`의 비밀 스캔(생성된 비밀 파일의 값 전부 ↔ `compose config` 출력·로그·이미지·아티팩트), 평문 스캔, 볼륨 파일 권한 600 단언 |
| C7 | Vitest `pkce.test.ts`·`relogin.test.tsx`, compose E2E(저장소 5종·주소·회전 — 짧은 수명 렐름으로 갱신 한 번), 하네스 E2E 그대로 |
| C8 | `WebScreensIT` — 등록 없는 출처 = 지금 10 지시어 그대로, 등록 출처 = `connect-src 'self' <발급자 출처>`만 다르다(나머지 아홉 동일), compose E2E 위반 0·외부 요청 = 발급자뿐 |
| C9 | `OtlpExportOffIT`(시험·CI 프로파일에 OTLP 내보내기 빈 0), compose E2E Jaeger 키 검사, 검증 메타데이터 diff 표 |
| C10 | `perf.yml` run ID·k6 요약 표(콜별 p50/p95/p99, 묶음 p95), P-2 예시값 대비 판정, ETag 표식 문장 영향(`/me` 콜의 p95를 표식 문장 유무로 — 같은 run 안 A/B는 하지 않고 Phase 7 문장 수 표와 대조해 한 줄) |
| C11 | `build`·`web-e2e`·`no-docker`·`compose-e2e` 잡, 번들 크기 줄 |
| C12 | 보고서 §문서, `v1.0` 태그는 사용자(Q9) |

## ④ 주입 계획 (한 ID씩 기록 — 통합·E2E는 `work/phase-8` 기반 초안 PR)

| ID | 넣는 것 | 잡아야 할 시험 |
|---|---|---|
| S1 | `restoreDriftMarks()` 호출 제거 | `RebuildIT.anOperatorRebuildKeepsAnUnresolvedBatchDriftMarked` |
| S2 | 불변식이 가장 **오래된** 행을 본다(`ORDER BY run_at ASC`) | `DriftMarkAgreementIT` |
| S3 | `portal-demo`에 `long` 곱셈 하나 | `MultiplicationScanTest` |
| S4 | 데모 서버가 샘플 밖 요청에 첫 샘플로 답한다 | `DemoServerTest.outOfSampleIs500` |
| S5 | `code_verifier`를 `sessionStorage`에 | 린트(`no-restricted-properties`) — 정적 스캔이 먼저 잡으므로 S5′도 둔다 |
| S5′ | `code_verifier`를 `window.name`에(린트 밖 저장 경로) | compose E2E 저장소·누출 검사(`window.name` 포함으로 넓힌다 — 계획에서 미리 추가) |
| S6 | 콜백에서 `replaceState` 생략(주소에 `code` 남음) | Vitest `callbackClearsTheAddress` + compose E2E 주소 검사 |
| S7 | 갱신 뒤 옛 refresh 토큰을 계속 쓴다 | Vitest `rotationReplacesTheRefreshToken` |
| S8 | CSP `script-src`에 `'unsafe-inline'` | `WebScreensIT` |
| S9 | `/auth/config`가 `audience`도 싣는다 | 계약 시험(`ApiContract` — 추가 속성 금지) |
| S10 | `oidc.ts` 밖 파일에서 `fetch` | 린트 시험 |
| S11 | 서비스 토큰을 `.env`에 | compose E2E 비밀 스캔(`compose config` 출력) |
| S12 | 시험 프로파일에서 OTLP 켬 | `OtlpExportOffIT` |
| S13 | 스팬 속성에 표 밖 키 | compose E2E Jaeger 검사(+ 기존 `TracingLeakIT` — 둘 중 먼저 잡는 쪽 기록) |
| S14 | `/auth/callback`을 웹 목록에만 | `WebScreensIT.screenPathsEqualTheWebRoutesBothWays` |
| S15 | 데모 사본 한 바이트 변경 | `verifyContractChecksums` |
| S16 | 콜백 페이지가 `code`를 DOM에 그린다(조건 B) | compose E2E 누출 검사 |

## ⑤ 하지 않는 것 (지시문 §4 + 이 계획)

- 포털 안 계산 0. `portal-demo`는 바이트를 고르고 내보낼 뿐이다(seq 비교·머리 계산은 정수 비교).
- 데모 전용 제품 경로 0 — 포털 앱 이미지는 운영 설정 그대로이고 compose 값만 다르다.
- 화면에 시뮬레이션 값 미리 채우기 없음.
- `engine` 프로파일 없음(Q6). DB 헬스·`portal_health` 없음(Q5). Prometheus 서버 없음.
- Phase 9 항목·새 도메인 기능 없음. 영문 문서는 마지막 커밋에서만.

## ⑥ 보고서에 더할 것 (지시문 §5)

데모 스트림 케이스 표, 규모 실측(엔진 커밋 기준 최종 수 + 위 측정), 다섯 화면 상태 표, PKCE 저장 위치 표(값마다 메모리/없음 — `state`·`code_verifier`·`code`·ID·액세스·refresh), CSP 최종 헤더 두 판(등록 없음/있음), k6 표, 새 의존성 diff, compose 기동 시간·이미지 digest 표, `RESULT.md` 상태(Q6 — 사용자 몫), 영문 동기화 범위, Phase 0~8 총괄 표(시험 수 추이·주입 수·DE/DD/RM 수), 남은 v2 목록(Q10).
