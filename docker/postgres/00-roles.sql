-- =============================================================================================
-- 00-roles.sql — compose 전용 초기화 진입점(포털 Phase 8 계획 P-15). docker-entrypoint-initdb.d에서 postgres 사용자(uid 999)로 한 번 돈다.
-- 롤 비밀번호는 생성된 비밀 파일(0600, uid 999)에서 psql 변수로 읽고(명령 인자·환경 변수에 값 0), 롤 정의의 정본 init-roles.sql을 그대로 부른다
-- — 그 파일의 *_local_only 기본값은 compose에서 쓰이지 않는다(변수가 이미 있으면 기본값을 두지 않는다).
-- =============================================================================================
\set migrator_password `cat /run/secrets/all/postgres/migrator`
\set app_password `cat /run/secrets/all/postgres/app`
\set directory_password `cat /run/secrets/all/postgres/directory`
\set issuer_password `cat /run/secrets/all/postgres/issuer`
\i /portal/init-roles.sql
