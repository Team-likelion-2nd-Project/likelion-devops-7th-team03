module "network" {
  source = "../../modules/network"

  cluster_name       = var.cluster_name
  vpc_cidr           = var.vpc_cidr
  azs                = var.azs
  public_subnets     = var.public_subnets
  private_subnets    = var.private_subnets
  database_subnets   = var.database_subnets
  single_nat_gateway = var.single_nat_gateway
}

# ECR은 bootstrap이 소유 (dev와 공유, prod를 destroy해도 이미지가 사라지지 않도록)
data "aws_ecr_repository" "management_service" {
  name = "management-service"
}

data "aws_ecr_repository" "redirect_service" {
  name = "redirect-service"
}

module "cluster" {
  source = "../../modules/cluster"

  cluster_name       = var.cluster_name
  cluster_version    = var.cluster_version
  aws_region         = var.aws_region
  vpc_id             = module.network.vpc_id
  subnet_ids         = module.network.private_subnets
  node_instance_type = var.node_instance_type
  node_min_size      = var.node_min_size
  node_max_size      = var.node_max_size
  node_desired_size  = var.node_desired_size
  cluster_admin_arns = var.cluster_admin_arns
}

module "database" {
  source = "../../modules/database"

  cluster_name                           = var.cluster_name
  vpc_id                                 = module.network.vpc_id
  database_subnet_group_name             = module.network.database_subnet_group_name
  database_subnets                       = module.network.database_subnets
  node_security_group_id                 = module.cluster.node_security_group_id
  elasticache_node_type                  = var.elasticache_node_type
  rds_multi_az                           = var.rds_multi_az
  elasticache_num_cache_clusters         = var.elasticache_num_cache_clusters
  elasticache_automatic_failover_enabled = var.elasticache_automatic_failover_enabled
  elasticache_multi_az_enabled           = var.elasticache_multi_az_enabled
}

module "streaming" {
  source = "../../modules/streaming"

  cluster_name      = var.cluster_name
  oidc_provider_arn = module.cluster.oidc_provider_arn
  oidc_provider     = module.cluster.oidc_provider
}

module "edge" {
  source = "../../modules/edge"
  providers = {
    aws.us_east_1 = aws.us_east_1
  }

  route53_zone_name = "snipy.life"   # dev/prod 공통 — 실제 등록된 apex 도메인
  cluster_name           = var.cluster_name
  domain_name            = var.domain_name
  vpc_id                 = module.network.vpc_id
  node_security_group_id = module.cluster.node_security_group_id
  waf_geo_match_enabled  = var.waf_geo_match_enabled
  waf_allowed_countries  = var.waf_allowed_countries
  origin_alb_domain_name = var.origin_alb_domain_name
}
