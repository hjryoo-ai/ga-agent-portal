#!/usr/bin/env bash
# 실 엔진 피드 E2E(포털 Phase 3 지시문 D-16) — 정규 빌드 밖, 사용자가 손으로 실행한다. README.md.
#   ENGINE_REPO=<엔진 저장소 commission-system 경로> e2e/engine-feed/run.sh
# ① 엔진 localstack/up.sh(Oracle + 시드 + A-1 둘째 판 + 엔진 앱 :8080, 테넌트 GA01) ② 일회용 PostgreSQL 컨테이너(고정 digest, 이 스크립트 전용
# 이름·포트, 볼륨 없음) ③ 시험 발급기(:portal-app:runTestIssuer) ④ 포털(인제스트 켬, :8081) ⑤ 테넌트 GA01·발급자·설계사 링크 등록(파일 SQL)
# ⑥ 커서 caught_up_at(= ingest_lag_seq 0) 대기 ⑦ 대사(포털 Phase 7 — 실행한 OS 사용자의 OPERATOR 링크를 파일 SQL로 등록하고 bootJar로
# `recon run --tenant=GA01 --ym=202608,202702`) ⑧ 판정(:portal-app:e2eCheck) → RESULT.md. 판정에는 시뮬레이션 한 쌍(포털 Phase 5 — 신계약 S1·해약 영향 S5를
# 포털 API로 보내 실 엔진이 답하고, 포털 응답이 계약을 통과하고, simulation_log에 SUCCEEDED 둘)이 들어 있다. 끝나면 포털 쪽 프로세스·컨테이너와 엔진 스택을 내린다.
# 토큰·subject·비밀은 .state/ 파일(권한 600)에만 — 셸 인자·환경 변수·출력에 싣지 않는다. 사용자의 compose 볼륨·~/.m2는 건드리지 않는다.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
STATE="$HERE/.state"
PG_NAME=ga-portal-e2e-pg
PG_PORT="${E2E_PG_PORT:-55432}"
PORTAL_PORT="${E2E_PORTAL_PORT:-8081}"
ENGINE_PORT=8080
TENANT=GA01

: "${ENGINE_REPO:?ENGINE_REPO(엔진 저장소의 commission-system 경로)를 지정한다}"
test -x "$ENGINE_REPO/localstack/up.sh" || { echo "[e2e] $ENGINE_REPO/localstack/up.sh가 없다" >&2; exit 2; }

PG_IMAGE="$(sed -n 's/^postgres-image *= *"\(.*\)".*$/\1/p' "$ROOT/gradle/libs.versions.toml")"
test -n "$PG_IMAGE" || { echo '[e2e] gradle/libs.versions.toml에 postgres-image가 없다' >&2; exit 2; }

migrator_psql() {   # 로컬 기본 자격 증명(docker/postgres/init-roles.sql) — 일회용 컨테이너 안에서만
  docker exec -i -e PGPASSWORD=migrator_local_only "$PG_NAME" psql -h 127.0.0.1 -U portal_migrator -d portal -v ON_ERROR_STOP=1 -qAt "$@"
}

cleanup() {
  set +e
  for f in portal.pid issuer.pid; do
    if [ -f "$STATE/$f" ]; then kill "$(cat "$STATE/$f")" 2> /dev/null; rm -f "$STATE/$f"; fi
  done
  docker rm -f "$PG_NAME" > /dev/null 2>&1
  if [ "${E2E_KEEP_ENGINE:-0}" != 1 ]; then "$ENGINE_REPO/localstack/up.sh" down > /dev/null 2>&1; fi
}
trap cleanup EXIT

rm -rf "$STATE"
mkdir -p "$STATE/tokens"
chmod 700 "$STATE" "$STATE/tokens"

echo '[e2e] ① 엔진 로컬 스택'
"$ENGINE_REPO/localstack/up.sh" up

echo "[e2e] ② PostgreSQL 일회용 컨테이너($PG_NAME, 127.0.0.1:$PG_PORT)"
docker rm -f "$PG_NAME" > /dev/null 2>&1 || true
( umask 077; printf 'POSTGRES_DB=portal\nPOSTGRES_PASSWORD=%s\n' "$(openssl rand -hex 16)" > "$STATE/pg.env" )
docker run -d --name "$PG_NAME" -p "127.0.0.1:$PG_PORT:5432" --env-file "$STATE/pg.env" \
  -v "$ROOT/docker/postgres/init-roles.sql:/docker-entrypoint-initdb.d/init-roles.sql:ro" "$PG_IMAGE" > /dev/null
for _ in $(seq 1 60); do
  if migrator_psql -c 'SELECT 1' > /dev/null 2>&1; then break; fi
  sleep 1
done
migrator_psql -c 'SELECT 1' > /dev/null

echo '[e2e] ③ 비밀·토큰 파일(.state, 600)'
( umask 077
  cp "$ENGINE_REPO/localstack/.state/engine-token" "$STATE/tokens/$TENANT"
  openssl rand 32 > "$STATE/policy-key"
  printf 'e2e-%s' "$(openssl rand -hex 8)" > "$STATE/subject" )

echo '[e2e] ④ 시험 발급기'
( cd "$ROOT" && ./gradlew -q --no-daemon :portal-app:runTestIssuer --args="$STATE" > "$STATE/issuer.log" 2>&1 ) &
for _ in $(seq 1 180); do
  if [ -f "$STATE/issuer-url" ]; then break; fi
  sleep 1
done
test -f "$STATE/issuer-url" || { echo '[e2e] 발급기 준비 시간 초과 — .state/issuer.log' >&2; exit 1; }

