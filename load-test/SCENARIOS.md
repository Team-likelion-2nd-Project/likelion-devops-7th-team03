# 부하테스트 시나리오

## 서비스 컨텍스트

snipy는 URL 단축 + 클릭 분석 서비스. 타겟 고객은 자체 인프라 없이 SNS로 홍보/분석해야 하는 마케터·인플루언서·온라인 셀러.

`GET /{slug}` (redirect-service)는 임의의 외부 방문자에게 노출되는 엔드포인트라 트래픽 변동이 크고 예측 불가 — 특정 링크가 바이럴되면 순간적으로 대규모 트래픽이 몰릴 수 있다. 반면 인증/링크 관리(management-service)는 내부 인증 사용자만 쓰기 때문에 트래픽이 안정적이다. **그래서 부하테스트는 redirect-service에 집중한다.**

## 트래픽 규모 추정

- 내부 사용자 1만 명, 평균 10개/월 링크 생성 → 신규 링크 10만 개/월
- 캠페인 평균 수명 3개월 → 동시 활성 링크 ≈ 30만 개 (Little's Law: 10만/월 × 3개월)
- 링크당 평균 클릭 200/월 → 전체 트래픽 30만 × 200 = 6,000만/월
- 6,000만/월 ÷ 30일 = 200만/일 → 200만 ÷ 86,400초 ≈ **평균 23 TPS**
- 피크 시간대(저녁/점심 등 집중, 국내 서비스라 시차 분산 없음)는 평균의 약 10배로 추정 → **피크 ~230 TPS** (실측 데이터 없는 가정치 — 서비스 운영 후 재검증 필요)
- 바이럴 링크: 250만 명이 1시간 내 특정 링크로 몰리는 경우 → 250만 ÷ 3,600초 ≈ 694 TPS, 세이프티 마진을 얹어 **바이럴 목표 800 TPS**

### 성능 요구사항

| 항목 | 목표 |
|---|---|
| 평상시 Throughput | ~230 TPS |
| 바이럴 Throughput | ~800 TPS |
| Latency | 미정 — percentile 기준 필요 (p95/p99) |
| 에러율 | 미정 — 5xx 허용 임계치 필요 |

> Latency/에러율 임계치는 아직 팀 논의로 확정 안 됨. 이번 1차 테스트는 관찰 위주로 진행하고, 결과를 보고 임계치를 역산하는 쪽으로 간다.

## 이번 라운드: 실행 시나리오 — "Warmup 없는 바이럴 링크"

시간 제약상 여러 시나리오(load/stress/spike/breakpoint/soak) 중 **하나를 확실하게** 보여주는 데 집중한다. 다른 시나리오는 후속 라운드로 미룸.

### 가설

캐시에 없는 링크가 갑자기 바이럴되면(사전 warmup 없이), 동시에 몰리는 캐시 미스가 MySQL/HikariCP 커넥션 풀에 짧고 날카로운 병목을 만든다.

### 근거 (코드 확인 완료)

`RedirectService.findDatabaseRedirectTarget` (redirect-service)의 캐시 미스 처리:
1. `linkRepository.findBySlug`로 MySQL 조회
2. 결과를 `redirectCache.put`으로 Redis에 write-back

이 경로에 **동시성 보호가 전혀 없음** — 락, `synchronized`, 분산 락(Redisson 등), 요청 병합(request coalescing) 중 아무것도 구현돼 있지 않다. 동시에 도착한 요청은 각자 독립적으로 MySQL을 조회하고 각자 캐시를 덮어쓴다. TTL은 10분 고정이라, 테스트를 5분 안으로 잡으면 이 herd는 **테스트 시작 시점에 딱 한 번**만 발생한다 (재발 없음 — TTL 만료로 인한 동시 재캐싱까지 보고 싶으면 별도 라운드에서 테스트 duration을 TTL 이상으로 늘려야 함).

herd 크기는 "첫 DB 조회+캐시 write-back 왕복시간" 동안 도착하는 요청 수로 결정된다. 진짜 병목은 MySQL 자체보다 **HikariCP 커넥션 풀**일 가능성이 높다 (풀 사이즈를 넘는 요청은 대기 → 지연 급증). Grafana 대시보드(`grafana-dashboards.yaml`)에 이미 HikariCP 패널이 있어서 이 순간이 바로 잡힌다.

### 테스트 파라미터

- 스크립트: `load-test/viral-spike.js`
- Executor: `ramping-arrival-rate` (VU 기반이 아닌 목표 TPS 기반 — latency가 늘어도 요청 발생률이 줄지 않도록)
- 프로파일: 0 → 800 TPS를 10초 만에 램프업(바이럴의 "갑자기 몰림" 표현) → 4분 30초 유지 → 20초 램프다운. 총 5분
- 대상: 사전 캐싱되지 않은 콜드 링크. herd를 더 크고 뚜렷하게 보고 싶으면 콤마로 여러 개(3~5개) 동시 지정 가능
- 실행(원스톱): `load-test/viral-spike-run.sh <context> <base-url> [num-slugs]`

### 콜드 링크는 어떻게 만드나

management-service API로는 못 만든다 — API로 생성하면 트랜잭션 커밋 직후 바로 Redis에 캐시가 써져서(`RedirectCacheRefreshEventListener`, `AFTER_COMMIT`) 처음부터 웜 상태가 되고, slug도 서버가 SecureRandom으로 자동 생성해서 원하는 값을 못 지정하며, API 호출 자체에 카카오 OAuth 로그인이 필요해 자동화하기 번거롭다.

그래서 `load-test/viral-spike-run.sh`가 MySQL에 **직접 INSERT**해서 콜드 링크를 만든다 (캐시 이벤트를 타지 않아 자연히 콜드 상태, slug도 원하는 대로, 로그인 불필요). RDS는 노드 보안그룹에서만 열려있어 로컬에서 직접 접속은 안 되고, 클러스터 안에 `kubectl run`으로 임시 mysql 클라이언트 파드를 띄워 실행한다. 테스트 후 INSERT한 링크는 자동으로 DELETE된다 (`trap cleanup EXIT`).

### 사전 준비 (실행 전 체크리스트)

1. `load-test/README.md`의 사전 준비(observability 스택 배포, Grafana 접근) 완료
2. kubectl context가 MFA 인증된 상태
3. `aws secretsmanager get-secret-value` 권한 (DB 비밀번호 조회용)

### 관찰 포인트 (Grafana)

- `redirect_cache_result_total`: t=0 근처 miss 급증 → 이후 hit로 수렴하는지
- HikariCP Connection Pool 패널: t=0 순간 active/pending 커넥션이 튀는지 (예상되는 실제 병목 지점)
- `redirect_outcome_total`: t=0 구간 지연/에러 발생 여부
- k6 대시보드(grafana.com id 19665): p95/p99 latency가 t=0 구간만 튀고 정상화되는지, `http_req_failed` 비율

### 판정 기준 (초안 — 확정 필요)

- herd 구간(첫 1~2초) 동안 5xx 비율 < ?% — **미정, 1차 실행 결과 보고 정함**
- herd 이후(예: t=5초~) 안정화 구간의 p95 latency < 150ms 유지 여부
- 테스트 전체에서 502/504(타임아웃/과부하로 인한 게이트웨이 오류) 발생 여부

## 후속 계획 (이번 라운드에는 포함 안 함)

동시성 보호 적용 전/후 비교로 개선 효과를 데이터로 증명하는 것까지가 목표. 후보:

1. **베이스라인 (이번 라운드)**: 보호 없음 — 위 시나리오 그대로
2. **in-process 락**: 슬러그별 JVM 로컬 락(`synchronized` 또는 Caffeine single-flight). 구현 빠름(~1시간). 한계: 파드 개수만큼은 여전히 중복 조회됨 (파드 경계를 못 넘음)
3. **Redis 분산 락**: `SET lock:{slug} NX PX 3000` + 락 획득 실패 시 짧은 폴링(캐시가 채워지길 기다림) → 몇 번 폴링해도 안 채워지면 안전장치로 직접 조회. 기존 `RedisRedirectCache`의 Lua 스크립트 패턴 재사용 가능, 새 라이브러리 불필요. 클러스터 전체에서 DB 조회를 사실상 1번으로 수렴시킴

2/3단계 진행 여부와 순서는 1차 테스트 결과를 보고 결정.

## 다른 시나리오 (초안 — 이번 라운드에 실행 안 함)

- **Load test**: 평균 23 TPS, 10분, 인기 링크 80/20 분포(상위 20%가 트래픽 80% 차지, 조건문 게이팅), 캐시 히트 80% 예상
- **Stress test**: 피크 230 TPS, 10분, load test와 동일 분포 — 지속 부하에서 DB 읽기(캐시 미스 20% 분)가 누적되는 패턴 확인용
- **Breakpoint test**: TPS를 한계까지 점진적 증가. 종료 조건(에러율/latency 임계치 또는 안전 상한 TPS) 필요 — prod RDS가 `db.t3.micro`라 여기서 DB가 먼저 무너질 것으로 예상(가설)
- **Soak test**: 1시간 — 진짜 leak-hunting용 soak(보통 수 시간 이상)이라기보다는 짧은 안정성 체크로 취급

## 파드 HPA / 노드 오토스케일링 튜닝 (별도 트랙)

1. HPA 끈 상태로 단일 파드 최대 TPS 측정
2. 목표 성능 기준 필요 파드 수 계산
3. 필요 노드 수 산출
4. HPA + Cluster Autoscaler 켜고 전체 테스트, 병목 위치 확인

**전제 조건**: 현재 redirect-service/management-service는 `replicas: 2` 고정이고 HPA 리소스 자체가 없음 (metrics-server만 설치됨). 이 트랙을 시작하려면 HPA를 먼저 추가해야 함.

**중요 제약**: Cluster Autoscaler의 노드 추가는 보통 1~3분 걸리는데, 바이럴 스파이크 테스트는 5분짜리라 초반 스파이크는 오토스케일링이 반응하기 전에 지나갈 수 있음. 스파이크 대응력은 사실상 사전 확보된 여유 capacity(HPA `minReplicas`)에 좌우된다는 점을 감안해서, "cold(기본 상태에서 시작)" vs "warm(미리 스케일업된 상태)" 스파이크를 나눠 테스트하면 오토스케일링의 실질적 기여도를 수치로 보여줄 수 있음.
