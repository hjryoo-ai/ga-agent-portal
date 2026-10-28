-- =============================================================================================
-- V8__recon_run.sql — 포털 Phase 7(대사·DRIFT 자동 복구·관측성). 설계서 §5(v1.10), 지시문 docs/phase-07-지시문.md ①·②, 계획
-- docs/phase-07-계획.md P-7·⑥(승인 2026-10-27 — 돌이킬 수 없는 결정 3).
--
-- recon_run — 대사 회차 기록(테넌트×회차 시작 시각 한 행). recon_result에는 DRIFT·REPAIRED만 쓰므로(승인 조건 A) "오늘 이미 돌았나"(Q9)·
-- "오늘 이미 재구축했나"·"이 DRIFT 뒤에 재구축을 시도했나"(Q14)·status의 마지막 회차 결과를 둘 곳이 여기다.
-- ① 값 열 0: 금액·설계사·달이 없다 — 수(정수 ≥ 0)와 닫힌 값만. 열 집합은 ReconRunSchemaIT가 카탈로그로 대조한다.
-- ② actor: 스케줄 회차는 recon:<그 테넌트>, CLI 회차는 cli:<OS 사용자>(inquiry.answered_by와 같은 형식) — trigger와 짝.
-- ③ result(P-14 우선순위 FAILED > DRIFT > REPAIRED > OK): R3이 재구축 전 임시 값(OK·DRIFT)으로 쓰고 R4·R6이 고친다.
--    FAILED ⇔ 재구축 실패, REPAIRED는 성공한 재구축 뒤에만(재대사는 재구축 뒤에만 돈다 — Q4).
-- ④ rebuilt_at·rebuild_outcome은 함께 있거나 함께 없다.
-- ⑤ RLS·권한은 V2와 같은 모양(tenant_isolation, portal_app DML).
-- =============================================================================================

CREATE TABLE recon_run (
    tenant_id        TEXT CONSTRAINT ck_recon_run_tenant_id CHECK (tenant_id ~ '^[A-Z0-9][A-Z0-9_]{0,31}$'),
    run_at           TIMESTAMPTZ,
    actor            TEXT    NOT NULL,
    trigger          TEXT    NOT NULL CONSTRAINT ck_recon_run_trigger CHECK (trigger IN ('SCHEDULE', 'CLI')),
    checked          INTEGER NOT NULL CONSTRAINT ck_recon_run_checked CHECK (checked >= 0),
    matched          INTEGER NOT NULL CONSTRAINT ck_recon_run_matched CHECK (matched >= 0),
    drift_new        INTEGER NOT NULL CONSTRAINT ck_recon_run_drift_new CHECK (drift_new >= 0),
    drift_unresolved INTEGER NOT NULL CONSTRAINT ck_recon_run_drift_unresolved CHECK (drift_unresolved >= 0),
    inconclusive     INTEGER NOT NULL CONSTRAINT ck_recon_run_inconclusive CHECK (inconclusive >= 0),
    engine_calls     INTEGER NOT NULL CONSTRAINT ck_recon_run_engine_calls CHECK (engine_calls >= 0),
    result           TEXT    NOT NULL CONSTRAINT ck_recon_run_result CHECK (result IN ('OK', 'DRIFT', 'REPAIRED', 'FAILED')),
    rebuilt_at       TIMESTAMPTZ,
    rebuild_outcome  TEXT CONSTRAINT ck_recon_run_rebuild_outcome CHECK (rebuild_outcome IN ('SUCCEEDED', 'FAILED')),
    PRIMARY KEY (tenant_id, run_at),
    CONSTRAINT ck_recon_run_actor CHECK ((trigger = 'SCHEDULE' AND actor = 'recon:' || tenant_id)
                                         OR (trigger = 'CLI' AND actor ~ '^cli:[A-Za-z0-9][A-Za-z0-9._@-]{0,63}$')),
    CONSTRAINT ck_recon_run_counts CHECK (matched + drift_new + inconclusive <= checked),
    CONSTRAINT ck_recon_run_rebuild CHECK ((rebuilt_at IS NULL) = (rebuild_outcome IS NULL)),
    CONSTRAINT ck_recon_run_outcome CHECK ((result = 'FAILED') = (rebuild_outcome IS NOT DISTINCT FROM 'FAILED')
                                           AND (result <> 'REPAIRED' OR rebuild_outcome = 'SUCCEEDED'))
);

ALTER TABLE recon_run ENABLE ROW LEVEL SECURITY;
ALTER TABLE recon_run FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON recon_run
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));
REVOKE ALL ON TABLE recon_run FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE recon_run TO portal_app;
