# 포털 Phase 6 수용 심사 결과 (2026-10-24) — ga-agent-portal

> 보관(2026-10-27): 사용자가 붙여 넣은 심사 회신 원문. PR #20은 이 회신 뒤 병합했다(merge commit `b87db17`, 태그 `phase-6` = `f865034` 유지 — `main`에서 도달).

## 판정: 수용. PR #20 병합 승인(merge commit). `phase-6` = `f865034` 유지. 조건 없음

읽은 것: `phase-6`의 `docs/phase-06-보고서.md` 전문, `CLAUDE.md` "알림·문의 규약" 절, `IngestApplier`(파생 호출이 프로젝터 `try` 밖·커서 앞 — 바깥 `catch`가 `APPLY_ERROR`), `NotificationRules`(sealed `Change` 전수 `switch`, 상향 = `stage(후) > stage(전)` 하나, 키에 `e.seq()`), `web/src/notify/links.ts`(승인 A — `ROUTE_PATHS` 타입 + 모양 + `matchPath` + 조각 정규식, `.`·`..` 거부). 3,353 + 135 + 67(실패 0·스킵 0), axe 144 상태 0, 누출 0, 번들 123,612 B, 동시성 `admitted=3 limited=5`, 재구축 전후 응답 동일은 CI run 40094460151과 초안 PR #21~#27 아티팩트 출처로 받는다.

## 1. 인정하는 것

- **S8·S14가 첫 판에서 CI를 통과한 것을 숨기지 않고 "시험의 구멍"으로 적은 것**(F5·F12), 그리고 둘을 막은 뒤 **다시 주입해** 실패를 본 것. 주입 절차의 목적이 바로 이것이다 — 통과하는 시험이 무엇을 안 보는지 찾는 것. F5의 "`45a32d4` 커밋 문구는 틀렸다 — 시험 없이 추론했다"는 문장도 그대로 둔다.
- **F3 — CLI `list`가 설계사의 글을 터미널에 그대로 냈다**(CWE-150)를 커밋 전 보안 검토에서 잡고, OSC·CSI·C1·RLO·영폭·BMP 밖 태그 문자까지 바이트로 시험한 것. 지시문·계획 어디에도 없었다. 설계사 입력이 운영자 화면에 닿는 유일한 길이라 맞는 자리다.
- **C7의 닫힌 목록이 정말 닫혔다**: 본문이 나온 곳 = 작성자 상세 응답 하나 + CLI `list` + 작성·상세 화면 DOM. `inquiryBodies 6`(허용된 곳에 실제로 있었다)로 E7이 공허하지 않음을 보인 것(P6-15).
- **Q4의 sealed `Change`** — 프로젝터가 읽은 행을 돌려주어 파생기가 뷰를 다시 읽지 않고, 변이가 늘면 규칙의 `switch`가 컴파일에서 받는다. 재구축 금지는 ArchUnit **전이 의존**으로 고정했다(S4가 `replayOne`에서 `candidates`만 불러도 잡혔다).
- **S10의 세 모양** — 호출 제거는 단위 시험이, 문장 변조는 `TenantPredicateScanTest`가 먼저 잡아, 통합 시험의 증거가 되는 모양(`txid_current()` 키)을 찾아 넣은 것. 정적 검사가 먼저 잡는 주입은 통합 증거가 아니라는 구분이 정확하다.
- **F7·F8** — 하네스 클래스패스로 앱을 띄우면 `@TestConfiguration`이 빈이 된다는 것을 보고 bootJar(운영자 명령 그대로)로 바꾼 것, 재구축 제어가 워커 잠금과 겹칠 때 **재구축 쪽을 고치지 않고** 제어가 다시 치게 한 것(잠금 경합은 정상 동작).
- `NotificationRulesTest` 524 — 16쌍 전수 + 시드 전기열의 "후보 수 = 경계 통과 수" 신탁. GA05가 하향 → 재상향을 **실제 환수 전기**로 만든 것.

## 2. 결정 (보고서 §5·§6)

