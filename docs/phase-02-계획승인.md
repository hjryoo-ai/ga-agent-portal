# 포털 Phase 2 계획 승인 (2026-10-17) — ga-agent-portal

## 판정: 승인. Q1~Q13 전부 권장안. 지시문 오류 넷 기록. 계약 v1.3 소형 PR(§3)은 엔진 ⓪a 파일이 오는 즉시 — Phase 2 본편과 병렬

읽은 것: `work/phase-2` = `d1d2a0a`의 `docs/phase-02-계획.md` 전문, 엔진 E2 계획(`4791307`). 두 계획의 A-1(월납 150,000) 손계산이 **독립적으로 같다**(한도 1,800,000·FY 945,000·시책 855,000+삭감 145,000·세액 59,400·순액 1,740,600·환수 −720,000·누적 1,080,000) — 엔진 ⓪a가 확인한다.

## 1. 인정하는 것

- **Q5 — 설계서 §5 불변식 "`accum_paid ≤ limit_amount`"가 A-3 감액 재산정과 모순**이라는 발견. 글자대로 구현했으면 재산정 뒤 첫 음수 전기에서 멀쩡한 테넌트가 멈췄다. 엔진 `LimitLedger.post`의 규칙(양수 전기만 상한, 누적 ≥ 0)을 그대로 가져온 것이 맞다 — **DD-8**.
- **Q4 — 불변 사실과 상태 사실을 가른 것.** 설계서 §4.2 표의 "upsert"를 전부 따랐으면 바뀐 라인 금액을 월 합산에서 조용히 고쳐 썼을 것이다. 엔진에서 `COMM_CALC`가 불변이라는 사실이 포털의 독 판정 근거가 됐다.
- **Q10 — D-11 "한 트랜잭션"의 모순**(`REBUILDING`이 커밋 전까지 보이지 않는다)을 세 트랜잭션으로 풀되 원자성은 txB가 그대로 갖게 한 것. 지시문 오류다.
- **C3의 `a1-posted-first`** — 엔진의 두 순서에서는 라인이 늘 먼저라 K2가 잡히지 않는다는 것을 보고 역전 스트림을 따로 만든 것. 주입이 실제로 무엇을 잡는지를 먼저 생각한 Phase 1 J9와 같은 자세.
- Q11(엔진 E1 `cycle` 스트림을 바이트로 들여 A-1이 덮지 않는 네 가지를 엔진 모양으로 시험), `ProjectionGoldenIT`(인메모리와 DB가 같은 행 — SQL만 틀리는 경우를 막는다), C11의 읽기 수 계수.

## 2. 결정

| # | 결정 | 조건 |
|---|---|---|
| Q1 | **`net_basis`** 승인(V4) | 열거 `ESTIMATE`/`CONFIRM`/`PAYOUT`, NULL = 세후 없음. 설계서 §6.2 표에 열 추가. |
| Q2 | **`recon_result` PK에 `source`·`source_seq`, `ON CONFLICT DO NOTHING`** 승인 | — |
| Q3 | **대상 라인에서 복사, 없으면 `UNKNOWN_REFERENCE`** | `basis.reason`은 엔진 자유 문자열 — 평문 스캔 대상 아님(설계사·고객 값이 아니다)을 README에. |
| Q4 | **불변 4종 `DUPLICATE_KEY`, 상태 3종 upsert** | 설계서 §4.2 표의 "효과" 열 정정. |
| Q5 | **엔진 규칙**: 양수 전기만 `≤ limit`, 모든 전기 `≥ 0`, 재산정 검사 없음 | DD-8. |
| Q6 | **순번 연속·`oldLimit` 대조** | — |
| Q7 | **표 밖은 독** | — |
| Q8 | **엔진 전이·짝 그대로**(`HELD→RELEASED` 없음) | 설계서 §6.2 그림 정정(DD-9: 엔진이 거부하는 전이를 설계서가 그렸다). |
| Q9 | **`data_status = REBUILDING` 미사용** | §5 주석. |
| Q10 | **세 트랜잭션** | 지시문 D-11 오류 — DE. |
| Q11 | **엔진 `cycle` 스트림을 둘째 골든으로** + 엔진 E2가 커밋할 `estimate_cycle_*.jsonl`(C2 다섯 케이스)을 **셋째**로(E2 뒤 — 그 전엔 `a1-estimated` 손 스트림) | 출처 해시 README. |
| Q12 | **시험 사례 10개로** — 지시문 "10종"은 오류(유형 6) | DE. |
| Q13 | **소형 PR — 단, R1~R8과 합쳐 계약 v1.3**(§3) | 엔진 `UPSTREAM`이 따라올 지점 하나. |
| E-2 | `DEFER_AFTER_FY` 삭감은 `deferred_total`로(D-7 그대로 — 계약의 정책값) | 설계서 §6.3에 "현재 엔진은 실제로 삭감(E1-1)" 한 줄, 화면 문구는 Phase 4. |
| ⑥ 상한 50,000 | 승인 | 파라미터. |
| 주입 K1~K14 | 승인 | — |

