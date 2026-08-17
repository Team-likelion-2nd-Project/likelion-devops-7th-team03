variable "cluster_name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "database_subnet_group_name" {
  type = string
}

variable "database_subnets" {
  type = list(string)
}

variable "node_security_group_id" {
  description = "EKS 노드 SG — RDS/ElastiCache 인바운드 허용 대상"
  type        = string
}

variable "elasticache_node_type" {
  description = "ElastiCache 노드 타입"
  type        = string
}

variable "rds_instance_class" {
  type    = string
  default = "db.t3.micro"
}

variable "rds_engine_version" {
  type    = string
  default = "8.0"
}

variable "rds_allocated_storage" {
  type    = number
  default = 20
}

variable "rds_max_allocated_storage" {
  type    = number
  default = 50
}

variable "rds_multi_az" {
  type    = bool
  default = true
}

variable "rds_backup_retention_period" {
  type    = number
  default = 1
}

variable "rds_deletion_protection" {
  type    = bool
  default = false
}
