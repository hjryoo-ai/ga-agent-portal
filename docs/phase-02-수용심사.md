# 포털 Phase 2 수용 심사 결과 (2026-10-17) — ga-agent-portal

## 판정: 수용. PR #7 병합 승인(merge commit). `phase-2` = `87c673f` 유지. 조건 없음

읽은 것: `phase-2` 태그의 `docs/phase-02-보고서.md` 전문, `CLAUDE.md` 프로젝션 절, `Rebuilder`의 상한·첫 페이지 `headSeq` 고정·`APPLIED` seq 두 검사(F10 수정분). 2,504건(실패 0·스킵 0)은 CI run 39006127055 아티팩트 출처로 받는다. A-1 첫 판 해시 `25929e7f…`가 엔진 E2 보고서와 일치.

## 1. 인정하는 것

- **F10 — 재구축 범위 결함 둘을 병합 전에 잡은 것.** ⓑ는 심각했다: 피드 머리가 뒤로 가면 커서가 `APPLIED` 행 아래에 서고, 워커가 그 행들을 "아는 이벤트"로 건너뛰어 **뷰에서 영영 빠진다** — Phase 1의 멱등 규칙(`SKIPPED(REPLAYED)`)이 재구축과 만나 생기는 틈이다. 지우기 전·재생 끝 두 곳에 독립된 검사를 두고(R2·R3가 서로 다른 문구로 멈춘다) 상한을 첫 `headSeq`에 고정했다(R1). 자동 보안 검토의 지적을 **도구 출력으로 보고 코드에서 성립을 확인한 뒤** 고쳤다는 문장이 규약 8 그대로다.
- **K1(DB)** — `deferred_total`을 빼먹은 저장소를 인메모리 골든은 못 보고 DB 골든만 봤다. `ProjectionGoldenIT`를 둔 이유가 첫 주입에서 증명됐다.
- **K7의 실패 메시지**("is REBUILDING — only RUNNING pauses"가 잠금 거부 대신 나옴) — 페이지마다 커밋하면 페이지 사이에 잠금이 풀린다는 것을 시험이 정확히 짚었다.
- **F11** — 설계서 §4.2 "효과" 열을 고치지 않고 변경 이력에만 적었던 것을 보고서를 쓰다 찾아 고친 것. 설계서가 정본이라는 규약을 스스로에게 적용했다.
- 재구축 IT의 규모·시간을 적고 50,000건 실측은 **하지 않았다**고 적은 것(§2).

## 2. 결정 (보고서 §5·§6)

| # | 결정 | 조건 |
|---|---|---|
| F1~F3·F5~F8·F12·F13 | 수용 | F13(한 번 `DRIFT`면 뒤 이벤트가 일치해도 `DRIFT`, 복구는 재구축) — 설계서 §6.6 그대로. |
| F9 / Q5 재생 중 알림 파생 | **기본 방향: 재생은 파생하지 않는다** — 알림은 새 사실의 신호이고 재구축은 사실을 다시 쓰는 것이다. Phase 6이 확정 | §14.1 #12에 방향 기록. |
| Q1 (F4) `limit_multiple` | **Phase 3 V5에서 `TEXT`**(계약 `decimalString` 원문) | `identity_link` 컬럼과 같은 마이그레이션. 화면은 원문 그대로. |
| Q2 엔진 E2 산출물 교체 | **계약 v1.4 소형 PR = Phase 3 0단계**(아래 §3) | — |
| Q3 `AgentSnapshot.status` 닫기 | **v1.4에서 `ACTIVE`/`DISMISSED`**(좁힘 — 엔진 `e2.1`이 어휘 시험·검증기 재실행) | — |
| Q4 50,000건 초과 | Phase 7 부하 시험에서 실측, 넘는 테넌트는 `TenantSnapshot`(계약 v2) 경로 | §14.1 #11 그대로. |
| Q6 `REINSTATE` 열린 플래그 없음 → no-op | **그대로** — 엔진 E2 보고서에 REVIVE 전제 확인이 없다. 보수적 선택 유지 | 설계서 §6.3에 한 줄. |
| Q7 `SYNC_DELAYED` | Phase 3에서: `dataStatus = SYNC_DELAYED` ⇔ 커서 상태 ∈ {`FAILED`, `PAUSED`, `REBUILDING`} 또는 `ingest_lag_seconds` > 임계(테넌트 파라미터). `asOf`는 커서 `last_seq`·`updated_at`. 재구축 중 옛 행이 정확해도 "갱신 중"을 숨기지 않는다 | Phase 3 지시문. |

