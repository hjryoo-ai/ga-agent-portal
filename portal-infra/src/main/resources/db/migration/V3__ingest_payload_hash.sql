-- =============================================================================================
-- V3__ingest_payload_hash.sql — 포털 Phase 1(인제스트). 설계서 §5 ingest_event·§9 롤 표(v1.4).
--
-- ① ingest_event.payload_hash (Phase 1 지시문 D-1, 계획 승인 Q1): SHA-256 hex(JCS({type, version, occurredAt, aggregate, payload})).
--    같은 eventId·같은 해시 → 건너뜀(행 없음, 승인 Q2), 같은 eventId·다른 해시 → 독 이벤트(HASH_MISMATCH). payload는 저장하지 않는다.
--    기본값 없이 NOT NULL을 더하므로 행이 하나라도 있으면 이 마이그레이션이 실패한다 — 제약 검사는 RLS를 따르지 않는다(마이그레이터가
--    FORCE RLS 때문에 0행을 보는 것과 무관하게 안전, Phase 0.1 B1).
-- ② 테넌트 목록 전용 롤 portal_directory (계획 승인 Q3): tenant의 tenant_id 컬럼 SELECT와 이 롤 전용 정책만. 테넌트 데이터는 워커가
--    테넌트마다 portal_app으로 바인딩해 읽는다. 롤은 docker/postgres/init-roles.sql이 만든다(마이그레이터는 NOCREATEROLE).
-- 데이터 마이그레이션 없음.
-- =============================================================================================

ALTER TABLE ingest_event
    ADD COLUMN payload_hash CHAR(64) NOT NULL
        CONSTRAINT ck_ingest_event_payload_hash CHECK (payload_hash ~ '^[0-9a-f]{64}$');

-- portal_directory: 모든 테넌트의 tenant_id만 본다(이 롤에만 적용되는 SELECT 정책 — 다른 롤은 여전히 tenant_isolation만).
CREATE POLICY tenant_directory ON tenant FOR SELECT TO portal_directory USING (true);
GRANT USAGE ON SCHEMA public TO portal_directory;
GRANT SELECT (tenant_id) ON TABLE tenant TO portal_directory;

-- 롤 단언: portal_directory가 있고 RLS 우회·superuser·상속·로그인 외 권한이 없다. 모든 portal_% 롤의 NOBYPASSRLS·NOSUPERUSER는 V2와 같이
-- 다시 본다(V2 뒤에 만든 롤도 잡는다).
DO $$
DECLARE
    offenders TEXT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'portal_directory'
                      AND rolcanlogin AND NOT rolinherit AND NOT rolcreaterole AND NOT rolcreatedb) THEN
        RAISE EXCEPTION 'role portal_directory must exist with LOGIN NOINHERIT NOCREATEROLE NOCREATEDB (docker/postgres/init-roles.sql)';
    END IF;
    SELECT string_agg(rolname, ', ' ORDER BY rolname) INTO offenders
      FROM pg_roles
     WHERE rolname LIKE 'portal\_%' AND (rolbypassrls OR rolsuper);
    IF offenders IS NOT NULL THEN
        RAISE EXCEPTION 'portal roles must be NOBYPASSRLS and NOSUPERUSER: %', offenders;
    END IF;
END
$$;
