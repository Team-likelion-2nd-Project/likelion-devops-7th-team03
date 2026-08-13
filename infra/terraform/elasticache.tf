# ── ElastiCache용 보안그룹 ────────────────────
resource "aws_security_group" "elasticache" {
  name        = "${var.cluster_name}-elasticache-sg"
  description = "Allow Redis access from EKS nodes"
  vpc_id      = module.vpc.vpc_id

  ingress {
    description     = "Redis from EKS nodes"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [module.eks.node_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.cluster_name}-elasticache-sg"
  }
}

# ── ElastiCache Replication Group ─────────────
# Cluster Mode: Disabled (단일 샤드), Primary 1 + Replica 1, Multi-AZ 자동 페일오버
resource "aws_elasticache_replication_group" "redis" {
  replication_group_id = "${var.cluster_name}-redis"
  description          = "Snipy Redis - click counters, visitor HLL"

  engine         = "redis"
  engine_version = "7.1"
  node_type      = var.elasticache_node_type
  port           = 6379

  num_cache_clusters = 2 # Primary 1 + Replica 1

  automatic_failover_enabled = true
  multi_az_enabled           = true

  subnet_group_name  = aws_elasticache_subnet_group.redis.name
  security_group_ids = [aws_security_group.elasticache.id]

  # 백업 불필요 — Athena 집계 결과가 별도로 RDS에 영구 저장됨
  snapshot_retention_limit = 0

  tags = {
    Name = "${var.cluster_name}-redis"
  }
}

resource "aws_elasticache_subnet_group" "redis" {
  name       = "${var.cluster_name}-redis-subnet-group"
  subnet_ids = module.vpc.database_subnets

  tags = {
    Name = "${var.cluster_name}-redis-subnet-group"
  }
}