## 3. 계약 v1.4 — Phase 3 0단계 (**이 문서가 승인**: 자체 CI 뒤 병합, 병합 커밋을 엔진 `e2.1`에)

| # | 변경 | 출처 |
|---|---|---|
| N1 | `engine-internal.openapi.yaml`의 `no` 키 → `'no'`. **계약 YAML 전체를 YAML 1.1·1.2 두 해석기로 읽어 키 집합·값이 같은지** 시험(`yes/no/on/off/y/n`·숫자형 문자열) | E2 §9-6 |
| N2 | `PolicyEventResult.clawbackAmount` 설명: "부호는 `CommissionCalculated`와 같다 — 해약·실효는 ≤ 0, 부활(REINSTATE) 재지급은 > 0" | E2 §9-12 |
| N3 | 시뮬레이션 응답 정의 설명: `deferral.firstYearPayout` = Σ`fyCommissionByInstallment` + Σ`incentives.amount`, `schedule`은 `dueYm`별 합산, `incentives`는 코드별 합산, `receivableRemaining` = 열린 채권 잔액 + max(0, −after), `INSURER_AMBIGUOUS` = 후보 보험사가 정확히 1곳이 아님(0 또는 2+) | E2 §9-12 |
| N4 | A-1 정본 = **둘째 판** `a1_portal_150000_e2.jsonl`(엔진 `b6b65df`, `e9a03d37…`, 첫 판이 앞부분) — `samples/a1/` 교체, 골든 뷰 `a1` 재생성(202702 `ESTIMATED` 행 추가 — 손 검산), `a1-estimated` 손 스트림 삭제 | E2 §6 |
| N5 | `contract-only/07-StatementEstimated` → 둘째 판 마지막 줄(실 샘플). 엔진 `estimate_cycle_*.jsonl` 5개를 **시험 자원**(`golden-streams/engine-e2-estimate-*/`)으로 — 계약 아님, 출처 해시 README | E2 §1 |
| N6 | `AgentSnapshot.status` 닫힌 열거 `ACTIVE`/`DISMISSED`(좁힘) | Q3 |

`CHECKSUMS` 재생성, `EventSchemaTest`. 설계서 §4.3 설명·부록 A-1 v2 반영. DECISIONS **DD-11**(계약 YAML이 1.1 해석기에서 깨지는 키를 썼다 — 설계 시 해석기 차이를 보지 않았다).

## 4. 지시문·심사 측 기록 (DECISIONS)

- Phase 2 지시문 D-11(세 트랜잭션)·C9(10종)·V4 목록은 계획 승인에서 이미 DE-9~11.
- 심사 측: 재구축의 F10 ⓑ(멱등 규칙과 재구축의 상호작용)를 지시문·계획 승인이 보지 못했다 — Phase 1 D-2(`SKIPPED(REPLAYED)`)를 승인할 때 "커서가 `APPLIED` 아래로 가는 경로"를 묻지 않았다. **RM-1**(심사 누락).

## 5. 다음

- **포털 Phase 3(조회 API·인증·인가) 지시문** — 0단계 계약 v1.4(§3), V5(`identity_link` 역할·조직, `limit_multiple TEXT`), OIDC 리소스 서버·`identity_link` 해석·`/api/v1/me/**`·IDOR·인가 매트릭스·감사·`dataStatus`(Q7)·CLI 인가 전환, 실 엔진 E2E 스크립트(`e2e/engine-feed/` — 엔진 로컬 스택 상대).
- 엔진은 `e2.1`(v1.4 `UPSTREAM`)뿐.
