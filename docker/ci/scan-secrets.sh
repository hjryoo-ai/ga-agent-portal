#!/usr/bin/env bash
# =============================================================================================
# compose 비밀 스캔(포털 Phase 8 지시문 §0·C6, 계획 ⑤ — CI 잡 compose-e2e, 스택이 떠 있는 채로 내리기 전에).
#  ① 볼륨 `secrets`의 파일: 전부 0600, 소비자 uid 소유(목록만 출력 — 값 없음).
#  ② 생성된 값 전부(+ policyKey 비밀의 base64·16진)가 다음 어디에도 없다: `docker compose config` 출력, 전 컨테이너 로그, 컨테이너 inspect(환경 변수),
#     빌드한 이미지 둘의 저장 스트림, compose E2E 산출물(web/build/compose-e2e). 값은 러너 임시 디렉터리에만 두고 지운다 — 출력하지 않는다.
#  ③ 관리 포트 9090은 호스트에 없다(포트 매핑 0, 접속 실패).
# =============================================================================================
set -euo pipefail
cd "$(dirname "$0")/../.."
C=(docker compose -f docker/compose.yaml --profile demo)
BUSYBOX=busybox@sha256:fd7dc98638c8e305f4dc34e979f1c0fdfdcaeb0fbf8fcff77ae834b6da3d7e6e
VOLUME=ga-portal-demo_secrets
work=$(mktemp -d "${RUNNER_TEMP:-/tmp}/compose-secrets.XXXXXX")
trap 'rm -rf "$work"' EXIT
failed=0

echo '① secret files (mode uid path):'
docker run --rm -v "$VOLUME:/s:ro" "$BUSYBOX" sh -c 'find /s -type f ! -name .generated -exec stat -c "%a %u %n" {} \;' | sort -k3 | tee "$work/modes"
files=$(wc -l < "$work/modes")
bad=$(awk '$1 != "600"' "$work/modes" | wc -l)
echo "files: $files, not 0600: $bad"
[ "$files" -eq 14 ] || { echo 'FAIL: not the 14 files generate.sh writes'; failed=1; }
[ "$bad" -eq 0 ] || { echo 'FAIL: a secret file is not 0600'; failed=1; }

docker run --rm -v "$VOLUME:/s:ro" "$BUSYBOX" sh -c '
  for f in $(find /s -type f ! -name .generated ! -name policy-key ! -name pgpass); do cat "$f"; echo; done
  base64 /s/portal/policy-key | tr -d "\n"; echo
  od -An -tx1 /s/portal/policy-key | tr -d " \n"; echo' | sort -u | grep -v '^$' > "$work/values"
values=$(wc -l < "$work/values")
echo "② distinct secret values: $values (not printed)"
[ "$values" -ge 9 ] || { echo 'FAIL: too few distinct secret values'; failed=1; }

"${C[@]}" config > "$work/config.yaml"
"${C[@]}" logs --no-color --timestamps > "$work/logs.txt" 2>&1
docker inspect $("${C[@]}" ps -aq) > "$work/inspect.json"
scan() { # scan <이름> <파일|디렉터리>
  local hits
  hits=$( { grep -r -a -l -F -f "$work/values" -- "$2" 2>/dev/null || true; } | wc -l)
  echo "   $1: files containing a secret value: $hits"
  [ "$hits" -eq 0 ] || failed=1
}
scan 'compose config' "$work/config.yaml"
scan 'container logs' "$work/logs.txt"
scan 'container inspect (env)' "$work/inspect.json"
for image in ga-portal-demo/portal:local ga-portal-demo/demo:local; do
  docker save "$image" > "$work/image.tar"
  scan "image $image" "$work/image.tar"
  rm -f "$work/image.tar"
done
scan 'compose E2E artifacts' web/build/compose-e2e

echo '③ management port 9090:'
# `docker compose port`는 공개되지 않은 포트에도 0으로 끝난다(":0") — 컨테이너의 실제 공개 목록을 본다: 8080 하나뿐이어야 한다.
published=$(docker inspect -f '{{range $p, $b := .NetworkSettings.Ports}}{{if $b}}{{$p}} {{end}}{{end}}' "$("${C[@]}" ps -q portal)")
echo "   portal publishes: ${published:-nothing}"
[ "$published" = '8080/tcp ' ] || { echo 'FAIL: the portal publishes something other than 8080'; failed=1; }
if curl -s --max-time 3 -o /dev/null http://127.0.0.1:9090/; then echo 'FAIL: 127.0.0.1:9090 answers'; failed=1; else echo '   127.0.0.1:9090 refuses'; fi

echo 'images (service, image, id):'
"${C[@]}" images --format '{{.ContainerName}} {{.Repository}}:{{.Tag}} {{.ID}}' 2>/dev/null || "${C[@]}" images

exit "$failed"
