# 포털 Phase 7 계획 — 대사·DRIFT 자동 복구·관측성 (ga-agent-portal)

> 이 계획은 지시문 `docs/phase-07-지시문.md` v1.0(2026-10-27)에 대한 것이다. 브랜치는 `work/phase-7`(`main` = `b87db17` 위 `1a8b871` — Phase 6 심사 보관)이다. **이 계획이 승인되기 전에는 엔진 샘플·계약 사본·코드를 쓰지 않는다.**
>
> **승인**(2026-10-27 — `docs/phase-07-계획승인.md`): Q1~Q14 권장안 전부(Q1 = ⓑ′), 돌이킬 수 없는 결정 셋(엔진 샘플 PR — 엔진 CI 뒤 직접 병합, 트레이스 의존성 둘, V8 `recon_run`) 모두 예. 작은 조건 A·B(③ R5·⑤ `status` — 아래 반영), Q9 운영 문서 한 줄, Q6 재생성 diff는 추가만·제거 0·전이 목록 표, P-7 값 열 0은 `CHECK` 시험으로. `docs/DECISIONS.md` "Phase 7 계획 승인"(DE-27·DE-28·DD-19). 구현에서 달라진 곳은 같은 파일 P7-n과 보고서 §5.
>
> **돌이킬 수 없는 결정 — 승인 전에 확인을 바라는 셋**(지시문 형식 절):
> 1. **엔진 샘플 커밋**(⓪) — 엔진 `ga-commission-engine`에 시험 코드만 바꾸는 PR 하나(Phase 5 #8과 같은 방식), 자체 CI 통과 뒤 병합해도 되는지(Q11).
> 2. **새 의존성**(Q6) — Micrometer Tracing의 OpenTelemetry 브리지와 시험용 in-memory exporter. 전부 Boot 4.1.1 BOM이 관리하는 판이다.
> 3. **V8 — 새 표 `recon_run` 하나**(P-7) — 대사 회차 기록. `recon_result`에는 `DRIFT`·`REPAIRED`만 쓰므로 "오늘 이미 돌았나"·"오늘 이미 재구축했나"·`status`의 "마지막 회차 결과"를 둘 곳이 없다.
>
> 읽은 것:
> - 지시문 전문, Phase 6 수용 심사 §3(Q1~Q6), 설계서 v1.9 §3.4·§5(`recon_result`·`earnings_month`)·§6.5~§6.7·§10·§12·§14.1 #11·#12.
> - 계약 `engine-internal.openapi.yaml` 1.4.0 `getStatement`, `StatementEstimated`·`StatementConfirmed` payload 스키마(필수·선택·설명문).
> - 포털 코드: `portal-recon`(골격만 — `package-info` 하나), `Rebuilder`·`ViewReset`(재구축이 지우는 표), `StatementProjector`(이벤트 대사), `ReconResultRepository`, `IngestCommands`·`CliRunner`, `MicrometerIngestMetrics` 등 지표 넷, `WebE2eHarness`·`StubSimulationEngine`, V1·V4의 `recon_result` DDL.
> - 엔진(`ai-comm/commission-system`, `main` = `2c3b093`): `StatementQueryController`·`StatementQuery`(ESTIMATE `basisSeq` = 그 시점 `outbox.headSeq()`), `SimulationContractTest`(샘플 쓰기·대조 방식).
> - 측정: 포털이 가진 엔진 스트림 전부의 `StatementEstimated`·`StatementConfirmed` 합계 vs 포털 Σ라인(Q1 — 아래 표).

## 0. 먼저 결정이 필요한 것 (요약)

| # | 질문 | 권장 | 다른 길 |
|---|---|---|---|
| Q1 | ESTIMATE 비교의 타당성 | **측정 결과: ESTIMATE `gross`는 분급 도래가 있는 달에서 포털 Σ와 다르다**(`estimate_cycle_05` seq 7 — 엔진 378,000 vs 포털 0; DEFERRED 라인은 seq 8에서야 발행). 나머지 ESTIMATE 일곱·CONFIRMED 열넷은 `gross`·`clawbackOffset` 모두 같다(아래 측정 표).<br>**권장 ⓑ′ — ESTIMATE 달은 DRIFT를 내지 않는다.** `clawbackOffset`·`adjustment`만 비교하고, 다르면 `INCONCLUSIVE(ESTIMATE_DIFF)`(지표만). DRIFT와 자동 복구는 CONFIRMED 모드에서만.<br>• 이유 하나: `gross`는 미리보기 몫 때문에 쓸 수 없다(ⓐ의 근거).<br>• 이유 둘: `basisSeq`는 **하한**이다(스키마 설명문 — "그 뒤의 사실도 반영됐을 수 있다"). 엔진 구현은 조회 시점 `outbox.headSeq()`라, 순번을 아직 받지 않은 사실이 예상에 섞일 수 있다. 그러면 ⓐ도 거짓 DRIFT → 자동 재구축 → 재대사 `REPAIRED`로 하루 한 번 쓸데없는 재구축을 부른다.<br>• 대사 창이 {당월, 직전 달}이라 열린 달은 다음 달 회차에서 CONFIRMED로 다시 본다 — 놓치는 것은 "확정 전 하루~한 달"의 조기 경보뿐이다. | ⓐ ESTIMATE는 `clawbackOffset`·`adjustment`로 DRIFT · ⓑ ESTIMATE 대사 없음 · ⓒ 엔진에 미리보기 몫 분리 요청(E3 — 엔진 변경, 이 Phase 범위 밖) |
| Q2 | 뒤처짐 기준 | **회차 시작에 테넌트마다 한 번 고정**: 커서 상태·`last_seq`·`caught_up_at`. 판정은 그 고정 값으로만(회차 중 커서가 전진해도 바꾸지 않는다).<br>• `RUNNING`이 아니거나 `caught_up_at`이 회차 시작 전 `recon_caught_up_within_seconds`(테넌트 파라미터, 기본 900) 밖이면 그 테넌트 전부 `INCONCLUSIVE(LAGGING)` — **엔진 호출 0**.<br>• ESTIMATE: `basisSeq > 고정 last_seq`면 `INCONCLUSIVE(LAGGING)`.<br>• CONFIRMED에서 합이 다르면 DRIFT로 쓰기 **전에** 피드 머리를 한 번 읽는다(`after = 고정 last_seq`, `limit 1`) — 머리 > 고정 `last_seq`면 `INCONCLUSIVE(LAGGING)`. 불일치일 때만 부르므로 호출이 거의 늘지 않는다(엔진 호출이 아니라 피드 호출 — 재시도 아님) | 회차 중 커서를 다시 읽는다 · `basisSeq`만 본다 |
| Q3 | CONFIRMED의 `basisSeq` | **`earnings_month.status ∈ {CONFIRMED, PAID}`가 증거다**(포털이 그 달의 `StatementConfirmed`를 적용했다 — 마감월은 이후 이벤트로 바뀌지 않는다, 규칙 4).<br>• 비교: `gross`·`adjustment`, 그리고 응답에 있으면 `clawbackOffset`(`StatementConfirmed`에서는 선택 — P-8, 이벤트 대사와 같은 규칙 "있는 필드만").<br>• `withholdingTax`·`net`은 비교하지 않고 저장도 하지 않는다(`recon_result.engine_net` NULL).<br>• 포털이 `PAID`이고 엔진 응답에 세후가 없거나, 포털이 `CONFIRMED`이고 엔진에 세후가 있어도 판정과 무관(세후 미비교) | 세후도 비교 |
| Q4 | 재대사 범위 | **그 테넌트의 미해결 DRIFT 설계사×달만**(이 회차 것 + 출처 무관 기존 것 — EVENT 포함, 지시문 ②) | 회차 전체 다시 |
| Q5 | `run_at` | **DRIFT = 회차 시작 시각 하나**(회차 식별 — `recon_run.run_at`과 같은 값), **REPAIRED = 재대사 시각**(설계사×달 트랜잭션마다 그 시각, 주입 `Clock`) | 설계사마다 시각 |
| Q6 | 트레이스 스택 | **Micrometer Tracing + OpenTelemetry 브리지**(Boot BOM 관리 — micrometer-tracing 1.7.1, opentelemetry 1.62.0), 내보내기 없음(Phase 8 compose에서 OTLP). 시험은 `opentelemetry-sdk-testing`의 `InMemorySpanExporter`.<br>• 새 직접 의존: `org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry`(앱), `io.opentelemetry:opentelemetry-sdk-testing`(시험). 전이 아티팩트 목록은 3단계 잠금 재생성 뒤 보고서 표로(검증 메타데이터 diff).<br>• 스팬은 `ObservationRegistry`(micrometer-observation — Spring 아님) 포트로: `portal-ingest`·`portal-recon`은 포트 인터페이스만 알고, `app`이 Micrometer 구현을 단다(Spring 무의존 모듈 규약 그대로). | OTel SDK 직접 · `spring-boot-starter-opentelemetry`(OTLP 내보내기까지 — Phase 8로) |
| Q7 | `status --all` | **그대로** — 테넌트마다 OPERATOR 링크를 확인하고, 거부된 테넌트는 `T not authorized` 줄, 하나라도 거부면 종료 2(Phase 3 Q10) | — |
| Q8 | 하네스 `corrupt` | **하네스의 DB 연결(시드 롤)로 직접 SQL** — `DELETE FROM earnings_line … calc_id = ?` + 그 달 `earnings_month.gross`를 남은 라인 합으로 다시 쓴다. 제품 코드 0. 재구축 상한 50,000 ≫ A-1 11건 | 제품 코드에 시험 진입점 |
| Q9 | 회차 스케줄러 | **둘 다 가능한 구조, 기본은 앱 안 스케줄러.** `@Scheduled` 1분 틱 → 테넌트마다 "KST 시각 ≥ `recon_hour_kst`이고 오늘(KST) `recon_run` 회차가 없음"이면 그 테넌트 회차. 회차는 **테넌트 권고 잠금**(`hashtext('portal.recon')` 공간 — 인제스트·시뮬레이션·문의와 겹치지 않음)을 회차 내내 쥔다 — 인스턴스 둘이어도 하나만. CLI `recon run`은 같은 진입점(`ReconRunner.run(tenant, actor, months)`)을 부른다. 스케줄러는 앱 설정 `portal.recon.scheduler-enabled`(기본 true)로 끈다 — 통합 시험·E2E는 끈다(P-11) | compose cron만 |
| Q10 | EVENT + BATCH 같은 달 | **`REPAIRED` 행 하나**(`source = BATCH`, `source_seq = 0`, `run_at` = 재대사 시각). "해결" = 그 (설계사, 달)의 마지막 `DRIFT` 행(출처 무관)보다 늦은 `REPAIRED` 행이 있다 | 출처마다 하나 |
| Q11 | 엔진 샘플 PR 병합 | **엔진 자체 CI 통과 뒤 내가 병합**(Phase 5 Q17과 같게 — 시험 코드·샘플 파일만, 엔진 `main` 소스 변경 0). 포털은 그 병합 커밋을 `UPSTREAM`에 | 병합은 사용자 |
| Q12 | 대상 달 지정 | **CLI `recon run --tenant=T [--ym=YYYYMM,…]`**(최대 6개, 없으면 창 {당월, 직전 달}). 스케줄러는 창만. E2E·실 엔진 `run.sh`는 `--ym`(P-3) | 시각을 바꿔 창을 맞춘다 |
| Q13 | 같은 값의 DRIFT가 다음 회차에 또 나오면 | **행을 다시 쓰지 않는다** — 그 (설계사, 달)의 미해결 DRIFT 행과 (포털 셋, 엔진 셋)이 같으면 지표만(`recon_checks_total{result=DRIFT}`). 값이 다르면 새 행(그러나 "새 DRIFT"는 아니다 — Q14) | 매 회차 한 행 |
| Q14 | "새 DRIFT"·"이미 시도함"의 정의(지시문 ② "미해결 DRIFT의 매일 재구축 금지") | **새 DRIFT = 회차 시작 때 그 (설계사, 달)에 미해결 DRIFT가 없었는데 이 회차가 DRIFT로 판정한 것.** 재구축 조건: 새 DRIFT ≥ 1, 또는 **복구를 아직 시도하지 않은** 미해결 DRIFT ≥ 1(시도 = 그 DRIFT 행 뒤에 `recon_run.rebuilt_at`이 있다) — 그리고 `auto_rebuild_enabled`, 오늘(KST) 재구축 0.<br>• 재구축 뒤에도 남은 DRIFT는 "시도함"이 되어 다음 날부터 재구축을 부르지 않는다(지시문 §0).<br>• 이 정의가 E2E ⓑ를 가능하게 한다: `auto_rebuild_enabled=false`로 먼저 DRIFT(화면 "확인 중")를 보고, 켠 뒤 다시 돌면 "시도 안 한" DRIFT라 재구축한다(P-16) | "미해결이면 건너뜀" 그대로 — ⓑ에서 화면의 DRIFT를 볼 수 없다 |

## ① 확인한 사실 (지시문·설계서와 다른 곳)

| # | 지시문 문구 | 사실 | 처리 |
|---|---|---|---|
| P-1 | ⓪·Q1 "E2 `estimate_cycle_01`의 분급 도래" | 분급 도래 스트림은 **`estimate_cycle_05_분급_도래`**다(측정 — Q1). `estimate_cycle_01`의 ESTIMATE는 포털 Σ와 같다 | 샘플은 `estimate_cycle_01`(지시문 그대로 — ESTIMATE·CONFIRMED 각 하나) + **`estimate_cycle_05` ESTIMATE 하나**(Q1 ⓑ′의 시험 데이터 — 미리보기 몫이 든 엔진 응답) |
| P-2 | ⓪ A-1 샘플과 `estimate_cycle_01` 샘플 | 둘 다 설계사 **`A-1001`**, 둘 다 `202608`이 있다 — 같은 요청(`A-1001`·`202608`·`CONFIRMED`)에 엔진 상태에 따라 다른 답(1,800,000 / 1,890,000) | Phase 5 스텁처럼 **엔진 상태별 샘플 묶음**: `statements/a1/`·`statements/estimate-cycle-01/`·`statements/estimate-cycle-05/`. 하네스 테넌트마다 명세 상태를 고른다(GA01·GA04·GA06 = `a1`, GA02 = `estimate-cycle-01`) |
| P-3 | ① 대상 = {당월(KST), 직전 달}, ⑤ E2E "202608 DRIFT", `run.sh` "A-1 두 달 `MATCH`" | A-1의 두 달은 **202608·202702**(이웃하지 않는다), E2E·`run.sh`는 실제 시계(2026-10 → 202610·202609)로 돈다 — 창으로는 그 달들이 대상이 아니다 | CLI `--ym`(Q12). 스케줄러 창은 지시문 그대로 |
| P-4 | ⑤ ⓔ GA04 EVENT DRIFT | GA04는 포털 손 스트림이라 엔진 샘플이 없다. 스텁은 요청으로 대조하므로 GA04(`A-1001`·`202608`·`CONFIRMED`)에는 `a1` 샘플(1,800,000)이 답한다 — 포털 570,000과 다르니 BATCH DRIFT도 함께 생긴다(EVENT는 999,000) | 그대로 쓴다 — 재구축 뒤에도 둘 다 DRIFT(스트림 자체의 불일치 + 엔진 답의 불일치), `REPAIRED` 0, 미해결 게이지 1. 보고서에 "GA04의 엔진 답은 A-1 샘플"로 적는다 |
| P-5 | ⑤ "GA01" ⓐ~ⓓ, ⓒ "피드 머리를 올리고" | GA01은 다른 시험(ga01-a1·ga01-inquiry·ga01-simulation·keyboard)이 쓰고, 대사 시험 차례에는 머리가 이미 끝(14)이다 — ⓒ를 할 수 없다. 이웃 설계사(`A-2002-NBR`)에도 월 행이 있다 | **새 테넌트 GA06** = A-1 둘째 판만(이웃 없음, 명세 상태 `a1`). ⓐ~ⓓ를 GA06에서: 머리 10까지 열고 ⓐ·ⓑ, ⓒ는 머리 11 + 피드 503. GA01 시험은 그대로 |
| P-6 | ② "여전히 DRIFT면 행은 그대로(미해결)" | **재구축은 `earnings_month`를 지우고 이벤트로 다시 만든다**(`ViewReset`) — BATCH DRIFT의 `data_status = DRIFT`는 재구축 뒤 사라진다(이벤트 대사 DRIFT만 재생이 다시 만든다). 재대사가 `MATCH`가 아니면 화면이 "정상"으로 보인다 | 재대사 트랜잭션이 **`MATCH`가 아닌 모든 미해결 달**(DRIFT·INCONCLUSIVE 모두)에 `data_status = DRIFT`를 다시 쓴다 |
| P-7 | ①·② "오늘 아직 재구축하지 않았으면", ③ `status` "마지막 대사 회차 결과·재구축 마지막 결과", Q9 하루 한 번 | `recon_result`에는 `DRIFT`·`REPAIRED`만 쓴다 — 회차가 돌았는지·재구축했는지 남길 곳이 없다(감사 행을 판정에 쓰는 것은 감사의 용도가 아니다) | **V8 `recon_run`**(테넌트 RLS, 닫힌 값 `CHECK`): `(tenant_id, run_at)` PK, `actor`(`recon:<tenant>`·`cli:<user>`), `trigger`(`SCHEDULE`·`CLI`), 수만(`checked`·`matched`·`drift_new`·`drift_unresolved`·`inconclusive`·`engine_calls`), `result`(`OK`·`DRIFT`·`REPAIRED`·`FAILED`), `rebuilt_at`·`rebuild_outcome`(`SUCCEEDED`·`FAILED`·NULL). 값 열 0(금액·설계사 없음). 돌이킬 수 없는 결정 3 |
| P-8 | §0 "`gross`·`clawbackOffset`·`adjustment`를 … 동등 비교" | `StatementConfirmed.clawbackOffset`은 **선택**이다 | 있는 필드만(이벤트 대사와 같은 규칙) |
| P-9 | C8 "식별자 태그 0" | `api_jwks_failures_total{issuer_hash}`는 Phase 3 F6에서 받은 **유일한 예외**(발급자 해시)다. `tenant` 태그는 지표 전부에 있다 | `docs/지표.md`에 둘을 명시(테넌트 ID는 식별자가 아니라 차원 — 설계사·증권번호·문의 id 등이 금지), `MetricsCatalogTest`가 그 표대로 |
| P-10 | ⑤ 하네스 제어 `/control/{tenant}/corrupt/line/{calcId}`·`/control/{tenant}/recon` | 하네스 제어의 기존 모양은 `/<동작>/<테넌트>/…`(Phase 4~6) | `POST /corrupt/<T>/line/<calcId>`, `POST /cli/<T>/recon[?ym=…]`(Phase 6 `/cli/` 묶음 — 실 CLI 자식) |
| P-11 | ① 스케줄러(Q9) | 통합 시험·E2E 프로세스는 실제 시계로 돈다 — KST 04시(UTC 19시)에 CI가 돌면 앱 스케줄러가 시험 테넌트에 회차를 낸다 | `portal.recon.scheduler-enabled=false`(시험·E2E). 스케줄러 자체는 시계 주입 단위 시험 + 통합 시험 하나(시계를 04시로 고정한 앱 문맥) |
| P-12 | §0 "엔진 404 `AGENT_NOT_FOUND`…" | 포털의 `agent_id`는 엔진 사실이지만, 포털 손 스트림 설계사(이웃 `A-2002-NBR`)처럼 엔진에 없을 수 있다 | `AGENT_NOT_FOUND` 샘플의 설계사는 엔진에 없는 허구 ID(`A-9999-NONE`) — 스텁 대조용 |
| P-13 | ⓪ "`202702 ESTIMATE`(`basisSeq` 10, A-1 둘째 판 seq 11과 같은 값)" | 포털 사본 A-1 seq 11의 `basisSeq` = **10**(측정) | 엔진 샘플이 10이 아니면 보고(지시문 그대로) |
| P-14 | ③ `recon_runs_total{tenant, result}` 값 `OK`·`DRIFT`·`REPAIRED`·`FAILED` | 회차에 DRIFT도 REPAIRED도 있을 수 있다 | 회차 결과 = 우선순위 `FAILED`(재구축 실패) > `DRIFT`(미해결 남음) > `REPAIRED`(새 DRIFT 전부 해결) > `OK` — 한 회차 한 값 |
| P-15 | C2 "차이 값을 담는 열·필드·로그 0" | `recon_result`에는 포털 셋·엔진 셋 열만 있다(차이 열 없음) — 그대로. ESTIMATE 달은 `gross`를 비교하지 않으므로 그 달의 DRIFT는 없다(Q1) | 비교하지 않은 필드는 `engine_*` NULL로 둔다(무엇을 비교했는지가 행에 보이게) |

### Q1 측정 — 스트림별 엔진 합계 vs 포털 Σ라인

포털이 가진 엔진 스트림 전부(`contracts/events/v1/samples/a1/`, `golden-streams/engine-e1-cycle.jsonl`, `golden-streams/engine-e2-estimate/*` 다섯, A-1 변형 둘)에서, 명세 이벤트를 만날 때마다 그 (설계사, 달)의 `CommissionCalculated` 합(`commType ∉ {CLAWBACK, ADJUSTMENT}` → gross, `CLAWBACK` → offset)을 그 시점까지 세었다:

| 스트림 | 이벤트 | gross 엔진 / 포털 Σ | clawbackOffset 엔진 / 포털 Σ | `basisSeq` |
|---|---|---|---|---|
| A-1 둘째 판(+ 변형 둘) | seq 6 Confirmed 202608 | 1,800,000 / 1,800,000 | 0 / 0 | — |
| 〃 | seq 11 Estimated 202702 | 0 / 0 | −720,000 / −720,000 | 10 |
| `engine-e1-cycle` | Confirmed 202608·202609·202610·202611 | 넷 모두 같음 | 넷 모두 같음 | — |
| `estimate_cycle_01_기본` | seq 4 Estimated 202608 · seq 5 Confirmed | 1,890,000 / 1,890,000 (둘) | 0 / 0 | 3 |
| `estimate_cycle_02_채권_상계` | Estimated 202610 + Confirmed 셋 | 같음 | 같음 | 14 |
| `estimate_cycle_03_정정_포함` | Estimated 202609 + Confirmed 둘 | 같음 | 같음 | 9 |
| `estimate_cycle_04_음수_순액_채권화` | Estimated 202609 + Confirmed 둘 | 같음 | −1,323,000 같음 | 8 |
| **`estimate_cycle_05_분급_도래`** | **seq 7 Estimated 202801** | **378,000 / 0 — 다름** | 0 / 0 | 6 |
| 〃 | seq 10 Confirmed 202801 | 378,000 / 378,000 | 0 / 0 | — |

→ ESTIMATE `gross`에는 아직 발행되지 않은 분급 도래분(DEFERRED)이 들어 있다. 이벤트 스키마 설명문("Σ 그 달 `CommissionCalculated` 라인")과 다르다 — 엔진 쪽 설명문 문제로 보고서 질문에 올린다(E3 후보, 이 Phase는 Q1 ⓑ′로 피한다).

## ② 판정 — 순수 함수 (`portal-recon` `ReconRules`, C3)

입력: 고정 커서(상태·`last_seq`·`caught_up_at`), 회차 시작 시각, 포털 달(상태·gross·offset·adjustment), 엔진 결과(성공 payload | 404 `AGENT_NOT_FOUND` | 404 `STATEMENT_NOT_CONFIRMED` | 422 `MONTH_CLOSED` | 400 | 불가(연결·시간 초과·5xx·401/403) | 스키마 위반), 예산 남음 여부, 피드 머리(불일치일 때만).
출력: `MATCH` | `DRIFT` | `INCONCLUSIVE(사유)`. 사유는 닫힌 열거: `LAGGING`·`BUDGET`·`AGENT_NOT_FOUND`·`NOT_CONFIRMED`·`MONTH_CLOSED`·`ENGINE_REJECTED`(400·예상 밖 4xx)·`ENGINE_UNAVAILABLE`·`ENGINE_CONTRACT`(스키마 위반·정수 아님)·`ESTIMATE_DIFF`.

| 단계 | 조건 | 판정 |
|---|---|---|
| 1 | 커서 ≠ `RUNNING` 또는 `caught_up_at` 없음 또는 회차 시작 − `caught_up_at` > 창 | `INCONCLUSIVE(LAGGING)` — 엔진 호출 없음 |
| 2 | 예산(호출 수·시간) 소진 | `INCONCLUSIVE(BUDGET)` — 호출 없음 |
| 3 | 모드 = 상태로(`PROVISIONAL`·`ESTIMATED` → ESTIMATE, `CONFIRMED`·`PAID` → CONFIRMED). 엔진 답이 성공이 아니면 | 위 사유 그대로 |
| 4 | ESTIMATE: `basisSeq > 고정 last_seq` | `INCONCLUSIVE(LAGGING)` |
| 5 | ESTIMATE: `clawbackOffset`·`adjustment` 같음 / 다름 | `MATCH` / `INCONCLUSIVE(ESTIMATE_DIFF)` |
| 6 | CONFIRMED: `gross`·`adjustment`·(있으면)`clawbackOffset` 같음 | `MATCH` |
| 7 | CONFIRMED 다름 + 피드 머리 > 고정 `last_seq` | `INCONCLUSIVE(LAGGING)` |
| 8 | CONFIRMED 다름 + 머리 = 고정 `last_seq` | `DRIFT` |

시험(`ReconRulesTest`): 표의 칸마다 + 전수 조합(포털 상태 4 × 엔진 결과 9 × 커서 3 × `basisSeq` 관계 3 × 머리 관계 2), 시드 고정 임의 값(같으면 MATCH, 한 필드라도 다르면 위 표). 정수 동등 비교뿐 — 곱셈·뺄셈 0(`MultiplicationScanTest` 무변경, C2).

## ③ 회차·자동 복구 — 트랜잭션 경계 (C4·C5)

| 단계 | 트랜잭션 | 하는 일 |
|---|---|---|
| R0 | 회차 잠금 tx(회차 내내 — 권고 잠금은 세션 수준, 풀에서 연결 하나를 최대 `recon_max_seconds` 동안 — 풀 크기 ≥ 인제스트 워커 + 조회 + 1을 `docs/운영.md` §8에 한 줄, 승인 Q9) | `pg_try_advisory_lock(portal.recon, tenant)` — 못 잡으면 회차 없음(지표). 커서 고정(Q2), 대상 = `earnings_month` 행이 있는 설계사 × 대상 달 |
| R1 | 없음(엔진 호출) | 설계사 순차, 호출 간격 `recon_call_interval_ms`(기본 200), 예산 `recon_max_calls`(기본 500)·`recon_max_seconds`(기본 900). 재시도 0 |
| R2 | 설계사×달마다 한 tx | `DRIFT`: `recon_result`(BATCH, `run_at` = 회차 시작) + `earnings_month.data_status = DRIFT` + 지표. Q13(같은 값이면 행 없음). `MATCH`·`INCONCLUSIVE`: 쓰기 없음 |
| R3 | 회차 기록 tx | `recon_run` 행(수·결과 — 재구축 전 임시 결과) |
| R4 | `Rebuilder`의 txA/txB/txC 그대로 | 조건(Q14)이면 재구축, 행위자 `recon:<tenant>`(감사 `INGEST_REBUILD*` 그대로). 성공·실패를 `recon_run.rebuilt_at`·`rebuild_outcome`에(별도 짧은 tx). 실패면 커서 `FAILED`(기존 규약), 회차 결과 `FAILED`, 재대사 없음 |
| R5 | 설계사×달마다 한 tx | 미해결 DRIFT 설계사×달만 엔진 재호출(Q4) → `MATCH`: `recon_result` `REPAIRED`(`run_at` = 그 시각) + `data_status = OK` + Q1 알림(`CONFIRMED`면 `STMT_CONFIRMED:{ym}`, `PAID`면 그것과 `PAYOUT:{ym}` — `NotificationRules`의 같은 후보 함수, `ON CONFLICT DO NOTHING`). 아니면 `data_status = DRIFT`(P-6) — **`INCONCLUSIVE`로 끝난 달에는 `recon_result` 행을 쓰지 않는다**(승인 조건 A — 행은 DRIFT·REPAIRED만, 그 달이 왜 "확인 중"인지는 `recon_run` 수와 지표로만). `DRIFT`로 끝난 달은 Q13 규칙(같은 값이면 행 없음) |
| R6 | 회차 기록 tx | `recon_run.result` 확정, 게이지 `recon_unresolved_drift{tenant}` |

- 파생 함수: `NotificationRules.monthRepaired(agentId, ym, status)` — 순수 함수, 같은 키·`params`·`link`(계약 무변경). 호출하는 곳은 `Reconciler`(R5)뿐 — ArchUnit: `Rebuilder`가 닿는 전이 의존에 `notify..`·파생 포트·적용기 0(Phase 6 규칙 그대로), `Reconciler` → `Rebuilder` 허용, `Rebuilder` → `Reconciler` 0(C6).
- 결정론(C4): 같은 입력·같은 시계 → 같은 `recon_result`·`recon_run` 바이트(시험이 두 테넌트를 같은 스트림·스텁·시계로 돌려 비교).

## ④ 엔진 명세 클라이언트 (C1·C7)

- `portal-recon`의 포트 `StatementSource`(Spring 무의존) + `app`의 HTTP 어댑터 — Phase 5 `portal-sim`의 엔진 HTTP 규약을 같은 클래스로 재사용할 수 있으면 옮기고(공통 부분은 `portal-domain`이 아니라 `app`의 엔진 HTTP 지원으로), 연결·읽기 시간 초과·토큰 파일 그대로.
- 응답 검증: 200은 `StatementEstimated`/`StatementConfirmed` payload 스키마(`contracts/events/v1/payloads/` — 포털 계약)로, 모드와 모양이 맞아야 하고 정수 표기만. 아니면 `ENGINE_CONTRACT`. 오류 본문은 `Problem.code`만 읽는다(메시지는 어디에도 옮기지 않는다).
- 스텁: `StubSimulationEngine`에 명세 경로를 더하거나 같은 원천 규약의 `StubStatementEngine` — 요청(`agentId`·`ym`·`mode`)을 샘플의 요청 파일과 대조, 샘플 밖은 500(C1). 모드 `DOWN`·`SLOW`·`BROKEN`은 명세에도 같게(ⓓ).

## ⑤ 관측성 (C8·C9·C10)

- **`docs/지표.md`(신설 — 닫힌 표)**: 이름 · 종류 · 태그 키 집합 · 태그 값(닫힌 열거 또는 "테넌트 ID") · 단위 · 설명. 기존 열넷 + 새 여덟(`recon_checks_total{tenant, result}`, `recon_inconclusive_total{tenant, reason}`, `recon_unresolved_drift{tenant}`, `recon_runs_total{tenant, result}`, `notifications_derived_total{tenant, type}`, `inquiries_total{tenant, action}`, `inquiry_open_total{tenant}`, `inquiry_open_oldest_age_seconds{tenant}`). 구조화 로그 필드 집합(이벤트 키 `seq`·`eventId`, `tenant`, 사유 코드, 수)도 같은 문서에.
- **`MetricsCatalogTest`**(통합 — 앱 문맥에서 모든 지표를 한 번씩 만든 뒤 레지스트리): 이름 ↔ 태그 키 집합 양방향 = 표, 태그 값 ∈ 표의 열거, 표에만 있는 이름 = 실패(폐기 항목). JVM·Tomcat·Hikari 기본 지표는 접두 허용 목록(닫힌 — `jvm_`·`process_`·`system_`·`tomcat_`·`hikaricp_`·`jdbc_`·`executor_`·`disk_`·`application_`·`http_server_requests`·`logback_`) 밖만 본다 — 이 목록도 표에 적는다.
- **문의 지표**: `inquiries_total{action}`은 접수(`InboxUseCases`)·답변·닫기(CLI)에서. `inquiry_open_*` 게이지는 대사 회차와 `status`가 읽을 때 갱신(행 수·가장 오래된 `created_at` — 값 없음).
- **`notifications_derived_total{tenant, type}`**: 파생기·CLI 답변·재대사가 실제로 넣은 행만(`ON CONFLICT`로 0이면 세지 않는다 — `insertIfAbsent`가 삽입 수를 돌려준다).
- **트레이스 스팬**(이름 · 속성 키 — 값은 해시·수·종류만): `ingest.apply`(`tenant`·`seq`·`event.type`) → 자식 `ingest.project`·`ingest.derive`(`notifications` 수)·`ingest.cursor`; `engine.feed`·`engine.simulation`·`engine.statement`(`mode`·`outcome`); `recon.run`(`tenant`·`checked`·`drift_new`·`trigger`) → 자식 `recon.check`(`mode`·`result`)·`recon.rebuild`·`recon.recheck`. `agent_id`·`calcId`·증권번호·`ym`·본문 없음(`ym`도 뺀다 — 설계사×달 조합이 식별에 가까워진다).
- **C9 시험**: `TracingLeakIT` — in-memory exporter가 모은 스팬의 이름·속성 전부를 센티널 파일(`pii-sentinels.properties` 전 키)로 훑어 0, 속성 키 ⊆ 위 표. 로그는 기존 `CapturedOutput` 패턴.
- **`status --tenant=T|--all`**(`CliRunner` 첫 단어 `status` — 한 단어 명령): 한 줄 표 — 테넌트 · 커서 상태 · lag(seq·초) · `caught_up_at` · DRIFT 미해결 수 · 가장 오래된 미해결 달의 나이(일) · 마지막 회차 시각·결과 · 마지막 재구축 결과 · 문의 `OPEN` 수 · 가장 오래된 `OPEN` 나이(시간). 설계사·달·증권번호 없음 — 미해결 달은 **나이(일)만**, 달 자체(`ym`)는 내지 않는다(승인 조건 B — `CliRunnerTest`가 출력에 `ym` 꼴 `\d{6}` 0을 단언). 감사 `STATUS`(target `cli:status`). 종료 0·2·64.

## ⑥ V8 (`V8__recon_run.sql`, P-7)

- `recon_run` 표(위 P-7 열), RLS(V2와 같은 정책 모양), `portal_app` DML 권한, 닫힌 값 `CHECK` 셋(`trigger`·`result`·`rebuild_outcome`) — `StatusEnumDdlTest`에 `ReconTrigger`·`ReconRunResult`·`RebuildOutcome` 열거 셋.
- `recon_result`는 그대로(열 무변경 — `status`의 `MATCH` 값은 V1부터 허용이지만 쓰지 않는다; 쓰기 시험이 `MATCH` 행 0을 본다).
- 테넌트 파라미터(전부 `tenant.params` — 코드 상수 아님, 없으면 앱 기본): `recon_hour_kst`(04), `auto_rebuild_enabled`(true), `recon_caught_up_within_seconds`(900), `recon_call_interval_ms`(200), `recon_max_calls`(500), `recon_max_seconds`(900), `inquiry_retention_years`(5 — Phase 6 수용 Q5, 운영 SQL만).

## ⑦ E2E (C11)

새 테넌트 **GA06**(A-1 둘째 판, 명세 상태 `a1`, `auto_rebuild_enabled=false`로 시작 — 하네스 제어로 켠다), 하네스 제어 `POST /corrupt/<T>/line/<calcId>`, `POST /cli/<T>/recon?ym=…`(실 CLI 자식 — 종료 코드), `POST /params/<T>/auto_rebuild/on|off`. 판정은 화면(기존 DRIFT 흐림·"확인 중" 시험과 같은 단언) + 브라우저 밖 조회(알림 응답)로.

| # | 시나리오 | 테넌트 | 증거 |
|---|---|---|---|
| ⓐ | 머리 10 → `recon run --ym=202608,202702` → 종료 0, 화면 정상(202608 지급완료·흐림 없음) | GA06 | 화면 + `status`가 아니라 화면·알림 응답(지표는 통합 시험) |
| ⓑ | `corrupt line 1`(202608) → 화면 합 바뀜 → `recon run`(자동 끔) → 202608 "확인 중"·흐림 → 자동 켬 → `recon run` → 재구축 → 재대사 `REPAIRED` → 화면 정상(지급완료, 1,800,000) + 알림 응답 전후 동일(`STMT_CONFIRMED`·`PAYOUT` 추가 0) | GA06 | 화면·알림 응답 |
| ⓒ | 머리 11 + 피드 503(따라잡기 멈춤) → `recon run` → 화면 그대로(DRIFT 0), 재구축 0(알림·화면 동일) | GA06 | 화면 |
| ⓓ | 피드 정상·따라잡음 뒤 엔진 `down` → `recon run` → 종료 0, DRIFT 0 | GA06 | 화면 |
| ⓔ | GA04(`auto_rebuild_enabled=false`) `recon run --ym=202608` → "확인 중" 그대로. 켜고 다시 → 재구축 1회 → 여전히 "확인 중". 한 번 더 → 재구축 0(시도함 — Q14) | GA04 | 화면 + 하네스가 읽는 감사 행 수(`INGEST_REBUILD_SUCCEEDED` by `recon:GA04X` — 하네스 제어 `GET`은 없으므로 CLI 종료 코드 + `status` 출력의 재구축 시각을 하네스가 수로만 돌려준다) |

- 수량 증거(DRIFT·REPAIRED 행 수, 엔진 호출 수, 재구축 수)는 **통합 시험이 주 증거**다(`ReconRunIT`·`AutoRepairIT`) — E2E는 화면과 사용자에게 보이는 결과.
- 번들 무변경(화면 코드 0 — 바이트 같음, C11). 산출물 스캔·센티널 그대로.
- 실 엔진 `run.sh`: 판정 ⑤ — `recon run --tenant=GA01 --ym=202608,202702` → 두 달 `MATCH`(202608 CONFIRMED 셋 비교, 202702 ESTIMATE 둘 비교), `recon_result` 0행. `run.sh`는 이 한 단계(운영자 링크 등록 + CLI 호출)만 늘어난다(⑨ 7). 사용자 실행 — 미실행이면 그렇게 보고.

## ⑧ 완료 기준 → 시험·주입

| C | 시험 | 주입(ID 단위) |
|---|---|---|
| C1 | `verifyUpstreamContract`(명세 샘플 묶음 셋 — 엔진 병합 커밋), `StatementSamplesTest`(샘플 ↔ payload 스키마), 스텁 샘플 밖 500 | S1 샘플 한 바이트 변경 → `verifyUpstreamContract` 실패 |
| C2 | `MultiplicationScanTest`·`SqlArithmeticScanTest` **무변경 통과**, `ReconResultWriteIT`(행 = 포털 셋·엔진 셋, 비교 안 한 필드 NULL, 차이 열 없음) | S2 대사에 `portal - engine` 계산 → 곱셈·산술 스캔 |
| C3 | `ReconRulesTest`(표 전수·시드) | S3 뒤처진 커서에서 비교 진행(단계 1 제거) → 시험 실패 |
| C4 | `ReconRunIT`(MATCH·INCONCLUSIVE 행 0·지표, DRIFT 행 + `data_status` 한 tx, 결정론 — 두 테넌트 바이트 비교, Q13 같은 값 행 없음) | S4 DRIFT 행과 `data_status`를 다른 tx로 → 롤백 시험 실패 |
| C5 | `AutoRepairIT`(새 DRIFT → 재구축 감사 `recon:` → 재대사 `REPAIRED` + `OK` + 알림 추가 0 / 키 없을 때 1, 끔·오늘 이미·시도함 → 재구축 0, 재구축 실패 → `FAILED`·재대사 0, EVENT DRIFT 달도 대상, GA04 모양 = 미해결) | S5 재대사 없이 `REPAIRED` · S6 "시도함" 판정 제거(매일 재구축) |
| C6 | `ArchitectureRulesTest` 기존 규칙 무변경 + `Rebuilder` → `Reconciler` 0, 파생 함수 호출자 = 파생기·CLI 답변·`Reconciler` 닫힌 목록 | S7 `Rebuilder`가 `monthRepaired`를 부른다 |
| C7 | `ReconEngineIT`(스텁 다운·느림·404 둘·422·400·스키마 위반 → 사유별 `INCONCLUSIVE`·커서 그대로·DRIFT 0, 호출 수 = 대상 수·재시도 0·동시 1(스텁이 동시 요청 수 최대값을 센다), 예산 초과 → 남은 것 `BUDGET`·호출 0) | S8 실패 시 한 번 다시 호출 |
| C8 | `MetricsCatalogTest` | S9 태그 하나 추가 · S10 표에만 있는 지표 |
| C9 | `TracingLeakIT`, 기존 `CapturedOutput` 누출 시험 | S11 스팬 속성에 `agent_id` |
| C10 | `CliRunnerTest`(`status`·`recon run` 인자·64), `StatusCliIT`(OPERATOR 인가·출력에 식별자 원문 0·`--all` 거부 섞임 → 2) | S12 `status` 출력에 설계사 ID |
| C11 | `web-e2e` ⓐ~ⓔ × 세 프로젝트, axe·CSP·누출 0, 번들 바이트 같음 | S13 재대사가 `REPAIRED` 뒤에도 `data_status`를 `DRIFT`로 둔다 → ⓑ 화면 실패 |
| C12 | 전체 CI, `no-docker` 실패, 평문 스캔(트레이스 exporter 출력 파일 포함), 빈 `GRADLE_USER_HOME` 재생성 diff = 새 의존성 목록뿐 | — |

통합·E2E만 잡는 주입은 Phase 6처럼 초안 PR(주입 한 커밋)로 CI에서 — **통과하면 시험을 고치고 다시 넣는다**(Phase 6 F5·F12). 본문·식별자 센티널 단언은 `contains` 결과만(실패 메시지가 출력을 되풀이하지 않게 — Phase 6 수용 §2).

## ⑨ 단계 (단계마다 시험·주입·설계서 같은 커밋)

0. 엔진 PR(`work/statement-samples`): `StatementContractTest` 사례 + `-Psamples.update=true` 샘플(묶음 셋) — 자체 CI 뒤 병합(Q11). 포털: 샘플 사본·`CHECKSUMS`·`UPSTREAM`, `verifyUpstreamContract` 확장, `StatementSamplesTest`.
1. V8 + 열거 셋 + `StatusEnumDdlTest`·`TenantContextGuardTest`·`TenantPredicateScanTest`(새 문장 수).
2. `portal-recon` 판정 순수 함수 + `ReconRulesTest`.
3. 엔진 명세 클라이언트·스텁 + `ReconEngineIT`. **의존성: 트레이스 브리지·시험 exporter 추가 + 잠금·검증 메타데이터 재생성**(이 단계에서만).
4. 회차(R0~R3)·`recon run` CLI·스케줄러 + `ReconRunIT`·`ReconResultWriteIT`.
5. 자동 복구(R4~R6)·`monthRepaired` + `AutoRepairIT`·ArchUnit.
6. 관측성: 지표 여덟·`docs/지표.md`·`MetricsCatalogTest`, 스팬·`TracingLeakIT`, `status` CLI·`StatusCliIT`.
7. E2E: GA06·하네스 제어 셋·명세 스텁 상태·ⓐ~ⓔ. 실 엔진: `run.sh`가 OS 사용자의 OPERATOR 링크를 파일 SQL로 등록(`docs/운영.md` §3 문장)하고 bootJar로 `recon run --tenant=GA01 --ym=202608,202702`를 부른 뒤, `EngineFeedE2eCheck` 판정 ⑤가 마지막 `recon_run` 행(검사 2·일치 2·DRIFT 0)과 `recon_result` 0행을 본다 — 실행은 사용자.
8. 문서·보고서·태그 `phase-7`·PR(병합은 심사 뒤).

## ⑩ 설계서 v1.10·`CLAUDE.md`에서 고칠 곳

- §3.4 3(대사 흐름 확정), §5 `recon_result` 쓰기 규칙(DRIFT·REPAIRED만, 비교 안 한 필드 NULL)·V8 `recon_run`, §6.5 표에 "재대사 `REPAIRED`" 행, §6.6 세 판정·사유·Q1 ⓑ′·뒤처짐(Q2)·복구 조건(Q14)·"시도한 DRIFT는 다시 재구축하지 않는다"·P-6, §6.7 행위자 `recon:<tenant>`, §10 지표 표 → `docs/지표.md`, 트레이스, §12 Phase 7, §13 시험, §14.1 #11(자동 재구축이 밤에 도는 이유 — 긴 트랜잭션)·#12 닫기("공백은 달 알림 범위에서만 메워진다").
- `CLAUDE.md` "대사·관측성 규약" 절(대사는 정수 동등 비교뿐, 뒤처짐은 DRIFT가 아니다, ESTIMATE는 DRIFT를 내지 않는다, 재구축은 파생 0·`REPAIRED` 알림은 재대사 tx만, 지표 이름·태그 = `docs/지표.md`, 스팬 속성에 값 0).
- `docs/운영.md` §8(무엇을 보고 무엇을 하나 + 파라미터 + `status`·`recon run`), `docs/지표.md` 신설, DECISIONS P7-n, `e2e/engine-feed/README.md` 판정 ⑤.

## ⑪ 하지 않는 것

지시문 §4 전부. 더해서: ESTIMATE 달의 DRIFT(Q1 ⓑ′), 대사 결과를 화면·`/me` 응답에 새로 싣기, 엔진 소스 변경(샘플 시험 코드만), OTLP 내보내기(Phase 8), 문의 보존 삭제 잡.

## ⑫ 다음 Phase 질문 (보고서에서 다시)

Phase 8(데모 생성기·compose·Keycloak PKCE·README): compose에서 스케줄러 대신 cron `recon run`을 쓸지(Q9), OTLP 수집기(Jaeger/Tempo)를 compose에 넣을지, `run.sh`를 compose 안으로(판정 ⑤ 포함), §14.1 #14 닫기, 엔진 ESTIMATE `gross` 설명문(E3 후보 — Q1).
