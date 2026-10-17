-- =============================================================================================
-- V5__query_api.sql — 포털 Phase 3(조회 API·인증·인가). 설계서 §5(v1.6), 지시문 docs/phase-03-지시문.md D-1·D-2·D-6·D-10, 계획
-- docs/phase-03-계획.md ⑥, 승인 docs/phase-03-계획승인.md(Q1~Q6, F6).
--
-- ① tenant 발급자 표(D-1): oidc_issuer(고유)·oidc_audience — 둘 다 NULL이면 로그인 불가 테넌트. 테넌트를 바인딩하기 전에 읽어야 하므로
--    전용 롤 portal_issuer만 세 열(tenant_id·oidc_issuer·oidc_audience)을 이 롤 전용 정책으로 본다(승인 Q1). portal_directory는 그대로.
-- ② identity_link(D-2, 승인 Q2): role·status·linked_at, org_path(인가용 조직 — 포털 사실, agent_profile.org_path는 엔진 사실·표시용).
--    agent_id NULL은 OPERATOR만. 기존 행은 Phase 0~2의 뜻 그대로 설계사 매핑 → AGENT·ACTIVE로 채운다. org_path는 채울 원천이 없다 —
--    기존 행이 있으면 ck_identity_link_org가 이 마이그레이션을 실패시킨다(Phase 0~2에는 링크를 만드는 경로가 없어 운영 행 0 — V4와 같은 이유).
-- ③ ingest_cursor.caught_up_at(승인 Q3): 회차가 피드를 받고 last_seq = headSeq로 끝난 마지막 시각 — 동기화 지연의 기준.
-- ④ audit_access.detail(승인 Q6): 거부 사유 코드(닫힌 값 — 값을 담지 않는다).
-- ⑤ policy_limit.limit_multiple → TEXT(D-10, 승인 F6): 계약 decimalString 원문. 기존 값은 지금 읽기와 같은 글자(trim_scale)로 옮기고,
--    원문 그대로는 재구축이 되돌린다(원문이 이벤트에 있다).
-- =============================================================================================

ALTER TABLE tenant ADD COLUMN oidc_issuer TEXT, ADD COLUMN oidc_audience TEXT;
ALTER TABLE tenant ADD CONSTRAINT uq_tenant_oidc_issuer UNIQUE (oidc_issuer);
ALTER TABLE tenant ADD CONSTRAINT ck_tenant_oidc_pair CHECK ((oidc_issuer IS NULL) = (oidc_audience IS NULL));
ALTER TABLE tenant ADD CONSTRAINT ck_tenant_oidc_issuer CHECK (oidc_issuer ~ '^https?://[^[:space:]]+$');

-- portal_issuer: 모든 테넌트의 발급자 세 열만 본다(이 롤에만 적용되는 SELECT 정책 — 다른 롤은 여전히 tenant_isolation만).
CREATE POLICY tenant_issuer ON tenant FOR SELECT TO portal_issuer USING (true);
GRANT USAGE ON SCHEMA public TO portal_issuer;
GRANT SELECT (tenant_id, oidc_issuer, oidc_audience) ON TABLE tenant TO portal_issuer;

-- 기존 행 채우기는 DML이 아니라 열 기본값으로(추가 뒤 기본값을 지운다 — 새 행은 값을 반드시 준다)
ALTER TABLE identity_link
    ADD COLUMN role      TEXT NOT NULL DEFAULT 'AGENT',
    ADD COLUMN status    TEXT NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN org_path  TEXT,
    ADD COLUMN linked_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE identity_link
    ALTER COLUMN role DROP DEFAULT,
    ALTER COLUMN status DROP DEFAULT,
    ALTER COLUMN linked_at DROP DEFAULT,
    ALTER COLUMN agent_id DROP NOT NULL;
ALTER TABLE identity_link
    ADD CONSTRAINT ck_identity_link_role CHECK (role IN ('AGENT', 'MANAGER', 'OPERATOR')),
    ADD CONSTRAINT ck_identity_link_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    ADD CONSTRAINT ck_identity_link_operator CHECK ((role = 'OPERATOR') = (agent_id IS NULL)),
    ADD CONSTRAINT ck_identity_link_org CHECK (role = 'OPERATOR' OR org_path IS NOT NULL);

ALTER TABLE ingest_cursor ADD COLUMN caught_up_at TIMESTAMPTZ;

ALTER TABLE audit_access ADD COLUMN detail TEXT;

ALTER TABLE policy_limit ALTER COLUMN limit_multiple TYPE TEXT USING trim_scale(limit_multiple)::text;
ALTER TABLE policy_limit ADD CONSTRAINT ck_policy_limit_limit_multiple CHECK (limit_multiple ~ '^(0|[1-9][0-9]*)(\.[0-9]{1,6})?$');

-- 롤 단언: portal_issuer가 있고 RLS 우회·superuser·상속·롤/DB 생성 권한이 없다. 모든 portal_% 롤의 NOBYPASSRLS·NOSUPERUSER는 다시 본다.
DO $$
DECLARE
    offenders TEXT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'portal_issuer'
                      AND rolcanlogin AND NOT rolinherit AND NOT rolcreaterole AND NOT rolcreatedb) THEN
        RAISE EXCEPTION 'role portal_issuer must exist with LOGIN NOINHERIT NOCREATEROLE NOCREATEDB (docker/postgres/init-roles.sql)';
    END IF;
    SELECT string_agg(rolname, ', ' ORDER BY rolname) INTO offenders
      FROM pg_roles
     WHERE rolname LIKE 'portal\_%' AND (rolbypassrls OR rolsuper);
    IF offenders IS NOT NULL THEN
        RAISE EXCEPTION 'portal roles must be NOBYPASSRLS and NOSUPERUSER: %', offenders;
    END IF;
END
$$;
