domain_name           = "snipy.life"
waf_geo_match_enabled = true
waf_allowed_countries = ["KR"]

# 1단계 apply 시엔 아래를 비워두고, Ingress 배포 후 ALB DNS 확인해서 채운 뒤 재적용
#origin_alb_domain_name = ""
