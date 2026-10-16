-- =============================================================================================
-- V2__rls.sql — 테넌트 격리 2차 방어(PostgreSQL RLS)와 애플리케이션 롤 권한(설계서 §5 RLS·§9 롤 표, v1.2)
--
-- 롤(클러스터 수준, docker/postgres/init-roles.sql에서 생성 — 그 파일이 정본):
--   portal_migrator : 스키마 소유자. Flyway 전용. NOBYPASSRLS — FORCE RLS로 소유자도 정책을 따른다.
--   portal_app      : 애플리케이션 데이터소스. NOBYPASSRLS, 테이블 DML(SELECT/INSERT/UPDATE/DELETE)과 audit_access 시퀀스 USAGE만.
--                     TRUNCATE·REFERENCES·TRIGGER 권한 없음, 스키마 CREATE 권한 없음, 소유자 아님 → RLS 해제·정책 삭제 불가.
--
-- 정책: tenant_id = current_setting('app.tenant_id', true)
--   이름은 platform-spring 0.2.0의 공개 상수 TenantSession.SETTING_NAME이다(RlsSettingNameTest가 대조 — C10).
--   미설정이면 NULL(또는 트랜잭션 종료 후 빈 문자열) → 어떤 행과도 같지 않음 → 0행(오류 아님).
--   WITH CHECK도 같은 식 → 다른 테넌트 tenant_id로 INSERT/UPDATE 불가. tenant 테이블도 자기 행만.
--   값은 TenantSessionBinder가 트랜잭션마다 set_config('app.tenant_id', ?, true)로 넣는다(트랜잭션 로컬 — SET LOCAL과 같다).
-- =============================================================================================

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'tenant', 'identity_link', 'agent_profile', 'earnings_line', 'earnings_month',
        'policy_limit', 'policy_limit_history', 'deferral_item', 'clawback_item', 'policy_flag',
        'notification', 'inquiry', 'simulation_log', 'ingest_cursor', 'ingest_event',
        'recon_result', 'audit_access'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I '
            'USING (tenant_id = current_setting(''app.tenant_id'', true)) '
            'WITH CHECK (tenant_id = current_setting(''app.tenant_id'', true))', t);
        EXECUTE format('REVOKE ALL ON TABLE %I FROM PUBLIC', t);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE %I TO portal_app', t);
    END LOOP;
END
$$;

-- audit_access.id(BIGSERIAL)의 시퀀스: 행 삽입에 필요한 USAGE만.
REVOKE ALL ON SEQUENCE audit_access_id_seq FROM PUBLIC;
GRANT USAGE ON SEQUENCE audit_access_id_seq TO portal_app;

-- 애플리케이션 롤은 스키마를 쓸 수만 있다(객체 생성 불가). Flyway 이력 테이블에는 권한이 없다.
GRANT USAGE ON SCHEMA public TO portal_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON TABLE flyway_schema_history FROM portal_app;

-- 모든 포털 롤은 RLS를 우회할 수 없고 superuser가 아니다(지시문 v1.2 §4·C3). 어긋나면 마이그레이션이 실패한다.
DO $$
DECLARE
    offenders TEXT;
BEGIN
    SELECT string_agg(rolname, ', ' ORDER BY rolname) INTO offenders
      FROM pg_roles
     WHERE rolname LIKE 'portal\_%' AND (rolbypassrls OR rolsuper);
    IF offenders IS NOT NULL THEN
        RAISE EXCEPTION 'portal roles must be NOBYPASSRLS and NOSUPERUSER: %', offenders;
    END IF;
    IF (SELECT count(*) FROM pg_roles WHERE rolname IN ('portal_migrator', 'portal_app')) <> 2 THEN
        RAISE EXCEPTION 'roles portal_migrator and portal_app must exist (docker/postgres/init-roles.sql)';
    END IF;
END
$$;
