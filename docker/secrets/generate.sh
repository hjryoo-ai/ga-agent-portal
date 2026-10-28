#!/bin/sh
# =============================================================================================
# 비밀 생성(포털 Phase 8 지시문 §0, 계획 P-15) — compose 서비스 `secrets`(busybox, root)가 처음 뜰 때 한 번. 볼륨 `secrets`에 파일로만 쓴다.
# 저장소·.env·이미지·환경 변수·로그·`docker compose config` 출력에 값 0 — 이 스크립트는 값을 출력하지 않는다(파일 이름·수만).
# 파일은 0600, 읽을 컨테이너의 uid가 소유한다: postgres 999 · portal·demo 10001 · keycloak 1000. 디렉터리는 소비자 소유 0700.
#   postgres/{superuser,migrator,app,directory,issuer}  — 롤 비밀번호(init-roles.sql에 psql 변수로), pgpass(시드 — portal_migrator)
#   portal/config/PORTAL_*_PASSWORD                      — Spring configtree(application.yaml 자리표시자와 같은 이름)
#   portal/engine-tokens/DEMO · demo/token              — 엔진 서비스 토큰(포털 인제스트·시뮬레이션·대사 ↔ 데모 엔진 대역, 같은 값)
#   portal/policy-key                                   — policyKey HMAC 비밀(32바이트 원시)
#   keycloak/admin                                      — Keycloak 관리자 비밀번호(데모 렐름 밖 — 콘솔용)
# 다시 띄우면 그대로 둔다(같은 비밀 — DB 볼륨과 짝). 새로 만들려면 `docker compose down -v`(볼륨 전부).
# =============================================================================================
set -eu
D=/run/secrets/all
if [ -f "$D/.generated" ]; then
  echo "secrets: already generated ($(find "$D" -type f ! -name .generated | wc -l) files)"
  exit 0
fi
umask 077

rand() {
  # 40자 영숫자(약 238비트) — 셸 변수로만 옮긴다
  head -c 64 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 40
}

put() { # put <소유 uid> <파일> <값>
  printf '%s' "$3" > "$2"
  chown "$1:$1" "$2"
  chmod 0600 "$2"
}

mkdir -p "$D/postgres" "$D/portal/config" "$D/portal/engine-tokens" "$D/demo" "$D/keycloak"
chmod 0755 "$D"

superuser=$(rand); migrator=$(rand); app=$(rand); directory=$(rand); issuer=$(rand); token=$(rand); admin=$(rand)

put 999 "$D/postgres/superuser" "$superuser"
put 999 "$D/postgres/migrator" "$migrator"
put 999 "$D/postgres/app" "$app"
put 999 "$D/postgres/directory" "$directory"
put 999 "$D/postgres/issuer" "$issuer"
put 999 "$D/postgres/pgpass" "postgres:5432:portal:portal_migrator:$migrator"

put 10001 "$D/portal/config/PORTAL_MIGRATOR_PASSWORD" "$migrator"
put 10001 "$D/portal/config/PORTAL_APP_PASSWORD" "$app"
put 10001 "$D/portal/config/PORTAL_DIRECTORY_PASSWORD" "$directory"
put 10001 "$D/portal/config/PORTAL_ISSUER_PASSWORD" "$issuer"
put 10001 "$D/portal/engine-tokens/DEMO" "$token"
put 10001 "$D/demo/token" "$token"
head -c 32 /dev/urandom > "$D/portal/policy-key"
chown 10001:10001 "$D/portal/policy-key"
chmod 0600 "$D/portal/policy-key"

put 1000 "$D/keycloak/admin" "$admin"

chown 999:999 "$D/postgres"
chown -R 10001:10001 "$D/portal" "$D/demo"
chown 1000:1000 "$D/keycloak"
chmod 0700 "$D/postgres" "$D/portal" "$D/portal/config" "$D/portal/engine-tokens" "$D/demo" "$D/keycloak"

touch "$D/.generated"
echo "secrets: generated $(find "$D" -type f ! -name .generated | wc -l) files"
