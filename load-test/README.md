# load-test

EKS 클러스터 안에서 k6로 부하테스트를 1회성으로 실행하기 위한 도구. GitOps(ArgoCD) 관리 대상이 아니고, 필요할 때 로컬에서 직접 실행한다.

## 사전 준비

1. `infra/argocd/{dev,prod}/observability.yaml`이 배포되어 `monitoring` 네임스페이스에 kube-prometheus-stack(Prometheus/Grafana)이 떠 있어야 함.
2. Grafana admin 비밀번호는 수동으로 만들 필요 없음 — `modules/app-secrets`가 Secrets Manager에 랜덤 비밀번호를 생성해두고, observability Application의 `extraManifests`에 포함된 `ExternalSecret`이 배포 시 자동으로 `grafana-admin-credentials` 시크릿을 만든다. 비밀번호 값을 보려면:
   ```bash
   aws secretsmanager get-secret-value \
     --secret-id snipy-dev-cluster/app/grafana-admin-password \
     --query SecretString --output text
   # prod는 snipy-cluster/app/grafana-admin-password
   ```
3. `envsubst` 필요 (macOS: `brew install gettext`).

## 실행

```bash
load-test/run.sh <kubectl-context> <script-file> <base-url> <slug> [vus] [duration]

# 간단한 smoke test (vus/duration 기반) — slug는 management-service API로 미리 만들어둔 값
load-test/run.sh snipy redirect-smoke.js https://dev.snipy.life test 10 30s

# 바이럴 스파이크 시나리오 — 콜드 링크 생성부터 실행/정리까지 원스톱
load-test/viral-spike-run.sh snipy https://dev.snipy.life 3
```

`redirect-smoke.js`용 `slug`는 실제로 links 테이블에 등록된 값이어야 302를 받는다. `viral-spike.js`는 **콜드(캐시에 없는) 링크**가 필요한데, management-service API로는 만들 수 없다 — API로 만들면 생성 즉시 Redis에 캐시가 채워지고, slug도 서버가 자동 생성해서 지정이 안 되기 때문. 그래서 `viral-spike-run.sh`가 MySQL에 직접 INSERT해서 콜드 링크를 만들고, 테스트 후 자동으로 정리한다 (자세한 내용은 `SCENARIOS.md` 참고).

## 결과 확인

k6가 `--out experimental-prometheus-rw`로 결과를 클러스터 내 Prometheus에 바로 remote-write한다. Grafana에서 보려면 공식 k6 대시보드(grafana.com dashboard id **19665**)를 Import 메뉴에서 한 번 가져오면 된다.

```bash
kubectl --context <context> port-forward -n monitoring svc/kube-prometheus-grafana 3000:80
# http://localhost:3000, admin / (사전 준비 2번에서 조회한 비밀번호)
```

## 정리

- Job/ConfigMap: 다음 실행 시 `run.sh`가 자동으로 지우고 새로 만듦. 수동 정리는:
  ```bash
  kubectl --context <context> delete job k6-load-test configmap k6-load-test-script
  ```
- `viral-spike-run.sh`로 만든 콜드 링크는 테스트 종료 시 자동 DELETE됨 (`trap cleanup EXIT` — 중간에 실패해도 실행됨). 클릭 로그(`link_daily_stats`, S3/Athena)는 자동 정리되지 않음.
