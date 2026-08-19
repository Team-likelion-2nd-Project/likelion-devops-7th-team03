environment  = "dev"
cluster_name = "snipy-dev-cluster"

# 비용 절감 사이징 — 인스턴스 타입은 prod와 동일하게 두고 개수/HA만 축소
single_nat_gateway = true # NAT Gateway 1개를 두 AZ가 공유

node_instance_type = "t3.medium" # prod와 동일 타입, 개수만 줄임
node_min_size      = 2
node_max_size      = 2
node_desired_size  = 2

rds_multi_az = false # Single-AZ

elasticache_num_cache_clusters         = 1 # Primary만, Replica 없음
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
# 2단계: k8s Ingress 배포 후 실제 ALB 이름을 확인해 채우고 재적용 (prod와 동일한 절차)
# ALB 의 DNS 값 넣으면 502 에러 남
# 지금 설계상 alb_origin의 alias 타깃은 var.origin_alb_domain_name의 값을 참조하는 게 아님
# elbv2.k8s.aws/cluster/ingress.k8s.aws/stack 태그로 매 apply마다 실제 살아있는 ALB를 다시 조회해서 채우고 있음
# alb.dev.snipy.life 이름으로 조회해 갱신하고 있음
# 값을 넣으면 갱신되지 않아서 문제 발생
origin_alb_domain_name = "alb.dev.snipy.life"
