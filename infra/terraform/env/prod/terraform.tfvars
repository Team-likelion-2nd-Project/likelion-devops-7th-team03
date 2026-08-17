environment  = "prod"
cluster_name = "snipy-cluster"

# 가용성 우선 사이징 (dev는 비용 절감 위해 env/dev/terraform.tfvars에서 축소)
single_nat_gateway                     = false
node_instance_type                     = "t3.medium"
node_min_size                          = 3
node_max_size                          = 5
node_desired_size                      = 3
rds_multi_az                           = true
elasticache_num_cache_clusters         = 2
elasticache_automatic_failover_enabled = true
elasticache_multi_az_enabled           = true

domain_name           = "snipy.life"
waf_geo_match_enabled = true
waf_allowed_countries = ["KR"]

# 1단계 apply: 아래를 비워둔 채로 적용 (ALB가 아직 없어서 CloudFront/ALB 조회 리소스는 스킵됨)
# 2단계: k8s Ingress 배포 후 `kubectl get ingress snipy-ingress -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'`로
# 확인한 실제 ALB DNS 이름(예: k8s-xxxx.ap-southeast-1.elb.amazonaws.com)을 채우고 재적용.
# "alb.snipy.life"를 넣으면 안 됨 — 그건 이 apply가 만드는 결과물이라 아직 존재하지 않음.
origin_alb_domain_name = ""
