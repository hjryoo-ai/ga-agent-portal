#!/usr/bin/env bash
# =============================================================================================
# compose 데모가 다 뜰 때까지(포털 Phase 8 계획 Q13 — perf.yml): 포털 healthy → 등록(seed) 종료 0 → 첫 동기화 끝(설계사 a-2001의 /me asOf.seq = 스트림 길이).
# 걸린 시간을 한 줄씩 출력한다. 비밀번호는 README의 허구 값(셸 변수로만 — 출력 0), 토큰도 셸 변수로만.
# =============================================================================================
set -euo pipefail
cd "$(dirname "$0")/../.."
C=(docker compose -f docker/compose.yaml --profile demo)
start=${1:-$(date +%s)}
length=$(grep -c '' contracts/shared/ga-commission-engine/samples/demo/demo_ga.jsonl)
password=$(sed -n 's/.*데모 비밀번호: `\([^`]*\)`.*/\1/p' README.md | head -1)
[ -n "$password" ] || { echo 'README has no demo password line'; exit 1; }
# 서비스 하나의 "상태 건강 종료코드"(건강이 없으면 -)
status_of() { "${C[@]}" ps -a --format '{{.Service}}|{{.State}}|{{.Health}}|{{.ExitCode}}' | awk -F'|' -v s="$1" '$1 == s { print $2, ($3 == "" ? "-" : $3), $4 }'; }

until [ "$(status_of portal | cut -d' ' -f2)" = healthy ]; do
  [ $(( $(date +%s) - start )) -lt 900 ] || { echo 'timed out: portal'; exit 1; }
  sleep 2
done
echo "portal healthy: $(( $(date +%s) - start )) s"
until [ "$(status_of seed | cut -d' ' -f1,3)" = 'exited 0' ]; do
  if [ "$(status_of seed | cut -d' ' -f1)" = exited ]; then echo 'seed failed'; exit 1; fi
  [ $(( $(date +%s) - start )) -lt 1000 ] || { echo 'timed out: seed'; exit 1; }
  sleep 2
done
echo "seeded: $(( $(date +%s) - start )) s"
seq=0
while [ "$seq" -lt "$length" ]; do
  [ $(( $(date +%s) - start )) -lt 1500 ] || { echo "timed out: sync at seq $seq of $length"; exit 1; }
  token=$(curl -s -X POST http://localhost:8180/realms/demo/protocol/openid-connect/token \
            --data-urlencode grant_type=password --data-urlencode client_id=k6 --data-urlencode username=a-2001 \
            --data-urlencode "password=$password" --data-urlencode scope=openid | jq -r '.access_token // empty' || true)
  if [ -n "$token" ]; then
    seq=$(curl -s -H "Authorization: Bearer $token" http://localhost:8080/api/v1/me | jq -r '.asOf.seq // 0' || echo 0)
  fi
  [ "$seq" -ge "$length" ] || sleep 3
done
echo "first sync done (seq $seq = stream length): $(( $(date +%s) - start )) s"
