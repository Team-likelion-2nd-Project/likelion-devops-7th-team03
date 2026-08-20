#!/usr/bin/env bash
# "Warmup 없는 바이럴 링크" 시나리오 원스톱 실행: 콜드 링크 DB 직접 INSERT →
# viral-spike.js 실행 → 테스트 데이터 DELETE. load-test/SCENARIOS.md 참고.
#
# management-service API를 쓰지 않는 이유:
#   - API로 만들면 링크 생성 트랜잭션 커밋 직후 바로 Redis에 캐시가 써져서
#     (RedirectCacheRefreshEventListener, AFTER_COMMIT) 처음부터 웜 상태가 됨
#   - slug도 서버가 SecureRandom으로 자동 생성해서 원하는 값을 못 정함
#   - API 호출 자체에 카카오 OAuth 로그인이 필요해서 스크립트로 돌리기 번거로움
# 그래서 MySQL에 직접 INSERT한다 (캐시 이벤트 안 탐, slug 마음대로, 로그인 불필요).
#
# 사용법:
#   load-test/viral-spike-run.sh <kubectl-context> <base-url> [num-slugs]
#
# 예시:
#   load-test/viral-spike-run.sh snipy https://dev.snipy.life 3
#
# 전제:
#   - kubectl context가 이미 MFA 인증된 상태여야 함 (aws eks get-token 호출)
#   - aws secretsmanager 읽기 권한
set -euo pipefail

CONTEXT="${1:?사용법: viral-spike-run.sh <kubectl-context> <base-url> [num-slugs]}"
BASE_URL="${2:?base-url 지정 (예: https://dev.snipy.life)}"
NUM_SLUGS="${3:-3}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# dev/prod 클러스터 이름 매핑 (Secrets Manager 키 prefix). 새 환경이 생기면 여기만 추가.
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
  --secret-id "${CLUSTER_NAME}/rds/app-user-password" \
  --query SecretString --output text)

# kubectl run으로 클러스터 안에서만 mysql 클라이언트 파드를 띄워 1회성 SQL 실행.
# -N(컬럼명 생략) -B(탭 구분 배치 출력)로 파싱하기 쉬운 순수 출력만 받는다.
run_sql() {
  local sql="$1"
  kubectl --context "$CONTEXT" run "mysql-client-$$" --rm -i --restart=Never \
    --image=mysql:8 --quiet \
    --env="MYSQL_PWD=${DB_PASSWORD}" \
    --command -- mysql -N -B -h "$DB_HOST" -P "$DB_PORT" -u shortlink_app "$DB_NAME" -e "$sql"
}

echo ">> [$CONTEXT] 기존 user id 조회"
USER_ID=$(run_sql "SELECT id FROM users ORDER BY id LIMIT 1;" | tr -d '\r')
if [[ -z "$USER_ID" ]]; then
  echo "users 테이블이 비어있음 — 카카오 로그인을 한 번이라도 한 계정이 있어야 함" >&2
  exit 1
fi
echo "   user_id=$USER_ID"

TS=$(date +%s)
SLUGS=()
for i in $(seq 1 "$NUM_SLUGS"); do
  SLUGS+=("viraltest-${TS}-${i}")
done
SLUG_CSV=$(IFS=,; echo "${SLUGS[*]}")

echo ">> [$CONTEXT] 콜드 링크 ${NUM_SLUGS}개 INSERT ($SLUG_CSV)"
INSERT_SQL="INSERT INTO links (link_id, user_id, slug, original_url, is_visible, expires_at) VALUES "
VALUES=()
for slug in "${SLUGS[@]}"; do
  VALUES+=("(UUID(), ${USER_ID}, '${slug}', 'https://example.com/viral-spike-test', TRUE, NULL)")
done
INSERT_SQL+=$(IFS=,; echo "${VALUES[*]}")
INSERT_SQL+=";"
run_sql "$INSERT_SQL"

cleanup() {
  echo ">> [$CONTEXT] 테스트 링크 정리 (slug IN $SLUG_CSV)"
  local in_clause
  in_clause=$(printf "'%s'," "${SLUGS[@]}")
  in_clause="${in_clause%,}"
  run_sql "DELETE FROM links WHERE slug IN (${in_clause});" || \
    echo "!! 자동 정리 실패 — 수동으로 지워야 함: DELETE FROM links WHERE slug IN (${in_clause});" >&2
}
trap cleanup EXIT

echo ">> [$CONTEXT] k6 viral-spike 실행"
"$SCRIPT_DIR/run.sh" "$CONTEXT" viral-spike.js "$BASE_URL" "$SLUG_CSV"
