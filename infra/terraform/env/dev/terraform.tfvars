environment  = "dev"
cluster_name = "snipy-dev-cluster"

# staging 역할 겸용 — prod와 동일한 HA/사이징 (prod 장애/스케일 특성을 그대로 재현하기 위함)
single_nat_gateway = false # AZ당 1개 (prod와 동일)

# m6g(Graviton)는 앱 이미지가 amd64 전용이라 exec format error가 남 — 이미지
# 멀티아키 빌드 붙이기 전까지는 m5.large(amd64) 유지. t3(burstable)는 부하테스트 중
# CPU 크레딧이 고갈되면서 노드 전체가 느려지는 문제가 있어 non-burstable로 변경.
node_instance_type = "m5.large"
node_min_size      = 3
node_max_size      = 5
node_desired_size  = 3

# RDS는 관리형 바이너리라 ARM 이슈 없음 — Graviton(m6g)로 비용 절감.
# t3.micro는 RAM 1GiB로 버퍼풀이 거의 안 잡혀서 부하테스트 병목의 주요 원인이었음.
rds_instance_class = "db.m6g.large"

# 부하테스트 기간 동안 Multi-AZ/레플리카는 비용만 나가고 필요 없어서 끔.
# 부하테스트 끝나면 다시 켜서 prod와 맞출 것.
rds_multi_az = false

elasticache_num_cache_clusters         = 1 # Primary만
elasticache_automatic_failover_enabled = false
elasticache_multi_az_enabled           = false

cluster_admin_arns = [
  "arn:aws:iam::834922934330:user/team03-user01",
  "arn:aws:iam::834922934330:user/team03-user02",
  "arn:aws:iam::834922934330:user/team03-user03",
  "arn:aws:iam::834922934330:user/team03-user04"
]

domain_name           = "dev.snipy.life"
waf_geo_match_enabled = true
waf_allowed_countries = ["KR"]

# 1단계 apply: 비워둔 채로 적용 (ALB가 아직 없어서 CloudFront/ALB 조회 리소스는 스킵됨)
# 2단계: k8s Ingress 배포 후 실제 ALB DNS 이름을 확인해 채우고 재적용 (prod와 동일한 절차)
origin_alb_domain_name = "alb.dev.snipy.life"
