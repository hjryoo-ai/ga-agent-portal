-- =============================================================================================
-- V1__init.sql — 읽기 모델 스키마(설계서 §5, v1.2). 정본 DDL은 이 파일이다(ddl-auto 류 금지, 커밋 뒤 수정 금지 — 새 V로만).
--
-- 모든 테이블은 tenant_id를 첫 컬럼으로 갖고 PK·UNIQUE·인덱스에 포함한다. 금액은 BIGINT(정수 원).
-- v1.2에서 설계서 §5에 더한 제약(계획 S5·S6, 승인 Q1·Q9) — 컬럼은 더하지 않았다:
--   형식 CHECK(정규식): tenant_id = 플랫폼 TenantId, agent_id = 플랫폼 AgentId, close_ym·due_ym = 플랫폼 Ym(yyyyMM),
--                      event_id = Envelope 핵심의 uuid(소문자).
--   닫힌 값 CHECK(… IN …): 상태 열거 컬럼 12개(comm_type 등 열린 값은 제외). 값 목록 = Java 열거(4개) 또는 설계서 §5 주석 —
--                      StatusEnumDdlTest가 대조한다.
--   notification: UNIQUE (tenant_id, agent_id, dedup_key) — dedup_key는 설계사 안에서 유일(설계서 v1.0·v1.1 결함 정정, Q1).
--   tenant.params 기본값 = 확정된 세 키(§14.0).
--
-- 마감월 불변(불변 규약 4): status가 CONFIRMED/PAID인 earnings_month의 월에 속한 earnings_line·earnings_month는 이후 이벤트로
-- 바뀌지 않는다. Phase 0은 이 주석으로만 둔다 — 강제는 인제스트(Phase 1~2)가 위반 이벤트를 만나면 그 테넌트를 정지하는 것이다.
-- =============================================================================================

CREATE TABLE tenant (
    tenant_id        TEXT PRIMARY KEY CONSTRAINT ck_tenant_tenant_id CHECK (tenant_id ~ '^[A-Z0-9][A-Z0-9_]{0,31}$'),
    name             TEXT NOT NULL,
    engine_base_url  TEXT NOT NULL,                              -- D-2: 테넌트별 엔진 인스턴스
    status           TEXT NOT NULL CONSTRAINT ck_tenant_status CHECK (status IN ('ACTIVE', 'PAUSED')),
    limit_warn_pct   NUMERIC(5,2) NOT NULL DEFAULT 80,           -- 예시값. 포털은 SQL에서 정수 bp로 읽는다(round(x * 100)::bigint — 승인 Q2)
    limit_crit_pct   NUMERIC(5,2) NOT NULL DEFAULT 95,           -- 예시값. 같은 규약
    payout_day_rule  TEXT NOT NULL,                              -- 예: "NEXT_MONTH_D10" (지급예정일 표시용)
    params           JSONB NOT NULL DEFAULT '{"org_member_lines_visible": false, "forecast_scenario_enabled": false, "notification_digest": false}'
);

