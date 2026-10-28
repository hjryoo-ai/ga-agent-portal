# Phase 8 보고서 — ga-agent-portal 데모 테넌트·통합 실행 (지시문 v1.0 — 마지막 Phase)

> 지시문 `docs/phase-08-지시문.md` · 계획 `docs/phase-08-계획.md`(승인 2026-10-28 — Q1~Q14 권장안 전부, 돌이킬 수 없는 결정 일곱 모두 예, 조건 A·B, `docs/phase-08-계획승인.md`) ·
> 브랜치 `work/phase-8` · PR [#35](https://github.com/hjryoo-ai/ga-agent-portal/pull/35) · 태그 `phase-8`(이 보고서 커밋). **병합은 수용 심사 회신 뒤. `v1.0`은 병합 커밋에 사용자가 단다(Q9).**
> 엔진 변경: 데모 스트림 PR(엔진 #10 — 병합 `9963809`)·이벤트 계약 1.4.1 사본 PR(엔진 #11 — 병합 `0ef6ab9`), 둘 다 엔진 CI 통과 뒤 병합(승인 결정 1·2). 포털 계약 1.4.1 표기 PR #36(병합 `80880b8`). `run.sh`(실 엔진 E2E)는 사용자 몫 — **미실행**(§4.12).
> **심사에서 결정이 필요한 것은 없다.** 계획과 다르게 한 것 P8-1~P8-17(§5) 가운데 불변식을 넓히는 항목은 없다. 시험 쪽 결정 셋(P8-14 Jaeger 키 목록, P8-15 재구축 비교의 `asOf.at`, P8-17 compose E2E의 회전)은 보고 대상이다.

이 보고서의 **"주입 S…"**는 계획 §④의 주입 ID다(S5′는 계획이 미리 둔 것). 1차 증거는 직접 조회한 CI run이다 — 코드·시험의 마지막 커밋 `27e544f`의 run [40697515087](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/40697515087)(`build`·`no-docker`·`web-e2e`·`compose-e2e` 모두 success). 그 뒤 커밋은 문서·영문·`seed-demo.sql` 주석뿐이다.

## 1. 커밋

`origin/main` = `2ccfc44`(Phase 7 병합) 위 `work/phase-8`:

| 커밋 | 내용 |
|---|---|
| `1581f50` | Phase 7 수용 심사 보관(조건 없음, P7-15 승인), DD-20·DE-29, Phase 8 재료 Q1~Q6 |
| `51620a8` | 지시문 v1.0·계획(Q1~Q14, P-1~P-24, 돌이킬 수 없는 결정 일곱) |
| `212e85c` | 계획 승인 보관 — 조건 A·B, DE-30·DD-21 |
| `1d1b1c3`·`80880b8`·`b7eb797` | ⓪a 이벤트 계약 1.4.1(표기만 — DD-19), PR #36 병합, `main`을 작업 가지로 |
| `b1e42c4` | 1단계 — 재구축 끝 불변식(F11): `ViewReset.restoreDriftMarks()`, `RebuildIT` 둘, `DriftMarkAgreementIT`(259칸), 하네스 E2E ⓔ′ |
| `0e8ccba` | ⓪c — 엔진 데모 묶음 사본(`9963809`, 17,856건), `DemoStreamTest`·`DemoStreamIT`, 골든 요약 + 손 검산 둘(A-2001·A-2009) |
| `0223223` | 2단계 — `portal-demo`(엔진 대역 — 계약 세 경로, 계산 0), `DemoServerTest`·`DemoServerFeedContractTest` |
| `c2597b5` | F11 시험의 `recon_result` 키(같은 시각 행에 `source_seq`) — CI 40685297729 등이 잡은 시험 데이터 잘못(P8-6) |
| `87c0c31` | 3단계 — 브라우저 로그인 PKCE(`src/auth/oidc.ts`), `/auth/config`(계약 1.3.0 — 출처 정확 일치, 조건 A), V10, 요청마다 CSP `connect-src`, `/auth/callback`(조건 B) |
| `d3d7c49` | 4단계 — compose `demo`(비밀 생성·Keycloak 렐름·socat 중계·Jaeger), OTLP·Prometheus 의존성(§4.8), 관리 포트 체인, `OtlpExportOffIT`·`ManagementPortIT`·`DemoComposeFilesTest` |
| `b8e5fac` | 5단계 — compose E2E(Playwright 프로젝트 `compose`, CI 잡 `compose-e2e`), `scan-secrets.sh`, 프레임워크 스팬 높은 기수 키 제거, README 데모 절 |
| `5a37359` | 6단계 — k6 `perf/k6/home.js`, 수동 `perf.yml`(P8-13), `wait-demo.sh` |
| `26e04ed` | compose E2E: 로그인 뒤 화면 안 이동(P8-16), 비밀 파일 14·포트 목록 검사 |
| `c45fdf3` | 7단계 문서 — README 부록 C·방법론·Phase 표, 설계서 v1.11 §12~§14, `docs/운영.md` §9, DECISIONS 색인, `CLAUDE.md` "데모·배포 규약" |
| `bc05513` | compose E2E: Jaeger 질의 API v3, 타임라인 24개월, 분급 조회 범위 |
| `27e544f` | compose E2E: 재구축 비교의 `asOf.at`(P8-15), Jaeger 키 목록 정확화(P8-14) — **코드·시험의 마지막 커밋** |
| `c3ac609` | 첫 동기화 시간 문구(약 90초 → 2~3분, CI 실측) |
| `9c63e80` | 영문 최종 동기화(Q8 — §4.14) |
| (이 커밋) | 보고서, DECISIONS P8-17 |

## 2. 테스트 (CI 출처)

run [40697515087](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/40697515087)(`27e544f`)의 아티팩트(`test-reports` 결과 XML, `web-e2e-reports`, `compose-e2e-reports`)를 내려받아 셌다. 기준선은 Phase 7 보고서 §2.

| 모듈 | 소스셋 | 건수 | Phase 7 대비 | 비고 |
|---|---|---:|---:|---|
| `portal-domain` | test | 2,106 | 0 | |
| `portal-projection` | test | 671 | +3 | 골든 요약 모드·데모 골든 |
| `portal-ingest` | test | 89 | +5 | `DemoStreamTest` 5(스트림 전체 — 검증기·프로젝션 결정론·골든·알림 규칙·케이스 표). `RebuilderTest`는 같은 메서드 안에서 |
| `portal-query` | test | 45 | +2 | `PortalApiContractTest` +1(인증 없는 연산 닫힌 표), `PortalApiCompatibilityTest` +1(1.3.0 추가만) |
| `portal-sim` | test | 15 | 0 | |
| `portal-inbox` | test | 28 | 0 | |
| `portal-recon` | test | 1,832 | 0 | |
| **`portal-demo`** | test | **19** | **+19** | 새 모듈 — `DemoServerTest`, `DemoServerFeedContractTest`(피드 계약 F1~F8), `DemoComposeFilesTest` 5 |
| `portal-infra` | test | 10 | 0 | |
| `portal-infra` | integrationTest | 224 | +8 | `IssuerDirectoryIT`(`byWebOrigin`·V10 `CHECK` 사례) |
| `portal-app` | test | 44 | +8 | `LoginConfigsTest` 4, `PublicPathsTest` 3, `TracingConfigurationTest` 1 |
| `portal-app` | integrationTest | 146 | +12 | `AuthConfigIT` 3, `ManagementPortIT` 2, `OtlpExportOffIT` 1, `DemoStreamIT` 1, `DriftMarkAgreementIT` 1, `RebuildIT` +2, `WebScreensIT` +1, `UniformRejectionIT` +1 |
| `portal-app` | archTest | 79 | 0 | `TenantPredicateScanTest` 문장 허용 +1(승인된 `IssuerDirectoryReader` 문장) |
| **Java 합계** | | **5,308** | **+57** | 실패 0, 스킵 0 |
| `web` | Vitest | 155 | +20 | `pkce` 15, `relogin` 3, `lintRules` +2 |
| `web` | Playwright(하네스) | 85 | +3 | ⓔ′ × 세 프로젝트 |
| `web` | Playwright(compose) | 4 | +4 | 데스크톱 Chromium — 로그인·다섯 화면·알림함·문의·로그아웃 / 콜백 카나리 / 운영 CLI / Jaeger |

`no-docker`: 통합 시험 78건 전부 실패·스킵 0(Docker 없음 — 규약대로). 번들: 초기 125,851 B gzip(상한 130,048 — PKCE 코드는 초기 청크 안, 상한 무변경).

## 3. 완료 기준 증거

| # | 기준 | 증거 | 주입 |
|---|---|---|---|
| C1 | 계약 1.4.1 = 표기만, 엔진 사본·데모 사본 = 엔진 바이트 | `EventSchemaTest.descriptionOnlyChangesSince140`, `verifyUpstreamContract`(`UPSTREAM` `9963809` — 기존 사본 바이트 동일 + 데모 넷), `verifyContractChecksums`, 엔진 PR #10·#11 병합 커밋 | S15(로컬) |
| C2 | 부록 B 케이스 전부, 계약·불변식·결정론, 골든, 알림·대사 예외 0 | `DemoStreamTest`(케이스 13 ↔ seq·설계사 — §4.1, 알림 후보 1,731·증권번호 0, 두 번 투영 같음), `DemoStreamIT`(DB 적재 → SQL 불변식 0, 골든 요약 같음, `ReconRules` 716달 전부 `MATCH`, 운영자 재구축 같은 뷰), 손 검산 §4.2 | — |
| C3 | 포털에 수수료·세액·한도 계산 0 | `MultiplicationScanTest`·`ReconArithmeticScanTest`·`SqlArithmeticScanTest` **원천 무변경**(`git diff 2ccfc44 -- …` 0줄), `portal-demo` main도 스캔 대상(로컬 주입 S3: `Replay#lambda$tick$0=[IMUL]`) | S3 |
| C4 | 운영자 재구축 뒤 미해결 BATCH DRIFT 유지 | `RebuildIT.anOperatorRebuildKeepsAnUnresolvedBatchDriftMarked`·`aRepairedMonthStaysOkAfterARebuild`, `DriftMarkAgreementIT`(259칸 = `Episodes.unresolved`), 하네스 E2E ⓔ′ × 3, ArchUnit `theRebuilderReachesNoDerivation` 무변경 | S1, S2 |
| C5 | 한 명령 → README 계정 → 다섯 화면 + 알림함·문의 | `compose-e2e` run 40697515087 — 시험 1 통과, 화면 상태 표 §4.3 | — |
| C6 | 비밀은 볼륨 파일(600)에만 | `scan-secrets.sh`: 파일 14 전부 0600·소비자 uid, 값 9개 ↔ `compose config`·컨테이너 로그·inspect(env)·이미지 둘(`docker save`)·E2E 산출물 = 0, 관리 포트 비공개(포털 공개 = 8080만, 127.0.0.1:9090 거부). 평문 스캔 0 | S11 |
| C7 | PKCE 전부 메모리, 주소 정리, 저장소 비어 있음, 회전, 401 → 로그인 | Vitest `pkce` 15(RFC 7636 벡터, `channelRejectsAForeignState`·`channelIgnoresASecondMessage`, `callbackClearsTheAddress…`, `rotationReplacesTheRefreshToken`, 저장소·`window.name` 0), `relogin` 3, compose E2E(화면마다 저장소 6종 0 — `window.name` 포함, 주소·요청·콘솔·응답 누출 0, 콜백 카나리), 하네스 E2E 그대로(프래그먼트 경로). 회전은 compose E2E에서 돌지 않는다(P8-17) | S5, S5′, S6, S7, S10, S16 |
| C8 | CSP `connect-src`에 발급자 출처만 | `WebScreensIT.aRegisteredOriginAddsOnlyItsIssuerOriginToConnectSrc`(등록 출처 = 그 지시어 하나만 다름), compose E2E CSP 위반 0·외부 요청 = 발급자 출처뿐(§4.5) | S8, S14 |
| C9 | OTLP는 속성으로만, 시험·CI 꺼짐, Jaeger 키 ⊆ 표, 의존성 추가만 | `OtlpExportOffIT`, `ManagementPortIT`(대조), compose E2E Jaeger(스팬 5,924 — 이름 24·속성 키 13 전부 표 안, 자원 키 넷, 목록 키 전부 관측 — P8-14), 검증 메타데이터 diff 추가만(§4.8) | S12, S13 |
| C10 | k6 표·판정·ETag 한 줄 | §4.6 — perf run [40694537717](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/40694537717) | — |
| C11 | Phase 0~7 무손상 | §2(기존 시험 전부 통과), 하네스 E2E 85/85, `no-docker` 실패, 번들 상한 안 | — |
| C12 | 문서 | §4.14 — README 부록 C·방법론·Phase 표, 영문 동기화, 설계서 v1.11 §12·§14, DECISIONS 색인. `v1.0`은 병합 뒤 사용자 | — |

### 주입 기록 (ID 단위 — 17)

각각 단독으로 넣고 실패를 본 뒤 되돌렸다. 단위·정적·빌드 검사가 잡는 것은 이 기계에서, 통합·compose E2E가 잡는 것은 **주입 한 커밋의 초안 PR**(밑 `work/phase-8`)로 CI를 돌리고 병합 없이 닫고 가지를 지웠다. 커밋 보안 검토가 S5′ 주입 커밋(`window.name = verifier`)을 짚었다 — 버릴 PR의 의도된 위반, 작업 가지에는 없다.

| ID | 기준 | 주입 | 실패한 곳 |
|---|---|---|---|
| S1 | C4 | `Rebuilder`가 `restoreDriftMarks()`를 부르지 않는다 | PR [#37](https://github.com/hjryoo-ai/ga-agent-portal/pull/37), run 40695191424: `RebuildIT.anOperatorRebuildKeepsAnUnresolvedBatchDriftMarked`(통합 146 중 1) |
| S2 | C4 | 불변식이 가장 오래된 DRIFT 행을 기준으로 본다 | PR [#38](https://github.com/hjryoo-ai/ga-agent-portal/pull/38), run 40695198723: `DriftMarkAgreementIT.theRestoreStatementAgreesWithEpisodesOnEveryGridCell` |
| S3 | C3 | `portal-demo` `Replay`에 곱셈 | 로컬: `MultiplicationScanTest` — `Replay#lambda$tick$0=[IMUL]` |
| S4 | C2 | 데모 서버가 샘플 밖 요청에 첫 샘플로 답한다 | 로컬: `DemoServerTest`(샘플 밖 500) |
| S5 | C7 | `code_verifier`를 `sessionStorage`에 | 로컬: ESLint(저장소 금지 규칙) |
| S5′ | C7 | `code_verifier`를 `window.name`에(린트 밖) | 로컬 먼저: Vitest `nothing lands in browser storage, cookies or window.name`. PR [#46](https://github.com/hjryoo-ai/ga-agent-portal/pull/46), run 40697968802: `webTest` 같은 시험 + compose E2E 시험 1 "storage home" |
| S6 | C7 | 콜백에서 `replaceState` 생략 | 로컬: Vitest `callbackClearsTheAddress…` |
| S7 | C7 | 갱신 뒤 옛 refresh 토큰을 계속 쓴다 | 로컬: Vitest `rotationReplacesTheRefreshToken` |
| S8 | C8 | CSP `script-src`에 `'unsafe-inline'` | PR [#39](https://github.com/hjryoo-ai/ga-agent-portal/pull/39), run 40695209443: `WebScreensIT` 셋(`aRegisteredOrigin…`·`everyScreenPath…`·`hashedAssets…`) |
| S9 | C8 | `/auth/config`가 `audience`도 싣는다 | PR [#40](https://github.com/hjryoo-ai/ga-agent-portal/pull/40), run 40695219672: `AuthConfigIT.eachOriginSeesOnlyItsOwnIssuer` |
| S10 | C7 | `oidc.ts` 밖에서 `fetch` | 로컬: ESLint 네트워크 금지(닫힌 예외 둘 밖) |
| S11 | C6 | 엔진 서비스 토큰을 `docker/.env`에(생성 스크립트가 그 값을 쓴다) | PR [#43](https://github.com/hjryoo-ai/ga-agent-portal/pull/43), run 40697930576: `build` — `DemoComposeFilesTest.composePinsImagesBindsLoopbackAndKeepsSecretsOutOfTheEnvironment`("secret-looking variable … must point at a file"); `compose-e2e` 비밀 스캔 — `compose config` 1·inspect(env) 1·산출물 1 |
| S12 | C9 | 시험 앱 조립에 OTLP 끝점 | PR [#41](https://github.com/hjryoo-ai/ga-agent-portal/pull/41), run 40695230625: `OtlpExportOffIT.noOtlpConnectionOrExporterInTheTestAssembly` |
| S13 | C9 | 모든 스팬에 표 밖 속성 키 `debug.note` | PR [#44](https://github.com/hjryoo-ai/ga-agent-portal/pull/44), run 40697941038: **`build`의 `TracingLeakIT`가 먼저**("attribute keys of ingest.apply ⊆ table"), `compose-e2e`의 Jaeger 시험도 실패 |
| S14 | C8 | `/auth/callback`을 웹 경로 목록에만 | PR [#42](https://github.com/hjryoo-ai/ga-agent-portal/pull/42), run 40695241584: **단위 `PublicPathsTest.noPublicPathIsAnApiOrScreenPath`가 먼저**(`portal-app:test` 44 중 1 — 통합 시험까지 가지 않았다. 계획이 적은 `WebScreensIT.screenPathsEqualTheWebRoutesBothWays`는 이 run에서 돌지 않았다) |
| S15 | C1 | 데모 사본 `cases.json` 한 바이트 | 로컬: `verifyContractChecksums` — "CHECKSUMS 가 계약 파일과 다르다" |
| S16 | C7(조건 B) | 콜백이 `code`를 DOM(`data-code`)에 쓴다 | PR [#45](https://github.com/hjryoo-ai/ga-agent-portal/pull/45), run 40697953908: `build` 통과(Vitest는 못 잡는다), `compose-e2e` 시험 2 "code in the callback DOM" |

## 4. 지시문 §5 추가 항목

### 4.1 데모 스트림 케이스 표 (엔진 `cases.json` ↔ seq·설계사 — `DemoStreamTest`가 seq가 그 설계사·계약을 가리키는지 본다)

| 케이스 | 설계사 | seq |
|---|---|---|
| 한도 80% (`LIMIT_80`) | A-2001 | 12510 |
| 한도 95% (`LIMIT_95`) | A-2003 | 12556 |
| 한도 100% 삭감 (`LIMIT_100_CUT`) | A-2005 | 742 |
| 한도 100% 이연 (`LIMIT_100_DEFER_AFTER_FY`) | A-2007 | 1477 |
| HELD 분급 (`DEFERRAL_HELD`) | A-2009 | 17102 |
| 경계 2026-06-30 한도 미적용 | A-2011 | 455 |
| 경계 2026-07-01 한도 적용 | A-2013 | 456 |
| 경계 2026-12-31 분급 미적용 | A-2015 | 2854 |
| 경계 2027-01-01 분급 적용 | A-2017 | 2861 |
| 재산정 (`REPRICE`) | A-2019 | 2509 |
| 환수 채권 이월 (`RECEIVABLE_CARRIED`) | A-2009 | 1602, 2045 |
| 취소·재분개 짝 (`REVERSAL_REBOOK`) | A-2021 | 1333, 1335 |
| 마감 후 조정 (`ADJUSTMENT_AFTER_CLOSE`) | A-2023 | 1984 |

(증권번호는 센티널이라 표에 싣지 않는다.)

### 4.2 규모 실측·골든·손 검산 (Q1·Q12)

| 항목 | 값 | 출처 |
|---|---|---|
| 이벤트 | 17,856(유형 12 — `CommissionCalculated` 6,950·`DeferralScheduled` 5,136·`LimitLedgerPosted` 3,705·확정·지급 각 686 …) | 엔진 `9963809` 사본 |
| 설계사·계약·달 | 30 · 432 · 716(`earnings_month`) | 골든 요약 |
| 골든 요약(행 수) | `earnings_line` 6,951 · `earnings_month` 716 · `policy_limit` 327 · `policy_limit_history` 3,710 · `deferral_item` 5,136 · `clawback_item` 21 · `policy_flag` 40 · `agent_profile` 0 · `recon_result` 0 — 표마다 정규화 행 SHA-256 | `golden-views/demo/summary.jsonl` |
| `DemoStreamIT`(Testcontainers) | 적재 118,625 ms(시험의 워커 설정) · 운영자 재구축 9,039 ms(한 트랜잭션, 상한 50,000 — §14.1 #11 실측) | run 40697515087 시험 출력 |
| compose 첫 동기화(쪽 1,000·폴링 5초) | 포털 준비 뒤 약 95초, 기동부터 138~149초 | §4.7 |

**손 검산**(A-2001·A-2009 — 전 행이 `golden-views/demo/agents/`에): 포털 `earnings_month`의 `gross`·`clawback_offset`·`adjustment`를 엔진 `StatementConfirmed`의 같은 필드와, `policy_limit.accum_paid`를 그 원장 마지막 `LimitLedgerPosted.accumAfter`와 대조(등호만):

| 설계사 | 확정 달 | 같음 | ESTIMATE 달 | 원장 누계 |
|---|---:|---:|---|---|
| A-2001 | 23 | 23 | 202802 — 엔진 `gross` ≠ 포털 Σ(분급 미리보기 포함 — DD-19, 계약 1.4.1 설명문 그대로) | 23 원장 중 23 같음(두 설계사 합) |
| A-2009 | 23 | 23 | 202802 — 같은 이유 | |

### 4.3 다섯 화면 상태 표 (compose E2E `screens.json` — 화면이 보인 문구·수, 스냅샷 아님, run 40697515087, 설계사 a-2001)

| 화면 | 보인 것 |
|---|---|
| 홈 | 칩 "기준 10-28 22:39", 이번 달 상태 "지급완료", 최근 알림 3 |
| 월별 명세 202608 | 라인 5, "지급완료" |
| 한도 | 원장 카드 2 |
| 분급 타임라인(24개월) | 달 9, 항목 31 |
| 시뮬레이션 | 신계약 결과 룰 스냅샷 `RS-d3be92…`, 해약 결과 `RS-574e39…` = 엔진 샘플 응답 |
| 알림함 | 20 |
| 문의 | 접수 → 상세 "접수됨" |
| 로그아웃 | 로그인 화면, 다시 누르면 Keycloak이 자격 증명을 다시 묻는다(종료 끝점이 세션을 끝냈다) |

화면마다 DOM 누출 0(증권번호 432·잡은 비밀 7), 저장소 6종 0, axe(WCAG 2.0/2.1 A·AA) 위반 0.

### 4.4 PKCE 흐름의 저장 위치 (`src/auth/oidc.ts`)

| 값 | 어디에 | 수명 |
|---|---|---|
| `state` | 본 창 모듈 변수(메모리) | 로그인 한 번 — 채널 이름 `pkce:`+state, 메시지 한 번 받고 닫는다 |
| `code_verifier` | 본 창 클로저(메모리) | 교환 한 번 |
| `code` | 콜백 → `BroadcastChannel` 메시지 → 본 창 메모리. 콜백 문서는 그리지 않고 주소를 바로 지운다 | 교환 한 번 |
| ID 토큰 | **없음**(받지 않는다 — 버린다) | — |
| 액세스 토큰 | `boot/token.ts` 메모리 | 300초, 만료 60초 전 갱신 |
| refresh 토큰 | `oidc.ts` 메모리 | 회전(쓰면 새 값으로 바꾸고 옛 값 버림), 로그아웃 때 종료 끝점에 POST |
| 브라우저 저장소·쿠키·IndexedDB·Cache·`window.name` | **없음** | ESLint·Vitest·compose E2E |

### 4.5 CSP 최종 헤더 (`ScreenConfiguration.policy`)

등록 없는 출처(또는 `/auth/config` 404 출처):

```
default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'
```

등록 출처(데모: `http://localhost:8080` → 발급자 출처 `http://localhost:8180`) — `connect-src` 하나만 다르다:

```
default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; font-src 'self'; connect-src 'self' http://localhost:8180; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'
```

### 4.6 k6 (perf run [40694537717](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/40694537717), `26e04ed` — 앱 코드는 `27e544f`와 같다)

GitHub 공유 러너(compose 전체 + k6가 같은 기계), 설계사 30명 동시(VU 30), 3분, 홈 다섯 콜을 한 묶음으로(`http.batch`). 묶음 49,627, 실패 0.

| 콜 | p50 ms | p95 ms | p99 ms | 최대 ms | 예시값 p95 ≤ 300 |
|---|---:|---:|---:|---:|---|
| `/me` | 54.0 | 136.5 | 190.3 | 577.9 | 통과 |
| `/me/earnings/{이번 달}` | 56.9 | 142.2 | 198.2 | 639.6 | 통과 |
| `/me/limits?status=ALL` | 59.0 | 143.9 | 199.9 | 505.5 | 통과 |
| `/me/deferrals` | 57.9 | 143.1 | 202.2 | 660.6 | 통과 |
| `/me/notifications?size=3` | 56.3 | 142.4 | 198.8 | 578.5 | 통과 |
| **홈 묶음** | 101 | **187** | 249 | 705 | 예시값 ≤ 800 통과 |

ETag 표식 문장(P7-15 — 콜마다 +1 문장, Phase 7 §4.9 문장 수 표): `/me`의 p95(136.5)는 다른 네 콜(142~144)과 같은 범위다 — 이 규모(30명·러너 한 대)에서 표식 문장의 영향은 보이지 않는다(같은 run 안 A/B는 하지 않았다 — 계획 C10).

### 4.7 compose 기동 시간·이미지 digest

| run | 포털 준비(healthy) | 등록(seed 0) | 첫 동기화(seq 17,856) |
|---|---:|---:|---:|
| 40693176741(`b8e5fac`) | 46.8 s | 46.9 s | 138.8 s |
| 40694514233(`26e04ed`) | 51.1 s | 51.2 s | 145.5 s |
| 40696153752(`bc05513`) | 49.0 s | 49.1 s | 149.2 s |
| **40697515087(`27e544f`)** | 51.0 s | 51.1 s | 146.3 s |
| perf 40694537717 | 49 s | 49 s | 143 s(이미지 빌드 140 s 별도) |

| 서비스 | 이미지(태그는 주석) | digest |
|---|---|---|
| secrets | busybox 1.38.0 | `sha256:fd7dc98638c8e305f4dc34e979f1c0fdfdcaeb0fbf8fcff77ae834b6da3d7e6e` |
| postgres·seed | postgres 18.6(하네스와 같은 카탈로그 값) | `sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336` |
| keycloak | quay.io/keycloak/keycloak 26.8.1 | `sha256:a78d6b79572168cb25886b80a8b5b8d4ed4390743e3eecb810538683e209dd6b` |
| jaeger | jaegertracing/jaeger 2.22.0 | `sha256:836b967b5abcf1f7758066aa6b348619267a2f1459f72d472840570f9bd88a17` |
| idp-relay | alpine/socat 1.8.1.3 | `sha256:603174b77eebaba25ba8f8ebb15e1234d6600409554e95d6fb203fd106150287` |
| portal·demo(빌드) | eclipse-temurin 25.0.4.1_1-jdk-noble | `sha256:a9c5ddd386f629518bd500ecfabf7d1e5d32d7ffed735d5c3b1604ae92c830f4` |
| portal·demo(실행) | eclipse-temurin 25.0.4.1_1-jre-noble | `sha256:ce8543ddd3070ccbeaac2106a74c9387ddeb8d47a1c43348345715c1ef245c7f` |
| (perf) k6 | grafana/k6 2.3.0 | `sha256:2dd4756ec6e14e62778916e1eb850108e4b499304c02df56760fa0bdb0fba0b8` |
| (빌드 입력) | ga-disclosure `platform-v0.2.0` 소스 묶음 | `sha256:b7811d0d600fb0cad22e6ec254b0b0047cd88152a3776f6a951cdaae6760918c`(`ADD --checksum`) |

### 4.8 새 의존성·검증 메타데이터 (`d3d7c49` — 빈 `GRADLE_USER_HOME`에서 재생성, 둘째 빈 홈 빌드 통과)

직접: `io.opentelemetry:opentelemetry-exporter-otlp`, `io.micrometer:micrometer-registry-prometheus`(둘 다 Boot 4.1.1 BOM 판). `portal-app/gradle.lockfile` **+18줄, 제거 0**: 

| 전이 포함 아티팩트 | 판 |
|---|---|
| `io.opentelemetry:opentelemetry-exporter-otlp`·`-otlp-common`·`-common`·`-sender-okhttp`, `opentelemetry-sdk-extension-autoconfigure-spi` | 1.62.0 |
| `io.micrometer:micrometer-registry-prometheus` | 1.17.1 |
| `io.prometheus:prometheus-metrics-config`·`-core`·`-exposition-formats`·`-exposition-textformats`·`-model`·`-tracer-common` | 1.7.0 |
| `com.squareup.okhttp3:okhttp`·`okhttp-jvm` | 5.3.2 |
| `com.squareup.okio:okio`·`okio-jvm` | 3.16.4 |
| `org.jetbrains.kotlin:kotlin-stdlib` · `org.jetbrains:annotations` | 2.3.21 · 13.0 |

`gradle/verification-metadata.xml` +183줄(구성 요소 +24, 제거 0) + Node linux-arm64 묶음 한 줄(이미지 빌드 — P8-7). 웹 의존성 변경 0.

### 4.9 compose E2E·운영 CLI (run 40697515087)

- `recon run --tenant=DEMO --ym=202608,202609`: `DEMO recon OK checked=60 matched=60 drift_new=0 drift_unresolved=0 inconclusive=0 engine_calls=60`(17.8 s — CLI 기동 포함). 데모 스트림은 엔진이 낸 것이라 DRIFT가 없다 — 감지·복구의 증거는 하네스 E2E ⓐ~ⓔ′(계획 Q4).
- `status --tenant=DEMO`: `drift_unresolved=0`.
- `ingest rebuild --tenant=DEMO`: `DEMO RUNNING at seq 17856 (rebuilt 17856 events)`(20.1 s), 다섯 응답 전후 바이트 같음(`asOf.at` 하나 제외 — P8-15).
- Jaeger: 스팬 5,924, 이름 24(앱 표 11 중 9 — `engine.*` 셋·`ingest.*` 넷·`recon.*` 둘, `http <메서드> <틀>` 15), 속성 키 13, 자원 키 4.

### 4.10 번들

초기 125,851 B gzip(상한 130,048), 지연 경로 10. Phase 7 대비 +2,239 B(PKCE·로그인 화면 — 초기 청크 안).

### 4.11 화면·계약 변경

계약 `portal-api` 1.3.0(`/auth/config` 추가만 — `PortalApiCompatibilityTest.version130IsAdditiveOver100To120`), 이벤트 계약 1.4.1(표기만). V10(`tenant.web_origin`·`oidc_client_id`). 화면: 로그인·콜백·로그아웃(그 밖 화면 원천 무변경).

### 4.12 `RESULT.md` (⑦·Q6)

`e2e/engine-feed/run.sh`(실 엔진 + Oracle — compose `engine` 프로파일은 v2, §14.2)는 사용자 몫이다 — 이 Phase에서 **실행하지 않았다**. 판정 다섯(Phase 7까지와 같다)의 결과 칸은 비어 있다.

### 4.13 CI run 목록 (이 Phase — 직접 조회)

| run | 커밋 | 결과 | 비고 |
|---|---|---|---|
| 40685297729·40686165421·40686776209 | `b1e42c4`·`0e8ccba`·`0223223` | failure | F11 시험 데이터(같은 키 행) — `c2597b5`(P8-6) |
| 40689755410 | `87c0c31` | success | |
| 40691820243 | `d3d7c49` | success | |
| 40693176741 | `b8e5fac` | failure → 취소 | compose E2E 첫 run — `page.goto`가 세션을 버린다(P8-16), 비밀 파일 수·포트 검사 |
| 40693643473 | `5a37359` | 취소 | 다음 커밋이 대신 |
| 40694514233 | `26e04ed` | failure → 취소 | 타임라인 기간, 분급 조회 범위, Jaeger 404(P8-14) |
| 40694537717 | `26e04ed`(perf) | success | k6 §4.6 |
| 40696153752 | `bc05513` | failure → 취소 | 재구축 비교 `asOf.at`(P8-15) |
| **40697515087** | `27e544f` | **success**(넷 다) | 1차 증거 |
| 40695191424 ~ 40697968802 | 주입 PR #37~#46 | failure(의도) | §3 |

### 4.14 문서·영문 동기화 범위 (C12, Q8)

- README: 상태(Phase 8), 데모 절(명령·계정·비밀·데모 날짜 P-20·타임라인 기간·시뮬레이션 입력 표 P-24), **포트폴리오 관점**(부록 C 다섯), **AI 협업 방법론**(절차·주입 규약·DE/DD/RM 수·Phase 0~8 표 — 태그·PR·병합 커밋·보고서 run·심사).
- 설계서 v1.11: §3.2 데모 구성, §6.7 재구축 끝 불변식, §7 계약 1.3.0, §9 브라우저 로그인·CSP, §11 관측·빌드, §12 전 Phase 완료(Phase 9 → §14.2), §13 성능(예시값)·데모·통합 행, §14.1 열린 것만(#2·#3·#5·#6·#8·#11) / §14.2 v2 표 / §14.3 옮긴 항목 원문(번호 유지 — 코드 표식 #2·#5·#9 그대로), 부록 B.
- `docs/운영.md` §9(compose·비밀·Keycloak·CLI·관측·데모 날짜), `docs/지표.md` §4.1, `CLAUDE.md` "데모·배포 규약", `docs/DECISIONS.md` 맨 앞 색인(DE 30·DD 21·RM 4·P-결정 Phase별).
- **영문**(`9c63e80`, 한국어 `c3ac609`의 번역): `README.en.md`, `docs/en/design.md`(v1.6 → v1.11 — 변경 이력 19판 전부), `docs/en/operations.md`(README의 영문 목록에 있어 함께 — 계획 Q8 목록 밖 하나 더), `docs/en/authz-matrix.md`, `e2e/engine-feed/README.en.md`. 기계가 읽는 펜스(`public-paths`·`authz-matrix`)는 한국어와 바이트 같다. 이 보고서·DECISIONS·지시문·계획은 번역하지 않는다.

### 4.15 Phase 0~8 총괄

| Phase | 범위 | Java 시험 | Vitest | Playwright | 주입(ID) | DE | DD | RM |
|---|---|---:|---:|---:|---:|---|---|---|
| 0 | 골격·격리 | 2,266 | — | — | 29 | 1~6 | 1~3 | — |
| 1 | 인제스트 | 2,414 | — | — | 12 | 7~8 | 4~7 | — |
| 2 | 프로젝션·재구축 | 2,504 | — | — | 14(+수정 3) | 9~11 | 8~11 | 1 |
| 3 | 조회 API·인가 | 2,655 | — | — | 17 | 12~19 | 12~13 | 2 |
| 4 | 화면 1차 | 2,660 | 70 | 25 | 30 | 20~21 | 14~16 | 3 |
| 5 | 타임라인·시뮬레이션 | 2,721 | 98 | 52 | 20 | 22~24 | 17 | — |
| 6 | 알림·문의 | 3,353 | 135 | 67 | 15 | 25~26 | 18 | 4 |
| 7 | 대사·관측 | 5,251 | 135 | 82 | 13(+다시 2) | 27~29 | 19~20 | — |
| **8** | 데모·PKCE·관측 스택·k6 | **5,308** | **155** | **85 + compose 4** | **17** | 30 | 21 | — |
| 합 | | | | | **167** | **30** | **21** | **4** |

P-결정(구현에서 계획과 다르게 한 것): Phase 1 4 · 2 12 · 3 10 · 4 12 · 5 17 · 6 18 · 7 17 · 8 17 = 107.

### 4.16 남은 v2 목록

설계서 §14.2 표 그대로(14): 조직장 뷰, 정산 담당자 콘솔, PWA·오프라인 스냅샷, 푸시·알림톡·다이제스트, PDF 명세, `/me/limits` 페이지, 온디맨드 잠정 명세, 엔진 아웃박스 보존·스냅샷 부트스트랩, subject 재매핑 절차, 예상 이월 채권 표시(V11 후보), `portal_health` 롤, compose `engine` 프로파일, 데모 스트림 재생성(P-20), 엔진 상품 마스터 API. 열린 미결정은 §14.1의 여섯(#2·#3·#5·#6·#8·#11).

## 5. 계획과 다르게 한 것·해석·발견

계획과 다르게 한 것은 `docs/DECISIONS.md` "Phase 8 구현에서 계획과 다르게 한 것" P8-1~P8-17. 요지: P8-1~P8-5 로그인·`/auth/config` 세부, P8-6 F11 시험 데이터, P8-7 Node linux-arm64 메타데이터, P8-8 관리 포트 전용 체인, P8-9 쪽 1,000·폴링 5초, P8-10 프레임워크 관측 둘 끔·높은 기수 키 제거, P8-11 `-f docker/compose.yaml`, P8-12 콜백 카나리 시험, P8-13 `perf/**` 가지로 측정, **P8-14** Jaeger v3·`collector-span-keys` 삭제, **P8-15** 재구축 비교에서 `asOf.at`만 제외, P8-16 화면 안 이동·타임라인 24개월, **P8-17** compose E2E에서 회전 없음.

| # | 발견·해석 | 처리 | 분류 |
|---|---|---|---|
| F1 | F11 시험이 같은 (시각, 출처)에 두 행을 넣어 `recon_result` 키와 부딪혔다 | 시험 데이터(`c2597b5` — P8-6). 제품 코드 무변경 | 기록 |
| F2 | 계획 Q1 "첫 동기화 약 90초"는 추정이었다 — CI 실측 138~149초(포털 준비 뒤 약 95초) | 문서 고침(`c3ac609`), P8-9 그대로 | 기록 |
| F3 | Jaeger 2.22에는 내부 API `/api/traces`가 없다(404). 질의 API v3에서는 범위·종류·상태가 속성이 아니다 — 계획 P-21이 예상한 수집기 키 목록은 쓰이지 않았다 | v3로, 목록 삭제 + 남은 목록은 전부 관측돼야(P8-14) | **발견·수정** |
| F4 | 데모 스트림의 첫 분급 도래가 2028-01이라, 2026-10 기준 타임라인 기본 기간(12개월)이 비어 있다 — "다섯 화면 데이터"는 기간을 고르면 성립 | README 한 줄·E2E 24개월(P8-16). 데모 시계는 두지 않는다(P-20, §14.2) | 기록 |
| F5 | 주입 S14는 단위 `PublicPathsTest`가 먼저 잡아 계획이 적은 `WebScreensIT` 시험까지 가지 않았다. S13은 `TracingLeakIT`가 먼저, S5′는 Vitest가 먼저 | 먼저 잡은 쪽 기록(계획 규칙). 각 주입의 compose 쪽 실패도 run에 있다(S13·S5′) | 기록 |
| F6 | 커밋 보안 검토가 S5′ 주입 커밋을 짚었다 | 버릴 PR의 의도된 위반 — 병합 0, 가지 삭제 | 기록 |
| F7 | 운영 CLI를 `compose exec`로 부르면 그 JVM의 로그도 표준 출력에 섞인다 — `operations.json`에 기동 로그가 실린다(비밀·증권번호 0 — 산출물 스캔) | 정규식이 결과 줄만 고른다. 고치지 않았다 | 기록 |

## 6. 다음 질문 (v2 — 마지막 Phase이므로 Phase 9는 없다)

1. **`v1.0` 태그**: 이 PR의 병합 커밋에 사용자가 단다(Q9). `phase-8`은 이 보고서 커밋.
2. **`run.sh` → `RESULT.md`**: 사용자 몫(Q6) — 실행하면 판정 다섯의 결과를 `RESULT.md`에.
3. **v2 순서**: §14.2 표에서 무엇을 먼저 할지 — 조직장 뷰(#9 — 조직 정본 결정이 먼저)와 compose `engine` 프로파일이 가장 큰 둘.
4. **데모 날짜**(P-20): 2028-02 뒤에는 홈의 이번 달이 비고 타임라인 기본 기간도 비어 있다 — 엔진 `DemoScenario`의 창을 실제 날짜 기준으로 다시 만들지(엔진 PR), 지금 창을 그대로 둘지.
