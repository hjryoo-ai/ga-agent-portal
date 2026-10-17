# 골든 스트림 (포털 Phase 2 계획 ⑨, 승인 Q11)

한 줄에 Envelope 하나(머리 줄 없음 — 엔진 골든 파일과 같은 형식). 기대 뷰는 `../golden-views/<스트림 이름>/<표>.jsonl`.

| 파일 | 출처 | 내용 | 시험 |
|---|---|---|---|
| (계약) `contracts/events/v1/samples/a1/a1_portal_150000.jsonl` | 엔진 `ga-commission-engine@a124621` E2 ⓪a, SHA-256 `25929e7f…abe718` | A-1 정본 10건 — 이 디렉터리에 사본을 두지 않는다 | C1 |
| `engine-e1-cycle.jsonl` | 엔진 `ga-commission-engine@826b3e4`(`e1`) `commission-outbox/src/test/resources/outbox-golden/cycle_마감_지급_채권_정정.jsonl` 바이트 그대로, SHA-256 `407087437945683246a54f58610957919e7fc51e40cf112f819ae0164d1f64c6` | 19건: 원장 둘, 마감·지급, 음수 순액 채권화(OPEN → CLOSED), 정정 → 정정만 있는 달의 확정, 고정 시계(모든 `occurredAt` 같음) | C1·C5 반례 기반 |
| `a1-interleaved.jsonl` | A-1에서 | 순서 `1,2,4,3,5,6,7,8,9,10`(계산·전기·계산·전기), seq 1~10 다시 매김 | C3 |
| `a1-posted-first.jsonl` | A-1에서 | 순서 `1,4,5,2,3,6,7,8,10,9`(전기가 그 라인보다 먼저 — D-6 역전), seq 다시 매김 | C3·K2 |
| `a1-estimated.jsonl` | A-1에서 | 1~5 뒤 `StatementEstimated`(202608 — 세액 59,400·순액 1,740,600·basisSeq 5, eventId `…0000000000e1`, 허구) 하나를 끼우고 6~10, seq 다시 매김 | C6(예상 → 확정(세후 유지) → 지급) — 엔진 E2 `estimate_cycle`이 오면 Phase 3 0단계에서 교체 |

`GoldenStreamsTest`가 A-1에서 위 규칙으로 다시 만들어 파일과 대조하고, 엔진 사본 둘의 SHA-256을 고정한다. 정정 `reason`(엔진 자유 문자열 — 설계사·고객 값이 아니다)은
평문 스캔 대상이 아니다(승인 Q3).
