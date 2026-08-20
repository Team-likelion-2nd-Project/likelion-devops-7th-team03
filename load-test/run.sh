#!/usr/bin/env bash
# k6 부하테스트를 EKS 클러스터 안에서 1회 실행한다 (CronJob 아님, 필요할 때 수동 실행).
#
# 사용법:
#   load-test/run.sh <kubectl-context> <script-file> <base-url> <slug> [vus] [duration]
#
# 예시:
#   load-test/run.sh snipy redirect-smoke.js https://dev.snipy.life test 10 30s
#   load-test/run.sh snipy viral-spike.js https://dev.snipy.life cold-test-1,cold-test-2
#
# SLUG(들)은 실제로 links 테이블에 등록된 slug여야 302를 받는다 (없으면 404 대상 테스트가 됨).
# viral-spike.js는 콤마로 여러 slug를 받고, 반드시 테스트 전 캐시에 없는 상태여야 herd가 재현된다.
# vus/duration은 redirect-smoke.js 전용 (viral-spike.js는 스크립트 안에 부하 프로파일이 고정돼 있어 무시됨).
set -euo pipefail

CONTEXT="${1:?사용법: run.sh <kubectl-context> <script-file> <base-url> <slug> [vus] [duration]}"
SCRIPT_FILE="${2:?스크립트 파일명 지정 (예: redirect-smoke.js, viral-spike.js)}"
BASE_URL="${3:?base-url 지정 (예: https://dev.snipy.life)}"
SLUG="${4:?테스트용 slug 지정 (콤마로 여러 개 가능)}"
VUS="${5:-10}"
DURATION="${6:-30s}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ ! -f "$SCRIPT_DIR/$SCRIPT_FILE" ]]; then
  echo "스크립트 파일을 찾을 수 없음: $SCRIPT_DIR/$SCRIPT_FILE" >&2
  exit 1
fi

echo ">> [$CONTEXT] 이전 Job/ConfigMap 정리"
kubectl --context "$CONTEXT" delete job k6-load-test --ignore-not-found
kubectl --context "$CONTEXT" delete configmap k6-load-test-script --ignore-not-found

echo ">> [$CONTEXT] 테스트 스크립트 ConfigMap 생성 ($SCRIPT_FILE)"
kubectl --context "$CONTEXT" create configmap k6-load-test-script \
  --from-file="$SCRIPT_FILE=$SCRIPT_DIR/$SCRIPT_FILE"

echo ">> [$CONTEXT] Job 생성 (SCRIPT=$SCRIPT_FILE BASE_URL=$BASE_URL SLUG=$SLUG VUS=$VUS DURATION=$DURATION)"
SCRIPT_FILE="$SCRIPT_FILE" BASE_URL="$BASE_URL" SLUG="$SLUG" VUS="$VUS" DURATION="$DURATION" \
  envsubst < "$SCRIPT_DIR/job.yaml.template" | kubectl --context "$CONTEXT" apply -f -

echo ">> Job 완료 대기 (최대 8분)"
kubectl --context "$CONTEXT" wait --for=condition=complete --timeout=8m job/k6-load-test || true

echo ">> 결과 로그"
kubectl --context "$CONTEXT" logs job/k6-load-test

cat <<EOF

정리하려면:
  kubectl --context $CONTEXT delete job k6-load-test configmap k6-load-test-script

이번 테스트로 생성된 더미 데이터(slug=$SLUG)는 별도로 DB/S3에서 정리해야 함.
EOF
