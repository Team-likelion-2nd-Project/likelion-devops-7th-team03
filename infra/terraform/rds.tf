# ── RDS용 보안그룹 ─────────────────────────────
resource "aws_security_group" "rds" {
  name        = "${var.cluster_name}-rds-sg"
  description = "Allow MySQL access from EKS nodes"
  vpc_id      = module.vpc.vpc_id

  ingress {
    description     = "MySQL from EKS nodes"
    from_port       = 3306
    to_port         = 3306
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
    Name = "${var.cluster_name}-rds-sg"
  }
}

# ── RDS 비밀번호 (Secrets Manager에 자동 저장) ───
resource "random_password" "rds_master" {
  length  = 24
  special = false # 일부 특수문자가 연결 문자열에서 문제되는 걸 피하기 위해 단순화
}

resource "aws_secretsmanager_secret" "rds_password" {
  name = "${var.cluster_name}/rds/app-password"
}

resource "aws_secretsmanager_secret_version" "rds_password" {
  secret_id     = aws_secretsmanager_secret.rds_password.id
  secret_string = random_password.rds_master.result
}

# ── RDS 인스턴스 ───────────────────────────────
# Multi-AZ로 이중화 (Primary + Standby, 동기 복제)
resource "aws_db_instance" "main" {
  identifier     = "${var.cluster_name}-db"
  engine         = "mysql"
  engine_version = "8.0"
  instance_class = "db.t3.micro"

  allocated_storage     = 20
  max_allocated_storage = 50 # 스토리지 오토스케일링 여유
  storage_type          = "gp3"

  db_name  = "snipy"
  username = "app_admin"
  password = random_password.rds_master.result
  port     = 3306

  db_subnet_group_name   = module.vpc.database_subnet_group_name
  vpc_security_group_ids = [aws_security_group.rds.id]

  multi_az = true # Primary(AZ-A) + Standby(AZ-B), 동기 복제, 자동 페일오버

  backup_retention_period = 1 # 최소한만 (짧은 프로젝트 기간)
  skip_final_snapshot     = true
  deletion_protection     = false # terraform destroy로 깔끔히 정리하기 위함

  publicly_accessible = false

  tags = {
    Name = "${var.cluster_name}-db"
  }
}
