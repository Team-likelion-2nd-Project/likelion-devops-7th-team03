#!/usr/bin/env bash
# Load/Stress 겸용 원스톱 실행: 링크 MySQL 직접 INSERT → normal-traffic.js 실행 →
# 테스트 데이터 DELETE. load-test/SCENARIOS.md 참고.
# management-service API 대신 직접 INSERT하는 이유는 viral-spike-run.sh 헤더 참고.
#
# 링크 풀 하나(기본 30만 개 — SCENARIOS.md의 동시 활성 링크 추정치와 동일)를 만들고
# normal-traffic.js가 그 안에서 hot/cold 구간을 나눈다(HOT_RATIO/HOT_TRAFFIC_RATIO).
# 풀을 bash로 나열하면 너무 커지므로 MySQL 재귀 CTE로 서버 사이드에서 한 번에
# 생성하고, 정리(DELETE)도 slug LIKE 패턴 하나로 끝낸다.
#
# 사용법:
#   load-test/normal-traffic-run.sh <kubectl-context> [base-url] [num-links] [peak-tps]
#
# 예시:
#   load-test/normal-traffic-run.sh snipy                              # Load: 기본 30만개, 230 TPS
#   load-test/normal-traffic-run.sh snipy http://redirect-service:8080 300000 23   # Load(낮은 TPS)
#   load-test/normal-traffic-run.sh snipy http://redirect-service:8080 300000 230  # Stress
set -euo pipefail

CONTEXT="${1:?사용법: normal-traffic-run.sh <kubectl-context> [base-url] [num-links] [peak-tps]}"
BASE_URL="${2:-http://redirect-service:8080}"
NUM_LINKS="${3:-300000}"
PEAK_TPS="${4:-230}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

case "$CONTEXT" in
  snipy)      CLUSTER_NAME="snipy-dev-cluster" ;;
  snipy-prod) CLUSTER_NAME="snipy-cluster" ;;
  *) echo "알 수 없는 context: $CONTEXT (CLUSTER_NAME 매핑을 스크립트에 추가하세요)" >&2; exit 1 ;;
esac

echo ">> [$CONTEXT] DB 접속 정보 조회"
DB_HOST=$(kubectl --context "$CONTEXT" get configmap redirect-service-config -o jsonpath='{.data.DB_HOST}')
DB_PORT=$(kubectl --context "$CONTEXT" get configmap redirect-service-config -o jsonpath='{.data.DB_PORT}')
DB_NAME=$(kubectl --context "$CONTEXT" get configmap redirect-service-config -o jsonpath='{.data.DB_NAME}')
DB_PASSWORD=$(aws secretsmanager get-secret-value \
  --region ap-southeast-1 \
  --secret-id "${CLUSTER_NAME}/rds/app-user-password" \
  --query SecretString --output text)

# viral-spike-run.sh와 동일한 이유로 --rm -i(attach) 대신 폴링 방식 사용.
run_sql() {
  local sql="$1"
  local pod_name="mysql-client-$$-${RANDOM}"

  kubectl --context "$CONTEXT" run "$pod_name" --restart=Never --image=mysql:8 --quiet \
    --env="MYSQL_PWD=${DB_PASSWORD}" \
    --command -- mysql -N -B -h "$DB_HOST" -P "$DB_PORT" -u shortlink_app "$DB_NAME" -e "$sql" \
    >/dev/null

  local phase=""
  for _ in $(seq 1 600); do # 30만 개 INSERT는 재귀 CTE라 시간이 걸릴 수 있어 넉넉히
    phase=$(kubectl --context "$CONTEXT" get pod "$pod_name" -o jsonpath='{.status.phase}' 2>/dev/null || echo "")
    [[ "$phase" == "Succeeded" || "$phase" == "Failed" ]] && break
    sleep 1
  done

  local output
  output=$(kubectl --context "$CONTEXT" logs "$pod_name" 2>&1)
  kubectl --context "$CONTEXT" delete pod "$pod_name" --ignore-not-found >/dev/null 2>&1

  if [[ "$phase" != "Succeeded" ]]; then
    echo "$output" >&2
    return 1
  fi
  echo "$output"
}

echo ">> [$CONTEXT] 기존 user id 조회"
USER_ID=$(run_sql "SELECT id FROM users ORDER BY id LIMIT 1;" | tr -d '\r' | head -n1)
if [[ -z "$USER_ID" ]]; then
  echo "users 테이블이 비어있음 — 카카오 로그인을 한 번이라도 한 계정이 있어야 함" >&2
  exit 1
fi
echo "   user_id=$USER_ID"

TS=$(date +%s)
SLUG_PREFIX="lt${TS: -6}"

# 재귀 CTE로 서버 사이드에서 N개를 한 번에 생성 (bash에서 수만 개 나열 안 함).
# cte_max_recursion_depth 기본값(1000)보다 큰 풀을 쓰므로 세션 변수로 올려줘야 함.
echo ">> [$CONTEXT] 링크 ${NUM_LINKS}개 (prefix=$SLUG_PREFIX) INSERT"
run_sql "
  SET SESSION cte_max_recursion_depth = $((NUM_LINKS + 10));
  INSERT INTO links (link_id, user_id, slug, original_url, is_visible, expires_at)
  WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < ${NUM_LINKS}
  )
  SELECT UUID(), ${USER_ID}, CONCAT('${SLUG_PREFIX}', n), 'https://example.com/normal-traffic-test', TRUE, NULL
  FROM seq;
"

cleanup() {
  echo ">> [$CONTEXT] 테스트 링크 정리 (prefix=$SLUG_PREFIX*)"
  run_sql "DELETE FROM links WHERE slug LIKE '${SLUG_PREFIX}%';" || \
    echo "!! 자동 정리 실패 — 수동으로 지워야 함: DELETE FROM links WHERE slug LIKE '${SLUG_PREFIX}%';" >&2
}
trap cleanup EXIT

echo ">> [$CONTEXT] k6 normal-traffic 실행 (링크 ${NUM_LINKS}개, PEAK_TPS=${PEAK_TPS}, 20분)"
export SLUG_PREFIX
export SLUG_COUNT="$NUM_LINKS"
export PEAK_TPS
"$SCRIPT_DIR/run-smoke.sh" "$CONTEXT" normal-traffic.js "$BASE_URL"
