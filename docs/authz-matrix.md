> 🌐 **한국어** · [English](en/authz-matrix.md)

# API 인가 매트릭스 (정본) — 조회·시뮬레이션·알림·문의

> 포털 Phase 3(지시문 D-11, 계획 ④, 승인 Q8). 이 문서의 `authz-matrix` 블록이 정본이고 `AuthzMatrixTest`가 코드와 **양방향**으로 대조한다 —
> 행 = 컨트롤러의 `@GetMapping`·`@PostMapping`·`@PatchMapping`(Phase 6) 템플릿 집합 = `ApiAuthorization` 키 집합(`com.ga.portal.domain.access`) = `@UseCaseEntry` 값 집합. 셋 중 하나만 바뀌어도 시험이 실패한다.

열의 뜻:

- `AGENT`·`MANAGER`·`OPERATOR` — 호출자의 `identity_link.role`(토큰 클레임이 아니다). `OPERATOR`는 설계사가 없는 운영 CLI 주체라 `Caller`가 만들어지지 않는다.
- `NO_LINK` — 그 테넌트에 subject의 링크가 없다. `REVOKED` — 링크가 해제됐다.
- `SELF` = 그 설계사 자신의 행만(남의 키·없는 키 → 같은 404). `404` = 사유와 무관하게 같은 404 바이트, 감사 `AUTHZ_DENIED`(detail = `NO_LINK`·`REVOKED`·`ROLE`·`MISSING`).
- 인증 실패(토큰·발급자·서명·만료·대상·테넌트)는 이 표 앞에서 전부 같은 401이다(감사 없음, 지표만).

```authz-matrix
endpoint,AGENT,MANAGER,OPERATOR,NO_LINK,REVOKED
GET /api/v1/me,SELF,404,404,404,404
GET /api/v1/me/earnings,SELF,404,404,404,404
GET /api/v1/me/earnings/{ym},SELF,404,404,404,404
GET /api/v1/me/earnings/{ym}/lines,SELF,404,404,404,404
GET /api/v1/me/earnings/lines/{calcId},SELF,404,404,404,404
GET /api/v1/me/limits,SELF,404,404,404,404
GET /api/v1/me/limits/{ledgerId},SELF,404,404,404,404
GET /api/v1/me/deferrals,SELF,404,404,404,404
POST /api/v1/me/simulations/new-contract,SELF,404,404,404,404
POST /api/v1/me/simulations/policy-event,SELF,404,404,404,404
GET /api/v1/me/notifications,SELF,404,404,404,404
PATCH /api/v1/me/notifications/{id}/read,SELF,404,404,404,404
GET /api/v1/me/inquiries,SELF,404,404,404,404
POST /api/v1/me/inquiries,SELF,404,404,404,404
GET /api/v1/me/inquiries/{id},SELF,404,404,404,404
```

시뮬레이션 두 행(포털 Phase 5, 계약 1.1.0): `SELF` = 호출자 자신의 설계사로만 엔진에 묻는다(본문에 설계사가 없다). 계약 이벤트의 `policyKey`는 그 설계사의 라인 증권번호 집합에서만 풀리고, 남의·없는 키는 같은 404(`MISSING` — `simulation_log` 행·엔진 호출 0, `SimulationAuthzIT`).

알림·문의 다섯 행(포털 Phase 6, 계약 1.2.0): `SELF` = 호출자 설계사의 알림·문의만(모든 문장이 `agent_id = :agentId`). 남의·없는·형식이 틀린 id는 같은 404(`MISSING` — 읽음은 갱신 0행, 문의 상세는 조회 0행). 문의의 `calcId`는 그 설계사의 라인에서만 풀리고, 아니면 같은 404(`MISSING` — 행 0, 레이트 리밋 판정 전). 성공한 쓰기(읽음·접수)는 감사 행을 남기지 않는다 — 행이 기록이다(계획 Q6). 답변·종료는 API가 아니라 운영 CLI(`OPERATOR`)다.

`MANAGER`는 v2 조직 조회(`/api/v1/org/**`)의 역할이다 — 그때 이 표에 행이 생긴다(설계서 §14.1). 지금 `/api/v1/me/**`는 `AGENT`만이다.
