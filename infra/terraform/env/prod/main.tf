module "network" {
  source = "../../modules/network"

  cluster_name     = var.cluster_name
  vpc_cidr         = var.vpc_cidr
  azs              = var.azs
  public_subnets   = var.public_subnets
  private_subnets  = var.private_subnets
  database_subnets = var.database_subnets
}

module "registry" {
  source = "../../modules/registry"
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
}

module "database" {
  source = "../../modules/database"

  cluster_name               = var.cluster_name
  vpc_id                     = module.network.vpc_id
  database_subnet_group_name = module.network.database_subnet_group_name
  database_subnets           = module.network.database_subnets
  node_security_group_id     = module.cluster.node_security_group_id
  elasticache_node_type      = var.elasticache_node_type
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

  cluster_name           = var.cluster_name
  domain_name            = var.domain_name
  vpc_id                 = module.network.vpc_id
  node_security_group_id = module.cluster.node_security_group_id
  waf_geo_match_enabled  = var.waf_geo_match_enabled
  waf_allowed_countries  = var.waf_allowed_countries
  origin_alb_domain_name = var.origin_alb_domain_name
}