## 3. 선행 소형 PR — 계약 v1.3 (`work/contracts-v1.3`, **이 문서가 승인**: CI 뒤 병합, 병합 커밋을 엔진에)

엔진 E2 ⓪a 커밋(`a1_portal_150000.jsonl`)이 push되면 **즉시**(Phase 2 단계와 무관하게):

1. **A-1 채택**(Q13): `samples/a1/` 10개 → 엔진 jsonl 하나 바이트 그대로, `EventSchemaTest`·`Samples`가 jsonl 줄도 읽게, `CHECKSUMS`. 설계서 부록 A-1 값 교체.
2. **엔진 요청 R1~R8**(E2 계획 §4): R1 `NewContractRequest.insurerCd` 선택 · R2 `limitAmount`·`projectedAccum` 선택(부재 = 한도 미적용) · R3 `incentives[].limitCutAmt` 선택 + 설명 · R4 `PolicyEventRequest.elapsedInstallments`·`newMonthlyPremium` 선택 · R5 `PolicyEventResult` 뜻(부호·전후·잔액) · R6 시뮬레이션·명세 조회의 `400`·`404`(`AGENT_NOT_FOUND`·`POLICY_NOT_FOUND`·`STATEMENT_NOT_CONFIRMED`)·`422`(`UNSUPPORTED_EVENT_TYPE`·`MONTH_CLOSED`) Problem · R7 `StatementEstimated.basisSeq` 설명(하한) + 선택 `receivableOffset`·`carriedReceivable`·`payable` · R8 `getStatement` `CONFIRMED` 설명. 전부 추가·완화·설명 — `version` 1 그대로.
3. 설계서 §4.3 표 반영. DECISIONS에 **DD-10**(§4.3 동기 API가 엔진 입력(보험사 키·회차·새 보험료)을 빠뜨렸다).

Phase 2 PR은 그 병합을 merge하고 골든만 바꾼다(계획 ⑨ D-1 교체 ⓑⓒ).

## 4. 지시문 오류 (DECISIONS)

- **DE-9** D-11 "커서 `REBUILDING` → 한 트랜잭션 안에서" — 같은 트랜잭션의 상태는 관측되지 않는다(Q10).
- **DE-10** C9 "플래그 매핑 10종" — 유형은 6(Q12).
- **DE-11** D-2·D-3의 V4 목록이 `net_basis`·`recon_result.source_seq`를 빠뜨렸다(Q1·Q2).
- **DD-8** §5 불변식(Q5), **DD-9** §6.2 분급 그림(Q8), **DD-10** §4.3 동기 API 입력(§3).

## 5. 절차

승인 보관 + 계획 갱신 1커밋 → 2~5단계(엔진 파일과 무관) → 엔진 ⓪a 도착 시 **v1.3 PR**(§3, 병렬) → 6단계 교체 → 7단계. 보고서·`phase-2` 태그·PR까지, 병합은 수용 심사 뒤. 엔진 `estimate_cycle` 파일은 E2 뒤에 오므로 Phase 2에서는 `a1-estimated` 손 스트림으로 C6을 닫고, 교체는 Phase 3 0단계로.
