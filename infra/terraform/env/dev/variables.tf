variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-southeast-1" # Singapore
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "prod"
}

variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
  default     = "snipy-cluster"
}

variable "cluster_version" {
  description = "Kubernetes version"
  type        = string
  default     = "1.31"
}

variable "vpc_cidr" {
  description = "VPC CIDR block"
  type        = string
  default     = "10.0.0.0/16"
}

variable "azs" {
  description = "Availability zones (2개, HA 구성)"
  type        = list(string)
  default     = ["ap-southeast-1a", "ap-southeast-1b"]
}

variable "public_subnets" {
  description = "퍼블릭 서브넷 CIDR — NAT Gateway, ALB"
  type        = list(string)
  default     = ["10.0.0.0/24", "10.0.1.0/24"]
}

variable "private_subnets" {
  description = "프라이빗 서브넷 CIDR — EKS 노드"
  type        = list(string)
  default     = ["10.0.10.0/24", "10.0.11.0/24"]
}

variable "database_subnets" {
  description = "DB 전용 서브넷 CIDR — RDS, ElastiCache"
  type        = list(string)
  default     = ["10.0.20.0/24", "10.0.21.0/24"]
}

variable "node_instance_type" {
  description = "EKS managed node group 인스턴스 타입"
  type        = string
  default     = "t3.medium"
}

variable "node_min_size" {
  type    = number
  default = 3
}

variable "node_max_size" {
  type    = number
  default = 5
}

variable "node_desired_size" {
  type    = number
  default = 3
}

variable "elasticache_node_type" {
  description = "ElastiCache 노드 타입"
  type        = string
  default     = "cache.t4g.micro"
}

variable "domain_name" {
  description = "서비스 도메인 (Route53에 등록된 도메인)"
  type        = string
  default     = "snipy.life"
}

variable "waf_geo_match_enabled" {
  description = "Geo-Match Rule 활성화 여부 (서비스 대상국 외 접근 제한)"
  type        = bool
  default     = false
}

variable "waf_allowed_countries" {
  description = "Geo-Match Rule 허용 국가 코드 (waf_geo_match_enabled=true일 때만 사용)"
  type        = list(string)
  default     = ["KR"]
}

variable "origin_alb_domain_name" {
  description = "AWS Load Balancer Controller가 생성한 ALB의 DNS 이름 (Ingress 배포 후 확인하여 입력)"
  type        = string
  default     = "" # Ingress 배포 후 채워서 재적용
}
