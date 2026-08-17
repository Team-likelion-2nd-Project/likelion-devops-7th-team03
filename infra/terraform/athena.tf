# ══════════════════════════════════════════════════
# Glue Data Catalog — Athena가 S3의 클릭 로그를 테이블로 인식하게 함
# ══════════════════════════════════════════════════
resource "aws_glue_catalog_database" "snipy" {
  name = "snipy_click_logs"
}

resource "aws_glue_catalog_table" "click_events" {
  name          = "click_events"
  database_name = aws_glue_catalog_database.snipy.name
  table_type    = "EXTERNAL_TABLE"

  parameters = {
    "classification"            = "json"
    "projection.enabled"        = "true"
    "projection.year.type"      = "integer"
    "projection.year.range"     = "2026,2030"
    "projection.month.type"     = "integer"
    "projection.month.range"    = "1,12"
    "projection.month.digits"   = "2"
    "projection.day.type"       = "integer"
    "projection.day.range"      = "1,31"
    "projection.day.digits"     = "2"
    "storage.location.template" = "s3://${aws_s3_bucket.click_logs.bucket}/year=$${year}/month=$${month}/day=$${day}/"
  }

  storage_descriptor {
    location      = "s3://${aws_s3_bucket.click_logs.bucket}/"
    input_format  = "org.apache.hadoop.mapred.TextInputFormat"
    output_format = "org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat"

    ser_de_info {
      serialization_library = "org.openx.data.jsonserde.JsonSerDe"
    }

    # redirect-service ClickEvent의 JSON(snake_case) 계약과 일치시킨다.
    columns {
      name = "event_id"
      type = "string"
    }
    columns {
      name = "schema_version"
      type = "int"
    }
    columns {
      name = "link_id"
      type = "bigint"
    }
    columns {
      name = "clicked_at"
      type = "string"
    }
    columns {
      name = "visitor_id"
      type = "string"
    }
    columns {
      name = "language"
      type = "string"
    }
    columns {
      name = "user_agent"
      type = "string"
    }
    columns {
      name = "device_type"
      type = "string"
    }
    columns {
      name = "operating_system"
      type = "string"
    }
    columns {
      name = "browser"
      type = "string"
    }
    columns {
      name = "referrer"
      type = "string"
    }
    columns {
      name = "referrer_category"
      type = "string"
    }
    columns {
      name = "is_bot"
      type = "boolean"
    }
  }

  # 날짜별 파티션 (Firehose의 S3 prefix 구조와 일치)
  partition_keys {
    name = "year"
    type = "string"
  }
  partition_keys {
    name = "month"
    type = "string"
  }
  partition_keys {
    name = "day"
    type = "string"
  }
}

# ══════════════════════════════════════════════════
# Athena Workgroup — 쿼리 결과 위치 + 비용 통제
# ══════════════════════════════════════════════════
resource "aws_athena_workgroup" "snipy" {
  name = "${var.cluster_name}-workgroup"

  configuration {
    enforce_workgroup_configuration    = true
    publish_cloudwatch_metrics_enabled = true

    result_configuration {
      output_location = "s3://${aws_s3_bucket.athena_results.bucket}/"
    }

    # 트래픽이 거의 없는 프로젝트라 스캔량 자체가 작겠지만,
    # 안전장치로 쿼리 1회당 스캔 상한을 걸어둠 (풀스캔하는 것 방지)
    bytes_scanned_cutoff_per_query = 1073741824 # 1GB
  }
}

# ══════════════════════════════════════════════════
# CronJob(IRSA)이 Athena/Glue/S3에 접근할 IAM Role
# ══════════════════════════════════════════════════
resource "aws_iam_role" "athena_cronjob" {
  name = "${var.cluster_name}-athena-cronjob-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Federated = module.eks.oidc_provider_arn
      }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${replace(module.eks.oidc_provider, "https://", "")}:sub" = "system:serviceaccount:default:athena-batch-sa"
          "${replace(module.eks.oidc_provider, "https://", "")}:aud" = "sts.amazonaws.com"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "athena_cronjob" {
  name = "${var.cluster_name}-athena-cronjob-policy"
  role = aws_iam_role.athena_cronjob.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "athena:StartQueryExecution",
          "athena:GetQueryExecution",
          "athena:GetQueryResults",
          "athena:StopQueryExecution"
        ]
        Resource = aws_athena_workgroup.snipy.arn
      },
      {
        Effect = "Allow"
        Action = [
          "glue:GetTable",
          "glue:GetDatabase",
          "glue:GetPartitions"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.click_logs.arn,
          "${aws_s3_bucket.click_logs.arn}/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.athena_results.arn,
          "${aws_s3_bucket.athena_results.arn}/*"
        ]
      }
    ]
  })
}

# ══════════════════════════════════════════════════
# redirect-service(IRSA)가 Firehose에 클릭 이벤트를 넣을 IAM Role
# ══════════════════════════════════════════════════
resource "aws_iam_role" "redirect_service" {
  name = "${var.cluster_name}-redirect-service-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Federated = module.eks.oidc_provider_arn
      }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${replace(module.eks.oidc_provider, "https://", "")}:sub" = "system:serviceaccount:default:redirect-service-sa"
          "${replace(module.eks.oidc_provider, "https://", "")}:aud" = "sts.amazonaws.com"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "redirect_service" {
  name = "${var.cluster_name}-redirect-service-policy"
  role = aws_iam_role.redirect_service.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "firehose:PutRecord",
        "firehose:PutRecordBatch"
      ]
      Resource = aws_kinesis_firehose_delivery_stream.click_events.arn
    }]
  })
}
