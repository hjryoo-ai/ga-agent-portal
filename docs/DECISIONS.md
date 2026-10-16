# 결정 기록

설계서·지시문과 다르게 정한 것, 지시문·설계서의 오류로 판정된 것을 날짜순으로 적는다. 판정 원문은 각 항목의 출처 문서다.

## 2026-10-16 — Phase 0 계획 승인 (`docs/phase-00-계획승인.md`)

### 지시문 오류 (승인 §6)

| # | 지시문 문구 | 무엇이 틀렸나 | 정한 것 |
|---|---|---|---|
| DE-1 | v1.2 §5 "payload `oneOf` 13" | 모르는 필드를 허용하는 열린 payload(설계서 §4.5)와 양립하지 않는다 — 필드가 겹치는 payload(`StatementEstimated`/`StatementConfirmed`)는 둘 다 맞아 실패하고, `type`과 무관한 payload도 통과한다 | `type`(+`version`)별 `if/then`, 열거 밖 `type`은 거부(Q5) |
| DE-2 | v1.2 §5 "샘플(부록 A-1 seq 1~9)" | v1.0 문구의 잔재. C7 "13종"과 모순(A-1 1~9는 6종, 1~13은 9종) | A-1 seq 1~13 + 4종(Q6) |
| DE-3 | 플랫폼 0.2 정비 지시문 완료 기준·v1.2 C9의 "패키지 접근 설정" | GitHub Packages의 Maven 레지스트리는 저장소 스코프라 "Manage Actions access" 화면이 없다(세분 권한 패키지 — 컨테이너·npm 등 — 에만 있다). 다른 저장소의 `GITHUB_TOKEN`으로는 읽지 못한다. 계획 ⑥과 표지가 같은 전제를 이어받았다 | PAT(classic, `read:packages`)를 저장소 비밀 `GHP_USER`·`GHP_READ_TOKEN`으로(승인 §3) |
| DE-4 | v1.2 §6 "v1.0 (a)~(d) 그대로", "SQL 스캔 v1.0 그대로" | v1.0 §6 원문을 싣지 않았다. 원문은 승인 문서 §4 | 계획 ⑤ 추정 + 차이 D1~D3(Q7) |

### 설계서 결함 (설계서 v1.2 변경 이력에도)

| # | 무엇 | 정한 것 |
|---|---|---|
| DD-1 | v1.0·v1.1 §5 `notification`의 `UNIQUE (tenant_id, dedup_key)` ↔ §6.5 `STMT_CONFIRMED:{ym}`·`PAYOUT:{ym}`(설계사가 키에 없음) — 같은 테넌트 두 번째 설계사부터 insert 실패 | `UNIQUE (tenant_id, agent_id, dedup_key)`(Q1) |
| DD-2 | §4.2 `CommissionCalculated`의 `amount`와 `limitCutAmt`의 관계가 표에 없었다 | `amount` = 한도 삭감 뒤 지급분, 요청액 = `amount + limitCutAmt`(I1 — 부록 A-1 seq 3~5가 더해지는 유일한 읽기). 엔진이 저장하는 금액은 E1에서 확인, 계약은 포털 것 |

## 2026-10-16 — Phase 0 수용 심사 (`docs/phase-00-수용심사.md`)

### 지시문 오류 (심사 §5)

| # | 지시문 문구 | 무엇이 틀렸나 | 정한 것 |
|---|---|---|---|
| DE-5 | v1.2 §6 (g) "`java.sql`·`javax.sql`·`DataSource` 직접 참조는 플랫폼 클래스와 허용 목록만" | 플랫폼 `RowMapper`가 `ResultSet`(과 `throws SQLException`)을 요구하므로 글자 그대로는 구현할 수 없다. 지시문을 쓸 때 플랫폼 API(`API-0.2.0.txt`)와 대조하지 않았다 | 연결·문장 수준 타입은 플랫폼 클래스·허용 목록만, 그 밖에서는 결과 타입 닫힌 목록 `java.sql.ResultSet`·`java.sql.SQLException`만(어디서 쓰는지는 (d) — `TenantScopedRepository` 하위 클래스). `ArchitectureRulesTest.RESULT_LEVEL_JDBC`, 주입 A9 |
| DE-6 | 계획 승인 Q8 "전 의존성 SHA-256" | 메타데이터를 **빈 `GRADLE_USER_HOME`에서** 만들어야 한다는 조건을 적지 않았다 — 캐시에 있던 파일(BOM `.module` 5개)이 기록되지 않아 첫 CI가 실패했다(보고서 F2) | CLAUDE.md 코드 규약에 "빈 `GRADLE_USER_HOME`에서" |

### 설계서 결함 (설계서 v1.2 변경 이력에도)

| # | 무엇 | 정한 것 |
|---|---|---|
| DD-3 | §5 `simulation_log.status`에 값 집합이 없었다(다른 상태 컬럼은 주석에 있었다) | `REQUESTED`/`SUCCEEDED`/`FAILED`(보고서 F4). Phase 5에서 실패 사유가 필요해지면 값을 늘리지 않고 `error` 컬럼을 새 V로(§5 개정 같은 커밋) |
