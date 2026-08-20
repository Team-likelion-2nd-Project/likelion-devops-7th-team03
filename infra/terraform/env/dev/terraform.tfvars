environment  = "dev"
cluster_name = "snipy-dev-cluster"

# staging 역할 겸용 — prod와 동일한 HA/사이징 (prod 장애/스케일 특성을 그대로 재현하기 위함)
single_nat_gateway = false # AZ당 1개 (prod와 동일)

node_instance_type = "t3.medium"
node_min_size      = 3
node_max_size      = 5
node_desired_size  = 3

rds_multi_az = true

elasticache_num_cache_clusters         = 2 # Primary+Replica
elasticache_automatic_failover_enabled = true
elasticache_multi_az_enabled           = true

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
