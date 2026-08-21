#!/usr/bin/env bash
# k6 부하테스트를 EKS 클러스터 안에서 1회 실행한다 (CronJob 아님, 필요할 때 수동 실행).
#
# 사용법:
#   load-test/run.sh <kubectl-context> <base-url> <slug> [vus] [duration]
#
# 예시:
#   load-test/run.sh snipy https://dev.snipy.life test 10 30s
#
# SLUG은 실제로 links 테이블에 등록된 slug여야 302를 받는다 (없으면 404 대상 부하테스트가 됨).
set -euo pipefail

CONTEXT="${1:?사용법: run.sh <kubectl-context> <base-url> <slug> [vus] [duration]}"
BASE_URL="${2:?base-url 지정 (예: https://dev.snipy.life)}"
SLUG="${3:?테스트용 slug 지정}"
VUS="${4:-10}"
DURATION="${5:-30s}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo ">> [$CONTEXT] 이전 Job/ConfigMap 정리"
kubectl --context "$CONTEXT" delete job k6-load-test --ignore-not-found
kubectl --context "$CONTEXT" delete configmap k6-load-test-script --ignore-not-found

echo ">> [$CONTEXT] 테스트 스크립트 ConfigMap 생성"
kubectl --context "$CONTEXT" create configmap k6-load-test-script \
  --from-file=redirect-smoke.js="$SCRIPT_DIR/redirect-smoke.js"

echo ">> [$CONTEXT] Job 생성 (BASE_URL=$BASE_URL SLUG=$SLUG VUS=$VUS DURATION=$DURATION)"
BASE_URL="$BASE_URL" SLUG="$SLUG" VUS="$VUS" DURATION="$DURATION" \
  envsubst < "$SCRIPT_DIR/job.yaml.template" | kubectl --context "$CONTEXT" apply -f -

echo ">> Job 완료 대기 (최대 5분)"
kubectl --context "$CONTEXT" wait --for=condition=complete --timeout=5m job/k6-load-test || true

echo ">> 결과 로그"
kubectl --context "$CONTEXT" logs job/k6-load-test

cat <<EOF

정리하려면:
  kubectl --context $CONTEXT delete job k6-load-test configmap k6-load-test-script

이번 테스트로 생성된 더미 데이터(slug=$SLUG)는 별도로 DB/S3에서 정리해야 함.
EOF
