environment  = "dev"
cluster_name = "snipy-dev-cluster"

# 비용 절감 사이징 — 인스턴스 타입은 prod와 동일하게 두고 개수/HA만 축소
single_nat_gateway = true # NAT Gateway 1개를 두 AZ가 공유

node_instance_type = "t3.medium" # prod와 동일 타입, 개수만 줄임
node_min_size      = 1
node_max_size      = 2
node_desired_size  = 1

rds_multi_az = false # Single-AZ

elasticache_num_cache_clusters         = 1 # Primary만, Replica 없음
elasticache_automatic_failover_enabled = false
elasticache_multi_az_enabled           = false

# TODO: dev/prod를 동시에 apply하면 이 apex 도메인의 Route53 alias가 prod와 충돌한다.
# 이번 스텝은 구조 확인용이라 값만 임시로 prod와 동일하게 둔다 — 실제 apply 전 dev 전용 서브도메인으로 교체 필요.
domain_name           = "dev.snipy.life"
waf_geo_match_enabled = true
waf_allowed_countries = ["KR"]

# 1단계 apply: 비워둔 채로 적용 (ALB가 아직 없어서 CloudFront/ALB 조회 리소스는 스킵됨)
# 2단계: k8s Ingress 배포 후 실제 ALB DNS 이름을 확인해 채우고 재적용 (prod와 동일한 절차)
origin_alb_domain_name = ""
