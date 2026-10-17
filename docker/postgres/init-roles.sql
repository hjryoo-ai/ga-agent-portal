-- =============================================================================================
-- init-roles.sql — 클러스터 롤과 DB 소유권(설계서 §9 롤 표의 정본). 컨테이너 최초 기동 시 superuser로 1회 실행된다
-- (docker-entrypoint-initdb.d, POSTGRES_DB=portal). Testcontainers 하네스가 같은 파일을 쓴다.
--
-- 자격 증명은 로컬 개발·테스트 전용 값이다(PostgresHarness의 상수와 짝). 운영 환경은 비밀 관리자로 주입하고 이 파일의 기본값을 쓰지 않는다
-- — 비밀번호는 psql 변수로 바꿀 수 있다(`psql -v app_password=… -f init-roles.sql`).
-- 롤을 더하면 V2__rls.sql의 단언(모든 portal_% 롤 NOBYPASSRLS·NOSUPERUSER)이 그 롤도 검사한다.
-- =============================================================================================

\if :{?migrator_password} \else \set migrator_password 'migrator_local_only' \endif
\if :{?app_password} \else \set app_password 'app_local_only' \endif
\if :{?directory_password} \else \set directory_password 'directory_local_only' \endif

-- 마이그레이션 전용(스키마 소유자). RLS 우회 권한 없음 — FORCE ROW LEVEL SECURITY로 소유자도 정책을 따른다.
CREATE ROLE portal_migrator LOGIN PASSWORD :'migrator_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;

-- 애플리케이션 데이터소스. RLS 우회·롤 전환·객체 생성 불가. 테이블 DML 권한은 V2__rls.sql이 준다.
CREATE ROLE portal_app LOGIN PASSWORD :'app_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT;

-- 테넌트 목록 전용(포털 Phase 1, 계획 승인 Q3 — 인제스트 워커가 돌 테넌트). tenant.tenant_id 컬럼 SELECT와 이 롤 전용 정책만(V3).
-- 플랫폼 TenantDirectoryReader가 이 롤로 접속한다. 테넌트 데이터는 읽지 못한다.
CREATE ROLE portal_directory LOGIN PASSWORD :'directory_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT;

-- (예약) portal_health — DB 헬스 지표 전용 롤. 포털 Phase 8에서 만든다(Phase 0은 DB 헬스 비활성, 설계서 §9).

ALTER DATABASE portal OWNER TO portal_migrator;
REVOKE ALL ON DATABASE portal FROM PUBLIC;
GRANT CONNECT ON DATABASE portal TO portal_app;
GRANT CONNECT ON DATABASE portal TO portal_directory;

ALTER SCHEMA public OWNER TO portal_migrator;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
