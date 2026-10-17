# 포털 Phase 1 수용 심사 결과 (2026-10-17) — ga-agent-portal

## 판정: 수용. PR #4 병합 승인(merge commit). `phase-1` = `b51ca65` 유지. 조건 없음

읽은 것: `phase-1` 태그의 `docs/phase-01-보고서.md` 전문, `CLAUDE.md` 인제스트 절, 커밋 목록(`main..phase-1`). 테스트 2,414(실패 0·스킵 0)는 규약대로 CI run 38963692932 아티팩트 출처로 받는다. 계약 v1.1.1 병합 `f54d008`이 엔진 E1 `UPSTREAM`과 일치함은 엔진 보고서에서 교차 확인했다.

## 1. 인정하는 것

- **F5 — `pause`의 잠금 순서 결함을 병합 전에 잡고 시험을 붙인 것.** 잠금 전에 커서를 읽으면 그 사이 워커가 커밋한 전진을 낡은 `last_seq`로 되돌릴 수 있었다. 이것은 J9와 같은 부류("경합이 있어야 드러나는 결함")이고, `commandsAreRefusedWhileAWorkerHoldsTheTenant`가 그 경합을 실제로 만든다.
- **J9의 실패 메시지가 승인에서 예측한 그대로**("second worker must not stop a healthy tenant … STOPPED, APPLY_ERROR")였다는 것 — 그리고 그 예측이 성립하려면 분류 못 한 적용 오류도 멈춰야 한다는 F1(`APPLY_ERROR`)로 이어진 것. 재시도로 덮었으면 J9는 영원히 통과했을 것이다.
- **J3·J5·J7·J8·J11에서 의도한 것 외의 시험도 함께 실패했다고 적은 것.** 어떤 규칙이 어디까지 걸리는지가 그 표에 있다.
- 토큰 센티널을 원문·16진 둘 다로 둔 것, `no-docker` 잡의 "token occurrences: 0", 폴링 기본 꺼짐(F10 — 배포 구성이 켠다).

## 2. 결정

| # | 결정 | 조건 |
|---|---|---|
| Q1 (F3) `tenant.status` | **그대로 — 두 문이 다 있어야 한다.** `tenant.status = PAUSED`는 **관리 상태**(GA 계약 중지·온보딩 전 — Phase 3부터 조회 API도 막는다), `ingest_cursor.state`는 **운영 상태**(데이터·설정 결함). 둘은 뜻이 다르고 서로를 대신하지 않는다 | 설계서 §5 `tenant.status` 주석에 그 뜻("PAUSED = 폴링·조회 모두 중지, 커서 상태와 독립")을 한 줄. Phase 3에서 조회 거부와 같은 상수 사용. |
| F1 `APPLY_ERROR` | 수용 | — |
| F2·F4·F6·F7·F8·F9·F10·F11 | 수용 | F8(파일 어댑터는 파일 순서 그대로)은 **시험용 어댑터의 성질**이고 엔진 피드 규약이 아님을 `streams/README.md`에 한 줄(Phase 8 데모 파일이 이 성질에 기대지 않게). |
| Q2 Phase 2 골든 뷰 | **계약 샘플이 아니라 엔진이 실제로 내는 스트림을 정본으로** 바꾼다 — 아래 §3 | — |
| Q3 재구축·FORCE RLS | Phase 2 계획에서 | 방향: 그림자 스키마는 `portal_migrator`가 만들고(테넌트마다 `set_config` 뒤) `portal_app`이 채운다, 스왑은 테넌트 단위 트랜잭션. 자세한 것은 Phase 2 지시문. |
| Q4 O-1 | Phase 2 V4 `agent_profile.role` NULL 허용 | 결정 그대로. |
| Q5 DD-5 | **확정 — Phase 2에서 `policy_limit_history` 키를 이벤트 `seq`로** | 설계서 §5 결함(DD-5)으로 기록. |
| Q6 실 엔진 E2E | **E2(엔진)와 함께** — 엔진 E2 지시문에 "로컬 스택(엔진 앱 + Oracle Free 컨테이너 + 시드 + 서비스 토큰) 한 명령 기동"을 넣고, 포털은 **별도 디렉터리 `e2e/engine-feed/`의 스크립트**(정규 빌드 밖, 사용자가 손으로 실행, 결과를 README "재현 기록"에)로 둔다. 포털 IT로는 두지 않는다(엔진·Oracle이 빌드 의존이 된다) | Phase 2 지시문에 자리만. |

