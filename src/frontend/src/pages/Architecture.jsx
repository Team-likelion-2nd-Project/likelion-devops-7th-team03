const LAYERS = [
  ['엣지 / CDN', ['Route 53', 'CloudFront', 'WAF Web ACL']],
  ['진입점', ['Internet Gateway', 'ALB (Multi-AZ, HTTPS:443)']],
  ['컴퓨트', ['EKS Managed Node Group', 'Worker Node ×2 (AZ당 1개)', 'management-service', 'redirect-service', 'HPA']],
  ['캐시', ['ElastiCache Redis Replication Group', 'Primary', 'Replica']],
  ['DB', ['RDS MySQL Multi-AZ', 'Primary', 'Standby (동기 복제)']],
  [
    '클릭 이벤트 파이프라인',
    ['Kinesis Data Streams', 'Kinesis Data Firehose', 'S3', 'EKS CronJob', 'Athena', 'Glue Data Catalog', 'RDS UPSERT'],
  ],
  ['관측성', ['CloudWatch Logs/Metrics', 'CloudTrail', 'VPC Flow Logs', 'ALB 액세스 로그']],
  ['보안 / 비밀 관리', ['Secrets Manager', 'ACM (us-east-1 / ap-southeast-1)', 'WAF Web ACL']],
]

const SYNC_STEPS = [
  ['1', '사용자 → Route 53', 'DNS 조회'],
  ['2', 'Route 53 → CloudFront', 'Alias 레코드'],
  ['3', 'CloudFront → IGW', '캐시 미스 시 오리진 요청, X-Origin-Verify 커스텀 헤더 부착'],
  ['4', 'IGW → ALB', 'HTTPS:443 유입'],
  ['5', 'ALB → EKS Worker Node', 'AZ-A/AZ-B, :8080, Target Type: IP — Pod 직접 라우팅'],
  [
    '6',
    'Worker Node → ElastiCache',
    'AZ-A: 조회 + 클릭 카운터 증가(Redis INCR)를 Primary에서 함께 처리 · AZ-B: 로컬 Replica 조회, 카운터 증가는 Primary로 원격 기록',
  ],
  ['7', '캐시 미스 → RDS Primary', '직접 조회'],
]

const REALTIME_STEPS = [
  ['8', 'Worker Node → Kinesis Data Streams', '클릭 이벤트 발행 (NAT 경유)'],
  ['9', 'Kinesis Data Streams → Firehose', '실시간 전달'],
  ['10', 'Firehose → S3', '적재 (버퍼링 기준 배치)'],
]

const BATCH_STEPS = [
  ['11', 'EKS CronJob → Athena', '쿼리 실행 요청 (NAT 경유)'],
  ['12', 'Athena → Glue Data Catalog / S3', '카탈로그 조회 / 데이터 스캔'],
  ['13', 'Athena → EKS', '쿼리 결과 반환'],
  ['14', 'EKS → RDS Primary', 'UPSERT (직접 연결)'],
]

const BACKUP_ITEMS = [
  ['RDS Primary ↔ Standby', '동기 복제 (Multi-AZ)'],
  ['ElastiCache Primary ↔ Replica', '비동기 복제 (Replication Group)'],
  ['RDS → S3', '자동 스냅샷'],
]

const NETWORK_NOTES = [
  'Private Subnet 아웃바운드는 NAT Gateway만 허용 (AWS 관리형 서비스 접근도 NAT 경유, PrivateLink 미사용)',
  '관리 접근: SSM Session Manager (Bastion 없음)',
  'DB 자격증명: Secrets Manager 관리 (자동 교체)',
  'ALB 오리진 보호: CloudFront 프리픽스 리스트(SG) + X-Origin-Verify 커스텀 헤더 검증 (ALB 리스너 규칙)',
]

const TLS_CARDS = [
  ['CloudFront용', 'us-east-1', 'CloudFront 리전 제약으로 고정'],
  ['ALB용', 'ap-southeast-1', '서비스 리전'],
]

const DR_ITEMS = [
  'RDS 자동 백업 스냅샷 → 2차 리전(ap-northeast-1) 복사',
  'S3 Cross-Region Replication (스냅샷/로그)',
  'Route 53 Health Check 기반 Failover 라우팅 (2차 리전 스탠바이 구성 후 적용)',
  'RTO/RPO 목표치는 추후 정의 예정',
]

