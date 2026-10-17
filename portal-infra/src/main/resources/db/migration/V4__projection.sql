-- =============================================================================================
-- V4__projection.sql — 포털 Phase 2(프로젝션). 설계서 §5(v1.5), 지시문 docs/phase-02-지시문.md §1, 계획 docs/phase-02-계획.md ⑧,
-- 승인 docs/phase-02-계획승인.md.
--
-- ① agent_profile: role NULL 허용(O-1, D-8 — 기본값으로 AGENT를 지어내지 않는다), status 닫힌 값.
-- ② earnings_month: 지급 런 확정값 네 컬럼(D-2) + 예상 기준 seq(D-14) + 세후의 출처 net_basis(승인 Q1).
-- ③ policy_limit_history: 키 = 이벤트 seq(D-7, DD-5) — REBASE는 전기 순번이 없을 수 있다(계약 v1.1.1 L1).
-- ④ policy_flag.reason: 플래그를 연 원 이벤트 유형(D-10 — WITHDRAW도 CANCELLED 플래그다).
-- ⑤ clawback_item.status·deferral_item.reason·status_reason: 계약 v1.2의 닫힌 어휘(엔진 열거가 정본).
-- ⑥ recon_result: 출처(D-3) + 이벤트 seq(승인 Q2) — 같은 시각의 확정·지급 DRIFT가 키에서 갈린다. 쓰기는 ON CONFLICT DO NOTHING.
--
-- 표들은 비어 있다(포털 Phase 2 전에는 뷰를 쓰는 코드가 없다) — NOT NULL 컬럼을 기본값 없이 더하고 PK를 바꿔도 안전하다. 행이 있으면
-- 이 마이그레이션이 실패한다(제약 검사는 RLS를 따르지 않는다 — V3와 같은 이유). 새 표가 없으므로 RLS·GRANT는 V2 그대로다.
-- 데이터 마이그레이션 없음.
-- =============================================================================================

ALTER TABLE agent_profile ALTER COLUMN role DROP NOT NULL;
ALTER TABLE agent_profile
    ADD CONSTRAINT ck_agent_profile_status CHECK (status IN ('ACTIVE', 'DISMISSED'));

ALTER TABLE earnings_month
    ADD COLUMN receivable_offset  BIGINT,
    ADD COLUMN carried_receivable BIGINT,
    ADD COLUMN payable            BIGINT,
    ADD COLUMN run_seq            BIGINT,
    ADD COLUMN estimate_basis_seq BIGINT,
    ADD COLUMN net_basis          TEXT CONSTRAINT ck_earnings_month_net_basis CHECK (net_basis IN ('ESTIMATE', 'CONFIRM', 'PAYOUT'));

ALTER TABLE policy_limit_history DROP CONSTRAINT policy_limit_history_pkey;
ALTER TABLE policy_limit_history ADD COLUMN source_seq BIGINT NOT NULL;
ALTER TABLE policy_limit_history ALTER COLUMN posting_seq DROP NOT NULL;
ALTER TABLE policy_limit_history ADD CONSTRAINT policy_limit_history_pkey PRIMARY KEY (tenant_id, ledger_id, source_seq);

ALTER TABLE policy_flag
    ADD COLUMN reason TEXT CONSTRAINT ck_policy_flag_reason CHECK (reason IN ('CANCEL', 'LAPSE', 'WITHDRAW', 'REDUCE', 'PAYMENT_OVERDUE'));

ALTER TABLE clawback_item
    ADD CONSTRAINT ck_clawback_item_status CHECK (status IN ('OPEN', 'OFFSET', 'CLOSED', 'WRITTEN_OFF'));
ALTER TABLE deferral_item
    ADD CONSTRAINT ck_deferral_item_reason CHECK (reason IN ('CURVE', 'LIMIT_DEFER'));
ALTER TABLE deferral_item
    ADD CONSTRAINT ck_deferral_item_status_reason CHECK (status_reason IN ('DUE_RELEASED', 'POLICY_NOT_INFORCE', 'POLICY_TERMINATED', 'SOURCE_REVERSED'));

ALTER TABLE recon_result DROP CONSTRAINT recon_result_pkey;
ALTER TABLE recon_result
    ADD COLUMN source     TEXT   NOT NULL CONSTRAINT ck_recon_result_source CHECK (source IN ('EVENT', 'BATCH')),
    ADD COLUMN source_seq BIGINT NOT NULL;
ALTER TABLE recon_result ADD CONSTRAINT recon_result_pkey PRIMARY KEY (tenant_id, agent_id, close_ym, run_at, source, source_seq);