CREATE TABLE identity_link (            -- IdP subject ↔ 설계사 매핑. agent_id는 여기서만 결정된다(불변 규약 3)
    tenant_id TEXT CONSTRAINT ck_identity_link_tenant_id CHECK (tenant_id ~ '^[A-Z0-9][A-Z0-9_]{0,31}$'),
    subject   TEXT,
    agent_id  TEXT NOT NULL CONSTRAINT ck_identity_link_agent_id CHECK (agent_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'),
    PRIMARY KEY (tenant_id, subject)
);

CREATE TABLE agent_profile (
    tenant_id  TEXT CONSTRAINT ck_agent_profile_tenant_id CHECK (tenant_id ~ '^[A-Z0-9][A-Z0-9_]{0,31}$'),
    agent_id   TEXT CONSTRAINT ck_agent_profile_agent_id CHECK (agent_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'),
    name       TEXT NOT NULL,
    grade      TEXT,
    org_path   TEXT NOT NULL,                                    -- 예: /HQ1/BR03/TM07
    role       TEXT NOT NULL,                                    -- AGENT/TEAM_LEAD/BRANCH_HEAD/... (열린 값)
    status     TEXT NOT NULL,
    valid_from DATE NOT NULL,
    last_seq   BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, agent_id)
);

CREATE TABLE earnings_line (
    tenant_id           TEXT CONSTRAINT ck_earnings_line_tenant_id CHECK (tenant_id ~ '^[A-Z0-9][A-Z0-9_]{0,31}$'),
    calc_id             TEXT,
    agent_id            TEXT NOT NULL CONSTRAINT ck_earnings_line_agent_id CHECK (agent_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'),
    policy_no           TEXT NOT NULL,
    product_key         TEXT,
    comm_type           TEXT NOT NULL,                           -- FY_COMM/RENEWAL/INCENTIVE/OVERRIDE/DEFERRED/CLAWBACK/ADJUSTMENT/... (열린 값 — CHECK 없음)
    amount              BIGINT NOT NULL,                         -- 한도 삭감 뒤 지급분(환수·취소는 음수). 요청액 = amount + limit_cut_amt(I1)
    close_ym            TEXT NOT NULL CONSTRAINT ck_earnings_line_close_ym CHECK (close_ym ~ '^[0-9]{4}(0[1-9]|1[0-2])$'),
    reversal_of_calc_id TEXT,
    limit_cut_amt       BIGINT NOT NULL DEFAULT 0,
    limit_cut_policy    TEXT,
    basis               JSONB NOT NULL,                          -- rate·payoutRate는 불투명 소수 문자열(표시 전용 — 승인 Q3)
    policy_summary      JSONB NOT NULL,
    source_seq          BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, calc_id)
);
CREATE INDEX ix_line_agent_ym ON earnings_line (tenant_id, agent_id, close_ym);
CREATE INDEX ix_line_policy   ON earnings_line (tenant_id, agent_id, policy_no);

CREATE TABLE earnings_month (
    tenant_id       TEXT CONSTRAINT ck_earnings_month_tenant_id CHECK (tenant_id ~ '^[A-Z0-9][A-Z0-9_]{0,31}$'),
    agent_id        TEXT CONSTRAINT ck_earnings_month_agent_id CHECK (agent_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'),
    close_ym        TEXT CONSTRAINT ck_earnings_month_close_ym CHECK (close_ym ~ '^[0-9]{4}(0[1-9]|1[0-2])$'),
    gross           BIGINT NOT NULL DEFAULT 0,                   -- 포털 합산: Σ line(comm_type ∉ {CLAWBACK, ADJUSTMENT})
    clawback_offset BIGINT NOT NULL DEFAULT 0,                   -- 포털 합산: Σ line(CLAWBACK)
    adjustment      BIGINT NOT NULL DEFAULT 0,                   -- 포털 합산: Σ line(ADJUSTMENT)
    withholding_tax BIGINT,                                      -- 엔진 명세 스냅샷에서만 채움 (NULL = 아직 없음)
    net             BIGINT,                                      -- 엔진 명세 스냅샷에서만 채움
    status          TEXT NOT NULL CONSTRAINT ck_earnings_month_status CHECK (status IN ('PROVISIONAL', 'ESTIMATED', 'CONFIRMED', 'PAID')),
    data_status     TEXT NOT NULL DEFAULT 'OK' CONSTRAINT ck_earnings_month_data_status CHECK (data_status IN ('OK', 'DRIFT', 'REBUILDING')),
    estimate_as_of  TIMESTAMPTZ,
    confirmed_at    TIMESTAMPTZ,
    paid_at         TIMESTAMPTZ,
    paid_amount     BIGINT,
    last_seq        BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, agent_id, close_ym)
);

CREATE TABLE policy_limit (
    tenant_id        TEXT CONSTRAINT ck_policy_limit_tenant_id CHECK (tenant_id ~ '^[A-Z0-9][A-Z0-9_]{0,31}$'),
    ledger_id        TEXT,
    agent_id         TEXT NOT NULL CONSTRAINT ck_policy_limit_agent_id CHECK (agent_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'),
    policy_no        TEXT NOT NULL,
    contract_date    DATE NOT NULL,
    fy_start         DATE NOT NULL,
    fy_end           DATE NOT NULL,
    monthly_premium  BIGINT NOT NULL,
    limit_multiple   NUMERIC(6,2) NOT NULL,                      -- 표시 전용. 포털은 곱하지 않는다 — 읽을 때는 SQL에서 정수(× 100)로(승인 Q2)
    limit_amount     BIGINT NOT NULL,
    accum_paid       BIGINT NOT NULL DEFAULT 0,                  -- 엔진 accumAfter를 그대로 저장 (포털 합산 금지)
    cut_total        BIGINT NOT NULL DEFAULT 0,
    deferred_total   BIGINT NOT NULL DEFAULT 0,
    last_posting_seq BIGINT NOT NULL DEFAULT 0,
    rule_version_id  TEXT NOT NULL,
    PRIMARY KEY (tenant_id, ledger_id),
    UNIQUE (tenant_id, policy_no, agent_id)
);

CREATE TABLE policy_limit_history (       -- 원장 타임라인 (전기·재산정)
    tenant_id   TEXT CONSTRAINT ck_policy_limit_history_tenant_id CHECK (tenant_id ~ '^[A-Z0-9][A-Z0-9_]{0,31}$'),
    ledger_id   TEXT,
    posting_seq BIGINT,
    kind        TEXT NOT NULL CONSTRAINT ck_policy_limit_history_kind CHECK (kind IN ('POST', 'REBASE')),
    calc_id     TEXT,
    amount      BIGINT,
    accum_after BIGINT,
    limit_after BIGINT,
    reason      TEXT,
    occurred_at TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, ledger_id, posting_seq)
);

CREATE TABLE deferral_item (
    tenant_id        TEXT CONSTRAINT ck_deferral_item_tenant_id CHECK (tenant_id ~ '^[A-Z0-9][A-Z0-9_]{0,31}$'),
    schedule_id      TEXT,
    agent_id         TEXT NOT NULL CONSTRAINT ck_deferral_item_agent_id CHECK (agent_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'),
    policy_no        TEXT NOT NULL,
    source_calc_id   TEXT NOT NULL,
    due_ym           TEXT NOT NULL CONSTRAINT ck_deferral_item_due_ym CHECK (due_ym ~ '^[0-9]{4}(0[1-9]|1[0-2])$'),
    amount           BIGINT NOT NULL,
    pay_condition    TEXT NOT NULL,
    status           TEXT NOT NULL CONSTRAINT ck_deferral_item_status CHECK (status IN ('SCHEDULED', 'RELEASED', 'HELD', 'CANCELLED')),
    reason           TEXT NOT NULL,
    curve_version_id TEXT NOT NULL,
    status_reason    TEXT,
    last_seq         BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, schedule_id)
);
CREATE INDEX ix_defer_agent_due ON deferral_item (tenant_id, agent_id, due_ym);

CREATE TABLE clawback_item (
    tenant_id      TEXT CONSTRAINT ck_clawback_item_tenant_id CHECK (tenant_id ~ '^[A-Z0-9][A-Z0-9_]{0,31}$'),
    receivable_id  TEXT,
    agent_id       TEXT NOT NULL CONSTRAINT ck_clawback_item_agent_id CHECK (agent_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'),
    origin_calc_id TEXT NOT NULL,
    amount         BIGINT NOT NULL,
    remaining      BIGINT NOT NULL,
    status         TEXT NOT NULL,
    last_seq       BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, receivable_id)
);

CREATE TABLE policy_flag (
    tenant_id   TEXT CONSTRAINT ck_policy_flag_tenant_id CHECK (tenant_id ~ '^[A-Z0-9][A-Z0-9_]{0,31}$'),
    policy_no   TEXT,
    agent_id    TEXT CONSTRAINT ck_policy_flag_agent_id CHECK (agent_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'),
    flag_type   TEXT CONSTRAINT ck_policy_flag_flag_type CHECK (flag_type IN ('CANCELLED', 'LAPSED', 'OVERDUE', 'REDUCED')),
    occurred_on DATE NOT NULL,
    cleared_at  TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, policy_no, agent_id, flag_type)
);

CREATE TABLE notification (
    tenant_id       TEXT CONSTRAINT ck_notification_tenant_id CHECK (tenant_id ~ '^[A-Z0-9][A-Z0-9_]{0,31}$'),
    notification_id UUID,
    agent_id        TEXT NOT NULL CONSTRAINT ck_notification_agent_id CHECK (agent_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'),
    type            TEXT NOT NULL,
    severity        TEXT NOT NULL CONSTRAINT ck_notification_severity CHECK (severity IN ('INFO', 'WARN', 'CRIT')),
    dedup_key       TEXT NOT NULL,
    title           TEXT NOT NULL,
    body            TEXT NOT NULL,
    link            TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    read_at         TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, notification_id),
    UNIQUE (tenant_id, agent_id, dedup_key)                      -- dedup_key는 설계사 안에서 유일(승인 Q1)
);

CREATE TABLE inquiry (
    tenant_id   TEXT CONSTRAINT ck_inquiry_tenant_id CHECK (tenant_id ~ '^[A-Z0-9][A-Z0-9_]{0,31}$'),
    inquiry_id  UUID,
    agent_id    TEXT NOT NULL CONSTRAINT ck_inquiry_agent_id CHECK (agent_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'),
    calc_id     TEXT,
    category    TEXT NOT NULL,
    message     TEXT NOT NULL,
    status      TEXT NOT NULL CONSTRAINT ck_inquiry_status CHECK (status IN ('OPEN', 'ANSWERED', 'CLOSED')),
    created_at  TIMESTAMPTZ NOT NULL,
    answered_at TIMESTAMPTZ,
    answer      TEXT,
    answered_by TEXT,
    PRIMARY KEY (tenant_id, inquiry_id)
);

CREATE TABLE simulation_log (
    tenant_id        TEXT CONSTRAINT ck_simulation_log_tenant_id CHECK (tenant_id ~ '^[A-Z0-9][A-Z0-9_]{0,31}$'),
    sim_id           UUID,
    agent_id         TEXT NOT NULL CONSTRAINT ck_simulation_log_agent_id CHECK (agent_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'),
    kind             TEXT NOT NULL,
    request          JSONB NOT NULL,
    response         JSONB,                                      -- REQUESTED·FAILED이면 NULL일 수 있다
    rule_snapshot_id TEXT,
    status           TEXT NOT NULL CONSTRAINT ck_simulation_log_status CHECK (status IN ('REQUESTED', 'SUCCEEDED', 'FAILED')),
    created_at       TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, sim_id)
);

CREATE TABLE ingest_cursor (
    tenant_id     TEXT PRIMARY KEY CONSTRAINT ck_ingest_cursor_tenant_id CHECK (tenant_id ~ '^[A-Z0-9][A-Z0-9_]{0,31}$'),
    last_seq      BIGINT NOT NULL,
    last_event_id TEXT CONSTRAINT ck_ingest_cursor_last_event_id CHECK (last_event_id ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'),
    state         TEXT NOT NULL CONSTRAINT ck_ingest_cursor_state CHECK (state IN ('RUNNING', 'PAUSED', 'FAILED', 'REBUILDING')),
    updated_at    TIMESTAMPTZ NOT NULL,
    error         TEXT
);

CREATE TABLE ingest_event (                -- 멱등키 + 적용 감사
    tenant_id   TEXT CONSTRAINT ck_ingest_event_tenant_id CHECK (tenant_id ~ '^[A-Z0-9][A-Z0-9_]{0,31}$'),
    event_id    TEXT CONSTRAINT ck_ingest_event_event_id CHECK (event_id ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'),
    seq         BIGINT NOT NULL,
    event_type  TEXT NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    status      TEXT NOT NULL CONSTRAINT ck_ingest_event_status CHECK (status IN ('APPLIED', 'SKIPPED', 'FAILED')),
    error       TEXT,
    PRIMARY KEY (tenant_id, event_id)
);

CREATE TABLE recon_result (
    tenant_id         TEXT CONSTRAINT ck_recon_result_tenant_id CHECK (tenant_id ~ '^[A-Z0-9][A-Z0-9_]{0,31}$'),
    agent_id          TEXT CONSTRAINT ck_recon_result_agent_id CHECK (agent_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'),
    close_ym          TEXT CONSTRAINT ck_recon_result_close_ym CHECK (close_ym ~ '^[0-9]{4}(0[1-9]|1[0-2])$'),
    run_at            TIMESTAMPTZ,
    portal_gross      BIGINT,
    portal_offset     BIGINT,
    portal_adjustment BIGINT,
    engine_gross      BIGINT,
    engine_offset     BIGINT,
    engine_adjustment BIGINT,
    engine_net        BIGINT,
    status            TEXT NOT NULL CONSTRAINT ck_recon_result_status CHECK (status IN ('MATCH', 'DRIFT', 'REPAIRED')),
    PRIMARY KEY (tenant_id, agent_id, close_ym, run_at)
);

CREATE TABLE audit_access (                -- 타인 데이터 조회·관리 행위 감사
    tenant_id      TEXT CONSTRAINT ck_audit_access_tenant_id CHECK (tenant_id ~ '^[A-Z0-9][A-Z0-9_]{0,31}$'),
    id             BIGSERIAL,
    actor_subject  TEXT,
    actor_agent_id TEXT CONSTRAINT ck_audit_access_actor_agent_id CHECK (actor_agent_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'),
    action         TEXT,
    target         TEXT,
    at             TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, id)
);
