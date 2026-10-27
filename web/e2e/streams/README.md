# web/e2e/streams — 포털이 만든 E2E 스트림(계약 검증 통과, 엔진이 내지 않는 형태)

포털 Phase 4 계획 ⑨(Q11). 하네스(`WebE2eHarness`)가 시작할 때 모든 줄을 계약 스키마(`EnvelopeValidator`)로 검증하고, 하나라도 거부되면 시작하지 않는다.
값은 전부 허구다. 증권번호·설계사 ID는 누출 검사의 센티널이다(화면·응답·콘솔·주소·저장소 어디에도 원문이 나오면 실패).

| 파일 | 붙는 곳 | 내용 |
|---|---|---|
| `ga01-neighbor.jsonl` | GA01 — 계약 사본 A-1 둘째 판(`contracts/events/v1/samples/a1/a1_portal_150000_e2.jsonl`, seq 1~11) 뒤 seq 12~14 | 같은 테넌트의 이웃 설계사 `A-2002-NBR`(원장·계산·전기) — A-1001의 화면에 이웃의 ID·증권번호가 0이어야 한다 |
| `edges.jsonl` | GA04 | ① 같은 달 취소 짝(`EDG-C3`가 `EDG-C1`을 취소, 202608) ② 다른 달 취소 짝(`EDG-C4`(202609)가 `EDG-C2`(202608)를 취소) ③ `CUT` 정책 삭감 30,000 ④ 202608 확정 합계 999,000 ≠ 포털 합 570,000 → `DRIFT`(독이 아니다 — 설계서 §6.6)⑤ (Phase 5, seq 11~24) 계약 `POL-EDGE-0002`(원장 `EDG-LEDGER-2`): 202701 확정 300,000·202702 잠정 200,000(홈 전월), 분급 여섯 — 202703·202709·202802(`LIMIT_DEFER`)·203001·203401(2027-02부터 84개월의 마지막 달)·203402(84개월 밖), 202709 → `HELD`(`POLICY_NOT_INFORCE`), 203001 → `CANCELLED`(`POLICY_TERMINATED` — 취소만 있는 달이라 타임라인 월 행이 없고 항목에만 남는다, 계약 1.1.1). 테넌트는 유지율 시나리오를 켠다 |
| `ga05-limits.jsonl` | GA05(포털 Phase 6 계획 Q26) | 원장 하나 `GA5-LEDGER-1`(계약 `POL-GA05-0001`, 한도 1,000,000, 202610)에 라인·전기 다섯과 재산정 하나: 2·3 500,000(50% — OK) · 4·5 350,000(85% — `LIMIT_WARN`) · 6·7 환수 −200,000(`CLAWBACK`, 65% — OK, 하향은 알림 없음) · 8·9 200,000(85% — `LIMIT_WARN` 재상향, 새 키) · 10·11 50,000(90% — 같은 단계, 알림 없음) · 12 재산정 900,000(남은 0 — 단계는 FULL이 되지만 `LIMIT_REBASE` 하나). 테넌트 `params.inquiry_rate_per_day` 3(문의 넷째 429). 손 계산은 `NotificationStreamsTest`·`NotificationDerivationIT`, 계약 검증은 `EnvelopeValidatorTest`도 Docker 없이 한다 |

GA02는 엔진 `estimate_cycle_01_기본`, GA03은 엔진 E1 cycle — 사본은 `portal-projection/src/test/resources/golden-streams/`(바이트 그대로)를 읽는다.
