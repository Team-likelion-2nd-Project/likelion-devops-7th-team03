output "cluster_endpoint" {
  description = "EKS cluster API endpoint"
  value       = module.eks.cluster_endpoint
}

output "cluster_name" {
  value = module.eks.cluster_name
}

output "configure_kubectl" {
  description = "kubectl 연결 명령어"
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${module.eks.cluster_name}"
}

output "vpc_id" {
  value = module.vpc.vpc_id
}

output "private_subnets" {
  value = module.vpc.private_subnets
}

output "database_subnets" {
  value = module.vpc.database_subnets
}

output "database_subnet_group_name" {
  description = "RDS 생성 시 이 subnet group 이름을 사용"
  value       = module.vpc.database_subnet_group_name
}

output "elasticache_primary_endpoint" {
  value = aws_elasticache_replication_group.redis.primary_endpoint_address
}

output "elasticache_reader_endpoint" {
  value = aws_elasticache_replication_group.redis.reader_endpoint_address
}

output "rds_endpoint" {
  value = aws_db_instance.main.endpoint
}

output "rds_db_name" {
  value = aws_db_instance.main.db_name
}

output "rds_password_secret_arn" {
  description = "Secrets Manager에서 실제 비밀번호 조회"
  value       = aws_secretsmanager_secret.rds_password.arn
}

output "kinesis_stream_name" {
  value = aws_kinesis_stream.click_events.name
}

output "click_logs_bucket" {
  value = aws_s3_bucket.click_logs.bucket
}

output "athena_workgroup" {
  value = aws_athena_workgroup.snipy.name
}

output "glue_database_name" {
  value = aws_glue_catalog_database.snipy.name
}

output "redirect_service_irsa_role_arn" {
  description = "redirect-service의 ServiceAccount에 이 ARN을 annotation으로 연결"
  value       = aws_iam_role.redirect_service.arn
}

output "athena_cronjob_irsa_role_arn" {
  description = "Athena 배치 CronJob의 ServiceAccount에 이 ARN을 annotation으로 연결"
  value       = aws_iam_role.athena_cronjob.arn
}

output "route53_zone_id" {
  value = data.aws_route53_zone.main.zone_id
}

output "alb_certificate_note" {
  description = "ap-southeast-1(ALB용) ACM 인증서는 별도로 이미 발급받으셨다고 하셨으니 그 ARN을 Ingress 매니페스트에 채워주세요"
  value       = "ALB(Ingress)용 ACM 인증서 ARN을 infra-k8s/ingress-with-origin-verify.yaml에 직접 입력하세요"
}

output "cloudfront_acm_certificate_arn" {
  description = "us-east-1에 발급된 CloudFront 전용 ACM 인증서"
  value       = aws_acm_certificate_validation.cloudfront.certificate_arn
}

output "waf_web_acl_arn" {
  value = aws_wafv2_web_acl.cloudfront.arn
}

output "origin_verify_secret_arn" {
  description = "X-Origin-Verify 헤더 값 — K8s Secret으로 주입해서 애플리케이션 필터가 검증"
  value       = aws_secretsmanager_secret.origin_verify.arn
}

output "alb_security_group_id" {
  description = "k8s/ingress-with-origin-verify.yaml의 alb.ingress.kubernetes.io/security-groups 값으로 채우세요"
  value       = aws_security_group.alb.id
}

output "cloudfront_domain_name" {
  description = "origin_alb_domain_name 채운 뒤 apply해야 값이 생김"
  value       = var.origin_alb_domain_name != "" ? aws_cloudfront_distribution.main[0].domain_name : "Ingress 배포 후 origin_alb_domain_name 채우고 재적용 필요"
}
