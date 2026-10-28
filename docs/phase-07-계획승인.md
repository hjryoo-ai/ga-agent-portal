# 포털 Phase 7 계획 승인 (2026-10-27) — ga-agent-portal

## 판정: 승인. Q1~Q14 전부 권장안(Q1 = ⓑ′). 돌이킬 수 없는 결정 셋 모두 **예**. 조건은 §2의 작은 것 둘, 계획 재제출 불필요

읽은 것: `work/phase-7` = `9d10fde`의 `docs/phase-07-계획.md` 전문(Q1~Q14, P-1~P-15, Q1 측정 표, ②~⑦).

## 1. 인정하는 것

- **Q1을 먼저 측정했다.** 포털이 가진 엔진 스트림 전부에서 명세 이벤트마다 포털 Σ를 세어 `estimate_cycle_05`(378,000 vs 0 — DEFERRED 라인은 두 이벤트 뒤)를 찾았다. 지시문이 `cycle_01`이라 짚은 것은 틀렸다(DE-27). ⓑ′의 **둘째 이유**(`basisSeq`는 하한 — 엔진 구현은 조회 시점 `headSeq`)가 ⓐ도 거짓 DRIFT를 낸다는 것까지 본 것이 좋다.
- **P-6 — 재구축이 BATCH DRIFT 표식을 지운다**(`earnings_month`를 이벤트로 다시 만든다). 지시문 ②의 "여전히 DRIFT면 행은 그대로"는 행만 보고 `data_status`를 못 봤다 — 재대사가 `MATCH`가 아닌 모든 미해결 달에 DRIFT를 다시 쓰지 않으면 화면이 "정상"을 보였을 것이다(DE-28). `INCONCLUSIVE`(엔진 다운)인 달도 DRIFT로 두는 것이 맞다 — 확인 못 한 숫자는 "확인 중"이다.
- **P-7 — 회차·재구축 사실을 둘 곳이 없다**는 것(감사 행을 판정에 쓰지 않는다)을 짚고 V8 `recon_run`을 값 열 0으로 둔 것. **Q14**의 두 정의("새 DRIFT"·"시도함")가 §0의 "미해결 DRIFT의 매일 재구축 금지"를 지키면서 E2E ⓑ에서 화면의 DRIFT를 볼 수 있게 한다.
- **Q2 7단계** — CONFIRMED 불일치를 DRIFT로 쓰기 전에 피드 머리를 한 번 읽어 `LAGGING`을 가르는 것(불일치일 때만, 재시도가 아니다).
- **P-5 — GA01에서는 ⓒ가 불가능하다**(다른 시험이 머리를 끝까지 올린다)를 보고 GA06을 둔 것, **P-4** — GA04의 엔진 답이 A-1 샘플이라 BATCH DRIFT도 같이 생긴다는 것을 미리 적은 것.
- 트레이스 속성에서 `ym`까지 뺀 것(설계사×달 조합은 식별에 가깝다), `notifications_derived_total`을 **실제로 넣은 행**만 세는 것(`insertIfAbsent` 반환값).

## 2. 결정

| # | 결정 | 조건 |
|---|---|---|
| Q1 | **ⓑ′** — ESTIMATE 달은 DRIFT 없음, `clawbackOffset`·`adjustment`만 `MATCH`/`INCONCLUSIVE(ESTIMATE_DIFF)` | §3 DD-19 |
| Q2·Q3·Q4·Q5 | 권장안 | Q3 — `engine_net` NULL(비교 안 한 것은 NULL — P-15) 그대로 |
| Q6 | **예 — 새 의존성 승인**: `spring-boot-micrometer-tracing-opentelemetry`(앱)·`opentelemetry-sdk-testing`(시험), Boot 4.1.1 BOM 판, 내보내기 0 | 빈 `GRADLE_USER_HOME` 재생성 diff — 추가만·제거 0·전이 목록 표 |
| Q7·Q8·Q9·Q10·Q12·Q13·Q14 | 권장안 | Q9 — 회차 잠금 연결은 풀에서 하나를 **회차 내내** 잡는다(최대 `recon_max_seconds`): 풀 크기와 인제스트 워커 수의 관계를 `docs/운영.md` §8에 한 줄 |
| Q11 | **예 — 엔진 샘플 PR은 엔진 CI 통과 뒤 직접 병합**(Phase 5 Q17과 같게, main 소스 변경 0) | 포털 `UPSTREAM`은 병합 커밋 |
| P-7 | **예 — V8 `recon_run`** | 값 열 0(금액·설계사 없음)은 `InboxSchemaIT` 방식의 `CHECK` 시험으로 |
| P-1~P-6, P-8~P-15 | 전부 그대로 | — |

### 작은 조건 둘
- **A.** R5에서 `INCONCLUSIVE`로 끝난 미해결 달에 `data_status = DRIFT`를 다시 쓸 때 **`recon_result` 행은 쓰지 않는다**(행은 DRIFT·REPAIRED만 — C4 그대로). 그 달이 왜 "확인 중"인지는 `recon_run`의 수와 지표로만. 시험 한 줄.
- **B.** `status` 출력의 "가장 오래된 미해결 달의 나이(일)" — 달 자체(`ym`)는 출력하지 않는다(트레이스에서 `ym`을 뺀 것과 같은 이유). 계획 ⑤ 문장이 이미 그렇게 읽히지만 시험(`CliRunnerTest`)에 `ym` 꼴 0을 넣는다.

## 3. 기록 (DECISIONS)

- **DE-27** 지시문 질문 1 "`estimate_cycle_01`" — 분급 도래 스트림은 `cycle_05`(P-1). **DE-28** 지시문 ② "여전히 DRIFT면 행은 그대로" — 재구축이 `data_status`를 지운다(P-6). 지시문 ⑤ 하네스 제어 경로 모양(P-10), ⓪ A-1·`cycle_01` 설계사가 둘 다 `A-1001`(P-2 — 상태별 샘플 묶음).
- **DD-19** 포털 계약 `contracts/events/v1/payloads/StatementEstimated` 설명문 "gross = Σ 그 달 `CommissionCalculated` 라인"이 엔진 동작(도래 예정 미리보기 포함)과 다르다. 설명문은 포털 소유이지만 엔진 사본·`CHECKSUMS`가 묶여 있으므로 **이 Phase에서 고치지 않는다** — 다음 이벤트 계약 개정(Phase 8 또는 E3) 때 "미리보기 포함"으로 표기 정정 + 엔진 사본 갱신 PR(선승인 패턴). 지금은 DECISIONS와 보고서 질문에.
- 돌이킬 수 없는 결정 셋(엔진 샘플 PR·트레이스 의존성·V8)은 사용자 대신 이 승인이 "예"를 적는다.

## 4. 절차

승인 보관 + 계획에 §2 A·B 반영 1커밋 → ⑨ 단계. 엔진 샘플 PR(엔진 CI → 직접 병합) → 포털 사본. 보고서·`phase-7` 태그·PR #28 ready, 병합은 수용 심사 뒤. `run.sh`(판정 다섯)는 사용자 실행.
