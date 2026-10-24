# Phase 6 보고서 — ga-agent-portal 알림 파생·이의제기 (지시문 v1.0)

> 지시문 `docs/phase-06-지시문.md` · 계획 `docs/phase-06-계획.md`(승인 2026-10-24 — Q1~Q30·P-1~P-23 권장안 전부 + 추가 지시 A, `docs/phase-06-계획승인.md`) ·
> 브랜치 `work/phase-6` · PR [#20](https://github.com/hjryoo-ai/ga-agent-portal/pull/20) · 태그 `phase-6`(이 보고서 커밋). **병합은 수용 심사 회신 뒤.**
> 엔진 변경 없음. `run.sh`(실 엔진 E2E)는 사용자 몫이다 — 이 Phase는 `RESULT.md` 판정에 넷째 "알림 행 수 > 0"만 더했다(§4.10).

이 보고서의 **"주입 S…"**는 계획 ⑩의 주입 ID다. **"E…"**는 계획 ⑨의 E2E 시나리오다.

## 1. 커밋

`origin/main` = `3a24ce6`(Phase 5.1 병합) 위 `work/phase-6`:

| 커밋 | 내용 | 주입 |
|---|---|---|
| `2cfedc2` | 지시문·계획(Q1~Q30, P-1~P-23) | — |
| `86b9ff2` | 계획 승인 보관 + 계획 머리의 승인 줄(추가 지시 A, DE-25·DE-26·DD-18, DE-24 문구 정정) | — |
| `bf24fa9` | 1단계 — `LimitStage`·`PolicyMask`·`RateLimitRule`을 `portal-domain` `com.ga.portal.domain.rule`로(Q12), 곱셈 허용 목록 FQN 갱신(폐기 항목 실패 1회 기록 — §4.11) | — |
| `68ac939` | 2단계 — V7(`notification` `params`·`type`·`link` `CHECK`·`title`/`body` NULL만·인덱스, `inquiry` `category`·길이·답변 열 `CHECK`·인덱스, 빈 표 가정 머리 주석 — P-8), 열거 넷, `StatusEnumDdlTest`·`SeedData` | — |
| `5419f04` | 3단계 — **계약 1.2.0**(경로 다섯, 1.1.1 바이트 사본·`CHECKSUMS`·`client:lock`), `portal-inbox`, `InboxController`, 인가 표·매트릭스 다섯 행, 문의 리밋, 지표 `route` 태그, 통합 시험 넷 + 기존 시험 확장, 설계서 §7·§9·§10 | S1·S6·S9 |
| `a1bca5f` | 3단계 CI 수정(run 40088726404 — 새 시험의 기대값 셋, 구현 무변경) | — |
| `91cf3dc` | 4단계 — 파생 규칙 `NotificationRules`, sealed `Change`, 프로젝터 반환, `NotificationRulesTest`·`NotificationStreamsTest`, 설계서 §6.5 최종 표 | S2·S3 |
| `981fa19` | 5단계 — `JdbcNotificationDeriver`(적용 트랜잭션 안 — RM-4), `IngestApplier` 예외 자리, `NoopNotificationDeriver`를 시험 픽스처로, ArchUnit 재구축 전이 의존 0, `NotificationDerivationIT`·`RebuildIT` 확장, 설계서 §6.5·§6.7 | S4·S5 |
| `4c3f4e5` | 6단계 — 단일 CLI 실행기 `CliRunner`, `inquiry list|answer|close`, `AnswerFile`, `InquiryOperatorRepository`, `InquiryCliIT`, 설계서 §6.1, `docs/운영.md` §7 | S11·S12 |
| `124242b` | 5단계 CI 수정(run 40089787668 — 시험 덤프의 `concat_ws`가 NULL 열을 버렸다) | — |
| `780d274` | CLI 출력의 제어·서식 문자 이스케이프(커밋 보안 검토 — CWE-150, P6-8) | — |
| `fc0742e` | 7단계 — 화면 `/notifications`·`/inquiries`·`/inquiries/new`·`/inquiries/:id`, 홈 최근 알림·안읽음 수, 라인·월 진입점, `link` 대조(승인 A), 템플릿 열넷, 설계서 §8 | S13·S15 |
| `32032ea` | 8단계 — E2E: GA05 손 스트림, 하네스 OPERATOR 링크 + CLI 제어 둘, 문의 본문 센티널, E1~E7, `EngineFeedE2eCheck` 넷째 판정, `EnvelopeValidatorTest` 손 스트림 검증, 같은 원장 재상향 IT | (S7·S8·S10·S14 — CI) |
| `45a32d4` | C7 거부 본문에 따옴표 없는 토큰(P6-16 — 주입 S8을 시험이 못 잡았을 것을 미리 찾음) | S8 |
| `d4417fd` | 첫 E2E(run 40091780371)가 잡은 문의 내역 44px(P6-17) | — |
| `c90a98f` | **주입이 찾은 시험의 구멍 둘**(F5·F12) — 맨 토큰 센티널을 ASCII로 시작하게(`Qv75문의센티널`), E5가 상세에서 앱 안 이동으로 나간 화면도 검사, DECISIONS P6-16 정정·P6-18 | S8·S14 |
| `40955f8` | 9단계 — 설계서 v1.9 머리·§3.3·§12·§13·§14.1, `CLAUDE.md` "알림·문의 규약", DECISIONS P6-1~17, README | — |
| (이 커밋) | 보고서 | — |

```
contracts       api/v1/portal-api.openapi.yaml 1.2.0 (+ baselines/1.1.1 바이트 사본) · CHECKSUMS
portal-domain   rule/ LimitStage·PolicyMask·RateLimitRule·Paging · notify/ NotificationParams·NotificationShapes·NotificationRecord
                NotificationType·Severity·InquiryStatus·InquiryCategory·InquiryTransitions·InquiryRecord · access/ApiAuthorization(+5)
portal-projection  Change(sealed 6) · ProjectionResult.change · notify/ NotificationRules·Candidate·Thresholds · testFixtures/NoopNotificationDeriver
portal-inbox    (새 모듈) InboxUseCases(진입 다섯) · InquiryRequests · InboxContracts · api/ 레코드 · port/ InboxStore·InboxTransactions·InboxMetrics
portal-ingest   apply/IngestApplier(파생 자리 — APPLY_ERROR) · Rebuilder(무변경 — 파생 0)
portal-infra    V7__inbox.sql · NotificationRepository · InquiryRepository · InquiryOperatorRepository
portal-app      web/InboxController · inbox/ InboxConfiguration·JdbcInboxStore·MicrometerInboxMetrics·NotificationParamsJson
                ingest/JdbcNotificationDeriver · cli/ CliRunner·InquiryCommands·AnswerFile·CliConfiguration·OperatorAuthorization
                integrationTest/ Notification*IT·Inquiry*IT·InboxIntegrationTest·WebE2eHarness(GA05·CLI 제어) · EngineFeedE2eCheck(④)
web             screens/ Notifications·Inquiries·InquiryNew·Inquiry · notify/ links·text·NotificationItem · messages.ko.json(+템플릿 14)
                e2e/specs/ ga01-inquiry·ga05-limits(+ga01-a1 E1·ga04-edges E2) · streams/ga05-limits.jsonl · support/inbox.ts·sentinels(inquiry)
```

## 2. 테스트 (CI 출처)

CI run [40094460151](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/40094460151)(`c90a98f` — 마지막 코드·시험 커밋, build·no-docker·web-e2e 모두 success). 수는 그 run의 아티팩트(`test-reports` — 결과 XML·Vitest junit, `web-e2e-reports` — junit·요약·누출·axe·산출물 스캔)를 내려받아 셌다. 기준선은 `main` = `3a24ce6`(CI run [40031300678](https://github.com/hjryoo-ai/ga-agent-portal/actions/runs/40031300678): Java 2,723·Vitest 98·E2E 52 — 같은 방법으로 셌다). `d4417fd`(run 40092522429)도 같은 수로 전부 success였다 — `c90a98f`는 시험 둘만 고쳤다(건수 무변경).

| 모듈 | 소스셋 | 건수 | `main` 대비 | 비고 |
|---|---|---:|---:|---|
| `portal-domain` | test | 2,106 | +22 | `NotificationShapesTest` 10, `InquiryTransitionsTest` 3, `RateLimitRuleTest` 3, `PagingTest` 1 + 옮겨 온 `LimitStageTest` 3·`PolicyMaskTest` 2 |
| `portal-projection` | test | 662 | +527 | `NotificationRulesTest` 524(표의 칸·단계 16쌍·시드 고정 전기열), `NotificationStreamsTest` 3(A-1·`edges`·`ga05-limits`) |
| `portal-ingest` | test | 84 | +4 | `IngestApplierTest` +2(호출 순서·`APPLY_ERROR`), `RebuilderTest` +1(파생 0), `EnvelopeValidatorTest` +1(손 스트림 셋) |
| `portal-query` | test | 42 | −5 | `LimitStageTest`·`PolicyMaskTest`가 `portal-domain`으로(Q12). `PortalApiCompatibilityTest`·`PortalApiContractTest`는 같은 메서드 안에서 1.2.0 |
| `portal-sim` | test | 15 | −2 | `RateLimitRuleTest`가 `portal-domain`으로 |
| `portal-inbox` | test | 28 | +28 | `InquiryRequestsTest` 18, `InboxUseCasesTest` 6, `InboxApiContractTest` 4 |
| `portal-infra` | test | 10 | 0 | `StatusEnumDdlTest`·`TenantContextGuardTest`는 같은 메서드 안에서 |
| `portal-infra` | integrationTest | 194 | +22 | `InboxSchemaIT` 22(V7 `CHECK` — 닫힌 `type`·`link` 형식·죽은 `title`/`body`·문의 상태별 열·답한 이 `cli:` 형식, 매개변수 거부 사례) |
| `portal-app` | test | 27 | +5 | `CliRunnerTest` 8(옮겨 다시 씀 — 옛 `IngestCliRunnerTest` 3 삭제), `AuthzMatrixTest`는 같은 메서드 안에서(`@PatchMapping`) |
| `portal-app` | integrationTest | 113 | +26 | `InquiryApiIT` 6, `NotificationApiIT` 5, `NotificationDerivationIT` 4, `InquiryIsolationIT` 3, `InquiryRateLimitIT` 2, `InquiryCliIT` 2, `UniformRejectionIT` +2, `ResponseLeakScanIT` +1, `RebuildIT` +1. `QueryStatementIT`·`PortalApplicationIT`·`ApiContract`는 같은 메서드 안에서 |
| `portal-app` | archTest | 72 | +3 | `ArchitectureRulesTest` +1(재구축 전이 의존 0), `ApiLayerRulesTest` +1(규칙 ⓖ), `TenantPredicateScanTest` +1(알림·문의 설계사 술어) |
| **Java 합계** | | **3,353** | **+630** | 실패 0, 스킵 0 |
| `web` | Vitest | 135 | +37 | `links` 23(승인 A), `phase6` 11, `messages` +3(13 → 16 — 템플릿 양방향·자리·문의 라벨). `screens`는 홈 기대값만 고쳤다(10 그대로) |
| `web` | Playwright | 67 | +15 | 새 시험 5 × 3 프로젝트(ga01-inquiry 1·ga05-limits 3·ga04-edges +1) + 기존 ga01-a1 안의 E1 단계 — 프로젝트별 22·22·23, 스킵 0 |

- 같은 run의 빌드 검사: `client lock ok (openapi-typescript@7.13.0, contract be7f09c3a915)`, `dist scan ok (22 files, 17 app chunks, 28 money properties)`, `bundle size ok: initial 123612 B gzip (cap 130048), 10 lazy routes`, `scanPlaintextLeaks: 134 files, 130 forbidden strings, 0 hits`(Phase 5: 76 — `inbox`·`inquiry` 키), `verifyContractChecksums: contracts/CHECKSUMS and contracts/shared/{ga-commission-engine,ga-disclosure}/CHECKSUMS match`, `verifyPlatformPins: 13 com.ga.platform:0.2.0 files pinned, equal to the handoff §6 table`, `verifyTestInputs: 10 test tasks read repository files, all declared`(+`:portal-inbox:test`), `verifyBootJarScreens: portal-app-0.1.0.jar ⊃ web-0.1.0.jar ⊃ portal-web/index.html + 19 scripts`, `licenses ok (257 packages, 7 bundled, 8 dev-only exceptions)`(새 npm 의존성 0), `verifySpringFree` 여섯(`portal-inbox` 추가), `files containing the token: 0`.
- `no-docker` 잡: "gradle exit code: 1", "result files: 11, with failures/errors: 11, skipped: 0"(`InboxSchemaIT` 하나 늘었다), "scanPlaintextLeaks: 11 files, 130 forbidden strings, 0 hits", "token occurrences: 0".
- `web-e2e` 잡: `summary.json` — tests 67·passed 67·skipped 0, 누출·axe 파일 각 67, 응답 585·DOM 208·콘솔 줄 99·주소 501·요청 1,862, CSP 위반 0, 외부 요청 0, `simIds` 27, **`inquiryBodies` 6**(허용된 상세 응답 중 본문 센티널이 실제로 실린 응답 수 — 프로젝트마다 E5 한 시험의 둘: 접수 뒤 상세·답변 뒤 상세), axe 상태 144·위반 0·미완 0. `artifact-scan.json` = 금지 문자열 57(센티널 — 문의 본문 셋 포함) + 토큰 45, hits 0.

## 3. 완료 기준 증거

| # | 기준 | 증거 | 주입 |
|---|---|---|---|
| C1 | 계약 1.2.0 | `PortalApiCompatibilityTest`(1.0.0·1.1.0·1.1.1 → 1.2.0 추가·완화만), `PortalApiContractTest`(경로 = 인가 표 키, 스키마 ↔ 레코드, `format` 0, 닫힌 객체), `InboxApiContractTest` 4(`params` 여섯 변이 ↔ `NotificationParams`, 응답 레코드 ↔ 스키마, `Won` 참조 0, `ScreenLink` 정규식 = V7 `CHECK`), 모든 통합 시험 응답의 계약 검증(`ApiContract` — 방법 + 경로) | S1 |
| C2 | 경계 상향 1회 | `NotificationRulesTest` 524(16쌍 전수 — 상향만 1건·후 단계, 두 경계 1건, 시드 전기열의 신탁), `NotificationStreamsTest` 3, `NotificationDerivationIT` 4(재시작 바이트 동일·재폴링 0 추가·같은 원장 재상향 새 행·같은 단계 0), E1·E3 | S2·S3 |
| C3 | 재구축 파생 0 | `RebuildIT.aRebuildNeitherDerivesNorDeletesNotificationsOrInquiries`(전후 행 바이트), `RebuilderTest.aRebuildDerivesNothing`, `ArchitectureRulesTest` "the rebuilder never derives notifications, not even transitively", E4(실 CLI 재구축 전후 응답 동일) | S4 |
| C4 | 파생 = 적용 트랜잭션 | `NotificationDerivationIT.aDerivationFailureStopsTheTenantWithAnApplyErrorAndRollsBackTheWholeEvent`(seq 5 — 행을 넣은 뒤 예외: `APPLY_ERROR`, `FAILED` 행 없음, 그 seq의 뷰·알림·`ingest_event` 0, 커서 4, 뷰는 4건 기준 테넌트와 같다), `IngestApplierTest` 2 | S5 |
| C5 | 응답에 원문·키 없음 | `ResponseLeakScanIT` +1(알림·문의 크롤 ∩ 증권번호·남의 ID·`dedup_key` 형식 = ∅), `NotificationApiIT`(`dedupKey`·`agentId`·`title`·`body` 키 0), 계약 `additionalProperties: false`, `NotificationStreamsTest`(후보의 키·`params`·`link` ∩ 스트림 증권번호 = ∅) | S6 |
| C6 | 본인 것만 | `NotificationApiIT` 5(최신순·변이 모양·남의/없는/형식 오류/대문자 id → 같은 404 + `AUTHZ_DENIED MISSING` 넷·남의 행 그대로·`PATCH` 멱등·ETag 304 → 읽으면 바뀜·`MANAGER`/운영자 `ROLE`), `InquiryApiIT` 6, `UniformRejectionIT` +2(쿠키만 → 401 같은 바이트 — GET·PATCH·POST, 방법 불일치 404, 문의 429 바이트 상수), `AuthzMatrixTest`(`@PatchMapping`) | S7 |
| C7 | 본문 격리 | `InquiryIsolationIT` 3(상세에만 — 접수 응답·목록·알림·남의 상세·감사·지표 태그·로그 캡처 0; 4,096바이트 201·4,097 400; 1,000 코드 포인트(UTF-16 2,000) 201·1,001 400; 거부 본문 일곱 — 깨진 JSON·중복 키·분류 자리·모르는 키·U+0000·짝 없는 서로게이트·**따옴표 없는 토큰** — 400 고정 바이트·로그 0), `InquiryCliIT`(CLI 출력·감사 — §4.4), `InquiryRequestsTest` 18, E5·E7 | S8·S9·S14 |
| C8 | 문의 리밋 | `InquiryRateLimitIT` 2 — 순차 N(3) 201·넷째 429 + `Retry-After`·행·감사 0·지표 +1; 동시 8(N+5): `INQUIRY_RATE_LIMIT_CONCURRENCY threads=8 limit=3 admitted=3 limited=5 statuses=[201, 429, 429, 201, 201, 429, 429, 429]`, 그 설계사의 행 3. E6 | S10 |
| C9 | CLI 한 트랜잭션 | `InquiryCliIT` 2(목록·답변·닫기 — 한 줄씩, 답변을 되풀이하지 않는다, 감사 셋, 알림 `INQUIRY_ANSWERED` 한 행; 알림 저장소가 던지면 문의는 `OPEN` 그대로·알림 0·감사 0), `CliRunnerTest` 8(`--answer` 64·`--actor` 64·모르는 옵션 64·파일 규칙), E5(실 CLI 자식 프로세스) | S11·S12 |
| C10 | 화면 | `messages.test`(템플릿 ↔ `NotificationType` 양방향·자리 ↔ `params` 키), `phase6.test.tsx` 11(문장·안읽음 수 = `unreadCount`·남은 글자 수 코드 포인트·`ComingSoon` 0·429 문구·고정 문구), `links.test.ts` 23(승인 A — 맞는 넷·틀린 셋 + 그 밖의 모양), ESLint `portal/no-hangul-literal`·`no-money-arithmetic` 그대로 | S13·S15 |
| C11 | E2E | `web-e2e` 67/67(§2), E1~E7(§4.7), axe 0·CSP 0·누출 0·번들 123,612 B ≤ 130,048·새 npm 의존성 0 | S14 |
| C12 | 무손상·스캔·메타데이터 | §2(기존 2,723건 전부 통과 — 옮긴 셋 포함), `no-docker` 11/11 실패·스킵 0, 평문 스캔 130 문자열 0, 의존성은 3단계에서 `portal-inbox` 잠금 하나 — 빈 `GRADLE_USER_HOME`에서 재생성 diff 0(§4.9), 아래 주입 표, `run.sh` 무변경(`EngineFeedE2eCheck` 판정 ④만) | — |

### 주입 기록 (ID 단위 — 15)

각각 단독으로 넣고 해당 검사를 돌려 실패를 본 뒤 되돌렸다. 단위·정적 시험이 잡는 것은 이 기계에서(`./gradlew --continue …` — 실패 클래스는 결과 XML에서), 통합·E2E만 잡는 것(S7·S8·S10·S11·S14)은 Docker를 켜지 않았으므로 **주입 한 커밋의 초안 PR**(밑 `work/phase-6`)로 CI를 돌리고, 결과를 본 뒤 병합 없이 닫고 가지를 지웠다. 첫 판(PR #21~#25, `d4417fd` 위)에서 **S8·S14는 CI를 통과했다** — 시험의 구멍 둘(F5·F12)을 `c90a98f`에서 막고, 그 위에서 다시 넣어(PR #26·#27) 실패를 봤다. 정적 스캔이 먼저 잡는 모양은 피했다(S10 — 아래).

| ID | 기준 | 주입 | 실패한 곳 |
|---|---|---|---|
| S1 | C1 | `NotificationParams.Ledger`에 성분 `stageName`(보조 생성자로 호출부 그대로) | `InboxApiContractTest` 1 — "`NotificationParams$Ledger`: schema [ledgerId, policyMasked] vs record [ledgerId, policyMasked, stageName]" |
| S2 | C2 | 상향 판정 `after > before` → `after ≥ before`(OK 제외) — 같은 단계 재알림 | `NotificationRulesTest` 179, `NotificationStreamsTest` 1 |
| S3 | C2 | 한도 단계 키에서 `:{seq}` 제거 — 재상향이 같은 키 | `NotificationRulesTest` 214, `NotificationStreamsTest` 1(GA05 둘째 `LIMIT_WARN` 키) |
| S4 | C3 | `Rebuilder.replayOne`이 `NotificationRules.candidates`를 부른다(행은 넣지 않는다) | `ArchitectureRulesTest` 1 — "Rule 'the rebuilder never derives notifications, not even transitively' was violated (2 times)" |
| S5 | C4 | 파생을 적용 트랜잭션이 커밋된 **뒤** 별도 트랜잭션으로 | `IngestApplierTest` 2(`theDeriverRunsAfterTheProjectorWithItsResultAndBeforeTheCursorMoves`, `aDerivationFailureIsAnApplyErrorWithoutAFailedRow` — 커서가 5까지 갔다). 단위 시험이 먼저 잡아 CI는 통합 시험에 닿지 않는다(F4) |
| S6 | C5 | `NotificationParams.Line`에 `policyNo` | `InboxApiContractTest` 1 — "`$Line`: schema [calcId, closeYm, policyMasked] vs record [… policyNo]" |
| S7 | C6 | 읽음 문장의 설계사 술어를 `(agent_id = :agentId OR true)`로(글자는 남겨 정적 스캔을 지난다 — P5-11과 같은 모양) | PR [#21](https://github.com/hjryoo-ai/ga-agent-portal/pull/21), run 40093213276: `NotificationApiIT.anotherAgentsMissingOrMalformedIdIsTheSame404AndLeavesTheRowUnread` — "expected: 404 but was: 200"(B가 A의 알림을 읽었다) |
| S8 | C7 | 거부 경로(`InquiryRequests`의 파싱 `catch`)가 파서 문구를 로그에 **첫 판** PR [#22](https://github.com/hjryoo-ai/ga-agent-portal/pull/22), run 40093219273: **통과**(못 잡았다 — F5). **둘째 판**(`c90a98f` 위) PR [#26](https://github.com/hjryoo-ai/ga-agent-portal/pull/26), run 40094500288: `InquiryIsolationIT.rejectedBodiesCarryingASentinelLeaveNoTrace` — 잡은 출력에 맨 토큰 센티널(F13) |
| S9 | C7 | 문의 목록 항목 레코드에 `message` | `InboxApiContractTest` 1 — "`InquiriesResponse$Item`: schema [answeredAt, calcId, category, createdAt, id, status] vs record […]" |
| S10 | C8 | 권고 잠금 키에 `txid_current()`를 붙인다(트랜잭션마다 다른 키 — 직렬화 없음) | PR [#23](https://github.com/hjryoo-ai/ga-agent-portal/pull/23), run 40093222064: `InquiryRateLimitIT.concurrentRequestsAdmitExactlyTheLimit` — "[admitted (a row each)] expected: 3L but was: 4L". 먼저 해 본 두 모양: 호출 제거는 `InboxUseCasesTest` 2(`anInquiryChecksTheLineThenLocksThenCountsThenInserts`, `theDailyLimitRejectsAtNWithRetryAfterAndWritesNothing`)가, 문장을 `SELECT true … WHERE :tenantId IS NOT NULL`로 바꾼 것은 `TenantPredicateScanTest`가 먼저 잡았다 |
| S11 | C9 | CLI 답변이 알림 삽입 예외를 삼킨다 | PR [#24](https://github.com/hjryoo-ai/ga-agent-portal/pull/24), run 40093227914: `InquiryCliIT.whenTheNotificationCannotBeWrittenTheInquiryStaysOpen` — "Expecting code to raise a throwable."(답변이 알림 없이 커밋됐다) |
| S12 | C9 | `--answer`를 옵션으로 받아 파일 대신 쓴다 | `CliRunnerTest.theAnswerComesFromAFileOnly` 1 |
| S13 | C10 | `messages.ko.json`에서 `notifications.LIMIT_REBASE` 삭제 | Vitest `messages.test` "one template per notification type, both ways" 1 |
| S14 | C11 | 상세 화면이 본문을 `document.title`에 두고 되돌리지 않는다(효과) **첫 판** PR [#25](https://github.com/hjryoo-ai/ga-agent-portal/pull/25), run 40093232665: **통과**(못 잡았다 — F12). **둘째 판** PR [#27](https://github.com/hjryoo-ai/ga-agent-portal/pull/27), run 40094503831: `ga01-inquiry` 세 프로젝트 — "DOM: ga01 line after the inquiry detail"·"DOM at the end"에 본문 센티널 셋(`inquiry body 탁센…`·`01…`·`PO…` — 고정 장치는 앞 두 글자만 싣는다) |
| S15 | C10 | `screenPath`가 `link`를 대조 없이 돌려준다 | Vitest `links.test` 23 중 17(밖 출처 URL·`//`·모르는 경로·그 밖의 모양 — 맞는 넷 등 6은 통과) |

## 4. 지시문 §5 추가 항목

### 4.1 파생 규칙 표 (최종 — 설계서 §6.5 v1.9)

| 트리거 | 조건 | `type`(심각도) | dedup_key | `params` 키 | `link` |
|---|---|---|---|---|---|
| `LimitLedgerPosted` | 단계(후) > 단계(전), 두 경계 이상이면 후 단계 하나 | `LIMIT_WARN`(INFO)·`LIMIT_CRIT`(WARN)·`LIMIT_FULL`(CRIT) | `LIMIT_{STAGE}:{ledgerId}:{seq}` | `ledgerId`·`policyMasked` | `/limits/{ledgerId}` |
| `LimitLedgerRebased` | 항상 | `LIMIT_REBASE`(INFO) | `LIMIT_REBASE:{ledgerId}:{seq}` | `ledgerId`·`policyMasked` | `/limits/{ledgerId}` |
| `CommissionCalculated` | `limitCutAmt > 0` | `LIMIT_CUT`(WARN) | `LIMIT_CUT:{calcId}` | `calcId`·`policyMasked`·`closeYm` | `/lines/{calcId}` |
| `CommissionCalculated` | `commType = CLAWBACK` | `CLAWBACK`(WARN) | `CLAWBACK:{calcId}` | 같음 | `/lines/{calcId}` |
| `DeferralStatusChanged` | `to ∈ {HELD, CANCELLED}` | `DEFER_HELD`·`DEFER_CANCELLED`(WARN) | `DEFER_{to}:{scheduleId}` | `calcId`(원 라인)·`policyMasked`·`dueYm` | `/lines/{calcId}` |
| `PolicyEventObserved` | `PAYMENT_OVERDUE`·`LAPSE`·`CANCEL` | `POLICY_*`(WARN) | `POLICY_{type}:{eventId}` | `policyMasked`·`occurredOn` | 없음 |
| `StatementConfirmed` | 후 달 ≠ `DRIFT` | `STMT_CONFIRMED`(INFO) | `STMT_CONFIRMED:{ym}` | `ym` | `/statements/{ym}` |
| `PayoutCompleted` | 후 달 ≠ `DRIFT` | `PAYOUT`(INFO) | `PAYOUT:{ym}` | `ym` | `/statements/{ym}` |
| CLI `inquiry answer` | `OPEN → ANSWERED` | `INQUIRY_ANSWERED`(INFO) | `INQUIRY:{inquiryId}` | `inquiryId` | `/inquiries/{id}` |

금액 없음(Q1), 증권번호는 `policyMasked`만, 수신자는 이벤트 또는 뷰 행의 엔진 `agentId`(원장·분급 이벤트에는 없다 — DD-18).

### 4.2 상향·하향·재상향 시험 표

| 경우 | 시험 | 결과 |
|---|---|---|
| OK → WARN | `NotificationRulesTest`(16쌍), GA05 seq 5 | `LIMIT_WARN` 1 |
| OK → FULL(두 경계 이상) | 16쌍, A-1 seq 5 | `LIMIT_FULL` 1(WARN·CRIT 없음) |
| 같은 단계 안(WARN → WARN) | 16쌍, GA05 seq 11(85% → 90%) | 0 |
| 하향(FULL → OK, WARN → OK) | 16쌍, A-1 seq 10, GA05 seq 7(환수 −200,000) | 0 |
| 하향 뒤 재상향 | GA05 seq 9 — `NotificationStreamsTest`·`NotificationDerivationIT.oneLedgerRisingAgainAfterAClawbackGetsASecondRowAndARebaseGetsOnlyItsOwn`·E3 | 새 행(`LIMIT_WARN:GA5-LEDGER-1:9` — 응답 `id`가 다르다) |
| 재산정으로 단계 상승(남은 0) | GA05 seq 12 | `LIMIT_REBASE` 1뿐 |
| 같은 이벤트 재처리(재시작·재폴링) | `NotificationDerivationIT.theSameStreamGivesTheSameNotificationsWithOrWithoutARestart` | 0 추가, 바이트 동일 |
| 시드 고정 임의 전기열 | `NotificationRulesTest`(`SeededCases`) | 후보 수 = 경계 금액 신탁으로 센 상향 수 |

### 4.3 재구축 전후 알림 수

- `RebuildIT.aRebuildNeitherDerivesNorDeletesNotificationsOrInquiries`: 재구축 전 알림·문의 행(읽음 포함)과 후의 행이 바이트로 같다.
- E4(GA05 — 실 CLI `ingest rebuild`, 종료 0): 전 `{total 4, unreadCount 3}`(LIMIT_REBASE를 눌러 읽음) = 후, 응답 JSON 전체가 같다(`expect(after).toEqual(before)`), 화면의 안읽음 표시 3 그대로. 세 프로젝트 모두.

### 4.4 문의 본문 격리 — 본문이 나온 곳의 닫힌 목록

| 원천 | 본문이 나온 곳(허용) | 0을 확인한 곳 |
|---|---|---|
| API | **작성자의 상세 응답** `GET /api/v1/me/inquiries/{id}` 하나 | 접수 응답·목록(전체·`OPEN`)·알림 목록·남의 상세(404)·감사 `detail`/`target`·지표 태그 전부·로그 캡처(`InquiryIsolationIT`), 응답 크롤(`ResponseLeakScanIT`) |
| CLI | **`inquiry list` 출력**(보이는 이스케이프) | `answer`·`close` 출력, 미등록 운영자의 출력, 감사(`InquiryCliIT`), 답변은 어디에도 되풀이되지 않는다 |
| 화면(E2E) | **작성 화면·상세 화면의 DOM**과 그 상세 응답 | 다른 화면 DOM(홈·목록·알림함…)·다른 응답·콘솔·주소·요청 주소·저장소(전부 0 — `fixtures.ts`), `build/e2e` 산출물 전부(`artifact-scan.json` hits 0) |
| 빌드 | — | 시험 결과 XML·빌드 로그·웹 산출물(`scanPlaintextLeaks` — 키 `inbox`·`inquiry`) |

### 4.5 거부 바이트 표

Phase 5의 열 그대로 + **문의 429** 하나(닫힌 11 — `ProblemResponses`): 401 `UNAUTHENTICATED` · 404 `NOT_FOUND` · 400 `INVALID_REQUEST` · 500 · 시뮬레이션 여섯(422 셋·429·502·503) · 문의 429 `RATE_LIMITED`(`too many inquiries`, `Retry-After`). 알림·문의의 남의·없는·형식이 틀린 `{id}`는 같은 404 + `AUTHZ_DENIED MISSING`, 본문 형식·크기·스키마 오류는 400 한 가지. `UniformRejectionIT`가 바이트 상수로 대조한다.

### 4.6 홈 콜 수 표 (`QueryStatementIT.homeCallsHaveAFixedStatementCountIndependentOfData` — 라인 1,000·원장 200·알림 500)

| 콜 | SQL 문장 | 비고 |
|---|---:|---|
| `/me` | 4 | 그대로 |
| `/earnings/{ym}` | 5 | 그대로 |
| `/limits?status=ALL` | 4 | 그대로 |
| `/deferrals?from&to` | 5 | 그대로 |
| **`/notifications?size=3`** | **4** | 새 다섯째 — 발급자 1 + 링크 1 + 페이지 1 + 수 1(설정·커서를 읽지 않는다) |

### 4.7 E2E 시나리오 (`web-e2e` 67/67 — 세 프로젝트 모두)

| # | 시나리오 | 시험 | 결과 |
|---|---|---|---|
| E1 | GA01 머리 2 → 5 → 7 → 11: [] → {CUT, FULL} → +{STMT_CONFIRMED, PAYOUT} → +{POLICY_CANCEL, CLAWBACK}(10 하향 0), 안읽음 6 | `ga01-a1.spec.ts` 첫 두 시험 | 통과 |
| E2 | GA04: {DEFER_CANCELLED, DEFER_HELD, LIMIT_CUT, LIMIT_FULL, LIMIT_WARN, STMT_CONFIRMED}, "2027년 1월 명세" 1·"2026년 8월 명세" 0, 보류 알림을 누르면 읽음 + `/lines/EDG-C5` | `ga04-edges.spec.ts` "알림: …" | 통과 |
| E3 | GA05: WARN(5) → +CLAWBACK(7) → +WARN(9, 다른 `id`) → 같은 단계 0(11) → +REBASE(12) | `ga05-limits.spec.ts` 1 | 통과 |
| E4 | 실 CLI 재구축 → 응답 동일(§4.3) | `ga05-limits.spec.ts` 2 | 통과 |
| E5 | 라인 2 → 문의(센티널 셋 — 남은 글자 수 코드 포인트) → 상세 → 앱 안 이동으로 라인 2(본문 0 — P6-18) → 실 CLI 답변(종료 0) → 홈 안읽음 +1 → `INQUIRY_ANSWERED` 누름 → 상세의 답변·"답변 완료" → 안읽음 원래대로 → 다시 앱 안 이동으로 라인 2(본문 0) | `ga01-inquiry.spec.ts` | 통과 |
| E6 | GA05 문의 셋 201 → 넷째 429 + `Retry-After` 문구, 입력 그대로, 목록 셋 | `ga05-limits.spec.ts` 3 | 통과 |
| E7 | 본문 센티널 — §4.4 화면 행, `inquiryBodies` 6(허용된 곳에 실제로 있었다) | 고정 장치 전 시험 + `run.mjs` | 통과 |

### 4.8 번들 (CI run 40094460151, gzip 바이트)

초기 로드 123,612 B(Phase 5 121,056 — 홈 최근 알림·`NotificationItem`·링크 대조·템플릿), 상한 130,048 그대로. 지연 경로 10(+4 — 알림함·문의 셋, `LAZY_SCREENS` 닫힌 목록).

### 4.9 `pnpm audit`·의존성

새 npm 의존성 0(`licenses ok (257 packages …)` 그대로). Java: 새 모듈 `portal-inbox`의 잠금 파일 하나 — 빈 `GRADLE_USER_HOME`에서 잠금·검증 메타데이터를 다시 만들어 diff 0(3단계). 그 뒤 의존성 변경 없음.

### 4.10 실 엔진 E2E

`run.sh` 무변경. `EngineFeedE2eCheck`에 판정 ④ — GA01 `notification` 행 수 > 0(종류별 수만 — 키·매개변수는 읽지 않는다), `RESULT.md`에 "## 알림(포털 Phase 6)" 절. 실행은 사용자 몫(병합 뒤 `main`) — 기대: A-1과 같으면 여섯 종류 각 1.

### 4.11 규칙 시험의 의도적 위반 기록 (1단계)

`LimitStage`를 옮긴 뒤 허용 목록 갱신 전: ① 옛 FQN만 — `MultiplicationScanTest.multiplicationAndDivisionAppearOnlyInTheAllowlistedMethods` 실패("… [com.ga.portal.domain.rule.LimitStage#of]") ② 새·옛 둘 다 — 실패("stale allowlist entry com.ga.portal.query.rule.LimitStage#of") ③ 새 FQN만 — 통과(커밋 `bf24fa9`).

### 4.12 CI run 목록 (이 Phase — 직접 조회)

| run | 커밋 | 결과 |
|---|---|---|
| 40088726404 | `5419f04` | build 실패 — 새 시험 기대값 셋(F1) |
| 40089306614 | `91cf3dc` | success |
| 40089787668 | `981fa19` | build 실패 — 시험 덤프 `concat_ws` NULL(F2) |
| 40090374809 | `124242b` | success |
| 40090455026 | `780d274` | success |
| 40090906113 | `fc0742e` | success |
| 40091780371 | `32032ea` | build·no-docker success, web-e2e 61/67 — 문의 내역 44px(F6) |
| 40092371957 | `45a32d4` | 같은 44px(수정 전 커밋) |
| 40092522429 | `d4417fd` | 전부 success |
| 40093213276·40093222064·40093227914 | 주입 S7·S10·S11(PR #21·#23·#24) | build 실패 — 기대한 시험 하나씩 |
| 40093219273·40093232665 | 주입 S8·S14 첫 판(PR #22·#25) | **success — 못 잡았다**(F5·F12) |
| **40094460151** | **`c90a98f`** | **전부 success** |
| 40094500288·40094503831 | 주입 S8·S14 둘째 판(PR #26·#27) | build 실패·web-e2e 실패 — 기대한 곳 |

## 5. 계획과 다르게 한 것·해석·발견

계획과 다르게 한 것은 `docs/DECISIONS.md` "Phase 6 구현에서 계획과 다르게 한 것" P6-1~P6-17(요지 — P6-18까지: P6-1 형식 오류 id도 감사, P6-2 `Paging`, P6-3 규칙 파일 `in`, P6-4 빈 `status`, P6-5 Noop 파생기는 픽스처만, P6-6 임계치는 테넌트 행, P6-7 답변 파일 끝 줄바꿈 하나, P6-8 CLI 이스케이프, P6-9 지표 `route`, P6-10 계약 검사기 방법 + 경로, P6-11 CLI 자식은 bootJar, P6-12 재구축 제어 재시도, P6-13 E1 자리, P6-14 GA05 모양, P6-15 E7 공허 방지, P6-16 따옴표 없는 토큰, P6-17 44px, P6-18 E5의 앱 안 이동 검사).

| # | 발견·해석 | 처리 | 분류 |
|---|---|---|---|
| F1 | 3단계 첫 CI(40088726404)의 실패 셋은 전부 새 시험의 기대값 — 컨트롤러 고정 목록, 빈 `?status=`(기본값 — P6-4), 내 calcId 안의 설계사 ID 접두를 누출로 셌다 | 시험 수정 `a1bca5f`(구현 무변경). 그 실패 로그에 시드 증권번호 센티널이 한 번 실렸다 — 실패 경로라 `scanPlaintextLeaks`도 같은 것을 잡는다 | 기록 |
| F2 | 시험 덤프의 `concat_ws`가 NULL `read_at`을 버려 열이 밀렸다 | `coalesce` `124242b` | 기록 |
| F3 | **CLI `list`가 설계사의 글을 그대로 터미널에 냈다**(커밋 보안 검토 — CWE-150): ESC·양방향 문자로 운영자 화면을 꾸밀 수 있었다 | 보이는 이스케이프 `780d274`(P6-8) — `CliRunnerTest`가 OSC·CSI·C1·RLO·영폭·줄 구분·DEL·BMP 밖 태그 문자 본문으로 출력 한 줄을 바이트로 본다, `InquiryCliIT`는 줄바꿈·탭 | **발견·수정** |
| F4 | S5는 단위 시험(`IngestApplierTest`)이 먼저 잡는다 — CI가 통합 시험에 닿지 않는다 | 단위 증거로 기록. 같은 성질을 `NotificationDerivationIT`가 실 DB로 본다(주입 없이 통과) | 기록 |
| F5 | **C7 거부 본문은 S8을 잡지 못했다 — 두 번.** ① 첫 여섯: Jackson 3이 파싱 오류에 원문을 숨겨(`INCLUDE_SOURCE_IN_LOCATION` 꺼짐) 어느 문구에도 입력이 없다(이 기계에서 문구를 찍어 확인) → 따옴표 없는 토큰 본문을 더했다(`45a32d4`). ② 그 센티널 `문의센티널Qv75`은 첫 바이트가 다중 바이트라 파서가 "Invalid UTF-8 start byte"만 썼다 — 주입 S8 첫 판(PR #22)이 CI를 통과해서 알았다. `45a32d4`의 커밋 문구("한글은 식별자 글자라 끊기지 않는다")는 틀렸다 — 시험 없이 추론했다 | `Qv75문의센티널`(ASCII로 시작 — `Unrecognized token '…'`이 토큰 전체를 싣는 것을 이 기계에서 확인) `c90a98f`(P6-16 정정), S8 둘째 판이 잡았다 | **발견·수정** |
| F6 | 문의 내역의 항목 링크가 92×19(첫 E2E가 세 프로젝트에서) | `.item__link` 44px `d4417fd`(P6-17) | **발견·수정** |
| F7 | Q9의 "하네스 클래스패스"로 `PortalApplication`을 띄우면 통합 시험의 `@TestConfiguration`이 빈이 된다 | bootJar(P6-11) — 운영자 명령 그대로 | 해석 |
| F8 | 재구축 제어가 워커의 잠금과 겹치면 CLI가 2로 거부한다(`try again`) | 제어가 다섯 번까지 다시 친다(P6-12) — 운영자 절차와 같다. 재구축 쪽을 고치지 않았다(잠금 경합은 정상 동작) | 해석 |
| F9 | 문의 보존기간이 설계서에 없다 | 정하지 않았다 — `docs/운영.md` §7, §6 질문 | 질문 |
| F10 | P-11(기존 화면 링크의 키 부호화) | F로만 기록(계획 ⑬) — 이 Phase의 새 링크는 퍼센트 부호화, 기존 화면은 그대로 | 기록 |
| F11 | 문서만 바꾼 `40955f8`·이 보고서 커밋도 같은 CI를 탄다 | 보고서의 수는 마지막 코드·시험 커밋(`c90a98f`) run 기준 | 기록 |
| F12 | **E5는 S14(상세가 문서 제목에 본문을 남김)를 잡지 못했다** — 상세를 늘 `portal.open`(새로 불러오기)으로 떠나, 새 문서가 제목을 버렸다. 주입 S14 첫 판(PR #25)이 CI를 통과해서 알았다 | 상세에서 라인 링크(앱 안 이동)로 나간 화면을 두 번 검사(`c90a98f`, P6-18), S14 둘째 판이 세 프로젝트에서 잡았다 | **발견·수정** |
| F13 | S8 둘째 판의 실패 메시지(AssertJ "Expecting actual … not to contain")가 잡은 출력 전체를 실었다 — 그 주입 run의 로그에 본문 센티널이 나온다 | 주입 run(닫은 PR)의 실패 경로뿐이다 — 정규 run에서는 같은 단언이 통과해 출력이 없다. F1과 같은 성질 | 기록 |

## 6. 다음 Phase 질문 (Phase 7 — 대사·DRIFT 자동 복구·관측성)

1. **`REPAIRED`가 된 달의 확정·지급 알림**(Q17): `DRIFT` 달에는 `STMT_CONFIRMED`·`PAYOUT`이 없다. 재대사로 `REPAIRED`가 되면 그때 낼지(키는 그대로 `STMT_CONFIRMED:{ym}` — 한 번만), 아니면 "확인 완료" 같은 별도 종류인지.
2. **재구축이 처음 적용한 사실의 알림**(Q25): 지금은 없다. 재대사가 엔진 명세 조회(`GET /internal/v1/agents/{agentId}/statements/{ym}`)로 달을 고친 뒤 빠진 알림을 메울지 — 메우면 그 경로가 "재구축은 파생하지 않는다"(§6.7)와 어떻게 갈리는지.
3. **운영 알림 채널**: DRIFT·`APPLY_ERROR`·인제스트 정지와 함께 문의 `OPEN` 적체(가장 오래된 것의 나이)도 볼지.
4. **지표 자리**: `notifications_derived_total{tenant, type}`(파생 수)와 문의 접수·답변 수 — 태그 키 집합을 지금 고정할지(P6-9의 교훈 — 한 이름에 한 집합).
5. **문의 보존기간**(F9): 알림은 1년(운영 SQL), 문의는 미정 — 감사(`audit_access`)와 같은 기간인지, 답변 뒤 N개월인지.
6. **트레이스**: 적용 트랜잭션 안의 파생을 같은 스팬에 둘지(지금 프로젝터·파생·커서가 한 트랜잭션).