echo "[e2e] ⑤ 포털 기동(:$PORTAL_PORT, 인제스트 켬)"
( cd "$ROOT" && ./gradlew -q --no-daemon :portal-app:bootJar )
jar="$(ls "$ROOT"/portal-app/build/libs/portal-app-*.jar | grep -v -- '-plain' | head -1)"
PORTAL_DB_URL="jdbc:postgresql://127.0.0.1:$PG_PORT/portal" PORTAL_INGEST_ENABLED=true PORTAL_RECON_SCHEDULER_ENABLED=false \
  PORTAL_ENGINE_TOKEN_DIR="$STATE/tokens" PORTAL_POLICY_KEY_SECRET_FILE="$STATE/policy-key" \
  nohup java -Dfile.encoding=UTF-8 -jar "$jar" --server.port="$PORTAL_PORT" > "$STATE/portal.log" 2>&1 &
echo $! > "$STATE/portal.pid"
for _ in $(seq 1 120); do
  if curl -sf "http://localhost:$PORTAL_PORT/actuator/health" > /dev/null; then break; fi
  kill -0 "$(cat "$STATE/portal.pid")" 2> /dev/null || { echo '[e2e] 포털이 종료됐다 — .state/portal.log' >&2; tail -n 40 "$STATE/portal.log" >&2; exit 1; }
  sleep 2
done
curl -sf "http://localhost:$PORTAL_PORT/actuator/health" > /dev/null

echo "[e2e] ⑥ 테넌트 $TENANT·발급자·설계사 링크 등록(파일 SQL — docs/운영.md §1·§2)"
( umask 077
  issuer="$(cat "$STATE/issuer-url")"
  subject="$(cat "$STATE/subject")"
  cat > "$STATE/register.sql" <<SQL
BEGIN;
SELECT set_config('app.tenant_id', '$TENANT', true);
INSERT INTO tenant (tenant_id, name, engine_base_url, status, payout_day_rule, oidc_issuer, oidc_audience)
VALUES ('$TENANT', 'E2E GA', 'http://localhost:$ENGINE_PORT', 'ACTIVE', 'NEXT_MONTH_D10', '$issuer', 'portal-e2e');
INSERT INTO identity_link (tenant_id, subject, agent_id, role, status, org_path, linked_at)
VALUES ('$TENANT', '$subject', 'A-1001', 'AGENT', 'ACTIVE', '/HQ1', now());
COMMIT;
SQL
)
migrator_psql < "$STATE/register.sql" > /dev/null

echo '[e2e] ⑦ 인제스트 따라잡기 대기(caught_up_at — last_seq = headSeq·RUNNING)'
cat > "$STATE/cursor.sql" <<SQL
BEGIN;
SELECT set_config('app.tenant_id', '$TENANT', true) AS bound \\gset
SELECT last_seq || ' ' || state FROM ingest_cursor WHERE tenant_id = '$TENANT' AND caught_up_at IS NOT NULL;
COMMIT;
SQL
cursor=''
for _ in $(seq 1 120); do
  cursor="$(migrator_psql < "$STATE/cursor.sql" || true)"
  if [ -n "$cursor" ]; then break; fi
  sleep 2
done
test -n "$cursor" || { echo '[e2e] 따라잡기 시간 초과 — .state/portal.log' >&2; exit 1; }
echo "[e2e]    커서: $cursor"

echo '[e2e] ⑧ 대사 — 운영자 링크(파일 SQL — docs/운영.md §3) + recon run(실 CLI, cli 프로파일)'
os_user="$(id -un)"
[[ "$os_user" =~ ^[A-Za-z0-9][A-Za-z0-9._@-]{0,63}$ ]] || { echo '[e2e] OS 사용자 이름이 운영자 subject 형식이 아니다' >&2; exit 2; }
( umask 077
  cat > "$STATE/operator.sql" <<SQL
BEGIN;
SELECT set_config('app.tenant_id', '$TENANT', true);
INSERT INTO identity_link (tenant_id, subject, agent_id, role, status, org_path, linked_at)
VALUES ('$TENANT', 'cli:$os_user', NULL, 'OPERATOR', 'ACTIVE', NULL, now());
COMMIT;
SQL
)
migrator_psql < "$STATE/operator.sql" > /dev/null
set +e
PORTAL_DB_URL="jdbc:postgresql://127.0.0.1:$PG_PORT/portal" PORTAL_INGEST_ENABLED=false \
  PORTAL_ENGINE_TOKEN_DIR="$STATE/tokens" PORTAL_POLICY_KEY_SECRET_FILE="$STATE/policy-key" \
  java -Dfile.encoding=UTF-8 -jar "$jar" --spring.profiles.active=cli recon run --tenant="$TENANT" --ym=202608,202702 > "$STATE/recon.log" 2>&1
echo $? > "$STATE/recon.exit"
set -e
echo "[e2e]    recon run 종료 코드 $(cat "$STATE/recon.exit")"

echo '[e2e] ⑨ 판정(뷰 9표·조회 API·시뮬레이션 한 쌍·알림·대사) → e2e/engine-feed/RESULT.md'
( cd "$ROOT" && ./gradlew -q --no-daemon :portal-app:e2eCheck --args="$STATE jdbc:postgresql://127.0.0.1:$PG_PORT/portal http://localhost:$PORTAL_PORT $HERE/RESULT.md $(git -C "$ENGINE_REPO" rev-parse HEAD) $(git -C "$ROOT" rev-parse HEAD)" )
