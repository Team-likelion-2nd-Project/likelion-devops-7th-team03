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
# Kinesis Data Stream
# ══════════════════════════════════════════════════
# On-Demand 모드: 평소 트래픽 거의 없다가 부하테스트 때만 스파이크 나는
# 이번 프로젝트 특성상, 용량을 미리 프로비저닝(Provisioned)하는 것보다
# 사용한 만큼만 과금되는 On-Demand가 비용 효율적
resource "aws_kinesis_stream" "click_events" {
  name = "${var.cluster_name}-click-events"
  stream_mode_details {
    stream_mode = "ON_DEMAND"
  }

  tags = {
    Name = "${var.cluster_name}-click-events"
  }
}

# ══════════════════════════════════════════════════
# Firehose — Kinesis → S3, 날짜별 파티셔닝
# ══════════════════════════════════════════════════
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
      {
        Effect = "Allow"
        Action = [
          "kinesis:DescribeStream",
          "kinesis:GetShardIterator",
          "kinesis:GetRecords",
          "kinesis:ListShards"
        ]
        Resource = aws_kinesis_stream.click_events.arn
      }
    ]
  })
}

resource "aws_kinesis_firehose_delivery_stream" "click_events" {
  name        = "${var.cluster_name}-click-events-firehose"
  destination = "extended_s3"

  kinesis_source_configuration {
    kinesis_stream_arn = aws_kinesis_stream.click_events.arn
    role_arn           = aws_iam_role.firehose.arn
  }

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
