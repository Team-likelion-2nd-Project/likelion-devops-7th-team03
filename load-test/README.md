# load-test

EKS 클러스터 안에서 k6로 부하테스트를 1회성으로 실행하기 위한 도구. GitOps(ArgoCD) 관리 대상이 아니고, 필요할 때 로컬에서 직접 실행한다.

## 사전 준비

1. `infra/argocd/{dev,prod}/observability.yaml`이 배포되어 `monitoring` 네임스페이스에 kube-prometheus-stack(Prometheus/Grafana)이 떠 있어야 함.
2. Grafana admin 비밀번호는 git에 안 올라가므로, 클러스터별로 미리 시크릿 생성 필요:
   ```bash
   kubectl --context <context> create secret generic grafana-admin-credentials \
     -n monitoring \
     --from-literal=admin-user=admin \
     --from-literal=admin-password='<원하는 비밀번호>'
   ```
   (observability Application 최초 sync 전에 만들어둘 것 — 없으면 Grafana Pod가 뜨지 않음)
3. `envsubst` 필요 (macOS: `brew install gettext`).

## 실행

```bash
load-test/run.sh <kubectl-context> <base-url> <slug> [vus] [duration]

# 예: dev에서 낮은 강도로 먼저 확인
load-test/run.sh snipy https://dev.snipy.life test 10 30s

# prod 본 테스트
load-test/run.sh snipy-prod https://snipy.life test 50 2m
```

`slug`는 실제로 links 테이블에 등록된 값이어야 302를 받는다. 부하테스트 전용 링크를 management-service API로 미리 만들어두고, 나중에 정리하기 쉽게 식별 가능한 slug(예: `loadtest-xxx`)를 쓰는 걸 권장.

## 결과 확인

k6가 `--out experimental-prometheus-rw`로 결과를 클러스터 내 Prometheus에 바로 remote-write한다. Grafana에서 보려면 공식 k6 대시보드(grafana.com dashboard id **19665**)를 Import 메뉴에서 한 번 가져오면 된다.

```bash
kubectl --context <context> port-forward -n monitoring svc/kube-prometheus-grafana 3000:80
# http://localhost:3000, admin / (위에서 만든 admin-password)
```

## 정리

- Job/ConfigMap: 다음 실행 시 `run.sh`가 자동으로 지우고 새로 만듦. 수동 정리는:
  ```bash
  kubectl --context <context> delete job k6-load-test configmap k6-load-test-script
  ```
- 테스트로 생성된 더미 데이터(링크, 클릭 로그 → `link_daily_stats`, S3/Athena)는 자동 정리되지 않음. slug 프리픽스로 식별해서 수동으로 정리할 것.
