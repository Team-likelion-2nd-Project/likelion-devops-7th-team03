# ══════════════════════════════════════════════════
# S3 — 원시 클릭 로그 저장 (날짜별 파티셔닝)
# ══════════════════════════════════════════════════
resource "aws_s3_bucket" "click_logs" {
  bucket        = "${var.cluster_name}-click-logs-${data.aws_caller_identity.current.account_id}"
  force_destroy = true # 테스트로 켰다 끄는 프로젝트라, 데이터 남아있어도 destroy 가능하게

  tags = {
    Name = "${var.cluster_name}-click-logs"
  }
}

# 학습용 프로젝트, 짧은 기간만 운영 — 오래된 로그는 자동 정리해서 스토리지 비용 방지
resource "aws_s3_bucket_lifecycle_configuration" "click_logs" {
  bucket = aws_s3_bucket.click_logs.id

  rule {
    id     = "expire-old-logs"
    status = "Enabled"

    filter {}

    expiration {
      days = 14
    }
  }
}

resource "aws_s3_bucket_public_access_block" "click_logs" {
  bucket = aws_s3_bucket.click_logs.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Athena 쿼리 결과 저장용 (원시 로그와 분리)
resource "aws_s3_bucket" "athena_results" {
  bucket        = "${var.cluster_name}-athena-results-${data.aws_caller_identity.current.account_id}"
  force_destroy = true

  tags = {
    Name = "${var.cluster_name}-athena-results"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "athena_results" {
  bucket = aws_s3_bucket.athena_results.id

  rule {
    id     = "expire-query-results"
    status = "Enabled"

    filter {}

    expiration {
      days = 7
    }
  }
}

data "aws_caller_identity" "current" {}

# ══════════════════════════════════════════════════
# Firehose — Direct PUT → S3, 날짜별 파티셔닝
# ══════════════════════════════════════════════════
# Redis 실시간 통계(#72)가 이미 별도 직행 경로로 처리되고 있어,
# 클릭 이벤트를 S3에 적재하는 유일한 목적을 위해 Kinesis Data Stream을
# 중간에 둘 이유가 없다. redirect-service가 Firehose에 직접 PutRecord한다.
resource "aws_iam_role" "firehose" {
  name = "${var.cluster_name}-firehose-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "firehose.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "firehose" {
  name = "${var.cluster_name}-firehose-policy"
  role = aws_iam_role.firehose.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:AbortMultipartUpload",
          "s3:GetBucketLocation",
          "s3:GetObject",
          "s3:ListBucket",
          "s3:ListBucketMultipartUploads",
          "s3:PutObject"
        ]
        Resource = [
          aws_s3_bucket.click_logs.arn,
          "${aws_s3_bucket.click_logs.arn}/*"
        ]
      },
    ]
  })
}

resource "aws_kinesis_firehose_delivery_stream" "click_events" {
  name        = "${var.cluster_name}-click-events-firehose"
  destination = "extended_s3"

  # kinesis_source_configuration 블록 없음 = Direct PUT 모드.
  # redirect-service가 이 스트림에 PutRecord/PutRecordBatch로 직접 씀.

  extended_s3_configuration {
    role_arn   = aws_iam_role.firehose.arn
    bucket_arn = aws_s3_bucket.click_logs.arn

    # 날짜별 파티셔닝 — Athena가 하루 1번 배치에서 오늘 파티션만 스캔하도록
    prefix              = "year=!{timestamp:yyyy}/month=!{timestamp:MM}/day=!{timestamp:dd}/"
    error_output_prefix = "errors/!{firehose:error-output-type}/year=!{timestamp:yyyy}/month=!{timestamp:MM}/day=!{timestamp:dd}/"

    buffering_size     = 5   # MB — 트래픽 적으니 크기보다 시간 기준으로 flush됨
    buffering_interval = 300 # 5분마다 flush (실시간성보다 비용 효율 우선)

    compression_format = "GZIP"
  }

  tags = {
    Name = "${var.cluster_name}-click-events-firehose"
  }
}