| # | 결정 | 비고 |
|---|---|---|
| F1·F2·F4·F6·F10·F11·F13 | 수용 | F13(주입 run의 AssertJ 메시지에 본문이 실림)은 닫은 PR의 실패 경로뿐 — 정규 run 0. 다만 **다음 Phase부터 본문 센티널 단언은 `.describedAs` 없이 `contains` 결과만 단언**해 실패 메시지가 출력을 되풀이하지 않게(작은 습관, 조건 아님) |
| F5·F12 | 수용 — §1 | — |
| F9 문의 보존 | §3 Q5 | — |
| P6-1~18 | 전부 수용 | P6-11(bootJar)·P6-12(재시도 5)는 해석이 맞다. P6-8(이스케이프)은 발견이다 |

## 3. 다음 Phase 질문 — 결정 (Phase 7 지시문 재료)

| # | 결정 |
|---|---|
| Q1 `REPAIRED` 달의 확정·지급 알림 | **낸다 — 같은 키**(`STMT_CONFIRMED:{ym}`·`PAYOUT:{ym}`, `ON CONFLICT` — 한 번만). 파생 지점은 재대사가 `REPAIRED`를 쓰는 **그 트랜잭션**(§6.5 표에 "재대사 `REPAIRED`" 행 — `INQUIRY_ANSWERED`처럼 이벤트 아닌 둘째 파생). 재구축은 여전히 파생하지 않는다 — 재대사와 재구축은 다른 트랜잭션이다(재구축 txB가 아니라 그 뒤의 재대사). "확인 완료" 같은 새 종류는 두지 않는다(설계사에게는 "확정됐다"가 사실). |
| Q2 재구축이 처음 적용한 사실의 알림 | **메우지 않는다.** 재대사는 달 단위 합산만 보므로 라인·원장·분급 알림을 복원할 근거가 없고, 달 알림(Q1)만 자연히 메워진다. §14.1 #12에 "공백은 달 알림(Q1) 범위에서만 메워진다"로 닫는다. |
| Q3 운영 알림 채널 | v1 채널은 **지표 + 운영 CLI `status`**(이메일·웹훅은 v2). 대상: DRIFT 수·`APPLY_ERROR`·커서 정지·`REPAIRED` 실패 + **문의 적체**(`inquiry_open_oldest_age_seconds{tenant}`, `inquiry_open_total{tenant}`). |
| Q4 지표 태그 집합 | **지금 고정**: `notifications_derived_total{tenant, type}`, `inquiries_total{tenant, action}`(`action` = `CREATED`·`ANSWERED`·`CLOSED`), `recon_runs_total{tenant, result}`(`result` = `OK`·`DRIFT`·`REPAIRED`·`FAILED`). 한 이름에 한 집합(P6-9). |
| Q5 문의 보존 | **감사와 같은 기간**(예시값 5년, 테넌트 파라미터 `inquiry_retention_years`) — 분쟁 기록이다. 삭제 잡 없음, 운영 SQL. |
| Q6 트레이스 | 적용 트랜잭션 = 스팬 하나, 프로젝터·파생·커서는 자식 스팬. 속성에 값 0(seq·eventId·종류·수만). |

## 4. 기록 (DECISIONS)

- 지시문 오류는 계획 승인에서 적은 DE-25·DE-26, 설계서 DD-18 그대로. 이 심사에서 새 RM·DE 없음.
- 사용자 직접 승인 세 번째 Phase — 사후 확인, 뒤집은 것 없음.

## 5. 다음

- 사용자: PR #20 병합 → `main`에서 `ENGINE_REPO=<엔진 commission-system 경로> e2e/engine-feed/run.sh` → `RESULT.md`(판정 넷 — 알림은 A-1과 같으면 여섯 종류 각 1).
- 다음 지시문: **Phase 7(대사·DRIFT 자동 복구·관측성) — E2 형식**, §3이 재료. 엔진 쪽은 `GET /internal/v1/agents/{agentId}/statements/{ym}`(E2가 구현, 계약 1.4.0) 사용만 — 엔진 변경 없음 예상.
