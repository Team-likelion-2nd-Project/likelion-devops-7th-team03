environment  = "dev"
cluster_name = "snipy-dev-cluster"

# 사이징(인스턴스 타입, 노드 수 등)은 이번 스텝에서는 prod와 동일하게 시작 — 나중 스텝에서 축소 예정

# TODO: dev/prod를 동시에 apply하면 이 apex 도메인의 Route53 alias가 prod와 충돌한다.
# 이번 스텝은 구조 확인용이라 값만 임시로 prod와 동일하게 둔다 — 실제 apply 전 dev 전용 서브도메인으로 교체 필요.
domain_name           = "snipy.life"
waf_geo_match_enabled = true
waf_allowed_countries = ["KR"]

# TODO: prod의 alb.snipy.life와 동일한 값을 임시로 사용 중 — dev 전용 값으로 교체 필요
origin_alb_domain_name = "alb.snipy.life"