export function Architecture() {
  return (
    <div className="page">
      <div className="arch-hero">
        <h1>인프라 아키텍처</h1>
        <p className="hero__sub">
          링크 단축 서비스가 실제로 어떤 AWS 구성 위에서 동작하는지 정리한 문서입니다 (v12).
        </p>
        <div className="arch-hero__meta">
          <span className="arch-chip">리전 · ap-southeast-1</span>
          <span className="arch-chip arch-chip--warn">2주 한시 데모 배포 (상시 운영 아님)</span>
        </div>
      </div>

      <nav className="arch-nav" aria-label="섹션 바로가기">
        <a href="#layers">전체 구성</a>
        <a href="#flow">요청 흐름</a>
        <a href="#network">네트워크 · 보안</a>
        <a href="#scaling">오토스케일링</a>
        <a href="#tls">TLS 인증서</a>
        <a href="#dr">DR 전략</a>
      </nav>

      <section id="layers" className="card arch-section">
        <h2>전체 구성 요약</h2>
        <div className="arch-layers">
          {LAYERS.map(([label, items]) => (
            <div className="arch-layers__row" key={label}>
              <div className="arch-layers__label">{label}</div>
              <div className="arch-layers__value">
                {items.map((item) => (
                  <span className="arch-pill" key={item}>
                    {item}
                  </span>
                ))}
              </div>
            </div>
          ))}
        </div>
      </section>

      <section id="flow" className="card arch-section">
        <h2>요청 흐름</h2>

        <div className="arch-flow-group">
          <div className="arch-flow-group__title">
            <span className="arch-flow-group__dot" style={{ background: 'var(--series-1)' }} />
            동기 흐름 (1–7) — 링크 클릭 시
          </div>
          <ol className="arch-steps">
            {SYNC_STEPS.map(([n, path, desc]) => (
              <li key={n}>
                <span className="arch-steps__num">{n}</span>
                <div>
                  <strong>{path}</strong>
                  <p>{desc}</p>
                </div>
              </li>
            ))}
          </ol>
        </div>

        <div className="arch-flow-group">
          <div className="arch-flow-group__title">
            <span className="arch-flow-group__dot" style={{ background: 'var(--series-2)' }} />
            비동기 — 실시간 수집 (8–10)
          </div>
          <ol className="arch-steps">
            {REALTIME_STEPS.map(([n, path, desc]) => (
              <li key={n}>
                <span className="arch-steps__num arch-steps__num--async">{n}</span>
                <div>
                  <strong>{path}</strong>
                  <p>{desc}</p>
                </div>
              </li>
            ))}
          </ol>
        </div>

        <div className="arch-flow-group">
          <div className="arch-flow-group__title">
            <span className="arch-flow-group__dot" style={{ background: 'var(--muted)' }} />
            비동기 — 배치 집계 (11–14, 별도 주기)
          </div>
          <ol className="arch-steps">
            {BATCH_STEPS.map(([n, path, desc]) => (
              <li key={n}>
                <span className="arch-steps__num" style={{ background: 'var(--muted)' }}>
                  {n}
                </span>
                <div>
                  <strong>{path}</strong>
                  <p>{desc}</p>
                </div>
              </li>
            ))}
          </ol>
        </div>

        <div className="arch-flow-group">
          <div className="arch-flow-group__title">
            <span className="arch-flow-group__dot" style={{ background: 'var(--good)' }} />
            백업 / 복제 (상시)
          </div>
          <ol className="arch-steps">
            {BACKUP_ITEMS.map(([path, desc]) => (
              <li key={path}>
                <span className="arch-steps__num" style={{ background: 'var(--good)' }}>
                  ⟳
                </span>
                <div>
                  <strong>{path}</strong>
                  <p>{desc}</p>
                </div>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section id="network" className="card arch-section">
        <h2>네트워크 &amp; 보안 그룹 체이닝</h2>
        <div className="arch-sg-chain">
          {[
            'ALB-SG   inbound 443   ← CloudFront 프리픽스 리스트(pl-cloudfront)만 허용',
            'ALB-SG   → app-sg:8080 → db-sg:3306(RDS 직접) / cache-sg:6379',
            'eks-sg   → db-sg:3306(RDS 직접)   # EKS CronJob 배치 UPSERT 경로',
          ].join('\n')}
        </div>
        <ul className="trace-list">
          {NETWORK_NOTES.map((note) => (
            <li key={note}>{note}</li>
          ))}
        </ul>
      </section>

      <section id="scaling" className="card arch-section">
        <h2>
          오토스케일링 <span className="badge badge--warn">확인 필요</span>
        </h2>
        <p className="hero__sub" style={{ margin: 0 }}>
          아직 정책이 정해지지 않았습니다 — HPA 임계값(CPU/메모리 기준), 최소/최대 노드 수,
          Cluster Autoscaler 적용 여부를 확정해서 채워야 합니다.
        </p>
      </section>

      <section id="tls" className="card arch-section">
        <h2>TLS 인증서</h2>
        <div className="arch-tls-grid">
          {TLS_CARDS.map(([label, region, note]) => (
            <div className="arch-tls-card" key={label}>
              <strong>{label}</strong>
              <p className="arch-tls-card__region">ACM · {region}</p>
              <p className="chart-card__footnote" style={{ margin: '6px 0 0' }}>
                {note}
              </p>
            </div>
          ))}
        </div>
      </section>

      <section id="dr" className="card arch-section arch-roadmap">
        <h2>
          DR 전략 <span className="badge badge--off">로드맵 — 현재 미적용</span>
        </h2>
        <ul>
          {DR_ITEMS.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      </section>
    </div>
  )
}
