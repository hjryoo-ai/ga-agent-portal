# 포털 Phase 7 지시문 — 대사·DRIFT 자동 복구·관측성 (ga-agent-portal, v1.0, 2026-10-27)

## 형식에 대해

E2 형식 — **범위·완료 기준·질문만**. 지시문 문장이 코드·계약·엔진과 다르면 P-번호로 반증한다. 계획 승인은 사용자가 직접 해도 된다(돌이킬 수 없는 결정이 있으면 승인 전에 보낸다 — 이번엔 **엔진 샘플 커밋**과 **새 의존성**(트레이스)이 후보).

브랜치 `work/phase-7`(이미 `1a8b871` — Phase 6 심사 보관), 태그 `phase-7`, PR, **병합은 수용 심사 회신 뒤**. 정본: `docs/설계서.md` v1.9(§3.4 3, §5 `recon_result`, §6.5 표(REPAIRED 행 추가 예정), §6.6, §6.7, §10 정합성·관측성, §12 Phase 7, §14.1 #11·#12), `contracts/api/v1/engine-internal.openapi.yaml` 1.4.0(`GET /internal/v1/agents/{agentId}/statements/{ym}?mode=ESTIMATE|CONFIRMED` — 엔진 E2 구현), Phase 6 수용 심사 §3(Q1~Q6 결정), `CLAUDE.md` 전 절.

## 0. 전제

- 포털 `main` = Phase 6 병합 뒤(`b87db17`). 이 Phase는 **화면을 바꾸지 않는다**(DRIFT "확인 중"·흐림은 Phase 4부터 있고, `REPAIRED`는 화면에서 `OK`다). 더하는 것은 **야간 대사**(`portal-recon` — Phase 0 골격만 있음), **자동 복구**(재구축 → 재대사 → `REPAIRED`), **`REPAIRED` 달의 확정·지급 알림**(Phase 6 수용 Q1 — 이벤트 아닌 둘째 파생 지점), **관측성**(지표 태그 집합 고정·트레이스·운영 `status`), 그리고 설계서 §12 Phase 7 완료 기준 — **"인위적 라인 누락 주입 → DRIFT 감지 → 자동 재구축 → REPAIRED"**를 E2E로.
- **대사는 비교만 한다.** 엔진 명세 응답의 `gross`·`clawbackOffset`·`adjustment`를 포털 `earnings_month`의 같은 세 정수와 **동등 비교**한다. 차이·비율·보정값을 만들지 않는다(규칙 1). 세후·`net`·`withholdingTax`는 비교하지 않는다(포털 합산이 아니다 — 엔진 값 그대로 담긴 열).
- **뒤처진 포털은 DRIFT가 아니다.** 커서가 `RUNNING`이 아니거나, 따라잡지 못했거나(`caught_up_at` 기준 — Phase 3 Q3), ESTIMATE 응답의 `basisSeq` > 포털 `last_seq`면 그 설계사×달은 **`INCONCLUSIVE`**(비교하지 않음 — 지표만, `recon_result` 행 없음). DRIFT는 "같은 사실을 둘 다 봤는데 합이 다르다"일 때만.
- **엔진 호출은 서버만, 재시도 0**, Phase 5 `portal-sim`의 클라이언트·토큰·타임아웃 규약 그대로(`agentId`는 포털 뷰의 엔진 사실, 응답은 이벤트 payload 스키마 사본으로 검증). 엔진 404 `AGENT_NOT_FOUND`·`STATEMENT_NOT_CONFIRMED`·422 `MONTH_CLOSED`·불가·시간 초과는 **전부 `INCONCLUSIVE`**(사유 코드별 지표) — DRIFT도, 정지도 아니다.
- **재구축은 여전히 파생하지 않는다**(Phase 6 C3 ArchUnit 그대로). `REPAIRED` 알림은 재대사가 `REPAIRED`를 쓰는 **그 트랜잭션**에서 — `INQUIRY_ANSWERED`처럼 이벤트 아닌 파생. 키는 그대로(`STMT_CONFIRMED:{ym}`·`PAYOUT:{ym}`, `ON CONFLICT DO NOTHING`), `params`·`link` 모양도 그대로(계약 변경 없음).
- 자동 재구축은 운영자 `ingest rebuild`와 **같은 코드 경로**(`Rebuilder` — txA/txB/txC, 상한, 감사)이고 행위자만 `recon:<tenant>`(시스템 행위자 — 감사 행에 그대로). 테넌트당 **하루 한 번**, 테넌트 파라미터 `auto_rebuild_enabled`(기본 true)로 끈다. 재구축 뒤에도 DRIFT면 **다음 날 다시 재구축하지 않는다**(같은 달·같은 설계사의 미해결 `DRIFT` 행이 있으면 건너뛰고 운영 지표만) — 엔진 쪽 정정 가능성은 재구축으로 안 풀린다.
- `portal-api` 계약 **변경 없음**(1.2.0 그대로). 엔진 변경은 **응답 샘플 커밋 하나**(⓪, Phase 5와 같은 방식 — 코드 0).
- 새 의존성은 트레이스뿐이어야 한다(Boot BOM 관리 — 질문 6). 검증 메타데이터는 빈 `GRADLE_USER_HOME`에서 재생성, diff를 보고서에.

## 1. 범위

⓪ **엔진 명세 샘플**(엔진 소형 커밋 — `SimulationContractTest`와 같은 방식의 `StatementContractTest` 사례 + `-Psamples.update=true`): A-1 설계사의 `202608 CONFIRMED`(지급 뒤 — `withholdingTax`·`net` 실림), `202702 ESTIMATE`(`basisSeq` 10, A-1 둘째 판 seq 11과 같은 값이어야 한다 — 다르면 보고), `202702 CONFIRMED` → 404 `STATEMENT_NOT_CONFIRMED`, `202608 ESTIMATE` → 422 `MONTH_CLOSED`, 모르는 설계사 404, 그리고 **E2 `estimate_cycle_01` 설계사**의 네 단계 중 ESTIMATE·CONFIRMED 각 하나. 포털 사본 `contracts/shared/ga-commission-engine/samples/statements/` + `CHECKSUMS`·`UPSTREAM`. 이 쌍이 포털 스텁·시험·E2E의 유일한 엔진 명세 데이터.

① **야간 대사(`portal-recon`)**: 테넌트마다 하루 한 번(테넌트 `params.recon_hour_kst`, 기본 04, 주입 `Clock`) — 대상 = 그 테넌트에서 **`earnings_month` 행이 있는 설계사 × {당월(KST), 직전 달}**(전 설계사가 아니다 — 행 없는 설계사·달은 비교할 것이 없다). 달마다 포털 상태로 모드를 고른다: `PROVISIONAL`·`ESTIMATED` → `ESTIMATE`, `CONFIRMED`·`PAID` → `CONFIRMED`. 판정은 셋 — `MATCH`·`DRIFT`·`INCONCLUSIVE`(§0 전제 — 뒤처짐·`basisSeq`·엔진 404/422/불가). 엔진 호출은 설계사마다 순차(동시 1, 호출 간격 파라미터)이고 전체 상한(테넌트당 호출 수·시간)을 넘으면 남은 설계사는 `INCONCLUSIVE(BUDGET)`. `recon_result`에는 **`DRIFT`·`REPAIRED` 행만** 쓴다(`source = BATCH`, `source_seq = 0`, `run_at` = 그 회차 시각 — PK가 회차로 갈린다). `MATCH`·`INCONCLUSIVE`는 지표(`recon_checks_total{tenant, result}`)와 회차 요약 로그 한 줄(수만)로. DRIFT면 `earnings_month.data_status = DRIFT`(이미 DRIFT면 그대로) + 지표. 운영 CLI `recon run --tenant=T`(같은 코드, 행위자 `cli:<user>` — E2E·운영 수동 실행).

② **자동 복구**: 회차 끝에 그 테넌트에 **새** DRIFT(이 회차가 처음 쓴 행)가 하나라도 있고 `auto_rebuild_enabled`이고 오늘 아직 재구축하지 않았으면 → `Rebuilder`(행위자 `recon:<tenant>`) → 성공하면 **그 테넌트의 DRIFT 설계사×달만 재대사**(엔진 재호출) → `MATCH`면 `recon_result` `REPAIRED` 행 + `earnings_month.data_status = OK` + **Q1 알림**(그 달이 `CONFIRMED`면 `STMT_CONFIRMED:{ym}`, `PAID`면 둘 다 — 같은 키) — 한 트랜잭션(설계사×달 단위). 여전히 `DRIFT`면 행은 그대로(미해결), 지표 `recon_unresolved_drift{tenant}`(게이지). 재구축 실패(txC)는 커서 `FAILED` — 기존 규약대로 그 테넌트 정지, 운영 지표. **이벤트 대사(`source = EVENT`) DRIFT 달도 같은 복구 대상**이다(Phase 2가 만든 행 — 재구축 뒤 재대사로 `REPAIRED`가 될 수 있다).

③ **관측성**: 지표 태그 집합을 **고정**(Phase 6 수용 Q4 + 이 Phase): `recon_checks_total{tenant, result}`(`MATCH`·`DRIFT`·`INCONCLUSIVE`), `recon_inconclusive_total{tenant, reason}`(닫힌 사유), `recon_unresolved_drift{tenant}`, `recon_runs_total{tenant, result}`(`OK`·`DRIFT`·`REPAIRED`·`FAILED`), `notifications_derived_total{tenant, type}`, `inquiries_total{tenant, action}`, `inquiry_open_total{tenant}`·`inquiry_open_oldest_age_seconds{tenant}`, 기존 `ingest_*`·`api_*`·`projection_drift_total`·`ingest_rebuild_seconds{outcome}`. 시험 하나가 **레지스트리의 전 지표 이름 ↔ 태그 키 집합**을 닫힌 표(`docs/지표.md`)와 양방향 대조한다(P6-9의 교훈 — 한 이름에 한 집합, 값은 닫힌 열거). 트레이스: 적용 트랜잭션 = 스팬 하나(프로젝터·파생·커서 자식), 엔진 호출(피드·시뮬레이션·명세) 스팬, 대사 회차 스팬 — 속성에 **값 0**(seq·eventId·종류·수·해시만, 증권번호·`agent_id` 원문·본문 없음 — 평문 스캔이 트레이스 내보내기(시험용 in-memory exporter) 결과도 본다). 구조화 로그의 필드 집합도 같은 표에. 운영 CLI `status --tenant=T|--all`: 커서 상태·lag·마지막 `caught_up_at`·DRIFT(미해결 수·가장 오래된 달)·마지막 대사 회차 결과·문의 적체(수·가장 오래된 나이)·재구축 마지막 결과 — 한 줄 표, 값 없음(식별자는 해시).

④ **운영 알림 채널 v1** = ③의 지표 + `status` CLI(Phase 6 수용 Q3). 이메일·웹훅은 범위 밖. `docs/운영.md` §8에 "무엇을 보고 무엇을 하나" 표(DRIFT 미해결 → 엔진 정정 확인, `FAILED(REBUILD_FAILED)` → 원인 수정 후 `ingest rebuild`, `INCONCLUSIVE(BUDGET)` → 상한 조정).

⑤ **E2E — 설계서 §12 완료 기준 그대로**: 하네스 엔진 스텁에 명세 엔드포인트(⓪ 샘플, 요청 = `agentId`·`ym`·`mode` 대조) + 제어 `POST /control/{tenant}/corrupt/line/{calcId}`(**포털 DB에서 `earnings_line` 한 행을 지우고 그 달 합을 다시 쓴다** — "인위적 라인 누락", 운영 롤로 직접 SQL — 하네스만) + `POST /control/{tenant}/recon`(실 CLI `recon run` 자식 프로세스 — Phase 6 P6-11 방식). 시나리오: GA01 — ⓐ 정상: `recon run` → `MATCH`(행 0, 지표만) ⓑ 라인 누락 → `recon run` → 202608 `DRIFT`(화면 "확인 중"·흐림 — 기존 Phase 4 시험 재사용) → 자동 재구축 → 재대사 `REPAIRED` → 화면 정상 + 알림함에 `STMT_CONFIRMED`·`PAYOUT` **추가 0**(이미 Phase 6에서 있던 키 — `ON CONFLICT`) ⓒ 뒤처짐: 피드 머리를 올리고 폴링을 멈춘 채 `recon run` → `INCONCLUSIVE`(DRIFT 0, 재구축 0) ⓓ 엔진 다운 → `INCONCLUSIVE` ⓔ `auto_rebuild_enabled=false` 테넌트(GA04의 EVENT DRIFT 202608) → DRIFT 유지·재구축 0. GA04에 `auto_rebuild_enabled=true`로 한 번 더: EVENT DRIFT(확정 합 불일치가 **스트림 자체**에 있다)는 재구축 뒤에도 `DRIFT` → 미해결 게이지 1, 다음 회차 재구축 0(§0 전제). 실 엔진 E2E(`run.sh`): 판정 ⑤ "`recon run` → A-1 두 달 `MATCH`, DRIFT 0"(사용자 실행 — 미실행이면 그렇게).

⑥ **문서**: 설계서 v1.10(§3.4 3 확정, §5 `recon_result` 쓰기 규칙, §6.5 표에 "재대사 `REPAIRED`" 행, §6.6 세 판정·`INCONCLUSIVE`·복구 조건·"다음 날 재시도 없음", §6.7 행위자 `recon:`, §10 지표 표 → `docs/지표.md` 참조, §12, §13, §14.1 #11(긴 트랜잭션 — 자동 재구축이 밤에 도는 이유)·#12 닫기), `CLAUDE.md` "대사·관측성 규약" 절, `docs/운영.md` §8, `docs/지표.md`(신설 — 닫힌 표), DECISIONS P7-n.

## 2. 완료 기준

| # | 기준 | 증명 |
|---|---|---|
| C1 | 엔진 샘플 사본 = 엔진 바이트(`UPSTREAM`·`CHECKSUMS`), 포털 응답 검증은 이벤트 payload 스키마 사본으로 | `verifyUpstreamContract` 확장, 스텁은 샘플 밖 요청에 500 |
| C2 | **대사는 세 정수 동등 비교뿐** — 바이트코드 곱셈 허용 목록 변경 0, SQL 산술 허용 목록 변경 0, 차이 값을 담는 열·필드·로그 0 | `MultiplicationScanTest`·`SqlArithmeticScanTest` 무변경 + `recon_result` 쓰기 시험(포털 값·엔진 값만) |
| C3 | 판정 표 전수: (포털 상태 × 모드 × 엔진 응답 종류 × 커서 상태 × `basisSeq` 관계) → `MATCH`/`DRIFT`/`INCONCLUSIVE(사유)` — 순수 함수 | `portal-recon` 단위 시험(표), 주입: 뒤처진 커서에서 DRIFT를 내게 |
| C4 | `recon_result`에는 `DRIFT`·`REPAIRED`만, `MATCH`·`INCONCLUSIVE`는 행 0 + 지표; `DRIFT` 행은 `earnings_month.data_status` 갱신과 한 트랜잭션; 결정론(같은 입력·같은 시계 → 같은 행 바이트) | 통합 시험 |
| C5 | 자동 복구: 새 DRIFT → 재구축(행위자 `recon:`, 감사 행) → 재대사 → `REPAIRED` + `data_status OK` + Q1 알림(같은 키 — 이미 있으면 추가 0) 한 트랜잭션; `auto_rebuild_enabled=false`·오늘 이미 재구축·미해결 DRIFT 존재 → 재구축 0 | 통합 시험 + 주입: 재대사 없이 `REPAIRED` 쓰기 / 미해결 DRIFT에 매일 재구축 |
| C6 | **재구축은 파생 0 그대로**(Phase 6 ArchUnit 무변경), `REPAIRED` 알림은 재대사 트랜잭션에서만 — `Rebuilder`·`Reconciler`의 의존 경계를 ArchUnit으로(`Reconciler` → `Rebuilder` 호출은 허용, `Rebuilder` → 파생 경로 0) | ArchUnit, 주입 |
| C7 | 엔진 호출 재시도 0, 동시 1, 상한 초과 → `INCONCLUSIVE(BUDGET)`, 엔진 불가·404·422 → `INCONCLUSIVE` + 지표(정지 0·DRIFT 0) | 스텁 다운·느림·404·422, 호출 수 단언 |
| C8 | 지표 이름 ↔ 태그 키 집합 양방향 = `docs/지표.md`, 태그 값은 닫힌 열거, 식별자 태그 0 | `MetricsCatalogTest`, 주입: 태그 하나 추가 / 표에만 있는 지표 |
| C9 | 트레이스·로그 속성에 값 0(증권번호·`agent_id` 원문·본문·토큰) — in-memory exporter 결과를 평문 스캔 + `ResponseLeakScanIT` 패턴으로 | 시험 + 센티널 주입 |
| C10 | `status` CLI: OPERATOR 인가(기존 규약), 출력에 식별자 원문 0, 종료 코드 규약 | `CliRunnerTest`·IT |
| C11 | E2E ⑤ ⓐ~ⓔ × 세 프로젝트(화면은 기존 시험 재사용 — DRIFT 흐림·정상), axe·CSP·누출 0, 번들 무변경(화면 변경 0 — 바이트 같음) | `web-e2e` 아티팩트 |
| C12 | Phase 0~6 무손상, `no-docker` 실패, 평문 스캔(트레이스 포함), 검증 메타데이터 diff(새 의존성은 트레이스뿐 — 목록), 주입 ID 단위, `run.sh` 판정 ⑤ 추가(미실행이면 미실행) | CI 아티팩트 |

## 3. 계획에서 답할 질문

1. **ESTIMATE 비교의 타당성 — 먼저 측정**: 엔진 `StatementEstimated`/`mode=ESTIMATE`의 `gross`가 포털 Σ라인(CLAWBACK·ADJUSTMENT 제외)과 같은지 — E2 계획 ②에 "도래 미리보기(DEFERRED 가상 줄)"가 있다. A-1 202702(`gross 0`, `clawbackOffset −720000`)는 맞지만, 분급 도래가 있는 달(E2 `estimate_cycle_01`)에서 ESTIMATE `gross`에 아직 `CommissionCalculated`로 발행되지 않은 도래분이 들어 있으면 **매달 거짓 DRIFT**다. 스트림으로 확인하고, 다르면 ⓐ ESTIMATE 모드는 `clawbackOffset`·`adjustment`만 비교 ⓑ ESTIMATE 대사를 빼고 CONFIRMED만 ⓒ 엔진 응답의 미리보기 몫을 분리해 달라고 요청(E3) — 측정 결과로 권장을 정한다.
2. **`INCONCLUSIVE`의 뒤처짐 기준**: `caught_up_at`이 회차 시작 전 N분 이내 **그리고** 회차 시작 시점 `last_seq`를 고정해 ESTIMATE `basisSeq ≤ last_seq`인지. 회차 중 커서가 전진하면 어떻게 할지(고정한 `last_seq`로만 판단 — 권장).
3. **CONFIRMED 모드의 `basisSeq`**: `StatementConfirmed` 모양에는 `basisSeq`가 없다. 마감월은 더 바뀌지 않으므로(규칙 4) 커서가 그 달의 확정 이벤트를 지났으면 충분 — `earnings_month.status ∈ {CONFIRMED, PAID}`가 그 증거인지, 지급 뒤 `withholdingTax`·`net`이 실린 응답과 `PAID` 전 포털(`net_basis` 없음)의 관계를 어떻게 둘지(세후는 비교 안 함 — §0).
4. **재대사 범위**: 재구축 뒤 그 테넌트의 DRIFT 설계사×달만인지, 당일 회차 전체를 다시 도는지. 권장 전자(엔진 호출 최소).
5. **`recon_result` 행의 `run_at`**: 회차 시작 시각 하나로 묶을지(회차 식별) 설계사마다 시각인지. 권장 회차 시각 하나 + `REPAIRED`는 재대사 시각.
6. **트레이스 스택**: Micrometer Tracing + OpenTelemetry 브리지(Boot BOM 관리)로 두고 내보내기는 Phase 8 compose에서(이 Phase는 in-memory exporter 시험만)인지, 아예 OTel SDK 직접인지. 새 아티팩트 목록을 계획에 — 검증 메타데이터 승인 대상.
7. **`status --all`의 자리**: 테넌트 전체를 보는 명령은 OPERATOR 링크가 테넌트마다 있어야 한다(Phase 3 Q10 규약 — 하나라도 거부면 2). 그대로 둘지.
8. **하네스 `corrupt` 제어**: 운영 롤로 포털 DB에 직접 `DELETE`(시험 전용 경로 — 제품 코드 0)로 충분한지, 재구축 `max-events`와 GA01 스트림 길이의 관계(상한 안).
9. **회차 스케줄러**: Spring `@Scheduled` + 테넌트별 시각 + 인스턴스 둘일 때 중복 방지(테넌트 advisory lock — 회차 단위)인지, Phase 8 compose가 cron으로 `recon run`을 부르는지. 권장 **둘 다 가능한 구조**(스케줄러는 CLI와 같은 진입점 호출), 기본은 앱 내 스케줄러 + 잠금.
10. **EVENT DRIFT와 BATCH DRIFT의 `REPAIRED`**: 같은 달에 둘 다 있으면 `REPAIRED` 행은 하나(`source = BATCH`)인지 둘인지. 권장 하나 — `data_status`는 달에 하나다.

## 4. 하지 말 것

차이·보정값 계산. 세후·`net` 비교. 엔진 재시도. 뒤처진 포털의 DRIFT 판정. 재구축에서 파생. 미해결 DRIFT의 매일 재구축. 화면 변경·계약 변경. 이메일·웹훅 채널. 대사 회차의 전 설계사 호출(행 있는 설계사만). 트레이스 속성의 값.

## 5. 보고 형식

Phase 6과 같게. 추가: 판정 표(최종 — 전수 조합), 질문 1의 측정 결과(스트림별 ESTIMATE `gross` vs 포털 Σ), 자동 복구 흐름의 트랜잭션 경계 표, 지표 표(`docs/지표.md` 그대로), 트레이스 스팬 목록·속성 키 집합, 엔진 호출 수(회차당 상한·실측), E2E ⓐ~ⓔ 표, 새 의존성·검증 메타데이터 diff, 다음 Phase 질문(Phase 8 — 데모 생성기 부록 B·compose·Keycloak PKCE·`run.sh`를 compose 안에서·README 포트폴리오 관점·§14.1 #14 닫기).