## 3. 엔진 E1 보고서가 포털에 넘긴 것 (Phase 2 지시문의 재료 — 여기서 결정)

| # | 질문 | 결정 |
|---|---|---|
| 1 | 조직장 v2 이벤트 | 포털 §14.1에 결정 항목 추가: "① 새 이벤트 `OrgCommissionCalculated`(권장 — 기존 소비자 무영향) vs ② `recipientKind` 선택 필드". 지금 결정하지 않는다(v2). |
| 2 | `TenantSnapshot`·원장 부트스트랩 | §14.1 #4를 넓힌다: "엔진 운영 중 포털 도입(seq 0이 과거가 아닌 경우) — 설계사·원장·분급·채권의 현재 상태 스냅샷 이벤트"가 필요하다. **E2 범위 후보**(엔진 잡 하나). 포털 Phase 2는 "seq 0부터 전부 받는다"는 전제로 가고, 스냅샷 이벤트는 계약 v2 항목으로 둔다. |
| 3 | 샘플 값 | **A-1을 엔진이 재현할 수 있는 값으로 다시 쓴다.** 엔진 시드(FY 7.0 × 지급률 0.9 = 월납 × 6.3, 시책 1,000,000 정액)로 **한도 초과가 실제로 일어나는 월납**은 `6.3p + 1,000,000 > 12p` → `p < 175,438`. **월납 150,000**을 제안한다: 한도 1,800,000, FY 945,000, 시책 855,000 + 삭감 145,000. 엔진 E2 0단계가 그 입력으로 낸 스트림(`PortalA1ComparisonTest`가 jsonl로 쓰게)을 **포털 샘플 01~13의 정본으로 채택**하고, 엔진이 내지 않는 셋(`StatementEstimated`는 E2 뒤 채움, `LIMIT_DEFER` 06·13)은 A-1 순서에서 빼서 **계약 예시 디렉터리로 분리**(`samples/contract-only/`). 그러면 A-1 대조가 "차이 표"가 아니라 **두 저장소의 같은 골든**이 된다. 함께: 08에서 세액·순액 제거, 13 사유 어휘, 14 채권 금액 양수·상태 어휘, 16 `role`·`nameMasked` 제거. 계약 v1.2(아래 4와 함께) — Phase 2 0단계. |
| 4 | 피드 400 | **추가**(additive): `400 INVALID_REQUEST`(Problem). `DeferralStatusChanged.reason`·`ClawbackReceivableUpdated.status`는 **엔진 어휘로 닫힌 열거**(`DUE_RELEASED`/`POLICY_NOT_INFORCE`/`POLICY_TERMINATED`/`SOURCE_REVERSED`, `OPEN`/`OFFSET`/`CLOSED`/`WRITTEN_OFF`), 채권 `amount`·`remaining` `minimum 0`. 이것은 **좁힘**(계약 v1.2) — 포털 Phase 2 0단계에서, 엔진은 그 병합 커밋으로 `UPSTREAM` 갱신(E2 0단계). |
| 5 | 한 계산의 여러 라인이 `계산…·전기…` 순으로 온다 | 포털 프로젝터는 `calcId`로 잇고 **순서에 기대지 않는다** — Phase 2 지시문 불변 규약에 넣고, 골든 스트림에 두 순서를 다 둔다. |

## 4. 심사·지시문 측 기록 (DECISIONS)

- 지시문 D-2·D-4 누락(DE-8)과 K7(DE-7)은 계획 승인에서 이미 기록.
- Phase 1 지시문이 `tenant.status`와 커서 상태의 관계를 정하지 않았다(Q1) — 설계서 §5가 `ACTIVE/PAUSED`만 적고 뜻을 적지 않은 결함 **DD-6**.

## 5. 다음

- **포털 Phase 2(프로젝션) 지시문** — 0단계 계약 v1.2(§3-3·4) + 엔진 스트림 채택, V4(`role` NULL, `policy_limit_history` 키), 프로젝터 5종, 불변식, 재구축·결정론, `REBUILDING` 전이, E2E 스크립트 자리.
- **엔진 E2 지시문**(계획 먼저 받는 형식) — 0단계 A-1(월납 150,000) 스트림 산출 + `UPSTREAM` v1.2, `StatementEstimated` dry-run, 시뮬레이션 2종, 명세 조회, 로컬 스택, `ClosedMonthGuardedCalcStore` 조립(E1 P11), 스냅샷 부트스트랩 후보.
