-- =============================================================================================
-- V6__simulation_log.sql — 포털 Phase 5(분급 타임라인·시뮬레이션). 설계서 §5(v1.8), 지시문 docs/phase-05-지시문.md ②, 계획 docs/phase-05-계획.md
-- Q21(승인 2026-10-23).
--
-- simulation_log는 V1이 만들었고(상태 CHECK 포함) RLS·권한은 V2 그대로다. 여기서는 셋만 더한다.
-- ① 레이트 리밋 창 조회(그 설계사의 직전 60초 행 수)의 인덱스 (tenant_id, agent_id, created_at).
-- ② 실패 사유 열 둘(설계서 §5 — 상태 값을 늘리지 않고 열로, Phase 0 F4·DD-3): error_code(포털 사유 또는 엔진 코드 — 닫힌 형식, 값 없음),
--    engine_status(엔진이 답한 HTTP 상태). 실패 행에는 본문을 남기지 않는다.
-- ③ 값 CHECK: kind(엔진 경로 둘 — Java SimulationKind와 같다), 상태별 열 조건 — REQUESTED는 결과 열이 모두 비고, SUCCEEDED는 응답·룰 스냅샷이
--    있고 사유가 없고, FAILED는 사유가 있고 응답이 없다. 기존 행(시드 — REQUESTED, 결과 열 NULL)은 조건을 만족한다.
-- =============================================================================================

CREATE INDEX ix_simulation_log_agent_created ON simulation_log (tenant_id, agent_id, created_at);

ALTER TABLE simulation_log
    ADD COLUMN error_code    TEXT CONSTRAINT ck_simulation_log_error_code CHECK (error_code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    ADD COLUMN engine_status INTEGER CONSTRAINT ck_simulation_log_engine_status CHECK (engine_status BETWEEN 100 AND 599);

ALTER TABLE simulation_log
    ADD CONSTRAINT ck_simulation_log_kind CHECK (kind IN ('NEW_CONTRACT', 'POLICY_EVENT')),
    ADD CONSTRAINT ck_simulation_log_columns CHECK (
        (status = 'REQUESTED' AND response IS NULL AND rule_snapshot_id IS NULL AND error_code IS NULL AND engine_status IS NULL)
        OR (status = 'SUCCEEDED' AND response IS NOT NULL AND rule_snapshot_id IS NOT NULL AND error_code IS NULL)
        OR (status = 'FAILED' AND response IS NULL AND rule_snapshot_id IS NULL AND error_code IS NOT NULL));
